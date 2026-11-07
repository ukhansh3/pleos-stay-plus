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

fun pushRoute(stack: List<String>, route: StayRoute) =
    if (stack.lastOrNull() == route.name) stack else stack + route.name

fun popRoute(stack: List<String>) =
    if (stack.size > 1) stack.dropLast(1) else listOf(StayRoute.HOME.name)
