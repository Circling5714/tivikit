package vn.tivikit.adb

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer

/** Giao thức scrcpy 5.0.1 phần TiviKit dùng (doc/develop.md, ControlMessageReader.java của bản đó). */
class GiaoThucScrcpyTest {
    @Test fun tenSocket_8ChuSoHex() {
        assertEquals("scrcpy_1a2b3c4d", GiaoThucScrcpy.tenSocket(0x1a2b3c4d))
        assertEquals("scrcpy_00000005", GiaoThucScrcpy.tenSocket(5))
    }

    /** Thử trên TX3 Mini: thiếu cleanup=false thì scrcpy tự xoá file máy chủ, lần sau "Aborted". */
    @Test fun lenhChay_dungBanVaTuyChon() {
        val l = GiaoThucScrcpy.lenhChay(0x1a2b3c4d, 1280, 4_000_000, 30)
        assertTrue(l.startsWith("CLASSPATH=${GiaoThucScrcpy.DUONG_DAN} app_process / com.genymobile.scrcpy.Server ${GiaoThucScrcpy.BAN} scid=1a2b3c4d "))
        listOf("tunnel_forward=true", "audio=false", "control=true", "video_codec=h264", "max_size=1280", "video_bit_rate=4000000",
            "max_fps=30", "power_on=false", "cleanup=false", "clipboard_autosync=false").forEach { assertTrue(it, l.contains(" $it")) }
    }

    /** Thử trên TX3 Mini: không hình, không tiếng, không tên máy thì luồng điều khiển nhận byte giả 0 rồi dùng được ngay. */
    @Test fun lenhChayDieuKhien_khongHinhKhongTieng() {
        val l = GiaoThucScrcpy.lenhChayDieuKhien(0x1a2b3c4d)
        assertTrue(l.startsWith("CLASSPATH=${GiaoThucScrcpy.DUONG_DAN} app_process / com.genymobile.scrcpy.Server ${GiaoThucScrcpy.BAN} scid=1a2b3c4d "))
        listOf("tunnel_forward=true", "video=false", "audio=false", "control=true", "send_device_meta=false", "power_on=false",
            "cleanup=false", "clipboard_autosync=false").forEach { assertTrue(it, l.contains(" $it")) }
        // Lệnh qua libadb phải toàn ASCII (chữ có dấu làm hỏng lệnh, xem TiviKitLauncher.chiAscii).
        assertTrue(l.all { it.code < 0x80 })
    }

    @Test fun goiClipboard_soThuTuDanVaChuUtf8() {
        val chu = "Sơn Tùng – Chúng ta"
        val raw = chu.toByteArray(Charsets.UTF_8)
        val b = ByteBuffer.wrap(GiaoThucScrcpy.goiClipboard(42, dan = true, chu = chu))
        assertEquals(14 + raw.size, b.capacity())
        assertEquals(9, b.get(0).toInt())
        assertEquals(42L, b.getLong(1))
        assertEquals(1, b.get(9).toInt())
        assertEquals(raw.size, b.getInt(10))
        assertArrayEquals(raw, b.array().copyOfRange(14, b.capacity()))
        assertEquals(0, GiaoThucScrcpy.goiClipboard(7, dan = false, chu = "a")[9].toInt())
    }

    @Test fun dauGoi_phien() {
        val b = ByteBuffer.allocate(12).put(0x80.toByte()).put(ByteArray(3)).putInt(1280).putInt(720).array()
        assertEquals(GiaoThucScrcpy.Dau.Phien(1280, 720), GiaoThucScrcpy.docDau(b))
    }

    @Test fun dauGoi_cauHinhVaKhungKhoa() {
        val cauHinh = ByteBuffer.allocate(12).putLong(1L shl 62).putInt(21).array()
        assertEquals(GiaoThucScrcpy.Dau.Goi(cauHinh = true, khoa = false, pts = 0, co = 21), GiaoThucScrcpy.docDau(cauHinh))
        val khoa = ByteBuffer.allocate(12).putLong((1L shl 61) or 123_456L).putInt(15_574).array()
        assertEquals(GiaoThucScrcpy.Dau.Goi(cauHinh = false, khoa = true, pts = 123_456, co = 15_574), GiaoThucScrcpy.docDau(khoa))
        val thuong = ByteBuffer.allocate(12).putLong(200_000L).putInt(8_362).array()
        assertEquals(GiaoThucScrcpy.Dau.Goi(cauHinh = false, khoa = false, pts = 200_000, co = 8_362), GiaoThucScrcpy.docDau(thuong))
    }

    @Test fun goiPhim_14Byte() {
        assertArrayEquals(byteArrayOf(0, 0, 0, 0, 0, 19, 0, 0, 0, 0, 0, 0, 0, 0), GiaoThucScrcpy.goiPhim(GiaoThucScrcpy.NHAN, PhimTv.LEN))
        assertArrayEquals(byteArrayOf(0, 1, 0, 0, 0, 23, 0, 0, 0, 0, 0, 0, 0, 0), GiaoThucScrcpy.goiPhim(GiaoThucScrcpy.NHA, PhimTv.OK))
    }

    @Test fun goiCham_32Byte() {
        val nhan = ByteBuffer.wrap(GiaoThucScrcpy.goiCham(GiaoThucScrcpy.NHAN, 640, 360, 1280, 720))
        assertEquals(32, nhan.capacity())
        assertEquals(2, nhan.get(0).toInt())
        assertEquals(GiaoThucScrcpy.NHAN, nhan.get(1).toInt())
        assertEquals(-2L, nhan.getLong(2))
        assertEquals(640, nhan.getInt(10)); assertEquals(360, nhan.getInt(14))
        assertEquals(1280, nhan.getShort(18).toInt()); assertEquals(720, nhan.getShort(20).toInt())
        assertEquals(0xFFFF, nhan.getShort(22).toInt() and 0xFFFF)
        assertEquals(0, nhan.getInt(24)); assertEquals(0, nhan.getInt(28))
        val nha = ByteBuffer.wrap(GiaoThucScrcpy.goiCham(GiaoThucScrcpy.NHA, 640, 360, 1280, 720))
        assertEquals(GiaoThucScrcpy.NHA, nha.get(1).toInt())
        assertEquals(0, nha.getShort(22).toInt())
    }

    @Test fun tenMay_boDem() {
        val b = ByteArray(64); "TX3 Mini".toByteArray().copyInto(b)
        assertEquals("TX3 Mini", GiaoThucScrcpy.docTenMay(b))
    }

    /** Cỡ máy chủ trong assets khớp hằng số: tivi có file đúng cỡ thì app khỏi đẩy lại. */
    @Test fun mayChuTrongAssets_dungCo() =
        assertEquals(GiaoThucScrcpy.CO_MAY_CHU, File("src/main/assets/${GiaoThucScrcpy.ASSET}").length())
}
