package vn.tivikit

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Tự cập nhật từ GitHub Releases (26/10/2026): đọc cap-nhat.json, kiểm tra file tải về, số phiên bản. */
class CapNhatTest {
    private val apk = "TiviKit thử".toByteArray()
    private val sha = vn.tivikit.dulieu.sha256(apk)

    private fun json(code: Int, sha256: String = sha, link: String = "https://github.com/${CapNhat.KHO}/releases/download/v1.1.1/TiviKit-1.1.1.apk") =
        JSONObject("""{"app":{"versionCode":$code,"versionName":"1.1.1","apk":"$link","co":${apk.size},"sha256":"$sha256","ghiChu":"- Sửa lỗi A\n- Thêm B","ghiChuEn":""}}""")

    @Test fun banMoiHon_coSha_thiNhan() {
        val b = CapNhat.docBanMoi(json(10101), 10100)!!
        assertEquals(10101, b.versionCode)
        assertEquals("1.1.1", b.versionName)
        assertEquals(apk.size.toLong(), b.co)
        assertEquals(sha, b.sha256)
        assertTrue(b.ghiChu.contains("Sửa lỗi A"))
    }

    @Test fun banKhongMoiHon_hoacThieuSha_thiBo() {
        assertNull(CapNhat.docBanMoi(json(10100), 10100))
        assertNull(CapNhat.docBanMoi(json(10101, sha256 = ""), 10100))
        assertNull(CapNhat.docBanMoi(json(10101, sha256 = "abc"), 10100))
        assertNull(CapNhat.docBanMoi(JSONObject("{}"), 10100))
    }

    @Test fun linkTuongDoi_ghepVoiBanMoiNhat() {
        assertEquals("${CapNhat.GOC}/TiviKit-1.1.1.apk", CapNhat.docBanMoi(json(10101, link = "TiviKit-1.1.1.apk"), 10100)!!.apk)
    }

    @Test fun fileTaiVe_phaiDungCoVaSha() {
        val b = CapNhat.docBanMoi(json(10101), 10100)!!
        assertTrue(CapNhat.hopLe(apk, b))
        assertFalse(CapNhat.hopLe(apk + byteArrayOf(0), b))
        // Cùng cỡ, khác nội dung (file bị tráo / hỏng).
        assertFalse(CapNhat.hopLe(apk.copyOf().also { it[0] = 'X'.code.toByte() }, b))
    }

    /** Số phiên bản trong app lấy từ version.properties: versionCode = lớn×10000 + tính năng×100 + vá, cao hơn 100 của TiviKit 1.0. */
    @Test fun soPhienBanTuVersionProperties() {
        val ten = java.util.Properties().apply { File("../version.properties").inputStream().use { load(it) } }.getProperty("VERSION_NAME")
        assertEquals(ten, BuildConfig.VERSION_NAME)
        val (lon, tinhNang, va) = ten.split('.').map { it.toInt() }
        assertEquals(lon * 10000 + tinhNang * 100 + va, BuildConfig.VERSION_CODE)
        assertTrue(BuildConfig.VERSION_CODE > 100)
    }
}
