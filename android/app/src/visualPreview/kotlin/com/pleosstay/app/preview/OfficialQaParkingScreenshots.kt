package com.pleosstay.app.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.pleosstay.app.parking.ui.LocationUiState

@PreviewTest
@Preview(name = "ParkingLocationUnavailable", widthDp = 1682, heightDp = 1210, apiLevel = 34, locale = "ko-rKR")
@Composable
fun OfficialQaParkingLocationUnavailable() {
    val sample = previewSample(PreviewScreen.PARKING, LocalResources.current)
    val state = checkNotNull(sample.parking).copy(
        locationState = LocationUiState.FAILED,
        locationMessage = "위치를 확인할 수 없습니다. 다시 시도해 주세요.",
        places = emptyList(),
        total = 0,
        selected = null,
    )
    PreviewFeature(PreviewScreen.PARKING, sample.copy(parking = state))
}

@PreviewTest
@Preview(name = "ParkingDebugCoordinates", widthDp = 1682, heightDp = 1210, apiLevel = 34, locale = "ko-rKR")
@Composable
fun OfficialQaParkingDebugCoordinates() {
    val sample = previewSample(PreviewScreen.PARKING, LocalResources.current)
    PreviewFeature(
        PreviewScreen.PARKING,
        sample.copy(parking = checkNotNull(sample.parking).copy(showDeveloperCoordinates = true)),
    )
}
