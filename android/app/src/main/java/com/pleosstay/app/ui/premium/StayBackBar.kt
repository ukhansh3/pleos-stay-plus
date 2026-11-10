package com.pleosstay.app.ui.premium

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Shared app/inspection back control; the host retains all navigation/save guards. */
@Composable
fun StayBackBar(onBack: () -> Unit, modifier: Modifier = Modifier,
                actions: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StayBackButton(onBack)
        Spacer(Modifier.weight(1f))
        actions()
    }
}

@Composable
fun StayBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val arrowColor = StayColors.Text
    Surface(onClick = onClick,
        modifier = modifier.size(64.dp).testTag("back").semantics { contentDescription = "뒤로 가기" },
        shape = RoundedCornerShape(18.dp),
        color = StayColors.Surface.copy(alpha = .68f),
        contentColor = StayColors.Text,
        border = BorderStroke(1.dp, StayColors.Border.copy(alpha = .72f)),
        shadowElevation = 2.dp) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(30.dp)) {
                val arrow = Path().apply {
                    moveTo(size.width * .82f, size.height * .5f)
                    lineTo(size.width * .20f, size.height * .5f)
                    moveTo(size.width * .47f, size.height * .22f)
                    lineTo(size.width * .20f, size.height * .5f)
                    lineTo(size.width * .47f, size.height * .78f)
                }
                drawPath(arrow, arrowColor,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}
