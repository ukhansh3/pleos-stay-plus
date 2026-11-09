package com.pleosstay.app.realtime

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.tooling.preview.Preview
import com.pleosstay.app.documents.DocumentRef
import com.pleosstay.app.preview.PreviewFeature
import com.pleosstay.app.preview.PreviewScreen
import com.pleosstay.app.preview.previewSample

@Preview(name = "차량 파일 전송 확인", widthDp = 1120, heightDp = 805)
@Composable
internal fun WorkTransferPreview() {
    val profile = WorkExportProfile("preview", "preview-connection")
    val source = DocumentRef(profile.userScope, profile.connectionId, "preview-file", "preview-version",
        "업무 문서.docx", WORK_DOCX_MIME, "0".repeat(64), 8_192, 2)
    val exported = WorkExportReceipt("preview-export", profile, "업무 문서 수정본.docx", 10_240,
        "1".repeat(64), "", source)
    val picker = WorkTransferPicker(WorkTransferTarget(profile, "preview-2D85A1"), listOf(exported), exported)
    val sample = previewSample(PreviewScreen.REALTIME, LocalResources.current)
    PreviewFeature(PreviewScreen.REALTIME, sample.copy(files = checkNotNull(sample.files).copy(workPicker = picker)))
}
