package com.pleosstay.app.work

import android.net.Uri
import android.content.ContentValues
import android.provider.MediaStore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextRange
import androidx.test.platform.app.InstrumentationRegistry
import com.pleosstay.app.documents.*
import com.pleosstay.app.work.docx.*
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.*
import org.junit.Assert.*

class DocxEditorDeviceTest {
 @get:Rule val ui=createComposeRule()
 private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
 private class Gateway:SharedDocumentGateway{
  override val access=MutableStateFlow(DocumentAccess(true,"test-owner","test-connection","READY"))
  override suspend fun refreshAccess()=access.value
  override suspend fun listFiles()=emptyList<DocumentFile>()
  override suspend fun listVersions(fileId:String)=emptyList<DocumentRef>()
  override suspend fun validate(ref:DocumentRef)=false
  override suspend fun downloadVerified(ref:DocumentRef)=DocumentOpenResult.Failed("TEST_ONLY")
  override suspend fun uploadRevision(ref:DocumentRef,contentUri:Uri,idempotencyKey:String,progress:(Long,Long)->Unit):DocumentUploadResult=error("Upload excluded")
  override fun clearTemporaryFiles(){}
  override fun close(){}
 }
 @Test fun composeKoreanSelectionFormattingUndoExportAndEncryptedRestore() {
  val gateway=Gateway();val namespace="docx-test-${UUID.randomUUID()}";val repo=WorkDraftRepository(AndroidEncryptedDocumentStore(context,namespace),gateway)
  val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate);val allowed=AtomicBoolean(true)
  val ctl=DocxEditorController(repo,gateway,AndroidDocxFileGateway(context),scope){allowed.get()}
  var visible by mutableStateOf(true)
  var info:DocxDraftInfo?=null;var exported:Uri?=null
  try{
   ui.setContent{MaterialTheme{if(visible)DocxEditorPanel(ctl,{kind,_->if(kind==DocxPickerKind.EXPORT)ctl.requestDownloads()},{})else Text("LOCKED")}}
   ui.runOnIdle{ctl.newDocument()};ready(ctl)
   ui.onNodeWithTag("docx-body").performTextInput("한글 업무 문서");ready(ctl)
   ui.onNodeWithTag("docx-body").performTextInputSelection(TextRange(0,2))
   ui.onNodeWithText("글꼴·서식").performClick();ui.onNodeWithText("굵게").performClick();ready(ctl)
   assertTrue(ctl.state.value.view!!.paragraphs.first().runs.any{it.text.contains("한글")&&it.bold})
   ui.onNodeWithTag("docx-body").performTextInputSelection(TextRange(3,5));ui.onNodeWithText("글꼴·서식").performClick();ui.onNodeWithText("굵게").performClick();ready(ctl)
   assertTrue(ctl.state.value.view!!.paragraphs.first().runs.any{it.text.contains("업무")&&it.bold})
   ui.runOnIdle{ctl.edit{it.addTable(2,2);val cell=it.view().tables.first().rows.first().first().first();it.replaceText(cell,0,0,"표 수정");it.shadeCell(0,0,0,"AAFFAA")}};ready(ctl)
   ui.runOnIdle{ctl.undo()};ready(ctl);assertTrue(ctl.state.value.view!!.tables.isEmpty())
   ui.runOnIdle{ctl.redo()};ready(ctl);assertEquals(1,ctl.state.value.view!!.tables.size)
   fun image(color:Int):ByteArray {val bitmap=android.graphics.Bitmap.createBitmap(20,20,android.graphics.Bitmap.Config.ARGB_8888);bitmap.eraseColor(color);return java.io.ByteArrayOutputStream().also{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it);bitmap.recycle()}.toByteArray()}
   val red=image(android.graphics.Color.RED);val green=image(android.graphics.Color.GREEN);val blue=image(android.graphics.Color.BLUE)
   ui.runOnIdle{ctl.edit{it.addImage(red,"image/png",120,80);it.addImage(green,"image/png",140,90);it.replaceImage(0,blue,"image/png");it.resizeImage(0,240,160);it.format(0,0,2,DocxStyle(font="Noto Serif CJK KR",size=22f,color="1234AB"));it.align(0,"center")}};ready(ctl)
   ui.onNodeWithText("저장").performClick();ready(ctl);info=ctl.state.value.draft!!
   val bytes=runBlocking{repo.loadDocx(info!!).bytes}
   File(context.cacheDir,"docx-verification").apply{mkdirs()}.resolve("edited.docx").writeBytes(bytes)
   ui.onNodeWithText("다른 이름으로 저장").performClick();assertNull(ctl.state.value.exportUri)
   ui.onNodeWithText("취소").performClick();assertNull(ctl.state.value.exportUri)
   ui.onNodeWithText("다른 이름으로 저장").performClick()
   ui.onNodeWithText("DOCX 파일 이름").performTextReplacement("stay-docx-test-${UUID.randomUUID()}.docx")
   ui.onNodeWithText("공개 저장 위치 확인·내보내기").performClick();ready(ctl)
   val uri=ctl.state.value.exportUri!!;exported=uri
   val reopened=runBlocking{AndroidDocxFileGateway(context).read(uri)}
   val doc=OoxmlDocxEngine.open(reopened).view();assertEquals("한글 업무 문서",doc.paragraphs.first().text);assertEquals(1,doc.tables.size);assertEquals(2,doc.images.size);assertEquals(240,doc.images.first().width);bytes.fill(0);reopened.fill(0)
   ui.runOnIdle{ctl.exportPrivate()};ready(ctl);assertNotNull(ctl.state.value.exportUri)
   val temporary=ctl.state.value.exportUri!!;val privateBytes=runBlocking{AndroidDocxFileGateway(context).read(temporary)};assertEquals("한글 업무 문서",OoxmlDocxEngine.open(privateBytes).view().paragraphs.first().text);privateBytes.fill(0)
   ui.onNodeWithText("내보낸 DOCX 재열기").performClick();ready(ctl);assertEquals("한글 업무 문서",ctl.state.value.view!!.paragraphs.first().text)
   ui.runOnIdle{allowed.set(false);ctl.mask();visible=false};ui.onNodeWithTag("docx-body").assertDoesNotExist();ui.onNodeWithText("LOCKED").assertExists()
   ui.runOnIdle{ctl.setText(0,"DENIED")};ui.waitUntil(10000){!ctl.state.value.busy};assertNull(ctl.state.value.view)
   ui.runOnIdle{allowed.set(true);visible=true;ctl.restore(info!!)};ready(ctl);assertEquals("한글 업무 문서",ctl.state.value.view!!.paragraphs.first().text)
   // Separate repository/controller recreation proves persisted state, not just in-memory undo.
   val restored=runBlocking{WorkDraftRepository(AndroidEncryptedDocumentStore(context,"unused"),gateway).listDocx()};assertTrue(restored.isEmpty())
   val same=runBlocking{WorkDraftRepository(AndroidEncryptedDocumentStore(context,namespace),gateway).loadDocx(info!!)};assertEquals("한글 업무 문서",OoxmlDocxEngine.open(same.bytes).view().paragraphs.first().text);same.bytes.fill(0)
  }finally{exported?.let{context.contentResolver.delete(it,null,null)};runBlocking{repo.listDocx().forEach{repo.deleteDocx(it)}};ui.runOnIdle{ctl.mask()};scope.cancel()}
 }
 private fun ready(controller:DocxEditorController){ui.waitUntil(20000){!controller.state.value.busy&&controller.state.value.view!=null};ui.waitForIdle()}
}
