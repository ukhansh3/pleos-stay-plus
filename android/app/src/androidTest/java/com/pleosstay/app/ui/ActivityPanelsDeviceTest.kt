package com.pleosstay.app.ui

import androidx.compose.ui.test.*
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.createComposeRule
import com.pleosstay.app.ui.activity.*
import com.pleosstay.app.ui.theme.PleosStayPlusTheme
import com.pleosstay.app.modes.ActivityMode
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Compiled with AndroidTest APK; execution requires a separately authorized device. */
class ActivityPanelsDeviceTest {
 @get:Rule val ui=createComposeRule()
 @Test fun transitionButtonsDispatchCancelAndApprovalExactlyOnce(){
  var cancel=0;var confirm=0
  ui.setContent{PleosStayPlusTheme{Column{ActivitySwitchPanel(ActivityMode.REST,ActivityMode.STUDY,false,{cancel++},{confirm++})}}}
  ui.onNodeWithText("취소").performClick();assertEquals(1,cancel)
  ui.onNodeWithText("공부 시작").performClick();assertEquals(1,confirm)
 }
 @Test fun busyTransitionDisablesBothButtons(){
  ui.setContent{PleosStayPlusTheme{Column{ActivitySwitchPanel(ActivityMode.WORK,ActivityMode.STUDY,true,{},{})}}}
  ui.onNodeWithText("취소").assertIsNotEnabled();ui.onNodeWithText("공부 시작").assertIsNotEnabled()
 }
 @Test fun recoveryOffersRetryAndReturnWithoutDiscard(){
  var retry=0;var back=0
  ui.setContent{PleosStayPlusTheme{ActivityRecoveryPanel("메모 입력 유지",false,{retry++},{back++})}}
  ui.onNodeWithText("다시 저장").performClick();ui.onNodeWithText("문서로 돌아가기").performClick()
  assertEquals(1,retry);assertEquals(1,back);ui.onNodeWithText("삭제").assertDoesNotExist()
 }
}
