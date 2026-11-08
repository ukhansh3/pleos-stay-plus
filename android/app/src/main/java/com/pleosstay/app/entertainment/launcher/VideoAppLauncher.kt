package com.pleosstay.app.entertainment.launcher

import android.content.Context
import android.content.Intent
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.os.Build
import com.pleosstay.app.waiting.safety.WaitingSafety

enum class VideoApp { YOUTUBE, NETFLIX }
/** Supplied only after checking the target PLEOS image. No guessed production package defaults. */
data class VerifiedVideoTarget(val app: VideoApp, val packageName: String, val verification: String) {
    init { require(packageName.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")) && verification.isNotBlank()) }
}
enum class LaunchStatus { AVAILABLE, NOT_INSTALLED, INSTALLATION_UNKNOWN, NO_ENTRY_POINT, POLICY_BLOCKED, SAFETY_BLOCKED, LAUNCHED, ERROR }
data class LaunchResult(val status: LaunchStatus, val message: String)
interface VideoAppPlatform {
    fun inspect(target: VerifiedVideoTarget): LaunchResult
    fun launch(target: VerifiedVideoTarget): LaunchResult
}
class VideoAppLauncher(private val platform: VideoAppPlatform, targets: List<VerifiedVideoTarget>,
    private val safety: () -> WaitingSafety) {
    private val targets = targets.associateBy { it.app }
    init { require(this.targets.size == targets.size) }
    fun availability(app: VideoApp): LaunchResult {
        if (!safety().canUse) return LaunchResult(LaunchStatus.SAFETY_BLOCKED,"정차 및 안전 상태를 확인해 주세요.")
        val target=targets[app] ?: return LaunchResult(LaunchStatus.INSTALLATION_UNKNOWN,"현재 PLEOS 패키지와 실행 진입점 검증이 필요합니다.")
        return platform.inspect(target)
    }
    fun launch(app: VideoApp): LaunchResult {
        val available=availability(app)
        if(available.status != LaunchStatus.AVAILABLE) return available
        if (!safety().canUse) return LaunchResult(LaunchStatus.SAFETY_BLOCKED,"안전 상태가 변경되었습니다.")
        return platform.launch(targets.getValue(app))
    }
}
/** No download, WebView fallback, DRM workarounds, or external playback control. */
class AndroidVideoAppPlatform(context: Context) : VideoAppPlatform {
    private val context=context.applicationContext
    private val pm=this.context.packageManager
    private fun declaredVisible(packageName:String):Boolean {
        if (Build.VERSION.SDK_INT < 30) return true
        val parser=context.assets.openXmlResourceParser("AndroidManifest.xml")
        return try {
            var inQueries=false; var visible=false
            while(parser.next()!=org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if(parser.eventType==org.xmlpull.v1.XmlPullParser.START_TAG) {
                    if(parser.name=="queries") inQueries=true
                    if(inQueries && parser.name=="package" && parser.getAttributeValue("http://schemas.android.com/apk/res/android","name")==packageName) visible=true
                }
                if(parser.eventType==org.xmlpull.v1.XmlPullParser.END_TAG && parser.name=="queries") inQueries=false
            }; visible
        } catch (_:Exception) { false } finally { parser.close() }
    }
    @Suppress("DEPRECATION")
    override fun inspect(target:VerifiedVideoTarget):LaunchResult = try {
        val app=pm.getApplicationInfo(target.packageName,0)
        if(!app.enabled) LaunchResult(LaunchStatus.POLICY_BLOCKED,"대상 앱이 비활성 상태입니다.")
        else {
            val intent=pm.getLaunchIntentForPackage(target.packageName)
            val activity=intent?.resolveActivityInfo(pm,0)
            when {
                activity==null -> LaunchResult(LaunchStatus.NO_ENTRY_POINT,"공개 실행 진입점을 찾을 수 없습니다.")
                !activity.exported || !activity.enabled -> LaunchResult(LaunchStatus.POLICY_BLOCKED,"외부 실행이 허용되지 않습니다.")
                activity.permission!=null && context.checkSelfPermission(activity.permission)!=PackageManager.PERMISSION_GRANTED -> LaunchResult(LaunchStatus.POLICY_BLOCKED,"대상 Activity 실행 권한이 없습니다.")
                else -> LaunchResult(LaunchStatus.AVAILABLE,"실행 요청 가능 · 실제 실행 성공은 별도 확인합니다.")
            }
        }
    } catch (_:PackageManager.NameNotFoundException) {
        if(declaredVisible(target.packageName)) LaunchResult(LaunchStatus.NOT_INSTALLED,"현재 사용자에게 설치된 앱을 찾을 수 없습니다.")
        else LaunchResult(LaunchStatus.INSTALLATION_UNKNOWN,"패키지 조회 가시성이 보장되지 않습니다.")
    } catch (_:SecurityException) { LaunchResult(LaunchStatus.POLICY_BLOCKED,"플랫폼에서 조회를 허용하지 않습니다.") }
      catch (_:Exception) { LaunchResult(LaunchStatus.ERROR,"앱 조회 중 오류가 발생했습니다.") }
    override fun launch(target:VerifiedVideoTarget):LaunchResult {
        val available=inspect(target);if(available.status!=LaunchStatus.AVAILABLE)return available
        return try {
            val intent=pm.getLaunchIntentForPackage(target.packageName) ?: return LaunchResult(LaunchStatus.NO_ENTRY_POINT,"실행 진입점이 변경됐습니다.")
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            LaunchResult(LaunchStatus.LAUNCHED,"외부 앱 실행 요청을 전달했습니다. 콘텐츠 재생 성공을 의미하지 않습니다.")
        } catch (_:ActivityNotFoundException) { LaunchResult(LaunchStatus.NO_ENTRY_POINT,"실행할 Activity가 없습니다.") }
          catch (_:SecurityException) { LaunchResult(LaunchStatus.POLICY_BLOCKED,"플랫폼에서 실행을 차단했습니다.") }
          catch (_:Exception) { LaunchResult(LaunchStatus.ERROR,"외부 앱 실행에 실패했습니다.") }
    }
}
