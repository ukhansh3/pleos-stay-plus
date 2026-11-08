package com.pleosstay.app.work.docx

import com.pleosstay.app.work.DocxDraft
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

internal class EncryptedEditRecovery(draft:DocxDraft) {
    val info=draft.info
    private val key=KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val sealed:ByteArray
    init {
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key)
        sealed=cipher.iv+cipher.doFinal(draft.bytes)
    }
    fun open():DocxDraft {
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,key,GCMParameterSpec(128,sealed,0,12))
        return DocxDraft(info,cipher.doFinal(sealed,12,sealed.size-12))
    }
    fun clear() { sealed.fill(0) }
}
