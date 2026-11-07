package com.pleosstay.app.documents

import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AndroidEncryptedDocumentStoreTest {
    @Test fun opaqueRecordsSurviveStoreReopenAndAreNamespaceIsolated() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val namespace = "test-${UUID.randomUUID()}"
        val first = AndroidEncryptedDocumentStore(context, namespace)
        val key = "scope/connection/file/version"
        val value = "private-note-content".toByteArray()
        try {
            first.write(key, value)
            val reopened = AndroidEncryptedDocumentStore(context, namespace)
            assertArrayEquals(value, reopened.read(key))
            assertEquals(listOf(key), reopened.keys())
            assertNull(AndroidEncryptedDocumentStore(context, "$namespace-other").read(key))
            val files = java.io.File(context.noBackupFilesDir, "documents/records").walkTopDown().filter { it.isFile }.toList()
            assertTrue(files.none { it.name.contains("scope") })
            assertTrue(files.none { it.readBytes().toString(Charsets.ISO_8859_1).contains("private-note-content") })
            reopened.delete(key); assertNull(reopened.read(key))
        } finally { first.delete(key) }
    }
}
