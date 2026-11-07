package com.pleosstay.app.rest

import android.content.ContextWrapper
import android.media.MediaMetadataRetriever
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pleosstay.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RestStorageTest {
    @Test fun presetsAndSessionSurviveStoreRecreation() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(app.cacheDir, "rest-test-${UUID.randomUUID()}").apply { mkdirs() }
        val isolated = object : ContextWrapper(app) {
            override fun getNoBackupFilesDir(): File = directory
        }
        val store = RestStore(isolated)
        val preset = RestPreset.defaults.last().copy(id = "user-ev", name = "내 충전 휴식", target = 85)
        val session = RestSession(preset, 1_000, 3_601_000, "waiting",
            targetReached = true, targetPrompt = true, targetAlertAt = 1_500)
        store.savePresets(listOf(preset))
        store.saveSession(session)
        val reopened = RestStore(isolated)
        assertEquals(listOf(preset), reopened.presets())
        assertEquals(session, reopened.session())
        reopened.savePresets(emptyList())
        assertTrue(RestStore(isolated).presets().isEmpty())
    }


    @Test fun legacySessionDefaultsToSystemAlarmAndForegroundModePersists() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(app.cacheDir, "rest-compat-" + UUID.randomUUID()).apply { mkdirs() }
        val isolated = object : ContextWrapper(app) {
            override fun getNoBackupFilesDir(): File = directory
        }
        val store = RestStore(isolated)
        val session = RestSession(RestPreset.defaults.first(), 1_000, 601_000, null,
            alarmMode = RestAlarmMode.FOREGROUND_ONLY)
        store.saveSession(session)
        assertEquals(session, RestStore(isolated).session())
        val file = File(directory, "rest-session.json")
        val legacy = org.json.JSONObject(file.readText()).apply { remove("alarmMode") }
        file.writeText(legacy.toString())
        assertEquals(RestAlarmMode.SYSTEM_EXACT, RestStore(isolated).session()?.alarmMode)
    }

    @Test fun packagedRestSoundsHavePlayableMediaDurations() {
        val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
        for (resource in listOf(R.raw.rest_rain, R.raw.rest_soft, R.raw.rest_wake)) {
            resources.openRawResourceFd(resource).use { descriptor ->
                val reader = MediaMetadataRetriever()
                try {
                    reader.setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
                    val duration = reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
                    assertTrue(duration >= 2_900)
                } finally {
                    reader.release()
                }
            }
        }
    }
}
