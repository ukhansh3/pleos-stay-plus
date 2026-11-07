package com.pleosstay.app.workspace

import android.content.Context
import androidx.compose.runtime.*
import com.pleosstay.app.documents.*
import com.pleosstay.app.modes.ModeStartManager
import com.pleosstay.app.realtime.FileTransferViewModel
import com.pleosstay.app.study.*
import com.pleosstay.app.work.*
import com.pleosstay.app.waiting.safety.WaitingSafetyGateway
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow

/** Composition-scoped adapters: a PIN/safety/foreground lock destroys in-memory workspaces. */
@Composable
fun DocumentWorkspaceEntry(
    context: Context, transfer: FileTransferViewModel, endpoint: String,
    manager: ModeStartManager, safety: WaitingSafetyGateway, study: Boolean, foreground: Boolean,
    onOpenFiles: () -> Unit, onOpenWaiting: () -> Unit, onHome: () -> Unit,
) = key(transfer, endpoint, study) {
    val app = context.applicationContext
    val scope = rememberCoroutineScope()
    val transferState by transfer.state.collectAsState()
    val client = remember { MutableStateFlow(DocumentClientAccess(false, false, false)) }
    val gateway = remember {
        val normalized = URI(endpoint).normalize().toString().trimEnd('/')
        val namespace = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        RealtimeDocumentGateway(transfer.workspaceApi, transfer, client, scope, namespace,
            File(app.noBackupFilesDir, "documents/previews/${UUID.randomUUID()}"), transfer::lock)
    }
    SideEffect { client.value = DocumentClientAccess(transferState.allowed, transferState.unlocked, foreground) }
    LaunchedEffect(gateway, transferState.allowed, transferState.unlocked, foreground) {
        gateway.refreshAccess()
    }
    DisposableEffect(gateway) {
        onDispose {
            client.value = DocumentClientAccess(false, false, false)
            gateway.close()
        }
    }
    if (study) {
        val model = remember { StudyViewModel(manager, safety, gateway, createStudyRepository(app)) }
        SideEffect { model.setForeground(foreground) }
        DisposableEffect(model) { onDispose { model.close() } }
        StudyScreen(model, onOpenFiles)
    } else {
        val model = remember { WorkViewModel(gateway,
            WorkDraftRepository(AndroidEncryptedDocumentStore(app, "work-v1"), gateway), manager, safety) }
        SideEffect { model.setForeground(foreground) }
        DisposableEffect(model) { onDispose { model.close() } }
        WorkScreen(model, onRealtime = onOpenFiles, onRecoverWaiting = onOpenWaiting, onHome = onHome)
    }
}
