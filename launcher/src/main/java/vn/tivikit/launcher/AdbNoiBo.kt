package vn.tivikit.launcher

import java.io.DataInputStream
import java.io.EOFException
import java.io.File
import java.io.IOException
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
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAKeyGenParameterSpec
import java.security.spec.RSAPublicKeySpec
import java.util.Base64

/**
 * Khởi động lại tivi bằng ADB của chính tivi (127.0.0.1:5555), cùng lệnh reboot với nút Khởi động lại của TiviKit
 * (29/10/2026). App thường không tự khởi động lại được; còn mục Khởi động lại trong menu nguồn của Android thì trên tivi
 * MediaTek thành tắt hẳn: TvServices của MediaTek thấy lý do tắt "userrequested" là cho tivi về chờ (thấy trên Skyworth,
 * phải bật lại bằng remote). Lần đầu tivi hỏi "Cho phép gỡ lỗi?" cho khoá riêng của launcher (filesDir/adbkey; launcher
 * không cho sao lưu). Chỉ giao thức ADB thường (khoá RSA, không TLS): tivi chỉ bật Gỡ lỗi không dây thì không dùng được.
 */
object AdbNoiBo {
    enum class KetQua {
        /** Tivi đã nhận lệnh. */
        DA_GUI,
        /** Tivi không mở ADB qua mạng ở cổng 5555 (hay chỉ nhận kết nối TLS). */
        KHONG_CO,
        /** Tivi hỏi cho phép gỡ lỗi mà người dùng không đồng ý (Huỷ, hay để quá thời gian chờ). */
        CHUA_CHO_PHEP,
        /** Lỗi khác: tivi trả lời lạ, mất kết nối giữa chừng. */
        LOI,
    }

    private const val CNXN = 0x4e584e43
    private const val AUTH = 0x48545541
    private const val OPEN = 0x4e45504f
    private const val OKAY = 0x59414b4f
    private const val STLS = 0x534c5453
    /** Bản giao thức bỏ qua tổng kiểm (Android 9+); gói nào cũng ghi tổng kiểm nên adbd cũ hơn vẫn nhận. */
    private const val PHIEN_BAN = 0x01000001
    private const val TAI_TOI_DA = 256 * 1024
    private const val TOKEN = 1
    private const val CHU_KY = 2
    private const val KHOA_CONG_KHAI = 3
    private const val TEN_KHOA = "tivikit-launcher@tivi"

    /** SHA-1 DigestInfo đứng trước token: adbd kiểm chữ ký bằng RSA_verify(NID_sha1), coi token 20 byte là bản băm. */
    private val DAU_SHA1 = byteArrayOf(0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14)

    /**
     * Gửi lệnh reboot cho tivi. Tivi chưa biết khoá của launcher thì gọi [dangHoi] (trên luồng đang chạy) rồi chờ người
     * dùng cho phép, tối đa 60 giây. Chặn luồng: gọi ngoài luồng giao diện.
     */
    fun khoiDongLai(thuMuc: File, dangHoi: () -> Unit): KetQua = chay(thuMuc, "shell:reboot", dangHoi)

    internal fun chay(thuMuc: File, lenh: String, dangHoi: () -> Unit, may: String = "127.0.0.1", cong: Int = 5555,
                      choToiDa: Int = 60_000): KetQua {
        val k = try { khoa(thuMuc) } catch (_: Exception) { return KetQua.LOI }
        Socket().use { s ->
            try {
                s.connect(InetSocketAddress(may, cong), 2000)
            } catch (_: IOException) {
                return KetQua.KHONG_CO
            }
            var daHoi = false
            try {
                s.soTimeout = 5000
                val vao = DataInputStream(s.getInputStream().buffered())
                val ra = s.getOutputStream().buffered()
                fun gui(ma: Int, a0: Int, du: ByteArray, a1: Int = 0) {
                    ra.write(maGoi(ma, a0, a1, du))
                    ra.flush()
                }
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
                    } else if (!daHoi) {
                        // Tivi chưa biết khoá: gửi khoá công khai, tivi hiện hộp "Cho phép gỡ lỗi?" và chờ người dùng.
                        daHoi = true
                        gui(AUTH, KHOA_CONG_KHAI, khoaCongKhai(k.public as RSAPublicKey))
                        s.soTimeout = choToiDa
                        dangHoi()
                    }
                }
                s.soTimeout = 5000
                gui(OPEN, 1, (lenh + "\u0000").toByteArray())
                // Tivi tắt nhanh đến mức đóng kết nối trước khi kịp trả lời cũng là đã nhận lệnh.
                val g = try { docGoi(vao) } catch (_: EOFException) { return KetQua.DA_GUI }
                return if (g.lenh == OKAY) KetQua.DA_GUI else KetQua.LOI
            } catch (_: IOException) {
                return if (daHoi) KetQua.CHUA_CHO_PHEP else KetQua.LOI
            } catch (_: GeneralSecurityException) {
                return KetQua.LOI
            }
        }
    }

    /** Khoá ADB của launcher: RSA 2048, tạo lần đầu, giữ dạng PKCS#8 ở [thuMuc]/adbkey. */
    internal fun khoa(thuMuc: File): KeyPair {
        val f = File(thuMuc, "adbkey")
        val kf = KeyFactory.getInstance("RSA")
        if (f.isFile) {
            val rieng = runCatching { kf.generatePrivate(PKCS8EncodedKeySpec(f.readBytes())) as RSAPrivateCrtKey }.getOrNull()
            if (rieng != null) return KeyPair(kf.generatePublic(RSAPublicKeySpec(rieng.modulus, rieng.publicExponent)), rieng)
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

    internal class Goi(val lenh: Int, val a0: Int, val a1: Int, val du: ByteArray)

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
