package com.pleosstay.app.preview

import android.net.Uri
import com.pleosstay.app.entertainment.*
import com.pleosstay.app.entertainment.audio.*
import com.pleosstay.app.entertainment.data.*
import com.pleosstay.app.entertainment.games.*
import com.pleosstay.app.entertainment.quiz.*
import com.pleosstay.app.entertainment.karaoke.*
import com.pleosstay.app.entertainment.launcher.*
import com.pleosstay.app.entertainment.recommendation.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pleosstay.app.documents.*
import com.pleosstay.app.modes.ModeStartManager
import com.pleosstay.app.parking.data.ParkingRepository
import com.pleosstay.app.parking.domain.*
import com.pleosstay.app.parking.location.*
import com.pleosstay.app.parking.navigation.*
import com.pleosstay.app.parking.ui.ParkingViewModel
import com.pleosstay.app.pickup.data.PickupRepository
import com.pleosstay.app.pickup.domain.*
import com.pleosstay.app.pickup.ui.PickupViewModel
import com.pleosstay.app.realtime.*
import com.pleosstay.app.rest.*
import com.pleosstay.app.study.*
import com.pleosstay.app.waiting.data.*
import com.pleosstay.app.waiting.domain.*
import com.pleosstay.app.waiting.safety.*
import com.pleosstay.app.waiting.ui.WaitingViewModel
import com.pleosstay.app.work.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File

