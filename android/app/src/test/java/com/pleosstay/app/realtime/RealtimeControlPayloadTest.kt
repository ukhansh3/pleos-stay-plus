package com.pleosstay.app.realtime

import org.junit.Assert.*
import org.junit.Test

class RealtimeControlPayloadTest {
    @Test fun bodylessControlWritesAlwaysHaveAnExplicitJsonObject() {
        for (method in listOf("POST", "PUT", "PATCH", "DELETE", "post"))
            assertEquals(method, "{}", realtimeControlPayload(method, null))
    }
    @Test fun readRequestsDoNotAcquireRequestBodies() {
        for (method in listOf("GET", "HEAD")) assertNull(realtimeControlPayload(method, null))
    }
    @Test fun suppliedControlDataAndUtf8TextArePreserved() {
        val body = """{"name":"테스트 차량"}"""
        assertEquals(body, realtimeControlPayload("POST", body))
    }
}
