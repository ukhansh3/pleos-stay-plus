package com.pleosstay.app.preview
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest

@PreviewTest @Preview(name="home_wide",widthDp=1682,heightDp=1210,fontScale=1f,apiLevel=34,locale="ko-rKR")
@Composable fun Responsive_home_wide()=JourneyHome(true)

@PreviewTest @Preview(name="rest_wide",widthDp=1682,heightDp=1210,fontScale=1f,apiLevel=34,locale="ko-rKR")
@Composable fun Responsive_rest_wide()=JourneyRest(20)

@PreviewTest @Preview(name="study_wide",widthDp=1682,heightDp=1210,fontScale=1f,apiLevel=34,locale="ko-rKR")
@Composable fun Responsive_study_wide()=JourneyStudy(3)

@PreviewTest @Preview(name="work_library_wide",widthDp=1682,heightDp=1210,fontScale=1f,apiLevel=34,locale="ko-rKR")
@Composable fun Responsive_work_library_wide()=JourneyWork(0)

@PreviewTest @Preview(name="work_editor_wide",widthDp=1682,heightDp=1210,fontScale=1f,apiLevel=34,locale="ko-rKR")
@Composable fun Responsive_work_editor_wide()=JourneyWork(2)

@PreviewTest @Preview(name="transition_wide",widthDp=1682,heightDp=1210,fontScale=1f,apiLevel=34,locale="ko-rKR")
@Composable fun Responsive_transition_wide()=JourneyTransition()

@PreviewTest @Preview(name="home_small",widthDp=960,heightDp=720,fontScale=1f,apiLevel=34,locale="ko-rKR")
@Composable fun Responsive_home_small()=JourneyHome(true)

@PreviewTest @Preview(name="rest_small",widthDp=960,heightDp=720,fontScale=1f,apiLevel=34,locale="ko-rKR")
@Composable fun Responsive_rest_small()=JourneyRest(20)

@PreviewTest @Preview(name="study_small",widthDp=960,heightDp=720,fontScale=1f,apiLevel=34,locale="ko-rKR")
@Composable fun Responsive_study_small()=JourneyStudy(3)

@PreviewTest @Preview(name="work_library_small",widthDp=960,heightDp=720,fontScale=1f,apiLevel=34,locale="ko-rKR")
@Composable fun Responsive_work_library_small()=JourneyWork(0)

@PreviewTest @Preview(name="work_editor_small",widthDp=960,heightDp=720,fontScale=1f,apiLevel=34,locale="ko-rKR")
@Composable fun Responsive_work_editor_small()=JourneyWork(2)

@PreviewTest @Preview(name="transition_small",widthDp=960,heightDp=720,fontScale=1f,apiLevel=34,locale="ko-rKR")
@Composable fun Responsive_transition_small()=JourneyTransition()

@PreviewTest @Preview(name="home_portrait",widthDp=720,heightDp=1210,fontScale=1f,apiLevel=34,locale="ko-rKR")
@Composable fun Responsive_home_portrait()=JourneyHome(true)

@PreviewTest @Preview(name="rest_portrait",widthDp=720,heightDp=1210,fontScale=1f,apiLevel=34,locale="ko-rKR")
@Composable fun Responsive_rest_portrait()=JourneyRest(20)

@PreviewTest @Preview(name="study_portrait",widthDp=720,heightDp=1210,fontScale=1f,apiLevel=34,locale="ko-rKR")
@Composable fun Responsive_study_portrait()=JourneyStudy(3)

@PreviewTest @Preview(name="work_library_portrait",widthDp=720,heightDp=1210,fontScale=1f,apiLevel=34,locale="ko-rKR")
@Composable fun Responsive_work_library_portrait()=JourneyWork(0)

@PreviewTest @Preview(name="work_editor_portrait",widthDp=720,heightDp=1210,fontScale=1f,apiLevel=34,locale="ko-rKR")
@Composable fun Responsive_work_editor_portrait()=JourneyWork(2)

@PreviewTest @Preview(name="transition_portrait",widthDp=720,heightDp=1210,fontScale=1f,apiLevel=34,locale="ko-rKR")
@Composable fun Responsive_transition_portrait()=JourneyTransition()

@PreviewTest @Preview(name="home_narrow",widthDp=600,heightDp=720,fontScale=1f,apiLevel=34,locale="ko-rKR")
@Composable fun Responsive_home_narrow()=JourneyHome(true)

