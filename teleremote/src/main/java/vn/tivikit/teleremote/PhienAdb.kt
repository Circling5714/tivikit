package vn.tivikit.teleremote

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAKeyGenParameterSpec
import java.security.spec.RSAPublicKeySpec
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Kết nối ADB giữ lâu tới chính tivi (127.0.0.1:5555) cho các lệnh ngắn (bấm phím, đọc tên ô Kênh nhanh, xem màn hình
 * đang bật hay chờ). Chép từ mapvoice/PhienAdb.kt (Map Voice 1.1, phần khoá, chữ ký và gói lại chép từ launcher/AdbNoiBo.kt
 * của TiviKit Launcher 1.4.1, đã thử trên TX3 và Skyworth). Lần đầu tivi hỏi "Cho phép gỡ lỗi?" cho khoá riêng của Tele
 * Remote (filesDir/adbkey). Chỉ giao thức ADB thường (khoá RSA, không TLS): tivi chỉ bật Gỡ lỗi không dây thì không dùng được.
 *
 * Cách dùng: [noi], rồi [docMai] trên một luồng riêng (chặn tới khi mất kết nối); [chay] gọi từ luồng khác trong lúc đó.
 */
class PhienAdb(private val thuMuc: File, private val may: String = "127.0.0.1", private val cong: Int = 5555) : Closeable {
    enum class KetQua {
        /** Đã nối, tivi đã nhận khoá. */
        DA_NOI,
        /** Tivi không mở ADB qua mạng ở cổng 5555 (hay chỉ nhận kết nối TLS). */
        KHONG_CO,
        /** Tivi hỏi cho phép gỡ lỗi mà người dùng không đồng ý (Huỷ, hay để quá thời gian chờ). */
        CHUA_CHO_PHEP,
        /** Lỗi khác: tivi trả lời lạ, mất kết nối giữa chừng. */
        LOI,
    }

    private class Luong(val dong: ((String) -> Unit)?, val het: (() -> Unit)?) {
        @Volatile var xa = 0
        val xong = CountDownLatch(1)
        val chu = ByteArrayOutputStream()
        val doDong = ByteArrayOutputStream()
    }

    private val s = Socket()
    private lateinit var vao: DataInputStream
    private lateinit var ra: OutputStream
    private val khoaGhi = Any()
    private val dsLuong = ConcurrentHashMap<Int, Luong>()
    private val soKeTiep = AtomicInteger(1)
    @Volatile private var daNoi = false

    /**
     * Nối và xác thực. Tivi chưa biết khoá thì gọi [dangHoi] (trên luồng đang chạy) rồi chờ người dùng cho phép, tối đa
     * [choToiDa] ms. Chặn luồng: gọi ngoài luồng giao diện.
     */
    fun noi(choToiDa: Int = 60_000, dangHoi: () -> Unit = {}): KetQua {
        val k = try { khoa(thuMuc) } catch (_: Exception) { return KetQua.LOI }
        try {
            s.connect(InetSocketAddress(may, cong), 2000)
        } catch (_: IOException) {
            return KetQua.KHONG_CO
        }
        var daHoi = false
        try {
            s.soTimeout = 5000
            vao = DataInputStream(s.getInputStream().buffered())
            ra = s.getOutputStream().buffered()
            gui(CNXN, PHIEN_BAN, "host::\u0000".toByteArray(), TAI_TOI_DA)
            var daKy = false
            while (true) {
                val g = docGoi(vao)
                if (g.lenh == CNXN) break
                if (g.lenh == STLS) return KetQua.KHONG_CO
                if (g.lenh != AUTH || g.a0 != TOKEN) continue
                if (!daKy) {
                    daKy = true
                    gui(AUTH, CHU_KY, ky(k, g.du))
                    continue
                }
                // Tivi chưa biết khoá: gửi khoá công khai, tivi hiện hộp "Cho phép gỡ lỗi?" và chờ người dùng. Token tới sau
                // đó (adbd chưa nối được phần hiện hộp hỏi thì hỏi lại khi nối được) cũng trả bằng khoá, như adb.
                gui(AUTH, KHOA_CONG_KHAI, khoaCongKhai(k.public as RSAPublicKey))
                if (!daHoi) {
                    daHoi = true
                    s.soTimeout = choToiDa
                    dangHoi()
                }
            }
            // Kết nối im lặng hàng giờ khi không ai bấm: không hạn giờ đọc, mất kết nối thì đọc báo lỗi.
            s.soTimeout = 0
            daNoi = true
            return KetQua.DA_NOI
        } catch (_: IOException) {
            return if (daHoi) KetQua.CHUA_CHO_PHEP else KetQua.LOI
        } catch (_: GeneralSecurityException) {
            return KetQua.LOI
        }
    }

