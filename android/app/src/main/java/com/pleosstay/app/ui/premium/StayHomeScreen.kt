package com.pleosstay.app.ui.premium

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
        val largeType = LocalDensity.current.fontScale > 1.3f
        val available = (maxHeight - 104.dp).coerceAtLeast(660.dp)
        val heroHeight = if (wide) (available * .44f).coerceAtLeast(310.dp) else 300.dp
        val modesHeight = if (wide) (available * .34f).coerceAtLeast(if (largeType) 340.dp else 280.dp) else 300.dp
        val shortcutsHeight = if (wide) (available * .22f).coerceAtLeast(if (largeType) 250.dp else 190.dp) else 190.dp
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            HomeHero(waiting, heroHeight, wide, navigate)
            ModeCards(wide, modesHeight,
                onRest = { navigate(StayRoute.REST) },
                onStudy = { navigate(StayRoute.STUDY) },
                onWork = { navigate(StayRoute.WORK) }, onEntertainment = { navigate(StayRoute.ENTERTAINMENT) })
            val cards = listOf(
                Triple(StayRoute.PARKING, R.drawable.parking_visual, stringResource(R.string.stay_parking_hint)),
                Triple(StayRoute.PICKUP, R.drawable.pickup_visual, pickup.session?.let { "Pickup · ${it.status}" } ?: stringResource(R.string.stay_pickup_hint)),
                Triple(StayRoute.FILES, R.drawable.files_visual, stringResource(R.string.stay_files_hint)),
            )
            if (wide) Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                cards.forEach { (route, image, hint) -> Shortcut(route, image, hint, Modifier.weight(1f).heightIn(min = shortcutsHeight), navigate) }
            } else Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                cards.forEach { (route, image, hint) -> Shortcut(route, image, hint, Modifier.fillMaxWidth().heightIn(min = shortcutsHeight), navigate) }
            }
        }
    }
}

/** Decorative photography only. Text, status and actions remain real Compose components. */
@Composable
private fun HomeHero(waiting: WaitingUiState, height: Dp, wide: Boolean, navigate: (StayRoute) -> Unit) {
    Box(Modifier.fillMaxWidth().heightIn(min = height)) {
        // Reserve the narrow photo band above status cards so the original car stays visible.
        Box(if (wide) Modifier.matchParentSize().fillMaxWidth()
            else Modifier.fillMaxWidth().height(360.dp).align(Alignment.TopCenter)) {
            Image(painterResource(R.drawable.hero_daylight_expanded), null,
                Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alignment = if (wide) Alignment.BottomEnd else Alignment.BottomCenter, alpha = .82f)
            // Match the surrounding navy at every edge, with a gradual falloff
            // into the photograph. Foreground text and status cards stay unmasked.
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(
                0f to StayColors.Background,
                .04f to StayColors.Background.copy(alpha = .92f),
                .12f to StayColors.Background.copy(alpha = .82f),
                .24f to StayColors.Background.copy(alpha = .78f),
                .46f to StayColors.Background.copy(alpha = .72f),
                .56f to StayColors.Background.copy(alpha = .12f),
                .70f to StayColors.Background.copy(alpha = .10f),
                .82f to StayColors.Background.copy(alpha = .38f),
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
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { BrandHeading() }
            Spacer(Modifier.height(200.dp))
            HomeStatus(waiting, navigate)
        }
    }
}

@Composable
private fun BrandHeading() {
    Text("Pleos Stay+", fontSize = 54.sp, lineHeight = 62.sp, fontWeight = FontWeight.Bold, color = StayColors.Text)
    Text(stringResource(R.string.stay_tagline), fontSize = 25.sp, lineHeight = 36.sp, color = StayColors.Secondary)
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
              onStudy: (() -> Unit)? = null, onWork: (() -> Unit)? = null, onEntertainment: (() -> Unit)? = null) {
    val modes = listOf(
        ModeVisual("REST", R.string.stay_rest, R.string.stay_rest_hint, R.drawable.rest_pillow, onRest),
        ModeVisual("STUDY", R.string.stay_study, R.string.stay_study_hint, R.drawable.study_book, onStudy),
        ModeVisual("WORK", R.string.stay_work, R.string.stay_work_hint, R.drawable.work_laptop, onWork),
        ModeVisual("ENTERTAINMENT", R.string.stay_entertainment, R.string.stay_entertainment_hint, R.drawable.entertainment_visual, onEntertainment),
    )
    BoxWithConstraints {
        val columns = if (maxWidth < 520.dp) 1 else if (wide) 4 else 2
        Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
            modes.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    row.forEach { mode ->
                        val action = mode.action
                        val actionModifier = if (action != null)
                            Modifier.clickable(role = Role.Button, onClick = action).testTag("mode_${mode.icon}")
                        else Modifier.semantics { disabled() }
                        PhotoPanel(mode.image, Modifier.weight(1f).heightIn(min = rowHeight).then(actionModifier)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                StayIcon(mode.icon, Modifier.size(46.dp))
                                Spacer(Modifier.weight(1f))
                                if (action == null) Text(stringResource(R.string.stay_preparing),
                                    fontSize = 14.sp, color = StayColors.Warning)
                            }
                            Spacer(Modifier.height(16.dp))
                            Text(stringResource(mode.title), fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.weight(1f))
                            Text(stringResource(mode.hint), fontSize = 21.sp, lineHeight = 30.sp, color = StayColors.Secondary)

                        }
                    }
                }
            }
        }
    }
}

private data class ModeVisual(val icon: String, val title: Int, val hint: Int, val image: Int,
                              val action: (() -> Unit)?)

@Composable
private fun Shortcut(route: StayRoute, image: Int, hint: String, modifier: Modifier, navigate: (StayRoute) -> Unit) {
    PhotoPanel(image, modifier.clip(RoundedCornerShape(24.dp)).clickable(role = Role.Button) { navigate(route) }.testTag("home_${route.name}")) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StayIcon(route.name, Modifier.size(40.dp))
            Text(if (route == StayRoute.PARKING) stringResource(R.string.stay_parking_search) else stringResource(route.label),
                Modifier.weight(1f), fontSize = 27.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.weight(1f))
        Text(hint, Modifier.fillMaxWidth(.76f), fontSize = 20.sp, lineHeight = 30.sp, color = StayColors.Secondary)
    }
}

@Composable
fun StayModeMenuScreen(onRest: (() -> Unit)? = null, onStudy: (() -> Unit)? = null, onWork: (() -> Unit)? = null, onEntertainment: (() -> Unit)? = null) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(stringResource(R.string.stay_modes), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.stay_mode_notice), color = StayColors.Secondary)
        ModeCards(wide = false, onRest = onRest, onStudy = onStudy, onWork = onWork, onEntertainment = onEntertainment)
    }
}

@Preview(widthDp = 1682, heightDp = 1210, showBackground = true)
@Composable
private fun HomePreview() {
    com.pleosstay.app.ui.theme.PleosStayPlusTheme {
        StayAppShell({}) { _, navigate -> StayHomeScreen(WaitingUiState(), PickupUiState(), navigate) }
    }
}
