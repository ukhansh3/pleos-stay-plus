package com.pleosstay.app.parking.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pleosstay.app.BuildConfig
import com.pleosstay.app.R
import com.pleosstay.app.parking.domain.*
import com.pleosstay.app.parking.navigation.NavigationStatus
import com.pleosstay.app.ui.premium.*

@Composable
fun ParkingScreen(viewModel: ParkingViewModel, onNearbySearch: () -> Unit, modifier: Modifier = Modifier, onWaiting: () -> Unit = {}) {
    val state by viewModel.state.collectAsState()
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = detailId != null) { detailId = null }
    val selected = state.selected?.takeIf { it.id == detailId }?.let { detail ->
        detail.copy(distanceMeters = detail.distanceMeters ?: state.places.firstOrNull { it.id == detail.id }?.distanceMeters)
    }
    val resultsScroll = rememberLazyListState()
    val detailScroll = rememberScrollState()
    LaunchedEffect(detailId) { detailScroll.scrollTo(0) }
    BoxWithConstraints(modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 16.dp)) {
        val wide = maxWidth >= 1000.dp
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Text("주차·대기 장소 검색", fontSize = 38.sp, lineHeight = 46.sp, fontWeight = FontWeight.Bold)
            if (wide) {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    FeaturePanel(Modifier.weight(.88f).fillMaxHeight()) {
                        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            ParkingConditions(state, viewModel, onNearbySearch)
                        }
                    }
                    FeaturePanel(Modifier.weight(1.72f).fillMaxHeight()) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("검색 결과", fontSize = 27.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            Text("${state.total}곳", color = StayColors.Blue, fontSize = 22.sp)
                        }
                        ParkingResults(state, detailId, Modifier.weight(1f), resultsScroll) { place ->
                            detailId = place.id; viewModel.select(place)
                        }
                    }
                    FeaturePanel(Modifier.weight(1.08f).fillMaxHeight()) {
                        Column(Modifier.verticalScroll(detailScroll), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            Text("선택한 장소", fontSize = 27.sp, fontWeight = FontWeight.Bold)
                            if (selected != null) ParkingSelectedInfo(selected, state.navigationStatus, viewModel::navigate, onWaiting)
                            else Text(if (detailId != null && state.loading) "상세정보 확인 중…" else "목록에서 장소를 선택하세요.", color = StayColors.Secondary, fontSize = 20.sp)
                        }
                    }
                }
            } else {
                LazyColumn(Modifier.weight(1f), state = resultsScroll, verticalArrangement = Arrangement.spacedBy(20.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    item { FeaturePanel(Modifier.fillMaxWidth()) { ParkingConditions(state, viewModel, onNearbySearch) } }
                    if (detailId != null) {
                        item { TextButton(onClick = { detailId = null }) { Text("검색 결과로 돌아가기") } }
                        if (selected != null) item { FeaturePanel(Modifier.fillMaxWidth()) { ParkingSelectedInfo(selected, state.navigationStatus, viewModel::navigate, onWaiting) } }
                    } else {
                        item { Text("검색 결과 · ${state.total}곳", fontSize = 27.sp, fontWeight = FontWeight.Bold) }
                        items(state.places, key = { it.id }) { place -> ParkingRow(place, false) { detailId = place.id; viewModel.select(place) } }
                        if (state.places.isEmpty() && !state.loading) item { Text("검색 결과가 없습니다.", color = StayColors.Secondary) }
                    }
                    if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    state.error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.ParkingConditions(state: ParkingUiState, viewModel: ParkingViewModel, onNearbySearch: () -> Unit) {
    Text("검색 조건", fontSize = 27.sp, fontWeight = FontWeight.Bold)
    HorizontalDivider(color = StayColors.Border)
    Text("검색 위치", fontSize = 18.sp, color = StayColors.Secondary)
    PremiumAction(if (state.locationState == LocationUiState.LOCATING) "위치 확인 중…" else "내 주변 검색", onNearbySearch, Modifier.fillMaxWidth(), !state.loading)
    if (state.locationState !in listOf(LocationUiState.IDLE, LocationUiState.READY)) {
        val locationHint = when (state.locationState) {
            LocationUiState.LOCATING -> "현재 위치를 확인하고 있습니다."
            LocationUiState.PERMISSION_DENIED -> "위치 권한을 허용한 뒤 다시 검색해 주세요."
            LocationUiState.DISABLED -> "차량의 위치 설정을 켜 주세요."
            LocationUiState.UNSUPPORTED -> "이 차량에서는 현재 위치를 사용할 수 없습니다."
            else -> "위치를 받지 못했습니다. 다시 검색해 주세요."
        }
        Text(locationHint, color = StayColors.Secondary, fontSize = 18.sp)
    }
    if (state.locationState in listOf(LocationUiState.FAILED, LocationUiState.PERMISSION_DENIED, LocationUiState.DISABLED)) TextButton(onClick = viewModel::showLocationAccessInformation) { Text("위치 권한 확인") }
    OutlinedTextField(state.radiusMeters, { viewModel.updateFilters(radius = it) }, label = { Text("검색 반경(m)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(state.plannedMinutes, { viewModel.updateFilters(minutes = it) }, label = { Text("예상 이용 시간(분)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
    Text("대기 목적", color = StayColors.Secondary, fontSize = 18.sp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ParkingPurpose.entries.forEach { purpose ->
            FilterChip(state.purpose == purpose, { viewModel.updateFilters(purpose = purpose) }, label = { Text(purpose.label()) }, modifier = Modifier.weight(1f))
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("무료만 보기", Modifier.weight(1f), fontSize = 19.sp)
        Switch(state.freeOnly, { viewModel.updateFilters(freeOnly = it) })
    }
    if (BuildConfig.DEBUG) {
        TextButton(onClick = viewModel::toggleDeveloperCoordinates) { Text(if (state.showDeveloperCoordinates) "좌표 입력 닫기" else "좌표 입력", color = StayColors.Secondary) }
        if (state.showDeveloperCoordinates) {
            OutlinedTextField(state.latitude, { viewModel.updateCoordinates(latitude = it) }, label = { Text("위도") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(state.longitude, { viewModel.updateCoordinates(longitude = it) }, label = { Text("경도") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            PremiumAction("입력 좌표 검색", viewModel::searchManual, Modifier.fillMaxWidth(), !state.loading, true)
        }
    }
}

@Composable
private fun ParkingResults(state: ParkingUiState, selectedId: String?, modifier: Modifier, scroll: androidx.compose.foundation.lazy.LazyListState, select: (ParkingPlace) -> Unit) {
    LazyColumn(modifier, state = scroll, verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
        if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        state.error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error, fontSize = 20.sp) } }
        if (state.places.isEmpty() && !state.loading && state.error == null) item {
            Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                StayIcon("PARKING", Modifier.size(42.dp), StayColors.Secondary)
                Text("검색 결과가 없습니다.", fontSize = 23.sp)
                Text("내 주변 검색을 누르거나 조건을 변경하세요.", color = StayColors.Secondary, fontSize = 19.sp)
            }
        }
        items(state.places, key = { it.id }) { place -> ParkingRow(place, place.id == selectedId) { select(place) } }
    }
}

@Composable
private fun ParkingRow(place: ParkingPlace, selected: Boolean, onClick: () -> Unit) {
    StayCard(Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("parking_result_${place.id}")) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(place.name, Modifier.weight(1f), fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold)
            if (selected) Text("선택됨", color = StayColors.Blue, fontSize = 16.sp)
        }
        Spacer(Modifier.height(10.dp))
        Text("${place.type.label()} · ${place.distanceLabel()}", fontSize = 19.sp, color = StayColors.Secondary)
        Spacer(Modifier.height(8.dp))
        Text(place.feeLabel(), color = StayColors.Blue, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
        Text(place.openingHoursText ?: "운영시간 미확인", color = StayColors.Secondary, fontSize = 18.sp)
        place.eligibilityReason?.let { Text(it, fontSize = 18.sp, color = StayColors.Secondary) }
    }
}

@Composable
internal fun ParkingDetail(place: ParkingPlace, navigationStatus: NavigationStatus, onNavigate: () -> Unit, onWaiting: () -> Unit) {
    FeaturePanel(Modifier.fillMaxWidth()) { ParkingSelectedInfo(place, navigationStatus, onNavigate, onWaiting) }
}

@Composable
private fun ParkingSelectedInfo(place: ParkingPlace, navigationStatus: NavigationStatus, onNavigate: () -> Unit, onWaiting: () -> Unit) {
    Column(Modifier.testTag("parking_selected_detail"), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        PhotoPanel(R.drawable.parking_visual, Modifier.fillMaxWidth().height(160.dp)) {}
        Text(place.name, fontSize = 27.sp, lineHeight = 35.sp, fontWeight = FontWeight.Bold)
        Text("${place.type.label()} · ${place.distanceLabel()}", fontSize = 20.sp, color = StayColors.Secondary)
        InfoLine("주소", place.address ?: "정보 없음")
        Text("예상 이용요금", fontSize = 18.sp, color = StayColors.Secondary)
        Text(place.feeLabel(), fontSize = 30.sp, color = StayColors.Blue, fontWeight = FontWeight.Bold)
        place.feePolicy.rawText?.let { Text(it, fontSize = 18.sp, color = StayColors.Secondary) }
        InfoLine("운영시간", place.openingHoursText ?: "정보 없음")
        InfoLine("이용 상태", place.operatingStatus.label())
        InfoLine("허용 용도", place.allowedUse.allowedUseLabel())
        place.eligibilityReason?.let { InfoLine("이용 조건", it) }
        place.restrictions?.let { InfoLine("추가 조건", it) }
        place.capacity?.let { InfoLine("전체 주차면", "${it}면") }
        if (place.operatorName != null || place.phoneNumber != null) InfoLine("관리·문의", listOfNotNull(place.operatorName, place.phoneNumber).joinToString(" / "))
        InfoLine("출처", place.source)
        place.sourceUpdatedAt?.let { Text("갱신 $it", color = StayColors.Secondary, fontSize = 16.sp) }
        Text("현장 표지와 운영 규칙을 확인하세요.", color = StayColors.Secondary, fontSize = 17.sp)
        PremiumAction("길 안내", onNavigate, Modifier.fillMaxWidth(), navigationStatus is NavigationStatus.Ready || navigationStatus is NavigationStatus.CallbackReceived)
        when (navigationStatus) {
            is NavigationStatus.Requesting -> Text("길 안내 요청 중…", color = StayColors.Secondary)
            is NavigationStatus.CallbackReceived -> Text(if (navigationStatus.callback == "onRouteStarted") "길 안내가 시작되었습니다." else "경로 요청이 처리되었습니다.", color = StayColors.Blue)
            is NavigationStatus.Failed -> Text(navigationStatus.message, color = MaterialTheme.colorScheme.error)
            NavigationStatus.Initializing -> Text("내비게이션 연결 중…", color = StayColors.Secondary)
            NavigationStatus.Ready -> Unit
        }
        PremiumAction("이 장소에서 대기", onWaiting, Modifier.fillMaxWidth(), secondary = true)
    }
}
private fun ParkingPurpose.label() = when (this) { ParkingPurpose.PICKUP -> "픽업"; ParkingPurpose.SHORT_WAIT -> "단기"; ParkingPurpose.LONG_STAY -> "장기" }
private fun ParkingPlace.feeLabel() = when (feeEstimateStatus) {
    FeeEstimateStatus.FREE -> "무료"
    FeeEstimateStatus.ESTIMATED -> estimatedFee?.let { "약 ${it}원" } ?: "요금 미확인"
    FeeEstimateStatus.UNKNOWN -> "요금 미확인"
}
private fun ParkingPlace.distanceLabel() = distanceMeters?.let { if (it < 1000) "${it}m" else String.format("%.1fkm", it / 1000.0) } ?: "거리 미확인"
private fun ParkingType.label() = when (this) { ParkingType.PARKING_LOT -> "주차장"; ParkingType.ON_STREET_PARKING -> "노상주차장"; ParkingType.PUBLIC_REST_AREA -> "휴게소"; ParkingType.PICKUP_ZONE -> "승하차 구역"; ParkingType.TIME_LIMITED_ZONE -> "시간제 허용 구역" }
private fun OperatingStatus.label() = when (this) { OperatingStatus.OPEN -> "운영 중"; OperatingStatus.CLOSED -> "운영 종료"; OperatingStatus.UNKNOWN -> "미확인" }
private fun List<String>.allowedUseLabel() = if (isEmpty()) "미확인" else joinToString(", ") { when (it) { "PARK" -> "주차"; "STOP" -> "정차"; "PICKUP" -> "승하차"; else -> it } }
