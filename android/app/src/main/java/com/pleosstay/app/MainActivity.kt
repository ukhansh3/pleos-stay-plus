package com.pleosstay.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Modifier
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.pleosstay.app.rest.RestAlarm
import com.pleosstay.app.rest.RestAudio
import com.pleosstay.app.rest.RestController
import com.pleosstay.app.rest.RestEvGateway
import com.pleosstay.app.rest.RestScreen
import com.pleosstay.app.rest.RestStore
import android.content.Intent
import android.content.Context
import com.pleosstay.app.waiting.data.*
import com.pleosstay.app.modes.ModeStartManager
import com.pleosstay.app.waiting.domain.WaitingPlace
import com.pleosstay.app.waiting.domain.WaitingSession
import com.pleosstay.app.waiting.domain.WaitingStatus
import com.pleosstay.app.waiting.safety.PlatformWaitingSafetyGateway
import com.pleosstay.app.waiting.safety.WaitingDrivingMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import com.pleosstay.app.waiting.safety.WaitingSafetyGateway
import com.pleosstay.app.waiting.safety.UnavailableWaitingSafetyGateway
import com.pleosstay.app.waiting.ui.WaitingViewModel
import com.pleosstay.app.waiting.ui.WaitingScreen
import com.pleosstay.app.realtime.RealtimeEntry
import com.pleosstay.app.workspace.DocumentWorkspaceEntry
import kotlinx.coroutines.flow.MutableStateFlow
import android.view.WindowManager
import android.os.Bundle
import android.util.Log
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.pleosstay.app.parking.data.HttpParkingRepository
import com.pleosstay.app.parking.location.FUSED_LOCATION_PERMISSION
import com.pleosstay.app.parking.location.PleosFusedLocationGateway
import com.pleosstay.app.parking.navigation.PleosNavigationGateway
import com.pleosstay.app.parking.ui.ParkingScreen
import com.pleosstay.app.parking.ui.ParkingViewModel
import com.pleosstay.app.ui.theme.PleosStayPlusTheme
import com.pleosstay.app.pickup.auth.SupabaseRestDriverIdentityProvider
import com.pleosstay.app.pickup.data.HttpPickupRepository
import com.pleosstay.app.pickup.tts.PleosPickupTtsGateway
import com.pleosstay.app.pickup.tts.UnsupportedPickupTtsGateway
import com.pleosstay.app.pickup.ui.PickupScreen
import com.pleosstay.app.pickup.ui.PickupViewModel

class MainActivity : ComponentActivity() {

    private val waitingDependencies by lazy { WaitingDependencies.get(applicationContext) }
    internal val modeStartManager get() = waitingDependencies.modeStartManager
    private var openRest by mutableStateOf(false)
    private val documentForeground = MutableStateFlow(false)
    private val waitingViewModel: WaitingViewModel by viewModels {
        WaitingViewModel.Factory(waitingDependencies.repository, waitingDependencies.safety, waitingDependencies.monitor)
    }
    private val pickupViewModel: PickupViewModel by viewModels {
        PickupViewModel.Factory(
            repository = HttpPickupRepository(
                BuildConfig.PICKUP_API_BASE_URL,
                SupabaseRestDriverIdentityProvider(applicationContext, BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY),
            ),
            locationGateway = PleosFusedLocationGateway(applicationContext),
            safety = waitingDependencies.safety,
            tts = runCatching { PleosPickupTtsGateway(applicationContext) }.getOrElse { error ->
                val reason = "Pleos TTS SDK_VERIFY: ${error.javaClass.simpleName} while constructing official adapter"
                Log.w("PickupTts", reason)
                UnsupportedPickupTtsGateway(reason)
            },
        )
    }

    override fun onStart() {
        super.onStart()
        waitingDependencies.safety.setForeground(true)
        waitingDependencies.safety.start()
    }

    override fun onResume() {
        super.onResume()
        if (android.os.Build.VERSION.SDK_INT < 29) {
            waitingDependencies.rest.setForeground(true)
            documentForeground.value = true
        }
    }

    override fun onTopResumedActivityChanged(isTopResumedActivity: Boolean) {
        super.onTopResumedActivityChanged(isTopResumedActivity)
        // PLEOS multi-window can keep several activities RESUMED after Home/app switches.
        waitingDependencies.rest.setForeground(isTopResumedActivity)
        documentForeground.value = isTopResumedActivity
        Log.d("RestLifecycle", "topResumed=" + isTopResumedActivity)
    }

