package vn.tivikit.launcher

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.DataInputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.math.BigInteger
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAPrivateKeySpec
import java.util.Base64
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class AdbNoiBoTest {
    @get:Rule
    val tam = TemporaryFolder()

    private val CNXN = 0x4e584e43
    private val AUTH = 0x48545541
    private val OPEN = 0x4e45504f
    private val OKAY = 0x59414b4f
    private val WRTE = 0x45545257
    private val CLSE = 0x45534c43
    private val STLS = 0x534c5453

    @Test
    fun khoaLuuLaiVaDungLai() {
        val thuMuc = tam.newFolder()
        val a = AdbNoiBo.khoa(thuMuc)
        val b = AdbNoiBo.khoa(thuMuc)
        assertEquals((a.public as RSAPublicKey).modulus, (b.public as RSAPublicKey).modulus)
        assertEquals(2048, (a.public as RSAPublicKey).modulus.bitLength())
        // File hỏng thì tạo khoá mới thay vì lỗi mãi.
        File(thuMuc, "adbkey").writeText("hỏng")
        val c = AdbNoiBo.khoa(thuMuc)
        assertTrue((c.public as RSAPublicKey).modulus != (a.public as RSAPublicKey).modulus)
        assertEquals((c.public as RSAPublicKey).modulus, (AdbNoiBo.khoa(thuMuc).public as RSAPublicKey).modulus)
    }

    @Test
    fun khoaKhongKemThamSoCrtVanDungLai() {
        // Conscrypt trên tivi đọc PKCS#8 ra RSAPrivateKey (không CRT): trước đây đọc hỏng nên mỗi lần một khoá mới (TX3).
        val goc = AdbNoiBo.khoa(tam.newFolder())
        val crt = goc.private as RSAPrivateCrtKey
        val khongCrt = KeyFactory.getInstance("RSA").generatePrivate(RSAPrivateKeySpec(crt.modulus, crt.privateExponent))
        assertFalse(khongCrt is RSAPrivateCrtKey)
        val thuMuc = tam.newFolder()
        val f = File(thuMuc, "adbkey").apply { writeBytes(khongCrt.encoded) }
        val truoc = f.readBytes()
        val k = AdbNoiBo.khoa(thuMuc)
        assertArrayEquals(truoc, f.readBytes())
        assertEquals(goc.public, k.public)
        val token = ByteArray(20) { it.toByte() }
        assertTrue(moChuKy(k.public as RSAPublicKey, AdbNoiBo.ky(k, token)).takeLast(20).toByteArray().contentEquals(token))
    }

    @Test
    fun khoaCongKhaiDungCauTrucCuaAndroid() {
        val k = AdbNoiBo.khoa(tam.newFolder()).public as RSAPublicKey
        val chu = String(AdbNoiBo.khoaCongKhai(k))
        assertTrue(chu.endsWith(" tivikit-launcher@tivi\u0000"))
        val b = ByteBuffer.wrap(Base64.getDecoder().decode(chu.substringBefore(' '))).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(524, b.remaining())
        assertEquals(64, b.int)
        val n0inv = b.int.toLong() and 0xffffffffL
        val n = docSo(b)
        val rr = docSo(b)
        assertEquals(k.modulus, n)
        // n0inv · n[0] ≡ −1 (mod 2^32), rr = R² mod n với R = 2^2048: thứ adbd dùng cho phép nhân Montgomery.
        assertEquals(0xffffffffL, (n0inv * (n.toLong() and 0xffffffffL)) and 0xffffffffL)
        assertEquals(BigInteger.ONE.shiftLeft(4096).mod(n), rr)
        assertEquals(65537, b.int)
    }

    @Test
    fun chuKyLaPkcs1Sha1CuaToken() {
        val k = AdbNoiBo.khoa(tam.newFolder())
        val token = ByteArray(20) { (it * 7 + 3).toByte() }
        val mo = moChuKy(k.public as RSAPublicKey, AdbNoiBo.ky(k, token))
        val dau = byteArrayOf(0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14)
        val mong = byteArrayOf(0, 1) + ByteArray(256 - 3 - dau.size - token.size) { -1 } + byteArrayOf(0) + dau + token
        assertArrayEquals(mong, mo)
    }

    @Test
    fun goiMaRoiDocLai() {
        val du = "host::\u0000".toByteArray()
        val g = AdbNoiBo.docGoi(DataInputStream(AdbNoiBo.maGoi(CNXN, 0x01000001, 4096, du).inputStream()))
        assertEquals(CNXN, g.lenh)
        assertEquals(0x01000001, g.a0)
        assertEquals(4096, g.a1)
        assertArrayEquals(du, g.du)
        val hong = AdbNoiBo.maGoi(OKAY, 1, 2, ByteArray(0)).also { it[20] = 0 }
        assertTrue(runCatching { AdbNoiBo.docGoi(DataInputStream(hong.inputStream())) }.exceptionOrNull() is IOException)
    }

    @Test
    fun khoaDaDuocChoPhepThiGuiLenhNgay() {
        val thuMuc = tam.newFolder()
        val k = AdbNoiBo.khoa(thuMuc).public as RSAPublicKey
        val lenh = AtomicReference<String>()
        val gia = AdbdGia { vao, ra ->
            assertEquals(CNXN, doc(vao).lenh)
            val token = ByteArray(20) { it.toByte() }
            gui(ra, AUTH, 1, 0, token)
            val ky = doc(vao)
            assertEquals(AUTH, ky.lenh)
            assertEquals(2, ky.a0)
            assertTrue(moChuKy(k, ky.du).takeLast(20).toByteArray().contentEquals(token))
            gui(ra, CNXN, 0x01000001, 4096, "device::\u0000".toByteArray())
            val mo = doc(vao)
            assertEquals(OPEN, mo.lenh)
            lenh.set(String(mo.du))
            gui(ra, OKAY, 7, mo.a0, ByteArray(0))
        }
        var hoi = 0
        assertEquals(AdbNoiBo.KetQua.DA_GUI, AdbNoiBo.chay(thuMuc, "shell:reboot", { hoi++ }, cong = gia.cong))
        gia.xong()
        assertEquals("shell:reboot\u0000", lenh.get())
        assertEquals(0, hoi)
    }

    @Test
    fun khoaMoiThiGuiKhoaCongKhaiVaChoNguoiDungChoPhep() {
        val thuMuc = tam.newFolder()
        val k = AdbNoiBo.khoa(thuMuc).public as RSAPublicKey
        val gia = AdbdGia { vao, ra ->
            doc(vao)
            gui(ra, AUTH, 1, 0, ByteArray(20))
            doc(vao) // chữ ký: coi như khoá lạ
            gui(ra, AUTH, 1, 0, ByteArray(20) { 9 })
            val khoa = doc(vao)
            assertEquals(3, khoa.a0)
            assertArrayEquals(AdbNoiBo.khoaCongKhai(k), khoa.du)
            Thread.sleep(300) // người dùng đang đọc hộp hỏi
            gui(ra, CNXN, 0x01000001, 4096, "device::\u0000".toByteArray())
            val mo = doc(vao)
            gui(ra, OKAY, 7, mo.a0, ByteArray(0))
        }
        var hoi = 0
        assertEquals(AdbNoiBo.KetQua.DA_GUI, AdbNoiBo.chay(thuMuc, "shell:reboot", { hoi++ }, cong = gia.cong))
        gia.xong()
        assertEquals(1, hoi)
    }

    @Test
    fun adbdHoiLaiThiGuiLaiKhoaCongKhai() {
        // adbd của Android 9 nhận khoá lúc phần hiện hộp hỏi chưa nối ("Client not connected", thấy trên TX3): nối được
        // thì gửi token mới, adb trả lời bằng khoá công khai lần nữa.
        val thuMuc = tam.newFolder()
        val k = AdbNoiBo.khoa(thuMuc).public as RSAPublicKey
        val gia = AdbdGia { vao, ra ->
            doc(vao)
            gui(ra, AUTH, 1, 0, ByteArray(20))
            doc(vao)
            gui(ra, AUTH, 1, 0, ByteArray(20))
            assertEquals(3, doc(vao).a0)
            gui(ra, AUTH, 1, 0, ByteArray(20) { 5 })
            val lai = doc(vao)
            assertEquals(3, lai.a0)
            assertArrayEquals(AdbNoiBo.khoaCongKhai(k), lai.du)
            gui(ra, CNXN, 0x01000001, 4096, "device::\u0000".toByteArray())
            val mo = doc(vao)
            gui(ra, OKAY, 7, mo.a0, ByteArray(0))
        }
        var hoi = 0
        assertEquals(AdbNoiBo.KetQua.DA_GUI, AdbNoiBo.chay(thuMuc, "shell:reboot", { hoi++ }, cong = gia.cong))
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
            Thread.sleep(1500) // không ai bấm OK
        }
        assertEquals(AdbNoiBo.KetQua.CHUA_CHO_PHEP, AdbNoiBo.chay(thuMuc, "shell:reboot", {}, cong = gia.cong, choToiDa = 300))
        gia.xong()
    }

    @Test
    fun tiviKhongMoAdbHayChiCoTls() {
        val thuMuc = tam.newFolder()
        val dong = ServerSocket(0).use { it.localPort }
        assertEquals(AdbNoiBo.KetQua.KHONG_CO, AdbNoiBo.chay(thuMuc, "shell:reboot", {}, cong = dong))
        val tls = AdbdGia { vao, ra ->
            doc(vao)
            gui(ra, STLS, 0x01000000, 0, ByteArray(0))
        }
        assertEquals(AdbNoiBo.KetQua.KHONG_CO, AdbNoiBo.chay(thuMuc, "shell:reboot", {}, cong = tls.cong))
        tls.xong()
    }

    @Test
    fun giuKetNoiToiKhiLenhChayXong() {
        // TX3 (29/10/2026): đóng kết nối ngay khi có OKAY thì adbd giết lệnh reboot trước khi nó kịp chạy.
        val thuMuc = tam.newFolder()
        val gia = AdbdGia { vao, ra ->
            doc(vao)
            gui(ra, CNXN, 0x01000001, 4096, "device::\u0000".toByteArray()) // tivi không đòi xác thực (ro.adb.secure=0)
            val mo = doc(vao)
            assertEquals(OPEN, mo.lenh)
            gui(ra, OKAY, 7, mo.a0, ByteArray(0))
            Thread.sleep(200) // lệnh đang chạy
            gui(ra, WRTE, 7, mo.a0, "xong\n".toByteArray())
            val nhan = doc(vao) // client còn nối và báo đã nhận
            assertEquals(OKAY, nhan.lenh)
            assertEquals(mo.a0, nhan.a0)
            assertEquals(7, nhan.a1)
            gui(ra, CLSE, 7, mo.a0, ByteArray(0))
        }
        assertEquals(AdbNoiBo.KetQua.DA_GUI, AdbNoiBo.chay(thuMuc, "shell:reboot", {}, cong = gia.cong))
        gia.xong()
    }

    @Test
    fun tiviTatSauOkayLaDaGuiConDongTruocOkayLaLoi() {
        val thuMuc = tam.newFolder()
        val tat = AdbdGia { vao, ra ->
            doc(vao)
            gui(ra, CNXN, 0x01000001, 4096, "device::\u0000".toByteArray())
            gui(ra, OKAY, 7, doc(vao).a0, ByteArray(0)) // rồi mất kết nối: tivi đang tắt
        }
        assertEquals(AdbNoiBo.KetQua.DA_GUI, AdbNoiBo.chay(thuMuc, "shell:reboot", {}, cong = tat.cong))
        tat.xong()
        val dong = AdbdGia { vao, ra ->
            doc(vao)
            gui(ra, CNXN, 0x01000001, 4096, "device::\u0000".toByteArray())
            assertEquals(OPEN, doc(vao).lenh) // không trả lời lệnh
        }
        assertEquals(AdbNoiBo.KetQua.LOI, AdbNoiBo.chay(thuMuc, "shell:reboot", {}, cong = dong.cong))
        dong.xong()
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

    private fun doc(vao: DataInputStream) = AdbNoiBo.docGoi(vao)

    private fun gui(ra: OutputStream, lenh: Int, a0: Int, a1: Int, du: ByteArray) {
        ra.write(AdbNoiBo.maGoi(lenh, a0, a1, du))
        ra.flush()
    }

    /** Phép RSA công khai trên chữ ký: ra khối PKCS#1 mà adbd so (256 byte). */
    private fun moChuKy(k: RSAPublicKey, ky: ByteArray): ByteArray {
        val m = BigInteger(1, ky).modPow(k.publicExponent, k.modulus).toByteArray()
        return ByteArray(256 - m.size) + m
    }

    private fun docSo(b: ByteBuffer): BigInteger {
        var x = BigInteger.ZERO
        for (i in 0 until 64) x = x.or(BigInteger.valueOf(b.int.toLong() and 0xffffffffL).shiftLeft(32 * i))
        return x
    }
}
