package com.pleosstay.app.study

import java.io.*
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.AtomicMoveNotSupportedException
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class StudyMaterialKey(val userScope:String,val connectionId:String,val fileId:String,val versionId:String) {
    init { require(listOf(userScope,connectionId,fileId,versionId).all { it.isNotBlank() }) }
    fun stableId():String {
        val data=ByteArrayOutputStream().also { out -> DataOutputStream(out).use { d -> listOf(userScope,connectionId,fileId,versionId).forEach { d.writeValue(it) } } }.toByteArray()
        return MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }
    }
}
data class StudyNote(val id:String,val anchor:String?,val body:String,val updatedAt:Long)
data class StudyProgress(val anchorKind:String,val anchor:String,val fraction:Float?,val completed:Boolean,val updatedAt:Long) {
    init { require(fraction==null || fraction.isFinite() && fraction in 0f..1f) }
}
data class StudyRecord(val key:StudyMaterialKey,val notes:List<StudyNote> = emptyList(),val progress:StudyProgress?=null)

interface StudyRepository {
    suspend fun load(key:StudyMaterialKey):StudyRecord
    suspend fun saveNote(key:StudyMaterialKey,id:String?,anchor:String?,body:String):StudyNote
    suspend fun deleteNote(key:StudyMaterialKey,id:String)
    suspend fun saveProgress(key:StudyMaterialKey,progress:StudyProgress)
    suspend fun deleteMaterialData(key:StudyMaterialKey)
    suspend fun lastMaterial(userScope:String,connectionId:String):StudyMaterialKey?
}
interface StudyCipher {
    fun encrypt(plain:ByteArray,context:ByteArray):ByteArray
    fun decrypt(sealed:ByteArray,context:ByteArray):ByteArray
}

/** No-backup directory is provided by the Android factory. Never treats a cache as authorization. */
class EncryptedStudyRepository(
    private val directory:File,
    private val cipher:StudyCipher,
    private val io:CoroutineDispatcher=Dispatchers.IO,
    private val now:()->Long=System::currentTimeMillis,
) : StudyRepository {
    private val mutex=Mutex()
    private fun file(key:StudyMaterialKey)=File(directory,key.stableId()+".study")
    private fun scopeId(owner:String,connection:String)=StudyMaterialKey(owner,connection,"last","last").stableId()
    private fun read(key:StudyMaterialKey):StudyRecord {
        val file=file(key)
        if(!file.exists())return StudyRecord(key)
        require(file.length()<=MAX_RECORD_BYTES) { "STUDY_RECORD_TOO_LARGE" }
        return StudyRecordCodec.decode(cipher.decrypt(file.readBytes(),key.stableId().toByteArray()),key)
    }
    private fun write(record:StudyRecord) {
        val sealed = cipher.encrypt(StudyRecordCodec.encode(record), record.key.stableId().toByteArray())
        require(sealed.size <= MAX_RECORD_BYTES) { "STUDY_RECORD_TOO_LARGE" }
        atomic(file(record.key), sealed)
        val scope=scopeId(record.key.userScope,record.key.connectionId)
        atomic(File(directory,scope+".last"),cipher.encrypt(StudyRecordCodec.encode(StudyRecord(record.key)),scope.toByteArray()))
    }
    private fun atomic(target:File,bytes:ByteArray) {
        check(directory.isDirectory || directory.mkdirs()) { "STUDY_DIRECTORY_UNAVAILABLE" }
        val temp=File.createTempFile("study-",".pending",directory)
        try {
            FileOutputStream(temp).use { it.write(bytes);it.fd.sync() }
            try { Files.move(temp.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING) }
            catch(_:AtomicMoveNotSupportedException) { Files.move(temp.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING) }
        } finally { temp.delete() }
    }
    override suspend fun load(key:StudyMaterialKey)=withContext(io) { mutex.withLock { read(key) } }
    override suspend fun saveNote(key:StudyMaterialKey,id:String?,anchor:String?,body:String):StudyNote=withContext(io) {
        mutex.withLock {
            require(body.isNotBlank()) { "STUDY_NOTE_EMPTY" }
            require(body.toByteArray().size<=MAX_TEXT_BYTES) { "STUDY_NOTE_TOO_LARGE" }
            val record=read(key)
            val note=StudyNote(id ?: UUID.randomUUID().toString(),anchor,body,now())
            val notes=record.notes.filterNot { it.id==note.id }+note
            require(notes.size<=MAX_NOTES) { "STUDY_NOTE_LIMIT" }
            write(record.copy(notes=notes));note
        }
    }
    override suspend fun deleteNote(key:StudyMaterialKey,id:String)=withContext(io) { mutex.withLock {
        val record=read(key);write(record.copy(notes=record.notes.filterNot { it.id==id }))
    } }
    override suspend fun saveProgress(key:StudyMaterialKey,progress:StudyProgress)=withContext(io) { mutex.withLock {
        require(progress.anchorKind in setOf("PDF_PAGE","DOCX_BLOCK","TXT_OFFSET")) { "STUDY_ANCHOR_UNSUPPORTED" }
        write(read(key).copy(progress=progress))
    } }
    override suspend fun deleteMaterialData(key:StudyMaterialKey)=withContext(io) { mutex.withLock {
        val target=file(key)
        check(!target.exists() || target.delete()) { "STUDY_DELETE_FAILED" }
        val scope=scopeId(key.userScope,key.connectionId)
        val last=File(directory,scope+".last")
        if(last.exists()) {
            val saved=StudyRecordCodec.decode(cipher.decrypt(last.readBytes(),scope.toByteArray()),null).key
            if(saved==key)check(last.delete()) { "STUDY_DELETE_FAILED" }
        }
    } }
    override suspend fun lastMaterial(userScope:String,connectionId:String):StudyMaterialKey?=withContext(io) { mutex.withLock {
        val scope=scopeId(userScope,connectionId)
        val last=File(directory,scope+".last")
        if(!last.exists())null else {
            require(last.length()<=MAX_RECORD_BYTES)
            StudyRecordCodec.decode(cipher.decrypt(last.readBytes(),scope.toByteArray()),null).key.also {
                require(it.userScope==userScope && it.connectionId==connectionId) { "STUDY_SCOPE_MISMATCH" }
            }
        }
    } }
    companion object {
        // Defensive decoding/memory bounds, not server file-size limits. Rejects rather than truncates.
        const val MAX_RECORD_BYTES=16L*1024*1024
        const val MAX_TEXT_BYTES=1024*1024
        const val MAX_NOTES=1000
    }
}

