package com.pleosstay.app.documents

import android.net.Uri
import java.io.File
import kotlinx.coroutines.flow.StateFlow

data class DocumentKey(val userScope: String, val connectionId: String, val fileId: String, val versionId: String)
data class DocumentFile(val userScope: String, val connectionId: String, val fileId: String, val name: String)
data class DocumentRef(
    val userScope: String, val connectionId: String, val fileId: String, val versionId: String,
    val name: String, val mimeType: String, val sha256: String, val sizeBytes: Long, val versionNo: Int,
) {
    val key: DocumentKey get() = DocumentKey(userScope, connectionId, fileId, versionId)
}
data class DocumentAccess(
    val allowed: Boolean = false, val userScope: String? = null, val connectionId: String? = null,
    val reason: String = "LOCKED", val revision: Long = 0,
)
data class DocumentClientAccess(val allowed: Boolean, val unlocked: Boolean, val foreground: Boolean) {
    val canUse: Boolean get() = allowed && unlocked && foreground
}
sealed interface DocumentOpenResult {
    data class Ready(val file: File, val ref: DocumentRef) : DocumentOpenResult
    data class Blocked(val reason: String) : DocumentOpenResult
    data class Failed(val reason: String) : DocumentOpenResult
}
sealed interface DocumentUploadResult {
    data class Saved(val ref: DocumentRef) : DocumentUploadResult
    data class Blocked(val reason: String) : DocumentUploadResult
    data class Failed(val reason: String) : DocumentUploadResult
}
interface SharedDocumentGateway {
    val access: StateFlow<DocumentAccess>
    suspend fun refreshAccess(): DocumentAccess
    suspend fun listFiles(): List<DocumentFile>
    suspend fun listVersions(fileId: String): List<DocumentRef>
    suspend fun validate(ref: DocumentRef): Boolean
    suspend fun downloadVerified(ref: DocumentRef): DocumentOpenResult
    suspend fun uploadRevision(ref: DocumentRef, contentUri: Uri, idempotencyKey: String,
        progress: (Long, Long) -> Unit = { _, _ -> }): DocumentUploadResult
    fun clearTemporaryFiles()
    fun close()
}

/** Encryption only: callers must check the live gateway before displaying retained records. */
interface DocumentRecordStore {
    suspend fun read(key: String): ByteArray?
    suspend fun write(key: String, bytes: ByteArray)
    suspend fun delete(key: String)
    suspend fun keys(): List<String>
}
