package com.pleosstay.app.realtime
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
/** Independent Realtime token storage, excluded from backup by its noBackupFilesDir location. */
class RealtimeTokenStore(context: Context, fileName: String = "realtime-auth.bin") {
 private val file=File(context.noBackupFilesDir,fileName)
 private fun key(): SecretKey {
  val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
  (store.getKey("stay_realtime_auth_v1",null) as? SecretKey)?.let { return it }
  return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply { init(KeyGenParameterSpec.Builder("stay_realtime_auth_v1",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build()) }.generateKey()
 }
 @Synchronized fun write(value: String) { val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());val temp=File(file.parentFile,file.name+".tmp");temp.writeBytes(cipher.iv+cipher.doFinal(value.toByteArray(Charsets.UTF_8)));check(temp.renameTo(file)){"TOKEN_SAVE_FAILED"} }
 @Synchronized fun read(): String? { if(!file.exists())return null;val bytes=file.readBytes();require(bytes.size>12);val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes,0,12));return String(cipher.doFinal(bytes,12,bytes.size-12),Charsets.UTF_8) }
 @Synchronized fun clear(){file.delete()}
}

