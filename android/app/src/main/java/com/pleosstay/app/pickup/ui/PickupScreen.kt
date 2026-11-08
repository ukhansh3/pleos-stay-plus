package com.pleosstay.app.pickup.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.pleosstay.app.pickup.domain.*
import com.pleosstay.app.ui.premium.*
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun PickupScreen(viewModel: PickupViewModel, onStartWithPermission: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()
    var largeQr by remember { mutableStateOf(false) }
    var guide by remember { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf<String?>(null) }
    val now by produceState(System.currentTimeMillis()) { while (true) { delay(1_000); value = System.currentTimeMillis() } }
    val session = state.session
    val invite = state.invitation
    val active = session?.status == PickupSessionStatus.ACTIVE
    val inviteValid = active && invite != null && runCatching { Instant.parse(invite.expiresAt).toEpochMilli() > now }.getOrDefault(false)
    val qr = remember(invite?.shareUrl, inviteValid) { if (inviteValid) invite?.let { runCatching { qrBitmap(it.shareUrl) }.getOrNull() } else null }
    if (largeQr && qr != null && !state.blocked) Dialog(onDismissRequest = { largeQr = false }) {
        GlassPanel {
            Text("초대 QR", fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Image(qr.asImageBitmap(), "Pickup 공유 링크 QR 확대", Modifier.sizeIn(maxWidth = 580.dp, maxHeight = 580.dp).aspectRatio(1f).background(Color.White))
            Text("유효기한 ${invite?.expiresAt?.timeLabel()}", color = StayColors.Secondary)
            PremiumAction("닫기", { largeQr = false })
        }
    }
    if (guide && !state.blocked) AlertDialog(onDismissRequest = { guide = false }, title = { Text("QR 공유 안내") },
        text = { Text("휴대폰으로 QR을 스캔한 뒤 초대 링크를 픽업 대상 한 명에게 전달하세요. 상대방이 참가에 동의하면 차량 위치가 공유됩니다.") },
        confirmButton = { TextButton(onClick = { guide = false }) { Text("확인") } })
    if (confirmation != null && !state.blocked && active) AlertDialog(onDismissRequest = { confirmation = null },
        title = { Text(when (confirmation) { "reissue" -> "QR을 다시 발급할까요?"; "complete" -> "합류를 완료할까요?"; else -> "픽업을 취소할까요?" }) },
        text = { Text(if (confirmation == "reissue") "기존 초대 링크와 참가자 연결이 해제됩니다." else "위치 공유와 참가자 연결이 종료됩니다.") },
        confirmButton = { TextButton(onClick = {
            val action = confirmation; confirmation = null
            when (action) { "reissue" -> viewModel.issueInvitation(); "complete" -> viewModel.complete(); else -> viewModel.cancel() }
        }) { Text(when (confirmation) { "reissue" -> "재발급"; "complete" -> "완료"; else -> "취소하기" }) } },
        dismissButton = { TextButton(onClick = { confirmation = null }) { Text("돌아가기") } })
    BoxWithConstraints(modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 16.dp)) {
        val wide = maxWidth >= 1000.dp
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Text("픽업 공유", fontSize = 38.sp, lineHeight = 46.sp, fontWeight = FontWeight.Bold)
            if (state.blocked) {
                FeaturePanel(Modifier.fillMaxWidth()) {
                    Text("정차 확인이 필요합니다", fontSize = 27.sp, fontWeight = FontWeight.Bold)
                    Text(state.safetyMessage, fontSize = 21.sp, color = StayColors.Secondary)
                }
            } else {
                val qrPanel: @Composable () -> Unit = {
                    PickupInvitationPanel(state, qr, inviteValid, { largeQr = true }, { guide = true }, {
                        if (invite == null) viewModel.issueInvitation() else confirmation = "reissue"
                    })
                }
                val statusPanel: @Composable () -> Unit = {
                    PickupStatusPanel(state, onStartWithPermission, { confirmation = "complete" }, { confirmation = "cancel" })
                }
                if (wide) Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    FeaturePanel(Modifier.weight(1f).fillMaxHeight()) {
                        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) { qrPanel() }
                    }
                    FeaturePanel(Modifier.weight(1.4f).fillMaxHeight()) {
                        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) { statusPanel() }
                    }
                    FeaturePanel(Modifier.weight(.78f).fillMaxHeight()) { PickupSteps(Modifier.verticalScroll(rememberScrollState())) }
                } else Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                    FeaturePanel(Modifier.fillMaxWidth()) { statusPanel() }
                    FeaturePanel(Modifier.fillMaxWidth()) { qrPanel() }
                    FeaturePanel(Modifier.fillMaxWidth()) { PickupSteps() }
                }
            }
        }
    }
}

