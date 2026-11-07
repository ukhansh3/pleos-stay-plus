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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        // Detail responses omit distance. Preserve the real search-origin distance for the same place.
        detail.copy(distanceMeters = detail.distanceMeters ?: state.places.firstOrNull { it.id == detail.id }?.distanceMeters)
    }
    val resultsScroll = rememberLazyListState()
    val detailScroll = rememberLazyListState()
    LaunchedEffect(detailId) { if(detailId != null) detailScroll.scrollToItem(0) }
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 28.dp), state = if(detailId == null) resultsScroll else detailScroll, verticalArrangement = Arrangement.spacedBy(22.dp),
        contentPadding = PaddingValues(bottom = 32.dp)) {
        item { FeatureHeader(if (detailId == null) "주차·대기 장소 검색" else selected?.name ?: "장소 상세",
            "잠깐의 대기에도, 더 가치 있는 선택을") }
        if (detailId != null) {
            item { TextButton(onClick = { detailId = null }) { Text("‹ 검색 결과로 돌아가기", fontSize = 21.sp) } }
            if (selected != null) {
                item { ParkingDetail(selected, state.navigationStatus, viewModel::navigate, onWaiting) }
            } else if (!state.loading && state.error == null) {
                item { NoticePanel("상세정보 없음", "해당 장소의 상세정보를 다시 조회해 주세요.") }
            }
        } else {
            item {
                ResponsivePanels(leftWeight = .8f, left = {
                    GlassPanel(Modifier.fillMaxWidth()) {
                        Text("검색 조건", fontSize = 27.sp, fontWeight = FontWeight.Bold)
                        Text(state.locationMessage, color = StayColors.Secondary, fontSize = 19.sp)
                        state.lastLocationLabel?.let { Text("최근 좌표 · $it", color = StayColors.Secondary) }
                        PremiumAction("내 주변 검색", onNearbySearch, Modifier.fillMaxWidth(), !state.loading)
                        if (state.locationState == LocationUiState.FAILED) TextButton(onClick = viewModel::showLocationAccessInformation) { Text("위치 접근 차단 안내") }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(state.radiusMeters, { viewModel.updateFilters(radius = it) }, label = { Text("반경(m)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(state.plannedMinutes, { viewModel.updateFilters(minutes = it) }, label = { Text("예정시간(분)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ParkingPurpose.entries.forEach { purpose ->
                                FilterChip(state.purpose == purpose, { viewModel.updateFilters(purpose = purpose) }, label = { Text(purpose.label()) })
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(state.freeOnly, { viewModel.updateFilters(freeOnly = it) }); Text("무료만 보기", fontSize = 19.sp)
                        }
                        TextButton(onClick = viewModel::toggleDeveloperCoordinates) { Text(if(state.showDeveloperCoordinates) "테스트 좌표 닫기" else "개발자용 테스트 좌표") }
                        if (state.showDeveloperCoordinates) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(state.latitude, { viewModel.updateCoordinates(latitude = it) }, label = { Text("위도") }, modifier = Modifier.weight(1f), singleLine = true)
                                OutlinedTextField(state.longitude, { viewModel.updateCoordinates(longitude = it) }, label = { Text("경도") }, modifier = Modifier.weight(1f), singleLine = true)
                            }
                            PremiumAction("입력 좌표 검색", viewModel::searchManual, enabled = !state.loading, secondary = true)
                        }
                    }
                }, right = {
                    PhotoPanel(R.drawable.parking_visual, Modifier.fillMaxWidth().height(390.dp)) {
                        Spacer(Modifier.weight(1f))
                        Text("머무를 장소를, 신중하게", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        Text("위치 기준으로 검색한 실제 장소를 아래 목록에서 확인하세요.\n지도·목적지 이름 검색은 현재 지원하지 않습니다.", fontSize = 20.sp, lineHeight = 30.sp)
                    }
                })
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("검색 결과 · ${state.total}곳", fontSize = 29.sp, fontWeight = FontWeight.Bold)
                    Text("데이터 · ${state.dataFreshness}", color = StayColors.Secondary, fontSize = 18.sp)
                }
            }
            if (state.places.isEmpty() && !state.loading && state.error == null) item {
                NoticePanel("표시할 장소가 없습니다", "내 주변 검색을 실행하거나 검색 조건을 바꿔 주세요. 실시간 빈자리 정보는 제공하지 않습니다.")
            }
            items(state.places, key = { it.id }) { place ->
                ParkingRow(place) { detailId = place.id; viewModel.select(place) }
            }
        }
        if (state.loading) item { Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { CircularProgressIndicator(); Text("장소 정보를 확인하고 있습니다", fontSize = 20.sp) } }
        state.error?.let { message -> item { NoticePanel("조회하지 못했습니다", message, true) } }
        item { Text("현장 표지와 운영 규칙이 공공데이터보다 우선합니다.", color = StayColors.Secondary, fontSize = 18.sp) }
    }
}

