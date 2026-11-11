package com.pleosstay.app.realtime

import com.pleosstay.app.BuildConfig
import java.net.URI

/** Configuration validation only. Saved settings do not imply a reachable server or paired PC. */
internal object RealtimeConnectionSettings {
    fun error(url: String, credential: String, auth: String, key: String,
              local: Boolean, allowLocal: Boolean = BuildConfig.DEBUG): String? {
        if (local) {
            if (!allowLocal) return "로컬 연결은 사용할 수 없습니다."
            if (url.isBlank()) return "서버 주소를 입력해 주세요."
            if (!isLoopbackRealtimeEndpoint(url)) return "로컬 서버 주소를 확인해 주세요."
            if (credential.isBlank()) return "로컬 연결 토큰을 입력해 주세요."
            if (credential.length < 32 || credential.any(Char::isWhitespace)) return "로컬 연결 토큰을 확인해 주세요."
            return null
        }
        if (url.isBlank() || auth.isBlank() || key.isBlank()) return "PC 연결 서비스가 설정되지 않았습니다."
        return if (RealtimeDeployment.configured(url, auth, key, allowLoopback = allowLocal)) null else "PC 연결 서비스 설정을 확인해 주세요."
    }
}

internal fun isLoopbackRealtimeEndpoint(url: String): Boolean = runCatching {
    val uri = URI(url)
    uri.scheme == "http" && uri.host in listOf("10.0.2.2", "127.0.0.1", "localhost") &&
        uri.path.trimEnd('/') == "/api/v1/realtime" && uri.userInfo == null &&
        uri.query == null && uri.fragment == null && (uri.port == -1 || uri.port in 1..65535)
}.getOrDefault(false)
