package com.pleosstay.app.work

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.pleosstay.app.realtime.*
import com.pleosstay.app.waiting.safety.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import org.junit.*

/** Uses an isolated config/PIN directory; never changes the real app profile or connects to a server. */
class WorkPinGateDeviceTest {
 @get:Rule val ui=createComposeRule()
 @Test fun managedWorkspaceNeverRendersBeforeActualPinVerificationOrAfterForegroundLoss(){
  val base=InstrumentationRegistry.getInstrumentation().targetContext
  val directory=File(base.cacheDir,"pin-test-${UUID.randomUUID()}").apply{mkdirs()}
  val isolated=object:ContextWrapper(base){override fun getApplicationContext():Context=this;override fun getNoBackupFilesDir():File=directory}
  val safety=object:WaitingSafetyGateway{override val state=MutableStateFlow(WaitingSafety(VehicleMotion.PARKED,false));override val drivingEvents=DrivingEventBuffer();override fun start(){};override fun stop(){};override fun close(){}}
  RealtimeTokenStore(isolated,"realtime-local-config.bin").write(JSONObject().put("url","http://127.0.0.1:49201/api/v1/realtime/").put("token",UUID.randomUUID().toString()).put("local",true).toString())
  var foreground by mutableStateOf(true)
  var observed:FileTransferViewModel?=null
  try{
   ui.setContent{MaterialTheme{RealtimeEntry(isolated,safety,foreground,managedWorkspace={m,_-> SideEffect{observed=m};{Text("SENSITIVE_TEST_DOCUMENT")}})}}
   ui.waitUntil(10000){ui.onAllNodesWithText("http://127.0.0.1:49201/api/v1/realtime/",substring=true).fetchSemanticsNodes().isNotEmpty()}
   ui.onNodeWithText("연결").performClick()
   ui.onNodeWithText("SENSITIVE_TEST_DOCUMENT").assertDoesNotExist()
   ui.onNodeWithText("PIN").performTextInput("654321");ui.onNodeWithText("PIN 확인").performTextInput("654321");ui.onNodeWithText("잠금 해제").performClick()
   try{ui.waitUntil(30000){ui.onAllNodesWithText("SENSITIVE_TEST_DOCUMENT").fetchSemanticsNodes().isNotEmpty()}}catch(t:Exception){
    val labels=ui.onRoot().printToString().lineSequence().filter{it.contains("Text =")&&(it.contains("PIN")||it.contains("잠금")||it.contains("ERROR"))}.joinToString("\n")
    val configured=File(directory,"realtime-pin.json").exists()
    throw AssertionError("PIN test: allowed=${observed?.state?.value?.allowed}, unlocked=${observed?.state?.value?.unlocked}, configured=$configured labels=$labels",t)
   }
   ui.runOnIdle{foreground=false};ui.onNodeWithText("SENSITIVE_TEST_DOCUMENT").assertDoesNotExist()
   ui.runOnIdle{foreground=true};ui.onNodeWithText("SENSITIVE_TEST_DOCUMENT").assertDoesNotExist();ui.onNodeWithText("파일 화면 PIN 잠금").assertExists()
  }finally{ui.runOnIdle{foreground=false};directory.deleteRecursively()}
 }
}
