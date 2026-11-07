package com.pleosstay.app.documents

import android.net.Uri
import com.pleosstay.app.realtime.RealtimeApi
import com.pleosstay.app.realtime.RealtimeWorkBridge
import com.pleosstay.app.realtime.WorkRevisionRequest
import java.io.File
import java.net.URLEncoder
import org.json.JSONObject

data class DocumentServerIdentity(val uid: String, val connectionId: String)

/** No token or independently constructed client crosses this port. */
interface DocumentTransport {
    suspend fun identity(): DocumentServerIdentity
    suspend fun files(userScope: String, connectionId: String): List<DocumentFile>
    suspend fun versions(userScope: String, connectionId: String, fileId: String): List<DocumentRef>
    suspend fun download(ref: DocumentRef, directory: File): File
    suspend fun upload(ref: DocumentRef, uri: Uri, key: String, progress: (Long, Long) -> Unit): DocumentRef
}

class RealtimeDocumentTransport(private val api: RealtimeApi, private val bridge: RealtimeWorkBridge) : DocumentTransport {
    override suspend fun identity(): DocumentServerIdentity {
        val device = api.call("GET", "devices/me")
        check(device.getString("kind") == "VEHICLE")
        val connection = api.call("GET", "connections/current").optJSONObject("connection") ?: error("NO_CONNECTION")
        check(connection.isNull("revokedAt"))
        check(connection.getString("vehicleDeviceId") == device.getString("id"))
        return DocumentServerIdentity(device.getString("uid"), connection.getString("id"))
    }
    override suspend fun files(userScope: String, connectionId: String): List<DocumentFile> =
        items(api.call("GET", "files")).map {
            check(it.getString("connectionId") == connectionId)
            DocumentFile(userScope, connectionId, it.getString("id"), it.getString("name"))
        }
    override suspend fun versions(userScope: String, connectionId: String, fileId: String): List<DocumentRef> =
        items(api.call("GET", "files/${segment(fileId)}/versions")).map {
            check(it.getString("connectionId") == connectionId && it.getString("fileId") == fileId)
            ref(it, userScope)
        }
    override suspend fun download(ref: DocumentRef, directory: File): File = api.download(JSONObject()
        .put("id", ref.versionId).put("size", ref.sizeBytes).put("sha256", ref.sha256).put("fileName", ref.name), directory) { _, _ -> }
    override suspend fun upload(ref: DocumentRef, uri: Uri, key: String, progress: (Long, Long) -> Unit): DocumentRef {
        require(uri.scheme == "content")
        require(Regex("[a-zA-Z0-9-]{16,100}").matches(key))
        val staged = bridge.stageRevision(WorkRevisionRequest(ref.fileId, ref.versionId, uri, ref.name, ref.mimeType, key))
        val result = bridge.uploadRevision(staged, progress)
        check(result.getString("fileId") == ref.fileId && result.getString("connectionId") == ref.connectionId)
        check(result.getString("baseVersionId") == ref.versionId)
        check(result.getLong("size") == staged.size && result.getString("sha256") == staged.sha256)
        return ref(result, ref.userScope)
    }
    private fun ref(j: JSONObject, scope: String): DocumentRef {
        check(j.getString("state") == "READY")
        return DocumentRef(scope, j.getString("connectionId"), j.getString("fileId"), j.getString("id"),
            j.getString("fileName"), j.getString("mimeType"), j.getString("sha256"), j.getLong("size"), j.getInt("versionNo"))
            .also { DocumentValidation.requireRef(it) }
    }
    private fun items(j: JSONObject): List<JSONObject> = j.getJSONArray("items").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
    private fun segment(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}

internal object DocumentValidation {
    const val MAX_BYTES = 20L * 1024 * 1024
    fun requireId(value: String) { require(value.isNotBlank() && value.length <= 512 && value.none { it.isISOControl() || it == '/' || it == '\\' }) }
    fun requireRef(ref: DocumentRef) {
        listOf(ref.userScope, ref.connectionId, ref.fileId, ref.versionId).forEach(::requireId)
        require(ref.name.isNotBlank() && ref.name.length <= 200 && ref.name.none { it.isISOControl() || it == '/' || it == '\\' })
        val mime = when (ref.name.substringAfterLast('.', "").lowercase()) {
            "txt" -> "text/plain"; "pdf" -> "application/pdf"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            else -> error("UNSUPPORTED_TYPE")
        }
        require(mime == ref.mimeType && ref.sizeBytes in 1..MAX_BYTES && ref.versionNo > 0)
        require(Regex("[a-f0-9]{64}").matches(ref.sha256))
    }
}
