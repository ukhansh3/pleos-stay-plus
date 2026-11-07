package com.pleosstay.app.work

import android.net.Uri
import com.pleosstay.app.documents.*
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** These are synthetic byte fixtures for provenance/storage, not claims of valid PDF rendering. */
class WorkDraftRepositoryTest {
    @Test fun persistsAndRestoresExactVersionAfterRepositoryRecreation() = runTest {
        val f = Fixture()
        val summary = f.retain()
        val recreated = WorkDraftRepository(f.store, f.gateway) { 90 }
        assertEquals(summary, recreated.restore(f.ref))
        assertFalse(summary.dirty)
        assertNull(summary.engineId)
    }

    @Test fun reimportNeverOverwritesUnsavedEngineSnapshot() = runTest {
        val f = Fixture(); f.retain()
        val snapshot = "opaque-engine-state".toByteArray()
        val dirty = f.repo.saveEngineSnapshot(f.ref, "test-only-engine", snapshot)
        assertTrue(dirty.dirty)
        assertEquals(dirty, f.retain())
        val record = WorkDraftCodec.decode(f.store.rows.getValue(dirty.id))
        try { assertArrayEquals(snapshot, record.engineSnapshot); assertArrayEquals(f.bytes, record.original) }
        finally { record.clearBuffers() }
    }

    @Test fun differentVersionsHaveIndependentRetainedRecords() = runTest {
        val f = Fixture(); val first = f.retain()
        val secondRef = f.ref.copy(versionId = "v2", versionNo = 2)
        f.gateway.refs += secondRef
        val second = f.withFile { f.repo.retainSource(secondRef, it) }
        assertNotEquals(first.id, second.id)
        assertEquals(2, f.repo.summaries().size)
    }

    @Test fun lockRevocationAndUserSwitchRetainButHideRecords() = runTest {
        val f = Fixture(); f.retain()
        f.gateway.access.value = DocumentAccess(false, "owner", "link", "LOCKED")
        assertFails { f.repo.restore(f.ref) }
        assertEquals(1, f.store.rows.size)
        f.gateway.access.value = DocumentAccess(true, "other-owner", "link", "READY")
        assertTrue(f.repo.summaries().isEmpty())
        assertFails { f.repo.restore(f.ref) }
        assertEquals(1, f.store.rows.size)
        f.gateway.access.value = DocumentAccess(true, "owner", "link", "READY")
        f.gateway.refs.clear()
        assertFails { f.repo.restore(f.ref) }
        assertEquals(1, f.store.rows.size)
    }

    @Test fun explicitDeleteDoesNotDeleteOtherVersionsOrServerSources() = runTest {
        val f = Fixture(); f.retain()
        val other = f.ref.copy(versionId = "v2", versionNo = 2); f.gateway.refs += other
        f.withFile { f.repo.retainSource(other, it) }
        f.repo.delete(f.ref)
        assertNull(f.repo.restore(f.ref))
        assertNotNull(f.repo.restore(other))
        assertEquals(2, f.gateway.refs.size)
    }

    @Test fun rejectsTamperedSourceBeforeWritingAnything() = runTest {
        val f = Fixture()
        f.withFile { file -> file.appendText("tampered"); assertFails { f.repo.retainSource(f.ref, file) } }
        assertTrue(f.store.rows.isEmpty())
    }

    @Test fun revokedDuringRevalidationNeverPersistsSource() = runTest {
        val f = Fixture()
        f.gateway.onValidate = { f.gateway.access.value = DocumentAccess(false, "owner", "link", "REVOKED") }
        assertFails { f.retain() }
        assertTrue(f.store.rows.isEmpty())
    }

    @Test fun rejectsCorruptRetainedRecordWithoutDeletingIt() = runTest {
        val f = Fixture(); val summary = f.retain()
        f.store.rows[summary.id] = byteArrayOf(0, 0, 0, 1)
        assertFails { f.repo.restore(f.ref) }
        assertTrue(f.store.rows.containsKey(summary.id))
    }

    @Test fun unsupportedTxtDoesNotBecomeAFakeWorkDocument() = runTest {
        val f = Fixture()
        val txt = f.ref.copy(mimeType = "text/plain", name = "fixture.txt"); f.gateway.refs += txt
        f.withFile { assertFails { f.repo.retainSource(txt, it) } }
        assertTrue(f.store.rows.isEmpty())
        assertFalse(WorkEngineReadiness().approved)
    }

    private suspend fun assertFails(block: suspend () -> Unit) {
        var failure: Exception? = null
        try { block() } catch (error: Exception) { failure = error }
        assertNotNull("Expected guarded operation to fail", failure)
    }
    private class Fixture {
        val bytes = "synthetic-test-provenance-bytes".toByteArray()
        val ref = DocumentRef("owner", "link", "file", "v1", "fixture.pdf", "application/pdf", WorkDraftRepository.digest(bytes), bytes.size.toLong(), 1)
        val gateway = WorkTestGateway(ref)
        val store = MemoryWorkRecords()
        val repo = WorkDraftRepository(store, gateway) { 50 }
        suspend fun retain() = withFile { repo.retainSource(ref, it) }
        suspend fun <T> withFile(block: suspend (File) -> T): T {
            val file = File.createTempFile("work-synthetic-", ".bin")
            return try { file.writeBytes(bytes); block(file) } finally { file.delete() }
        }
    }
}

internal class MemoryWorkRecords : DocumentRecordStore {
    val rows = mutableMapOf<String, ByteArray>()
    override suspend fun read(key: String) = rows[key]?.copyOf()
    override suspend fun write(key: String, bytes: ByteArray) { rows[key] = bytes.copyOf() }
    override suspend fun delete(key: String) { rows.remove(key)?.fill(0) }
    override suspend fun keys() = rows.keys.toList()
}

internal class WorkTestGateway(ref: DocumentRef) : SharedDocumentGateway {
    override val access = MutableStateFlow(DocumentAccess(true, ref.userScope, ref.connectionId, "READY"))
    val refs = mutableListOf(ref)
    var onValidate: () -> Unit = {}
    var beforeList: suspend () -> Unit = {}
    override suspend fun refreshAccess() = access.value
    override suspend fun listFiles(): List<DocumentFile> {
        beforeList()
        return refs.distinctBy { it.fileId }.map { DocumentFile(it.userScope, it.connectionId, it.fileId, it.name) }
    }
    override suspend fun listVersions(fileId: String) = refs.filter { it.fileId == fileId }
    override suspend fun validate(ref: DocumentRef): Boolean { onValidate(); return access.value.allowed && ref in refs }
    override suspend fun downloadVerified(ref: DocumentRef): DocumentOpenResult = DocumentOpenResult.Failed("TEST_NO_DOWNLOAD")
    override suspend fun uploadRevision(ref: DocumentRef, contentUri: Uri, idempotencyKey: String, progress: (Long, Long) -> Unit): DocumentUploadResult = DocumentUploadResult.Blocked("TEST_NO_UPLOAD")
    override fun clearTemporaryFiles() = Unit
    override fun close() = Unit
}
