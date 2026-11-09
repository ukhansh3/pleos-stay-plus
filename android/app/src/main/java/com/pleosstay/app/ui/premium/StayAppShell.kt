package com.pleosstay.app.ui.premium

import android.content.Context
import android.os.Build
import android.util.Log
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pleosstay.app.BuildConfig
import com.pleosstay.app.R

@Composable
fun StayAppShell(
    onEnter:(StayRoute)->Unit,
    requestedRoute:StayRoute?=null,
    onRequestedRouteHandled:()->Unit={},
    navigationGuard:(StayRoute,StayRoute,()->Unit)->Unit={_,_,proceed->proceed()},
    waitingShortcut: String? = null,
    restActive: Boolean = false,
    waitingDeadlineKey: String? = null,
    content:@Composable (StayRoute,(StayRoute)->Unit)->Unit,
) {
    var savedStack by rememberSaveable { mutableStateOf(listOf(StayRoute.HOME.name)) }
    val stack = submissionRouteStack(savedStack)
    val route=StayRoute.valueOf(stack.last())
    SideEffect { if (savedStack != stack) savedStack = stack }
    val backDispatcher=LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val navigate:(StayRoute)->Unit={ next -> if(next!=route) navigationGuard(route,next) { savedStack=pushRoute(stack,next);onEnter(next) } }
    LaunchedEffect(requestedRoute) {
        requestedRoute?.let { navigate(it);onRequestedRouteHandled() }
    }
    BackHandler(stack.size>1) {
        val target=StayRoute.valueOf(popRoute(stack).last())
        navigationGuard(route,target) { savedStack=popRoute(stack);onEnter(target) }
    }
    val waitingNotice = remember { SnackbarHostState() }
    LaunchedEffect(waitingDeadlineKey) {
        waitingNotice.currentSnackbarData?.dismiss()
        if (waitingDeadlineKey != null) waitingNotice.showSnackbar("대기 예정 시간이 지났습니다.")
    }
    val density=LocalDensity.current
    val context=LocalContext.current
    val topInset=WindowInsets.safeDrawing.getTop(density)
    val bottomInset=WindowInsets.safeDrawing.getBottom(density)
    BoxWithConstraints(Modifier.fillMaxSize().background(StayColors.Background).safeDrawingPadding()
        .onSizeChanged { size ->
            if(BuildConfig.DEBUG) {
                val metrics=if(Build.VERSION.SDK_INT>=30) (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).currentWindowMetrics.bounds.toShortString() else "API<30"
                Log.d("StayViewport","window=$metrics content=${size.width}x${size.height} density=${density.density} fontScale=${density.fontScale} safeInsets=$topInset,$bottomInset")
            }
        }) {
        val railWidth=if(maxWidth>=1300.dp) 180.dp else 112.dp
        Row(Modifier.fillMaxSize()) {
            StayNavigationRail(route.group,navigate,railWidth)
            Column(Modifier.weight(1f).fillMaxHeight()) {
                if(route!=StayRoute.HOME) StayBackBar(
                    onBack = { if(backDispatcher!=null) backDispatcher.onBackPressed() else savedStack=popRoute(stack) }) {
                    if(route!=StayRoute.WAITING && waitingShortcut!=null) TextButton(onClick={navigate(StayRoute.WAITING)}) {
                        Text(waitingShortcut, color = StayColors.Blue)
                    } else if(route==StayRoute.PARKING) TextButton(onClick={navigate(StayRoute.WAITING)}) { Text(stringResource(R.string.stay_waiting)) }
                    if(route!=StayRoute.REST && restActive) TextButton(onClick={navigate(StayRoute.REST)}) {
                        Text("진행 중인 휴식", color = StayColors.Blue)
                    }
                    if(route==StayRoute.WAITING) TextButton(onClick={navigate(StayRoute.PARKING)}) { Text(stringResource(R.string.stay_parking_search)) }
                }
                Box(Modifier.weight(1f)) {
                    content(route,navigate)
                    SnackbarHost(waitingNotice, Modifier.align(Alignment.BottomCenter).padding(16.dp))
                }
            }
        }
    }
}

@Composable
fun StayNavigationRail(selected:NavigationGroup,navigate:(StayRoute)->Unit,width:androidx.compose.ui.unit.Dp=180.dp) {
    BoxWithConstraints(Modifier.width(width).fillMaxHeight().background(StayColors.Rail)) {
        val expanded=width>=160.dp
        val itemHeight=((maxHeight-200.dp)/6).coerceIn(72.dp,116.dp)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=14.dp,vertical=36.dp),
            horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Image(painterResource(R.drawable.stay_brand_logo), "Stay+ 로고",
                Modifier.size(if(expanded) 104.dp else 76.dp), contentScale=ContentScale.Fit)
            Spacer(Modifier.height(8.dp))
            listOf(StayRoute.HOME,StayRoute.PARKING,StayRoute.PICKUP,StayRoute.MODES,StayRoute.FILES,StayRoute.SETTINGS).forEach { route ->
                val active=route.group==selected
                val itemModifier=Modifier.fillMaxWidth().heightIn(min=itemHeight).clip(RoundedCornerShape(18.dp))
                    .then(if(active) Modifier.background(StayColors.Active).border(1.dp,StayColors.Purple,RoundedCornerShape(18.dp)) else Modifier)
                    .selectable(active,role=Role.Tab,onClick={navigate(route)}).testTag("nav_${route.name}")
                if(expanded) Row(itemModifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                    StayIcon(route.name,Modifier.size(32.dp),if(active) StayColors.Text else StayColors.Secondary)
                    Text(stringResource(route.label),fontSize=18.sp,color=if(active) StayColors.Text else StayColors.Secondary)
                } else Column(itemModifier.padding(vertical=12.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    StayIcon(route.name,tint=if(active) StayColors.Text else StayColors.Secondary)
                    Text(stringResource(route.label),style=MaterialTheme.typography.labelMedium,color=if(active) StayColors.Text else StayColors.Secondary)
                }
            }
        }
    }
}

@Composable
fun StaySettingsScreen() {
    Column(Modifier.fillMaxSize().padding(32.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
        Text(stringResource(R.string.stay_settings),style=MaterialTheme.typography.headlineLarge)
        Text("설정할 항목이 없습니다.", color = StayColors.Secondary)
    }
}
