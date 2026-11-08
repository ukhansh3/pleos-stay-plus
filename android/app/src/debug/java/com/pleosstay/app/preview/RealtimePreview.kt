package com.pleosstay.app.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/** Static, synthetic layout sample; no server, vehicle, permissions or file operations. */
@Preview(name = "05 파일 작업 · 예시", widthDp = 1682, heightDp = 1210, apiLevel = 34, locale = "ko-rKR", showBackground = true)
@Composable
private fun RealtimePreview() = PreviewFeature(PreviewScreen.REALTIME)
