package com.pleosstay.app.study

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidStudyCipher : StudyCipher {
    private fun key():SecretKey = synchronized(KEY_LOCK) {
        val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS,null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    override fun encrypt(plain:ByteArray,context:ByteArray):ByteArray {
        val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());c.updateAAD(context)
        check(c.iv.size==12)
        return c.iv+c.doFinal(plain)
    }
    override fun decrypt(sealed:ByteArray,context:ByteArray):ByteArray {
        require(sealed.size>=28) { "STUDY_RECORD_CORRUPT" }
        val c=Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,sealed,0,12));c.updateAAD(context)
        return c.doFinal(sealed,12,sealed.size-12)
    }
    companion object { private const val ALIAS="stay_study_records_v1";private val KEY_LOCK=Any() }
}
fun createStudyRepository(context:Context):StudyRepository =
    EncryptedStudyRepository(File(context.applicationContext.noBackupFilesDir,"study/records"),AndroidStudyCipher())
