package com.pleosstay.app.realtime

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.pleosstay.app.ui.theme.PleosStayPlusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Synthetic UI only: no real identity, token, service URL or server calls. Not a live pairing test. */
class RealtimeConnectScreenTest {
    @get:Rule val ui = createComposeRule()
    @Test fun normalFirstRunHasOneConnectionActionAndNoTechnicalInputs() {
        var requests = 0
        ui.setContent { PleosStayPlusTheme { RealtimeConnectScreen(null, false) { requests++ } } }
        ui.onNodeWithText("PC 연결하기").performScrollTo().performClick()
        ui.runOnIdle { assertEquals(1, requests) }
        ui.onNodeWithText("서버 주소").assertDoesNotExist()
        ui.onNodeWithText("로컬 연결 토큰").assertDoesNotExist()
        ui.onNodeWithText("인증 공개 키").assertDoesNotExist()
        ui.onNodeWithText("개발자 연결 설정").assertDoesNotExist()
    }
    @Test fun retryIsExplicitAndDoesNotPresentFakeCodesOrConfigurationFields() {
        var requests = 0
        ui.setContent { PleosStayPlusTheme {
            RealtimeConnectScreen("지금은 PC 연결을 사용할 수 없습니다.", false) { requests++ }
        } }
        ui.onNodeWithText("다시 시도").performScrollTo().performClick()
        ui.runOnIdle { assertEquals(1, requests) }
        ui.onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }
}
