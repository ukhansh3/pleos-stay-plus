package com.pleosstay.app.preview
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest

@PreviewTest @Preview(name="scenario01_step01_home",widthDp=1682,heightDp=1210,apiLevel=34,locale="ko-rKR")
@Composable fun Journey01_01()=JourneyHome(false)

@PreviewTest @Preview(name="scenario01_step02_parking",widthDp=1682,heightDp=1210,apiLevel=34,locale="ko-rKR")
@Composable fun Journey01_02()=JourneyParking()

@PreviewTest @Preview(name="scenario01_step03_parking_detail",widthDp=1682,heightDp=1210,apiLevel=34,locale="ko-rKR")
@Composable fun Journey01_03()=JourneyParking(true)

@PreviewTest @Preview(name="scenario01_step04_route_request",widthDp=1682,heightDp=1210,apiLevel=34,locale="ko-rKR")
@Composable fun Journey01_04()=JourneyParking(true,true)

@PreviewTest @Preview(name="scenario01_step05_waiting_choice",widthDp=1682,heightDp=1210,apiLevel=34,locale="ko-rKR")
@Composable fun Journey01_05()=JourneyWaiting(false)

@PreviewTest @Preview(name="scenario01_step06_waiting_active",widthDp=1682,heightDp=1210,apiLevel=34,locale="ko-rKR")
@Composable fun Journey01_06()=JourneyWaiting(true)

@PreviewTest @Preview(name="scenario01_step07_rest_settings",widthDp=1682,heightDp=1210,apiLevel=34,locale="ko-rKR")
@Composable fun Journey01_07()=JourneyRest()

@PreviewTest @Preview(name="scenario01_step08_rest_started",widthDp=1682,heightDp=1210,apiLevel=34,locale="ko-rKR")
@Composable fun Journey01_08()=JourneyRest(0)

@PreviewTest @Preview(name="scenario01_step09_rest_20min",widthDp=1682,heightDp=1210,apiLevel=34,locale="ko-rKR")
@Composable fun Journey01_09()=JourneyRest(20)

@PreviewTest @Preview(name="scenario01_step10_home_work",widthDp=1682,heightDp=1210,apiLevel=34,locale="ko-rKR")
@Composable fun Journey01_10()=JourneyHome(true,20)

@PreviewTest @Preview(name="scenario01_step11_confirm",widthDp=1682,heightDp=1210,apiLevel=34,locale="ko-rKR")
@Composable fun Journey01_11()=JourneyTransition()

@PreviewTest @Preview(name="scenario01_step12_processing",widthDp=1682,heightDp=1210,apiLevel=34,locale="ko-rKR")
@Composable fun Journey01_12()=JourneyTransition(true)

@PreviewTest @Preview(name="scenario01_step13_work_library",widthDp=1682,heightDp=1210,apiLevel=34,locale="ko-rKR")
@Composable fun Journey01_13()=JourneyWork(0)

@PreviewTest @Preview(name="scenario01_step14_work_open",widthDp=1682,heightDp=1210,apiLevel=34,locale="ko-rKR")
@Composable fun Journey01_14()=JourneyWork(1)

@PreviewTest @Preview(name="scenario01_step15_work_edit",widthDp=1682,heightDp=1210,apiLevel=34,locale="ko-rKR")
@Composable fun Journey01_15()=JourneyWork(2)

@PreviewTest @Preview(name="scenario01_step16_work_saving",widthDp=1682,heightDp=1210,apiLevel=34,locale="ko-rKR")
@Composable fun Journey01_16()=JourneyWork(3)

@PreviewTest @Preview(name="scenario01_step17_work_saved",widthDp=1682,heightDp=1210,apiLevel=34,locale="ko-rKR")
@Composable fun Journey01_17()=JourneyWork(4)

@PreviewTest @Preview(name="scenario02_step01_waiting",widthDp=1682,heightDp=1210,apiLevel=34,locale="ko-rKR")
@Composable fun Journey02_01()=JourneyWaiting(true)

@PreviewTest @Preview(name="scenario02_step02_study_start",widthDp=1682,heightDp=1210,apiLevel=34,locale="ko-rKR")
@Composable fun Journey02_02()=JourneyStudy(0)

@PreviewTest @Preview(name="scenario02_step03_study_library",widthDp=1682,heightDp=1210,apiLevel=34,locale="ko-rKR")
@Composable fun Journey02_03()=JourneyStudy(1)

@PreviewTest @Preview(name="scenario02_step04_study_read",widthDp=1682,heightDp=1210,apiLevel=34,locale="ko-rKR")
@Composable fun Journey02_04()=JourneyStudy(2)

@PreviewTest @Preview(name="scenario02_step05_study_note",widthDp=1682,heightDp=1210,apiLevel=34,locale="ko-rKR")
@Composable fun Journey02_05()=JourneyStudy(3)

@PreviewTest @Preview(name="scenario02_step06_study_saved",widthDp=1682,heightDp=1210,apiLevel=34,locale="ko-rKR")
@Composable fun Journey02_06()=JourneyStudy(4)

@PreviewTest @Preview(name="scenario02_step07_study_home",widthDp=1682,heightDp=1210,apiLevel=34,locale="ko-rKR")
@Composable fun Journey02_07()=JourneyHome(true)

@PreviewTest @Preview(name="scenario02_step08_study_return",widthDp=1682,heightDp=1210,apiLevel=34,locale="ko-rKR")
@Composable fun Journey02_08()=JourneyStudy(4)

@PreviewTest @Preview(name="scenario02_step09_study_error",widthDp=1682,heightDp=1210,apiLevel=34,locale="ko-rKR")
@Composable fun Journey02_09()=JourneyStudy(5)

@PreviewTest @Preview(name="scenario03_step01_home",widthDp=1682,heightDp=1210,apiLevel=34,locale="ko-rKR")
@Composable fun Journey03_01()=JourneyHome(true)

@PreviewTest @Preview(name="scenario03_step02_pickup_start",widthDp=1682,heightDp=1210,apiLevel=34,locale="ko-rKR")
@Composable fun Journey03_02()=JourneyPickup(0)

@PreviewTest @Preview(name="scenario03_step03_pickup_qr",widthDp=1682,heightDp=1210,apiLevel=34,locale="ko-rKR")
@Composable fun Journey03_03()=JourneyPickup(1)

@PreviewTest @Preview(name="scenario03_step04_pickup_status",widthDp=1682,heightDp=1210,apiLevel=34,locale="ko-rKR")
@Composable fun Journey03_04()=JourneyPickup(2)

@PreviewTest @Preview(name="scenario03_step05_pickup_home",widthDp=1682,heightDp=1210,apiLevel=34,locale="ko-rKR")
@Composable fun Journey03_05()=JourneyHome(true)

@PreviewTest @Preview(name="AdaptiveHome",widthDp=960,heightDp=720,apiLevel=34,locale="ko-rKR")
@Composable fun JourneyAdaptiveHome()=JourneyHome(true)

@PreviewTest @Preview(name="AdaptiveRest",widthDp=720,heightDp=1210,apiLevel=34,locale="ko-rKR")
@Composable fun JourneyAdaptiveRest()=JourneyRest(20)

@PreviewTest @Preview(name="TransitionFailure",widthDp=960,heightDp=720,apiLevel=34,locale="ko-rKR")
@Composable fun JourneyTransitionFailure()=JourneyTransition(failed=true)
