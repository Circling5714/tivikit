package vn.tivikit.teleremote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.DataInputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.security.interfaces.RSAPublicKey
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import vn.tivikit.teleremote.PhienAdb.Companion.AUTH
import vn.tivikit.teleremote.PhienAdb.Companion.CLSE
import vn.tivikit.teleremote.PhienAdb.Companion.CNXN
import vn.tivikit.teleremote.PhienAdb.Companion.OKAY
import vn.tivikit.teleremote.PhienAdb.Companion.OPEN
import vn.tivikit.teleremote.PhienAdb.Companion.STLS
import vn.tivikit.teleremote.PhienAdb.Companion.WRTE

class PhienAdbTest {
    @get:Rule
    val tam = TemporaryFolder()

    @Test
    fun khoaRiengCuaMapVoice() {
        val thuMuc = tam.newFolder()
        val a = PhienAdb.khoa(thuMuc)
        assertEquals((a.public as RSAPublicKey).modulus, (PhienAdb.khoa(thuMuc).public as RSAPublicKey).modulus)
        assertTrue(String(PhienAdb.khoaCongKhai(a.public as RSAPublicKey)).endsWith(" tivikit-teleremote@tivi\u0000"))
    }

    @Test
    fun luongLogcatVaLenhNganCungMotKetNoi() {
        val thuMuc = tam.newFolder()
        val moLogcat = AtomicReference<String>()
        val moLenh = AtomicReference<String>()
        val gia = AdbdGia { vao, ra ->
            assertEquals(CNXN, doc(vao).lenh)
            gui(ra, CNXN, 0x01000001, 4096, "device::\u0000".toByteArray()) // tivi không đòi xác thực
            val mo = doc(vao)
            assertEquals(OPEN, mo.lenh)
            moLogcat.set(String(mo.du, Charsets.UTF_8))
            gui(ra, OKAY, 7, mo.a0, ByteArray(0))
            // Một câu chia hai gói, cắt giữa chữ "ạ" (3 byte UTF-8).
            val cau = "I/k( 1): setFinalRecognizedText(mở nhạc piano)\n".toByteArray(Charsets.UTF_8)
            val cat = cau.indexOf(0xe1.toByte()) + 1
            gui(ra, WRTE, 7, mo.a0, cau.copyOfRange(0, cat))
            assertEquals(OKAY, doc(vao).lenh)
            gui(ra, WRTE, 7, mo.a0, cau.copyOfRange(cat, cau.size))
            assertEquals(OKAY, doc(vao).lenh)
            // Lệnh ngắn trên luồng thứ hai, trong lúc logcat vẫn chạy.
            val mo2 = doc(vao)
            assertEquals(OPEN, mo2.lenh)
            moLenh.set(String(mo2.du, Charsets.UTF_8))
            gui(ra, OKAY, 8, mo2.a0, ByteArray(0))
            gui(ra, WRTE, 8, mo2.a0, "xong\n".toByteArray())
            assertEquals(OKAY, doc(vao).lenh)
            gui(ra, CLSE, 8, mo2.a0, ByteArray(0))
            val dong2 = doc(vao)
            assertEquals(CLSE, dong2.lenh)
            assertEquals(mo2.a0, dong2.a0)
            assertEquals(8, dong2.a1)
            // logcat thoát.
            gui(ra, CLSE, 7, mo.a0, ByteArray(0))
            assertEquals(CLSE, doc(vao).lenh)
        }
        val p = PhienAdb(thuMuc, cong = gia.cong)
        assertNull(p.chay("echo"))
        assertEquals(PhienAdb.KetQua.DA_NOI, p.noi())
        val dong = Collections.synchronizedList(mutableListOf<String>())
        val coCau = CountDownLatch(1)
        val het = CountDownLatch(1)
        p.mo("logcat -e x", { dong += it; coCau.countDown() }, het = { het.countDown() })
        val doc = thread { p.docMai() }
        assertTrue(coCau.await(5, TimeUnit.SECONDS))
        assertEquals("xong\n", p.chay("am force-stop com.google.android.katniss"))
        assertTrue(het.await(5, TimeUnit.SECONDS))
        p.close()
        doc.join(5000)
        gia.xong()
        assertEquals(listOf("I/k( 1): setFinalRecognizedText(mở nhạc piano)"), dong.toList())
        assertEquals("shell:logcat -e x\u0000", moLogcat.get())
        assertEquals("shell:am force-stop com.google.android.katniss\u0000", moLenh.get())
        assertNull(p.chay("echo")) // đã đóng
    }

