package com.pleosstay.app.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.pleosstay.app.pickup.ui.PickupUiState
import com.pleosstay.app.ui.premium.*
import com.pleosstay.app.ui.theme.PleosStayPlusTheme
import com.pleosstay.app.waiting.ui.WaitingUiState

/** IDE-only gallery. Every frame reuses actual UI; all data is synthetic and static. */
@Preview(name = "01 홈 · 예시", group = "Stay+ 전체 화면", widthDp = 1682, heightDp = 1210, apiLevel = 34, locale = "ko-rKR", showBackground = true)
@Composable
private fun GalleryHome() {
    if (!LocalInspectionMode.current) { Text("Design/Split 미리보기에서 열어 주세요."); return }
    PleosStayPlusTheme {
        Row(Modifier.fillMaxSize().background(StayColors.Background)) {
            StayNavigationRail(NavigationGroup.HOME, {}, 180.dp)
            Box(Modifier.weight(1f)) { StayHomeScreen(WaitingUiState(ready = true), PickupUiState(), {}) }
        }
    }
}

@Preview(name = "02 주차장 검색·상세 · 예시", group = "Stay+ 전체 화면", widthDp = 1682, heightDp = 1210, apiLevel = 34, locale = "ko-rKR", showBackground = true)
@Composable
private fun GalleryParking() = PreviewFeature(PreviewScreen.PARKING)

@Preview(name = "03 대기 관리 · 예시", group = "Stay+ 전체 화면", widthDp = 1682, heightDp = 1210, apiLevel = 34, locale = "ko-rKR", showBackground = true)
@Composable
private fun GalleryWaiting() = PreviewFeature(PreviewScreen.WAITING)

@Preview(name = "04 픽업 공유 · 예시", group = "Stay+ 전체 화면", widthDp = 1682, heightDp = 1210, apiLevel = 34, locale = "ko-rKR", showBackground = true)
@Composable
private fun GalleryPickup() = PreviewFeature(PreviewScreen.PICKUP)

@Preview(name = "05 파일 작업 · 예시", group = "Stay+ 전체 화면", widthDp = 1682, heightDp = 1210, apiLevel = 34, locale = "ko-rKR", showBackground = true)
@Composable
private fun GalleryRealtime() = PreviewFeature(PreviewScreen.REALTIME)

@Preview(name = "06 휴식 · 예시", group = "Stay+ 전체 화면", widthDp = 1682, heightDp = 1210, apiLevel = 34, locale = "ko-rKR", showBackground = true)
@Composable
private fun GalleryRest() = PreviewFeature(PreviewScreen.REST)

@Preview(name = "07 공부 · 예시", group = "Stay+ 전체 화면", widthDp = 1682, heightDp = 1210, apiLevel = 34, locale = "ko-rKR", showBackground = true)
@Composable
private fun GalleryStudy() = PreviewFeature(PreviewScreen.STUDY)

@Preview(name = "08 업무 자료함 · 예시", group = "Stay+ 전체 화면", widthDp = 1682, heightDp = 1210, apiLevel = 34, locale = "ko-rKR", showBackground = true)
@Composable
private fun GalleryWorkLibrary() = PreviewFeature(PreviewScreen.WORK_LIBRARY)

@Preview(name = "09 DOCX 편집기 · 예시", group = "Stay+ 전체 화면", widthDp = 1682, heightDp = 1210, apiLevel = 34, locale = "ko-rKR", showBackground = true)
@Composable
private fun GalleryWorkEditor() = PreviewFeature(PreviewScreen.WORK_EDITOR)
