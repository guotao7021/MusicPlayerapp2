plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("androidx.navigation.safeargs.kotlin")
    id("kotlin-parcelize")
    // 如果你在项目中使用 kapt 注解处理器，请保留下面一行
    id("org.jetbrains.kotlin.kapt")
}

android {
    namespace   = "com.huanmie.musicplayerapp"
    compileSdk  = 35  // 更新到API 35

    defaultConfig {
        applicationId     = "com.huanmie.musicplayerapp"
        minSdk            = 24
        targetSdk         = 35  // 更新到API 35
        versionCode       = 5
        versionName       = "5.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = file("C:\\Users\\86138\\AndroidStudioProjects\\MusicPlayerapp2\\keystore.jks")
            storePassword = "admin123"
            keyAlias = "admin"
            keyPassword = "admin123"
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig    = signingConfigs.getByName("release")
            isMinifyEnabled  = false      // 如需混淆可设 true 并配置 proguard
        }
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        // **关键**：启用 ViewBinding，才能生成 com.example.musicplayerapp2.databinding.ActivityPlayerBinding
        viewBinding = true
    }
}

dependencies {
    // 图片加载 - 更新版本
    implementation("com.github.bumptech.glide:glide:4.16.0")
    kapt("com.github.bumptech.glide:compiler:4.16.0")  // 统一版本

    // Android 核心扩展 - 更新版本
    implementation("androidx.core:core-ktx:1.13.1")

    // AppCompat - 更新版本
    implementation("androidx.appcompat:appcompat:1.7.0")

    // Material Components - 更新版本
    implementation("com.google.android.material:material:1.12.0")

    // JSON 序列化
    implementation("com.google.code.gson:gson:2.10.1")

    // LiveData Kotlin 扩展 - 更新版本
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.4")

    // Navigation - 更新版本
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7")

    // AndroidX Media，用于控制媒体播放
    implementation(libs.androidx.media)

    // 单元测试
    testImplementation("junit:junit:4.13.2")
    // Android Instrumentation 测试 - 更新版本
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
}