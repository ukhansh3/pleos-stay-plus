package com.pleosstay.app.realtime

import android.content.Context
import android.net.Uri
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class WorkRevisionRequest(val originalFileId: String, val baseVersionId: String, val sourceUri: Uri, val fileName: String, val mimeType: String, val idempotencyKey: String)
data class StagedRevision(val transferId: String, val request: WorkRevisionRequest, val file: File, val size: Long, val sha256: String)
interface RealtimeWorkBridge {
    suspend fun stageRevision(request: WorkRevisionRequest): StagedRevision
    suspend fun uploadRevision(staged: StagedRevision, progress: (Long, Long) -> Unit): org.json.JSONObject
}
interface RevisionStore {
 suspend fun stage(request: WorkRevisionRequest): StagedRevision
 fun describe(uri: Uri): Pair<String,String>
 fun save(staged: StagedRevision, connection: String)
 fun pending(connection: String): StagedRevision?
 fun finish(staged: StagedRevision)
 fun clear()
}
class LocalFileStore(private val context: Context, private val maxBytes: Long = 20L * 1024 * 1024) : RevisionStore {
    private val journal = RealtimeTokenStore(context, "realtime-transfer.bin")
    companion object {
        fun documentMime(name: String): String? = when(name.substringAfterLast('.', "").lowercase()) {
            "txt" -> "text/plain"; "pdf" -> "application/pdf"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            else -> null
        }
    }
    override fun describe(uri: Uri): Pair<String,String> {
        val name=context.contentResolver.query(uri,arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),null,null,null)?.use { if(it.moveToFirst()) it.getString(0) else null } ?: error("SOURCE_NAME_MISSING")
        return name to (documentMime(name) ?: error("UNSUPPORTED_TYPE"))
    }
    override fun save(staged: StagedRevision, connection: String) {
        val r=staged.request
        journal.write(org.json.JSONObject().put("connection",connection).put("id",staged.transferId).put("fileId",r.originalFileId).put("base",r.baseVersionId).put("name",r.fileName).put("mime",r.mimeType).put("key",r.idempotencyKey).put("size",staged.size).put("sha",staged.sha256).toString())
    }
    override fun pending(connection: String): StagedRevision? {
        val text=journal.read() ?: return null
        if(text=="{}") return null
        val j=org.json.JSONObject(text)
        check(j.getString("connection")==connection) { "PENDING_DIFFERENT_CONNECTION" }
        val id=java.util.UUID.fromString(j.getString("id")).toString()
        val file=File(context.noBackupFilesDir,"realtime/staged/$id")
        check(file.isFile) { "STAGED_FILE_MISSING" }
        return StagedRevision(id,WorkRevisionRequest(j.getString("fileId"),j.getString("base"),Uri.EMPTY,j.getString("name"),j.getString("mime"),j.getString("key")),file,j.getLong("size"),j.getString("sha"))
    }
    override fun finish(staged: StagedRevision) { journal.write("{}"); staged.file.delete() }
    override fun clear() { journal.write("{}"); File(context.noBackupFilesDir,"realtime/staged").listFiles()?.forEach { it.delete() } }
    override suspend fun stage(request: WorkRevisionRequest): StagedRevision = withContext(Dispatchers.IO) {
        require(request.sourceUri.scheme == "content") { "CONTENT_URI_REQUIRED" }
        require(documentMime(request.fileName) == request.mimeType) { "UNSUPPORTED_TYPE" }
        val id = UUID.randomUUID().toString()
        val directory = File(context.noBackupFilesDir, "realtime/staged").apply { mkdirs() }
        val target = File(directory, id)
        try {
            val hash = MessageDigest.getInstance("SHA-256"); var size = 0L
            val source = context.contentResolver.openInputStream(request.sourceUri) ?: error("SOURCE_MISSING")
            source.use { input -> target.outputStream().use { output ->
                val buffer = ByteArray(65536)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer); if (count < 0) break
                    size += count; require(size <= maxBytes) { "FILE_TOO_LARGE" }
                    hash.update(buffer, 0, count); output.write(buffer, 0, count)
                }
            } }
            require(size > 0) { "EMPTY_FILE" }
            StagedRevision(id, request, target, size, hash.digest().joinToString("") { "%02x".format(it) })
        } catch (e: Throwable) { target.delete(); throw e }
    }
}
