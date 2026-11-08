package com.pleosstay.app.study

import java.nio.ByteBuffer
import java.nio.file.Files
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

class StudyDraftPersistenceTest {
    @Test fun oldVersionOneRecordsStillDecodeWithoutDraft() {
        val record=StudyRecord(StudyMaterialKey("owner","link","file","v1"),listOf(StudyNote("note",null,"kept",1)))
        val current=StudyRecordCodec.encode(record)
        val legacy=current.copyOf(current.size-1)
        ByteBuffer.wrap(legacy).putInt(1)
        assertEquals(record,StudyRecordCodec.decode(legacy,record.key))
    }
    @Test fun encryptedDraftSurvivesRepositoryRecreationAndStaysVersionScoped()=runTest {
        val directory=Files.createTempDirectory("study-draft").toFile()
        try {
            val key=StudyMaterialKey("owner","link","file","v1")
            val cipher=TestStudyCipher()
            val first=EncryptedStudyRepository(directory,cipher,StandardTestDispatcher(testScheduler))
            first.saveDraft(key,StudyNoteDraft("pending-id","unfinished"))
            val restored=EncryptedStudyRepository(directory,cipher,StandardTestDispatcher(testScheduler))
            assertEquals("unfinished",restored.load(key).draft!!.body)
            assertNull(restored.load(key.copy(versionId="v2")).draft)
            restored.saveNote(key,"pending-id",null,"unfinished")
            assertNull(restored.load(key).draft);assertEquals(1,restored.load(key).notes.size)
        } finally { directory.deleteRecursively() }
    }
}
