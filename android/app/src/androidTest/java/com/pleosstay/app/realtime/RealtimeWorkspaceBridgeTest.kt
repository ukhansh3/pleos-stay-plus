package com.pleosstay.app.realtime

import android.net.Uri
import androidx.lifecycle.ViewModelStore
import com.pleosstay.app.waiting.safety.DrivingEventBuffer
import com.pleosstay.app.waiting.safety.VehicleMotion
import com.pleosstay.app.waiting.safety.WaitingSafety
import com.pleosstay.app.waiting.safety.WaitingSafetyGateway
import java.io.File
import java.net.ServerSocket
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Loopback HTTP only, fake records/identities; never connects to Supabase or a real file server. */
class RealtimeWorkspaceBridgeTest {
    private class Safety : WaitingSafetyGateway {
        override val drivingEvents = DrivingEventBuffer()
        override val state = MutableStateFlow(WaitingSafety(VehicleMotion.PARKED, false))
        override fun start() = Unit
        override fun stop() = Unit
        override fun close() = Unit
    }
    private class Store(private val directory: File) : RevisionStore {
        var bytes = "original-revision".toByteArray()
        var saved: StagedRevision? = null
        var connection: String? = null
        var stages = 0
        override suspend fun stage(request: WorkRevisionRequest): StagedRevision {
            stages++
            val id = UUID.randomUUID().toString()
            val file = File(directory, id).apply { writeBytes(bytes) }
            return StagedRevision(id, request, file, bytes.size.toLong(), MessageDigest.getInstance("SHA-256")
                .digest(bytes).joinToString("") { "%02x".format(it) })
        }
        override fun describe(uri: Uri) = "test.txt" to "text/plain"
        override fun save(staged: StagedRevision, connection: String) { saved = staged; this.connection = connection }
        override fun pending(connection: String): StagedRevision? { check(saved == null || this.connection == connection); return saved }
        override fun finish(staged: StagedRevision) { staged.file.delete(); saved = null; connection = null }
        override fun clear() { saved?.file?.delete(); saved = null; connection = null }
    }
    private class Server : AutoCloseable {
        private val socket = ServerSocket(0, 8, java.net.InetAddress.getByName("127.0.0.1"))
        val url = "http://127.0.0.1:${socket.localPort}/api/v1/realtime/"
        @Volatile var connection = "connection-one"
        @Volatile var failUpload = true
        val preparations = AtomicInteger()
        @Volatile private var stopped = false
        private val worker = Thread {
            while (!stopped) {
                try {
                    socket.accept().use { client ->
                        client.soTimeout = 5_000
                        val input = client.getInputStream().buffered()
                        fun line(): String {
                            val text = StringBuilder()
                            while (true) { val next = input.read(); if (next < 0 || next == 10) break; if (next != 13) text.append(next.toChar()) }
                            return text.toString()
                        }
                        val first = line().split(' ')
                        var size = 0
                        while (true) { val header = line(); if (header.isEmpty()) break
                            if (header.startsWith("Content-Length:", true)) size = header.substringAfter(':').trim().toInt()
                        }
                        var remaining = size
                        val buffer = ByteArray(4096)
                        while (remaining > 0) { val read = input.read(buffer, 0, minOf(buffer.size, remaining)); check(read > 0); remaining -= read }
                        val method = first[0]; val path = first[1]
                        val failed = method == "PUT" && failUpload
                        val body = when {
                            method == "GET" && path.endsWith("connections/current") -> JSONObject().put("connection", JSONObject().put("id", connection)).toString()
                            method == "POST" && path.endsWith("/uploads") -> { preparations.incrementAndGet(); "{\"id\":\"upload-one\",\"state\":\"PENDING\"}" }
                            method == "POST" && path.endsWith("/complete") -> "{\"id\":\"upload-one\",\"state\":\"READY\",\"versionNo\":2}"
                            failed -> "{\"error\":{\"code\":\"TEST_NETWORK_FAILURE\"}}"
                            else -> "{}"
                        }.toByteArray(Charsets.UTF_8)
                        client.getOutputStream().use { output ->
                            val status = if (failed) "500 Test Failure" else "200 OK"
                            output.write("HTTP/1.1 $status\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
                            output.write(body); output.flush()
                        }
                    }
                } catch (_: Exception) { if (stopped) break }
            }
        }.apply { isDaemon = true; start() }
        override fun close() { stopped = true; socket.close(); worker.join(2_000) }
    }
    private fun request() = WorkRevisionRequest("file-one", "version-one", Uri.parse("content://test-only/source"),
        "test.txt", "text/plain", UUID.randomUUID().toString())
    private suspend fun model(server: Server, records: Store, directory: File, owner: ViewModelStore): FileTransferViewModel = withContext(Dispatchers.Main) {
        FileTransferViewModel(RealtimeApi(server.url) { "test-only-credential" }, Safety(), records, directory).also {
            owner.put("model", it); yield(); it.unlockAfterUserVerification(); check(it.state.value.unlocked)
        }
    }
    @Test fun failedUploadRetriesSameJournalAndKeyThenCompletes() = runBlocking {
        val directory = java.nio.file.Files.createTempDirectory("bridge-test").toFile()
        val owner = ViewModelStore()
        Server().use { server ->
            val records = Store(directory)
            try {
                val model = model(server, records, directory, owner)
                val request = request()
                val first = model.stageRevision(request)
                try { model.uploadRevision(first) { _, _ -> }; fail("Expected injected upload failure") } catch (_: IllegalStateException) { }
                assertEquals(first.transferId, records.saved?.transferId)
                val retry = model.stageRevision(request)
                assertEquals(first.transferId, retry.transferId)
                assertEquals(1, directory.listFiles().orEmpty().size)
                server.failUpload = false
                assertEquals("READY", model.uploadRevision(retry) { _, _ -> }.getString("state"))
                assertEquals(2, server.preparations.get())
                assertNull(records.saved); assertFalse(first.file.exists())
            } finally { withContext(Dispatchers.Main) { owner.clear() }; directory.deleteRecursively() }
        }
    }
    @Test fun sameKeyWithChangedBytesCannotReplacePendingRevision() = runBlocking {
        val directory = java.nio.file.Files.createTempDirectory("bridge-test").toFile(); val owner = ViewModelStore()
        Server().use { server ->
            val records = Store(directory)
            try {
                val model = model(server, records, directory, owner); val request = request()
                val first = model.stageRevision(request); records.bytes = "changed-revision".toByteArray()
                try { model.stageRevision(request); fail("Changed bytes accepted") } catch (_: IllegalStateException) { }
                assertEquals(first.transferId, records.saved?.transferId)
                assertArrayEquals("original-revision".toByteArray(), first.file.readBytes())
                assertEquals(1, directory.listFiles().orEmpty().size)
            } finally { withContext(Dispatchers.Main) { owner.clear() }; directory.deleteRecursively() }
        }
    }
    @Test fun connectionChangeCannotUploadOldStagedRevision() = runBlocking {
        val directory = java.nio.file.Files.createTempDirectory("bridge-test").toFile(); val owner = ViewModelStore()
        Server().use { server ->
            val records = Store(directory)
            try {
                val model = model(server, records, directory, owner); val staged = model.stageRevision(request())
                server.connection = "connection-two"
                try { model.uploadRevision(staged) { _, _ -> }; fail("Old connection upload accepted") } catch (_: IllegalStateException) { }
                assertEquals(0, server.preparations.get()); assertFalse(staged.file.exists())
            } finally { withContext(Dispatchers.Main) { owner.clear() }; directory.deleteRecursively() }
        }
    }
    @Test fun concurrentSameKeyStagesShareSingleJournal() = runBlocking {
        val directory = java.nio.file.Files.createTempDirectory("bridge-test").toFile(); val owner = ViewModelStore()
        Server().use { server ->
            val records = Store(directory)
            try {
                val model = model(server, records, directory, owner); val request = request()
                val values = coroutineScope {
                    val first = async(Dispatchers.Default) { model.stageRevision(request) }
                    val second = async(Dispatchers.Default) { model.stageRevision(request) }
                    listOf(first.await(), second.await())
                }
                assertEquals(values[0].transferId, values[1].transferId)
                assertEquals(1, directory.listFiles().orEmpty().size)
            } finally { withContext(Dispatchers.Main) { owner.clear() }; directory.deleteRecursively() }
        }
    }
}
