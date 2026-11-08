package com.pleosstay.app.ui

import android.net.Uri
import com.pleosstay.app.realtime.*
import com.pleosstay.app.rest.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.flowOf
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import org.json.JSONArray
import org.json.JSONObject

/** Loopback fixture server: synthetic list/version data only; no cloud calls or uploads. */
internal class ProofRealtimeServer : AutoCloseable {
    private val socket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
    val url = "http://127.0.0.1:${socket.localPort}/api/v1/realtime/"
    @Volatile private var stopped = false
    private val worker = Thread {
        while (!stopped) try {
            socket.accept().use { client ->
                client.soTimeout = 5000
                val input = client.getInputStream().bufferedReader()
                val request = input.readLine().orEmpty().split(' ')
                while (!input.readLine().isNullOrEmpty()) { /* consume request headers */ }
                val path = request.getOrElse(1) { "" }
                val body = when {
                    path.endsWith("connections/current") -> JSONObject().put("connection", JSONObject().put("id", "proof-connection").put("name", "테스트 PC")).toString()
                    path.endsWith("/versions") -> JSONObject().put("items", JSONArray().put(JSONObject().put("id", "proof-version").put("fileId", "proof-docx")
                        .put("versionNo", 1).put("fileName", "테스트_회의록.docx").put("mimeType", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                        .put("size", 4096).put("sha256", "0".repeat(64)))).toString()
                    path.endsWith("files") -> JSONObject().put("items", JSONArray().put(JSONObject().put("id", "proof-docx").put("name", "테스트_회의록.docx"))
                        .put(JSONObject().put("id", "proof-text").put("name", "테스트_학습자료.txt"))).toString()
                    else -> "{}"
                }.toByteArray(Charsets.UTF_8)
                client.getOutputStream().use { output ->
                    output.write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
                    output.write(body); output.flush()
                }
            }
        } catch (_: Exception) { if (stopped) break }
    }.apply { isDaemon = true; name = "premium-proof-loopback"; start() }
    override fun close() { stopped = true; socket.close(); worker.join(2000) }
}
internal class ProofRevisionStore : RevisionStore {
    override suspend fun stage(request: WorkRevisionRequest): StagedRevision = error("SCREENSHOT_FIXTURE_HAS_NO_UPLOAD")
    override fun describe(uri: Uri) = error("SCREENSHOT_FIXTURE_HAS_NO_UPLOAD")
    override fun save(staged: StagedRevision, connection: String) = Unit
    override fun pending(connection: String): StagedRevision? = null
    override fun finish(staged: StagedRevision) = Unit
    override fun clear() = Unit
}
internal fun proofRest(waiting: ProofWaiting, scope: CoroutineScope): RestController {
    val persistence = object : RestPersistence {
        private var values = emptyList<RestPreset>()
        private var current: RestSession? = null
        override fun presets() = values
        override fun savePresets(values: List<RestPreset>) { this.values = values }
        override fun session() = current
        override fun saveSession(value: RestSession) { current = value }
    }
    val alerts = object : RestAlerts {
        override fun problem(style: RestAlert): String? = null
        override fun soundProblem(style: RestAlert): String? = null
        override fun foregroundProblem(style: RestAlert): String? = null
        override fun schedule(session: RestSession) = Unit
        override fun cancel(session: RestSession) = Unit
        override fun sound(session: RestSession, reason: RestEnd) = Unit
        override fun silence() = Unit
    }
    val audio = object : RestSoundPlayer { override fun play() = true; override fun stop() = Unit }
    return RestController(persistence, alerts, audio, RestBattery { flowOf(EvReading(54, "테스트 배터리")) }, waiting.manager, waiting.safety, scope)
}