    /**
     * Mở luồng chạy lệnh shell [lenh]: mỗi dòng in ra gọi [dong], luồng đóng (lệnh thoát) gọi [het]; cả hai trên luồng của
     * [docMai]. Trả số luồng.
     */
    fun mo(lenh: String, dong: (String) -> Unit, het: () -> Unit = {}): Int = moLuong(lenh, Luong(dong, het))

    /**
     * Chạy lệnh shell [lenh] tới khi xong, trả chữ in ra; null nếu chưa nối, tivi từ chối hay quá [choToiDa] ms. Cần [docMai]
     * đang chạy ở luồng khác.
     */
    fun chay(lenh: String, choToiDa: Long = 10_000): String? {
        if (!daNoi) return null
        val l = Luong(null, null)
        val so = try { moLuong(lenh, l) } catch (_: IOException) { return null }
        if (!l.xong.await(choToiDa, TimeUnit.MILLISECONDS)) {
            dsLuong.remove(so)
            runCatching { if (l.xa != 0) gui(CLSE, so, ByteArray(0), l.xa) }
            return null
        }
        return synchronized(l) { l.chu.toString(Charsets.UTF_8.name()) }
    }

    /** Đọc và chia gói cho các luồng tới khi mất kết nối (hay [close]); chặn luồng gọi. */
    fun docMai() {
        try {
            while (true) {
                val g = docGoi(vao)
                val l = dsLuong[g.a1]
                when (g.lenh) {
                    OKAY -> if (l != null) l.xa = g.a0
                    WRTE -> {
                        if (l == null) continue
                        if (l.xa == 0) l.xa = g.a0
                        // Báo đã nhận trước (để adbd gửi tiếp) rồi mới xử lý: dữ liệu vừa nhận có thể làm luồng khác mở lệnh mới
                        // ngay, gói OPEN đó phải đi sau gói báo nhận này.
                        gui(OKAY, g.a1, ByteArray(0), g.a0)
                        nhan(l, g.du)
                    }
                    CLSE -> if (l != null) {
                        dsLuong.remove(g.a1)
                        // a0 = 0: tivi không mở được luồng. Còn lại: lệnh đã thoát, trả CLSE như adb.
                        if (g.a0 != 0) runCatching { gui(CLSE, g.a1, ByteArray(0), g.a0) }
                        ketThuc(l)
                    }
                }
            }
        } catch (_: IOException) {
        } finally {
            daNoi = false
            dsLuong.values.forEach(::ketThuc)
            dsLuong.clear()
        }
    }

    override fun close() {
        daNoi = false
        runCatching { s.close() }
    }

    private fun moLuong(lenh: String, l: Luong): Int {
        val so = soKeTiep.getAndIncrement()
        dsLuong[so] = l
        try {
            gui(OPEN, so, ("shell:$lenh\u0000").toByteArray(Charsets.UTF_8))
        } catch (e: IOException) {
            dsLuong.remove(so)
            throw e
        }
        return so
    }

    private fun nhan(l: Luong, du: ByteArray) {
        if (l.dong == null) {
            synchronized(l) { l.chu.write(du) }
            return
        }
        for (b in du) {
            if (b == '\n'.code.toByte()) {
                val dong = l.doDong.toString(Charsets.UTF_8.name()).trimEnd('\r')
                l.doDong.reset()
                runCatching { l.dong.invoke(dong) }
            } else if (l.doDong.size() < 64 * 1024) {
                l.doDong.write(b.toInt())
            }
        }
    }

    private fun ketThuc(l: Luong) {
        if (l.xong.count == 0L) return
        l.xong.countDown()
        runCatching { l.het?.invoke() }
    }

    private fun gui(ma: Int, a0: Int, du: ByteArray, a1: Int = 0) {
        synchronized(khoaGhi) {
            ra.write(maGoi(ma, a0, a1, du))
            ra.flush()
        }
    }

    internal class Goi(val lenh: Int, val a0: Int, val a1: Int, val du: ByteArray)

