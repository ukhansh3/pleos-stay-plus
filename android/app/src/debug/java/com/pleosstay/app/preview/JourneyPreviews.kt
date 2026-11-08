package com.pleosstay.app.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import com.pleosstay.app.ui.theme.PleosStayPlusTheme
import com.pleosstay.app.ui.premium.*
import com.pleosstay.app.ui.activity.*
import com.pleosstay.app.modes.ActivityMode
import com.pleosstay.app.rest.*
import com.pleosstay.app.waiting.ui.WaitingUiState
import com.pleosstay.app.waiting.domain.*
import com.pleosstay.app.waiting.safety.*
import com.pleosstay.app.pickup.ui.PickupUiState
import com.pleosstay.app.parking.ui.ParkingDetail
import com.pleosstay.app.parking.navigation.NavigationStatus
import com.pleosstay.app.entertainment.*
import com.pleosstay.app.entertainment.games.*
import com.pleosstay.app.entertainment.quiz.*
import com.pleosstay.app.entertainment.data.EntertainmentStore
import com.pleosstay.app.work.docx.*
import kotlin.random.Random

// Fixed UTC instant = 14:00 KST. Only inspection state uses this clock.
internal const val JOURNEY_START=1794114000000L
internal val journeySafety=WaitingSafety(VehicleMotion.PARKED,false,message="주차 상태를 확인했습니다.")
internal fun journeyWaiting(elapsed:Int=0)=WaitingUiState(ready=true,safety=journeySafety,now=JOURNEY_START+elapsed*60_000,
    sessions=listOf(WaitingSession("journey-waiting",JOURNEY_START,JOURNEY_START+120*60_000,place=WaitingPlace("sample-parking","예시 공영주차장"))))

