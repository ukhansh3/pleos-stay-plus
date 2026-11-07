package com.pleosstay.app.realtime
import android.content.Context
import androidx.compose.foundation.layout.Column
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

@Composable fun RealtimeEntry(context: Context, safety: WaitingSafetyGateway,
 foreground: Boolean = true,
 workspace: (@Composable (FileTransferViewModel, String) -> Unit)? = null) {
 val app=context.applicationContext
 val configStore=remember { RealtimeTokenStore(app,"realtime-local-config.bin") }
 var url by remember { mutableStateOf(if(BuildConfig.DEBUG) "http://10.0.2.2:3010/api/v1/realtime/" else "") }
 var credential by remember { mutableStateOf("") }
 var authUrl by remember { mutableStateOf("") }
 var publicKey by remember { mutableStateOf("") }
 var local by remember { mutableStateOf(BuildConfig.DEBUG) }
 var ready by remember { mutableStateOf(false) }
 var error by remember { mutableStateOf("") }
 val scope=rememberCoroutineScope()
 LaunchedEffect(Unit){try{val saved=withContext(Dispatchers.IO){
  // Completed downloads are a cache, never a cross-session authority grant.
  File(app.noBackupFilesDir,"realtime/downloads").listFiles()?.forEach{it.delete()}
  configStore.read()
 };if(saved!=null){val j=JSONObject(saved);url=j.getString("url");credential=j.optString("token");authUrl=j.optString("authUrl");publicKey=j.optString("publicKey");local=BuildConfig.DEBUG && j.optBoolean("local",true)}}catch(e:Exception){error="연결 설정 복원 실패"}}
 val safetyState by safety.state.collectAsState()
 if(!safetyState.canUse){Text("정차 상태 확인 전에는 파일 전송 설정을 조작할 수 없습니다.");return}
 if(!ready){Column{
  Text("Stay+ 연결 설정")
  if(BuildConfig.DEBUG){Checkbox(local,{local=it});Text("LOCAL FAKE 테스트 모드")}
  OutlinedTextField(url,{url=it},label={Text("Realtime 서버 주소")})
  if(local) OutlinedTextField(credential,{credential=it},label={Text("차량 테스트 토큰")},visualTransformation=PasswordVisualTransformation())
  else {
   OutlinedTextField(authUrl,{authUrl=it},label={Text("Supabase HTTPS 주소")})
   OutlinedTextField(publicKey,{publicKey=it},label={Text("Supabase 공개 키")})
  }
  Text(error)
  Button(onClick={scope.launch{try{
   if(local){require(BuildConfig.DEBUG && credential.length>=32);require(java.net.URI(url).host in listOf("10.0.2.2","127.0.0.1","localhost"))}
   else {require(java.net.URI(url).scheme=="https" && java.net.URI(authUrl).scheme=="https");require(publicKey.isNotBlank())}
   RealtimeApi(url){credential}
   withContext(Dispatchers.IO){configStore.write(JSONObject().put("url",url).put("token",if(local)credential else "").put("local",local).put("authUrl",authUrl).put("publicKey",publicKey).toString())};ready=true
  }catch(e:Exception){error="설정 확인 필요: HTTPS 주소 또는 로컬 테스트 토큰을 확인하세요"}}}){Text("설정 저장 / 파일 화면 진입")}
 };return}
 val provider=remember(authUrl,publicKey,local){if(local)null else RealtimeIdentityProvider(authUrl,publicKey,RealtimeTokenStore(app,"realtime-auth-"+java.security.MessageDigest.getInstance("SHA-256").digest(authUrl.toByteArray()).joinToString(""){"%02x".format(it)}+".bin"))}
 val modelKey=remember(url,authUrl,local,credential){java.security.MessageDigest.getInstance("SHA-256").digest((url+authUrl+local+credential).toByteArray()).joinToString(""){"%02x".format(it)}}
 val model:FileTransferViewModel=viewModel(key="realtime-"+modelKey,factory=remember(url,credential,provider){object:ViewModelProvider.Factory{
  @Suppress("UNCHECKED_CAST") override fun <T:ViewModel> create(modelClass:Class<T>):T=FileTransferViewModel(RealtimeApi(url){provider?.validToken() ?: credential},safety,LocalFileStore(app),File(app.noBackupFilesDir,"realtime/downloads")){provider?.resetAfterRevocation();withContext(Dispatchers.IO){RealtimePinStore(app).clearAfterUserVerification()}} as T
 }})
 DisposableEffect(model){onDispose{model.lock()}}
 LaunchedEffect(foreground,model) { if(!foreground) model.lock() }
 if(!foreground){Text("앱으로 돌아온 후 PIN을 다시 확인해 주세요.");return}
 RealtimeLockedScreen(model,remember{RealtimePinStore(app)}) {
  if(workspace==null) FileTransferScreen(model) else workspace(model,url)
 }
}
