package com.pleosstay.app.ui

/** Main-thread lifecycle ownership for process-scoped safety/audio dependencies.
 * An older Activity's delayed pause/stop must not hide the current top Activity.
 * No owner means blocked; this never supplies gear, speed or UX permission.
 */
internal class ActivityForegroundOwner(private val update: (Boolean) -> Unit) {
    private var current: Any? = null

    fun claim(owner: Any) {
        if (current === owner) return
        current = owner
        // A new owner must reacquire fresh vehicle signals even across an Activity handoff.
        update(true)
    }

    fun release(owner: Any) {
        if (current !== owner) return
        current = null
        update(false)
    }
}