@PreviewTest @Preview(name="rest_narrow",widthDp=600,heightDp=720,fontScale=1f,apiLevel=34,locale="ko-rKR")
@Composable fun Responsive_rest_narrow()=JourneyRest(20)

@PreviewTest @Preview(name="study_narrow",widthDp=600,heightDp=720,fontScale=1f,apiLevel=34,locale="ko-rKR")
@Composable fun Responsive_study_narrow()=JourneyStudy(3)

@PreviewTest @Preview(name="work_library_narrow",widthDp=600,heightDp=720,fontScale=1f,apiLevel=34,locale="ko-rKR")
@Composable fun Responsive_work_library_narrow()=JourneyWork(0)

@PreviewTest @Preview(name="work_editor_narrow",widthDp=600,heightDp=720,fontScale=1f,apiLevel=34,locale="ko-rKR")
@Composable fun Responsive_work_editor_narrow()=JourneyWork(2)

@PreviewTest @Preview(name="transition_narrow",widthDp=600,heightDp=720,fontScale=1f,apiLevel=34,locale="ko-rKR")
@Composable fun Responsive_transition_narrow()=JourneyTransition()

@PreviewTest @Preview(name="transition_busy_wide",widthDp=1682,heightDp=1210,fontScale=1f,apiLevel=34,locale="ko-rKR")
@Composable fun Responsive_transition_busy_wide()=JourneyTransition(true)

@PreviewTest @Preview(name="transition_busy_narrow",widthDp=600,heightDp=720,fontScale=1f,apiLevel=34,locale="ko-rKR")
@Composable fun Responsive_transition_busy_narrow()=JourneyTransition(true)

@PreviewTest @Preview(name="transition_failed_wide",widthDp=1682,heightDp=1210,fontScale=1f,apiLevel=34,locale="ko-rKR")
@Composable fun Responsive_transition_failed_wide()=JourneyTransition(false,true)

@PreviewTest @Preview(name="transition_failed_narrow",widthDp=600,heightDp=720,fontScale=1f,apiLevel=34,locale="ko-rKR")
@Composable fun Responsive_transition_failed_narrow()=JourneyTransition(false,true)

@PreviewTest @Preview(name="study_error_wide",widthDp=1682,heightDp=1210,fontScale=1f,apiLevel=34,locale="ko-rKR")
@Composable fun Responsive_study_error_wide()=JourneyStudy(5)

@PreviewTest @Preview(name="study_error_narrow",widthDp=600,heightDp=720,fontScale=1f,apiLevel=34,locale="ko-rKR")
@Composable fun Responsive_study_error_narrow()=JourneyStudy(5)

@PreviewTest @Preview(name="work_error_wide",widthDp=1682,heightDp=1210,fontScale=1f,apiLevel=34,locale="ko-rKR")
@Composable fun Responsive_work_error_wide()=ResponsiveWorkRecovery()

@PreviewTest @Preview(name="work_error_narrow",widthDp=600,heightDp=720,fontScale=1f,apiLevel=34,locale="ko-rKR")
@Composable fun Responsive_work_error_narrow()=ResponsiveWorkRecovery()

@PreviewTest @Preview(name="long_modal_wide",widthDp=1682,heightDp=1210,fontScale=1f,apiLevel=34,locale="ko-rKR")
@Composable fun Responsive_long_modal_wide()=ResponsiveLongModal()

@PreviewTest @Preview(name="long_modal_narrow",widthDp=600,heightDp=720,fontScale=1f,apiLevel=34,locale="ko-rKR")
@Composable fun Responsive_long_modal_narrow()=ResponsiveLongModal()

@PreviewTest @Preview(name="home_font130",widthDp=720,heightDp=1210,fontScale=1.3f,apiLevel=34,locale="ko-rKR")
@Composable fun Responsive_home_font130()=JourneyHome(true)

@PreviewTest @Preview(name="rest_font130",widthDp=720,heightDp=1210,fontScale=1.3f,apiLevel=34,locale="ko-rKR")
@Composable fun Responsive_rest_font130()=JourneyRest(20)

@PreviewTest @Preview(name="work_editor_font130",widthDp=720,heightDp=1210,fontScale=1.3f,apiLevel=34,locale="ko-rKR")
@Composable fun Responsive_work_editor_font130()=JourneyWork(2)
