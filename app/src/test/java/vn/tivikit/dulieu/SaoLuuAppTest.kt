package vn.tivikit.dulieu

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Sao lưu dữ liệu TiviKit ra file zip và nhập trên máy khác (gộp, không ghi đè bừa, không ra ngoài thư mục). */
class SaoLuuAppTest {
    @get:Rule val tam = TemporaryFolder()

    private fun sl(khoa: String, luc: Long, daTat: Set<String>, ip: String = "192.168.1.20") = BanSaoLuu(
        khoa = khoa, ten = "TCL 55C645", ip = ip, luc = luc, daTatTruoc = setOf("x.da.tat"), homeTruoc = "com.tcl.home/.Main",
        dnsModeTruoc = "opportunistic", dnsHostTruoc = "null", truyCapTruoc = "null", getprop = "", goiTruoc = listOf("a"),
        daTatBoiApp = daTat.toCollection(linkedSetOf()),
    )

    private fun mayCu(): File = tam.newFolder("cu").also { goc ->
        File(goc, "sao-luu").mkdirs()
        File(goc, "sao-luu/tcl-1.json").writeText(sl("tcl-1", 100, setOf("com.tcl.ad")).toJson().toString())
        File(goc, "sao-luu/sony-2.json").writeText(sl("sony-2", 200, setOf("com.sony.ad")).toJson().toString())
        KhoBoCai(goc).also { kho ->
            File(kho.thuMucBo("bo1"), "smarttube.apk").writeBytes(byteArrayOf(1, 2, 3))
            kho.luu(BoCai("bo1", "Nhà khách", listOf(MucBoCai.tuJson(JSONObject().put("ten", "SmartTube").put("file", "smarttube.apk")))))
        }
        File(goc, "adb").mkdirs()
        File(goc, "adb/khoa.der").writeBytes(byteArrayOf(9, 9))
        File(goc, "adb/cert.der").writeBytes(byteArrayOf(8, 8))
    }

    private fun dongGoi(goc: File, kemKhoa: Boolean): File {
        val f = File(tam.root, "sao-luu-${System.nanoTime()}.zip")
        f.outputStream().use { SaoLuuApp.dongGoi(goc, JSONObject().put("tivi_gan_day", JSONArray().put(JSONObject().put("ip", "192.168.1.20"))), kemKhoa, "1.0", it) }
        return f
    }

    @Test fun luuRoiNhapSangMayMoi() {
        val zip = dongGoi(mayCu(), kemKhoa = false)
        val nd = SaoLuuApp.xem(zip)
        assertEquals(2, nd.soSaoLuu); assertEquals(1, nd.soBoCai); assertFalse(nd.coKhoa)
        val moi = tam.newFolder("moi")
        val kq = SaoLuuApp.nhap(zip, moi, dungKhoa = true)
        assertEquals(2, kq.saoLuuMoi); assertEquals(0, kq.saoLuuGop); assertEquals(1, kq.boCaiMoi)
        assertEquals(setOf("tcl-1", "sony-2"), KhoSaoLuuFile(moi).khoa())
        val bo = KhoBoCai(moi).tatCa().single()
        assertEquals("Nhà khách", bo.ten)
        assertArrayEquals(byteArrayOf(1, 2, 3), File(KhoBoCai(moi).thuMucBo("bo1"), "smarttube.apk").readBytes())
        assertEquals("192.168.1.20", kq.caiDat!!.getJSONArray("tivi_gan_day").getJSONObject(0).getString("ip"))
        assertFalse(kq.daNhapKhoa); assertFalse(File(moi, "adb/khoa.der").exists())
    }

    @Test fun khoaChiNhapKhiDongY() {
        val zip = dongGoi(mayCu(), kemKhoa = true)
        assertTrue(SaoLuuApp.xem(zip).coKhoa)
        val khong = tam.newFolder("khong")
        assertFalse(SaoLuuApp.nhap(zip, khong, dungKhoa = false).daNhapKhoa)
        assertFalse(File(khong, "adb/khoa.der").exists())
        val co = tam.newFolder("co")
        assertTrue(SaoLuuApp.nhap(zip, co, dungKhoa = true).daNhapKhoa)
        assertArrayEquals(byteArrayOf(9, 9), File(co, "adb/khoa.der").readBytes())
    }

