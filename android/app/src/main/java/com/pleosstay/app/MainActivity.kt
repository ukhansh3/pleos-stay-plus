package com.pleosstay.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pleosstay.app.parking.data.HttpParkingRepository
import com.pleosstay.app.parking.navigation.PleosNavigationGateway
import com.pleosstay.app.parking.ui.ParkingScreen
import com.pleosstay.app.parking.ui.ParkingViewModel
import com.pleosstay.app.ui.theme.PleosStayPlusTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val factory = ParkingViewModel.Factory(
            repository = HttpParkingRepository(BuildConfig.PARKING_API_BASE_URL),
            navigationGateway = PleosNavigationGateway(applicationContext),
        )
        setContent {
            PleosStayPlusTheme {
                val parkingViewModel: ParkingViewModel = viewModel(factory = factory)
                ParkingScreen(viewModel = parkingViewModel)
            }
        }
    }
}
