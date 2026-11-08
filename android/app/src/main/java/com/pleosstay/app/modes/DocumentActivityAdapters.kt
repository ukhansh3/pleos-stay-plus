package com.pleosstay.app.modes

import com.pleosstay.app.study.StudyViewModel
import com.pleosstay.app.work.WorkViewModel

class StudyActivityAdapter(private val model:StudyViewModel,private val minutes:()->Int,private val choice:()->WaitingChoice?={null}):ActivityAdapter {
    override val mode=ActivityMode.STUDY
    override fun sessionId()=model.sessionId
    override suspend fun preflight()=model.transitionPreflight()
    override suspend fun start()=model.startForTransition(minutes(),choice())
    override suspend fun resume()=model.transitionPreflight()
    override suspend fun stop()=model.stopForTransition()
}
class WorkActivityAdapter(private val model:WorkViewModel,private val choice:()->WaitingChoice?={null}):ActivityAdapter {
    override val mode=ActivityMode.WORK
    override fun sessionId()=model.transitionSessionId
    override suspend fun preflight()=model.transitionPreflight()
    override suspend fun start()=model.startForTransition(choice())
    override suspend fun resume()=model.transitionPreflight()
    override suspend fun stop()=model.stopForTransition()
}
