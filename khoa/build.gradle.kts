import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// App "Khoá trẻ em" cài lên tivi (23/10/2026): khoá màn hình theo khung giờ, mở bằng PIN. CleanTV đóng sẵn APK này
// trong assets (scripts/dung-khoa.sh) và cài khi phụ huynh bật. Ký cùng khoá với CleanTV.
val kyFile = rootProject.file("ky.properties")
val ky = Properties().apply { if (kyFile.exists()) kyFile.inputStream().use { load(it) } }

android {
    // Bản dựng tái tạo được (24/10/2026): khối "dependency info" AGP nhét vào khối ký được mã hoá bằng khoá của Google,
    // mỗi lần dựng ra khác nhau. App không lên Play nên bỏ (F-Droid cũng khuyên vậy).
    dependenciesInfo { includeInApk = false; includeInBundle = false }
    namespace = "vn.tivikit.khoa"
    compileSdk = 35
    testOptions { unitTests.isReturnDefaultValues = true }

    defaultConfig {
        // Theo tên gói của iCM_CleanTV: không đụng Khoá trẻ em của CleanTV chính thức (zone.vgs.cleantv.khoa, ký khoá VGS).
        applicationId = "vn.tivikit.khoa"
        minSdk = 26
        targetSdk = 35
        // 2 (25/10/2026): giới hạn giờ xem mỗi ngày, báo cáo thời gian xem.
        versionCode = 2
        versionName = "1.1"
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
            // Không ghi mã commit git vào APK: kho công khai có lịch sử khác kho nội bộ, dựng lại từ đó sẽ lệch.
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
