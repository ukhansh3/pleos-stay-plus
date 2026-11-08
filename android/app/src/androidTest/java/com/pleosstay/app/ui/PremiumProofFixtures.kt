package com.pleosstay.app.ui

import android.content.Context
import android.net.Uri
import androidx.room.Room
import com.pleosstay.app.documents.*
import com.pleosstay.app.modes.ModeStartManager
import com.pleosstay.app.parking.data.ParkingRepository
import com.pleosstay.app.parking.domain.*
import com.pleosstay.app.parking.location.*
import com.pleosstay.app.parking.navigation.*
import com.pleosstay.app.pickup.data.PickupRepository
import com.pleosstay.app.pickup.domain.*
import com.pleosstay.app.study.*
import com.pleosstay.app.waiting.data.*
import com.pleosstay.app.waiting.domain.*
import com.pleosstay.app.waiting.safety.*
import com.pleosstay.app.work.docx.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

/** Synthetic, instrumentation-only inputs. This never grants production authorization. */
internal class ProofSafety : WaitingSafetyGateway {
    override val state = MutableStateFlow(WaitingSafety(VehicleMotion.PARKED, false, message = "테스트 정차"))
    override val drivingEvents = DrivingEventBuffer()
    override fun start() = Unit
    override fun stop() = Unit
    override fun close() = Unit
}
internal class ProofWaiting(context: Context, val safety: ProofSafety = ProofSafety()) : AutoCloseable {
    private val database = Room.inMemoryDatabaseBuilder(context, WaitingDatabase::class.java).build()
    val repository = LocalWaitingRepository(RoomWaitingStore(database), object : DrivingEndJournal {
        override fun read(): PendingDrivingEnd? = null
        override fun write(command: PendingDrivingEnd) = Unit
        override fun clear() = Unit
    }, { safety.state.value })
    val manager = ModeStartManager(repository, safety)
    override fun close() = database.close()
}
internal class ProofLocation : CurrentLocationGateway {
    override suspend fun getCurrentLocation() = CurrentLocationResult.Success(VehicleLocation(37.0, 127.0, System.currentTimeMillis()))
    override fun showAccessBlockedInformation() = Unit
    override fun close() = Unit
}
internal class ProofNavigation : NavigationGateway {
    override val status = MutableStateFlow<NavigationStatus>(NavigationStatus.Ready)
    override fun requestRoute(place: ParkingPlace) { status.value = NavigationStatus.CallbackReceived(place.name, "onRouteStarted") }
    override fun close() = Unit
}
internal class ProofParking : ParkingRepository {
    val places = listOf(
        ParkingPlace("proof-parking-one", "테스트 공영주차장", ParkingType.PARKING_LOT, 37.0, 127.0,
            "테스트 주소 1", 820, listOf("PARK"), "ELIGIBLE", "승용차 이용", "09:00–22:00", OperatingStatus.OPEN,
            null, FeeEstimateStatus.FREE, FeePolicy("FREE", null, null, null, null, null, "무료"), null, null, null, null, null, null, "TEST_FIXTURE", "2026-11-08"),
        ParkingPlace("proof-parking-two", "테스트 노상주차장", ParkingType.ON_STREET_PARKING, 37.0, 127.0,
            "테스트 주소 2", 1300, listOf("PARK"), "ELIGIBLE", "현장 표지 확인", "10:00–20:00", OperatingStatus.UNKNOWN,
            null, FeeEstimateStatus.UNKNOWN, FeePolicy("UNKNOWN", null, null, null, null, null, null), null, null, null, null, null, null, "TEST_FIXTURE", "2026-11-08")
    )
    override suspend fun search(latitude: Double, longitude: Double, radiusMeters: Int, plannedMinutes: Int, purpose: ParkingPurpose, freeOnly: Boolean) = ParkingSearchResult(places, places.size, "TEST_FIXTURE")
    override suspend fun detail(placeId: String) = places.first { it.id == placeId }.copy(distanceMeters = null)
}
internal class ProofPickup : PickupRepository {
    private var current = PickupSession("proof-pickup", null, PickupSessionStatus.ACTIVE, true,
        PickupParticipant("proof-guest", "테스트 참가자", PickupParticipantStatus.DEPARTED, Instant.now().toString()),
        null, 1, Instant.now().toString(), null)
    override suspend fun create(waitingSessionId: String?, idempotencyKey: String) = current
    override suspend fun getSession(sessionId: String) = current
    override suspend fun issueInvitation(sessionId: String, idempotencyKey: String) = PickupInvitation("proof-invite", "https://example.test/share/SYNTHETIC_UI_FIXTURE", Instant.now().plusSeconds(3600).toString())
    override suspend fun sendVehicleLocation(sessionId: String, location: PickupVehicleLocation) = Unit
    override suspend fun snapshot(sessionId: String) = PickupSnapshot(current, false, current.revision, Instant.now().toString(), null)
    override suspend fun events(sessionId: String, afterRevision: Int) = PickupEventPage(emptyList(), current.revision)
    override suspend fun complete(sessionId: String, idempotencyKey: String) = current.copy(status = PickupSessionStatus.COMPLETED).also { current = it }
    override suspend fun cancel(sessionId: String, reason: String, idempotencyKey: String) = current.copy(status = PickupSessionStatus.CANCELLED).also { current = it }
    override suspend fun endLinkedWaiting(sessionId: String, idempotencyKey: String) = current.copy(status = PickupSessionStatus.ENDED).also { current = it }
}

