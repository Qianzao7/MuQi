plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.qtanim.module"
    compileSdk = 37

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        applicationId = "com.qtanim.module"
        minSdk = 33
        targetSdk = 37
        versionCode = 164
        versionName = "2.2.2"
        // 目标设备为小米 ARM64 手机，只打包 arm64-v8a，去掉 x86_64 的冗余 .so
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        // R8 收缩 + 资源收缩（砍掉 Compose/miuix 里没用到的大量子类，T3.8 基线即此配置），
        // 但 proguard-rules.pro 里 -dontobfuscate 保证类名/方法名不混淆，
        // Xposed 反射入口 / JNI 绑定 / NavKey 序列化全部安全。
        debug {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            // 飞入动画核心在 libhyperbounce_hook.so：强制去符号防逆向。
            // AGP 默认仅 release 会 strip，这里显式清空让 debug 交付包也 strip。
            keepDebugSymbols.clear()
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.material3:material3:1.3.0")
    implementation("top.yukonga.miuix.kmp:miuix-ui-android:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-preference-android:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-icons-android:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-blur-android:0.9.4")
    compileOnly("io.github.libxposed:api:102.0.0")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
