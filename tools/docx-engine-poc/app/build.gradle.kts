plugins { id("com.android.application") }
android {
 namespace="com.pleosstay.docxprobe"
 compileSdk { version=release(37) }
 defaultConfig { applicationId="com.pleosstay.docxprobe"; minSdk=34; targetSdk=34; versionCode=1; versionName="poc" }
 compileOptions { sourceCompatibility=JavaVersion.VERSION_11; targetCompatibility=JavaVersion.VERSION_11 }
 packaging { resources.excludes += setOf("META-INF/DEPENDENCIES","META-INF/LICENSE*","META-INF/NOTICE*","META-INF/versions/9/module-info.class") }
}
dependencies { implementation("org.apache.poi:poi-ooxml:5.4.1") }
