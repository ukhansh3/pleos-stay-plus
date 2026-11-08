package com.pleosstay.app.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pleosstay.app.ui.activity.*
import com.pleosstay.app.ui.premium.*
import com.pleosstay.app.ui.theme.PleosStayPlusTheme
import com.pleosstay.app.modes.ActivityMode
import com.pleosstay.app.entertainment.GamePanel
import com.pleosstay.app.entertainment.QuizPanel
import com.pleosstay.app.entertainment.games.Game2048State
import com.pleosstay.app.entertainment.quiz.*

/** Synthetic presentation only: no runtime, admission, server, microphone or private document. */
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
@Preview(name="Entertainment · 기존 게임과 퀴즈 예시",widthDp=1682,heightDp=1210,locale="ko-rKR",apiLevel=34)
@Composable private fun EntertainmentEntryPreview()=PleosStayPlusTheme {
 Row(Modifier.fillMaxSize().background(StayColors.Background).padding(32.dp).verticalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(32.dp)) {
  Column(Modifier.weight(1f)){Text("Entertainment · 2048");GamePanel(Game2048State(listOf(2,4,0,0,8,2,0,0,0,0,4,0,0,0,0,2)),true,{},{})}
  Column(Modifier.weight(1f)){Text("상식 퀴즈");QuizPanel(QuizState(GeneralKnowledgeQuestions.all.take(5)),true,{_,_->},{},{})}
 }
}
