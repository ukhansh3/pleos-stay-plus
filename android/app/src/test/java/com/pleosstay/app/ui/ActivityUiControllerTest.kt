package com.pleosstay.app.ui

import com.pleosstay.app.ui.activity.*
import com.pleosstay.app.ui.premium.*
import com.pleosstay.app.modes.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActivityUiControllerTest {
 private class Adapter(override val mode:ActivityMode):ActivityAdapter {
  var id:String?=null;var starts=0;var stops=0;var ready=true;var failStop=false
  var gate:CompletableDeferred<Unit>?=null;var input=false
  override fun sessionId()=id
  override suspend fun preflight()=ActivityOperation(ready)
  override suspend fun start():ActivityOperation {if(input)return ActivityOperation(false,"입력 필요",true);id="session-"+(++starts);return ActivityOperation.Done}
  override suspend fun resume()=ActivityOperation.Done
  override suspend fun stop():ActivityOperation{stops++;gate?.await();if(!failStop)id=null;return ActivityOperation(!failStop,"저장 실패")}
 }
 private class Fixture(scope:CoroutineScope) {
  val adapters=ActivityMode.entries.associateWith{Adapter(it)}
  var now=0L;var documents=true;var foreground=true;var save=true;var repair=true;var confirmLeave=false;var confirmEnd=false
  val hidden=mutableListOf<ActivityMode>();val choices=mutableListOf<WaitingChoice>();var repairs=0
  val coordinator=ActivityTransitionCoordinator(adapters.values.toList(),{now},1000)
  val ui=ActivityUiController(coordinator,scope,{documents},{ActivityOperation(foreground)},
   {ActivityOperation(save,"초안 실패")},{hidden.add(it)},{repairs++;ActivityOperation(repair,"복구 실패")},
   {confirmLeave},{confirmEnd},{_,choice->choices.add(choice)},{},{})
  val rest get()=adapters.getValue(ActivityMode.REST)
  val study get()=adapters.getValue(ActivityMode.STUDY)
  val work get()=adapters.getValue(ActivityMode.WORK)
  fun handled(route:StayRoute){assertTrue(ui.takeNavigationPermit(route));assertFalse(ui.takeNavigationPermit(route))}
 }
 @Test fun connectionSetupDoesNotStartWorkOrStopCurrentRest()=runTest {
  val f=Fixture(this);f.ui.open(ActivityMode.REST);runCurrent();f.handled(StayRoute.REST)
  f.documents=false;f.ui.open(ActivityMode.WORK)
  assertTrue(f.ui.state.value.preparing)
  f.ui.openDocumentConnection();runCurrent();f.handled(StayRoute.FILES)
  assertFalse(f.ui.state.value.preparing);assertEquals(0,f.work.starts)
  assertNotNull(f.rest.id);assertEquals(0,f.rest.stops)
 }
 @Test fun busyPreparationCannotBeReplacedByConnectionNavigation()=runTest {
  val f=Fixture(this);f.documents=false;f.ui.open(ActivityMode.WORK)
  assertTrue(f.ui.beginPreparation())
  f.ui.openDocumentConnection()
  assertTrue(f.ui.state.value.preparing);assertNull(f.ui.state.value.destination)
 }
 @Test fun connectionNavigationKeepsExistingWorkSaveConfirmation()=runTest {
  val f=Fixture(this);f.ui.open(ActivityMode.WORK);runCurrent();f.handled(StayRoute.WORK)
  f.confirmLeave=true;f.documents=false;f.ui.open(ActivityMode.STUDY)
  f.ui.openDocumentConnection();runCurrent()
  assertEquals(StayRoute.FILES,f.ui.state.value.leavePrompt)
  assertNull(f.ui.state.value.destination);assertNotNull(f.work.id)
 }
 @Test fun verifiedDocumentPreparationContinuesIntoRequestedModeWithoutFilesDetour()=runTest {
  for(mode in listOf(ActivityMode.STUDY,ActivityMode.WORK)) {
   val f=Fixture(this)
   f.ui.leave(StayRoute.FILES,StayRoute.HOME);runCurrent();f.handled(StayRoute.HOME)
   // Owners are hidden on Home, then perform fresh authorization for the selected mode.
   f.documents=false;f.ui.open(mode)
   assertTrue(f.ui.state.value.preparing);assertEquals(mode,f.ui.state.value.target)
   assertTrue(f.ui.beginPreparation())
   f.documents=true;f.ui.prepared();runCurrent()
   assertFalse(f.ui.state.value.preparing)
   assertEquals(mode.route(),f.ui.state.value.destination)
   assertEquals(1,f.adapters.getValue(mode).starts)
  }
 }
 @Test fun modeEntryRefreshesEvenAnAlreadyVerifiedWorkspaceBeforeStarting()=runTest {
  for(mode in listOf(ActivityMode.STUDY,ActivityMode.WORK)) {
   val f=Fixture(this);f.documents=true
   f.ui.open(mode,refreshDocuments=true);runCurrent()
   assertTrue(f.ui.state.value.preparing)
   assertEquals(0,f.adapters.getValue(mode).starts)
   assertNull(f.ui.state.value.destination)
   assertTrue(f.ui.beginPreparation());f.ui.prepared();runCurrent()
   assertEquals(mode.route(),f.ui.state.value.destination)
   assertEquals(1,f.adapters.getValue(mode).starts)
  }
 }
 @Test fun documentEntryCannotSkipAnUnverifiedOrRevokedSession()=runTest {
  for(mode in listOf(ActivityMode.STUDY,ActivityMode.WORK)) {
   val f=Fixture(this);f.documents=false;f.ui.open(mode)
   assertTrue(f.ui.beginPreparation());f.ui.prepared();runCurrent()
   assertTrue(f.ui.state.value.preparing)
   assertNull(f.ui.state.value.destination)
   assertEquals(0,f.adapters.getValue(mode).starts)
   f.ui.cancelPreparation();assertFalse(f.ui.state.value.preparing)
  }
 }
 @Test fun legacyAndUnknownRoutesAreDroppedFromSavedNavigation() {
  assertEquals(listOf("HOME", "MODES"), submissionRouteStack(listOf("HOME", "MODES", "ENTERTAINMENT")))
  assertEquals(listOf("HOME"), submissionRouteStack(listOf("ENTERTAINMENT", "UNKNOWN")))
  assertEquals(listOf("HOME", "WORK"), submissionRouteStack(listOf("HOME", "ENTERTAINMENT", "WORK")))
  assertEquals(listOf("HOME", "WORK"), pushRoute(listOf("HOME", "ENTERTAINMENT"), StayRoute.WORK))
 }

 @Test fun homeAndSameModeReturnKeepSessionForAllActivities()=runTest {
  for(mode in ActivityMode.entries){val f=Fixture(this);f.ui.open(mode);runCurrent();f.handled(mode.route());val id=f.adapters.getValue(mode).id
   f.ui.leave(mode.route(),StayRoute.HOME);runCurrent();f.handled(StayRoute.HOME)
   assertEquals(id,f.adapters.getValue(mode).id);f.ui.open(mode);runCurrent();assertEquals(1,f.adapters.getValue(mode).starts);assertEquals(mode.route(),f.ui.state.value.destination)
  }
 }
 @Test fun preparationDoesNotStopSourceOrNavigateBeforeRealDocumentReadiness()=runTest {
  val f=Fixture(this);f.ui.open(ActivityMode.REST);runCurrent();f.handled(StayRoute.REST);f.documents=false
  f.ui.open(ActivityMode.STUDY);assertTrue(f.ui.state.value.preparing);assertNotNull(f.rest.id);assertNull(f.ui.state.value.destination)
  assertTrue(f.ui.beginPreparation());f.ui.prepared();assertTrue(f.ui.state.value.preparing);assertNotNull(f.ui.state.value.preparationError)
  f.documents=true;f.ui.prepared();runCurrent();assertNotNull(f.ui.state.value.confirmation);assertNotNull(f.rest.id);assertEquals(0,f.study.starts)
 }
 @Test fun cancelSwitchKeepsRestAndPreventsDuplicatePrompt()=runTest {
  val f=Fixture(this);f.ui.open(ActivityMode.REST);runCurrent();f.handled(StayRoute.REST)
  f.ui.open(ActivityMode.STUDY);runCurrent();val token=f.ui.state.value.confirmation
  f.ui.open(ActivityMode.WORK);runCurrent();assertEquals(token,f.ui.state.value.confirmation)
  f.ui.confirmSwitch(false);runCurrent();assertNotNull(f.rest.id);assertEquals(0,f.study.starts);assertNull(f.ui.state.value.destination)
 }
 @Test fun approvedSwitchWaitsForStopAndBlocksRapidRepeatedInput()=runTest {
  val f=Fixture(this);f.ui.open(ActivityMode.REST);runCurrent();f.handled(StayRoute.REST)
  f.rest.gate=CompletableDeferred();f.ui.open(ActivityMode.STUDY);runCurrent();f.ui.confirmSwitch(true);runCurrent()
  f.ui.confirmSwitch(true);f.ui.open(ActivityMode.WORK);assertTrue(f.ui.state.value.busy);assertEquals(0,f.study.starts);assertNull(f.ui.state.value.destination)
  f.rest.gate!!.complete(Unit);runCurrent();assertEquals(1,f.rest.stops);assertEquals(1,f.study.starts);assertNull(f.rest.id);assertEquals(StayRoute.STUDY,f.ui.state.value.destination)
 }
 @Test fun failedStudySaveBlocksSwitchAndExplicitRetryRepairsBeforeStarting()=runTest {
  val f=Fixture(this);f.ui.open(ActivityMode.STUDY);runCurrent();f.handled(StayRoute.STUDY);f.study.failStop=true
  f.ui.open(ActivityMode.WORK);runCurrent();f.ui.confirmSwitch(true);runCurrent()
  assertNotNull(f.ui.state.value.error);assertNotNull(f.study.id);assertEquals(0,f.work.starts);assertNull(f.ui.state.value.destination)
  f.repair=false;f.ui.retry();runCurrent();assertEquals(0,f.work.starts);assertNotNull(f.study.id)
  f.repair=true;f.study.failStop=false;f.ui.retry();runCurrent();assertEquals(StayRoute.WORK,f.ui.state.value.destination);assertNull(f.study.id)
 }
 @Test fun workHomeFailureKeepsTimerAndRetryDoesNotEndActivity()=runTest {
  val f=Fixture(this);f.ui.open(ActivityMode.WORK);runCurrent();f.handled(StayRoute.WORK);val id=f.work.id
  f.confirmLeave=true;f.save=false;f.ui.leave(StayRoute.WORK,StayRoute.HOME);assertNotNull(f.ui.state.value.leavePrompt)
  f.ui.confirmLeaving(true);runCurrent();assertNotNull(f.ui.state.value.error);assertEquals(id,f.work.id);assertTrue(f.hidden.isEmpty())
  f.save=true;f.ui.retry();runCurrent();assertEquals(StayRoute.HOME,f.ui.state.value.destination);assertEquals(id,f.work.id);assertEquals(0,f.work.stops)
 }
 @Test fun returnToWorkAfterFailureKeepsSourceAndDoesNotStartTarget()=runTest {
  val f=Fixture(this);f.ui.open(ActivityMode.WORK);runCurrent();f.handled(StayRoute.WORK);f.work.failStop=true
  f.ui.open(ActivityMode.STUDY);runCurrent();f.ui.confirmSwitch(true);runCurrent();f.ui.returnToWork()
  assertEquals(StayRoute.WORK,f.ui.state.value.destination);assertNotNull(f.work.id);assertEquals(0,f.study.starts)
 }
 @Test fun expiredApprovalRetryMustAskAgainWithoutStoppingSource()=runTest {
  val f=Fixture(this);f.ui.open(ActivityMode.REST);runCurrent();f.handled(StayRoute.REST)
  f.ui.open(ActivityMode.STUDY);runCurrent();val old=f.ui.state.value.confirmation;f.now=1001;f.ui.confirmSwitch(true);runCurrent();assertNotNull(f.ui.state.value.error)
  f.ui.retry();runCurrent();assertNotNull(f.ui.state.value.confirmation);assertNotEquals(old,f.ui.state.value.confirmation);assertEquals(0,f.rest.stops);assertEquals(0,f.study.starts)
 }
 @Test fun safetyFailureBeforeConfirmedSwitchRetryMustNotImplicitlyEndSource()=runTest {
  val f=Fixture(this);f.ui.open(ActivityMode.REST);runCurrent();f.handled(StayRoute.REST);f.ui.open(ActivityMode.STUDY);runCurrent()
  f.study.ready=false;f.ui.confirmSwitch(true);runCurrent();f.study.ready=true;f.ui.retry();runCurrent()
  assertEquals(0,f.rest.stops);assertNotNull(f.ui.state.value.confirmation)
 }
 @Test fun foregroundLossBlocksIdleStartAndRecoveryRetry()=runTest {
  val f=Fixture(this);f.foreground=false;f.ui.open(ActivityMode.REST);runCurrent();assertNotNull(f.ui.state.value.error);assertEquals(0,f.rest.starts)
  f.foreground=true;f.ui.retry();runCurrent();assertEquals(StayRoute.REST,f.ui.state.value.destination)
 }
 @Test fun explicitEndUsesOneConfirmationAndKeepsConfigurationOnSuccess()=runTest {
  val f=Fixture(this);f.ui.open(ActivityMode.WORK);runCurrent();f.handled(StayRoute.WORK);f.confirmEnd=true
  f.ui.finish(ActivityMode.WORK);f.ui.finish(ActivityMode.WORK);assertEquals(ActivityMode.WORK,f.ui.state.value.endPrompt)
  f.ui.confirmFinish(false);assertNotNull(f.work.id);f.ui.finish(ActivityMode.WORK);f.ui.confirmFinish(true);runCurrent()
  assertNull(f.work.id);assertEquals(StayRoute.WORK,f.ui.state.value.destination);assertEquals(1,f.work.stops)
 }
 @Test fun startInputDoesNotNavigateAndCancellationNeverStarts()=runTest {
  val f=Fixture(this);f.study.input=true;f.ui.open(ActivityMode.STUDY);runCurrent();assertTrue(f.ui.state.value.input);assertNull(f.ui.state.value.destination)
  f.ui.choose(WaitingChoice.BACK);runCurrent();assertNull(f.study.id);assertFalse(f.ui.state.value.modal)
 }
 @Test fun waitingChoiceResubmitsOnlyThroughCoordinator()=runTest {
  val f=Fixture(this);f.study.input=true;f.ui.open(ActivityMode.STUDY);runCurrent();f.study.input=false
  f.ui.choose(WaitingChoice.SHORTEN);runCurrent();assertEquals(listOf(WaitingChoice.SHORTEN),f.choices);assertEquals(1,f.study.starts)
 }
 @Test fun recoveryPanelRetryOnlyRepairsAndNeverEndsTimer()=runTest {
  val f=Fixture(this);f.ui.open(ActivityMode.WORK);runCurrent();f.handled(StayRoute.WORK);val id=f.work.id
  f.repair=false;f.ui.repairOnly(ActivityMode.WORK);runCurrent();assertNotNull(f.ui.state.value.error)
  f.repair=true;f.ui.retry();runCurrent();assertEquals(id,f.work.id);assertEquals(0,f.work.stops);assertNull(f.ui.state.value.error)
 }
 @Test fun activeAdapterCannotBeReplacedAndMissingBindingDeniesEntry()=runTest {
  val binding=BoundActivityAdapter(ActivityMode.STUDY);assertFalse(binding.preflight().success)
  val first=Adapter(ActivityMode.STUDY);assertTrue(binding.bind(first));first.start()
  assertFalse(binding.bind(Adapter(ActivityMode.STUDY)));assertEquals(first.id,binding.sessionId())
 }
 @Test fun activityHistoryNeverResurrectsEndedMode() {
  var stack=listOf("HOME");stack=pushRoute(stack,StayRoute.REST);stack=pushRoute(stack,StayRoute.STUDY)
  assertFalse(stack.contains("REST"));assertEquals(listOf("HOME"),popRoute(stack))
  stack=pushRoute(stack,StayRoute.FILES);assertFalse(stack.contains("STUDY"))
 }
}
