package com.pleosstay.app.study

import java.io.File
import java.nio.file.Files
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

internal class TestStudyCipher : StudyCipher {
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    override fun encrypt(plain: ByteArray, context: ByteArray): ByteArray = Cipher.getInstance("AES/GCM/NoPadding").run {
        init(Cipher.ENCRYPT_MODE, key); updateAAD(context); iv + doFinal(plain)
    }
    override fun decrypt(sealed: ByteArray, context: ByteArray): ByteArray = Cipher.getInstance("AES/GCM/NoPadding").run {
        init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, sealed.copyOfRange(0, 12))); updateAAD(context); doFinal(sealed.copyOfRange(12, sealed.size))
    }
}

class StudyRepositoryTest {
    private val key = StudyMaterialKey("owner", "connection", "file", "v1")
    @Test fun notesCrudProgressAndRestartAreEncryptedAndVersionScoped() = runTest {
        val directory = Files.createTempDirectory("study-test").toFile()
        try {
            val cipher = TestStudyCipher()
            val first = EncryptedStudyRepository(directory, cipher)
            val note = first.saveNote(key, null, "2", "한글 메모 confidential")
            first.saveNote(key, note.id, "2", "수정한 confidential 메모")
            val progress = StudyProgress("PDF_PAGE", "2", .75f, true, 123)
            first.saveProgress(key, progress)
            val restarted = EncryptedStudyRepository(directory, cipher)
            assertEquals("수정한 confidential 메모", restarted.load(key).notes.single().body)
            assertEquals(progress, restarted.load(key).progress)
            assertEquals(key, restarted.lastMaterial(key.userScope, key.connectionId))
            assertTrue(restarted.load(key.copy(versionId = "v2")).notes.isEmpty())
            assertTrue(restarted.load(key.copy(userScope = "other")).notes.isEmpty())
            assertNull(restarted.lastMaterial("other", key.connectionId))
            directory.listFiles()!!.forEach { assertFalse(it.readBytes().toString(Charsets.ISO_8859_1).contains("confidential")) }
            restarted.deleteNote(key, note.id)
            assertTrue(restarted.load(key).notes.isEmpty())
            assertEquals(progress, restarted.load(key).progress)
            restarted.deleteMaterialData(key)
            assertNull(restarted.load(key).progress)
            assertNull(restarted.lastMaterial(key.userScope, key.connectionId))
        } finally { directory.deleteRecursively() }
    }
    @Test fun ciphertextTamperingAndRecordSwappingNeverBecomeEmptySuccess() = runTest {
        val directory = Files.createTempDirectory("study-test").toFile()
        try {
            val repo = EncryptedStudyRepository(directory, TestStudyCipher())
            repo.saveNote(key, null, null, "private")
            val original = File(directory, key.stableId() + ".study")
            val other = key.copy(versionId = "v2")
            original.copyTo(File(directory, other.stableId() + ".study"))
            try { repo.load(other); fail("swapped record accepted") } catch (_: javax.crypto.AEADBadTagException) { }
            val bytes = original.readBytes(); bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte(); original.writeBytes(bytes)
            try { repo.load(key); fail("tampered record accepted") } catch (_: javax.crypto.AEADBadTagException) { }
        } finally { directory.deleteRecursively() }
    }
    @Test fun lengthPrefixedIdentityAvoidsConcatenationCollision() {
        assertNotEquals(key.copy(userScope = "ab", connectionId = "c").stableId(), key.copy(userScope = "a", connectionId = "bc").stableId())
    }
}
