package com.pleosstay.app.realtime

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pleosstay.app.ui.premium.PremiumAction
import com.pleosstay.app.ui.premium.StayColors

@Composable
internal fun WorkTransferPickerDialog(
    picker: WorkTransferPicker,
    select: (String) -> Unit,
    confirm: () -> Unit,
    dismiss: () -> Unit,
) {
    val selected = picker.selected
    AlertDialog(
        onDismissRequest = dismiss,
        containerColor = StayColors.Surface,
        titleContentColor = StayColors.Text,
        textContentColor = StayColors.Secondary,
        title = { Text(if (selected == null) "차량 파일 보내기" else "전송 확인") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("연결된 PC · ${picker.target.pcId.takeLast(6)}")
                if (selected != null) {
                    Text(selected.name, color = StayColors.Text, style = MaterialTheme.typography.titleMedium)
                    Text("${(selected.size + 1023) / 1024} KB")
                    val source = selected.source
                    Text(if (source == null) "새 파일" else "${source.name} · v${source.versionNo}의 수정본")
                } else if (picker.exports.isEmpty()) {
                    Text("보낼 파일이 없습니다. Work에서 DOCX를 내보내 주세요.")
                } else {
                    Column(
                        Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        picker.exports.forEach { export ->
                            PremiumAction("${export.name} · ${(export.size + 1023) / 1024} KB",
                                { select(export.id) }, Modifier.fillMaxWidth(), secondary = true)
                        }
                    }
                }
            }
        },
        confirmButton = { if (selected != null) TextButton(onClick = confirm) { Text("전송") } },
        dismissButton = { TextButton(onClick = dismiss) { Text(if (selected == null) "닫기" else "취소") } },
    )
}
