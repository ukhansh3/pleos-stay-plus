package com.pleosstay.app.preview

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.Lifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pleosstay.app.R
import com.pleosstay.app.parking.ui.ParkingScreen
import com.pleosstay.app.pickup.ui.PickupScreen
import com.pleosstay.app.realtime.FileTransferScreen
import com.pleosstay.app.rest.RestScreen
import com.pleosstay.app.study.StudyScreen
import com.pleosstay.app.waiting.ui.WaitingScreen
import com.pleosstay.app.work.WorkScreen
import com.pleosstay.app.work.docx.DocxEditorPanel
import com.pleosstay.app.ui.premium.*
import com.pleosstay.app.ui.theme.PleosStayPlusTheme

/** Design/Split rendering only. Do not deploy with Run Preview or use as an app route. */
@Composable
internal fun PreviewFeature(screen: PreviewScreen) {
    if (!LocalInspectionMode.current) {
        Text("Android Studio의 Design/Split 미리보기에서 열어 주세요.")
        return
    }
    val models = remember { PreviewModels() }
    val owners = remember { PreviewOwners() }
    val resources = LocalContext.current.resources
    val sample = remember(screen) { previewSample(screen, resources) }
    DisposableEffect(models, owners) { onDispose { models.close(); owners.lifecycle.currentState = Lifecycle.State.DESTROYED } }
    val route = when (screen) {
        PreviewScreen.PARKING -> StayRoute.PARKING
        PreviewScreen.WAITING -> StayRoute.WAITING
        PreviewScreen.PICKUP -> StayRoute.PICKUP
        PreviewScreen.REALTIME -> StayRoute.FILES
        PreviewScreen.REST -> StayRoute.REST
        PreviewScreen.STUDY -> StayRoute.STUDY
        PreviewScreen.WORK_LIBRARY, PreviewScreen.WORK_EDITOR -> StayRoute.WORK
    }
    CompositionLocalProvider(LocalFeatureInspection provides sample,
        LocalOnBackPressedDispatcherOwner provides owners, LocalActivityResultRegistryOwner provides owners) {
        PleosStayPlusTheme {
            // Reuse the actual rail and screens. Only the host framing is inspection-specific.
            Row(Modifier.fillMaxSize().background(StayColors.Background)) {
                StayNavigationRail(route.group, {}, 180.dp)
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                        TextButton({}, Modifier.heightIn(min = 60.dp)) { Text(stringResource(R.string.stay_back), fontSize = 19.sp) }
                        Text(stringResource(route.label), color = StayColors.Secondary, modifier = Modifier.padding(top = 20.dp))
                    }
                    Box(Modifier.weight(1f)) {
                        when (screen) {
                            PreviewScreen.PARKING -> ParkingScreen(models.parking, {})
                            PreviewScreen.WAITING -> WaitingScreen(models.waiting, null, onRest = {}, onStudy = {}, onWork = {})
                            PreviewScreen.PICKUP -> PickupScreen(models.pickup, {})
                            PreviewScreen.REALTIME -> FileTransferScreen(models.files)
                            PreviewScreen.REST -> RestScreen(models.rest, {}, {})
                            PreviewScreen.STUDY -> StudyScreen(models.study, {})
                            PreviewScreen.WORK_LIBRARY -> WorkScreen(models.work, {}, {}, {})
                            PreviewScreen.WORK_EDITOR -> DocxEditorPanel(models.work.docx, { _, _ -> }, {})
                        }
                    }
                }
            }
        }
    }
}
private class PreviewOwners : OnBackPressedDispatcherOwner, ActivityResultRegistryOwner {
    override val lifecycle = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
    override val onBackPressedDispatcher = OnBackPressedDispatcher()
    override val activityResultRegistry = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) = Unit
    }
}
