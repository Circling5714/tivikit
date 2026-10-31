import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// App "Hẹn giờ" cài lên tivi (25/10/2026): bật / tắt tivi và mở app theo lịch đặt trong iCM_CleanTV. iCM_CleanTV đóng
// sẵn APK này trong assets (build-nguon.ps1 / scripts/dung-hen-gio.sh) và cài khi người dùng lưu lịch. Ký cùng khoá
// với iCM_CleanTV.
val kyFile = rootProject.file("ky.properties")
val ky = Properties().apply { if (kyFile.exists()) kyFile.inputStream().use { load(it) } }

android {
    // Bản dựng tái tạo được (xem khoa/build.gradle.kts).
    dependenciesInfo { includeInApk = false; includeInBundle = false }
    namespace = "vn.tivikit.hengio"
    compileSdk = 35
    testOptions { unitTests.isReturnDefaultValues = true }

    defaultConfig {
        applicationId = "vn.tivikit.hengio"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.1.2"
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
