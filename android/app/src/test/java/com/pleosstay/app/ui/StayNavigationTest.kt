package com.pleosstay.app.ui

import com.pleosstay.app.ui.premium.*
import org.junit.Assert.*
import org.junit.Test

class StayNavigationTest {
    @Test fun restUsesModesGroupAndReturnsToItsEntryRoute() {
        val entry = listOf(StayRoute.HOME.name, StayRoute.MODES.name)
        val rest = pushRoute(entry, StayRoute.REST)
        assertEquals(NavigationGroup.MODES, StayRoute.valueOf(rest.last()).group)
        assertEquals(entry, popRoute(rest))
        assertEquals(rest, pushRoute(rest, StayRoute.REST))
    }
    @Test fun waitingKeepsParkingGroupAndBackRestoresActualRoute() {
        var stack = listOf(StayRoute.HOME.name)
        stack = pushRoute(stack, StayRoute.PARKING)
        stack = pushRoute(stack, StayRoute.WAITING)
        assertEquals(NavigationGroup.PARKING, StayRoute.valueOf(stack.last()).group)
        stack = pushRoute(stack, StayRoute.PICKUP)
        stack = popRoute(stack)
        assertEquals(StayRoute.WAITING, StayRoute.valueOf(stack.last()))
        assertEquals(NavigationGroup.PARKING, StayRoute.valueOf(stack.last()).group)
        assertEquals(stack, pushRoute(stack, StayRoute.WAITING))
        assertEquals(listOf("HOME"), popRoute(listOf("HOME")))
    }
}
