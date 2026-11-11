package com.pleosstay.app.realtime

import com.pleosstay.app.BuildConfig
import com.pleosstay.app.ui.publicHttpsLink
import org.json.JSONObject
import java.util.Base64

internal object RealtimeDeployment {
    val endpoint = defaultEndpoint(BuildConfig.REALTIME_API_BASE_URL, BuildConfig.DEBUG)
    val auth = BuildConfig.REALTIME_SUPABASE_URL.trim().ifBlank { BuildConfig.SUPABASE_URL.trim() }
    val key = BuildConfig.REALTIME_SUPABASE_PUBLIC_KEY.trim().ifBlank { BuildConfig.SUPABASE_PUBLISHABLE_KEY.trim() }
    val guide = publicHttpsLink(BuildConfig.REALTIME_PAIRING_PAGE_URL.trim())
        ?.takeIf { it.path.trimEnd('/') == "/connect" && it.rawQuery == null && it.rawFragment == null && it.toString().length <= 512 }?.toString()

    fun configured(endpoint: String, auth: String, key: String, allowLoopback: Boolean = BuildConfig.DEBUG): Boolean {
        // The actual authenticated local server is also usable in Debug; it still requires Supabase Auth.
        // Release accepts public HTTPS only. This is separate from fixed-token local QA authentication.
        val api = publicHttpsLink(endpoint) ?: if (allowLoopback && isLoopbackRealtimeEndpoint(endpoint))
            java.net.URI(endpoint) else return false
        val identity = publicHttpsLink(auth) ?: return false
        return api.path.trimEnd('/') == "/api/v1/realtime" &&
            api.rawQuery == null && api.rawFragment == null &&
            identity.path.trimEnd('/').isEmpty() && identity.rawQuery == null && identity.rawFragment == null &&
            publishable(key)
    }

    internal fun defaultEndpoint(configured: String, debug: Boolean): String = configured.trim().ifBlank {
        if (debug) "http://10.0.2.2:3012/api/v1/realtime/" else ""
    }

    private fun publishable(key: String): Boolean = key.startsWith("sb_publishable_") || runCatching {
        val payload = Base64.getUrlDecoder().decode(key.split('.')[1]).toString(Charsets.UTF_8)
        JSONObject(payload).optString("role") == "anon"
    }.getOrDefault(false)
}