    override fun onPause() {
        documentForeground.value = false
        waitingDependencies.rest.setForeground(false)
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("openRest", false)) openRest = true
    }

    override fun onStop() {
        documentForeground.value = false
        waitingDependencies.safety.setForeground(false)
        super.onStop()
    }
    private val parkingViewModel: ParkingViewModel by viewModels {
        ParkingViewModel.Factory(
            repository = HttpParkingRepository(BuildConfig.PARKING_API_BASE_URL),
            navigationGateway = PleosNavigationGateway(applicationContext),
            locationGateway = PleosFusedLocationGateway(applicationContext),
        )
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) parkingViewModel.searchNearby() else parkingViewModel.onLocationPermissionDenied()
    }
    private val pickupLocationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        pickupViewModel.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val waitingModel = waitingViewModel
        openRest = intent.getBooleanExtra("openRest", false)
        enableEdgeToEdge()
        setContent {
            PleosStayPlusTheme {
                var section by rememberSaveable { mutableIntStateOf(0) }
                val documentVisible by documentForeground.collectAsState()
                DisposableEffect(section) {
                    if (section == 5 || section == 6) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    onDispose {
                        if (section == 5 || section == 6) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
                LaunchedEffect(openRest) {
                    if (openRest) { section = 4; openRest = false }
                }
                val parkingState by parkingViewModel.state.collectAsState()
                val waitingState by waitingModel.state.collectAsState()
                val pickupState by pickupViewModel.state.collectAsState()
                LaunchedEffect(waitingState.sessions, pickupState.session) {
                    val linkedId = pickupState.session?.waitingSessionId
                    if (hasEndedLinkedWaiting(linkedId, waitingState.sessions)) {
                        pickupViewModel.endLinkedWaiting()
                    }
                }
                Column {
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        TextButton(onClick = { section = 0 }) { Text("장소 검색") }
                        TextButton(onClick = { section = 1; requestVehicleInformation() }) { Text("대기 관리") }
                        TextButton(onClick = { section = 2; requestVehicleInformation() }) { Text("픽업") }
                        TextButton(onClick = { section = 3; requestVehicleInformation() }) { Text("파일 전송") }
                        TextButton(onClick = { section = 4; requestVehicleInformation() }) { Text("휴식") }
                        TextButton(onClick = { section = 5; requestVehicleInformation() }) { Text("공부") }
                        TextButton(onClick = { section = 6; requestVehicleInformation() }) { Text("업무") }
                    }
                    when (section) {
                        1 -> {
                            val place = parkingState.selected?.let { WaitingPlace(it.id, it.name) }
                            WaitingScreen(waitingModel, place)
                        }
                        2 -> PickupScreen(pickupViewModel, ::requestPickupStart)
                        3, 5, 6 -> RealtimeEntry(applicationContext, waitingDependencies.safety, documentVisible) { model, endpoint ->
                            if (section == 3) com.pleosstay.app.realtime.FileTransferScreen(model)
                            else DocumentWorkspaceEntry(applicationContext, model, endpoint, modeStartManager,
                                waitingDependencies.safety, section == 5, documentVisible, onOpenFiles = { section = 3 },
                                onOpenWaiting = { section = 1 }, onHome = { section = 0 })
                        }
                        4 -> RestScreen(waitingDependencies.rest, onRecoverWaiting = waitingModel::reload) { section = 0 }
                        else -> ParkingScreen(viewModel = parkingViewModel, onNearbySearch = ::requestNearbySearch)
                    }
                }
            }
        }
    }

    private val vehiclePermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            // Reconnect to subscribe to properties newly available after the permission grant.
            waitingDependencies.safety.stop()
            waitingDependencies.safety.start()
        }
    }

    private fun requestVehicleInformation() {
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE) &&
            ContextCompat.checkSelfPermission(this, "android.car.permission.CAR_SPEED") != PackageManager.PERMISSION_GRANTED) {
            vehiclePermissionLauncher.launch("android.car.permission.CAR_SPEED")
        }
    }
    private fun requestNearbySearch() {
        if (ContextCompat.checkSelfPermission(this, FUSED_LOCATION_PERMISSION) == PackageManager.PERMISSION_GRANTED) {
            parkingViewModel.searchNearby()
        } else {
            locationPermissionLauncher.launch(FUSED_LOCATION_PERMISSION)
        }
    }
    private fun requestPickupStart() {
        if (ContextCompat.checkSelfPermission(this, FUSED_LOCATION_PERMISSION) == PackageManager.PERMISSION_GRANTED) {
            pickupViewModel.start()
        } else {
            pickupLocationPermissionLauncher.launch(FUSED_LOCATION_PERMISSION)
        }
    }
}

internal fun hasEndedLinkedWaiting(linkedId: String?, sessions: List<WaitingSession>): Boolean =
    linkedId != null && sessions.any { it.id == linkedId && it.status == WaitingStatus.ENDED }

internal fun restRuntime(context: Context): RestController = WaitingDependencies.get(context).rest

// App-process scoped DB/gateway: activity recreation must not create a second owner.
private class WaitingDependencies(context: Context) {
    val safety: WaitingSafetyGateway = if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
        runCatching { PlatformWaitingSafetyGateway(context) }.getOrElse { UnavailableWaitingSafetyGateway() }
    } else UnavailableWaitingSafetyGateway()
    val repository = LocalWaitingRepository(
        RoomWaitingStore.open(context), PreferencesDrivingEndJournal(context), { safety.state.value }
    )
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val monitor = WaitingDrivingMonitor(repository, safety.drivingEvents, scope)
    val modeStartManager = ModeStartManager(repository, safety)
    val rest = RestController(RestStore(context), RestAlarm(context), RestAudio(context), RestEvGateway(context), modeStartManager, safety, scope)
    companion object {
        @Volatile private var instance: WaitingDependencies? = null
        fun get(context: Context): WaitingDependencies = instance ?: synchronized(this) {
            instance ?: WaitingDependencies(context.applicationContext).also { instance = it }
        }
    }
}
