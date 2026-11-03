package com.pleosstay.app.pickup.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DriverAuthSession(
    val userId: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochSeconds: Long,
)

class DriverIdentityException(message: String, val blocked: Boolean = false) : Exception(message)

interface DriverIdentityProvider {
    suspend fun validSession(): DriverAuthSession
}

private interface DriverAuthSessionStore {
    fun read(): DriverAuthSession?
    fun write(session: DriverAuthSession)
    fun clear()
}

/** Access/refresh tokens are encrypted with an app-private Android Keystore AES key. */
private class KeystoreDriverAuthSessionStore(context: Context) : DriverAuthSessionStore {
    private val preferences = context.getSharedPreferences("pickup_driver_auth", Context.MODE_PRIVATE)

    override fun read(): DriverAuthSession? = runCatching {
        val encoded = preferences.getString(DATA, null) ?: return null
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        require(bytes.size > IV_SIZE)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, IV_SIZE))
        val json = JSONObject(String(cipher.doFinal(bytes.copyOfRange(IV_SIZE, bytes.size)), StandardCharsets.UTF_8))
        DriverAuthSession(
            userId = json.getString("userId"),
            accessToken = json.getString("accessToken"),
            refreshToken = json.getString("refreshToken"),
            expiresAtEpochSeconds = json.getLong("expiresAt"),
        )
    }.getOrNull()

    override fun write(session: DriverAuthSession) {
        val json = JSONObject()
            .put("userId", session.userId)
            .put("accessToken", session.accessToken)
            .put("refreshToken", session.refreshToken)
            .put("expiresAt", session.expiresAtEpochSeconds)
            .toString().toByteArray(StandardCharsets.UTF_8)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(json)
        preferences.edit().putString(DATA, Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)).apply()
    }

    override fun clear() { preferences.edit().remove(DATA).apply() }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
            generateKey()
        }
    }

    private companion object {
        const val DATA = "session"
        const val ALIAS = "pleos_stay_pickup_driver_auth_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
    }
}

class SupabaseRestDriverIdentityProvider(
    context: Context,
    supabaseUrl: String,
    private val publishableKey: String,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1_000 },
) : DriverIdentityProvider {
    private val baseUrl = supabaseUrl.trimEnd('/')
    private val store: DriverAuthSessionStore = KeystoreDriverAuthSessionStore(context.applicationContext)

    override suspend fun validSession(): DriverAuthSession = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank() || publishableKey.isBlank()) {
            throw DriverIdentityException("Supabase 공개 설정이 없어 Pickup 인증을 시작할 수 없습니다", blocked = true)
        }
        val current = store.read()
        if (current != null && current.expiresAtEpochSeconds > nowEpochSeconds() + 60) return@withContext current
        if (current != null) {
            val refreshed = runCatching { refresh(current) }.getOrNull()
            if (refreshed != null) {
                store.write(refreshed)
                return@withContext refreshed
            }
            store.clear()
        }
        signInAnonymously().also(store::write)
    }

    private fun signInAnonymously(): DriverAuthSession = authRequest("signup", JSONObject(), null)

    private fun refresh(current: DriverAuthSession): DriverAuthSession = authRequest(
        "token?grant_type=refresh_token",
        JSONObject().put("refresh_token", current.refreshToken),
        current.userId,
    )

    private fun authRequest(path: String, body: JSONObject, knownUserId: String?): DriverAuthSession {
        val connection = URI("$baseUrl/auth/v1/$path").toURL().openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 5_000
            connection.readTimeout = 15_000
            connection.doOutput = true
            connection.setRequestProperty("apikey", publishableKey)
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val json = JSONObject(stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty().ifBlank { "{}" })
            if (status !in 200..299) {
                val disabled = status == 400 && json.optString("msg").contains("anonymous", ignoreCase = true)
                throw DriverIdentityException(
                    if (disabled) "Supabase 익명 로그인이 비활성화되어 있습니다" else "Pickup 운전자 인증에 실패했습니다 ($status)",
                    blocked = disabled,
                )
            }
            DriverAuthSession(
                userId = json.optJSONObject("user")?.optString("id").orEmpty().ifBlank { knownUserId ?: error("인증 사용자 ID 누락") },
                accessToken = json.getString("access_token"),
                refreshToken = json.getString("refresh_token"),
                expiresAtEpochSeconds = nowEpochSeconds() + json.optLong("expires_in", 3600),
            )
        } finally { connection.disconnect() }
    }
}
