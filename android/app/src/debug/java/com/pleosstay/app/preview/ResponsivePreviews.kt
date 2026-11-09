package com.pleosstay.app.preview
import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import com.pleosstay.app.ui.activity.*
import com.pleosstay.app.ui.premium.PremiumAction

// Real production components with synthetic failure text; not the integrated Work error host.
@Composable internal fun ResponsiveWorkRecovery() {
    JourneyWork(2)
    ActivityModal("초안 저장 확인",false,{}) {
        ActivityRecoveryPanel("초안을 저장하지 못했습니다. 작업 내용을 유지한 채 다시 시도해 주세요.",false,{},{})
    }
}
@Composable internal fun ResponsiveLongModal() {
    JourneyHome(true)
    ActivityModal("긴 안내 스크롤 스트레스 테스트",false,{}) {
        repeat(12) { Text("${it+1}. 저장 중 오류가 발생하면 작업 내용을 유지하고 다시 시도할 수 있습니다. 이 문구는 긴 안내 배치 검증을 위한 테스트 데이터입니다.") }
        PremiumAction("다시 시도",{})
        PremiumAction("작업으로 돌아가기",{},secondary=true)
    }
}