/** Only src/debug previews create these. No SDK, Room, Keystore, real identity or network requests. */
internal class PreviewModels {
    private val created = mutableListOf<ViewModel>()
    private fun <T : ViewModel> own(value: T): T = value.also { created += it }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val safety = object : WaitingSafetyGateway {
        override val state = MutableStateFlow(WaitingSafety()) // Real model remains fail closed.
        override val drivingEvents = DrivingEventBuffer()
        override fun start() = Unit
        override fun stop() = Unit
        override fun close() = Unit
    }
    private val waitingRepository = LocalWaitingRepository(object : WaitingStore {
        override fun all() = emptyList<WaitingSession>()
        override fun create(session: WaitingSession) = unavailable()
        override fun extend(session: WaitingSession, expectedRevision: Int) = unavailable()
        override fun end(id: String, at: Long, reason: EndReason) = unavailable()
    }, object : DrivingEndJournal {
        override fun read(): PendingDrivingEnd? = null
        override fun write(command: PendingDrivingEnd) = unavailable()
        override fun clear() = Unit
    }, { safety.state.value }, dispatcher = Dispatchers.Unconfined)
    private val manager = ModeStartManager(waitingRepository, safety)
    private val documents = object : SharedDocumentGateway {
        override val access = MutableStateFlow(DocumentAccess())
        override suspend fun refreshAccess() = access.value
        override suspend fun listFiles() = emptyList<DocumentFile>()
        override suspend fun listVersions(fileId: String) = emptyList<DocumentRef>()
        override suspend fun validate(ref: DocumentRef) = false
        override suspend fun downloadVerified(ref: DocumentRef) = DocumentOpenResult.Blocked("PREVIEW_ONLY")
        override suspend fun uploadRevision(ref: DocumentRef, contentUri: Uri, idempotencyKey: String, progress: (Long, Long) -> Unit) = DocumentUploadResult.Blocked("PREVIEW_ONLY")
        override fun clearTemporaryFiles() = Unit
        override fun close() = Unit
    }
    private val records = object : DocumentRecordStore {
        override suspend fun read(key: String): ByteArray? = null
        override suspend fun write(key: String, bytes: ByteArray) = unavailable()
        override suspend fun delete(key: String) = Unit
        override suspend fun keys() = emptyList<String>()
    }
    private val location = object : CurrentLocationGateway {
        override suspend fun getCurrentLocation() = CurrentLocationResult.Unsupported
        override fun showAccessBlockedInformation() = Unit
        override fun close() = Unit
    }
    val parking by lazy { own(ParkingViewModel(object : ParkingRepository {
        override suspend fun search(latitude: Double, longitude: Double, radiusMeters: Int, plannedMinutes: Int, purpose: ParkingPurpose, freeOnly: Boolean) = ParkingSearchResult(emptyList(), 0, "PREVIEW_ONLY")
        override suspend fun detail(placeId: String): ParkingPlace = unavailable()
    }, object : NavigationGateway {
        override val status = MutableStateFlow<NavigationStatus>(NavigationStatus.Initializing)
        override fun requestRoute(place: ParkingPlace) = Unit
        override fun close() = Unit
    }, location, Dispatchers.Unconfined)) }
    val waiting by lazy { own(WaitingViewModel(waitingRepository, safety)) }
    val pickup by lazy { own(PickupViewModel(object : PickupRepository {
        override suspend fun create(waitingSessionId: String?, idempotencyKey: String): PickupSession = unavailable()
        override suspend fun getSession(sessionId: String): PickupSession = unavailable()
        override suspend fun events(sessionId: String, afterRevision: Int): PickupEventPage = unavailable()
        override suspend fun issueInvitation(sessionId: String, idempotencyKey: String): PickupInvitation = unavailable()
        override suspend fun sendVehicleLocation(sessionId: String, location: PickupVehicleLocation) = unavailable()
        override suspend fun snapshot(sessionId: String): PickupSnapshot = unavailable()
        override suspend fun complete(sessionId: String, idempotencyKey: String): PickupSession = unavailable()
        override suspend fun cancel(sessionId: String, reason: String, idempotencyKey: String): PickupSession = unavailable()
        override suspend fun endLinkedWaiting(sessionId: String, idempotencyKey: String): PickupSession = unavailable()
    }, location, safety)) }
    val study by lazy { own(StudyViewModel(manager, safety, documents, object : StudyRepository {
        override suspend fun load(key: StudyMaterialKey) = StudyRecord(key)
        override suspend fun saveNote(key: StudyMaterialKey, id: String?, anchor: String?, body: String): StudyNote = unavailable()
        override suspend fun deleteNote(key: StudyMaterialKey, id: String) = unavailable()
        override suspend fun saveProgress(key: StudyMaterialKey, progress: StudyProgress) = unavailable()
        override suspend fun deleteMaterialData(key: StudyMaterialKey) = unavailable()
        override suspend fun lastMaterial(userScope: String, connectionId: String): StudyMaterialKey? = null
    })) }
    val work by lazy { own(WorkViewModel(documents, WorkDraftRepository(records, documents), manager, safety)) }
    val files by lazy { own(FileTransferViewModel(
        // A concrete client is required by the current constructor; no method is invoked.
        // Even an accidental action fails at the locked model before reaching it.
        RealtimeApi("https://example.invalid/api/v1") { unavailable() }, safety,
        object : RevisionStore {
            override suspend fun stage(request: WorkRevisionRequest): StagedRevision = unavailable()
            override fun describe(uri: Uri): Pair<String, String> = unavailable()
            override fun save(staged: StagedRevision, connection: String) = unavailable()
            override fun pending(connection: String): StagedRevision? = null
            override fun finish(staged: StagedRevision) = Unit
            override fun clear() = Unit
        }, File("preview-no-files"))) }
    val rest by lazy { RestController(object : RestPersistence {
        override fun presets() = RestPreset.defaults
        override fun savePresets(values: List<RestPreset>) = unavailable()
        override fun session(): RestSession? = null
        override fun saveSession(value: RestSession) = unavailable()
    }, object : RestAlerts {
        override fun problem(style: RestAlert) = "PREVIEW_ONLY"
        override fun soundProblem(style: RestAlert) = "PREVIEW_ONLY"
        override fun schedule(session: RestSession) = Unit
        override fun cancel(session: RestSession) = Unit
        override fun sound(session: RestSession, reason: RestEnd) = Unit
        override fun silence() = Unit
    }, object : RestSoundPlayer {
        override fun play() = false
        override fun stop() = Unit
    }, RestBattery { emptyFlow() }, manager, safety, scope, io = Dispatchers.Unconfined) }
    /** Inactive real screen only. Every operation and real admission remain denied. */
    val entertainment by lazy {
        val store=object:EntertainmentStore {override fun read(key:String):ByteArray?=null;override fun write(key:String,bytes:ByteArray)=unavailable()}
        val history=KaraokeHistory(store)
        val controller=EntertainmentController(object:EntertainmentActivityAdmission {override suspend fun acquire():String=unavailable();override suspend fun release(permit:String)=Unit},safety,emptyMap(),store,scope)
        val microphone=object:KaraokeMicrophone {override fun support()=MicrophoneSupport(false,"PREVIEW_ONLY");override fun frames():Flow<PcmChunk> = emptyFlow();override fun interrupt()=Unit;override suspend fun stop()=Unit}
        val player=object:KaraokeBackingPlayer {
            override var onEvent:((PlaybackEvent)->Unit)?=null
            override suspend fun prepare(song:KaraokeSong)=unavailable();override suspend fun seekTo(positionMillis:Long)=unavailable()
            override suspend fun play()=unavailable();override suspend fun pause()=Unit;override fun positionMillis()=0L
            override fun interrupt()=Unit;override suspend fun release()=Unit
        }
        val karaoke=KaraokeSession(microphone,player,history,store,scope,{safety.state.value},{false})
        val availability=MutableStateFlow<Map<EntertainmentContent,ContentAvailability>>(emptyMap())
        val video=VideoAppLauncher(object:VideoAppPlatform {override fun inspect(target:VerifiedVideoTarget)=LaunchResult(LaunchStatus.POLICY_BLOCKED,"PREVIEW_ONLY");override fun launch(target:VerifiedVideoTarget)=unavailable()},emptyList()){safety.state.value}
        EntertainmentRuntime(controller,Game2048Service(store),QuizService(store),karaoke,history,video,emptyList(),WaitingRecommendationFeed(waitingRepository,safety.state,availability),availability)
    }
    fun close() { created.forEach { it.viewModelScope.cancel() }; scope.cancel() }
}
private fun unavailable(): Nothing = error("Static Compose Preview: no operation is performed")
