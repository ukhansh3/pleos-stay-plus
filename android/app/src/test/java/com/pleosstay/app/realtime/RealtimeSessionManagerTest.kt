package com.pleosstay.app.realtime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
class RealtimeSessionManagerTest {
 @Test fun signupRestoreAndRefreshKeepOneIdentity()=runTest {
  var saved:RealtimeSession?=null;val refreshes=mutableListOf<String?>();var now=100L
  fun manager()=RealtimeSessionManager({saved},{saved=it},{r->refreshes.add(r);RealtimeSession("uid","access-${refreshes.size}","refresh",now+3600)},{now})
  assertEquals("access-1",manager().token());assertEquals("access-1",manager().token());assertEquals(1,refreshes.size)
  now+=3600;assertEquals("access-2",manager().token());assertEquals(listOf(null,"refresh"),refreshes)
 }
 @Test fun refreshFailureNeverSignsUpOrClearsIdentity()=runTest {
  val saved=RealtimeSession("uid","old","refresh",0);var writes=0
  for(code in listOf("AUTH_UNAVAILABLE","REPAIR_REQUIRED")){
   val manager=RealtimeSessionManager({saved},{writes++},{r->assertEquals("refresh",r);error(code)},{100})
   try{manager.token();fail("accepted failed refresh")}catch(e:IllegalStateException){assertEquals(code,e.message)}
  };assertEquals(0,writes)
 }
 @Test fun mismatchedUidIsRejectedWithoutOverwritingSession()=runTest {
  var saved=RealtimeSession("uid","old","refresh",0)
  val manager=RealtimeSessionManager({saved},{saved=it},{RealtimeSession("other","new","r",9999)},{100})
  try{manager.token();fail("identity changed")}catch(e:IllegalStateException){assertEquals("IDENTITY_CHANGED",e.message)}
  assertEquals("uid",saved.uid)
 }
}
