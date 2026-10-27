import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Khoá ký nằm ngoài git (tv-sach.keystore + ky.properties). Thiếu thì bản release ký bằng khoá debug.
val kyFile = rootProject.file("ky.properties")
val ky = Properties().apply { if (kyFile.exists()) kyFile.inputStream().use { load(it) } }

// Số phiên bản từ version.properties (26/10/2026, workflow Phát hành tự tăng). versionCode = lớn×10000 + tính năng×100 + vá:
// luôn tăng theo số phiên bản và cao hơn 100 của TiviKit 1.0 (1.1.0 = 10100); tính năng và vá phải dưới 100.
val tenBan: String = Properties().apply { rootProject.file("version.properties").inputStream().use { load(it) } }.getProperty("VERSION_NAME")
val maBan: Int = tenBan.split('.').map { it.toInt() }.let { (lon, tinhNang, va) ->
    require(tinhNang in 0..99 && va in 0..99) { "version.properties: $tenBan" }
    lon * 10000 + tinhNang * 100 + va
}

android {
    // Bản dựng tái tạo được (24/10/2026): khối "dependency info" AGP nhét vào khối ký được mã hoá bằng khoá của Google,
    // mỗi lần dựng ra khác nhau. App không lên Play nên bỏ (F-Droid cũng khuyên vậy).
    dependenciesInfo { includeInApk = false; includeInBundle = false }
    testOptions { unitTests.isReturnDefaultValues = true }
    namespace = "vn.tivikit"
    compileSdk = 35

    defaultConfig {
        // Tên gói riêng: cài song song được với CleanTV chính thức (zone.vgs.cleantv). 25/10/2026 đổi com.icm.cleantv →
        // vn.tivikit theo tên TiviKit (mã nguồn cũng chuyển từ zone.vgs.tvsach sang vn.tivikit); app phụ bản cũ trên tivi
        // được chuyển sang gói mới (dulieu/GoiCu.kt).
        applicationId = "vn.tivikit"
        minSdk = 26
        targetSdk = 35
        // TiviKit 1.0 (versionCode 100) dựa trên CleanTV 2.8.2; từ 1.1.0 số phiên bản lấy từ version.properties.
        versionCode = maBan
        versionName = tenBan
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        // Tự cập nhật (26/10/2026): hỏi GitHub Releases của TiviKit (CapNhat.KHO), không còn hỏi máy chủ của CleanTV.
        buildConfigField("boolean", "TU_CAP_NHAT", "true")
        // Không gửi số liệu ẩn danh / danh sách gói về máy chủ VGS (Dem.kt), không hiện các nút mở vgs.zone (góp ý,
        // cộng đồng VGS): bản này không phải của VGS, góp ý gửi về đó VGS không xử lý được.
        buildConfigField("boolean", "GUI_SO_LIEU", "false")
        buildConfigField("boolean", "LIEN_KET_VGS", "false")
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
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("vgs") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    // APK launcher đóng sẵn: không nén lại để đọc nhanh.
    androidResources { noCompress += "apk" }
    packaging {
        resources { excludes += setOf("META-INF/versions/9/OSGI-INF/MANIFEST.MF", "META-INF/DEPENDENCIES") }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")

    // ADB qua Wi-Fi (TCP 5555 và ghép đôi Wireless debugging).
    implementation("com.github.MuntashirAkon:libadb-android:3.1.1")
    implementation("org.conscrypt:conscrypt-android:2.5.3")
    // Tạo chứng chỉ X.509 cho khoá ADB (cùng dòng jdk15to18 mà libadb đã kéo về).
    implementation("org.bouncycastle:bcpkix-jdk15to18:1.81")
    // Tivi LG webOS (25/10/2026): WebSocket SSAP cổng 3000 / 3001; SSH root vào tivi LG đã root (Homebrew Channel).
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.github.mwiede:jsch:0.2.25")
    // Mã QR cài mã OTP Khoá trẻ em (28/10/2026): Project Nayuki, MIT, Java thuần ~40 KB.
    implementation("io.nayuki:qrcodegen:1.8.0")
}
