package com.pleosstay.app.realtime
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
@Composable fun FileTransferScreen(model: FileTransferViewModel) {
 var confirmSwitch by remember { mutableStateOf(false) }
 var selectedVersion by remember { mutableStateOf<org.json.JSONObject?>(null) }
 val picker=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()){uri -> val version=selectedVersion; if(uri!=null && version!=null)model.uploadFakeWork(uri,version)}
 val state by model.state.collectAsState(); var code by remember { mutableStateOf("") }
 if(!state.allowed || !state.unlocked) { Text("파일 화면 잠김: 정차 확인 및 사용자 인증 필요"); return }
 if(confirmSwitch) AlertDialog(onDismissRequest={confirmSwitch=false},title={Text("이전 사용자 연결 지우기")},text={Text("현재 PC 연결을 해제하고 차량의 파일·인증정보·PIN을 지웁니다. 다음 사용자는 다시 페어링해야 합니다.")},confirmButton={TextButton(onClick={confirmSwitch=false;model.switchUserAfterConfirmation()}){Text("지우고 사용자 전환")}},dismissButton={TextButton(onClick={confirmSwitch=false}){Text("취소")}})
 Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
  Text("Stay+ FileTransfer",style=MaterialTheme.typography.headlineSmall)
  Text(state.message)
  Button(onClick=model::register,enabled=!state.busy){Text("기기 등록 / 연결 복원")}
  Text("연결: ${state.connectionId ?: "없음"}")
  Button(onClick=model::issue,enabled=!state.busy){Text("페어링 코드 발급")}; Text(state.code)
  OutlinedTextField(code,{code=it},label={Text("상대 기기 코드")})
  Button(onClick={model.claim(code)},enabled=!state.busy){Text("코드 입력")}
  Button(onClick=model::inspectPairing,enabled=!state.busy){Text("상대 기기 확인")}
  Button(onClick=model::approve,enabled=!state.busy){Text("확인한 상대 승인")}
  Button(onClick=model::refresh,enabled=!state.busy){Text("목록 새로고침")}
  state.files.forEach { f -> TextButton(onClick={model.versions(f.getString("id"))},enabled=!state.busy){Text(f.getString("name"))} }
  state.versions.forEach { v -> TextButton(onClick={model.download(v)},enabled=!state.busy){Text("v${v.getInt("versionNo")} · ${v.getString("fileName")} · ${v.getLong("size")} bytes 다운로드")} }
  if(com.pleosstay.app.BuildConfig.DEBUG) state.versions.forEach { v -> TextButton(enabled=!state.busy,onClick={selectedVersion=v;picker.launch(arrayOf("text/plain","application/pdf","application/vnd.openxmlformats-officedocument.wordprocessingml.document"))}){Text("Fake Work: v${v.getInt("versionNo")} 기반 문서 수정본 선택") } }
  Text("진행률 ${(state.progress*100).toInt()}%")
  Button(onClick=model::retryUpload,enabled=!state.busy){Text("중단된 수정본 재시도")}
  Button(onClick=model::clearLocalFiles,enabled=!state.busy){Text("로컬 파일 지우고 잠금")}
  Button(onClick=model::cancel){Text("전송 취소")}
  Button(onClick=model::disconnect,enabled=!state.busy){Text("연결 해제")}
  Button(onClick={confirmSwitch=true},enabled=!state.busy){Text("사용자 전환 / 이전 연결 지우기")}
  Button(onClick=model::lock){Text("화면 잠금")}
 }
}

