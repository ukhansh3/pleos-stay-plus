package com.pleosstay.app.realtime
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pleosstay.app.ui.premium.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
/** Integrating host provides the existing Waiting safety-backed model and this local profile lock. */
@Composable fun RealtimeLockedScreen(model: FileTransferViewModel, pins: RealtimePinStore,
 content: @Composable () -> Unit = { FileTransferScreen(model) }) {
 val state by model.state.collectAsState();var pin by remember { mutableStateOf("") };var confirm by remember { mutableStateOf("") };var message by remember { mutableStateOf("") };var busy by remember { mutableStateOf(false) };val scope=rememberCoroutineScope()
 if(!state.allowed){NoticePanel("파일 화면 잠김","정차 상태가 확인되지 않아 파일 화면이 잠겼습니다.",true);return}
 if(state.unlocked){content();return}
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),verticalArrangement=Arrangement.spacedBy(22.dp)) {
  Text("자료함 PIN",style=MaterialTheme.typography.headlineLarge)
  FeaturePanel(Modifier.widthIn(max=640.dp).fillMaxWidth()) {
  Text(if(pins.isConfigured())"파일 화면 PIN 잠금" else "PIN 설정 · 6~12자리")
  OutlinedTextField(pin,{pin=it},visualTransformation=PasswordVisualTransformation(),label={Text("PIN")},modifier=Modifier.fillMaxWidth(),singleLine=true,keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.NumberPassword))
  if(!pins.isConfigured())OutlinedTextField(confirm,{confirm=it},visualTransformation=PasswordVisualTransformation(),label={Text("PIN 확인")},modifier=Modifier.fillMaxWidth(),singleLine=true,keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.NumberPassword))
  if(message.isNotBlank())Text(message,color=MaterialTheme.colorScheme.error)
  Button(enabled=!busy,onClick={busy=true;scope.launch{try{val accepted=withContext(Dispatchers.IO){if(!pins.isConfigured()){require(pin==confirm){"PIN_MISMATCH"};pins.configure(pin)};pins.verify(pin)};if(accepted)model.unlockAfterUserVerification() else message="PIN이 일치하지 않습니다"}catch(e:Exception){message=if(e.message=="PIN_MISMATCH")"PIN이 일치하지 않습니다." else "PIN을 확인해 주세요. 6~12자리를 입력할 수 있습니다."}finally{pin="";confirm="";busy=false}}}){Text("잠금 해제")}
  }
 }
}