    /** Máy mới đã dọn lại chính tivi đó: giữ ảnh "trước khi dọn" của bản cũ hơn, cộng gói đã tắt của cả hai. */
    @Test fun gopBanSaoLuuCungTivi() {
        val zip = dongGoi(mayCu(), kemKhoa = false)
        val moi = tam.newFolder("moi2")
        File(moi, "sao-luu").mkdirs()
        File(moi, "sao-luu/tcl-1.json").writeText(sl("tcl-1", 500, setOf("com.tcl.tracker"), ip = "192.168.1.99").toJson().toString())
        val kq = SaoLuuApp.nhap(zip, moi, dungKhoa = false)
        assertEquals(1, kq.saoLuuMoi); assertEquals(1, kq.saoLuuGop)
        val b = BanSaoLuu.tuJson(JSONObject(File(moi, "sao-luu/tcl-1.json").readText()))
        assertEquals(100L, b.luc)
        assertEquals(setOf("com.tcl.ad", "com.tcl.tracker"), b.daTatBoiApp)
        assertEquals("192.168.1.99", b.ip)
    }

    @Test fun boCaiTrungGiuBoDangCo() {
        val zip = dongGoi(mayCu(), kemKhoa = false)
        val moi = tam.newFolder("moi3")
        KhoBoCai(moi).luu(BoCai("bo1", "Bộ của máy mới", emptyList()))
        val kq = SaoLuuApp.nhap(zip, moi, dungKhoa = false)
        assertEquals(0, kq.boCaiMoi); assertEquals(1, kq.boCaiTrung)
        assertEquals("Bộ của máy mới", KhoBoCai(moi).tatCa().single().ten)
    }

    @Test fun fileLaVaTenNguyHiem() {
        val la = File(tam.root, "la.zip")
        ZipOutputStream(la.outputStream()).use { z -> z.putNextEntry(ZipEntry("a.txt")); z.write(1); z.closeEntry() }
        assertTrue(runCatching { SaoLuuApp.xem(la) }.isFailure)
        // Bản sao lưu hợp lệ nhưng có mục tên "../": bỏ qua, không ghi ra ngoài thư mục bộ cài.
        val xau = File(tam.root, "xau.zip")
        ZipOutputStream(xau.outputStream()).use { z ->
            fun chu(ten: String, s: String) { z.putNextEntry(ZipEntry(ten)); z.write(s.toByteArray()); z.closeEntry() }
            chu("manifest.json", JSONObject().put("loai", SaoLuuApp.LOAI).toString())
            chu("bo-cai/ds.json", JSONArray().put(JSONObject().put("id", "bo9").put("ten", "x").put("apps", JSONArray())).toString())
            chu("bo-cai/bo9/../../tron.txt", "x")
            chu("sao-luu/../../tron.json", sl("../../tron", 1, emptySet()).toJson().toString())
        }
        val moi = tam.newFolder("moi4")
        val kq = SaoLuuApp.nhap(xau, moi, dungKhoa = true)
        assertEquals(0, kq.saoLuuMoi); assertEquals(1, kq.boCaiMoi)
        assertFalse(File(tam.root, "tron.txt").exists()); assertFalse(File(moi, "tron.txt").exists())
        assertFalse(File(moi.parentFile, "tron.json").exists())
    }

    /** Liệt kê khoá các bản sao lưu trong thư mục (KhoSaoLuu cần Context). */
    private class KhoSaoLuuFile(goc: File) {
        private val thuMuc = File(goc, "sao-luu")
        fun khoa(): Set<String> = thuMuc.listFiles { f -> f.name.endsWith(".json") }.orEmpty().map { it.name.removeSuffix(".json") }.toSet()
    }
}