    companion object {
        internal const val CNXN = 0x4e584e43
        internal const val AUTH = 0x48545541
        internal const val OPEN = 0x4e45504f
        internal const val OKAY = 0x59414b4f
        internal const val WRTE = 0x45545257
        internal const val CLSE = 0x45534c43
        internal const val STLS = 0x534c5453
        /** Bản giao thức bỏ qua tổng kiểm (Android 9+); gói nào cũng ghi tổng kiểm nên adbd cũ hơn vẫn nhận. */
        private const val PHIEN_BAN = 0x01000001
        private const val TAI_TOI_DA = 256 * 1024
        private const val TOKEN = 1
        private const val CHU_KY = 2
        private const val KHOA_CONG_KHAI = 3
        private const val TEN_KHOA = "tivikit-teleremote@tivi"

        /** SHA-1 DigestInfo đứng trước token: adbd kiểm chữ ký bằng RSA_verify(NID_sha1), coi token 20 byte là bản băm. */
        private val DAU_SHA1 = byteArrayOf(0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14)

        /** Khoá ADB của Tele Remote: RSA 2048 số mũ F4, tạo lần đầu, giữ dạng PKCS#8 ở [thuMuc]/adbkey. */
        internal fun khoa(thuMuc: File): KeyPair {
            val f = File(thuMuc, "adbkey")
            val kf = KeyFactory.getInstance("RSA")
            if (f.isFile) {
                // Conscrypt (Android) đọc ra RSAPrivateKey không kèm số mũ công khai: đòi RSAPrivateCrtKey thì đọc hỏng, mỗi lần
                // nối một khoá mới và tivi hỏi cho phép mãi (thấy với TiviKit Launcher trên TX3). Khoá nào cũng tạo với F4.
                val rieng = runCatching { kf.generatePrivate(PKCS8EncodedKeySpec(f.readBytes())) as RSAPrivateKey }.getOrNull()
                if (rieng != null) {
                    val mu = (rieng as? RSAPrivateCrtKey)?.publicExponent ?: RSAKeyGenParameterSpec.F4
                    return KeyPair(kf.generatePublic(RSAPublicKeySpec(rieng.modulus, mu)), rieng)
                }
            }
            val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(RSAKeyGenParameterSpec(2048, RSAKeyGenParameterSpec.F4)) }
                .generateKeyPair()
            val tam = File(thuMuc, "adbkey.tam")
            tam.writeBytes(kp.private.encoded)
            f.delete()
            if (!tam.renameTo(f)) throw IOException("không lưu được khoá ADB")
            return kp
        }

        /** Khoá công khai gửi cho tivi, như adbkey.pub: cấu trúc RSAPublicKey của Android (từ 32 bit đầu nhỏ) mã base64. */
        internal fun khoaCongKhai(k: RSAPublicKey): ByteArray {
            val tu = 64 // 2048 bit
            val n = k.modulus
            val r32 = BigInteger.ONE.shiftLeft(32)
            val b = ByteBuffer.allocate(4 * (3 + 2 * tu)).order(ByteOrder.LITTLE_ENDIAN)
            b.putInt(tu)
            b.putInt(n.mod(r32).modInverse(r32).negate().mod(r32).toInt()) // n0inv = -1 / n[0] mod 2^32
            for (i in 0 until tu) b.putInt(n.shiftRight(32 * i).toInt())
            val rr = BigInteger.ONE.shiftLeft(64 * tu).mod(n) // R² mod n, R = 2^2048
            for (i in 0 until tu) b.putInt(rr.shiftRight(32 * i).toInt())
            b.putInt(k.publicExponent.toInt())
            return (Base64.getEncoder().encodeToString(b.array()) + " " + TEN_KHOA + "\u0000").toByteArray()
        }

        internal fun ky(k: KeyPair, token: ByteArray): ByteArray =
            Signature.getInstance("NONEwithRSA").run { initSign(k.private); update(DAU_SHA1); update(token); sign() }

        /** Gói ADB: 6 số 32 bit đầu nhỏ (lệnh, 2 tham số, độ dài, tổng các byte, lệnh đảo bit) rồi dữ liệu. */
        internal fun maGoi(lenh: Int, a0: Int, a1: Int, du: ByteArray): ByteArray =
            ByteBuffer.allocate(24 + du.size).order(ByteOrder.LITTLE_ENDIAN).putInt(lenh).putInt(a0).putInt(a1)
                .putInt(du.size).putInt(du.sumOf { it.toInt() and 0xff }).putInt(lenh.inv()).put(du).array()

        internal fun docGoi(vao: DataInputStream): Goi {
            val dau = ByteBuffer.wrap(ByteArray(24).also(vao::readFully)).order(ByteOrder.LITTLE_ENDIAN)
            val lenh = dau.int
            val a0 = dau.int
            val a1 = dau.int
            val dai = dau.int
            dau.int // tổng kiểm: adbd mới không tính
            if (dau.int != lenh.inv() || dai !in 0..TAI_TOI_DA) throw IOException("gói ADB hỏng")
            return Goi(lenh, a0, a1, ByteArray(dai).also(vao::readFully))
        }
    }
}
