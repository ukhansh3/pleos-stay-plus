package com.pleosstay.app.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/** Static, synthetic layout sample; no server, vehicle, permissions or file operations. */
@Preview(name = "08 업무 자료함 · 예시", widthDp = 1682, heightDp = 1210, apiLevel = 34, locale = "ko-rKR", showBackground = true)
@Composable
private fun WorkLibraryPreview() = PreviewFeature(PreviewScreen.WORK_LIBRARY)
