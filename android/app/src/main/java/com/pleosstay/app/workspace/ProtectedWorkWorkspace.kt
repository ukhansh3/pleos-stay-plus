package com.pleosstay.app.workspace

import android.content.Context
import androidx.compose.runtime.*
import com.pleosstay.app.documents.*
import com.pleosstay.app.modes.ModeStartManager
import com.pleosstay.app.realtime.FileTransferViewModel
import com.pleosstay.app.waiting.safety.WaitingSafetyGateway
import com.pleosstay.app.work.*
import com.pleosstay.app.work.docx.*
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow

/** Returns content without rendering it. RealtimeEntry invokes it only INSIDE its existing PIN gate. */
@Composable fun rememberProtectedWorkContent(context:Context,transfer:FileTransferViewModel,endpoint:String,
 manager:ModeStartManager,safety:WaitingSafetyGateway,foreground:Boolean,
 onFiles:()->Unit,onWaiting:()->Unit,onHome:()->Unit,onEditState:(Boolean,Boolean)->Unit = {_,_->}): @Composable ()->Unit {
 val app=context.applicationContext;val scope=rememberCoroutineScope()
 val state by transfer.state.collectAsState()
 val client=remember(transfer){MutableStateFlow(DocumentClientAccess(false,false,false))}
 val gateway=remember(transfer,endpoint){
  val ns=MessageDigest.getInstance("SHA-256").digest(URI(endpoint).normalize().toString().trimEnd('/').toByteArray()).joinToString(""){"%02x".format(it)}
  RealtimeDocumentGateway(transfer.workspaceApi,transfer,client,scope,ns,File(app.noBackupFilesDir,"documents/previews/${UUID.randomUUID()}"),transfer::lock)
 }
 SideEffect{client.value=DocumentClientAccess(state.allowed,state.unlocked,foreground)}
 LaunchedEffect(gateway,state.allowed,state.unlocked,foreground){gateway.refreshAccess()}
 val model=remember(gateway){WorkViewModel(gateway,WorkDraftRepository(AndroidEncryptedDocumentStore(app,"work-v1"),gateway),manager,safety,docxFiles=AndroidDocxFileGateway(app))}
 SideEffect{model.setForeground(foreground)}
 val picker=rememberDocxPicker(model)
 val editor by model.docx.state.collectAsState()
 SideEffect{onEditState(editor.busy,editor.dirty)}
 DisposableEffect(model,gateway){onDispose{model.close();gateway.close()}}
 return { WorkScreen(model,onFiles,onWaiting,onHome,onDocxPicker=picker) }
}
