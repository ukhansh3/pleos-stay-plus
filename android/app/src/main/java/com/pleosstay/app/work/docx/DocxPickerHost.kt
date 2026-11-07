package com.pleosstay.app.work.docx

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import com.pleosstay.app.work.WorkViewModel
import kotlinx.coroutines.launch

/** Retains only request metadata while the mandatory PIN gate masks the editor. */
@Composable fun rememberDocxPicker(model:WorkViewModel):(DocxPickerKind,Int?)->Unit {
 var request by remember { mutableStateOf<DocxPickerRequest?>(null) }
 var returned by remember { mutableStateOf<Uri?>(null) }
 val work by model.state.collectAsState();val scope=rememberCoroutineScope()
 val open=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri==null)request=null else returned=uri}
 val create=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(DOCX_MIME)){uri->if(uri==null)request=null else returned=uri}
 LaunchedEffect(returned,work.allowed,work.session){
  val uri=returned;val pending=request
  if(uri!=null&&pending!=null&&work.allowed&&work.session!=null){returned=null;request=null;model.docx.accept(pending,uri)}
 }
 return {kind,image->if(request==null)scope.launch{try{
  if(!model.docx.hasSystemPicker()) { if(kind==DocxPickerKind.EXPORT)model.docx.requestDownloads() else model.docx.pickerUnavailable();return@launch }
  val next=model.docx.prepare(kind,image);request=next
  if(kind==DocxPickerKind.EXPORT)create.launch(next.draft?.name ?: "문서.docx")
  else open.launch(if(kind==DocxPickerKind.OPEN)arrayOf(DOCX_MIME)else arrayOf("image/png","image/jpeg"))
 }catch(_:Exception){request=null;model.docx.pickerUnavailable()}}}
}
