package com.pleosstay.app.realtime
import androidx.lifecycle.ViewModelStore
import com.pleosstay.app.waiting.safety.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
private class SafetyFixture : WaitingSafetyGateway {
 override val state=MutableStateFlow(WaitingSafety())
 override val drivingEvents=DrivingEventBuffer()
 var starts=0;var stops=0
 override fun start(){starts++};override fun stop(){stops++};override fun close(){}
}
private class RevisionFixture : RevisionStore {
 override suspend fun stage(request:WorkRevisionRequest):StagedRevision=error("UNEXPECTED_IO")
 override fun describe(uri:android.net.Uri):Pair<String,String> = error("UNEXPECTED_IO")
 override fun save(staged:StagedRevision,connection:String)=error("UNEXPECTED_IO")
 override fun pending(connection:String):StagedRevision?=null
 override fun finish(staged:StagedRevision){}
 override fun clear(){}
}
@OptIn(ExperimentalCoroutinesApi::class)
class FileTransferSafetyTest {
 @Test fun unknownAndDrivingLockWithoutOwningSafetyLifecycle()=runTest {
  Dispatchers.setMain(StandardTestDispatcher(testScheduler));val owner=ViewModelStore()
  try {
   val safety=SafetyFixture();val vm=FileTransferViewModel(RealtimeApi("https://fake.invalid/"){error("NETWORK_FORBIDDEN")},safety,RevisionFixture(),File("unused"));owner.put("realtime",vm);runCurrent()
   vm.unlockAfterUserVerification();assertFalse(vm.state.value.unlocked)
   safety.state.value=WaitingSafety(VehicleMotion.PARKED,false);runCurrent();assertTrue(vm.state.value.allowed);assertFalse(vm.state.value.unlocked)
   vm.unlockAfterUserVerification();assertTrue(vm.state.value.unlocked)
   safety.state.value=WaitingSafety(VehicleMotion.DRIVING,false);runCurrent();assertFalse(vm.state.value.allowed);assertFalse(vm.state.value.unlocked)
   safety.state.value=WaitingSafety(VehicleMotion.PARKED,false);runCurrent();assertFalse(vm.state.value.unlocked)
   assertEquals(0,safety.starts);assertEquals(0,safety.stops)
  }finally{owner.clear();Dispatchers.resetMain()}
 }
 @Test fun verifiedPinSurvivesInternalNavigationButNotBackgroundOrExplicitLock()=runTest {
  Dispatchers.setMain(StandardTestDispatcher(testScheduler));val owner=ViewModelStore()
  try {
   val safety=SafetyFixture().apply{state.value=WaitingSafety(VehicleMotion.PARKED,false)}
   val vm=FileTransferViewModel(RealtimeApi("https://fake.invalid/"){error("NETWORK_FORBIDDEN")},safety,RevisionFixture(),File("unused"))
   owner.put("realtime",vm);runCurrent()
   assertFalse(vm.state.value.unlocked)
   vm.unlockAfterUserVerification()
   // MainActivity now supplies the actual app foreground through Files/Home/Modes/Study/Work.
   repeat(5){vm.setAppForeground(true);runCurrent();assertTrue(vm.state.value.unlocked)}
   vm.setAppForeground(false);assertFalse(vm.state.value.unlocked)
   vm.setAppForeground(true);runCurrent();assertFalse(vm.state.value.unlocked)
   vm.unlockAfterUserVerification();vm.lock();vm.setAppForeground(true)
   assertFalse(vm.state.value.unlocked)
   vm.unlockAfterUserVerification()
   safety.state.value=WaitingSafety(VehicleMotion.UNKNOWN,true);runCurrent()
   assertFalse(vm.state.value.unlocked)
   safety.state.value=WaitingSafety(VehicleMotion.PARKED,false);runCurrent()
   vm.setAppForeground(true);assertFalse(vm.state.value.unlocked)
  }finally{owner.clear();Dispatchers.resetMain()}
 }
 @Test fun mimeAllowlistIsExplicit(){assertEquals("application/pdf",LocalFileStore.documentMime("Report.PDF"));assertNull(LocalFileStore.documentMime("report.docm"));assertNull(LocalFileStore.documentMime("report.exe"))}
}
