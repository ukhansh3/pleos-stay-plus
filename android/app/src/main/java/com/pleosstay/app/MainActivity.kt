package com.pleosstay.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import android.content.Context
import com.pleosstay.app.waiting.data.*
import com.pleosstay.app.waiting.domain.WaitingPlace
import com.pleosstay.app.waiting.safety.PlatformWaitingSafetyGateway
import com.pleosstay.app.waiting.safety.WaitingSafetyGateway
import com.pleosstay.app.waiting.safety.UnavailableWaitingSafetyGateway
import com.pleosstay.app.waiting.ui.WaitingViewModel
import com.pleosstay.app.waiting.ui.WaitingScreen
import android.os.Bundle
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

class MainActivity : ComponentActivity() {
    private val waitingDependencies by lazy { WaitingDependencies.get(applicationContext) }
    private val waitingViewModel: WaitingViewModel by viewModels {
        WaitingViewModel.Factory(waitingDependencies.repository, waitingDependencies.safety)
    }

    override fun onStart() {
        super.onStart()
        waitingDependencies.safety.start()
    }

    override fun onStop() {
        waitingDependencies.safety.stop()
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val waitingModel = waitingViewModel
        enableEdgeToEdge()
        setContent {
            PleosStayPlusTheme {
                var showWaiting by remember { mutableStateOf(false) }
                val parkingState by parkingViewModel.state.collectAsState()
                Column {
                    Row {
                        TextButton(onClick = { showWaiting = false }) { Text("장소 검색") }
                        TextButton(onClick = { showWaiting = true }) { Text("대기 관리") }
                    }
                    if (showWaiting) {
                        val place = parkingState.selected?.let { WaitingPlace(it.id, it.name) }
                        WaitingScreen(waitingModel, place)
                    } else {
                        ParkingScreen(viewModel = parkingViewModel, onNearbySearch = ::requestNearbySearch)
                    }
                }
            }
        }
    }

    private fun requestNearbySearch() {
        if (ContextCompat.checkSelfPermission(this, FUSED_LOCATION_PERMISSION) == PackageManager.PERMISSION_GRANTED) {
            parkingViewModel.searchNearby()
        } else {
            locationPermissionLauncher.launch(FUSED_LOCATION_PERMISSION)
        }
    }
}

// App-process scoped DB/gateway: activity recreation must not create a second owner.
private class WaitingDependencies(context: Context) {
    val safety: WaitingSafetyGateway = if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
        runCatching { PlatformWaitingSafetyGateway(context) }.getOrElse { UnavailableWaitingSafetyGateway() }
    } else UnavailableWaitingSafetyGateway()
    val repository = LocalWaitingRepository(
        RoomWaitingStore.open(context), PreferencesDrivingEndJournal(context), { safety.state.value }
    )
    companion object {
        @Volatile private var instance: WaitingDependencies? = null
        fun get(context: Context): WaitingDependencies = instance ?: synchronized(this) {
            instance ?: WaitingDependencies(context.applicationContext).also { instance = it }
        }
    }
}
