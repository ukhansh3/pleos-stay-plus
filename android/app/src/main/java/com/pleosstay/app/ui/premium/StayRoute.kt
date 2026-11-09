package com.pleosstay.app.ui.premium

import com.pleosstay.app.R

enum class NavigationGroup { HOME, PARKING, PICKUP, MODES, FILES, SETTINGS }

enum class StayRoute(val group: NavigationGroup, val label: Int) {
    HOME(NavigationGroup.HOME, R.string.stay_home),
    PARKING(NavigationGroup.PARKING, R.string.stay_parking_group),
    WAITING(NavigationGroup.PARKING, R.string.stay_waiting),
    PICKUP(NavigationGroup.PICKUP, R.string.stay_pickup),
    FILES(NavigationGroup.FILES, R.string.stay_files),
    MODES(NavigationGroup.MODES, R.string.stay_modes),
    REST(NavigationGroup.MODES, R.string.stay_rest),
    STUDY(NavigationGroup.MODES, R.string.stay_study),
    WORK(NavigationGroup.MODES, R.string.stay_work),
    SETTINGS(NavigationGroup.SETTINGS, R.string.stay_settings);
}

// Ignore obsolete or unknown route names when restoring the Android saved state.
internal fun submissionRouteStack(saved: List<String>): List<String> =
    saved.filter { name -> StayRoute.entries.any { it.name == name } }
        .ifEmpty { listOf(StayRoute.HOME.name) }

fun pushRoute(stack: List<String>, route: StayRoute): List<String> {
    val available = submissionRouteStack(stack)
    return if (available.lastOrNull() == route.name) available
    else if (route == StayRoute.HOME) listOf(StayRoute.HOME.name)
    else if (route.isActivity() || StayRoute.valueOf(available.last()).isActivity())
        available.filterNot { StayRoute.valueOf(it).isActivity() }.let { if(it.lastOrNull()==route.name)it else it+route.name }
    else available + route.name
}

internal fun StayRoute.isActivity()=this in setOf(StayRoute.REST,StayRoute.STUDY,StayRoute.WORK)

fun popRoute(stack: List<String>) =
    if (stack.size > 1) stack.dropLast(1) else listOf(StayRoute.HOME.name)