@Composable
private fun PickupInvitationPanel(state: PickupUiState, qr: Bitmap?, inviteValid: Boolean, enlarge: () -> Unit, guide: () -> Unit, issue: () -> Unit) {
    val active = state.session?.status == PickupSessionStatus.ACTIVE
    Text("Pickup 초대", fontSize = 27.sp, fontWeight = FontWeight.Bold)
    Text("참가자 1명 · 60분 유효", color = StayColors.Secondary, fontSize = 18.sp)
    BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = 300.dp).background(StayColors.Background), contentAlignment = Alignment.Center) {
        if (qr != null) Image(qr.asImageBitmap(), "Pickup 공유 링크 QR", Modifier.size(minOf(maxWidth - 20.dp, 340.dp)).background(Color.White))
        else Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            StayIcon("PICKUP", Modifier.size(72.dp), StayColors.Secondary)
            Text(when { state.session == null -> "초대 QR 발급 전"; !active -> "종료된 초대"; state.invitation != null && !inviteValid -> "초대가 만료되었습니다."; else -> "초대를 발급하세요." }, fontSize = 21.sp, color = StayColors.Secondary)
        }
    }
    if (qr != null) {
        Text("유효기한 ${state.invitation?.expiresAt?.timeLabel()}", color = StayColors.Secondary, fontSize = 18.sp)
        PremiumAction("QR 크게 보기", enlarge, Modifier.fillMaxWidth(), secondary = true)
    }
    if (active) PremiumAction(if (state.invitation == null) "QR 발급" else "QR 재발급", issue, Modifier.fillMaxWidth(), !state.busy, true)
    TextButton(onClick = guide, modifier = Modifier.heightIn(min = 56.dp)) { Text("QR 공유 안내", fontSize = 20.sp) }
}

@Composable
private fun PickupStatusPanel(state: PickupUiState, start: () -> Unit, complete: () -> Unit, cancel: () -> Unit) {
    val session = state.session
    val active = session?.status == PickupSessionStatus.ACTIVE
    Column(Modifier.testTag("pickup_status"), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Text("픽업 진행 현황", fontSize = 27.sp, fontWeight = FontWeight.Bold)
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 20.sp) }
        state.locationMessage?.let { Text(it, color = StayColors.Warning, fontSize = 19.sp) }
        StayCard(Modifier.fillMaxWidth()) {
            Text("참가 상태", color = StayColors.Secondary, fontSize = 18.sp)
            Spacer(Modifier.height(12.dp))
            Text(session?.participant?.displayName?.takeIf { it.isNotBlank() } ?: "아직 참가자가 없습니다.", fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold)
            session?.participant?.let { Text(it.status.label(), color = StayColors.Blue, fontSize = 22.sp) }
        }
        if (session != null) {
            InfoLine("픽업", session.status.label())
            InfoLine("위치 공유", if (state.sharingLocation) "공유 중" else "공유 중지")
            if (state.syncing) Text("참가자 상태 확인 중…", color = StayColors.Secondary, fontSize = 19.sp)
            state.lastLocationAt?.let { InfoLine("최근 갱신", it.timeLabel()) }
            if (state.proximityCandidate && active) Text("픽업 대상이 가까이 있습니다. 합류를 확인하세요.", color = StayColors.Blue, fontSize = 22.sp)
            if (active) {
                PremiumAction("합류 완료", complete, Modifier.fillMaxWidth(), !state.busy)
                PremiumAction("Pickup 취소", cancel, Modifier.fillMaxWidth(), !state.busy, true)
            }
        } else PremiumAction(if (state.busy) "시작 중…" else "현재 위치로 Pickup 시작", start, Modifier.fillMaxWidth(), !state.busy)
    }
}

@Composable
private fun PickupSteps(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(30.dp)) {
        Text("참가 절차", fontSize = 27.sp, fontWeight = FontWeight.Bold)
        listOf("차량에서 Pickup 시작", "휴대폰으로 초대 QR 스캔", "링크 전달 및 참가 동의", "출발·도착 상태 확인").forEachIndexed { index, step ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("0${index + 1}", color = StayColors.Secondary, fontSize = 17.sp)
                Text(step, fontSize = 21.sp, lineHeight = 30.sp)
                HorizontalDivider(color = StayColors.Border)
            }
        }
    }
}
private fun PickupSessionStatus.label() = when (this) {
    PickupSessionStatus.ACTIVE -> "진행 중"; PickupSessionStatus.COMPLETED -> "합류 완료"; PickupSessionStatus.CANCELLED -> "취소";
    PickupSessionStatus.ENDED -> "종료"; PickupSessionStatus.EXPIRED -> "만료"
}
private fun PickupParticipantStatus.label() = when (this) {
    PickupParticipantStatus.JOINED -> "참가함"; PickupParticipantStatus.DEPARTED -> "출발함"; PickupParticipantStatus.NEARBY -> "근처 도착";
    PickupParticipantStatus.ARRIVED -> "도착함"; PickupParticipantStatus.CANCELLED -> "참가 취소"
}
private fun String.timeLabel() = runCatching { Instant.parse(this).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM.dd HH:mm")) }.getOrDefault("시간 확인 필요")
private fun qrBitmap(value: String): Bitmap {
    val matrix = MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, 512, 512)
    return Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).also { bitmap ->
        for (y in 0 until 512) for (x in 0 until 512) bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    }
}
