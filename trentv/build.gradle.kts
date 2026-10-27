import java.util.Properties

plugins {
    id("com.android.application")
}

// Các lớp Java TiviKit chạy trên tivi bằng app_process dưới quyền shell, như máy chủ scrcpy (27/10/2026): không cài lên
// tivi, TiviKit đặt file vào /data/local/tmp rồi chạy (adb/TrenTv.kt). Chỉ Java, không R8: file vài KB, không cần thư viện
// Kotlin trên tivi, tên lớp giữ nguyên để app_process gọi. scripts/dung-tren-tv.sh chép bản dựng vào assets/tren-tv.jar.
val kyFile = rootProject.file("ky.properties")
val ky = Properties().apply { if (kyFile.exists()) kyFile.inputStream().use { load(it) } }

android {
    // Bản dựng tái tạo được (xem khoa/build.gradle.kts).
    dependenciesInfo { includeInApk = false; includeInBundle = false }
    namespace = "vn.tivikit.trentv"
    compileSdk = 35

    defaultConfig {
        applicationId = "vn.tivikit.trentv"
        minSdk = 21
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
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("vgs") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
