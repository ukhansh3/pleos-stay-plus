package com.pleosstay.app.ui

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PublicShareLinkTest {
    @Test fun shareLinksRequireHttpsAndAPublicHost() {
        assertNotNull(publicHttpsLink("https://share.example.test/connect"))
        for (url in listOf("http://share.example.test/connect", "https://localhost:3001/connect",
            "https://127.0.0.1/connect", "https://dev.local/connect", "https://user@share.example.test/connect")) {
            assertNull(url, publicHttpsLink(url))
        }
    }
}
