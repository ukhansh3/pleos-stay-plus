package com.pleosstay.app.realtime
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pleosstay.app.ui.premium.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pleosstay.app.BuildConfig
import com.pleosstay.app.waiting.safety.WaitingSafetyGateway
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Memory-only connection UI state survives Activity recreation; credentials never enter SavedState. */
class RealtimeUiSession:ViewModel() {
 var url by mutableStateOf(if(BuildConfig.DEBUG) "http://10.0.2.2:3010/api/v1/realtime/" else "")
 var credential by mutableStateOf("");var authUrl by mutableStateOf("");var publicKey by mutableStateOf("")
 var local by mutableStateOf(BuildConfig.DEBUG);var ready by mutableStateOf(false);var error by mutableStateOf("");var loaded=false
}
@Composable fun RealtimeEntry(context: Context, safety: WaitingSafetyGateway,
 foreground: Boolean = true,
 visible:Boolean=true,
 presentation:@Composable (@Composable ()->Unit)->Unit={it()},
 managedWorkspace: (@Composable (FileTransferViewModel, String) -> (@Composable () -> Unit))? = null,
 workspace: (@Composable (FileTransferViewModel, String) -> Unit)? = null) {
 val app=context.applicationContext
 val configStore=remember { RealtimeTokenStore(app,"realtime-local-config.bin") }
 val session:RealtimeUiSession=viewModel(key="realtime-connection-ui")
 var url by session::url;var credential by session::credential;var authUrl by session::authUrl
 var publicKey by session::publicKey;var local by session::local;var ready by session::ready;var error by session::error
 val scope=rememberCoroutineScope()
 LaunchedEffect(Unit){if(session.loaded)return@LaunchedEffect;session.loaded=true;try{val saved=withContext(Dispatchers.IO){
  // Completed downloads are a cache, never a cross-session authority grant.
  File(app.noBackupFilesDir,"realtime/downloads").listFiles()?.forEach{it.delete()}
  configStore.read()
 };if(saved!=null){val j=JSONObject(saved);url=j.getString("url");credential=j.optString("token");authUrl=j.optString("authUrl");publicKey=j.optString("publicKey");local=BuildConfig.DEBUG && j.optBoolean("local",true)}}catch(e:Exception){error="연결 설정 복원 실패"}}
 val safetyState by safety.state.collectAsState()
 if(!safetyState.canUse){if(visible) presentation {Column(Modifier.fillMaxSize().padding(28.dp)){
  Text("자료함 연결",fontSize=38.sp)
  NoticePanel("정차 확인 필요", "정차 상태 확인 후 연결할 수 있습니다.",true)
 }};return}
 if(!ready){if(visible) presentation {Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),verticalArrangement=Arrangement.spacedBy(22.dp)){
  Text("자료함 연결",fontSize=38.sp)
  FeaturePanel(Modifier.widthIn(max=760.dp).fillMaxWidth()) {
  Text("PC 연결 설정",fontSize=28.sp)
  if(BuildConfig.DEBUG){Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){Checkbox(local,{local=it});Text("로컬 서버 연결")}}
  OutlinedTextField(url,{url=it},label={Text("서버 주소")},modifier=Modifier.fillMaxWidth(),singleLine=true)
  if(local) OutlinedTextField(credential,{credential=it},label={Text("로컬 연결 토큰")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth(),singleLine=true)
  else {
   OutlinedTextField(authUrl,{authUrl=it},label={Text("인증 서버 주소")},modifier=Modifier.fillMaxWidth(),singleLine=true)
   OutlinedTextField(publicKey,{publicKey=it},label={Text("인증 공개 키")},modifier=Modifier.fillMaxWidth(),singleLine=true)
  }
  if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
  Button(onClick={scope.launch{try{
   if(local){require(BuildConfig.DEBUG && credential.length>=32);require(java.net.URI(url).host in listOf("10.0.2.2","127.0.0.1","localhost"))}
   else {require(java.net.URI(url).scheme=="https" && java.net.URI(authUrl).scheme=="https");require(publicKey.isNotBlank())}
   RealtimeApi(url){credential}
   withContext(Dispatchers.IO){configStore.write(JSONObject().put("url",url).put("token",if(local)credential else "").put("local",local).put("authUrl",authUrl).put("publicKey",publicKey).toString())};ready=true
  }catch(e:Exception){error="서버 주소와 연결 정보를 확인해 주세요."}}}){Text("연결")}
 }}};return}
 val provider=remember(authUrl,publicKey,local){if(local)null else RealtimeIdentityProvider(authUrl,publicKey,RealtimeTokenStore(app,"realtime-auth-"+java.security.MessageDigest.getInstance("SHA-256").digest(authUrl.toByteArray()).joinToString(""){"%02x".format(it)}+".bin"))}
 val modelKey=remember(url,authUrl,local,credential){java.security.MessageDigest.getInstance("SHA-256").digest((url+authUrl+local+credential).toByteArray()).joinToString(""){"%02x".format(it)}}
 val model:FileTransferViewModel=viewModel(key="realtime-"+modelKey,factory=remember(url,credential,provider){object:ViewModelProvider.Factory{
  @Suppress("UNCHECKED_CAST") override fun <T:ViewModel> create(modelClass:Class<T>):T=FileTransferViewModel(RealtimeApi(url){provider?.validToken() ?: credential},safety,LocalFileStore(app),File(app.noBackupFilesDir,"realtime/downloads")){provider?.resetAfterRevocation();withContext(Dispatchers.IO){RealtimePinStore(app).clearAfterUserVerification()}} as T
 }})
 DisposableEffect(model){onDispose{model.lock()}}
 // Construct state/ActivityResult registrations, never render workspace content here.
 val protectedContent = managedWorkspace?.invoke(model,url)
 LaunchedEffect(foreground,model) { if(!foreground) model.lock() }
 if(!visible)return
 presentation {
 if(!foreground){NoticePanel("문서 화면 잠김","PIN을 다시 확인해 주세요.")}else
 RealtimeLockedScreen(model,remember{RealtimePinStore(app)}) {
  // Every content path remains inside the original verified PIN gate.
  if(protectedContent!=null) protectedContent() else if(workspace==null) FileTransferScreen(model) else workspace(model,url)
 }
}
}
