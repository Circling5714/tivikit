import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// App "Tele Remote" cài lên tivi (31/10/2026): bot Telegram riêng của tivi chạy ngay trên tivi, người chủ tivi cho phép
// điều khiển tivi từ Telegram (bàn phím remote, Kênh nhanh, YouTube, tình trạng tivi, tắt tivi). Ký cùng khoá với TiviKit.
val kyFile = rootProject.file("ky.properties")
val ky = Properties().apply { if (kyFile.exists()) kyFile.inputStream().use { load(it) } }

android {
    // Bản dựng tái tạo được (xem khoa/build.gradle.kts).
    dependenciesInfo { includeInApk = false; includeInBundle = false }
    namespace = "vn.tivikit.teleremote"
    compileSdk = 35
    testOptions { unitTests.isReturnDefaultValues = true }

    defaultConfig {
        applicationId = "vn.tivikit.teleremote"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
    signingConfigs {
        if (kyFile.exists()) {
            create("vgs") {
                storeFile = rootProject.file(ky.getProperty("storeFile"))
                storePassword = ky.getProperty("storePassword")
                keyAlias = ky.getProperty("keyAlias")
                keyPassword = ky.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            vcsInfo.include = false
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("vgs") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
