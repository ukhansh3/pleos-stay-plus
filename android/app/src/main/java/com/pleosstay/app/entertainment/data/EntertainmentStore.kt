package com.pleosstay.app.entertainment.data

import java.io.*
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.AtomicMoveNotSupportedException
import java.util.zip.CRC32

/** Dedicated app-private Entertainment records. Never stores PCM, tokens or Waiting data. */
interface EntertainmentStore {
    fun read(key: String): ByteArray?
    fun write(key: String, bytes: ByteArray)
}
class CorruptEntertainmentData : IOException("Entertainment 저장 데이터를 읽을 수 없습니다.")
class FileEntertainmentStore(private val directory: File) : EntertainmentStore {
    private fun file(key: String): File {
        require(key.matches(Regex("[a-z0-9-]{1,60}")))
        return File(directory, "$key.ent")
    }
    @Synchronized override fun read(key: String): ByteArray? {
        val target = file(key)
        if (!target.exists()) return null
        if (target.length() !in 16..1_048_592) throw CorruptEntertainmentData()
        return decodeRecord(target.readBytes()) {
            require(readInt() == 0x454E5431)
            val size = readInt(); require(size in 0..1_048_576)
            val crc = readLong()
            val payload = ByteArray(size); readFully(payload)
            require(CRC32().apply { update(payload) }.value == crc)
            payload
        }
    }
    @Synchronized override fun write(key: String, bytes: ByteArray) {
        require(bytes.size <= 1_048_576)
        check(directory.isDirectory || directory.mkdirs())
        val target = file(key)
        val temp = File.createTempFile("ent-", ".pending", directory)
        try {
            val encoded = encodeRecord {
                writeInt(0x454E5431); writeInt(bytes.size)
                writeLong(CRC32().apply { update(bytes) }.value); write(bytes)
            }
            FileOutputStream(temp).use { it.write(encoded); it.fd.sync() }
            try { Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
            catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            check(read(key)?.contentEquals(bytes) == true)
        } finally { temp.delete() }
    }
}
fun encodeRecord(block: DataOutputStream.() -> Unit): ByteArray = ByteArrayOutputStream().use { out ->
    DataOutputStream(out).use(block); out.toByteArray()
}
fun <T> decodeRecord(bytes: ByteArray, block: DataInputStream.() -> T): T = try {
    require(bytes.size <= 1_048_576 + 16)
    DataInputStream(ByteArrayInputStream(bytes)).use { input ->
        val value = input.block(); require(input.available() == 0); value
    }
} catch (_: Exception) { throw CorruptEntertainmentData() }
