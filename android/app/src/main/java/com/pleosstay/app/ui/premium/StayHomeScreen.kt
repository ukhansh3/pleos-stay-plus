package com.pleosstay.app.ui.premium

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pleosstay.app.R
import com.pleosstay.app.waiting.ui.WaitingUiState
import com.pleosstay.app.pickup.ui.PickupUiState

@Composable
fun StayHomeScreen(waiting: WaitingUiState, pickup: PickupUiState, navigate: (StayRoute) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().testTag("stay_home_screen")) {
        val wide = maxWidth >= 1000.dp
        val contentWidth = maxWidth - 64.dp
        val largeType = LocalDensity.current.fontScale >= 1.3f
        val compact = !wide && maxHeight < 860.dp
        val statusBelowActivities = compact || (!wide && contentWidth < 720.dp)
        val available = (maxHeight - 104.dp).coerceAtLeast(660.dp)
        val heroHeight = if (wide) (available * .44f).coerceAtLeast(310.dp) else 300.dp
        val modesHeight = if (wide) (available * .34f).coerceAtLeast(if (largeType) 340.dp else 280.dp) else 300.dp
        val shortcutsHeight = if (wide) (available * .22f).coerceAtLeast(if (largeType) 250.dp else 190.dp) else 190.dp
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            HomeHero(waiting, heroHeight, wide, compact, statusBelowActivities, navigate)
            val activityCardHeight = if (wide) modesHeight else 216.dp
            ModeCards(wide, activityCardHeight, compact = !wide,
                onRest = { navigate(StayRoute.REST) },
                onStudy = { navigate(StayRoute.STUDY) },
                onWork = { navigate(StayRoute.WORK) }, home = true)
            if (statusBelowActivities) Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                HomeStatus(waiting, navigate)
            }
            val cards = listOf(
                Triple(StayRoute.PARKING, R.drawable.parking_visual, stringResource(R.string.stay_parking_hint)),
                Triple(StayRoute.PICKUP, R.drawable.pickup_visual, pickup.session?.let { "Pickup · ${it.status}" } ?: stringResource(R.string.stay_pickup_hint)),
                Triple(StayRoute.FILES, R.drawable.files_visual, stringResource(R.string.stay_files_hint)),
            )
            if (wide) Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                cards.forEach { (route, image, hint) -> Shortcut(route, image, hint, Modifier.weight(1f).heightIn(min = shortcutsHeight), navigate) }
            } else {
                val columns = homeColumns(contentWidth, largeType)
                cards.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { (route, image, hint) ->
                            Shortcut(route, image, hint, Modifier.weight(1f).heightIn(min = if (StayColors.isDark) shortcutsHeight else 0.dp),
                                navigate, adaptive = true)
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/** Decorative photography only. Text, status and actions remain real Compose components. */
@Composable
private fun HomeHero(waiting: WaitingUiState, height: Dp, wide: Boolean, compact: Boolean, statusBelowActivities: Boolean, navigate: (StayRoute) -> Unit) {
    val dark = StayColors.isDark
    Box(Modifier.fillMaxWidth().heightIn(min = if (compact) 222.dp else height)) {
        // Reserve the narrow photo band above status cards so the original car stays visible.
        Box(if (wide) Modifier.matchParentSize().fillMaxWidth()
            else Modifier.fillMaxWidth().height(if (compact) 222.dp else 360.dp).align(Alignment.TopCenter)) {
            Image(painterResource(R.drawable.hero_daylight_expanded), null,
                Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alignment = if (wide) Alignment.BottomEnd else Alignment.BottomCenter, alpha = if (dark) .82f else 1f)
            // Match the surrounding navy at every edge, with a gradual falloff
            // into the photograph. Foreground text and status cards stay unmasked.
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(
                0f to StayColors.Background,
                .04f to StayColors.Background.copy(alpha = if (dark) .92f else .80f),
                .12f to StayColors.Background.copy(alpha = if (dark) .82f else .76f),
                .24f to StayColors.Background.copy(alpha = if (dark) .78f else .72f),
                .46f to StayColors.Background.copy(alpha = if (dark) .72f else .17f),
                .56f to StayColors.Background.copy(alpha = if (dark) .12f else .02f),
                .70f to StayColors.Background.copy(alpha = if (dark) .10f else .02f),
                .82f to StayColors.Background.copy(alpha = if (dark) .38f else .19f),
                .94f to StayColors.Background.copy(alpha = .88f),
                1f to StayColors.Background)))
            Box(Modifier.matchParentSize().background(Brush.horizontalGradient(
                0f to StayColors.Background,
                .02f to StayColors.Background.copy(alpha = .92f),
                .05f to StayColors.Background.copy(alpha = .58f),
                .10f to StayColors.Background.copy(alpha = .18f),
                .18f to Color.Transparent,
                .82f to Color.Transparent,
                .90f to StayColors.Background.copy(alpha = .18f),
                .95f to StayColors.Background.copy(alpha = .58f),
                .98f to StayColors.Background.copy(alpha = .92f),
                1f to StayColors.Background)))
        }
        if (wide) Row(Modifier.fillMaxWidth().heightIn(min = height), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1.8f).padding(top = 48.dp, start = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)) { BrandHeading() }
            Column(Modifier.weight(1f).padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                HomeStatus(waiting, navigate)
            }
        } else Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(Modifier.padding(if (compact) 16.dp else 24.dp),
                verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 16.dp)) { BrandHeading(compact) }
            if (!compact && !statusBelowActivities) {
                Spacer(Modifier.height(200.dp))
                HomeStatus(waiting, navigate)
            }
        }
    }
}

