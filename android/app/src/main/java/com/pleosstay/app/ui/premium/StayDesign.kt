package com.pleosstay.app.ui.premium

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import com.pleosstay.app.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.pleosstay.app.waiting.ui.WaitingUiState
import com.pleosstay.app.pickup.ui.PickupUiState

internal data class StayPalette(
    val Background: Color,
    val Rail: Color,
    val Surface: Color,
    val Text: Color,
    val Secondary: Color,
    val Border: Color,
    val Blue: Color,
    val Accent: Color,
    val Warning: Color,
    val Success: Color,
    val Error: Color,
    val Action: Color,
    val OnAction: Color,
    val Selected: Color,
    val OnSelected: Color,
    val SelectionOutline: Color,
    val PanelStart: Color,
    val PanelEnd: Color,
    val GlassStart: Color,
    val GlassEnd: Color,
    val Ring: Color,
    val RingTrack: Color,
    val isDark: Boolean,
)

internal val StayDayPalette = StayPalette(
    Background = Color(0xFFE6ECF4),
    Rail = Color(0xFFC5D8EB),
    Surface = Color(0xFFEDF3FB),
    Text = Color(0xFF12253D),
    Secondary = Color(0xFF3B536E),
    Border = Color(0xFF738DA8),
    Blue = Color(0xFF1C4A77),
    Accent = Color(0xFF365F84),
    Warning = Color(0xFF754600),
    Success = Color(0xFF13604D),
    Error = Color(0xFFA12E3F),
    Action = Color(0xFF26527E),
    OnAction = Color(0xFFF8FBFF),
    Selected = Color(0xFFB5D0E8),
    OnSelected = Color(0xFF183A59),
    SelectionOutline = Color(0xFF476F93),
    PanelStart = Color(0xFFF3F7FC),
    PanelEnd = Color(0xFFE6EEF7),
    GlassStart = Color(0xFFEDF3FA),
    GlassEnd = Color(0xFFDFEBF7),
    Ring = Color(0xFF214B77),
    RingTrack = Color(0xFFB1C4D7),
    isDark = false,
)

internal val StayNightPalette = StayPalette(
    Background = Color(0xFF071322),
    Rail = Color(0xFF0B192C),
    Surface = Color(0xFF14253A),
    Text = Color(0xFFECF3FD),
    Secondary = Color(0xFFB8CCE3),
    Border = Color(0xFF3A526D),
    Blue = Color(0xFFA3CAFA),
    Accent = Color(0xFF9CBFE2),
    Warning = Color(0xFFFFCD86),
    Success = Color(0xFF7DDBB7),
    Error = Color(0xFFFFAAB5),
    Action = Color(0xFF284665),
    OnAction = Color(0xFFF1F6FC),
    Selected = Color(0xFF284665),
    OnSelected = Color(0xFFF1F6FC),
    SelectionOutline = Color(0xFF6485A7),
    PanelStart = Color(0xFF16283E),
    PanelEnd = Color(0xFF0E1E31),
    GlassStart = Color(0xFF182A41),
    GlassEnd = Color(0xFF112134),
    Ring = Color(0xFFC5D8F1),
    RingTrack = Color(0xFF3C536D),
    isDark = true,
)

internal val LocalStayPalette = staticCompositionLocalOf { StayNightPalette }

internal val StayColors: StayPalette
    @Composable get() = LocalStayPalette.current

@Composable
fun StayCard(modifier: Modifier=Modifier, brush: Brush=Brush.linearGradient(listOf(StayColors.Surface, StayColors.Surface)), content:@Composable ColumnScope.()->Unit) {
    Column(modifier.background(brush,RoundedCornerShape(22.dp))
        .border(1.dp,StayColors.Border,RoundedCornerShape(22.dp)).padding(24.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
}

/** Native, code-owned line icons. No additional dependency or licensed image. */
@Composable
fun StayIcon(kind:String,modifier:Modifier=Modifier,tint:Color=StayColors.Text) {
    Canvas(modifier.size(30.dp)) {
        val s=size.minDimension
        fun p(x:Float,y:Float)=Offset(x*s,y*s)
        fun line(x:Float,y:Float,xx:Float,yy:Float)=drawLine(tint,p(x,y),p(xx,yy),s*.065f,StrokeCap.Round)
        val stroke=Stroke(s*.065f,cap=StrokeCap.Round,join=StrokeJoin.Round)
        when(kind) {
            "SETTINGS" -> { drawCircle(tint,s*.29f,p(.5f,.5f),style=stroke); drawCircle(tint,s*.1f,p(.5f,.5f),style=stroke); for(angle in 0..7) { val r=angle*Math.PI/4; line((.5+Math.cos(r)*.3).toFloat(),(.5+Math.sin(r)*.3).toFloat(),(.5+Math.cos(r)*.45).toFloat(),(.5+Math.sin(r)*.45).toFloat()) } }
            "HOME" -> { drawPath(Path().apply { moveTo(s*.1f,s*.45f); lineTo(s*.5f,s*.12f); lineTo(s*.9f,s*.45f); moveTo(s*.22f,s*.38f); lineTo(s*.22f,s*.88f); lineTo(s*.78f,s*.88f); lineTo(s*.78f,s*.38f) },tint,style=stroke); line(.44f,.88f,.44f,.62f); line(.44f,.62f,.6f,.62f); line(.6f,.62f,.6f,.88f) }
            "PARKING" -> { drawCircle(tint,s*.43f,p(.5f,.5f),style=stroke); line(.4f,.75f,.4f,.27f); drawPath(Path().apply { moveTo(s*.4f,s*.27f); lineTo(s*.6f,s*.27f); quadraticTo(s*.8f,s*.46f,s*.6f,s*.52f); lineTo(s*.4f,s*.52f) },tint,style=stroke) }
            "PICKUP","MODES" -> for(x in listOf(.12f,.58f)) for(y in listOf(.12f,.58f)) drawRect(tint,p(x,y),Size(s*.28f,s*.28f),style=stroke)
            "WORK" -> { drawRect(tint,p(.22f,.14f),Size(s*.56f,s*.55f),style=stroke); line(.22f,.69f,.08f,.86f); line(.08f,.86f,.92f,.86f); line(.92f,.86f,.78f,.69f) }
            "FILES" -> drawPath(Path().apply { moveTo(s*.1f,s*.28f); lineTo(s*.1f,s*.83f); lineTo(s*.9f,s*.83f); lineTo(s*.9f,s*.3f); lineTo(s*.48f,s*.3f); lineTo(s*.38f,s*.17f); lineTo(s*.1f,s*.17f); close() },tint,style=stroke)
            "REST" -> { line(.12f,.25f,.12f,.88f); line(.88f,.5f,.88f,.88f); line(.12f,.72f,.88f,.72f); drawRect(tint,p(.12f,.4f),Size(s*.76f,s*.3f),style=stroke) }
            "STUDY" -> drawPath(Path().apply { moveTo(s*.5f,s*.25f); quadraticTo(s*.25f,s*.1f,s*.08f,s*.22f); lineTo(s*.08f,s*.82f); quadraticTo(s*.3f,s*.7f,s*.5f,s*.85f); quadraticTo(s*.7f,s*.7f,s*.92f,s*.82f); lineTo(s*.92f,s*.22f); quadraticTo(s*.7f,s*.1f,s*.5f,s*.25f); lineTo(s*.5f,s*.85f) },tint,style=stroke)
        }
    }
}
