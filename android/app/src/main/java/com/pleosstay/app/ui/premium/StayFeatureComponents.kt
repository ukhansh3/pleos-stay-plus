package com.pleosstay.app.ui.premium

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pleosstay.app.R

@Composable
fun FeatureHeader(title:String,subtitle:String,modifier:Modifier=Modifier) {
    Box(modifier.fillMaxWidth().heightIn(min=230.dp)) {
        AmbientCurve(Modifier.matchParentSize())
        Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text("Pleos Stay+",fontSize=34.sp,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.secondary)
            Text(title,fontSize=38.sp,lineHeight=46.sp,fontWeight=FontWeight.Bold)
            Text(subtitle,fontSize=20.sp,lineHeight=29.sp,color=StayColors.Secondary)
        }
    }
}

@Composable
fun PremiumAction(label:String,onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,secondary:Boolean=false) {
    Button(onClick=onClick,enabled=enabled,modifier=modifier.heightIn(min=64.dp),
        shape=RoundedCornerShape(18.dp),
        colors=ButtonDefaults.buttonColors(containerColor=if(secondary) StayColors.Surface else StayColors.Action,
            contentColor=StayColors.Text,disabledContainerColor=StayColors.Surface,disabledContentColor=StayColors.Secondary)) {
        Text(label,fontSize=20.sp,lineHeight=28.sp,fontWeight=FontWeight.SemiBold)
    }
}

@Composable
fun InfoLine(label:String,value:String) {
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(20.dp)) {
            Text(label,Modifier.weight(.32f),color=StayColors.Secondary,fontSize=19.sp)
            Text(value,Modifier.weight(.68f),fontSize=20.sp,lineHeight=29.sp)
        }
        HorizontalDivider(color=StayColors.Border.copy(alpha=.6f))
    }
}

@Composable
fun ResponsivePanels(leftWeight:Float=1f,left:@Composable ()->Unit,right:@Composable ()->Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if(maxWidth>=1000.dp) Row(horizontalArrangement=Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(leftWeight)) { left() }
            Column(Modifier.weight(1f)) { right() }
        } else Column(verticalArrangement=Arrangement.spacedBy(24.dp)) { left();right() }
    }
}

@Composable
fun NoticePanel(title:String,message:String,error:Boolean=false) {
    GlassPanel(Modifier.fillMaxWidth()) {
        Text(title,fontSize=23.sp,fontWeight=FontWeight.SemiBold,color=if(error) MaterialTheme.colorScheme.error else StayColors.Blue)
        Text(message,fontSize=19.sp,lineHeight=28.sp,color=StayColors.Secondary)
    }
}
