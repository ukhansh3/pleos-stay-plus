package com.pleosstay.app.realtime

import org.junit.Assert.*
import org.junit.Test

class RealtimeConnectionSettingsTest {
    private val local = "http://10.0.2.2:3010/api/v1/realtime/"
    private val testCredential = "test-only-credential-".repeat(3)
    private fun error(url: String = local, token: String = testCredential, allowLocal: Boolean = true) =
        RealtimeConnectionSettings.error(url, token, "", "", local = true, allowLocal = allowLocal)

    @Test fun emptyTokenIsExplainedBeforeAnyNetworkCall() {
        assertEquals("로컬 연결 토큰을 입력해 주세요.", error(token = ""))
        assertEquals("로컬 연결 토큰을 확인해 주세요.", error(token = "short"))
        assertNull(error())
    }
    @Test fun localConfigurationIsDeniedInReleaseRegardlessOfValidCredentials() {
        assertEquals("로컬 연결은 사용할 수 없습니다.", error(allowLocal = false))
    }
    @Test fun localSecretsCannotBeSentToOtherHostsOrEmbeddedInUrls() {
        for (url in listOf("http://example.test/api/v1/realtime/", "http://10.0.2.2:3010/other",
            local + "?token=test", local + "#fragment", "http://user@10.0.2.2:3010/api/v1/realtime/",
            "http://10.0.2.2:0/api/v1/realtime/")) {
            assertNotNull(url, error(url = url))
        }
        assertNotNull(error(token = testCredential + "\n"))
    }
    @Test fun realSupabaseAuthCanUseLoopbackInDebugWithoutAnyFixedToken() {
        assertNull(RealtimeConnectionSettings.error("http://10.0.2.2:3012/api/v1/realtime/", "",
            "https://auth.example.test", "sb_publishable_test", local = false, allowLocal = true))
        assertNotNull(RealtimeConnectionSettings.error("http://10.0.2.2:3012/api/v1/realtime/", "",
            "https://auth.example.test", "sb_publishable_test", local = false, allowLocal = false))
        assertNotNull(RealtimeConnectionSettings.error("http://other.example.test/api/v1/realtime/", "",
            "https://auth.example.test", "sb_publishable_test", local = false, allowLocal = true))
        assertNotNull(RealtimeConnectionSettings.error("http://10.0.2.2:3012/api/v1/realtime/", "",
            "http://auth.example.test", "sb_publishable_test", local = false, allowLocal = true))
    }
    @Test fun missingServiceIsDifferentFromMalformedService() {
        assertEquals("PC 연결 서비스가 설정되지 않았습니다.",
            RealtimeConnectionSettings.error("", "", "", "", false))
        assertEquals("PC 연결 서비스 설정을 확인해 주세요.",
            RealtimeConnectionSettings.error("https://api.example.test/wrong-path", "", "https://auth.example.test", "sb_publishable_test", false))
        assertNull(RealtimeConnectionSettings.error("https://api.example.test/api/v1/realtime/", "",
            "https://auth.example.test", "sb_publishable_test", false))
    }
}
