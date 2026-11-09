package com.pleosstay.app.preview

import android.content.res.Resources
import com.pleosstay.app.R
import com.pleosstay.app.documents.*
import com.pleosstay.app.parking.domain.*
import com.pleosstay.app.parking.navigation.NavigationStatus
import com.pleosstay.app.parking.ui.*
import com.pleosstay.app.pickup.domain.*
import com.pleosstay.app.pickup.ui.PickupUiState
import com.pleosstay.app.realtime.FileTransferState
import com.pleosstay.app.rest.*
import com.pleosstay.app.study.*
import com.pleosstay.app.ui.premium.FeatureInspectionState
import com.pleosstay.app.waiting.domain.*
import com.pleosstay.app.waiting.safety.*
import com.pleosstay.app.waiting.ui.WaitingUiState
import com.pleosstay.app.work.*
import com.pleosstay.app.work.docx.*
import java.io.File
import java.time.Instant
import org.json.JSONObject

internal enum class PreviewScreen { PARKING, WAITING, PICKUP, REALTIME, REST, STUDY, WORK_LIBRARY, WORK_EDITOR }

/** Synthetic display state; no real location, invitation, credentials, or user documents. */
internal fun previewSample(screen: PreviewScreen, resources: Resources): FeatureInspectionState {
    val now = System.currentTimeMillis()
    val safety = WaitingSafety(VehicleMotion.PARKED, false, message = "예시 정차 상태")
    val document = DocumentRef("preview", "preview", "sample-docx", "v1", "예시_프로젝트.docx", DOCX_MIME, "0".repeat(64), 4096, 1)
    val files = listOf(DocumentFile("preview", "preview", document.fileId, document.name), DocumentFile("preview", "preview", "sample-pdf", "예시_학습자료.pdf"))
    val draft = DocxDraftInfo("sample-draft", "preview", "preview", document.name, now, document)
    return when (screen) {
        PreviewScreen.PARKING -> {
            val place = ParkingPlace("sample-parking", "예시 공영주차장", ParkingType.PARKING_LOT,
                0.0, 0.0, "예시 주소", 820, listOf("PARK"), "ELIGIBLE", "일반 차량 이용",
                "09:00–22:00", OperatingStatus.OPEN, 1000, FeeEstimateStatus.ESTIMATED,
                FeePolicy("LINEAR", 30, 1000, 10, 300, "30분 1,000원 / 추가 10분 300원", null),
                "공영", "노외", 120, null, null, null, "미리보기 예시 데이터", null)
            val places = listOf(place, place.copy(id = "sample-street", name = "예시 노상주차장", type = ParkingType.ON_STREET_PARKING, distanceMeters = 1300), place.copy(id = "sample-rest", name = "예시 휴게 주차 공간", type = ParkingType.PUBLIC_REST_AREA, distanceMeters = 1700))
            FeatureInspectionState(parking = ParkingUiState(places = places, selected = place, total = places.size,
                plannedMinutes = "30", locationState = LocationUiState.READY, navigationStatus = NavigationStatus.Ready))
        }
        PreviewScreen.WAITING -> FeatureInspectionState(waiting = WaitingUiState(
            sessions = listOf(WaitingSession("sample-waiting", now - 12 * 60_000, now + 18 * 60_000, place = WaitingPlace("sample-parking", "예시 공영주차장"))),
            safety = safety, now = now, ready = true))
        PreviewScreen.PICKUP -> {
            val expiry = Instant.ofEpochMilli(now + 45 * 60_000).toString()
            FeatureInspectionState(pickup = PickupUiState(
                session = PickupSession("sample-pickup", null, PickupSessionStatus.ACTIVE, true,
                    PickupParticipant("sample-guest", "예시 참가자", PickupParticipantStatus.DEPARTED, Instant.ofEpochMilli(now).toString()), expiry, 1, Instant.ofEpochMilli(now - 15 * 60_000).toString(), null),
                invitation = PickupInvitation("sample-invitation", "https://example.invalid/pickup/preview-only", expiry),
                sharingLocation = true, safetyMessage = "예시 정차 상태", locationMessage = "예시 위치 공유 중"))
        }
        PreviewScreen.REALTIME -> FeatureInspectionState(files = FileTransferState(allowed = true, unlocked = true,
            connectionId = "preview-connection", message = "PC 연결됨", files = files.map { f -> JSONObject().put("id", f.fileId).put("name", f.name) },
            versions = listOf(JSONObject().put("id", "v1").put("fileId", document.fileId).put("fileName", document.name).put("versionNo", 1).put("mimeType", DOCX_MIME).put("size", 4096).put("sha256", "0".repeat(64)).put("createdAt", Instant.ofEpochMilli(now).toString()))))
        PreviewScreen.REST -> FeatureInspectionState(rest = RestState(presets = RestPreset.defaults, ready = true,
            session = RestSession(RestPreset.defaults[2], now - 12 * 60_000, now + 18 * 60_000, "sample-waiting", alarmMode = RestAlarmMode.FOREGROUND_ONLY),
            ev = EvReading(54, "예시 배터리")), safety = safety)
        PreviewScreen.STUDY -> FeatureInspectionState(study = StudyUiState(active = true, remainingMillis = 25 * 60_000, allowed = true, message = "",
            files = files, versions = listOf(document), selected = document,
            document = StudyDocument(StudyFormat.DOCX, File("preview-no-file.docx"), blocks = listOf(
                StudyBlock.Paragraph(listOf(StudyTextRun("예시 학습자료", bold = true, points = 24f))),
                StudyBlock.Paragraph(listOf(StudyTextRun("핵심 내용을 읽고 오른쪽 메모에 정리합니다."))),
                StudyBlock.Paragraph(listOf(StudyTextRun("1. 주요 개념", bold = true))),
                StudyBlock.Paragraph(listOf(StudyTextRun("이 내용은 화면 배치 확인을 위한 예시입니다."))))),
            record = StudyRecord(StudyMaterialKey("preview", "preview", document.fileId, document.versionId),
                notes = listOf(StudyNote("sample-note", "0", "예시 메모: 핵심 내용을 정리합니다.", now)),
                progress = StudyProgress("BLOCK", "0", .35f, false, now))))
        PreviewScreen.WORK_LIBRARY -> FeatureInspectionState(work = WorkState(allowed = true,
            session = WorkSession("sample-waiting", now + 30 * 60_000), remainingMillis = 30 * 60_000,
            files = files, versions = listOf(document), message = ""), docx = DocxEditorState(drafts = listOf(draft)))
        PreviewScreen.WORK_EDITOR -> {
            fun p(id: Int, text: String, bold: Boolean = false, points: Float = 16f) =
                DocxParagraph(id, listOf(DocxRun(text, "Noto Sans CJK KR", points, bold, false, false, "273B63")), "left", true)
            // Decode the drawable through its typed API; retain the same source artwork.
            val bitmap = checkNotNull(android.graphics.BitmapFactory.decodeResource(resources, R.drawable.work_laptop, android.graphics.BitmapFactory.Options().apply { inScaled = false }))
            val image = try {
                java.io.ByteArrayOutputStream().use { output ->
                    check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
                    output.toByteArray()
                }
            } finally { bitmap.recycle() }
            FeatureInspectionState(docx = DocxEditorState(draft = draft, dirty = true, canUndo = true, message = "",
                view = DocxView(listOf(p(0, "예시 프로젝트 제안서", true, 24f), p(1, "주요 기능과 작업 계획을 정리합니다."),
                    p(2, "기능", true), p(3, "설명", true), p(4, "Study"), p(5, "자료 열람과 메모"), p(6, "Work"), p(7, "문서 작성과 편집")),
                    listOf(DocxTable(0, listOf(listOf(listOf(2), listOf(3)), listOf(listOf(4), listOf(5)), listOf(listOf(6), listOf(7))))),
                    listOf(DocxImage(0, image, 240, 180)), null)))
        }
    }
}
