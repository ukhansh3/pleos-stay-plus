package com.pleosstay.app.study

import android.graphics.Bitmap
import android.net.Uri
import com.pleosstay.app.documents.*
import com.pleosstay.app.modes.*
import com.pleosstay.app.waiting.MemoryJournal
import com.pleosstay.app.waiting.MemoryWaitingStore
import com.pleosstay.app.waiting.data.LocalWaitingRepository
import com.pleosstay.app.waiting.domain.WaitingClock
import com.pleosstay.app.waiting.safety.*
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StudyViewModelTest {
    @Test fun recoveryAndInvalidDurationDoNotStartTimerThenReadyPreservesWaitingOnEnd() = runTest {
        val f = Fixture(this)
        try {
            f.vm.start(30); runCurrent()
            assertFalse(f.vm.state.value.active); assertTrue(f.store.rows.isEmpty())
            f.waiting.restore()
            f.vm.start(1); runCurrent(); assertFalse(f.vm.state.value.active)
            f.vm.start(30); runCurrent()
            assertTrue(f.vm.state.value.active); assertEquals(1, f.store.rows.size)
            f.vm.end(); runCurrent()
            assertFalse(f.vm.state.value.active); assertEquals(0, f.store.endings)
        } finally { f.close() }
    }
    @Test fun needsChoiceBackShortenAndExtendAreResolvedBySharedManager() = runTest {
        val f = Fixture(this)
        try {
            f.waiting.restore(); f.waiting.start(10)
            f.vm.start(30); runCurrent()
            assertNotNull(f.vm.state.value.prompt); assertFalse(f.vm.state.value.active)
            f.vm.choose(WaitingChoice.BACK); runCurrent()
            assertFalse(f.vm.state.value.active); assertNull(f.vm.state.value.prompt)
            f.vm.start(30); runCurrent(); f.vm.choose(WaitingChoice.SHORTEN); runCurrent()
            assertTrue(f.vm.state.value.active); assertTrue(f.vm.state.value.remainingMillis <= 10 * 60000L)
            f.vm.end(); f.vm.start(30); runCurrent(); f.vm.choose(WaitingChoice.EXTEND); runCurrent()
            assertTrue(f.vm.state.value.active); assertEquals(1, f.store.rows.size)
            assertTrue(f.store.rows.values.single().revision > 0)
        } finally { f.close() }
    }
    @Test fun storageFailureAndUnknownCannotStart() = runTest {
        val f = Fixture(this)
        try {
            f.waiting.restore(); f.store.failWrites = true
            f.vm.start(30); runCurrent(); assertFalse(f.vm.state.value.active)
            f.store.failWrites = false
            f.safety.state.value = WaitingSafety(); runCurrent()
            f.vm.start(30); runCurrent(); assertFalse(f.vm.state.value.active)
            assertFalse(f.vm.state.value.allowed)
        } finally { f.close() }
    }
    @Test fun authorizedVersionNotesRestoreButRevocationAndDrivingImmediatelyMaskContent() = runTest {
        val f = Fixture(this)
        try {
            f.waiting.restore(); f.vm.start(30); runCurrent()
            f.vm.open(f.ref); runCurrent(); assertNotNull(f.vm.state.value.document)
            f.vm.saveNote(null, "private note"); runCurrent()
            assertEquals("private note", f.vm.state.value.record!!.notes.single().body)
            f.safety.state.value = WaitingSafety(VehicleMotion.DRIVING, true); runCurrent()
            assertNull(f.vm.state.value.document); assertNull(f.vm.state.value.record); assertNull(f.vm.state.value.selected)
            f.safety.state.value = WaitingSafety(VehicleMotion.PARKED, false); runCurrent()
            f.vm.restoreLast(); runCurrent()
            assertEquals("private note", f.vm.state.value.record!!.notes.single().body)
            f.gateway.access.value = DocumentAccess(false, reason = "REVOKED", revision = 2); runCurrent()
            assertNull(f.vm.state.value.record); assertTrue(f.vm.state.value.files.isEmpty())
            assertEquals("private note", f.records.load(f.ref.materialKey()).notes.single().body)
        } finally { f.close() }
    }
    @Test fun failedFreshValidationNeverDisplaysCachedSourceAndBackgroundMasksNotes() = runTest {
        val f = Fixture(this)
        try {
            f.waiting.restore(); f.vm.start(30); runCurrent(); f.vm.open(f.ref); runCurrent()
            f.gateway.valid = false; f.vm.move(0); runCurrent()
            assertNull(f.vm.state.value.document)
            f.gateway.valid = true; f.vm.open(f.ref); runCurrent(); assertNotNull(f.vm.state.value.document)
            f.vm.setForeground(false); runCurrent()
            assertNull(f.vm.state.value.document); assertFalse(f.vm.state.value.allowed)
        } finally { f.close() }
    }
    @Test fun failedNoteSaveDoesNotEmitSuccessAndLeavingNeverEndsWaiting() = runTest {
        val f = Fixture(this)
        try {
            f.waiting.restore(); f.vm.start(30); runCurrent(); f.vm.open(f.ref); runCurrent()
            f.failNoteSave = true
            f.vm.saveNote(null, "keep editor input"); runCurrent()
            assertEquals(0L, f.vm.state.value.noteSaveSerial)
            assertTrue(f.vm.state.value.record!!.notes.isEmpty())
            f.vm.leave(); runCurrent()
            assertFalse(f.vm.state.value.active); assertNull(f.vm.state.value.document)
            assertEquals(0, f.store.endings)
        } finally { f.close() }
    }
    private class Safety : WaitingSafetyGateway {
        override val state = MutableStateFlow(WaitingSafety(VehicleMotion.PARKED, false))
        override val drivingEvents = DrivingEventBuffer()
        override fun start() = Unit
        override fun stop() = Unit
        override fun close() = Unit
    }
    private class Gateway(val ref: DocumentRef, val file: File) : SharedDocumentGateway {
        override val access = MutableStateFlow(DocumentAccess(true, "user", "connection", "READY", 1))
        var valid = true
        override suspend fun refreshAccess() = access.value
        override suspend fun listFiles() = listOf(DocumentFile("user", "connection", "file", "source.txt"))
        override suspend fun listVersions(fileId: String) = listOf(ref)
        override suspend fun validate(ref: DocumentRef) = valid && access.value.allowed
        override suspend fun downloadVerified(ref: DocumentRef) = DocumentOpenResult.Ready(file, ref)
        override suspend fun uploadRevision(ref: DocumentRef, contentUri: Uri, idempotencyKey: String, progress: (Long, Long) -> Unit): DocumentUploadResult = error("Study cannot upload")
        override fun clearTemporaryFiles() = Unit
        override fun close() = Unit
    }
    private class Fixture(scope: TestScope) {
        val directory = Files.createTempDirectory("study-vm").toFile()
        private val dispatcher = StandardTestDispatcher(scope.testScheduler)
        val safety = Safety()
        val store = MemoryWaitingStore()
        private val now = System.currentTimeMillis()
        val waiting = LocalWaitingRepository(store, MemoryJournal(), { safety.state.value }, WaitingClock { now }, dispatcher = dispatcher)
        val ref = DocumentRef("user", "connection", "file", "version", "source.txt", "text/plain", "0".repeat(64), 10, 1)
        val gateway = Gateway(ref, File(directory, "source.txt"))
        val records = EncryptedStudyRepository(File(directory, "records"), TestStudyCipher(), dispatcher)
        var failNoteSave = false
        val vm: StudyViewModel
        init {
            Dispatchers.setMain(dispatcher)
            vm = StudyViewModel(ModeStartManager(waiting, safety), safety, gateway, object : StudyRepository by records {
                override suspend fun saveNote(key: StudyMaterialKey, id: String?, anchor: String?, body: String): StudyNote {
                    check(!failNoteSave) { "test disk failure" }
                    return records.saveNote(key, id, anchor, body)
                }
            }, object : StudyReader {
                override suspend fun open(file: File, mimeType: String) = StudyDocument(StudyFormat.TXT, file, blocks = listOf(StudyBlock.Paragraph(listOf(StudyTextRun("source")))), textOffsets = listOf(0))
                override suspend fun renderPage(document: StudyDocument, pageIndex: Int, zoom: Float): Bitmap = error("not PDF")
            })
            vm.setForeground(true)
        }
        fun close() { vm.close(); Dispatchers.resetMain(); directory.deleteRecursively() }
    }
}
