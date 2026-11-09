import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun localSetting(name: String): String = providers.gradleProperty(name).orNull
    ?: providers.environmentVariable(name).orNull
    ?: localProperties.getProperty(name, "")

fun publicBuildString(name: String): String = "\"" +
    localSetting(name).replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "").replace("\n", "") + "\""

android {
    useLibrary("android.car", false)
    namespace = "com.pleosstay.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.pleosstay.app"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        require(localSetting("REALTIME_SUPABASE_PUBLIC_KEY").trim().let { it.isEmpty() || it.startsWith("sb_publishable_") }) {
            "REALTIME_SUPABASE_PUBLIC_KEY must contain only a Supabase publishable key"
        }
        listOf("REALTIME_API_BASE_URL", "REALTIME_PAIRING_PAGE_URL", "REALTIME_SUPABASE_URL", "REALTIME_SUPABASE_PUBLIC_KEY")
            .forEach { buildConfigField("String", it, publicBuildString(it)) }
        buildConfigField(
            "String",
            "PARKING_API_BASE_URL",
            "\"${providers.gradleProperty("PARKING_API_BASE_URL").getOrElse("http://10.0.2.2:3000/api/v1/")}\""
        )
        buildConfigField(
            "String",
            "PICKUP_API_BASE_URL",
            "\"${providers.gradleProperty("PICKUP_API_BASE_URL").getOrElse("http://10.0.2.2:3000/api/v1/")}\""
        )
        buildConfigField(
            "String",
            "SUPABASE_URL",
            "\"${localSetting("SUPABASE_URL")}\""
        )
        buildConfigField(
            "String",
            "SUPABASE_PUBLISHABLE_KEY",
            "\"${localSetting("SUPABASE_PUBLISHABLE_KEY")}\""
        )
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.room:room-runtime:2.7.2")
    annotationProcessor("androidx.room:room-compiler:2.7.2")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:NaviHelper:3.0.5")
    implementation("ai.pleos.playground:FusedLocation:1.1.3")
    implementation("ai.pleos.playground:TextToSpeech:3.3.1")
    implementation("com.google.zxing:core:3.5.4") // Apache-2.0; QR encoding only.
}
