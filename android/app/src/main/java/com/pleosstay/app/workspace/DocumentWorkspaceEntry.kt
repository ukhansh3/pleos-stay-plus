package com.pleosstay.app.workspace

import android.content.Context
import androidx.compose.runtime.*
import com.pleosstay.app.documents.*
import com.pleosstay.app.modes.ModeStartManager
import com.pleosstay.app.realtime.FileTransferViewModel
import com.pleosstay.app.study.*
import com.pleosstay.app.work.*
import com.pleosstay.app.waiting.safety.WaitingSafetyGateway

/** Route-scoped presentation of an Activity-owned session; locks clear sensitive surfaces. */
@Composable
fun DocumentWorkspaceEntry(
    context: Context, transfer: FileTransferViewModel, endpoint: String,
    manager: ModeStartManager, safety: WaitingSafetyGateway, study: Boolean, foreground: Boolean,
    onOpenFiles: () -> Unit, onOpenWaiting: () -> Unit, onHome: () -> Unit,
) = key(transfer, endpoint, study) {
    val owner=rememberDocumentSession(context,transfer,endpoint,manager,safety,study)
    val transferState by transfer.state.collectAsState()
    SideEffect { owner.client.value=DocumentClientAccess(transferState.allowed,transferState.unlocked,foreground) }
    LaunchedEffect(owner,transferState.allowed,transferState.unlocked,foreground) { owner.gateway.refreshAccess() }
    DisposableEffect(owner) { onDispose { owner.hide() } }
    if (study) {
        val model=checkNotNull(owner.study)
        SideEffect { model.setForeground(foreground) }
        StudyScreen(model, onOpenFiles)
    } else {
        val model=checkNotNull(owner.work)
        SideEffect { model.setForeground(foreground) }
        WorkScreen(model, onRealtime = onOpenFiles, onRecoverWaiting = onOpenWaiting, onHome = onHome)
    }
}
