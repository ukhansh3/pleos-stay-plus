package com.pleosstay.app.ui.premium

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalInspectionMode
import com.pleosstay.app.parking.ui.ParkingUiState
import com.pleosstay.app.pickup.ui.PickupUiState
import com.pleosstay.app.realtime.FileTransferState
import com.pleosstay.app.rest.RestState
import com.pleosstay.app.study.StudyUiState
import com.pleosstay.app.waiting.safety.WaitingSafety
import com.pleosstay.app.waiting.ui.WaitingUiState
import com.pleosstay.app.work.WorkState
import com.pleosstay.app.work.docx.DocxEditorState

/** Layout inspection only. Samples/providers live in src/debug, never in release. */
internal data class FeatureInspectionState(
    val parking: ParkingUiState? = null,
    val waiting: WaitingUiState? = null,
    val pickup: PickupUiState? = null,
    val files: FileTransferState? = null,
    val rest: RestState? = null,
    val safety: WaitingSafety? = null,
    val study: StudyUiState? = null,
    val work: WorkState? = null,
    val docx: DocxEditorState? = null,
)
internal val LocalFeatureInspection = staticCompositionLocalOf<FeatureInspectionState?> { null }

/** An accidental provider in a running app cannot replace its real safety/auth/UI state. */
@Composable
internal fun featureInspection(): FeatureInspectionState? =
    if (LocalInspectionMode.current) LocalFeatureInspection.current else null
