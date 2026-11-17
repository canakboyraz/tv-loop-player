plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.pion.tvloop"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pion.tvloop"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        // Varsayılan manifest adresi — uygulama içinden (MENU tuşu) değiştirilebilir
        buildConfigField("String", "DEFAULT_MANIFEST_URL", "\"https://example.com/tv/manifest.json\"")
    }
    buildFeatures { buildConfig = true }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
}
