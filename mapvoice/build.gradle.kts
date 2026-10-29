import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// App "Map Voice" cài lên tivi (29/10/2026): nói vào nút mic của remote như thường (Google Assistant), câu tìm nhạc / video
// mở bằng SmartTube thay vì YouTube gốc, câu mở kênh mở bằng VTVgo / TV360. Ký cùng khoá với TiviKit.
val kyFile = rootProject.file("ky.properties")
val ky = Properties().apply { if (kyFile.exists()) kyFile.inputStream().use { load(it) } }

android {
    // Bản dựng tái tạo được (xem khoa/build.gradle.kts).
    dependenciesInfo { includeInApk = false; includeInBundle = false }
    namespace = "vn.tivikit.mapvoice"
    compileSdk = 35
    testOptions { unitTests.isReturnDefaultValues = true }

    defaultConfig {
        applicationId = "vn.tivikit.mapvoice"
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