@Composable
private fun BrandHeading(compact: Boolean = false) {
    Text("Pleos Stay+", fontSize = if (compact) 38.sp else 54.sp,
        lineHeight = if (compact) 46.sp else 62.sp, fontWeight = FontWeight.Bold, color = StayColors.Text)
    Text(stringResource(R.string.stay_tagline), fontSize = if (compact) 19.sp else 25.sp,
        lineHeight = if (compact) 27.sp else 36.sp, color = StayColors.Secondary)
}

@Composable
private fun HomeStatus(waiting: WaitingUiState, navigate: (StayRoute) -> Unit) {
    // Recovery must finish before a stored session is described as current.
    val active = waiting.active.takeIf { waiting.ready }
    GlassPanel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StayIcon("PARKING", tint = StayColors.Blue)
            Text(if (!waiting.ready) "대기 기록 확인 중" else if (active == null) stringResource(R.string.stay_no_waiting)
                else stringResource(R.string.stay_waiting_active), Modifier.weight(1f), fontSize = 23.sp,
                color = if (active == null) StayColors.Text else StayColors.Success)
            if (active != null) Text("%02d:%02d:%02d".format(
                ((waiting.now - active.startedAt).coerceAtLeast(0) / 1000) / 3600,
                ((waiting.now - active.startedAt).coerceAtLeast(0) / 1000) / 60 % 60,
                ((waiting.now - active.startedAt).coerceAtLeast(0) / 1000) % 60), color = StayColors.Text, fontSize = 23.sp)
        }
    }
    GlassPanel(Modifier.fillMaxWidth()) {
        Text(active?.place?.name ?: stringResource(R.string.stay_no_place), fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
        HorizontalDivider(color = StayColors.Border)
        Text(waiting.safety.message, color = if (waiting.safety.canUse) StayColors.Success else StayColors.Warning,
            style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { navigate(StayRoute.WAITING) }, modifier = Modifier.heightIn(min = 56.dp).testTag("home_waiting")) {
            Text(if (active == null) stringResource(R.string.stay_start_waiting) else stringResource(R.string.stay_manage_waiting), fontSize = 19.sp)
        }
    }
}

