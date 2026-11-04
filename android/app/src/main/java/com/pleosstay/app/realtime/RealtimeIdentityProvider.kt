package com.pleosstay.app.realtime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
/** Production anonymous Auth adapter. Only public keys enter the APK. */
class RealtimeIdentityProvider(private val url:String,private val publishableKey:String,private val store:RealtimeTokenStore) {
 init { require(URI(url).scheme=="https") }
 private val sessions=RealtimeSessionManager(
  read={withContext(Dispatchers.IO){store.read()?.let{val j=JSONObject(it);RealtimeSession(j.getJSONObject("user").getString("id"),j.getString("access_token"),j.getString("refresh_token"),j.getLong("expires_at"))}}},
  write={s->withContext(Dispatchers.IO){store.write(JSONObject().put("user",JSONObject().put("id",s.uid)).put("access_token",s.accessToken).put("refresh_token",s.refreshToken).put("expires_at",s.expiresAt).toString())}},
  exchange={refresh->exchange(refresh)},
 )
 suspend fun validToken():String=sessions.token()
 suspend fun resetAfterRevocation()=sessions.reset { withContext(Dispatchers.IO){store.clear()} }
 private suspend fun exchange(refresh:String?):RealtimeSession=withContext(Dispatchers.IO){
  val path=if(refresh==null)"signup" else "token?grant_type=refresh_token"
  val body=if(refresh==null)JSONObject() else JSONObject().put("refresh_token",refresh)
  val c=URI(url.trimEnd('/')+"/auth/v1/"+path).toURL().openConnection() as HttpURLConnection
  try {
   c.requestMethod="POST";c.connectTimeout=10000;c.readTimeout=15000;c.doOutput=true;c.instanceFollowRedirects=false
   c.setRequestProperty("apikey",publishableKey);c.setRequestProperty("Content-Type","application/json")
   c.outputStream.use{it.write(body.toString().toByteArray(Charsets.UTF_8))}
   val status=c.responseCode
   check(status in 200..299){if(status>=500||status==429)"AUTH_UNAVAILABLE" else if(refresh!=null)"REPAIR_REQUIRED" else "AUTH_BLOCKED"}
   val j=JSONObject(c.inputStream.bufferedReader().use{it.readText()})
   RealtimeSession(j.getJSONObject("user").getString("id"),j.getString("access_token"),j.getString("refresh_token"),System.currentTimeMillis()/1000+j.getLong("expires_in"))
  }catch(e:java.io.IOException){throw IllegalStateException("AUTH_UNAVAILABLE",e)}finally{c.disconnect()}
 }
}