    @Test
    fun matKetNoiThiDocMaiTraVeVaBaoHetLuong() {
        val thuMuc = tam.newFolder()
        val gia = AdbdGia { vao, ra ->
            doc(vao)
            gui(ra, CNXN, 0x01000001, 4096, "device::\u0000".toByteArray())
            gui(ra, OKAY, 7, doc(vao).a0, ByteArray(0))
            // rồi adbd khởi động lại: mất kết nối
        }
        val p = PhienAdb(thuMuc, cong = gia.cong)
        assertEquals(PhienAdb.KetQua.DA_NOI, p.noi())
        val het = CountDownLatch(1)
        p.mo("logcat", {}, het = { het.countDown() })
        p.docMai()
        assertTrue(het.await(1, TimeUnit.SECONDS))
        p.close()
        gia.xong()
    }

    @Test
    fun khoaMoiThiHoiRoiChoNguoiDung() {
        val thuMuc = tam.newFolder()
        val gia = AdbdGia { vao, ra ->
            doc(vao)
            gui(ra, AUTH, 1, 0, ByteArray(20))
            assertEquals(2, doc(vao).a0) // chữ ký: coi như khoá lạ
            gui(ra, AUTH, 1, 0, ByteArray(20) { 9 })
            assertEquals(3, doc(vao).a0) // khoá công khai
            Thread.sleep(200)
            gui(ra, CNXN, 0x01000001, 4096, "device::\u0000".toByteArray())
        }
        var hoi = 0
        val p = PhienAdb(thuMuc, cong = gia.cong)
        assertEquals(PhienAdb.KetQua.DA_NOI, p.noi { hoi++ })
        p.close()
        gia.xong()
        assertEquals(1, hoi)
    }

    @Test
    fun nguoiDungKhongChoPhep() {
        val thuMuc = tam.newFolder()
        val gia = AdbdGia { vao, ra ->
            doc(vao)
            gui(ra, AUTH, 1, 0, ByteArray(20))
            doc(vao)
            gui(ra, AUTH, 1, 0, ByteArray(20))
            doc(vao)
            Thread.sleep(1000) // không ai bấm OK
        }
        val p = PhienAdb(thuMuc, cong = gia.cong)
        assertEquals(PhienAdb.KetQua.CHUA_CHO_PHEP, p.noi(choToiDa = 300))
        p.close()
        gia.xong()
    }

    @Test
    fun tiviKhongMoAdbHayChiCoTls() {
        val thuMuc = tam.newFolder()
        val dong = ServerSocket(0).use { it.localPort }
        PhienAdb(thuMuc, cong = dong).use { assertEquals(PhienAdb.KetQua.KHONG_CO, it.noi()) }
        val tls = AdbdGia { vao, ra ->
            doc(vao)
            gui(ra, STLS, 0x01000000, 0, ByteArray(0))
        }
        PhienAdb(thuMuc, cong = tls.cong).use { assertEquals(PhienAdb.KetQua.KHONG_CO, it.noi()) }
        tls.xong()
    }

    /** adbd giả nhận một kết nối ở cổng ngẫu nhiên; kịch bản chạy xong thì đóng kết nối. */
    private class AdbdGia(kichBan: (DataInputStream, OutputStream) -> Unit) {
        private val ss = ServerSocket(0)
        private val loi = AtomicReference<Throwable?>()
        val cong = ss.localPort
        private val luong = thread(isDaemon = true) {
            ss.use { s ->
                s.accept().use { c -> runCatching { kichBan(DataInputStream(c.getInputStream()), c.getOutputStream()) }.onFailure(loi::set) }
            }
        }

        /** Đợi kịch bản xong; lỗi bên adbd giả (kể cả assert) thì ném lại ở luồng test. */
        fun xong() {
            luong.join(5000)
            loi.get()?.let { throw it }
        }
    }

    private fun doc(vao: DataInputStream) = PhienAdb.docGoi(vao)

    private fun gui(ra: OutputStream, lenh: Int, a0: Int, a1: Int, du: ByteArray) {
        ra.write(PhienAdb.maGoi(lenh, a0, a1, du))
        ra.flush()
    }
}
