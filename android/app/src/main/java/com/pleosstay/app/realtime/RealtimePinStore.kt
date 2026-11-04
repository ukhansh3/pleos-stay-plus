package com.pleosstay.app.realtime

import android.content.Context
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import org.json.JSONObject

/** Local profile lock; not a substitute for server authorization or OS user isolation. */
class RealtimePinStore(context: Context) {
 private val file=File(context.noBackupFilesDir,"realtime-pin.json")
 private fun hash(pin: String,salt: ByteArray)=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(pin.toCharArray(),salt,210000,256)).encoded
 private fun encode(bytes: ByteArray)=android.util.Base64.encodeToString(bytes,android.util.Base64.NO_WRAP)
 private fun decode(value: String)=android.util.Base64.decode(value,android.util.Base64.NO_WRAP)
 @Synchronized fun clearAfterUserVerification(){check(!file.exists() || file.delete())}
 fun isConfigured()=file.exists()
 @Synchronized fun configure(pin: String){check(!file.exists());require(pin.matches(Regex("[0-9]{6,12}")));val salt=ByteArray(16).also { SecureRandom().nextBytes(it) };save(JSONObject().put("salt",encode(salt)).put("hash",encode(hash(pin,salt))).put("failures",0).put("blockedUntil",0L))}
 private fun save(json: JSONObject){val temp=File(file.parentFile,"realtime-pin.tmp");temp.writeText(json.toString());check(temp.renameTo(file))}
 @Synchronized fun verify(pin: String): Boolean {
  val json=JSONObject(file.readText());check(System.currentTimeMillis()>=json.getLong("blockedUntil")){"PIN_RATE_LIMITED"}
  val success=MessageDigest.isEqual(decode(json.getString("hash")),hash(pin,decode(json.getString("salt"))))
  val failures=if(success)0 else json.getInt("failures")+1
  json.put("failures",failures).put("blockedUntil",if(failures>=5)System.currentTimeMillis()+300000 else 0L);save(json);return success
 }
}
