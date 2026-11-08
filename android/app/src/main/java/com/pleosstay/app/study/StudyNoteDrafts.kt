package com.pleosstay.app.study

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

data class StudyNoteDraft(val id: String, val body: String)

/** Route/configuration retention, encrypted even while the document surface is masked.
 * The key lives only in the retained owner; the repository separately persists encrypted drafts.
 * A retained draft is never an authorization grant. */
class StudyNoteDrafts {
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val entries = mutableMapOf<StudyMaterialKey, ByteArray>()
    fun put(material: StudyMaterialKey, draft: StudyNoteDraft) {
        val plain = ByteArrayOutputStream().also { stream -> DataOutputStream(stream).use {
            val body = draft.body.toByteArray(Charsets.UTF_8)
            require(body.size <= EncryptedStudyRepository.MAX_TEXT_BYTES)
            it.writeUTF(draft.id); it.writeInt(body.size); it.write(body)
        } }.toByteArray()
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            cipher.updateAAD(material.stableId().toByteArray())
            entries.put(material, cipher.iv + cipher.doFinal(plain))?.fill(0)
        } finally { plain.fill(0) }
    }
    fun get(material: StudyMaterialKey): StudyNoteDraft? {
        val sealed = entries[material] ?: return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,key,GCMParameterSpec(128,sealed,0,12))
        cipher.updateAAD(material.stableId().toByteArray())
        val plain = cipher.doFinal(sealed,12,sealed.size-12)
        return try { DataInputStream(ByteArrayInputStream(plain)).use {
            val id=it.readUTF();val length=it.readInt();require(length in 0..EncryptedStudyRepository.MAX_TEXT_BYTES && length<=it.available())
            val body=ByteArray(length);it.readFully(body)
            try { StudyNoteDraft(id,body.toString(Charsets.UTF_8)) } finally { body.fill(0) }
        } } finally { plain.fill(0) }
    }
    fun remove(material: StudyMaterialKey) { entries.remove(material)?.fill(0) }
    fun clear() { entries.values.forEach { it.fill(0) }; entries.clear() }
}
