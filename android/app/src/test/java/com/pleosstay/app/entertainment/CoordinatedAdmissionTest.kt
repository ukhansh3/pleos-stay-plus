package com.pleosstay.app.entertainment

import com.pleosstay.app.entertainment.data.*
import com.pleosstay.app.entertainment.recommendation.EntertainmentContent
import com.pleosstay.app.modes.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

class CoordinatedAdmissionTest {
    @Test fun realAdmissionRequiresCoordinatorAndHomeNeverAutoRestartsContent()=runTest {
        val admission=CoordinatedEntertainmentAdmission();val content=Content()
        val controller=EntertainmentController(admission,EntertainmentSafety(),mapOf(EntertainmentContent.KARAOKE to content),MemoryEntertainmentStore(),backgroundScope)
        controller.onHostForeground(true)
        assertFalse(controller.start().success)
        val adapter=object:ActivityAdapter {
            override val mode=ActivityMode.ENTERTAINMENT
            override fun sessionId()=admission.sessionId()
            override suspend fun preflight()=ActivityOperation(controller.canStart())
            override suspend fun start()=admission.start(controller).let { ActivityOperation(it.success) }
            override suspend fun resume()=controller.onHostForeground(true).let { ActivityOperation(it.success) }
            override suspend fun stop()=controller.end().let { ActivityOperation(it.success) }
        }
        assertFalse(adapter.start().success)
        val coordinator=ActivityTransitionCoordinator(listOf(adapter))
        assertEquals(TransitionStatus.STARTED,coordinator.request(ActivityMode.ENTERTAINMENT).status)
        assertTrue(controller.select(EntertainmentContent.KARAOKE).success)
        val id=adapter.sessionId();controller.onInternalHome()
        assertEquals(id,adapter.sessionId());assertEquals(EntertainmentPhase.PAUSED,controller.state.value.phase)
        assertEquals(TransitionStatus.RESUMED,coordinator.request(ActivityMode.ENTERTAINMENT).status)
        assertEquals(1,content.starts)
        controller.onHostForeground(false);controller.onHostForeground(true)
        assertEquals(1,content.starts);assertEquals(EntertainmentPhase.PAUSED,controller.state.value.phase)
        assertEquals(TransitionStatus.STOPPED,coordinator.end().status)
        assertNull(adapter.sessionId());assertEquals(1,content.stops)
    }
}
