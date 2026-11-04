package com.pleosstay.app.realtime

import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
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
    private suspend fun connection(method: String, path: String): HttpURLConnection =
        (URI(baseUrl.trimEnd('/') + "/" + path).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = method; connectTimeout = 10000; readTimeout = 30000
            instanceFollowRedirects = false
            setRequestProperty("Authorization", "Bearer ${token()}")
        }
    private fun response(c: HttpURLConnection): JSONObject {
        val status = c.responseCode
        val text = (if (status in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        check(status in 200..299) { "HTTP_$status: $text" }
        return if (text.isBlank()) JSONObject() else JSONObject(text)
    }
    suspend fun call(method: String, path: String, body: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        val c = connection(method, path)
        try { if (body != null) { c.doOutput = true; c.setRequestProperty("Content-Type", "application/json"); c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) } }; response(c) }
        finally { c.disconnect() }
    }
    suspend fun upload(id: String, file: File, progress: (Long, Long) -> Unit): JSONObject = withContext(Dispatchers.IO) {
        val c = connection("PUT", "uploads/$id/content")
        try {
            c.doOutput = true; c.setRequestProperty("Content-Type", "application/octet-stream"); c.setFixedLengthStreamingMode(file.length())
            file.inputStream().use { input -> c.outputStream.use { output ->
                val buffer = ByteArray(65536); var total = 0L
                while (true) { currentCoroutineContext().ensureActive(); val n = input.read(buffer); if (n < 0) break; progress(total,file.length()); output.write(buffer, 0, n); total += n; progress(total, file.length()) }
            } }; response(c)
        } finally { c.disconnect() }
    }
    suspend fun download(version: JSONObject, directory: File, progress: (Long, Long) -> Unit): File = withContext(Dispatchers.IO) {
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
            check(part.renameTo(target)) { "LOCAL_SAVE_FAILED" }; target
        } finally { c.disconnect(); part.delete() }
    }
}
