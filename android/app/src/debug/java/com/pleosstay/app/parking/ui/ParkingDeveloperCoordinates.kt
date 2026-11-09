package com.pleosstay.app.parking.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pleosstay.app.ui.premium.PremiumAction
import com.pleosstay.app.ui.premium.StayColors

@Composable
internal fun ParkingDeveloperCoordinates(state: ParkingUiState, viewModel: ParkingViewModel) {
    if (!viewModel.developerCoordinatesEnabled) return
    TextButton(onClick = viewModel::toggleDeveloperCoordinates, enabled = !state.loading) {
        Text(
            if (state.showDeveloperCoordinates) "개발자 좌표 닫기" else "개발자 좌표 입력",
            color = StayColors.Secondary,
        )
    }
    if (!state.showDeveloperCoordinates) return

    OutlinedTextField(
        state.latitude,
        { viewModel.updateCoordinates(latitude = it) },
        label = { Text("위도") },
        enabled = !state.loading,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        state.longitude,
        { viewModel.updateCoordinates(longitude = it) },
        label = { Text("경도") },
        enabled = !state.loading,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    PremiumAction(
        "입력 좌표 검색",
        viewModel::searchManual,
        Modifier.fillMaxWidth(),
        !state.loading,
        true,
    )
}
