package com.pleosstay.app.ui.premium

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun AmbientCurve(modifier: Modifier=Modifier) {
    Canvas(modifier) {
        val w=size.width; val h=size.height
        val curve=Path().apply {
            moveTo(0f,0f); lineTo(w*.62f,0f)
            cubicTo(w*.48f,h*.64f,w*.20f,h*.93f,0f,h*.98f)
            close()
        }
        drawPath(curve,Brush.radialGradient(
            listOf(Color(0xFF486786),Color(0xFF263D58),Color(0xFF17263D),Color(0xFF0B1426)),
            center=Offset(w*.60f,h*.12f),radius=w*.60f))
        val edge=Path().apply { moveTo(w*.62f,0f); cubicTo(w*.48f,h*.64f,w*.20f,h*.93f,0f,h*.98f) }
        drawPath(edge,StayColors.Blue.copy(alpha=.08f),style=Stroke(20.dp.toPx()))
        drawPath(edge,StayColors.Blue.copy(alpha=.16f),style=Stroke(7.dp.toPx()))
        drawPath(edge,StayColors.Blue.copy(alpha=.3f),style=Stroke(1.dp.toPx()))
    }
}

fun Modifier.stayGlow(highlighted: Boolean=false): Modifier = drawBehind {
    val radius=CornerRadius(24.dp.toPx())
    val color=StayColors.Blue
    for((width,alpha) in listOf(14f to .025f,7f to .045f,3f to .12f)) {
        drawRoundRect(color.copy(alpha=alpha),cornerRadius=radius,style=Stroke(width.dp.toPx()))
    }
}

@Composable
fun GlassPanel(modifier: Modifier=Modifier,content:@Composable ColumnScope.()->Unit) {
    val shape=RoundedCornerShape(22.dp)
    Column(modifier.stayGlow().clip(shape)
        .background(Brush.linearGradient(listOf(Color(0xFF20304B).copy(alpha=.94f),Color(0xFF101A2D).copy(alpha=.9f))))
        .border(1.dp,Brush.linearGradient(listOf(StayColors.Blue.copy(alpha=.6f),StayColors.Border)),shape).padding(24.dp),
        verticalArrangement=Arrangement.spacedBy(16.dp)) {
        androidx.compose.runtime.CompositionLocalProvider(LocalContentColor provides StayColors.Text) { content() }
    }
}

@Composable
fun PhotoPanel(image:Int,modifier:Modifier=Modifier,highlighted:Boolean=false,contentPadding:Dp=26.dp,content:@Composable ColumnScope.()->Unit) {
    val shape=RoundedCornerShape(24.dp)
    Box(modifier.stayGlow(highlighted).clip(shape).background(StayColors.Surface)
        .border(if(highlighted) 1.5.dp else 1.dp,
            Brush.linearGradient(listOf(StayColors.Blue.copy(alpha=.4f),StayColors.Border,StayColors.Border.copy(alpha=.5f))),shape)) {
        Image(painterResource(image),null,Modifier.matchParentSize(),alignment=Alignment.CenterEnd,contentScale=ContentScale.Crop)
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(StayColors.Background.copy(alpha=.58f),StayColors.Background.copy(alpha=.10f)))))
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent,StayColors.Background.copy(alpha=.16f),StayColors.Background.copy(alpha=.72f)))))
        Column(Modifier.matchParentSize().padding(contentPadding)) {
            androidx.compose.runtime.CompositionLocalProvider(LocalContentColor provides StayColors.Text) { content() }
        }
    }
}
