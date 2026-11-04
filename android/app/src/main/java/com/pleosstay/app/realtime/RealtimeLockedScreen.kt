package com.pleosstay.app.realtime
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
/** Integrating host provides the existing Waiting safety-backed model and this local profile lock. */
@Composable fun RealtimeLockedScreen(model: FileTransferViewModel, pins: RealtimePinStore) {
 val state by model.state.collectAsState();var pin by remember { mutableStateOf("") };var confirm by remember { mutableStateOf("") };var message by remember { mutableStateOf("") };var busy by remember { mutableStateOf(false) };val scope=rememberCoroutineScope()
 if(!state.allowed){Text("정차 상태가 확인되지 않아 파일 화면이 잠겼습니다.");return}
 if(state.unlocked){FileTransferScreen(model);return}
 Column {
  Text(if(pins.isConfigured())"파일 화면 PIN 잠금" else "공유 차량 파일 보호 PIN 설정 (6~12자리)")
  OutlinedTextField(pin,{pin=it},visualTransformation=PasswordVisualTransformation(),label={Text("PIN")})
  if(!pins.isConfigured())OutlinedTextField(confirm,{confirm=it},visualTransformation=PasswordVisualTransformation(),label={Text("PIN 확인")})
  Text(message)
  Button(enabled=!busy,onClick={busy=true;scope.launch{try{val accepted=withContext(Dispatchers.IO){if(!pins.isConfigured()){require(pin==confirm){"PIN_MISMATCH"};pins.configure(pin)};pins.verify(pin)};if(accepted)model.unlockAfterUserVerification() else message="PIN이 일치하지 않습니다"}catch(e:Exception){message=e.message ?: "잠금 오류"}finally{pin="";confirm="";busy=false}}}){Text("잠금 해제")}
 }
}
