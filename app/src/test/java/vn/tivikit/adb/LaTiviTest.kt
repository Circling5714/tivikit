package vn.tivikit.adb

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Chặn dọn nhầm điện thoại (CleanTV 2.10.12): máy không khai leanback / television thì không phải tivi. */
class LaTiviTest {
    @Test fun androidTv_khaiLeanback() = assertTrue(ThaoTacTv.laTiviTheoFeatures(
        "feature:android.hardware.wifi\nfeature:android.software.leanback\nfeature:android.software.leanback_only\n"))

    @Test fun mayChieu_khaiTelevision() = assertTrue(ThaoTacTv.laTiviTheoFeatures(
        "feature:android.hardware.wifi\nfeature:android.hardware.type.television\n"))

    @Test fun dienThoai_khongPhaiTivi() = assertFalse(ThaoTacTv.laTiviTheoFeatures(
        "feature:android.hardware.telephony\nfeature:android.hardware.touchscreen\nfeature:android.software.home_screen\n"))

    /** Lệnh lỗi / không đọc được: coi như tivi, không chặn nhầm. */
    @Test fun khongDocDuoc_coiNhuTivi() {
        assertTrue(ThaoTacTv.laTiviTheoFeatures(""))
        assertTrue(ThaoTacTv.laTiviTheoFeatures("Error: Can't find service: package"))
    }
}