@Composable internal fun JourneyFrame(route:StayRoute=StayRoute.ENTERTAINMENT,scroll:Boolean=true,content:@Composable ()->Unit) {
    if(!LocalInspectionMode.current){Text("Preview only");return}
    PleosStayPlusTheme {BoxWithConstraints(Modifier.fillMaxSize()) {
        val railWidth=if(maxWidth>=1300.dp)180.dp else 112.dp
        Row(Modifier.fillMaxSize().background(StayColors.Background)) {
            StayNavigationRail(route.group,{},railWidth)
            Column(Modifier.weight(1f).fillMaxHeight()) {
                if(route!=StayRoute.HOME)StayBackBar({})
                if(scroll) Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(28.dp),verticalArrangement=Arrangement.spacedBy(22.dp)){content()}
                else Box(Modifier.weight(1f)){content()}
            }
        }
    }}
}
@Composable internal fun JourneyHome(active:Boolean,elapsed:Int=0)=JourneyFrame(StayRoute.HOME,false) {
    StayHomeScreen(if(active)journeyWaiting(elapsed) else WaitingUiState(ready=true,safety=journeySafety,now=JOURNEY_START),PickupUiState(),{})
}
@Composable internal fun JourneyParking(detail:Boolean=false,requesting:Boolean=false) {
    val sample=previewSample(PreviewScreen.PARKING,LocalResources.current)
    if(!detail)PreviewFeature(PreviewScreen.PARKING,sample)
    else JourneyFrame(StayRoute.PARKING) { ParkingDetail(checkNotNull(sample.parking?.selected),
        if(requesting)NavigationStatus.Requesting("예시 공영주차장") else NavigationStatus.Ready,{},{}) }
}
@Composable internal fun JourneyWaiting(active:Boolean) {
    val sample=previewSample(PreviewScreen.WAITING,LocalResources.current)
    PreviewFeature(PreviewScreen.WAITING,sample.copy(waiting=if(active)journeyWaiting() else WaitingUiState(ready=true,safety=journeySafety,now=JOURNEY_START)))
}
@Composable internal fun JourneyRest(elapsed:Int=-1,muted:Boolean=false) {
    val sample=previewSample(PreviewScreen.REST,LocalResources.current)
    val preset=RestPreset.defaults[2].copy(minutes=60,volume=65,muted=muted)
    val rest=RestState(ready=true,presets=RestPreset.defaults,session=if(elapsed<0)null else RestSession(preset,JOURNEY_START,JOURNEY_START+60*60_000,"journey-waiting",alarmMode=RestAlarmMode.FOREGROUND_ONLY))
    PreviewFeature(PreviewScreen.REST,sample.copy(rest=rest,restEditor=if(elapsed<0)preset else null,
        nowMillis=JOURNEY_START+maxOf(elapsed,0)*60_000,safety=journeySafety))
}
@Composable internal fun JourneyTransition(busy:Boolean=false,failed:Boolean=false)=PleosStayPlusTheme {
    JourneyHome(true,20)
    ActivityModal(if(failed)"전환을 완료하지 못했어요" else if(busy)"활동을 정리하고 있어요" else "휴식 종료 후 업무 전환",busy,{}) {
        if(failed) { Text("저장 상태를 확인한 뒤 다시 시도해 주세요.");PremiumAction("다시 시도",{});PremiumAction("작업으로 돌아가기",{},secondary=true) }
        else ActivitySwitchPanel(ActivityMode.REST,ActivityMode.WORK,busy,{},{})
    }
}
@Composable internal fun JourneyWork(stage:Int) {
    val screen=if(stage==0)PreviewScreen.WORK_LIBRARY else PreviewScreen.WORK_EDITOR
    val sample=previewSample(screen,LocalResources.current)
    if(stage==0)PreviewFeature(screen,sample)
    else {
        val engine=remember(stage){OoxmlDocxEngine.blank().apply {
            replaceText(0,0,0,"오늘의 업무 계획")
            addParagraph(0,if(stage>=2)"제안서 검토 완료. 다음 회의 자료를 준비합니다." else "제안서 검토와 회의 자료 준비")
            format(0,0,view().paragraphs[0].text.length,DocxStyle(bold=true,size=24f))
        }}
        PreviewFeature(screen,sample.copy(docx=sample.docx!!.copy(view=engine.view(),dirty=stage==2,busy=stage==3,
            message=if(stage==4)"암호화 DOCX 초안 저장 완료" else "")))
    }
}
@Composable internal fun JourneyStudy(stage:Int) {
    val sample=previewSample(PreviewScreen.STUDY,LocalResources.current);val old=sample.study!!
    val state=when(stage) {
        0->old.copy(active=false,selected=null,document=null,record=null)
        1->old.copy(selected=null,document=null,record=null)
        2->old
        3->old.copy(noteText="중요한 개념을 정리했습니다.")
        4->old.copy(noteText="",noteSaveSerial=1,record=old.record!!.copy(notes=old.record.notes + com.pleosstay.app.study.StudyNote("journey-note","0","중요한 개념을 정리했습니다.",JOURNEY_START)),message="학습 기록을 저장했습니다.")
        5->old.copy(noteText="중요한 개념을 정리했습니다.",noteSaveFailed=true,message="메모를 저장하지 못했습니다.")
        else->old
    }
    PreviewFeature(PreviewScreen.STUDY,sample.copy(study=state))
}
@Composable internal fun JourneyPickup(stage:Int) {
    val sample=previewSample(PreviewScreen.PICKUP,LocalResources.current);val old=sample.pickup!!
    PreviewFeature(PreviewScreen.PICKUP,sample.copy(pickup=when(stage){
        0->old.copy(session=null,invitation=null,sharingLocation=false)
        1->old.copy(sharingLocation=false,locationMessage="위치 공유 전입니다.")
        else->old
    }))
}
internal fun journeyGame(stage:Int):Game2048State {
    val initial=Game2048State(listOf(2,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0))
    return if(stage==0)initial else Game2048Engine(Random(7)).move(initial,MoveDirection.LEFT)
}
private class JourneyStore:EntertainmentStore {
    private val records=mutableMapOf<String,ByteArray>()
    override fun read(key:String)=records[key]?.copyOf()
    override fun write(key:String,bytes:ByteArray){records[key]=bytes.copyOf()}
}
internal fun journeyQuiz(stage:Int):QuizState {
    val service=QuizService(JourneyStore(),Random(7));var state=service.start()
    if(stage>=1){state=service.submit(state.current!!.id,state.current!!.answer)}
    if(stage>=2)state=service.next()
    if(stage>=3)state=service.submit(state.current!!.id,(state.current!!.answer+1)%4)
    if(stage>=4){service.next();while(service.resume()?.complete==false){val q=service.resume()!!.current!!;service.submit(q.id,q.answer);service.next()};state=service.resume()!!}
    return state
}
internal fun journeyMatch(stage:Int):MatchState? {
    if(stage==0)return null
    val engine=QuizMatchService(random=Random(7));engine.enter();var s=engine.start(MatchConfig(listOf("플레이어 1","플레이어 2")))
    if(stage>=2)s=engine.submit(s.current!!.id,s.current!!.answer)
    if(stage>=3)s=engine.next()
    if(stage>=4)s=engine.submit(s.current!!.id,s.current!!.answer)
    if(stage>=5){engine.next();while(engine.state.value?.complete==false){val q=engine.state.value!!.current!!;engine.submit(q.id,q.answer);engine.next()};s=engine.state.value!!}
    return s
}
@Composable internal fun JourneyEntertainment(content:String,stage:Int=0)=JourneyFrame {
    when(content) {
        "catalogue"->EntertainmentCatalogue(true,{},{},{})
        "game"->GamePanel(journeyGame(stage),true,{},{})
        "quiz"->QuizPanel(journeyQuiz(stage),true,{_,_->},{},{})
        "match"->MatchPanel(journeyMatch(stage),true,{},{_,_->},{},{})
    }
}
