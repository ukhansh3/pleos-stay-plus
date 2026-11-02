package com.pleosstay.app.parking.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pleosstay.app.parking.domain.FeeEstimateStatus
import com.pleosstay.app.parking.domain.ParkingPlace
import com.pleosstay.app.parking.domain.ParkingPurpose
import com.pleosstay.app.parking.navigation.NavigationStatus

@Composable
fun ParkingScreen(
    viewModel: ParkingViewModel,
    onNearbySearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("주변 대기 장소", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("현장 표지와 운영 규칙이 공공데이터보다 우선합니다.", style = MaterialTheme.typography.bodySmall)
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("내 주변", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(state.locationMessage, style = MaterialTheme.typography.bodySmall)
                    state.lastLocationLabel?.let { Text("최근 수신 좌표: $it", style = MaterialTheme.typography.labelSmall) }
                    Button(onClick = onNearbySearch, enabled = !state.loading) { Text("내 주변 검색") }
                    if (state.locationState == LocationUiState.FAILED) {
                        TextButton(onClick = viewModel::showLocationAccessInformation) { Text("위치 접근 차단 안내") }
                    }
                    TextButton(onClick = viewModel::toggleDeveloperCoordinates) {
                        Text(if (state.showDeveloperCoordinates) "개발자용 좌표 닫기" else "개발자용 좌표 입력")
                    }
                }
            }
        }
        if (state.showDeveloperCoordinates) {
            item {
                Text("개발자용 테스트 좌표", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.latitude,
                        onValueChange = { viewModel.updateCoordinates(latitude = it) },
                        label = { Text("위도") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = state.longitude,
                        onValueChange = { viewModel.updateCoordinates(longitude = it) },
                        label = { Text("경도") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                }
                Button(onClick = viewModel::searchManual, enabled = !state.loading) { Text("입력 좌표 검색") }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.radiusMeters,
                    onValueChange = { viewModel.updateFilters(radius = it) },
                    label = { Text("반경(m)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = state.plannedMinutes,
                    onValueChange = { viewModel.updateFilters(minutes = it) },
                    label = { Text("예정시간(분)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ParkingPurpose.entries.forEach { purpose ->
                    FilterChip(
                        selected = state.purpose == purpose,
                        onClick = { viewModel.updateFilters(purpose = purpose) },
                        label = { Text(purpose.label()) },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = state.freeOnly, onCheckedChange = { viewModel.updateFilters(freeOnly = it) })
                Text("무료만")
            }
        }
        if (state.loading) item { CircularProgressIndicator() }
        state.error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
        if (state.places.isNotEmpty()) item {
            Text("${state.total}개 결과 · 데이터 ${state.dataFreshness}", style = MaterialTheme.typography.titleSmall)
        }
        state.selected?.let { selected ->
            item { ParkingDetail(selected, state.navigationStatus, viewModel::navigate) }
        }
        items(state.places, key = { it.id }) { place ->
            ParkingRow(place = place, onClick = { viewModel.select(place) })
        }
    }
}

@Composable
private fun ParkingRow(place: ParkingPlace, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(place.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(listOf(place.type.label(), place.distanceLabel(), place.feeLabel()).joinToString(" · "))
            Text(place.address ?: "주소 정보 없음", style = MaterialTheme.typography.bodySmall)
            Text("운영시간: ${place.openingHoursText ?: "미확인"} · 현재 ${place.operatingStatus.label()}", style = MaterialTheme.typography.bodySmall)
            Text("이용 조건: ${place.eligibilityReason ?: "미확인"}", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ParkingDetail(place: ParkingPlace, navigationStatus: NavigationStatus, onNavigate: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("상세 · ${place.name}", style = MaterialTheme.typography.titleLarge)
            Text("유형: ${place.type.label()} · 거리: ${place.distanceLabel()}")
            Text(place.address ?: "주소 정보 없음")
            Text("운영시간: ${place.openingHoursText ?: "미확인"} · 현재 ${place.operatingStatus.label()}")
            Text("요금: ${place.feeLabel()}${place.feePolicy.rawText?.let { " ($it)" }.orEmpty()}")
            place.capacity?.let { Text("주차 구획: ${it}면") }
            Text("허용 용도: ${place.allowedUse.allowedUseLabel()}")
            Text("이용 조건: ${place.eligibilityReason ?: "미확인"}")
            place.restrictions?.let { Text("추가 조건: $it") }
            Text("관리: ${place.operatorName ?: "미확인"} / ${place.phoneNumber ?: "연락처 없음"}")
            Text("출처: ${place.source} · 기준일 ${place.sourceUpdatedAt ?: "미확인"}", style = MaterialTheme.typography.labelSmall)
            Text("실시간 빈자리 정보는 제공하지 않습니다.", style = MaterialTheme.typography.labelSmall)
            Button(onClick = onNavigate, enabled = navigationStatus is NavigationStatus.Ready || navigationStatus is NavigationStatus.CallbackReceived) {
                Text("길 안내")
            }
            Text(navigationStatus.label(), style = MaterialTheme.typography.bodySmall)
        }
    }
    Spacer(Modifier.height(4.dp))
}

private fun ParkingPurpose.label(): String = when (this) {
    ParkingPurpose.PICKUP -> "픽업"
    ParkingPurpose.SHORT_WAIT -> "단기"
    ParkingPurpose.LONG_STAY -> "장기"
}

private fun ParkingPlace.feeLabel(): String = when (feeEstimateStatus) {
    FeeEstimateStatus.FREE -> "무료"
    FeeEstimateStatus.ESTIMATED -> "약 ${estimatedFee ?: 0}원"
    FeeEstimateStatus.UNKNOWN -> "요금 미확인"
}

private fun ParkingPlace.distanceLabel(): String = distanceMeters?.let { distance ->
    if (distance < 1_000) "${distance}m" else String.format("%.1fkm", distance / 1_000.0)
} ?: "거리 미확인"

private fun com.pleosstay.app.parking.domain.ParkingType.label(): String = when (this) {
    com.pleosstay.app.parking.domain.ParkingType.PARKING_LOT -> "주차장"
    com.pleosstay.app.parking.domain.ParkingType.ON_STREET_PARKING -> "정식 노상주차장"
    com.pleosstay.app.parking.domain.ParkingType.PUBLIC_REST_AREA -> "공식 휴게소"
    com.pleosstay.app.parking.domain.ParkingType.PICKUP_ZONE -> "승하차 구역"
    com.pleosstay.app.parking.domain.ParkingType.TIME_LIMITED_ZONE -> "시간제 허용 구역"
}

private fun com.pleosstay.app.parking.domain.OperatingStatus.label(): String = when (this) {
    com.pleosstay.app.parking.domain.OperatingStatus.OPEN -> "운영 중"
    com.pleosstay.app.parking.domain.OperatingStatus.CLOSED -> "운영 종료"
    com.pleosstay.app.parking.domain.OperatingStatus.UNKNOWN -> "상태 미확인"
}

private fun List<String>.allowedUseLabel(): String = if (isEmpty()) "미확인" else joinToString(", ") { use ->
    when (use) {
        "PARK" -> "주차"
        "STOP" -> "정차"
        "PICKUP" -> "승하차"
        else -> use
    }
}

private fun NavigationStatus.label(): String = when (this) {
    NavigationStatus.Initializing -> "NaviHelper 초기화 중"
    NavigationStatus.Ready -> "NaviHelper 준비됨"
    is NavigationStatus.Requesting -> "${destination} 길 안내 요청 중"
    is NavigationStatus.CallbackReceived -> "${destination}: NaviHelper $callback 콜백 수신"
    is NavigationStatus.Failed -> message
}
