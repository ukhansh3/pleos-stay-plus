plugins { id("com.android.application") }
android {
    namespace = "com.pleosstay.workenginepoc"
    compileSdk { version = release(37) }
    defaultConfig {
        applicationId = "com.pleosstay.workenginepoc"
        minSdk = 34
        targetSdk = 34
        versionCode = 1
        versionName = "evaluation-only"
    }
    buildTypes { debug { isDebuggable = true } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
}
dependencies {
    implementation(files("../artifacts/pdf23.2/lib/aspose-pdf-23.2-android.via.java.jar"))
    implementation(files("../artifacts/aspose-words-25.9-android.via.java.jar"))
}
