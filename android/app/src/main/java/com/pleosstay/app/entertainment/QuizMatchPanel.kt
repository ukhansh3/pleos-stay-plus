package com.pleosstay.app.entertainment

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pleosstay.app.entertainment.quiz.*
import com.pleosstay.app.ui.premium.*

@Composable
internal fun MatchPanel(state:MatchState?,enabled:Boolean,start:(MatchConfig)->Unit,
    answer:(String,Int)->Unit,next:()->Unit,restart:()->Unit) {
    Surface(color=GamePaper,shape=androidx.compose.foundation.shape.RoundedCornerShape(24.dp),modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(28.dp),verticalArrangement=Arrangement.spacedBy(22.dp)) {
            when {
                state==null -> MatchSetup(enabled,start)
                state.complete -> {
                    Text(if(state.winners.size>1) "함께 우승했어요!" else "${state.config.names[state.winners.first()]} 우승!",color=GameInk,fontSize=36.sp,fontWeight=FontWeight.Bold)
                    state.scores.indices.sortedByDescending{state.scores[it]}.forEach { player ->
                        Text("${state.config.names[player]} · ${state.scores[player]}점" + if(player in state.winners) "  ✓ 우승" else "",fontSize=28.sp,color=GameInk)
                    }
                    PremiumAction("다시 대결하기",restart,enabled=enabled)
                }
                else -> {
                    Text("${state.config.names[state.player]} 차례 · ${state.scores[state.player]}점",color=GameInk,fontSize=28.sp,fontWeight=FontWeight.Bold)
                    Text("개인 문제 ${state.turn/state.config.names.size+1} / ${state.config.perPlayer} · 전체 ${state.turn+1} / ${state.questions.size}",fontSize=20.sp,color=GameInk)
                    LinearProgressIndicator(progress={state.turn.toFloat()/state.questions.size},modifier=Modifier.fillMaxWidth(),color=GameInk)
                    state.current?.let {q->QuizQuestionPanel(q,state.answers[state.turn],enabled,{answer(q.id,it)})}
                    state.correct?.let { correct ->
                        Text(if(correct) "✓ 정답이에요! +10점" else "오답이에요. +0점",color=GameInk,fontSize=24.sp)
                        PremiumAction(if(state.turn==state.questions.lastIndex) "결과 보기" else "다음 참가자",next,enabled=enabled)
                    }
                }
            }
        }
    }
}

@Composable
internal fun MatchSetup(enabled:Boolean,start:(MatchConfig)->Unit) {
    var players by remember { mutableIntStateOf(2) };var count by remember{mutableIntStateOf(5)}
    var category by remember{mutableStateOf(MatchCategory.ALL)}
    var names by remember{mutableStateOf(List(4){"플레이어 ${it+1}"})}
    Text("함께 대결하기",fontSize=34.sp,color=GameInk,fontWeight=FontWeight.Bold)
    Text("한 화면에서 차례대로 풀어보세요.",fontSize=22.sp,color=GameInk)
    @Composable fun <T> choices(title:String,values:List<T>,selected:T,label:(T)->String,change:(T)->Unit) {
        Text(title,fontSize=22.sp,color=GameInk)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            values.forEach { v -> Button(onClick={change(v)},enabled=enabled,modifier=Modifier.heightIn(min=64.dp),
                colors=ButtonDefaults.buttonColors(containerColor=if(v==selected)GameInk else GameSky,contentColor=if(v==selected)StayColors.Text else GameInk)) {
                Text((if(v==selected)"✓ " else "")+label(v),fontSize=22.sp)
            } }
        }
    }
    choices("참가 인원",listOf(2,3,4),players,{"${it}명"},{players=it})
    repeat(players) { i->OutlinedTextField(names[i],{v->names=names.toMutableList().apply{this[i]=v.take(24)}},
        label={Text("참가자 ${i+1}")},singleLine=true,enabled=enabled,modifier=Modifier.fillMaxWidth(),
        colors=OutlinedTextFieldDefaults.colors(focusedTextColor=GameInk,unfocusedTextColor=GameInk,focusedLabelColor=GameInk,unfocusedLabelColor=GameInk)) }
    choices("1인당 문제 수",listOf(5,10,15),count,{"${it}문제"},{count=it})
    choices("카테고리",MatchCategory.entries,category,{it.label},{category=it})
    Text("정답 10점 · 오답 0점 · 이름은 대결 중에만 사용해요.",fontSize=20.sp,color=GameInk)
    PremiumAction("대결 시작",{start(MatchConfig(names.take(players).mapIndexed{i,n->n.trim().ifBlank{"플레이어 ${i+1}"}},count,category))},enabled=enabled)
}
