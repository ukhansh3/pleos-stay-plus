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

object StayColors {
    val Background = Color(0xFF081323)
    val Rail = Color(0xFF0A172B)
    val Surface = Color(0xFF15263F)
    val Text = Color(0xFFF5F7FC)
    val Secondary = Color(0xFFABBAD1)
    val Border = Color(0xFF304463)
    val Blue = Color(0xFF75A7FF)
    val Purple = Color(0xFF7963D8)
    val Warning = Color(0xFFFFCB76)
    val Success = Color(0xFF61DDB0)
    val Action = Color(0xFF315B88)
    val Hero = Brush.linearGradient(listOf(Color(0xFF172D46), Color(0xFF172337), Background))
    val Active = Brush.horizontalGradient(listOf(Purple, Color(0xFF34326C)))
}

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
            "ENTERTAINMENT" -> { drawCircle(tint,s*.43f,p(.5f,.5f),style=stroke); drawPath(Path().apply { moveTo(s*.42f,s*.3f); lineTo(s*.7f,s*.5f); lineTo(s*.42f,s*.7f); close() },tint,style=stroke) }
        }
    }
}
