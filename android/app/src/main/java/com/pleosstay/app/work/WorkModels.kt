package com.pleosstay.app.work

import com.pleosstay.app.documents.DocumentFile
import com.pleosstay.app.documents.DocumentRef
import com.pleosstay.app.modes.ModeStartResult

enum class WorkFormat(val mimeType: String) {
    PDF("application/pdf"), DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    companion object { fun of(ref: DocumentRef): WorkFormat? = entries.firstOrNull { it.mimeType == ref.mimeType } }
}

/** No edit-command schema is invented before an approved engine proves real capabilities. */
data class WorkEngineReadiness(val approved: Boolean = false, val reason: String = "DOCX 자체 OOXML 편집 지원 · PDF 편집/업로드 제외",val docxAvailable:Boolean=true,val pdfAvailable:Boolean=false) {
    companion object {
        const val ENGINE_BLOCKED = "BLOCKED · PDF 본문·이미지·페이지 및 DOCX 고급 편집 엔진의 승인과 실파일 검증이 필요합니다."
    }
}

data class WorkDraftSummary(val id: String, val source: DocumentRef, val updatedAt: Long, val dirty: Boolean, val engineId: String?)

/** Opaque engine bytes are not plain-text editing or exported PDF/DOCX files. */
class WorkDraftRecord(val summary: WorkDraftSummary, val original: ByteArray, val engineSnapshot: ByteArray? = null) {
    fun clearBuffers() { original.fill(0); engineSnapshot?.fill(0) }
}

data class WorkSession(val waitingId: String?, val endsAt: Long) {
    fun remaining(now: Long): Long = (endsAt - now).coerceAtLeast(0)
}

data class WorkState(
    val allowed: Boolean = false, val busy: Boolean = false, val minutes: String = "30",
    val session: WorkSession? = null, val remainingMillis: Long = 0,
    val files: List<DocumentFile> = emptyList(), val versions: List<DocumentRef> = emptyList(),
    val drafts: List<WorkDraftSummary> = emptyList(), val selected: WorkDraftSummary? = null,
    val choice: ModeStartResult.NeedsChoice? = null, val storageError: Boolean = false,
    val exitConfirmation: Boolean = false,
    val message: String = "정차 상태와 기존 파일 연결·PIN을 확인해 주세요.",
    val engine: WorkEngineReadiness = WorkEngineReadiness(),
)
