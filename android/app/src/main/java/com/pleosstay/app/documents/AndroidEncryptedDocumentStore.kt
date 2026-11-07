package com.pleosstay.app.documents

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Opaque retained records; no credentials, auto-purge, or offline authorization grant. */
class AndroidEncryptedDocumentStore(
    context: Context, namespace: String, private val maxRecordBytes: Int = 48 * 1024 * 1024,
) : DocumentRecordStore {
    private val namespaceHash = hash(namespace.toByteArray(Charsets.UTF_8))
    private val directory = File(context.noBackupFilesDir, "documents/records/$namespaceHash")
    private val alias = "stay_documents_v1_$namespaceHash"
    private val mutex = namespaceLocks.computeIfAbsent(namespaceHash) { Mutex() }
    init { require(namespace.isNotBlank() && namespace.length <= 1024); require(maxRecordBytes in 1..64 * 1024 * 1024) }
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey(alias, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
            generateKey()
        }
    }
    private fun filename(key: String): String {
        require(key.isNotBlank() && key.toByteArray(Charsets.UTF_8).size <= 4096)
        return hash(key.toByteArray(Charsets.UTF_8)) + ".bin"
    }
    private fun aad(name: String) = "stay-documents-v1:$namespaceHash:$name".toByteArray(Charsets.UTF_8)
    private fun decode(name: String): Pair<String, ByteArray>? {
        val atomic = AtomicFile(File(directory, name))
        if (!atomic.baseFile.exists() && !File(directory, "$name.bak").exists()) return null
        val encoded = atomic.openRead().use { input ->
            require(input.channel.size() <= maxRecordBytes.toLong() + 8192) { "DOCUMENT_RECORD_TOO_LARGE" }
            input.readBytes()
        }
        try {
            val stream = DataInputStream(ByteArrayInputStream(encoded))
            require(stream.readInt() == 1) { "DOCUMENT_RECORD_VERSION" }
            val ivSize = stream.readInt(); require(ivSize == 12)
            val iv = ByteArray(ivSize).also { stream.readFully(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)); cipher.updateAAD(aad(name))
            val encrypted = stream.readBytes()
            val plain = cipher.doFinal(encrypted)
            try {
                DataInputStream(ByteArrayInputStream(plain)).use { data ->
                    val keySize = data.readInt(); require(keySize in 1..4096)
                    val keyBytes = ByteArray(keySize).also { data.readFully(it) }
                    val recordKey = keyBytes.toString(Charsets.UTF_8)
                    require(filename(recordKey) == name)
                    val size = data.readInt(); require(size in 0..maxRecordBytes && data.available() == size)
                    return recordKey to ByteArray(size).also { data.readFully(it) }
                }
            } finally { plain.fill(0) }
        } catch (_: Exception) { throw IllegalStateException("DOCUMENT_RECORD_UNREADABLE") }
        finally { encoded.fill(0) }
    }
    override suspend fun read(key: String): ByteArray? = withContext(Dispatchers.IO) {
        mutex.withLock { decode(filename(key))?.also { check(it.first == key) }?.second }
    }
    override suspend fun write(key: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        mutex.withLock {
            require(bytes.size <= maxRecordBytes)
            val name = filename(key); check(directory.isDirectory || directory.mkdirs())
            val keyBytes = key.toByteArray(Charsets.UTF_8)
            val plain = ByteArrayOutputStream(bytes.size + keyBytes.size + 8).apply {
                DataOutputStream(this).use { it.writeInt(keyBytes.size); it.write(keyBytes); it.writeInt(bytes.size); it.write(bytes) }
            }.toByteArray()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key()); cipher.updateAAD(aad(name))
            val encrypted = try { cipher.doFinal(plain) } finally { plain.fill(0) }
            val atomic = AtomicFile(File(directory, name))
            val output = atomic.startWrite()
            try {
                val data = DataOutputStream(output)
                data.writeInt(1); data.writeInt(cipher.iv.size); data.write(cipher.iv); data.write(encrypted); data.flush()
                atomic.finishWrite(output)
            } catch (failure: Throwable) { atomic.failWrite(output); throw failure }
            finally { encrypted.fill(0) }
        }
    }
    override suspend fun delete(key: String) = withContext(Dispatchers.IO) { mutex.withLock { AtomicFile(File(directory, filename(key))).delete() } }
    override suspend fun keys(): List<String> = withContext(Dispatchers.IO) {
        mutex.withLock {
            directory.listFiles().orEmpty().map { it.name.removeSuffix(".bak") }.distinct()
                .filter { Regex("[a-f0-9]{64}\\.bin").matches(it) }.mapNotNull { name ->
                    decode(name)?.let { record -> record.second.fill(0); record.first }
                }
        }
    }
    companion object {
        private val namespaceLocks = ConcurrentHashMap<String, Mutex>()
        private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
