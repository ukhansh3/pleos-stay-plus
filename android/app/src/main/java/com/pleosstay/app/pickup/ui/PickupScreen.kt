package com.pleosstay.app.pickup.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter

@Composable
fun PickupScreen(viewModel: PickupViewModel, onStartWithPermission: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("픽업 연결", style = MaterialTheme.typography.headlineMedium)
        Text("Waiting 없이 독립적으로 시작할 수 있습니다.")
        if (state.blocked) Text(state.safetyMessage, color = MaterialTheme.colorScheme.error)
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        val session = state.session
        if (session == null) {
            Button(onClick = onStartWithPermission, enabled = !state.busy && !state.blocked) { Text(if (state.busy) "시작 중…" else "현재 위치로 Pickup 시작") }
        } else {
            Text("상태: ${session.status}")
            Text(if (state.sharingLocation) "차량 위치 공유 중 · 약 5초 목표" else "차량 위치 공유 중지")
            state.lastLocationAt?.let { Text("마지막 차량 위치: $it") }
            state.invitation?.let { invite ->
                val bitmap = remember(invite.shareUrl) { qrBitmap(invite.shareUrl) }
                Image(bitmap.asImageBitmap(), contentDescription = "Pickup 공유 링크 QR", modifier = Modifier.size(220.dp))
                Text("참가 유효기한: ${invite.expiresAt}")
                Text("QR은 공유 안내 링크이며 스캔만으로 차량 위치가 공개되지 않습니다.")
            } ?: Button(onClick = viewModel::issueInvitation, enabled = !state.busy) { Text("60분 초대 QR 발급") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::complete, enabled = !state.busy && session.status.name == "ACTIVE") { Text("합류 완료") }
                OutlinedButton(onClick = viewModel::cancel, enabled = !state.busy && session.status.name == "ACTIVE") { Text("Pickup 취소") }
            }
            Text("Pleos TTS는 공식 SDK 검증 전 SDK_VERIFY 상태입니다.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun qrBitmap(value: String): Bitmap {
    val matrix = MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, 512, 512)
    return Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).also { bitmap ->
        for (y in 0 until 512) for (x in 0 until 512) bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    }
}
