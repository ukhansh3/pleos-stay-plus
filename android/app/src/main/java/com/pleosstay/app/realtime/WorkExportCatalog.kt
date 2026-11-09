package com.pleosstay.app.realtime

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.pleosstay.app.documents.AndroidEncryptedDocumentStore
import com.pleosstay.app.documents.DocumentRecordStore
import com.pleosstay.app.documents.DocumentRef
import com.pleosstay.app.documents.DocumentValidation
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal const val WORK_DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

data class WorkExportProfile(val userScope: String, val connectionId: String) {
    init {
        DocumentValidation.requireId(userScope)
        DocumentValidation.requireId(connectionId)
    }

    internal fun checkSource(source: DocumentRef?) {
        if (source == null) return
        DocumentValidation.requireRef(source)
        check(source.userScope == userScope && source.connectionId == connectionId && source.mimeType == WORK_DOCX_MIME)
    }
}

/** Provenance of a verified, user-confirmed Work export. Public Downloads are never enumerated. */
@ConsistentCopyVisibility
data class WorkExportReceipt internal constructor(
    val id: String,
    val profile: WorkExportProfile,
    val name: String,
    val size: Long,
    internal val sha256: String,
    internal val contentUri: String,
    val source: DocumentRef?,
) {
    internal fun verifyContent(actualSize: Long, actualHash: String) {
        check(size == actualSize && sha256 == actualHash) { "WORK_EXPORT_CHANGED" }
    }
}

class WorkExportCatalog(context: Context) {
    private val resolver = context.applicationContext.contentResolver
    private val records: DocumentRecordStore =
        AndroidEncryptedDocumentStore(context.applicationContext, "work-exports-v1", maxRecordBytes = 8192)

    internal suspend fun record(uri: Uri, bytes: ByteArray, profile: WorkExportProfile, source: DocumentRef?) =
        withContext(Dispatchers.IO) {
            check(uri.scheme == "content" && uri.authority == "media")
            profile.checkSource(source)
            require(bytes.size.toLong() in 1..DocumentValidation.MAX_BYTES)
            val name = name(uri) ?: error("WORK_EXPORT_MISSING")
            check(LocalFileStore.documentMime(name) == WORK_DOCX_MIME)
            val receipt = WorkExportReceipt(
                UUID.randomUUID().toString(), profile, name, bytes.size.toLong(),
                MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) },
                uri.toString(), source,
            )
            records.write(key(profile, receipt.id), encode(receipt).toString().toByteArray(Charsets.UTF_8))
        }

    internal suspend fun list(profile: WorkExportProfile): List<WorkExportReceipt> = withContext(Dispatchers.IO) {
        val prefix = key(profile, "")
        records.keys().filter { it.startsWith(prefix) }.mapNotNull { entry ->
            read(profile, entry.removePrefix(prefix))?.takeIf { name(Uri.parse(it.contentUri)) == it.name }
        }.sortedBy { it.name }
    }

    internal suspend fun read(profile: WorkExportProfile, id: String): WorkExportReceipt? =
        records.read(key(profile, UUID.fromString(id).toString()))?.let { bytes ->
            try {
                val json = JSONObject(bytes.toString(Charsets.UTF_8))
                check(json.getString("id") == id)
                val storedProfile = WorkExportProfile(json.getString("owner"), json.getString("connection"))
                check(storedProfile == profile)
                val source = json.optJSONObject("source")?.let {
                    DocumentRef(
                        storedProfile.userScope, storedProfile.connectionId, it.getString("fileId"),
                        it.getString("versionId"), it.getString("name"), it.getString("mime"),
                        it.getString("sha"), it.getLong("size"), it.getInt("version"),
                    )
                }
                storedProfile.checkSource(source)
                WorkExportReceipt(id, storedProfile, json.getString("name"), json.getLong("size"),
                    json.getString("sha"), json.getString("uri"), source)
            } finally {
                bytes.fill(0)
            }
        }

    internal suspend fun checkAvailable(receipt: WorkExportReceipt) = withContext(Dispatchers.IO) {
        check(name(Uri.parse(receipt.contentUri)) == receipt.name) { "WORK_EXPORT_MISSING" }
    }

    private fun key(profile: WorkExportProfile, id: String) =
        "${profile.userScope}/${profile.connectionId}/$id"

    private fun name(uri: Uri): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || uri.scheme != "content" || uri.authority != "media") return null
        return try {
            val columns = arrayOf(OpenableColumns.DISPLAY_NAME, MediaStore.MediaColumns.RELATIVE_PATH)
            resolver.query(uri, columns, null, null, null)?.use {
                if (it.moveToFirst() && it.getString(1)?.trimEnd('/') == "${Environment.DIRECTORY_DOWNLOADS}/StayPlus") {
                    it.getString(0)
                } else null
            }
        } catch (_: SecurityException) {
            null
        }
    }

    private fun encode(receipt: WorkExportReceipt) = JSONObject()
        .put("id", receipt.id)
        .put("owner", receipt.profile.userScope)
        .put("connection", receipt.profile.connectionId)
        .put("name", receipt.name)
        .put("size", receipt.size)
        .put("sha", receipt.sha256)
        .put("uri", receipt.contentUri)
        .apply {
            receipt.source?.let { source ->
                put("source", JSONObject().put("fileId", source.fileId).put("versionId", source.versionId)
                    .put("name", source.name).put("mime", source.mimeType).put("sha", source.sha256)
                    .put("size", source.sizeBytes).put("version", source.versionNo))
            }
        }
}
