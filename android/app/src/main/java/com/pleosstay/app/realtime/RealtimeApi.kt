package com.pleosstay.app.realtime

import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Token provider is independent from Pickup. Local test credentials are supplied only by a debug host. */
class RealtimeApi(private val baseUrl: String, private val token: suspend () -> String) {
    init {
        val uri = URI(baseUrl)
        require(uri.scheme == "https" || (com.pleosstay.app.BuildConfig.DEBUG && uri.scheme == "http" && uri.host in listOf("10.0.2.2", "127.0.0.1", "localhost"))) { "HTTPS_REQUIRED" }
    }
    // Match the document workspace's endpoint namespace.
    internal val documentNamespace: String
        get() = MessageDigest.getInstance("SHA-256")
            .digest(URI(baseUrl).normalize().toString().trimEnd('/').toByteArray())
            .joinToString("") { "%02x".format(it) }

    private suspend fun connection(method: String, path: String): HttpURLConnection {
        val accessToken = token()
        currentCoroutineContext().ensureActive()
        return (URI(baseUrl.trimEnd('/') + "/" + path).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = method; connectTimeout = 10000; readTimeout = 30000
            instanceFollowRedirects = false
            setRequestProperty("Authorization", "Bearer $accessToken")
        }
    }

    private fun response(c: HttpURLConnection): JSONObject {
        val status = c.responseCode
        val text = (if (status in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        check(status in 200..299) { "HTTP_$status: $text" }
        return if (text.isBlank()) JSONObject() else JSONObject(text)
    }
    suspend fun call(method: String, path: String, body: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        val c = connection(method, path)
        try {
            // Android also supplies implicit form headers on body-less DELETE requests.
            c.setRequestProperty("Content-Type", "application/json")
            val payload = realtimeControlPayload(method, body?.toString())
            if (payload != null) {
                // Android's body-less POST defaults to form encoding. All control writes use JSON.
                val bytes = payload.toByteArray(Charsets.UTF_8)
                c.doOutput = true
                c.setFixedLengthStreamingMode(bytes.size)
                c.outputStream.use { it.write(bytes) }
            }
            response(c)
        }
        finally { c.disconnect() }
    }
    suspend fun upload(id: String, file: File, progress: (Long, Long) -> Unit): JSONObject = withContext(Dispatchers.IO) {
        coroutineScope {
            val c = connection("PUT", "uploads/$id/content")
            val closed = AtomicBoolean(false)
            // A cancelled job checks each chunk and also disconnects a blocked HTTP write.
            val abort = launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    awaitCancellation()
                } finally {
                    if (closed.compareAndSet(false, true)) c.disconnect()
                }
            }
            try {
                currentCoroutineContext().ensureActive()
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/octet-stream")
                val length = file.length()
                c.setFixedLengthStreamingMode(length)
                progress(0, length)
                file.inputStream().use { input -> c.outputStream.use { output ->
                    val buffer = ByteArray(65536)
                    var total = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        progress(total, length)
                        output.write(buffer, 0, n)
                        total += n
                        progress(total, length)
                    }
                } }
                currentCoroutineContext().ensureActive()
                response(c)
            } finally {
                if (closed.compareAndSet(false, true)) c.disconnect()
                abort.cancel()
            }
        }
    }
    suspend fun download(version: JSONObject, directory: File, progress: (Long, Long) -> Unit): File {
      var completed: File? = null
      return try { withContext(Dispatchers.IO) {
        val connectionBefore=call("GET","connections/current").optJSONObject("connection")?.getString("id") ?: error("NO_CONNECTION")
        directory.mkdirs(); val part = File.createTempFile("transfer-", ".part", directory)
        val c = connection("GET", "versions/${version.getString("id")}/content")
        try {
            check(c.responseCode == 200) { "DOWNLOAD_FAILED_${c.responseCode}" }
            val hash = MessageDigest.getInstance("SHA-256"); var total = 0L; val expected = version.getLong("size")
            c.inputStream.use { input -> part.outputStream().use { output ->
                val buffer = ByteArray(65536)
                while (true) { currentCoroutineContext().ensureActive(); val n = input.read(buffer); if (n < 0) break; progress(total,expected); total += n; check(total <= expected) { "SIZE_MISMATCH" }; hash.update(buffer,0,n); output.write(buffer,0,n); progress(total,expected) }
            } }
            check(total == expected && hash.digest().joinToString("") { "%02x".format(it) } == version.getString("sha256")) { "INTEGRITY_MISMATCH" }
            check(call("GET","connections/current").optJSONObject("connection")?.getString("id")==connectionBefore) { "CONNECTION_REVOKED" }
            currentCoroutineContext().ensureActive(); progress(total,expected)
            val target = File(directory, java.util.UUID.randomUUID().toString() + "." + version.getString("fileName").substringAfterLast('.', "bin").lowercase().let { if(it in listOf("txt","pdf","docx")) it else "bin" })
            check(part.renameTo(target)) { "LOCAL_SAVE_FAILED" }; completed=target; target
        } finally { c.disconnect(); part.delete() }
      } } catch (error: Throwable) { completed?.delete(); throw error }
    }
}

/** Binary upload has its own API; control POST/PUT/PATCH/DELETE must never become implicit form requests. */
internal fun realtimeControlPayload(method: String, serializedBody: String?): String? = serializedBody
    ?: if (method.uppercase(java.util.Locale.ROOT) in setOf("POST", "PUT", "PATCH", "DELETE")) "{}" else null
