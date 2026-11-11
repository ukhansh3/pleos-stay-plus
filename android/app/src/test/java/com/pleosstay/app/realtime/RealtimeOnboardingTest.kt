package com.pleosstay.app.realtime

import com.pleosstay.app.BuildConfig
import org.junit.Assert.*
import org.junit.Test

class RealtimeOnboardingTest {
    private fun session(config: RealtimeConnectionConfig) = RealtimeUiSession().also {
        it.configuration = config
        it.loaded = true
    }
    private val valid = RealtimeConnectionConfig("https://transfer.example.test/api/v1/realtime/",
        "https://auth.example.test", "sb_publishable_test")

    @Test fun connectIsExplicitAndNeverClaimsAnActualPcConnection() {
        val ui = session(valid)
        assertFalse(ui.connectionRequested)
        ui.connect()
        assertTrue(ui.connectionRequested) // authorization/PIN and HTTP pairing still happen separately
        assertNull(ui.error)
        assertFalse(ui.developerSettings)
    }
    @Test fun missingServiceDoesNotOpenSettingsOrInventPairing() {
        val ui = session(valid.copy(url = ""))
        ui.connect()
        assertFalse(ui.connectionRequested)
        assertFalse(ui.developerSettings)
        assertNotNull(ui.error)
        assertFalse(ui.error!!.contains("http"))
        assertFalse(ui.error!!.contains("token", ignoreCase = true))
        ui.configuration = valid
        ui.connect()
        assertTrue(ui.connectionRequested)
        assertNull(ui.error)
    }
    @Test fun loadingDoesNotStartOrOpenAConnection() {
        val ui = session(valid).also { it.loaded = false }
        ui.connect()
        assertFalse(ui.connectionRequested)
    }
    @Test fun developerSettingsAreExplicitAndBuildGated() {
        val ui = session(valid)
        ui.connect()
        ui.openDeveloperSettings()
        assertEquals(BuildConfig.DEBUG, ui.developerSettings)
        if (BuildConfig.DEBUG) assertFalse(ui.connectionRequested)
        ui.closeDeveloperSettings()
        assertFalse(ui.developerSettings)
        assertEquals(valid, ui.configuration)
    }
    @Test fun onlyDebugHasTheKnownAuthenticatedLocalServerDefault() {
        assertEquals("http://10.0.2.2:3012/api/v1/realtime/", RealtimeDeployment.defaultEndpoint("", true))
        assertEquals("", RealtimeDeployment.defaultEndpoint("", false))
        assertEquals(valid.url, RealtimeDeployment.defaultEndpoint(" " + valid.url + " ", true))
    }
    @Test fun credentialDataIsRedactedFromDiagnosticString() {
        assertEquals("RealtimeConnectionConfig(redacted)", valid.copy(credential = "test-only-not-a-real-token").toString())
    }
}
