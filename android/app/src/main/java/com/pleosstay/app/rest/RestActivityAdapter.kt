package com.pleosstay.app.rest

import com.pleosstay.app.modes.*

internal class RestActivityAdapter(private val controller:RestController,private val preset:()->RestPreset,private val choice:()->WaitingChoice?={null},private val acceptForeground:()->Boolean={false},private val withoutWaiting:()->Boolean={false}):ActivityAdapter {
    override val mode=ActivityMode.REST
    override fun restored()=controller.state.value.ready
    override fun sessionId()=controller.transitionSessionId()
    override suspend fun preflight()=controller.transitionPreflight(preset())
    override suspend fun start()=controller.startForTransition(preset(),choice(),acceptForeground(),withoutWaiting())
    override suspend fun resume()=ActivityOperation.Done
    override suspend fun stop()=controller.stopForTransition()
}
