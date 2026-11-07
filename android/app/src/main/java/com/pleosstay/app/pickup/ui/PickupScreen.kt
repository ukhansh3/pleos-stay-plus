package com.pleosstay.app.pickup.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.pleosstay.app.R
import com.pleosstay.app.pickup.domain.*
import com.pleosstay.app.ui.premium.*

@Composable
fun PickupScreen(viewModel: PickupViewModel, onStartWithPermission: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()
    var largeQr by remember { mutableStateOf(false) }
    var guide by remember { mutableStateOf(false) }
    val session = state.session
    val invite = state.invitation
    val qr = remember(invite?.shareUrl) { invite?.let { qrBitmap(it.shareUrl) } }
    val active = session?.status == PickupSessionStatus.ACTIVE
    if(largeQr && qr != null && active) Dialog(onDismissRequest = { largeQr = false }) {
        GlassPanel {
            Text("공유 안내 QR", fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Image(qr.asImageBitmap(), "Pickup 공유 링크 QR 확대", Modifier.sizeIn(maxWidth = 580.dp,maxHeight = 580.dp).aspectRatio(1f).background(Color.White))
            Text("유효기한 · ${invite?.expiresAt}", color = StayColors.Secondary)
            PremiumAction("닫기", { largeQr = false })
        }
    }
    if(guide) AlertDialog(onDismissRequest = { guide = false }, title = { Text("QR 공유 안내") },
        text = { Text("운전자 휴대폰으로 QR을 스캔해 공유 안내 페이지를 엽니다. 링크를 픽업 대상 한 명에게 전달하고, 대상이 링크에서 참가에 동의해야 합니다. 스캔만으로 차량 위치가 공개되지 않습니다. 재발급하면 기존 초대와 참가 권한이 폐기됩니다.") },
        confirmButton = { TextButton(onClick = { guide = false }) { Text("확인") } })
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 28.dp), verticalArrangement = Arrangement.spacedBy(22.dp),
        contentPadding = PaddingValues(bottom = 32.dp)) {
        item { FeatureHeader("픽업 공유", "한 번의 연결로, 더 편안하게 만나세요") }
        if(state.blocked) item { NoticePanel("정차 확인이 필요합니다", state.safetyMessage, true) }
        state.locationMessage?.let { item { NoticePanel("위치 수신 안내", it, true) } }
        state.error?.let { item { NoticePanel("요청을 처리하지 못했습니다", it, true) } }
        if(session == null) item {
            ResponsivePanels(left = {
                GlassPanel(Modifier.fillMaxWidth()) {
                    Text("새 Pickup 시작", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text("Waiting 없이 독립적으로 시작합니다. 실제 초대가 발급되면 참가 QR이 표시됩니다.", fontSize = 22.sp, lineHeight = 32.sp, color = StayColors.Secondary)
                    Text("참가자 1명 · 초대 유효시간 60분", color = StayColors.Blue, fontSize = 22.sp)
                    PremiumAction(if(state.busy) "시작 중…" else "현재 위치로 Pickup 시작", onStartWithPermission, Modifier.fillMaxWidth(), !state.busy && !state.blocked)
                }
            }, right = {
                PhotoPanel(R.drawable.pickup_visual, Modifier.fillMaxWidth().height(360.dp)) {
                    Spacer(Modifier.weight(1f))
                    Text("함께 만날 준비", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text("배경 이미지는 참가용 QR이 아닙니다.", color = StayColors.Secondary, fontSize = 19.sp)
                }
            })
        } else item {
            ResponsivePanels(left = {
                GlassPanel(Modifier.fillMaxWidth()) {
                    Text("초대 QR", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text("실제 초대 링크로 참가자를 연결합니다.", fontSize = 20.sp, color = StayColors.Secondary)
                    if(qr != null && active) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Image(qr.asImageBitmap(), "Pickup 공유 링크 QR", Modifier.size(330.dp).background(Color.White))
                        }
                        Text("참가 유효기한 · ${invite?.expiresAt}", fontSize = 20.sp, color = StayColors.Blue)
                        PremiumAction("QR 크게 보기", { largeQr = true }, Modifier.fillMaxWidth(), secondary = true)
                        PremiumAction("QR 재발급 · 기존 참가 권한 폐기", viewModel::issueInvitation, Modifier.fillMaxWidth(), !state.busy, true)
                    } else if(active) {
                        NoticePanel("아직 초대가 없습니다", "초대를 발급하면 실제 참가용 QR이 표시됩니다.")
                        PremiumAction("60분 초대 QR 발급", viewModel::issueInvitation, Modifier.fillMaxWidth(), !state.busy)
                    } else Text("Pickup이 종료되어 초대를 사용할 수 없습니다.", fontSize = 22.sp)
                    TextButton(onClick = { guide = true }) { Text("QR 공유 안내", fontSize = 20.sp) }
                }
            }, right = {
                Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
                    GlassPanel(Modifier.fillMaxWidth()) {
                        Text("Pickup 현황", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        InfoLine("세션 상태", session.status.label())
                        InfoLine("참가자", session.participant?.displayName ?: "연결 대기 중")
                        InfoLine("참가 상태", session.participant?.status?.label() ?: "아직 참가하지 않았습니다")
                        Text(if(state.sharingLocation) "차량 위치 공유 중 · 약 5초 목표" else "차량 위치 공유 중지", color = StayColors.Blue, fontSize = 20.sp)
                        Text(if(state.syncing) "참가자 상태 확인 중…" else "참가자 상태 자동 동기화", color = StayColors.Secondary, fontSize = 19.sp)
                        state.lastLocationAt?.let { Text("최근 위치 갱신 · $it", color = StayColors.Secondary) }
                        if(state.proximityCandidate) Text("20m 이내 30초 조건 충족\n직접 합류를 확인해 주세요.", fontSize = 22.sp, color = StayColors.Blue)
                        Text("자동 근접 판정은 알림입니다. 합류 완료는 직접 확정합니다.", color = StayColors.Secondary, fontSize = 18.sp)
                        PremiumAction("합류 완료", viewModel::complete, Modifier.fillMaxWidth(), !state.busy && active)
                        PremiumAction("Pickup 취소", viewModel::cancel, Modifier.fillMaxWidth(), !state.busy && active, true)
                    }
                    NoticePanel("음성·연결 안내", state.ttsMessage)
                }
            })
        }
    }
}
private fun PickupSessionStatus.label() = when(this) {
    PickupSessionStatus.ACTIVE -> "진행 중"; PickupSessionStatus.COMPLETED -> "합류 완료"; PickupSessionStatus.CANCELLED -> "취소";
    PickupSessionStatus.ENDED -> "종료"; PickupSessionStatus.EXPIRED -> "만료"
}
private fun PickupParticipantStatus.label() = when(this) {
    PickupParticipantStatus.JOINED -> "참가함"; PickupParticipantStatus.DEPARTED -> "출발함"; PickupParticipantStatus.NEARBY -> "근처 도착";
    PickupParticipantStatus.ARRIVED -> "도착함"; PickupParticipantStatus.CANCELLED -> "참가 취소"
}
private fun qrBitmap(value: String): Bitmap {
    val matrix = MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, 512, 512)
    return Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888).also { bitmap ->
        for(y in 0 until 512) for(x in 0 until 512) bitmap.setPixel(x,y,if(matrix[x,y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    }
}
