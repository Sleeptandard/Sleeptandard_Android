import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val localProps = Properties().apply {
    load(rootProject.file("local.properties").inputStream())
}

android {
    namespace = "com.leejang.sleeptandard"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.leejang.sleeptandard"
        minSdk = 26
        targetSdk = 35
        versionCode = libs.versions.project.versionCode.get().toInt()
        versionName = libs.versions.project.versionName.get()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SUPABASE_URL", "\"${localProps["SUPABASE_URL"]}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${localProps["SUPABASE_ANON_KEY"]}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            pickFirsts += listOf(
                "lib/arm64-v8a/libc++_shared.so",
                "lib/armeabi-v7a/libc++_shared.so",
                "lib/x86/libc++_shared.so",
                "lib/x86_64/libc++_shared.so"
            )
        }
    }

}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.material3)
    implementation("com.google.android.gms:play-services-wearable:18.1.0") // [추가] 워치 통신용

    // icons를 import 할 수 없는 문제 때문에 추가
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.unit)
    implementation(libs.androidx.compose.ui.text)
    implementation(libs.androidx.compose.animation.core)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.foundation.layout)
    implementation(libs.androidx.animation.core)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // navigation
    val nav_version = "2.9.6"
    implementation("androidx.navigation:navigation-compose:${nav_version}")

    // numberpicker
    implementation("com.chargemap.compose:numberpicker:1.0.3")
    
    // Wearable API for Phone-Watch communication
    implementation("com.google.android.gms:play-services-wearable:18.1.0")
    
    // Coroutines support for Play Services (await() 사용)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // 앱 시작 화면
    implementation("androidx.core:core-splashscreen:1.0.1")

    // WorkManager (대용량 파일 백그라운드 업로드 + 네트워크 재시도)
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    implementation(libs.coil.compose)

    // 물방울 효과 라이브러리
    implementation("io.github.kyant0:backdrop:1.0.6")

    // Supabase
    implementation(platform("io.github.jan-tennert.supabase:bom:3.1.2"))
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:storage-kt")
    implementation("io.ktor:ktor-client-okhttp:3.1.2")


    // 워치앱 선언 (현재 활성화 중)
    wearApp(project(":wear"))

    // PyTorch Lite — 온디바이스 수면 단계 추론
    implementation("org.pytorch:pytorch_android_lite:1.13.1")
    implementation("org.pytorch:pytorch_android_torchvision_lite:1.13.1")

}