@Composable
fun ModeCards(wide: Boolean, rowHeight: Dp = 280.dp, onRest: (() -> Unit)? = null,
              onStudy: (() -> Unit)? = null, onWork: (() -> Unit)? = null, compact: Boolean = false, home: Boolean = false) {
    val modes = listOf(
        ModeVisual("REST", R.string.stay_rest, R.string.stay_rest_hint, R.drawable.rest_pillow, R.drawable.home_day_rest, onRest),
        ModeVisual("STUDY", R.string.stay_study, R.string.stay_study_hint, R.drawable.study_book, R.drawable.home_day_study, onStudy),
        ModeVisual("WORK", R.string.stay_work, R.string.stay_work_hint, R.drawable.work_laptop, R.drawable.home_day_work, onWork),
    )
    BoxWithConstraints {
        val largeType = LocalDensity.current.fontScale >= 1.3f
        val columns = if (home && !wide) homeColumns(maxWidth, largeType)
            else if (wide || maxWidth >= 680.dp || (compact && maxWidth >= 378.dp && !largeType)) 3 else 1
        val useCompact = compact && columns > 1
        val spacing = if (useCompact) 10.dp else 22.dp
        Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
            modes.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                    row.forEach { mode ->
                        val action = mode.action
                        val actionModifier = if (action != null)
                            Modifier.clickable(role = Role.Button, onClick = action).testTag("mode_${mode.icon}")
                        else Modifier.semantics { disabled() }
                        val minHeight = if (home && !wide && !StayColors.isDark) 0.dp else rowHeight
                        val cardModifier = Modifier.weight(1f).heightIn(min = minHeight)
                            .clip(RoundedCornerShape(24.dp)).then(actionModifier)
                        if (home) {
                            HomePhotoCard(mode.image, mode.dayImage, mode.icon, stringResource(mode.title),
                                stringResource(mode.hint), cardModifier, useCompact, action == null, adaptive = !wide)
                        } else {
                            PhotoPanel(mode.image, cardModifier, contentPadding = if (useCompact) 12.dp else 26.dp) {
                                CardHeading(mode.icon, stringResource(mode.title), stringResource(mode.hint),
                                    useCompact, action == null)
                            }
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

private fun homeColumns(width: Dp, largeType: Boolean): Int = when {
    largeType -> 1
    width >= 720.dp -> 3
    width >= 390.dp -> 2
    else -> 1
}

private data class ModeVisual(val icon: String, val title: Int, val hint: Int, val image: Int, val dayImage: Int,
                              val action: (() -> Unit)?)

@Composable
private fun Shortcut(route: StayRoute, image: Int, hint: String, modifier: Modifier, navigate: (StayRoute) -> Unit, adaptive: Boolean = false) {
    val dayImage = when (route) {
        StayRoute.PARKING -> R.drawable.home_day_parking
        StayRoute.PICKUP -> R.drawable.home_day_pickup
        else -> R.drawable.home_day_files
    }
    HomePhotoCard(image, dayImage, route.name,
        stringResource(if (route == StayRoute.PARKING) R.string.stay_parking_search else route.label), hint,
        modifier.clip(RoundedCornerShape(24.dp)).clickable(role = Role.Button) { navigate(route) }
            .testTag("home_${route.name}"), compact = adaptive, unavailable = false, shortcut = true, adaptive = adaptive)
}

@Composable
private fun HomePhotoCard(
    image: Int, dayImage: Int, icon: String, title: String, hint: String,
    modifier: Modifier, compact: Boolean, unavailable: Boolean, shortcut: Boolean = false, adaptive: Boolean = false,
) {
    val padding = if (compact) 10.dp else if (shortcut) 18.dp else 22.dp
    if (StayColors.isDark) {
        PhotoPanel(image, modifier, contentPadding = padding, fitImage = adaptive) {
            CardHeading(icon, title, hint, compact, unavailable)
        }
    } else {
        val shape = RoundedCornerShape(24.dp)
        Column(modifier.background(StayColors.Surface, shape).border(1.dp, StayColors.Border, shape)) {
            Box(Modifier.heightIn(min = if (adaptive && compact) 86.dp else 0.dp).padding(padding)) { CardHeading(icon, title, hint, compact, unavailable) }
            val photo = painterResource(dayImage)
            val photoModifier = if (adaptive) {
                Modifier.fillMaxWidth().aspectRatio(photo.intrinsicSize.width / photo.intrinsicSize.height)
            } else Modifier.fillMaxWidth().weight(1f).heightIn(min = if (compact) 44.dp else 65.dp)
            Image(photo, contentDescription = null, modifier = photoModifier,
                alignment = BiasAlignment(.5f, .5f), contentScale = if (adaptive) ContentScale.Fit else ContentScale.Crop)
        }
    }
}

@Composable
private fun CardHeading(icon: String, title: String, hint: String, compact: Boolean, unavailable: Boolean) {
    if (compact) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                StayIcon(icon, Modifier.size(24.dp))
                Text(title, fontSize = 21.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, color = StayColors.Text)
            }
            Text(hint, fontSize = 15.sp, lineHeight = 20.sp, color = StayColors.Secondary)
            if (unavailable) Text(stringResource(R.string.stay_preparing), color = StayColors.Warning)
        }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StayIcon(icon, Modifier.size(40.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontSize = 29.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold, color = StayColors.Text)
                Text(hint, fontSize = 20.sp, lineHeight = 26.sp, color = StayColors.Secondary)
                if (unavailable) Text(stringResource(R.string.stay_preparing), color = StayColors.Warning)
            }
        }
    }
}

@Composable
fun StayModeMenuScreen(onRest: (() -> Unit)? = null, onStudy: (() -> Unit)? = null, onWork: (() -> Unit)? = null) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(stringResource(R.string.stay_modes), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.stay_mode_notice), color = StayColors.Secondary)
        ModeCards(wide = false, rowHeight = 220.dp, onRest = onRest, onStudy = onStudy, onWork = onWork, compact = true)
    }
}

@Preview(name = "Day", widthDp = 1682, heightDp = 1210, uiMode = 16, showBackground = true)
@Preview(name = "Night", widthDp = 1682, heightDp = 1210, uiMode = 32, showBackground = true)
@Composable
private fun HomePreview() {
    com.pleosstay.app.ui.theme.PleosStayPlusTheme {
        StayAppShell({}) { _, navigate -> StayHomeScreen(WaitingUiState(), PickupUiState(), navigate) }
    }
}