internal object StudyRecordCodec {
    fun encode(value:StudyRecord):ByteArray = ByteArrayOutputStream().also { bytes ->
        DataOutputStream(bytes).use { out ->
            out.writeInt(1);with(value.key) { listOf(userScope,connectionId,fileId,versionId).forEach(out::writeValue) }
            out.writeInt(value.notes.size)
            value.notes.forEach { n -> out.writeValue(n.id);out.writeOptional(n.anchor);out.writeValue(n.body);out.writeLong(n.updatedAt) }
            out.writeBoolean(value.progress!=null)
            value.progress?.let { p -> out.writeValue(p.anchorKind);out.writeValue(p.anchor);out.writeBoolean(p.fraction!=null);p.fraction?.let(out::writeFloat);out.writeBoolean(p.completed);out.writeLong(p.updatedAt) }
        }
    }.toByteArray().also { require(it.size<=EncryptedStudyRepository.MAX_RECORD_BYTES) { "STUDY_RECORD_TOO_LARGE" } }
    fun decode(bytes:ByteArray,expected:StudyMaterialKey?):StudyRecord = DataInputStream(ByteArrayInputStream(bytes)).use { input ->
        require(input.readInt()==1) { "STUDY_RECORD_VERSION" }
        val key=StudyMaterialKey(input.readValue(),input.readValue(),input.readValue(),input.readValue())
        require(expected==null || key==expected) { "STUDY_SCOPE_MISMATCH" }
        val count=input.readInt();require(count in 0..EncryptedStudyRepository.MAX_NOTES)
        val notes=(0 until count).map { StudyNote(input.readValue(),input.readOptional(),input.readValue(),input.readLong()) }
        require(notes.map { it.id }.distinct().size==notes.size)
        val progress=if(input.readBoolean()) StudyProgress(input.readValue(),input.readValue(),if(input.readBoolean())input.readFloat() else null,input.readBoolean(),input.readLong()) else null
        require(input.available()==0) { "STUDY_RECORD_TRAILING_DATA" }
        StudyRecord(key,notes,progress)
    }
}
private fun DataOutputStream.writeValue(value:String) { val bytes=value.toByteArray(StandardCharsets.UTF_8);require(bytes.size<=EncryptedStudyRepository.MAX_TEXT_BYTES);writeInt(bytes.size);write(bytes) }
private fun DataOutputStream.writeOptional(value:String?) { writeBoolean(value!=null);value?.let(::writeValue) }
private fun DataInputStream.readValue():String { val length=readInt();require(length in 0..EncryptedStudyRepository.MAX_TEXT_BYTES && length<=available());val b=ByteArray(length);readFully(b);return StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(b)).toString() }
private fun DataInputStream.readOptional():String?=if(readBoolean())readValue() else null
