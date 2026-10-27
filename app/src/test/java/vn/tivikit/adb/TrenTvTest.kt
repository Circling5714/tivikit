package vn.tivikit.adb

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Base64

/** Đọc đầu ra của các lớp chạy trên tivi (module trentv, Chu.thoat). */
class TrenTvTest {
    @Test fun docTen_boThoatVaBoDongSai() {
        val out = "com.android.tv.settings\t1\tC\\u00e0i \\u0111\\u1eb7t\n" +
            "org.smarttube.stable\t2458\tSmartTube\n" +
            "loi\tjava.lang.SecurityException\n" +
            "x;reboot\t1\tHack\n" +
            "com.example.thieu\tkhongphaiso\tA\n" +
            "com.plexapp.android\t856254864\tPlex\r\n"
        val m = TrenTv.docTen(out)
        assertEquals(TrenTv.TenTv(1, "Cài đặt"), m["com.android.tv.settings"])
        assertEquals(TrenTv.TenTv(856254864, "Plex"), m["com.plexapp.android"])
        assertEquals(setOf("com.android.tv.settings", "org.smarttube.stable", "com.plexapp.android"), m.keys)
    }

    @Test fun docAnh_bannerHayBieuTuong() {
        val webp = byteArrayOf(82, 73, 70, 70, 1, 2, 3)
        val b64 = Base64.getEncoder().encodeToString(webp)
        val out = "org.smarttube.stable\t2458\tB\t$b64\ncom.phlox.tvwebbrowser\t69\tI\t$b64\ncom.x.y\t1\tZ\t$b64\ncom.a.b\t1\tB\t!!\n"
        val m = TrenTv.docAnh(out)
        assertEquals(setOf("org.smarttube.stable", "com.phlox.tvwebbrowser"), m.keys)
        assertTrue(m.getValue("org.smarttube.stable").banner)
        assertFalse(m.getValue("com.phlox.tvwebbrowser").banner)
        assertArrayEquals(webp, m.getValue("com.phlox.tvwebbrowser").webp)
    }

    @Test fun boThoat_nguocCuaChuThoat() {
        assertEquals("a\tb\nc\\d Sơn 😀", TrenTv.boThoat("a\\tb\\nc\\\\d S\\u01a1n \\ud83d\\ude00"))
        assertEquals("SmartTube", TrenTv.boThoat("SmartTube"))
        // Thoát dở ở cuối chuỗi: giữ nguyên, không ném lỗi.
        assertEquals("abc\\", TrenTv.boThoat("abc\\"))
        assertEquals("x\\u12", TrenTv.boThoat("x\\u12"))
    }

    @Test fun lenh_toanAscii() {
        val l = TrenTv.lenh("TenApp ten")
        assertEquals("CLASSPATH=/data/local/tmp/tivikit-tren-tv.jar app_process / vn.tivikit.trentv.TenApp ten", l)
        assertTrue(l.all { it.code < 0x80 })
    }

    /** File chạy trên tivi có trong assets và nhỏ (scripts/dung-tren-tv.sh). */
    @Test fun jarTrongAssets() = assertTrue(File("src/main/assets/${TrenTv.ASSET}").length() in 1..100_000)
}
