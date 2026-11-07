package com.pleosstay.app.work
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.pleosstay.app.documents.*
import com.pleosstay.app.work.docx.*
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*

/** Supervisor runs seed → am force-stop → verify; no real profile, source or database is used. */
class DocxProcessDeviceTest {
 @Test fun processBoundaryRetainsValidEncryptedDocx()=runBlocking {
  val inst=InstrumentationRegistry.getInstrumentation();val context=inst.targetContext
  val phase=InstrumentationRegistry.getArguments().getString("docxPhase")
  org.junit.Assume.assumeTrue("Opt-in process-boundary test: pass docxPhase seed or verify",phase in setOf("seed","verify"))
  val gate=object:SharedDocumentGateway{
   override val access=MutableStateFlow(DocumentAccess(true,"process-test-owner","process-test-connection","READY"))
   override suspend fun refreshAccess()=access.value
   override suspend fun listFiles()=emptyList<DocumentFile>();override suspend fun listVersions(fileId:String)=emptyList<DocumentRef>()
   override suspend fun validate(ref:DocumentRef)=false
   override suspend fun downloadVerified(ref:DocumentRef)=DocumentOpenResult.Failed("test")
   override suspend fun uploadRevision(ref:DocumentRef,contentUri:Uri,idempotencyKey:String,progress:(Long,Long)->Unit):DocumentUploadResult=error("excluded")
   override fun clearTemporaryFiles(){};override fun close(){}
  }
  val repo=WorkDraftRepository(AndroidEncryptedDocumentStore(context,"docx-process-proof-v1"),gate)
  val marker=File(context.cacheDir,"docx-process-proof-id.txt")
  if(phase=="seed"){
   repo.listDocx().forEach{repo.deleteDocx(it)}
   val e=OoxmlDocxEngine.blank();e.replaceText(0,0,0,"재시작 복원 한글");e.addTable(1,1)
   val d=repo.createDocx("프로세스 복원.docx",e.export());marker.writeText(d.info.id);d.bytes.fill(0)
  }else{
   assertTrue(marker.isFile);val expected=DocxDraftInfo(marker.readText(),"process-test-owner","process-test-connection","프로세스 복원.docx",0)
   val d=repo.loadDocx(expected);try{val v=OoxmlDocxEngine.open(d.bytes).view();assertEquals("재시작 복원 한글",v.paragraphs.first().text);assertEquals(1,v.tables.size)}finally{d.bytes.fill(0);repo.deleteDocx(expected);marker.delete()}
  }
 }
}