/** Real byte payloads, generated within androidTest; no personal source document is loaded. */
internal class ProofDocuments(private val directory: File) : SharedDocumentGateway {
    override val access = MutableStateFlow(DocumentAccess(true, "proof-user", "proof-connection", "TEST_ONLY", 1))
    private val text = "테스트 학습 자료\n\n학습 목표\n문서를 읽고 핵심 내용을 메모합니다.\n\n이번 차시\n학습자료의 내용을 확인하고 진도를 저장합니다.\n\n정리\n문서 열람과 메모의 화면 배치를 확인합니다.".toByteArray()
    private val docx = OoxmlDocxEngine.blank().also { document ->
        document.replaceText(document.view().paragraphs.first().id, 0, 0, "테스트 회의록")
        document.addParagraph(null, "회의 목적과 논의할 내용을 정리합니다.")
        document.addParagraph(null, "다음 작업과 확인할 사항을 기록합니다.")
        document.format(document.view().paragraphs.first().id, 0, "테스트 회의록".length, DocxStyle(size = 20f, bold = true))
        document.addTable(2, 2)
    }.export()
    val txtRef = ref("proof-text", "테스트_학습자료.txt", "text/plain", text)
    val docxRef = ref("proof-docx", "테스트_회의록.docx", DOCX_MIME, docx)
    private val payloads = mapOf(txtRef.fileId to text, docxRef.fileId to docx)
    private fun ref(id: String, name: String, mime: String, bytes: ByteArray) = DocumentRef("proof-user", "proof-connection", id, "${id}-v1", name, mime,
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }, bytes.size.toLong(), 1)
    override suspend fun refreshAccess() = access.value
    override suspend fun listFiles() = listOf(txtRef, docxRef).map { DocumentFile(it.userScope, it.connectionId, it.fileId, it.name) }
    override suspend fun listVersions(fileId: String) = listOf(txtRef, docxRef).filter { it.fileId == fileId }
    override suspend fun validate(ref: DocumentRef) = ref in listOf(txtRef, docxRef) && access.value.allowed
    override suspend fun downloadVerified(ref: DocumentRef): DocumentOpenResult {
        check(validate(ref))
        val file = File.createTempFile("proof-document-", ".${ref.name.substringAfterLast('.')}", directory).apply { writeBytes(payloads.getValue(ref.fileId)) }
        return DocumentOpenResult.Ready(file, ref)
    }
    override suspend fun uploadRevision(ref: DocumentRef, contentUri: Uri, idempotencyKey: String, progress: (Long, Long) -> Unit): DocumentUploadResult = DocumentUploadResult.Blocked("SCREENSHOT_TEST_HAS_NO_UPLOAD")
    override fun clearTemporaryFiles() = Unit
    override fun close() = Unit
}
internal class ProofStudyCipher : StudyCipher {
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    override fun encrypt(plain: ByteArray, context: ByteArray): ByteArray = Cipher.getInstance("AES/GCM/NoPadding").run {
        init(Cipher.ENCRYPT_MODE, key); updateAAD(context); iv + doFinal(plain)
    }
    override fun decrypt(sealed: ByteArray, context: ByteArray): ByteArray = Cipher.getInstance("AES/GCM/NoPadding").run {
        init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, sealed, 0, 12)); updateAAD(context); doFinal(sealed, 12, sealed.size - 12)
    }
}
