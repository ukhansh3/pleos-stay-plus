package com.pleosstay.app.ui.activity

import androidx.compose.runtime.staticCompositionLocalOf
import com.pleosstay.app.modes.*
import com.pleosstay.app.rest.RestPreset

internal interface ActivityUiActions {
    fun startRest(preset:RestPreset)
    fun startStudy(minutes:Int)
    fun startWork()
    fun finish(mode:ActivityMode)
    fun home()
    fun choose(mode:ActivityMode,choice:WaitingChoice)
    fun consentRest()
    fun independentRest()
    fun retry(mode:ActivityMode)
}
/** MainActivity always supplies this. Null is reserved for isolated screens/previews/tests. */
internal val LocalActivityUiActions=staticCompositionLocalOf<ActivityUiActions?>{null}
