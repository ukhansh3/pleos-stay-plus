package com.pleosstay.app.workspace

import android.content.Context
import androidx.compose.runtime.*
import com.pleosstay.app.documents.*
import com.pleosstay.app.modes.ModeStartManager
import com.pleosstay.app.realtime.FileTransferViewModel
import com.pleosstay.app.waiting.safety.WaitingSafetyGateway
import com.pleosstay.app.work.*
import com.pleosstay.app.work.docx.*

/** Returns content without rendering it. RealtimeEntry invokes it only INSIDE its existing PIN gate. */
@Composable fun rememberProtectedWorkContent(context:Context,transfer:FileTransferViewModel,endpoint:String,
 manager:ModeStartManager,safety:WaitingSafetyGateway,foreground:Boolean,
 onFiles:()->Unit,onWaiting:()->Unit,onHome:()->Unit,onEditState:(Boolean,Boolean)->Unit = {_,_->}): @Composable ()->Unit {
 val owner=rememberDocumentSession(context,transfer,endpoint,manager,safety,false)
 val state by transfer.state.collectAsState()
 SideEffect{owner.client.value=DocumentClientAccess(state.allowed,state.unlocked,foreground)}
 LaunchedEffect(owner,state.allowed,state.unlocked,foreground){owner.gateway.refreshAccess()}
 val model=checkNotNull(owner.work)
 SideEffect{model.setForeground(foreground)}
 val picker=rememberDocxPicker(model)
 val editor by model.docx.state.collectAsState()
 SideEffect{onEditState(editor.busy,editor.dirty)}
 DisposableEffect(owner){onDispose{owner.hide()}}
 return { WorkScreen(model,onFiles,onWaiting,onHome,onDocxPicker=picker) }
}
