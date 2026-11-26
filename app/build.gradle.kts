/*
 * Copyright 2026 SMTM-PH and NasPhotoTV contributors
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.nasphoto.tv"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.nasphoto.tv"
        // Android 7.0（API 24）起支持，覆盖 7.0 / 7.1.x 老电视盒子。
        // java.time 等 API 26 才有的类由 core library desugaring 提供。
        minSdk = 24
        targetSdk = 34
        versionCode = 21
        versionName = "1.2.15"
    }

    signingConfigs {
        // 兼容 Android 7.x：AGP 8 在 minSdk >= 24 时默认只签 v2，
        // 部分国产 7.0/7.1 盒子的 v2 校验实现不完整，显式补上 v1(JAR) 签名。
        // Android 7.0 是首个支持 v2 的版本，双签不影响新系统。
        getByName("debug") {
            enableV1Signing = true
            enableV2Signing = true
        }
        create("release") {
            // 正式签名：keystore 不进 git（见 .gitignore），密码在本机 keystore/pass.txt。
            // 首次生成：keytool -genkeypair -keystore keystore/release.jks -alias nasphototv ...
            val ksFile = rootProject.file("keystore/release.jks")
            val passFile = rootProject.file("keystore/pass.txt")
            if (ksFile.exists() && passFile.exists()) {
                storeFile = ksFile
                storePassword = passFile.readText().trim()
                keyAlias = "nasphototv"
                keyPassword = passFile.readText().trim()
            }
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // 有正式 keystore 时 release 用正式签名；没有则退回 debug 签名，保证 CI 可构建
            signingConfig = if (rootProject.file("keystore/release.jks").exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // 支持 Android 7.x（API 24/25）：java.time 等 API 26 才有的类通过脱糖提供
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    lint {
        // 本项目没有 androidTest 源码；离线构建时不必为了 lint 去解析 androidTest 依赖。
        checkTestSources = false
        // 下面几项是项目既有的基线误报（media3 的 @UnstableApi 标记、androidx 的
        // RestrictedApi、Compose 的 lambda 参数约定），与 minSdk 24 兼容无关。
        // 保留 NewApi / InlinedApi 等真正能挡住低版本崩溃的检查继续生效。
        disable += setOf(
            "UnsafeOptInUsageError",
            "RestrictedApi",
            "UnusedContentLambdaTargetStateParameter",
        )
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("com.google.zxing:core:3.5.3")
    // 飞牛相册对接：WebSocket 登录 + 签名 HTTP + 后续缩略图/视频流共用同一个客户端
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // 真实视频 / 实况片段播放（带 AccessToken 的 OkHttp 数据源）
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.5.1")
    // 真实 NAS 图片加载（与 API 共用 OkHttp 客户端和证书信任）
    implementation("io.coil-kt:coil-compose:2.7.0")
    // 动图（GIF / 动态 WebP / HEIF 序列）解码
    implementation("io.coil-kt:coil-gif:2.7.0")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
