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
 val focus = androidx.compose.ui.platform.LocalFocusManager.current
 val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
 val state by model.state.collectAsState();var pin by remember { mutableStateOf("") };var confirm by remember { mutableStateOf("") };var message by remember { mutableStateOf("") };var busy by remember { mutableStateOf(false) };val scope=rememberCoroutineScope()
 if(!state.allowed){NoticePanel("파일 화면 잠김","정차 상태가 확인되지 않아 파일 화면이 잠겼습니다.",true);return}
 if(state.unlocked){content();return}
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),verticalArrangement=Arrangement.spacedBy(22.dp)) {
  Text("자료함 PIN",style=MaterialTheme.typography.headlineLarge)
  FeaturePanel(Modifier.widthIn(max=640.dp).fillMaxWidth()) {
  val configured = pins.isConfigured()
  Text(if (configured) "자료함 PIN을 입력해 주세요." else "숫자 6~12자리로 PIN을 만들어 주세요.")
  OutlinedTextField(pin,{pin=it},visualTransformation=PasswordVisualTransformation(),label={Text("PIN")},modifier=Modifier.fillMaxWidth(),singleLine=true,keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.NumberPassword))
  if(!configured)OutlinedTextField(confirm,{confirm=it},visualTransformation=PasswordVisualTransformation(),label={Text("PIN 확인")},modifier=Modifier.fillMaxWidth(),singleLine=true,keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.NumberPassword))
  if(message.isNotBlank())Text(message,color=MaterialTheme.colorScheme.error)
  Button(enabled = !busy, onClick = {
   val submittedPin = pin
   val repeatedPin = confirm
   busy = true
   scope.launch {
    try {
     val accepted = withContext(Dispatchers.IO) {
      if (!pins.isConfigured()) {
       require(submittedPin == repeatedPin) { "PIN_MISMATCH" }
       require(submittedPin.matches(Regex("[0-9]{6,12}"))) { "PIN_FORMAT" }
       pins.configure(submittedPin)
      }
      pins.verify(submittedPin)
     }
     if (accepted) {
      focus.clearFocus(force = true)
      keyboard?.hide()
      model.unlockAfterUserVerification()
     }
     else message = "PIN이 맞지 않아요. 다시 입력해 주세요."
    } catch (e: kotlinx.coroutines.CancellationException) {
     throw e
    } catch (e: Exception) {
     message = when (e.message) {
      "PIN_MISMATCH" -> "두 PIN이 달라요. 다시 입력해 주세요."
      "PIN_FORMAT" -> "숫자 6~12자리를 입력해 주세요."
      "PIN_RATE_LIMITED" -> "입력이 여러 번 틀렸어요. 잠시 후 다시 시도해 주세요."
      else -> "PIN을 확인할 수 없어요. 다시 시도해 주세요."
     }
    } finally {
     pin = ""; confirm = ""; busy = false
    }
   }
  }) { Text(if (configured) "잠금 해제" else "PIN 설정") }
  }
 }
}
