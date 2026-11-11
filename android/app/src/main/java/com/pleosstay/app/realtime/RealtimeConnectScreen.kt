package com.pleosstay.app.realtime

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pleosstay.app.BuildConfig
import com.pleosstay.app.ui.premium.*
import kotlinx.coroutines.launch

/** No server, token, public-key or test-input controls exist on the normal first-run surface. */
@Composable internal fun RealtimeConnectScreen(error: String?, localTest: Boolean, connect: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Text("파일 작업", fontSize = 38.sp)
        FeaturePanel(Modifier.widthIn(max = 760.dp).fillMaxWidth().testTag("realtime_connect")) {
            StayIcon("WORK", Modifier.size(56.dp), StayColors.Blue)
            Text("내 컴퓨터 연결", fontSize = 30.sp)
            Text("Windows Stay+에서 차량에 표시된 8자리 코드를 입력하세요.", color = StayColors.Secondary)
            Text("PC와 차량에서 서로 확인한 뒤 연결됩니다.", color = StayColors.Secondary)
            if (localTest) Text("개발용 테스트 연결", color = StayColors.Warning)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            PremiumAction(if (error == null) "PC 연결하기" else "다시 시도", connect)
        }
    }
}

/** Deliberately reachable only through Debug Settings, never offered as a connection step. */
@Composable internal fun RealtimeDeveloperSettings(initial: RealtimeConnectionConfig, back: () -> Unit,
    save: suspend (RealtimeConnectionConfig) -> Unit) {
    if (!BuildConfig.DEBUG) return
    var url by remember { mutableStateOf(initial.url) }
    var auth by remember { mutableStateOf(initial.authUrl) }
    var key by remember { mutableStateOf(initial.publicKey) }
    var token by remember { mutableStateOf(initial.credential) }
    var local by remember { mutableStateOf(initial.local) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Text("개발자 연결 설정", fontSize = 30.sp)
        FeaturePanel(Modifier.widthIn(max = 760.dp).fillMaxWidth()) {
            Row { Checkbox(local, { local = it; error = null }, enabled = !busy); Text("개발 전용 테스트 인증") }
            OutlinedTextField(url, { url = it; error = null }, label = { Text("서버 주소") }, singleLine = true, enabled = !busy)
            if (local) OutlinedTextField(token, { token = it; error = null }, label = { Text("로컬 연결 토큰") },
                visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !busy)
            else {
                OutlinedTextField(auth, { auth = it; error = null }, label = { Text("인증 서버 주소") }, singleLine = true, enabled = !busy)
                OutlinedTextField(key, { key = it; error = null }, label = { Text("인증 공개 키") }, singleLine = true, enabled = !busy)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            PremiumAction("설정 저장", {
                val next = RealtimeConnectionConfig(url.trim(), auth.trim(), key.trim(), local, token)
                error = next.error()
                if (error == null) scope.launch {
                    busy = true
                    try { save(next) }
                    catch (c: kotlinx.coroutines.CancellationException) { throw c }
                    catch (_: Exception) { error = "연결 설정을 저장하지 못했습니다. 다시 시도해 주세요." }
                    finally { busy = false }
                }
            }, enabled = !busy)
            PremiumAction("돌아가기", back, enabled = !busy, secondary = true)
        }
    }
}
