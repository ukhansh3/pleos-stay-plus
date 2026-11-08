package com.pleosstay.app.workspace

import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pleosstay.app.documents.*
import com.pleosstay.app.modes.ModeStartManager
import com.pleosstay.app.realtime.FileTransferViewModel
import com.pleosstay.app.study.*
import com.pleosstay.app.waiting.safety.WaitingSafetyGateway
import com.pleosstay.app.work.*
import com.pleosstay.app.work.docx.AndroidDocxFileGateway
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow

/** Activity ViewModelStore owns timers across route/configuration changes. No sensitive UI is kept.
 * On process death active timers are not automatically restarted; saved notes/drafts need fresh auth.
 * The future common host supplies these same instances to the coordinator adapters. */
class DocumentSessionOwner(context: Context, transfer: FileTransferViewModel, endpoint: String,
    manager: ModeStartManager, safety: WaitingSafetyGateway, studyMode: Boolean) : AndroidViewModel(context.applicationContext as android.app.Application) {
    val client = MutableStateFlow(DocumentClientAccess(false,false,false))
    private val app get()=getApplication<android.app.Application>()
    private val namespace=MessageDigest.getInstance("SHA-256").digest(URI(endpoint).normalize().toString().trimEnd('/').toByteArray()).joinToString(""){"%02x".format(it)}
    val gateway=RealtimeDocumentGateway(transfer.workspaceApi,transfer,client,viewModelScope,namespace,
        File(app.noBackupFilesDir,"documents/previews/"+UUID.randomUUID()),transfer::lock)
    val study=if(studyMode) StudyViewModel(manager,safety,gateway,createStudyRepository(app)) else null
    val work=if(!studyMode) WorkViewModel(gateway,WorkDraftRepository(AndroidEncryptedDocumentStore(app,"work-v1"),gateway),manager,safety,docxFiles=AndroidDocxFileGateway(app)) else null
    fun hide() {
        client.value=DocumentClientAccess(false,false,false)
        study?.setForeground(false);work?.setForeground(false)
        gateway.clearTemporaryFiles()
    }
    override fun onCleared() { hide();study?.close();work?.close();gateway.close();super.onCleared() }
}
