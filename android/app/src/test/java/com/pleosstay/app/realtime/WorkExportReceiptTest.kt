package com.pleosstay.app.realtime

import com.pleosstay.app.documents.DocumentRef
import org.junit.Assert.assertThrows
import org.junit.Test

class WorkExportReceiptTest {
    private val profile = WorkExportProfile("server:owner", "pc-connection")
    private val source = DocumentRef(profile.userScope, profile.connectionId, "original-file", "base-version",
        "회의.docx", WORK_DOCX_MIME, "a".repeat(64), 100, 2)
    private val receipt = WorkExportReceipt("test-export", profile, source.name, 200,
        "b".repeat(64), "content://media/external/downloads/1", source)

    @Test fun exportWithNoSourceIsOriginalAndExactProfileMayUseRevisionSource() {
        profile.checkSource(null)
        profile.checkSource(source)
    }

    @Test fun sameDocumentNameDoesNotAuthorizeAnotherOwnerOrConnection() {
        assertThrows(IllegalStateException::class.java) { profile.checkSource(source.copy(userScope = "server:other")) }
        assertThrows(IllegalStateException::class.java) { profile.checkSource(source.copy(connectionId = "different-pc")) }
    }

    @Test fun stagedBytesMustMatchBothSizeAndShaOfConfirmedExport() {
        receipt.verifyContent(200, "b".repeat(64))
        assertThrows(IllegalStateException::class.java) { receipt.verifyContent(201, "b".repeat(64)) }
        assertThrows(IllegalStateException::class.java) { receipt.verifyContent(200, "c".repeat(64)) }
    }
}
