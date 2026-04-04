plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    kotlin("plugin.serialization") version "2.3.20"
    // 아래 줄을 추가하세요 (Kotlin 버전과 동일하게 설정)
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20"
}

android {
    namespace = "com.leejang.sleeptandard.wear"
    compileSdk = 36

    defaultConfig {
        // CRITICAL: applicationId must match Phone app for Wearable Data Layer API communication
        // namespace remains ".wear" to preserve R class and internal code structure
        applicationId = "com.leejang.sleeptandard"
        minSdk = 26
        targetSdk = 35
        versionCode = libs.versions.project.versionCode.get().toInt()
        versionName = libs.versions.project.versionName.get()
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

    buildFeatures {
        compose = true
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
    // AndroidX Core
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
    
    // Wearable API for Phone-Watch communication
    implementation("com.google.android.gms:play-services-wearable:18.1.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    
    // JSON serialization for data transfer
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    
    // PyTorch Mobile for AI inference
    implementation("org.pytorch:pytorch_android_lite:1.13.1")
    implementation("org.pytorch:pytorch_android_torchvision_lite:1.13.1")
    implementation("androidx.activity:activity-ktx:1.8.0")

    // (혹시 UI 관련 오류가 남는다면 이것도 추가)
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // Wear OS Compose 라이브러리
    implementation("androidx.wear.compose:compose-material:1.2.1")
    implementation("androidx.wear.compose:compose-foundation:1.2.1")
    implementation("androidx.wear.compose:compose-navigation:1.2.1")

    // Preview를 위한 도구
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation(libs.androidx.wear.tooling.preview)
    debugImplementation("androidx.compose.ui:ui-tooling")

    // 1. Compose 핵심 라이브러리 (버전 관리를 위해 BOM 사용 권장)
    implementation(platform("androidx.compose:compose-bom:2024.01.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")

    // 2. setContent 에러 해결을 위한 핵심 라이브러리 (가장 중요!)
    implementation("androidx.activity:activity-compose:1.8.0")
}


