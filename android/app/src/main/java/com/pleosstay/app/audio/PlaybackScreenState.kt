package com.pleosstay.app.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.core.content.ContextCompat

/** Android display inactivity signal for foreground-only playback. */
internal class PlaybackScreenState(context: Context) {
    private val appContext = context.applicationContext
    private val power = appContext.getSystemService(PowerManager::class.java)
    private var receiver: BroadcastReceiver? = null

    val isInteractive: Boolean get() = power?.isInteractive == true

    fun watch(onScreenOff: () -> Unit) {
        stop()
        val watcher = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (receiver === this && intent.action == Intent.ACTION_SCREEN_OFF) onScreenOff()
            }
        }
        ContextCompat.registerReceiver(
            appContext, watcher, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        receiver = watcher
    }

    fun stop() {
        val watcher = receiver ?: return
        receiver = null
        runCatching { appContext.unregisterReceiver(watcher) }
    }
}
