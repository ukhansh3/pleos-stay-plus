package com.pleosstay.app.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pleosstay.app.ui.activity.*
import com.pleosstay.app.ui.premium.*
import com.pleosstay.app.ui.theme.PleosStayPlusTheme
import com.pleosstay.app.modes.ActivityMode

/** Synthetic presentation only: no runtime, admission, server or private document. */
@Preview(name="활동 전환 · 예시",widthDp=1682,heightDp=1210,locale="ko-rKR",apiLevel=34)
@Composable private fun ActivityTransitionPreview()=PleosStayPlusTheme {
 Column(Modifier.fillMaxSize().background(StayColors.Background).padding(40.dp)) {
  FeaturePanel(Modifier.widthIn(max=760.dp)){Text("휴식을 종료하시겠습니까?");ActivitySwitchPanel(ActivityMode.REST,ActivityMode.STUDY,false,{},{})}
 }
}
@Preview(name="Study 저장 실패 · 예시",widthDp=960,heightDp=720,locale="ko-rKR",apiLevel=34)
@Composable private fun StudySaveFailurePreview()=PleosStayPlusTheme {
 Column(Modifier.fillMaxSize().background(StayColors.Background).padding(32.dp)){ActivityRecoveryPanel("메모 입력은 유지됩니다. 연결과 저장공간을 확인해 주세요.",false,{},{})}
}
@Preview(name="Work 복구 · 예시",widthDp=960,heightDp=720,locale="ko-rKR",apiLevel=34)
@Composable private fun WorkRecoveryPreview()=PleosStayPlusTheme {
 Column(Modifier.fillMaxSize().background(StayColors.Background).padding(32.dp)){ActivityRecoveryPanel("암호화 초안을 저장하지 못했습니다. 외부 DOCX는 변경하지 않았습니다.",false,{},{})}
}
