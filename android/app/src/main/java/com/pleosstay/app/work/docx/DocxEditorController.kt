package com.pleosstay.app.work.docx

import android.net.Uri
import com.pleosstay.app.documents.*
import com.pleosstay.app.work.*
import java.util.ArrayDeque
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class DocxEditorState(val view:DocxView?=null,val draft:DocxDraftInfo?=null,val drafts:List<DocxDraftInfo> = emptyList(),val busy:Boolean=false,val dirty:Boolean=false,val canUndo:Boolean=false,val canRedo:Boolean=false,val exportUri:Uri?=null,val confirmDownloads:Boolean=false,val message:String="DOCX를 열거나 새 문서를 만드세요.")
enum class DocxPickerKind { OPEN, INSERT_IMAGE, REPLACE_IMAGE, EXPORT }
data class DocxPickerRequest(val kind:DocxPickerKind,val owner:String,val connection:String,val draft:DocxDraftInfo?,val image:Int?=null)

/** Owns a real mutable document. Persistence, picker I/O and UI are separate ports. */
class DocxEditorController(
 private val repository:WorkDraftRepository,private val documents:SharedDocumentGateway,
 private val files:DocxFileGateway?,private val scope:CoroutineScope,
 private val retentionPermitted:(()->Boolean)?=null,private val permitted:()->Boolean,
) {
 private val mutable=MutableStateFlow(DocxEditorState());val state=mutable.asStateFlow()
 private var engine:OoxmlDocxEngine?=null
 private var info:DocxDraftInfo?=null
 private val mutex=Mutex();private var epoch=0L
 private val undo=ArrayDeque<ByteArray>();private val redo=ArrayDeque<ByteArray>()
 private var currentBytes:ByteArray?=null
 private var pending=0
 private val jobs = mutableSetOf<Job>()
 private var quiescing = false
 private var retentionFailed = false
 val requiresRetentionRecovery get()=retentionFailed || failedRetention!=null || pending>0
 private var failedRetention: EncryptedEditRecovery? = null

 /** Prevent new edits, drain accepted jobs, and verify private retention. Never exports. */
 suspend fun flushForTransition(): com.pleosstay.app.modes.ActivityOperation {
  quiescing = true
  return try {
   jobs.toList().joinAll()
   mutex.withLock {
    if (retentionFailed || failedRetention != null) return@withLock com.pleosstay.app.modes.ActivityOperation(false,"DOCX 초안 저장을 다시 확인해 주세요.")
    if (engine != null) {
     checkAccess()
     repository.retainDocx(DocxDraft(checkNotNull(info),checkNotNull(currentBytes)))
     checkAccess()
    }
    com.pleosstay.app.modes.ActivityOperation.Done
   }
  } catch (c:CancellationException) { throw c }
  catch (_:Exception) { retentionFailed=true; com.pleosstay.app.modes.ActivityOperation(false,"DOCX 초안을 저장하지 못했습니다.") }
  finally { if(retentionFailed) quiescing=false }
 }

 /** A failed candidate is retained privately for explicit retry, never reported as committed. */
 suspend fun retryRetention(): com.pleosstay.app.modes.ActivityOperation = mutex.withLock {
  try {
   check((retentionPermitted?.invoke() ?: permitted()) && documents.refreshAccess().allowed)
   failedRetention?.let { repository.loadDocx(it.info).bytes.fill(0) }
   val candidate=failedRetention?.open()
   if(candidate!=null) {
    try {
    repository.retainDocx(candidate)
    if(permitted() && documents.access.value.allowed) {
     engine=withContext(Dispatchers.IO){OoxmlDocxEngine.open(candidate.bytes)}
     checkAccess()
     info=candidate.info;currentBytes?.fill(0);currentBytes=candidate.bytes.copyOf()
     publish("초안 저장을 재시도했습니다.")
    }
    failedRetention?.clear();failedRetention=null
    } finally { candidate.bytes.fill(0) }
   } else if(engine!=null) repository.retainDocx(DocxDraft(checkNotNull(info),checkNotNull(currentBytes)))
   retentionFailed=false
   com.pleosstay.app.modes.ActivityOperation.Done
  } catch(c:CancellationException){throw c}
  catch(_:Exception){com.pleosstay.app.modes.ActivityOperation(false,"초안 저장 재시도에 실패했습니다.")}
 }
 private fun checkAccess(){check(permitted()&&documents.access.value.allowed){"DOCX_LOCKED"}}
 private fun clearHistory(){undo.forEach{it.fill(0)};redo.forEach{it.fill(0)};undo.clear();redo.clear()}
 fun mask(){if(pending>0)retentionFailed=true;epoch++;pending=0;quiescing=false;files?.clearPrivateExports();engine=null;info=null;currentBytes?.fill(0);currentBytes=null;clearHistory();mutable.value=DocxEditorState(message="문서 잠김 · 암호화 초안은 유지됩니다.")}
 private fun action(queue:Boolean=false,block:suspend()->Unit){if(quiescing || failedRetention!=null || (mutable.value.busy&&!queue))return;val ticket=epoch;pending++;mutable.update{it.copy(busy=true)}
  val job=scope.launch(start=CoroutineStart.LAZY){try{mutex.withLock{check(ticket==epoch);checkAccess();block();if(ticket!=epoch||!permitted())mask()}}catch(c:CancellationException){throw c}catch(_:Exception){if(ticket==epoch)retentionFailed=true;if(ticket==epoch)mutable.update{it.copy(message="DOCX 작업 실패: 권한·저장공간·파일 호환성을 확인해 주세요. 초안은 유지됩니다.")}}finally{if(ticket==epoch){pending--;mutable.update{it.copy(busy=pending>0)}}}}
  jobs.add(job);job.invokeOnCompletion{jobs.remove(job)};job.start()
 }
 private suspend fun attach(draft:DocxDraft){try{
  val next=withContext(Dispatchers.IO){OoxmlDocxEngine.open(draft.bytes)};checkAccess()
  engine=next;info=draft.info;currentBytes?.fill(0);currentBytes=draft.bytes.copyOf();clearHistory()
  mutable.value=DocxEditorState(view=next.view(),draft=draft.info,busy=pending>0,message=next.view().readOnlyReason ?: "DOCX 구조 기반 편집 · Word 페이지 배치와 다를 수 있습니다.")
 }finally{draft.bytes.fill(0)}}
 fun newDocument()=action{val bytes=withContext(Dispatchers.IO){OoxmlDocxEngine.blank().export()};try{attach(repository.createDocx("새 문서.docx",bytes))}finally{bytes.fill(0)}}
 fun listDrafts()=action{val list=repository.listDocx();checkAccess();mutable.update{it.copy(drafts=list,message=if(list.isEmpty())"보관한 DOCX 초안이 없습니다."else"마지막 작업 순으로 표시합니다.")}}
 fun restore(d:DocxDraftInfo)=action{attach(repository.loadDocx(d))}
 fun delete(d:DocxDraftInfo)=action{repository.deleteDocx(d);if(info?.id==d.id){engine=null;info=null;currentBytes?.fill(0);currentBytes=null;clearHistory()};val list=repository.listDocx();checkAccess();mutable.update{it.copy(drafts=list,view=engine?.view(),draft=info,message="초안을 삭제했습니다.")}}
 suspend fun openRemote(ref:DocumentRef){checkAccess();require(ref.mimeType==DOCX_MIME);val bytes=repository.sourceForDocx(ref);try{attach(repository.createDocx(ref.name,bytes,ref))}finally{bytes.fill(0)}}
 private fun history(queue:ArrayDeque<ByteArray>,bytes:ByteArray){queue.addLast(bytes.copyOf());while(queue.size>20||queue.sumOf{it.size}>32*1024*1024)queue.removeFirst().fill(0)}
 fun setText(id:Int,value:String)=edit{doc->val old=doc.view().paragraphs.first{it.id==id}.text;var prefix=0;while(prefix<old.length&&prefix<value.length&&old[prefix]==value[prefix])prefix++;if(prefix>0&&prefix<old.length&&old[prefix].isLowSurrogate()&&old[prefix-1].isHighSurrogate())prefix--;var suffix=0;while(suffix<old.length-prefix&&suffix<value.length-prefix&&old[old.lastIndex-suffix]==value[value.lastIndex-suffix])suffix++;val boundary=old.length-suffix;if(suffix>0&&boundary>0&&boundary<old.length&&old[boundary].isLowSurrogate()&&old[boundary-1].isHighSurrogate())suffix--;doc.replaceText(id,prefix,old.length-suffix,value.substring(prefix,value.length-suffix))}
 fun edit(change:(DocxDocumentEngine)->Unit)=action(queue=true){
  val old=checkNotNull(currentBytes).copyOf();val ticket=epoch
  val captured=checkNotNull(info)
  try{val candidate=withContext(Dispatchers.IO){OoxmlDocxEngine.open(old).also(change)};checkAccess();val bytes=withContext(Dispatchers.IO){candidate.export()}
   try{val d=DocxDraft(captured.copy(updatedAt=System.currentTimeMillis()),bytes)
    // Accepted edits finish encrypted retention atomically even if a local lock arrives meanwhile.
    try { withContext(NonCancellable){repository.retainDocx(d)} }
    catch(error:Exception) { failedRetention?.clear();failedRetention=EncryptedEditRecovery(d);throw error }
    if(ticket==epoch&&permitted()){history(undo,old);redo.forEach{it.fill(0)};redo.clear();engine=candidate;info=d.info;currentBytes?.fill(0);currentBytes=bytes.copyOf();publish("자동 초안 저장됨 · 외부 DOCX 내보내기 전")}
   }finally{bytes.fill(0)}
  }finally{old.fill(0)}
 }
 private fun publish(message:String){mutable.update{it.copy(view=engine?.view(),draft=info,dirty=true,canUndo=undo.isNotEmpty(),canRedo=redo.isNotEmpty(),message=message)}}
 fun undo()=travel(undo,redo)
 fun redo()=travel(redo,undo)
 private fun travel(from:ArrayDeque<ByteArray>,to:ArrayDeque<ByteArray>)=action{if(from.isEmpty())return@action;val bytes=from.last().copyOf();try{val next=withContext(Dispatchers.IO){OoxmlDocxEngine.open(bytes)};checkAccess();repository.retainDocx(DocxDraft(info!!.copy(updatedAt=System.currentTimeMillis()),bytes));checkAccess();history(to,currentBytes!!);from.removeLast().fill(0);currentBytes?.fill(0);currentBytes=bytes.copyOf();engine=next;publish("변경 이력 복원·초안 저장됨")}finally{bytes.fill(0)}}
 fun save()=action{checkNotNull(currentBytes);repository.retainDocx(DocxDraft(info!!.copy(updatedAt=System.currentTimeMillis()),currentBytes!!));checkAccess();mutable.update{it.copy(message="암호화 DOCX 초안 저장 완료 · 외부 파일은 다른 이름으로 저장하세요.")}}
 fun pickerUnavailable(){mutable.update{it.copy(message="시스템 파일 선택기를 열 수 없습니다. 암호화 초안은 앱 전용 저장소에 보관되며 파일 선택기가 가능한 환경에서 다시 내보낼 수 있습니다.")}}
 fun hasSystemPicker()=files?.hasSystemPicker()==true
 fun exportPrivate()=action{val bytes=currentBytes!!.copyOf();val ticket=epoch;try{val uri=files!!.writePrivate(bytes){ticket==epoch&&permitted()};checkAccess();mutable.update{it.copy(exportUri=uri,message="앱 전용 임시 DOCX 생성·재열기 검증 완료. 잠금/화면 종료 시 임시 파일은 삭제되며 암호화 초안은 유지됩니다. 시스템 파일 선택기는 미지원입니다.")}}finally{bytes.fill(0)}}
 fun reopenExport() {val uri=state.value.exportUri?:return;val a=documents.access.value;accept(DocxPickerRequest(DocxPickerKind.OPEN,a.userScope?:return,a.connectionId?:return,null),uri)}
 fun requestDownloads(){if(permitted()&&engine!=null&&!state.value.busy)mutable.update{it.copy(confirmDownloads=true)}}
 fun dismissDownloads(){mutable.update{it.copy(confirmDownloads=false)}}
 fun exportDownloads(name:String)=action{
  mutable.update{it.copy(confirmDownloads=false)}
  val ticket=epoch;val draft=repository.loadDocx(checkNotNull(info))
  try{checkAccess();val uri=files!!.writeDownload(name,draft.bytes){ticket==epoch&&permitted()}
   if(ticket==epoch&&permitted())mutable.update{it.copy(exportUri=uri,dirty=false,message="Download/StayPlus에 새 DOCX 저장·재열기 검증 완료. 공개 저장본은 잠금·앱 삭제 후에도 남습니다.")}
  }finally{draft.bytes.fill(0)}
 }
 suspend fun prepare(kind:DocxPickerKind,image:Int?=null):DocxPickerRequest=mutex.withLock{
  checkAccess();val a=documents.refreshAccess();checkAccess();require(a.allowed)
  if(kind!=DocxPickerKind.OPEN){check(engine!=null);check(engine!!.view().readOnlyReason==null||kind==DocxPickerKind.EXPORT);repository.retainDocx(DocxDraft(info!!,currentBytes!!))}
  DocxPickerRequest(kind,a.userScope!!,a.connectionId!!,if(kind==DocxPickerKind.OPEN)null else info,image)
 }
 fun accept(request:DocxPickerRequest,uri:Uri)=action{
  val a=documents.refreshAccess();checkAccess();check(a.userScope==request.owner&&a.connectionId==request.connection)
  val io=checkNotNull(files)
  if(request.kind==DocxPickerKind.OPEN){val bytes=io.read(uri);try{withContext(Dispatchers.IO){OoxmlDocxEngine.open(bytes)};checkAccess();attach(repository.createDocx(io.name(uri),bytes))}finally{bytes.fill(0)}}
  else {attach(repository.loadDocx(checkNotNull(request.draft)));val bytes=currentBytes!!
   if(request.kind==DocxPickerKind.EXPORT){val ticket=epoch;io.writeNew(uri,bytes){ticket==epoch&&permitted()};checkAccess();mutable.update{it.copy(dirty=false,message="DOCX 파일 저장·해시·구조 재열기 검증 완료")}}
   else {val data=io.read(uri,4*1024*1024);try{val mime=if(data.size>7&&data[0]==(-119).toByte())"image/png"else"image/jpeg";val candidate=OoxmlDocxEngine.open(bytes);if(request.kind==DocxPickerKind.INSERT_IMAGE)candidate.addImage(data,mime,240,160)else candidate.replaceImage(checkNotNull(request.image),data,mime);val exported=candidate.export();try{checkAccess();repository.retainDocx(DocxDraft(info!!.copy(updatedAt=System.currentTimeMillis()),exported));checkAccess();history(undo,bytes);engine=candidate;currentBytes?.fill(0);currentBytes=exported.copyOf();publish("이미지 변경·초안 저장됨")}finally{exported.fill(0)}}finally{data.fill(0)}}
  }
 }
}
