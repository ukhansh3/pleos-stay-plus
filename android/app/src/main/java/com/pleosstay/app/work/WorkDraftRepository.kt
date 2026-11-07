package com.pleosstay.app.work

import com.pleosstay.app.documents.DocumentRecordStore
import com.pleosstay.app.documents.DocumentRef
import com.pleosstay.app.documents.SharedDocumentGateway
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Immutable source and opaque engine snapshot share one atomic encrypted record. */
class WorkDraftRepository(
    private val records: DocumentRecordStore,
    private val documents: SharedDocumentGateway,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()

    private fun docxPrefix(owner:String,connection:String) = "docx:" + digest((owner.length.toString()+":"+owner+connection).toByteArray()) + ":"
    private fun docxKey(info:DocxDraftInfo) = docxPrefix(info.owner,info.connection)+info.id
    private suspend fun checkDocxProfile(info:DocxDraftInfo?=null) {
        val a=documents.refreshAccess();check(a.allowed&&a.userScope!=null&&a.connectionId!=null){"WORK_ACCESS_BLOCKED"}
        if(info!=null){check(info.owner==a.userScope&&info.connection==a.connectionId);info.source?.let{authorize(it)}}
    }
    suspend fun createDocx(name:String,bytes:ByteArray,source:DocumentRef?=null):DocxDraft = mutex.withLock {
        checkDocxProfile();source?.let{authorize(it)}
        val a=documents.access.value
        val id=source?.let{"remote-"+id(it)} ?: java.util.UUID.randomUUID().toString()
        val info=DocxDraftInfo(id,checkNotNull(a.userScope),checkNotNull(a.connectionId),safeDocxName(name),now(),source)
        val old=records.read(docxKey(info))
        if(old!=null){try{return@withLock DocxDraftCodec.decode(old).also{check(it.info.id==info.id&&it.info.owner==info.owner&&it.info.connection==info.connection&&it.info.source==source);checkDocxProfile(it.info)}}finally{old.fill(0)}}
        val draft=DocxDraft(info,bytes.copyOf());writeDocx(draft);draft
    }
    suspend fun listDocx():List<DocxDraftInfo> = mutex.withLock {
        checkDocxProfile();val a=documents.access.value;val prefix=docxPrefix(a.userScope!!,a.connectionId!!)
        val result=mutableListOf<DocxDraftInfo>()
        for(key in records.keys().filter{it.startsWith(prefix)}){val raw=records.read(key)?:continue;try{val d=DocxDraftCodec.decode(raw);try{checkDocxProfile(d.info);result+=d.info}finally{d.bytes.fill(0)}}finally{raw.fill(0)}}
        result.sortedByDescending{it.updatedAt}
    }
    suspend fun loadDocx(info:DocxDraftInfo):DocxDraft = mutex.withLock {
        checkDocxProfile(info);val bytes=records.read(docxKey(info))?:error("DOCX_DRAFT_MISSING")
        try{DocxDraftCodec.decode(bytes).also{check(it.info.id==info.id&&it.info.owner==info.owner&&it.info.connection==info.connection&&it.info.source==info.source);checkDocxProfile(it.info)}}finally{bytes.fill(0)}
    }
    /** Retains an already-authorized edit after a local lock; never grants read access or creates a new identity. */
    internal suspend fun retainDocx(draft:DocxDraft) = mutex.withLock {
        val old=records.read(docxKey(draft.info))?:error("DOCX_DRAFT_MISSING")
        try{val existing=DocxDraftCodec.decode(old);try{check(existing.info.id==draft.info.id&&existing.info.owner==draft.info.owner&&existing.info.connection==draft.info.connection&&existing.info.source==draft.info.source)}finally{existing.bytes.fill(0)}}finally{old.fill(0)}
        writeDocx(draft)
    }
    suspend fun deleteDocx(info:DocxDraftInfo) = mutex.withLock {checkDocxProfile(info);records.delete(docxKey(info))}
    suspend fun sourceForDocx(ref:DocumentRef):ByteArray = mutex.withLock {
        authorize(ref);val r=readRecord(id(ref))?:error("WORK_SOURCE_MISSING")
        try{check(r.summary.source==ref&&r.original.size.toLong()==ref.sizeBytes&&digest(r.original)==ref.sha256);check(r.engineSnapshot==null){"이전 편집 엔진의 초안은 별도 호환 검토가 필요합니다."};r.original.copyOf()}finally{r.clearBuffers()}
    }
    private suspend fun writeDocx(d:DocxDraft){val encoded=DocxDraftCodec.encode(d);try{records.write(docxKey(d.info),encoded)}finally{encoded.fill(0)}}
    private fun safeDocxName(name:String):String {val safe=name.substringAfterLast('/').substringAfterLast('\\').filterNot{it.isISOControl()}.take(160).ifBlank{"문서.docx"};return if(safe.endsWith(".docx",true))safe else "$safe.docx"}

    suspend fun retainSource(ref: DocumentRef, file: File): WorkDraftSummary = mutex.withLock {
        authorize(ref)
        require(WorkFormat.of(ref) != null) { "WORK_FORMAT_UNSUPPORTED" }
        require(ref.sizeBytes in 1..MAX_SIZE) { "WORK_SIZE_INVALID" }
        val bytes = withContext(Dispatchers.IO) {
            file.inputStream().use { input -> ByteArrayOutputStream().use { output ->
                val buffer = ByteArray(65_536)
                try {
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        check(output.size().toLong() + count <= ref.sizeBytes) { "WORK_SIZE_MISMATCH" }
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                } finally { buffer.fill(0) }
            } }
        }
        try {
            check(bytes.size.toLong() == ref.sizeBytes && digest(bytes) == ref.sha256.lowercase()) { "WORK_INTEGRITY_MISMATCH" }
            authorize(ref)
            val id = id(ref)
            val existing = readRecord(id)
            if (existing != null) {
                try { check(existing.summary.source == ref) { "WORK_SOURCE_CHANGED" }; return@withLock existing.summary }
                finally { existing.clearBuffers() }
            }
            val summary = WorkDraftSummary(id, ref, now(), false, null)
            write(WorkDraftRecord(summary, bytes))
            summary
        } finally { bytes.fill(0) }
    }

    suspend fun summaries(): List<WorkDraftSummary> = mutex.withLock {
        val access = documents.refreshAccess()
        check(access.allowed) { "WORK_ACCESS_BLOCKED" }
        val result = mutableListOf<WorkDraftSummary>()
        for (id in records.keys().filter { it.matches(Regex("[a-f0-9]{64}")) }) {
            currentCoroutineContext().ensureActive()
            val record = readRecord(id) ?: continue
            try {
                val ref = record.summary.source
                // Other owners/connections are never returned or deleted.
                if (ref.userScope == access.userScope && ref.connectionId == access.connectionId && documents.validate(ref)) result += record.summary
            } finally { record.clearBuffers() }
        }
        check(documents.access.value.allowed && documents.access.value.userScope == access.userScope &&
            documents.access.value.connectionId == access.connectionId) { "WORK_ACCESS_CHANGED" }
        result.sortedByDescending { it.updatedAt }
    }

    suspend fun restore(ref: DocumentRef): WorkDraftSummary? = mutex.withLock {
        authorize(ref)
        val record = readRecord(id(ref)) ?: return@withLock null
        try {
            check(record.summary.source == ref) { "WORK_SOURCE_CHANGED" }
            check(record.original.size.toLong() == ref.sizeBytes && digest(record.original) == ref.sha256.lowercase()) { "WORK_INTEGRITY_MISMATCH" }
            authorize(ref)
            record.summary
        } finally { record.clearBuffers() }
    }

    /** Future approved engine seam, not exported PDF/DOCX or uploaded revision. */
    suspend fun saveEngineSnapshot(ref: DocumentRef, engineId: String, snapshot: ByteArray): WorkDraftSummary = mutex.withLock {
        require(engineId.isNotBlank() && engineId.length <= 200 && snapshot.size in 1..MAX_SIZE.toInt()) { "WORK_SNAPSHOT_INVALID" }
        authorize(ref)
        val previous = readRecord(id(ref)) ?: error("WORK_SOURCE_MISSING")
        try {
            check(previous.summary.source == ref) { "WORK_SOURCE_CHANGED" }
            val summary = previous.summary.copy(updatedAt = now(), dirty = true, engineId = engineId)
            authorize(ref)
            write(WorkDraftRecord(summary, previous.original, snapshot))
            summary
        } finally { previous.clearBuffers() }
    }

    /** Explicit same-owner delete only; revocation/user switch never calls this. */
    suspend fun delete(ref: DocumentRef) = mutex.withLock { authorize(ref); records.delete(id(ref)) }

    private suspend fun authorize(ref: DocumentRef) {
        val access = documents.access.value
        check(access.allowed && access.userScope == ref.userScope && access.connectionId == ref.connectionId) { "WORK_ACCESS_BLOCKED" }
        check(documents.validate(ref)) { "WORK_SOURCE_NOT_AUTHORIZED" }
        currentCoroutineContext().ensureActive()
        val latest = documents.access.value
        check(latest.allowed && latest.userScope == ref.userScope && latest.connectionId == ref.connectionId) { "WORK_ACCESS_CHANGED" }
    }

    private suspend fun readRecord(id: String): WorkDraftRecord? {
        val encoded = records.read(id) ?: return null
        return try {
            WorkDraftCodec.decode(encoded).also {
                if (it.summary.id != id) { it.clearBuffers(); error("WORK_DRAFT_KEY_MISMATCH") }
            }
        }
        finally { encoded.fill(0) }
    }
    private suspend fun write(record: WorkDraftRecord) {
        val encoded = WorkDraftCodec.encode(record)
        try { records.write(record.summary.id, encoded) } finally { encoded.fill(0) }
    }
    companion object {
        const val MAX_SIZE = 20L * 1024 * 1024
        internal fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        internal fun id(ref: DocumentRef): String = digest(ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { data -> listOf(ref.userScope, ref.connectionId, ref.fileId, ref.versionId).forEach(data::writeUTF) }; bytes.toByteArray()
        })
    }
}

