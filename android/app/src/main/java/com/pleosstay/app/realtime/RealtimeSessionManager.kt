package com.pleosstay.app.realtime

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class RealtimeSession(val uid:String,val accessToken:String,val refreshToken:String,val expiresAt:Long)
/** Auth state machine, independent of Android storage and HTTP; never replaces a lost identity implicitly. */
class RealtimeSessionManager(
 private val read:suspend ()->RealtimeSession?,
 private val write:suspend (RealtimeSession)->Unit,
 private val exchange:suspend (String?)->RealtimeSession,
 private val clock:()->Long={System.currentTimeMillis()/1000},
) {
 private val mutex=Mutex()
 suspend fun token():String=mutex.withLock {
  val saved=read()
  if(saved!=null && saved.expiresAt>clock()+60) return@withLock saved.accessToken
  val next=exchange(saved?.refreshToken)
  check(next.uid.isNotBlank() && next.accessToken.isNotBlank() && next.refreshToken.isNotBlank()) { "INVALID_AUTH_RESPONSE" }
  check(saved==null || saved.uid==next.uid) { "IDENTITY_CHANGED" }
  write(next);next.accessToken
 }
 suspend fun reset(clear:suspend ()->Unit)=mutex.withLock { clear() }
}
