package com.pleosstay.app.documents

import android.net.Uri
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RealtimeDocumentGatewayTest {
    private val namespace = "a".repeat(64)
    private val bytes = "document test".toByteArray()
    private fun ref() = DocumentRef("$namespace:user", "connection", "file", "version", "test.txt", "text/plain",
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }, bytes.size.toLong(), 1)
    private inner class Transport : DocumentTransport {
        var identity = DocumentServerIdentity("user", "connection")
        var unavailable = false
        var offered = ref()
        var downloadBytes = bytes
        var downloads = 0
        var afterDownload: suspend (File) -> Unit = {}
        override suspend fun identity(): DocumentServerIdentity { check(!unavailable); return identity }
        override suspend fun files(userScope: String, connectionId: String) = listOf(DocumentFile(userScope, connectionId, "file", "test.txt"))
        override suspend fun versions(userScope: String, connectionId: String, fileId: String) = listOf(offered)
        override suspend fun download(ref: DocumentRef, directory: File): File {
            downloads++; directory.mkdirs()
            return File(directory, "download.txt").apply { writeBytes(downloadBytes) }.also { afterDownload(it) }
        }
        override suspend fun upload(ref: DocumentRef, uri: Uri, key: String, progress: (Long, Long) -> Unit): DocumentRef = error("NOT_USED")
    }
    @Test fun repeatedSuccessfulAuthorizationDoesNotInvalidateReaders() = runTest {
        val directory = java.nio.file.Files.createTempDirectory("documents-test").toFile()
        val client = MutableStateFlow(DocumentClientAccess(true, true, true))
        val gateway = RealtimeDocumentGateway(Transport(), client, backgroundScope, namespace, directory)
        try {
            assertTrue(gateway.refreshAccess().allowed)
            val revision = gateway.access.value.revision
            assertTrue(gateway.validate(ref()))
            assertEquals(revision, gateway.refreshAccess().revision)
            assertEquals(listOf(ref()), gateway.listVersions("file"))
        } finally { gateway.close(); directory.deleteRecursively() }
    }
    @Test fun pinOrForegroundLockImmediatelyDeniesAndRemovesTemporaryContent() = runTest {
        val directory = java.nio.file.Files.createTempDirectory("documents-test").toFile()
        val client = MutableStateFlow(DocumentClientAccess(true, true, true))
        val gateway = RealtimeDocumentGateway(Transport(), client, backgroundScope, namespace, directory)
        try {
            gateway.refreshAccess(); File(directory, "private.txt").writeText("temporary")
            runCurrent(); client.value = DocumentClientAccess(true, true, false); runCurrent()
            assertFalse(gateway.access.value.allowed)
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        } finally { gateway.close(); directory.deleteRecursively() }
    }
    @Test fun remoteUserOrConnectionSwitchRequiresRelock() = runTest {
        val directory = java.nio.file.Files.createTempDirectory("documents-test").toFile()
        val transport = Transport(); var relocks = 0
        val gateway = RealtimeDocumentGateway(transport, MutableStateFlow(DocumentClientAccess(true, true, true)), backgroundScope, namespace, directory) { relocks++ }
        try {
            gateway.refreshAccess(); transport.identity = DocumentServerIdentity("other-user", "other-connection")
            assertFalse(gateway.refreshAccess().allowed); assertEquals(1, relocks)
        } finally { gateway.close(); directory.deleteRecursively() }
    }
    @Test fun authorizationNetworkFailureCannotUseCachedGrant() = runTest {
        val directory = java.nio.file.Files.createTempDirectory("documents-test").toFile()
        val transport = Transport()
        val gateway = RealtimeDocumentGateway(transport, MutableStateFlow(DocumentClientAccess(true, true, true)), backgroundScope, namespace, directory)
        try { gateway.refreshAccess(); transport.unavailable = true; assertFalse(gateway.refreshAccess().allowed) }
        finally { gateway.close(); directory.deleteRecursively() }
    }
    @Test fun verifiedBytesOnlyAndNoCrossVersionRestoration() = runTest {
        val directory = java.nio.file.Files.createTempDirectory("documents-test").toFile()
        val transport = Transport()
        val gateway = RealtimeDocumentGateway(transport, MutableStateFlow(DocumentClientAccess(true, true, true)), backgroundScope, namespace, directory)
        try {
            assertFalse(gateway.validate(ref().copy(versionId = "other")))
            assertFalse(gateway.validate(ref().copy(userScope = "$namespace:other")))
            assertEquals(0, transport.downloads)
            val opened = gateway.downloadVerified(ref()) as DocumentOpenResult.Ready
            assertArrayEquals(bytes, opened.file.readBytes())
        } finally { gateway.close(); directory.deleteRecursively() }
    }
    @Test fun malformedServerVersionCannotBecomeReady() = runTest {
        val directory = java.nio.file.Files.createTempDirectory("documents-test").toFile()
        val transport = Transport().apply { offered = ref().copy(sizeBytes = -1) }
        val gateway = RealtimeDocumentGateway(transport, MutableStateFlow(DocumentClientAccess(true, true, true)), backgroundScope, namespace, directory)
        try {
            assertFalse(gateway.validate(ref()))
            assertFalse(gateway.access.value.allowed); assertEquals(0, transport.downloads)
        } finally { gateway.close(); directory.deleteRecursively() }
    }
    @Test fun wrongHashAndSizeAreNeverReturned() = runTest {
        for (corrupt in listOf("wrong length".toByteArray(), ByteArray(bytes.size) { 1 })) {
            val directory = java.nio.file.Files.createTempDirectory("documents-test").toFile()
            val transport = Transport().apply { downloadBytes = corrupt }
            val gateway = RealtimeDocumentGateway(transport, MutableStateFlow(DocumentClientAccess(true, true, true)), backgroundScope, namespace, directory)
            try {
                assertTrue(gateway.downloadVerified(ref()) is DocumentOpenResult.Failed)
                assertTrue(directory.listFiles().orEmpty().isEmpty())
            } finally { gateway.close(); directory.deleteRecursively() }
        }
    }
    @Test fun cancellationAfterTransportCreatesFileCannotLeavePlaintextOrphan() = runTest {
        val directory = java.nio.file.Files.createTempDirectory("documents-test").toFile()
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val transport = Transport().apply {
            afterDownload = { file -> withContext(NonCancellable) {
                entered.complete(Unit); release.await()
                // Mimics a final transport rename racing the lock's earlier cleanup.
                file.writeBytes(bytes)
            } }
        }
        val client = MutableStateFlow(DocumentClientAccess(true, true, true))
        val gateway = RealtimeDocumentGateway(transport, client, backgroundScope, namespace, directory)
        try {
            val result = async { gateway.downloadVerified(ref()) }
            entered.await(); client.value = DocumentClientAccess(true, false, true); runCurrent()
            release.complete(Unit)
            try { result.await(); fail("External PIN lock did not cancel download") } catch (_: CancellationException) { }
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        } finally { release.complete(Unit); gateway.close(); directory.deleteRecursively() }
    }
}
