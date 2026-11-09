package com.pleosstay.app.realtime

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.pleosstay.app.work.DocxDraftInfo
import com.pleosstay.app.work.docx.AndroidDocxFileGateway
import com.pleosstay.app.work.docx.OoxmlDocxEngine
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class WorkExportCatalogDeviceTest {
    @Test fun publishedDocxIsListedOnlyForConfirmedProfileAndMissingFilesAreOmitted() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val gateway = AndroidDocxFileGateway(context)
        val profile = WorkExportProfile("device-test:${UUID.randomUUID()}", UUID.randomUUID().toString())
        val bytes = OoxmlDocxEngine.blank().export()
        val name = "work-export-${UUID.randomUUID()}.docx"
        var uri: Uri? = null
        try {
            val published = gateway.writeDownload(name, bytes) { true }
            uri = published
            val catalog = WorkExportCatalog(context)
            assertTrue(catalog.list(profile).isEmpty())
            gateway.rememberDownload(published, bytes,
                DocxDraftInfo("device-test", profile.userScope, profile.connectionId, name, 0))
            val item = WorkExportCatalog(context).list(profile).single()
            assertEquals(name, item.name)
            assertEquals(bytes.size.toLong(), item.size)
            assertTrue(catalog.list(profile.copy(userScope = "device-test:other")).isEmpty())
            assertTrue(catalog.list(profile.copy(connectionId = "other-pc")).isEmpty())
            assertEquals(1, context.contentResolver.delete(published, null, null))
            uri = null
            assertTrue(catalog.list(profile).isEmpty())
        } finally {
            uri?.let { context.contentResolver.delete(it, null, null) }
            bytes.fill(0)
        }
    }
}