internal object WorkDraftCodec {
    fun encode(record: WorkDraftRecord): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { out ->
            val s = record.summary; val r = s.source
            out.writeInt(1); out.writeUTF(s.id)
            listOf(r.userScope, r.connectionId, r.fileId, r.versionId, r.name, r.mimeType, r.sha256).forEach(out::writeUTF)
            out.writeLong(r.sizeBytes); out.writeInt(r.versionNo)
            out.writeLong(s.updatedAt); out.writeBoolean(s.dirty); out.writeUTF(s.engineId.orEmpty())
            out.writeInt(record.original.size); out.write(record.original)
            out.writeInt(record.engineSnapshot?.size ?: 0); record.engineSnapshot?.let(out::write)
        }; bytes.toByteArray()
    }
    fun decode(bytes: ByteArray): WorkDraftRecord = DataInputStream(ByteArrayInputStream(bytes)).use { input ->
        check(input.readInt() == 1) { "WORK_DRAFT_VERSION_UNSUPPORTED" }
        val id = input.readUTF()
        val ref = DocumentRef(input.readUTF(), input.readUTF(), input.readUTF(), input.readUTF(), input.readUTF(), input.readUTF(), input.readUTF(), input.readLong(), input.readInt())
        val summary = WorkDraftSummary(id, ref, input.readLong(), input.readBoolean(), input.readUTF().ifBlank { null })
        fun blob(): ByteArray {
            val size = input.readInt()
            check(size in 0..WorkDraftRepository.MAX_SIZE.toInt() && size <= input.available()) { "WORK_DRAFT_CORRUPT" }
            return ByteArray(size).also(input::readFully)
        }
        val original = blob()
        var snapshot: ByteArray? = null
        try {
            snapshot = blob().takeIf { it.isNotEmpty() }
            check(input.available() == 0) { "WORK_DRAFT_CORRUPT" }
            WorkDraftRecord(summary, original, snapshot)
        } catch (error: Exception) { original.fill(0); snapshot?.fill(0); throw error }
    }
}
