package com.pleosstay.app.realtime

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pleosstay.app.BuildConfig
import com.pleosstay.app.ui.premium.*
import com.pleosstay.app.waiting.safety.WaitingSafetyGateway
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal data class RealtimeConnectionConfig(
    val url: String, val authUrl: String, val publicKey: String,
    val local: Boolean = false, val credential: String = "",
) {
    fun error() = RealtimeConnectionSettings.error(url, credential, authUrl, publicKey, local)
    override fun toString() = "RealtimeConnectionConfig(redacted)"
}

/** Memory-only intent/UI state; credentials never enter SavedState. */
class RealtimeUiSession : ViewModel() {
    internal var configuration by mutableStateOf(RealtimeConnectionConfig(RealtimeDeployment.endpoint, RealtimeDeployment.auth, RealtimeDeployment.key))
    internal var loaded by mutableStateOf(false)
    internal var connectionRequested by mutableStateOf(false)
    internal var developerSettings by mutableStateOf(false)
    internal var error by mutableStateOf<String?>(null)

    internal fun connect() {
        if (!loaded) return
        if (configuration.error() != null) {
            error = "지금은 PC 연결을 사용할 수 없습니다. 잠시 후 다시 시도해 주세요."
            return
        }
        error = null
        connectionRequested = true
    }
    internal fun openDeveloperSettings() {
        if (!BuildConfig.DEBUG) return
        developerSettings = true
        connectionRequested = false
        error = null
    }
    internal fun closeDeveloperSettings() { developerSettings = false; error = null }
}

@Composable fun RealtimeEntry(context: Context, safety: WaitingSafetyGateway,
    foreground: Boolean = true, visible: Boolean = true,
    presentation: @Composable (@Composable () -> Unit) -> Unit = { it() },
    managedWorkspace: (@Composable (FileTransferViewModel, String) -> (@Composable () -> Unit))? = null,
    workspace: (@Composable (FileTransferViewModel, String) -> Unit)? = null,
    session: RealtimeUiSession = viewModel(key = "realtime-connection-ui"),
) {
    val app = context.applicationContext
    val configStore = remember { RealtimeTokenStore(app, "realtime-local-config.bin") }
    LaunchedEffect(Unit) {
        if (session.loaded) return@LaunchedEffect
        try {
            val saved = withContext(Dispatchers.IO) {
                File(app.noBackupFilesDir, "realtime/downloads").listFiles()?.forEach { it.delete() }
                configStore.read()
            }
            if (saved != null) {
                val j = JSONObject(saved)
                val savedLocal = j.optBoolean("local", true)
                val current = session.configuration
                if ((BuildConfig.DEBUG && savedLocal) || (!savedLocal &&
                    (current.error() != null || j.optString("url").trimEnd('/') == current.url.trimEnd('/')))) {
                    session.configuration = RealtimeConnectionConfig(j.getString("url"), j.optString("authUrl"),
                        j.optString("publicKey"), savedLocal, j.optString("token"))
                }
            }
        } catch (c: kotlinx.coroutines.CancellationException) { throw c }
        catch (_: Exception) { session.error = "저장한 연결을 불러오지 못했습니다. 다시 시도해 주세요." }
        session.loaded = true
    }
    val safetyState by safety.state.collectAsState()
    if (!safetyState.canUse) {
        if (visible) presentation { NoticePanel("주차 상태를 확인해 주세요", safetyState.message, true) }
        return
    }
    if (!session.loaded) {
        if (visible) presentation { NoticePanel("PC 연결 준비 중", "잠시만 기다려 주세요.") }
        return
    }
    val config = session.configuration
    if (BuildConfig.DEBUG && session.developerSettings) {
        if (visible) presentation {
            RealtimeDeveloperSettings(config, session::closeDeveloperSettings) { next ->
                withContext(Dispatchers.IO) {
                    configStore.write(JSONObject().put("url", next.url).put("token", if (next.local) next.credential else "")
                        .put("local", next.local).put("authUrl", next.authUrl).put("publicKey", next.publicKey).toString())
                }
                session.configuration = next
                session.connectionRequested = false
                session.closeDeveloperSettings()
            }
        }
        return
    }
    if (!session.connectionRequested || config.error() != null) {
        if (visible) presentation { RealtimeConnectScreen(session.error, config.local, session::connect) }
        return
    }
    val provider = remember(config.authUrl, config.publicKey, config.local) {
        if (config.local) null else RealtimeIdentityProvider(config.authUrl, config.publicKey,
            RealtimeTokenStore(app, "realtime-auth-" + config.authUrl.sha256() + ".bin"))
    }
    val modelKey = remember(config) { listOf(config.url, config.authUrl, config.publicKey, config.local.toString(), config.credential).joinToString("\u0000").sha256() }
    val model: FileTransferViewModel = viewModel(key = "realtime-" + modelKey, factory = remember(config, provider) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T =
                FileTransferViewModel(RealtimeApi(config.url) { provider?.validToken() ?: config.credential }, safety,
                    LocalFileStore(app), File(app.noBackupFilesDir, "realtime/downloads"), WorkExportCatalog(app)) {
                    provider?.resetAfterRevocation()
                    withContext(Dispatchers.IO) { RealtimePinStore(app).clearAfterUserVerification() }
                } as T
        }
    })
    DisposableEffect(model) { onDispose { model.lock() } }
    val protectedContent = managedWorkspace?.invoke(model, config.url)
    LaunchedEffect(foreground, model) { if (!foreground) model.lock() }
    if (!visible) return
    presentation {
        if (!foreground) NoticePanel("문서 화면 잠김", "PIN을 다시 확인해 주세요.")
        else RealtimeLockedScreen(model, remember { RealtimePinStore(app) }) {
            if (protectedContent != null) protectedContent()
            else if (workspace == null) FileTransferScreen(model) else workspace(model, config.url)
        }
    }
}

private fun String.sha256() = java.security.MessageDigest.getInstance("SHA-256")
    .digest(toByteArray()).joinToString("") { "%02x".format(it) }
