import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// App "Kênh nhanh" cài lên tivi (25/10/2026): mở thẳng một kênh trực tiếp của TV360 khi bấm ô trên Home sạch hoặc
// nút remote đã gán. iCM_CleanTV đóng sẵn APK này trong assets (build-nguon.ps1 / scripts/dung-kenh.sh) và cài khi người
// dùng lưu kênh. Ký cùng khoá với iCM_CleanTV.
val kyFile = rootProject.file("ky.properties")
val ky = Properties().apply { if (kyFile.exists()) kyFile.inputStream().use { load(it) } }

android {
    // Bản dựng tái tạo được (xem khoa/build.gradle.kts).
    dependenciesInfo { includeInApk = false; includeInBundle = false }
    namespace = "vn.tivikit.kenh"
    compileSdk = 35
    testOptions { unitTests.isReturnDefaultValues = true }

    defaultConfig {
        applicationId = "vn.tivikit.kenh"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }
    // Nhiều ô kênh (25/10/2026): Home sạch mở app và gán nút remote theo tên gói, nên mỗi ô là một app riêng — cùng mã,
    // khác tên gói. Ô 1 giữ tên gói cũ để bản một ô trước đây cài đè được (giữ kênh đã đặt).
    flavorDimensions += "o"
    productFlavors {
        create("o1") { dimension = "o"; applicationId = "vn.tivikit.kenh"; resValue("string", "ten_app", "Kênh nhanh") }
        (2..6).forEach { n ->
            create("o$n") { dimension = "o"; applicationId = "vn.tivikit.kenh$n"; resValue("string", "ten_app", "Kênh nhanh $n") }
        }
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
