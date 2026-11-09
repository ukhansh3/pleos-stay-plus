package com.pleosstay.app.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.pleosstay.app.modes.ActivityMode
import com.pleosstay.app.ui.activity.ActivityModal
import com.pleosstay.app.ui.activity.ActivitySwitchPanel
import com.pleosstay.app.ui.premium.StayModeMenuScreen
import com.pleosstay.app.ui.premium.StayRoute
import com.pleosstay.app.ui.theme.PleosStayPlusTheme

@PreviewTest
@Preview(name = "HomeWide", widthDp = 1682, heightDp = 1210, apiLevel = 34, locale = "ko-rKR")
@Composable fun CaptureSubmissionHomeWide() = JourneyHome(active = true)

@PreviewTest
@Preview(name = "HomeSmall", widthDp = 960, heightDp = 720, apiLevel = 34, locale = "ko-rKR")
@Composable fun CaptureSubmissionHomeSmall() = JourneyHome(active = true)

@PreviewTest
@Preview(name = "HomePortrait", widthDp = 720, heightDp = 1210, apiLevel = 34, locale = "ko-rKR")
@Composable fun CaptureSubmissionHomePortrait() = JourneyHome(active = true)

@PreviewTest
@Preview(name = "HomeNarrow", widthDp = 600, heightDp = 720, apiLevel = 34, locale = "ko-rKR")
@Composable fun CaptureSubmissionHomeNarrow() = JourneyHome(active = true)

@PreviewTest
@Preview(name = "Waiting", widthDp = 960, heightDp = 720, apiLevel = 34, locale = "ko-rKR")
@Composable fun CaptureSubmissionWaiting() = JourneyWaiting(active = true)

@PreviewTest
@Preview(name = "ModeMenu", widthDp = 960, heightDp = 720, apiLevel = 34, locale = "ko-rKR")
@Composable fun CaptureSubmissionModeMenu() = JourneyFrame(StayRoute.MODES, scroll = false) {
    StayModeMenuScreen(onRest = {}, onStudy = {}, onWork = {})
}

@PreviewTest
@Preview(name = "Transition", widthDp = 960, heightDp = 720, apiLevel = 34, locale = "ko-rKR")
@Composable fun CaptureSubmissionTransition() = PleosStayPlusTheme {
    CaptureSubmissionModeMenu()
    ActivityModal("휴식 종료 후 공부 전환", onBack = {}) {
        ActivitySwitchPanel(ActivityMode.REST, ActivityMode.STUDY, false, {}, {})
    }
}