@Composable
private fun ParkingRow(place: ParkingPlace, onClick: () -> Unit) {
    GlassPanel(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(place.name, Modifier.weight(1f), fontSize = 27.sp, fontWeight = FontWeight.SemiBold)
            Text("상세 보기 ›", color = StayColors.Blue, fontSize = 20.sp)
        }
        Text(listOf(place.type.label(), place.distanceLabel(), place.feeLabel()).joinToString(" · "), fontSize = 22.sp, color = StayColors.Blue)
        Text(place.address ?: "주소 정보 없음", color = StayColors.Secondary, fontSize = 19.sp)
        Text("운영 · ${place.openingHoursText ?: "미확인"} · ${place.operatingStatus.label()}", fontSize = 19.sp)
        Text("조건 · ${place.eligibilityReason ?: "미확인"}", fontSize = 19.sp, color = StayColors.Secondary)
        Text("출처 · ${place.source} · 기준일 ${place.sourceUpdatedAt ?: "미확인"}", color = StayColors.Secondary)
    }
}

@Composable
internal fun ParkingDetail(place: ParkingPlace, navigationStatus: NavigationStatus, onNavigate: () -> Unit, onWaiting: () -> Unit) {
    ResponsivePanels(leftWeight = 1.2f, left = {
        GlassPanel(Modifier.fillMaxWidth()) {
            Text("장소 정보", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            InfoLine("종류", place.type.label())
            InfoLine("주소", place.address ?: "미확인")
            InfoLine("검색 기준 거리", place.distanceLabel())
            InfoLine("운영시간", "${place.openingHoursText ?: "미확인"} · ${place.operatingStatus.label()}")
            InfoLine("허용 용도", place.allowedUse.allowedUseLabel())
            InfoLine("이용 조건", place.eligibilityReason ?: "미확인")
            place.restrictions?.let { InfoLine("추가 조건", it) }
            place.capacity?.let { InfoLine("주차 구획", "${it}면 · 빈자리 수 아님") }
            InfoLine("관리·문의", "${place.operatorName ?: "미확인"} / ${place.phoneNumber ?: "연락처 없음"}")
            Text("출처 · ${place.source}\n기준일 ${place.sourceUpdatedAt ?: "미확인"}", color = StayColors.Secondary, fontSize = 18.sp)
        }
    }, right = {
        Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
            PhotoPanel(R.drawable.parking_visual, Modifier.fillMaxWidth().height(230.dp)) {
                Spacer(Modifier.weight(1f)); Text("선택한 장소로 길 안내", fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text("지도 미리보기는 제공하지 않습니다.", color = StayColors.Secondary)
            }
            GlassPanel(Modifier.fillMaxWidth()) {
                Text("예상 이용요금", color = StayColors.Secondary, fontSize = 21.sp)
                Text(place.feeLabel(), fontSize = 37.sp, fontWeight = FontWeight.Bold, color = StayColors.Blue)
                place.feePolicy.rawText?.let { Text(it, fontSize = 19.sp) }
                Text("실시간 빈자리·실제 주행거리·도착 예상시간은 제공하지 않습니다.", color = StayColors.Secondary, fontSize = 18.sp)
                PremiumAction("순정 내비게이션으로 길 안내", onNavigate, Modifier.fillMaxWidth(),
                    navigationStatus is NavigationStatus.Ready || navigationStatus is NavigationStatus.CallbackReceived)
                Text(navigationStatus.label(), color = StayColors.Secondary, fontSize = 18.sp)
                PremiumAction("이 장소에서 대기 관리", onWaiting, Modifier.fillMaxWidth(), secondary = true)
            }
        }
    })
}
private fun ParkingPurpose.label() = when(this) { ParkingPurpose.PICKUP -> "픽업"; ParkingPurpose.SHORT_WAIT -> "단기"; ParkingPurpose.LONG_STAY -> "장기" }
private fun ParkingPlace.feeLabel(): String = when(feeEstimateStatus) {
    FeeEstimateStatus.FREE -> "무료"
    FeeEstimateStatus.ESTIMATED -> estimatedFee?.let { "약 ${it}원" } ?: "요금 미확인"
    FeeEstimateStatus.UNKNOWN -> "요금 미확인"
}
private fun ParkingPlace.distanceLabel(): String = distanceMeters?.let { if(it < 1000) "${it}m" else String.format("%.1fkm", it/1000.0) } ?: "거리 미확인"
private fun ParkingType.label() = when(this) { ParkingType.PARKING_LOT -> "주차장"; ParkingType.ON_STREET_PARKING -> "정식 노상주차장"; ParkingType.PUBLIC_REST_AREA -> "공식 휴게소"; ParkingType.PICKUP_ZONE -> "승하차 구역"; ParkingType.TIME_LIMITED_ZONE -> "시간제 허용 구역" }
private fun OperatingStatus.label() = when(this) { OperatingStatus.OPEN -> "운영 중"; OperatingStatus.CLOSED -> "운영 종료"; OperatingStatus.UNKNOWN -> "상태 미확인" }
private fun List<String>.allowedUseLabel() = if(isEmpty()) "미확인" else joinToString(", ") { when(it) { "PARK" -> "주차"; "STOP" -> "정차"; "PICKUP" -> "승하차"; else -> it } }
private fun NavigationStatus.label() = when(this) {
    NavigationStatus.Initializing -> "NaviHelper 초기화 중"
    NavigationStatus.Ready -> "NaviHelper 준비됨"
    is NavigationStatus.Requesting -> "$destination 길 안내 요청 중"
    is NavigationStatus.CallbackReceived -> "$destination: NaviHelper $callback 콜백 수신"
    is NavigationStatus.Failed -> message
}
