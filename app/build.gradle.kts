plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.xplayer.app"
    compileSdk = 37

    // CI 未配置 keystore Secret 时自动退回 debug 证书，避免 release 打包直接失败
    val releaseKeystore = System.getenv("XPLAYER_KEYSTORE_PATH")
        ?.takeIf { it.isNotBlank() }
        ?.let { file(it) }
        ?.takeIf { it.exists() }
    val releaseStorePassword = System.getenv("XPLAYER_KEYSTORE_PASSWORD")?.takeIf { it.isNotEmpty() }
    val releaseKeyAlias = System.getenv("XPLAYER_KEY_ALIAS")?.takeIf { it.isNotEmpty() }
    val releaseKeyPassword = System.getenv("XPLAYER_KEY_PASSWORD")?.takeIf { it.isNotEmpty() }
    val hasCompleteReleaseSigning = releaseKeystore != null &&
        releaseStorePassword != null &&
        releaseKeyAlias != null &&
        releaseKeyPassword != null

    defaultConfig {
        applicationId = "com.xplayer.app"
        minSdk = 29
        targetSdk = 36
        versionCode = 8
        versionName = "1.4.2"
    }

    signingConfigs {
        create("release") {
            if (hasCompleteReleaseSigning) {
                storeFile = releaseKeystore
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            //  volume / ABI 优化由 jniLibs 与 dex 压缩完成；保留完整 proguard 规则以便后续随时开启 R8
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (hasCompleteReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    // 按 ABI 分包发布：release 页提供四个架构各自的包，另加一个 universal 兜底
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
            )
        }
        jniLibs {
            // 压缩存放 .so：APK 从 ~85MB 降到 ~57MB
            // 代价是安装时解压一次，首次安装耗时与占用略增
            useLegacyPackaging = true
        }
        dex {
            // dex 默认不压缩（minSdk>=28 的快速启动优化），这里换回压缩换取体积
            useLegacyPackaging = true
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.documentfile)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)

    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)
    implementation(libs.media3.ffmpeg.decoder)

    implementation(libs.mpv.android.lib)

    implementation(libs.coil.compose)
}
