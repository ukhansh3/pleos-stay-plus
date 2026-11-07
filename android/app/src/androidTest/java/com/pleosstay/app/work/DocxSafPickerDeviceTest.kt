package com.pleosstay.app.work

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.pleosstay.app.work.docx.*
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*

/** Real system ACTION_CREATE_DOCUMENT, synthetic text only; deletes only the returned test URI. */
class DocxSafPickerDeviceTest {
 @get:Rule val ui=createComposeRule()
 @Test fun actualSystemPickerCreatesValidDocxAndAllowsReopen(){
  val inst=InstrumentationRegistry.getInstrumentation();val app=inst.targetContext
  val intent=Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(DOCX_MIME)
  Assume.assumeTrue("BLOCKED: vehicle image resolves only a DocumentsUI stub",AndroidDocxFileGateway(app).hasSystemPicker())
  val saved=AtomicReference<Uri?>(null);val error=AtomicReference<String?>(null);val complete=AtomicReference(false)
  val engine=OoxmlDocxEngine.blank();engine.replaceText(0,0,0,"파일 선택기 한글 저장");val bytes=engine.export()
  try{
   ui.setContent{val scope=rememberCoroutineScope();val launcher=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(DOCX_MIME)){uri->
    saved.set(uri);if(uri!=null)scope.launch{try{AndroidDocxFileGateway(app).writeNew(uri,bytes){true};complete.set(true)}catch(t:Exception){error.set(t.javaClass.simpleName)}}
   };MaterialTheme{Button(onClick={launcher.launch("stay-docx-saf-${UUID.randomUUID()}.docx")}){Text("TEST_SAVE_DOCX")}}}
   ui.onNodeWithText("TEST_SAVE_DOCX").performClick()
   val deadline=SystemClock.uptimeMillis()+20000;var clicked=false
   while(SystemClock.uptimeMillis()<deadline&&!clicked){
    fun find(node:AccessibilityNodeInfo?):AccessibilityNodeInfo? {
     if(node==null)return null
     if(node.isClickable&&(node.text?.toString()?.lowercase() in setOf("save","저장")||node.contentDescription?.toString()?.lowercase() in setOf("save","저장")))return node
     for(i in 0 until node.childCount)find(node.getChild(i))?.let{return it};return null
    }
    val button=find(inst.uiAutomation.rootInActiveWindow)
    if(button!=null)clicked=button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    if(!clicked)SystemClock.sleep(100)
   }
   val ids=mutableListOf<String>();fun ids(node:AccessibilityNodeInfo?){if(node==null)return;if(node.isClickable)ids.add("${node.className}:${node.viewIdResourceName}");for(i in 0 until node.childCount)ids(node.getChild(i))};if(!clicked)ids(inst.uiAutomation.rootInActiveWindow)
   assertTrue("System picker save action unavailable; controls=$ids",clicked)
   ui.waitUntil(20000){complete.get()||error.get()!=null}
   assertNull(error.get());assertNotNull(saved.get())
   val reopened=runBlocking{AndroidDocxFileGateway(app).read(saved.get()!!)}
   assertEquals("파일 선택기 한글 저장",OoxmlDocxEngine.open(reopened).view().paragraphs.first().text);reopened.fill(0)
  }finally{saved.get()?.let{app.contentResolver.delete(it,null,null)};bytes.fill(0)}
 }
}
