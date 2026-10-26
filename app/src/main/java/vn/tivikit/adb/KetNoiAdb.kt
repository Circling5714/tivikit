package vn.tivikit.adb

import android.content.Context
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.ByteArrayOutputStream
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Một kết nối ADB tới tivi. Khoá RSA tạo một lần rồi giữ trong bộ nhớ app, để tivi đã bấm
 * "Luôn cho phép" thì lần sau không hỏi lại.
 */
class KetNoiAdb private constructor(
    private val khoa: PrivateKey,
    private val chungChi: Certificate,
) : AbsAdbConnectionManager() {

    init {
        setTimeout(20, TimeUnit.SECONDS)
        // libadb mặc định api = 1: lúc bắt tay xin gói tối đa 4 KB, tivi đồng ý đúng 4 KB, nên mỗi 4 KB gửi đi phải chờ
        // một lượt OKAY — cài APK vài chục MB qua Wi-Fi chậm như rùa (25/10/2026). Xin như adb trên máy tính (api ≥ 28:
        // gói 1 MB, bỏ checksum); tivi tự lấy cỡ nhỏ hơn của hai bên (Android 7–8: 256 KB), và libadb luôn tính checksum
        // khi gửi nên tivi đời cũ vẫn kiểm được.
        setApi(34)
    }

    override fun getPrivateKey(): PrivateKey = khoa
    override fun getCertificate(): Certificate = chungChi
    override fun getDeviceName(): String = "TiviKit"

    /** Địa chỉ của lần kết nối thành công gần nhất (cả Gỡ lỗi không dây, connectTls cũng đi qua đây). */
    @Volatile var diemCuoi: Pair<String, Int>? = null
        private set

    override fun connect(host: String, port: Int): Boolean {
        val ok = super.connect(host, port)
        if (ok) { diemCuoi = host to port; coHeadC = null }
        return ok
    }

    /**
     * Nối lại đúng địa chỉ vừa dùng khi kết nối rớt giữa chừng (Wi-Fi chập chờn, tivi khởi động lại). Dò cổng trước, chờ
     * lâu (DoMang): libadb nối thẳng vào máy không trả lời thì treo cả phút.
     */
    fun ketNoiLai(): Boolean {
        synchronized(khoaNoiLai) {
            val d = diemCuoi ?: return false
            // Hai nơi cùng thấy rớt (giữ kết nối + một thao tác): nơi sau dùng luôn kết nối nơi trước vừa nối.
            if (System.currentTimeMillis() - lucNoiLai < 5000 && runCatching { isConnected }.getOrDefault(false)) return true
            runCatching { disconnect() }
            if (d.first != "127.0.0.1" && DoMang.doLau(d.first, d.second) != DoMang.Cong.MO) return false
            val ok = runCatching { super.connect(d.first, d.second) }.getOrDefault(false)
            if (ok) lucNoiLai = System.currentTimeMillis()
            return ok
        }
    }

    private val khoaNoiLai = Any()
    @Volatile private var lucNoiLai = 0L

    /**
     * Kết nối còn dùng được không (25/10/2026, Skyworth SWTV: sang trình chọn file rồi quay lại thì kết nối đã đứt,
     * nhiều điện thoại cắt socket của app nằm nền). libadb không tự biết socket đã đứt: lệnh kế lỗi ngay, hoặc open()
     * chờ OKAY mãi trong lúc giữ khoá — nên hỏi `echo` trên luồng riêng có hạn giờ; quá hạn thì ngắt luồng đó để nhả
     * khoá, ketNoiLai mới disconnect được.
     */
    fun conSong(giay: Long = 6): Boolean {
        if (diemCuoi == null) return false
        val ex = java.util.concurrent.Executors.newSingleThreadExecutor()
        return try {
            ex.submit<Boolean> { shell("echo ok", giay.toInt()).trim() == "ok" }.get(giay, TimeUnit.SECONDS)
        } catch (e: Exception) {
            false
        } finally {
            ex.shutdownNow()
        }
    }

    /**
     * Chạy một lệnh shell, trả về chữ in ra (stdout + stderr gộp).
     *
     * Đọc tới dấu kết thúc tự chèn chứ không chờ luồng đóng: libadb 3.1.1 có lỗi đua, khi tivi
     * đóng luồng lúc còn dữ liệu chưa đọc thì read() chờ mãi.
     */
    fun shell(lenh: String, giay: Int = 120): String {
        val dau = "__TVS_" + java.lang.Long.toHexString(System.nanoTime()) + "__"
        val chu = docToiKhi(openStream("shell:$lenh; echo $dau"), giay) { it.contains(dau) }
        return chu.substringBefore(dau)
    }

    /**
     * Đọc đúng [co] byte stdout của [lenh] (exec:, nhị phân) vào [ra] (25/10/2026: tải APK từ tivi về, đọc từng đoạn
     * file trên tivi). Đọc đủ thì thôi, không chờ tivi đóng luồng (lỗi đua của libadb, xem shell). Quá [giay] giây không
     * nhận thêm byte nào thì đóng luồng: read() đang chờ nhận lỗi và thoát.
     */
    fun docRa(lenh: String, co: Long, ra: java.io.OutputStream, giay: Int = 30, tienDo: (Long) -> Unit = {}) {
        val stream = openStream("exec:$lenh")
        val lanCuoi = java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis())
        val hetGio = java.util.concurrent.atomic.AtomicBoolean(false)
        val canh = Thread {
            try {
                while (true) {
                    Thread.sleep(1000)
                    if (System.currentTimeMillis() - lanCuoi.get() > giay * 1000L) { hetGio.set(true); runCatching { stream.close() }; break }
                }
            } catch (_: InterruptedException) {}
        }.apply { isDaemon = true; start() }
        try {
            val vao = stream.openInputStream()
            val buf = ByteArray(64 * 1024)
            var da = 0L
            while (da < co) {
                val n = try { vao.read(buf, 0, minOf(buf.size.toLong(), co - da).toInt()) } catch (e: java.io.IOException) {
                    // Luồng do chính mình đóng vì quá giờ: báo đúng là tivi không trả lời, không phải mất kết nối.
                    if (hetGio.get()) throw java.io.IOException("Tivi không trả lời sau $giay giây ($da/$co byte)") else throw e
                }
                if (n < 0) throw java.io.IOException("Tivi ngừng gửi giữa chừng ($da/$co byte)")
                ra.write(buf, 0, n)
                da += n
                lanCuoi.set(System.currentTimeMillis())
                tienDo(da)
            }
        } finally {
            canh.interrupt()
            runCatching { stream.close() }
        }
    }

    /** Ảnh PNG màn hình tivi (qua base64 để đi được đường shell có dấu kết thúc). */
    fun chupPng(): ByteArray {
        val b64 = shell("screencap -p | base64").filter { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' }
        return android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
    }

    /**
     * Chạy một lệnh không tự dừng (getevent) và đọc tới khi [xong] đúng với phần đã nhận (25/10/2026, dò mã nút remote).
     * Quá [giay] giây thì trả null — không phải lỗi, người dùng chưa bấm gì; luồng tự đóng trước (lệnh lỗi, mất kết nối)
     * thì trả phần đã nhận. Lệnh trên tivi chỉ biết luồng đã đóng khi in thêm dòng: người gọi tự dừng nó.
     */
    fun nghe(lenh: String, giay: Int, xong: (String) -> Boolean): String? = docHoacHetGio(openStream("shell:$lenh"), giay, xong)

    /** Đọc luồng tới khi [xong] đúng với phần đã nhận, hoặc luồng đóng, hoặc quá [giay] giây. */
    private fun docToiKhi(stream: io.github.muntashirakon.adb.AdbStream, giay: Int = 120, xong: (String) -> Boolean): String =
        docHoacHetGio(stream, giay, xong) ?: throw java.io.IOException("Tivi không trả lời sau $giay giây")

    /** Như docToiKhi, quá [giay] giây thì trả null. */
    private fun docHoacHetGio(stream: io.github.muntashirakon.adb.AdbStream, giay: Int, xong: (String) -> Boolean): String? {
        val out = ByteArrayOutputStream()
        val hetHan = System.currentTimeMillis() + giay * 1000L
        val luong = Thread {
            runCatching {
                val input = stream.openInputStream()
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    synchronized(out) { out.write(buf, 0, n) }
                    if (xong(synchronized(out) { out.toString("UTF-8") })) break
                }
            }
        }.apply { isDaemon = true; start() }
        while (luong.isAlive && System.currentTimeMillis() < hetHan) luong.join(200)
        runCatching { stream.close() }
        if (luong.isAlive) {
            luong.interrupt()
            return null
        }
        return synchronized(out) { out.toString("UTF-8") }.replace("\r", "")
    }

    /**
     * Cài APK theo kiểu `adb install` mới: `cmd package install -S <cỡ>` rồi đổ byte vào stdin.
     * Trả về chữ máy in ra ("Success" hoặc "Failure [...]").
     */
    fun caiApk(apk: ByteArray): String {
        val stream = openStream("exec:cmd package install -r -S ${apk.size}")
        val o = stream.openOutputStream()
        val khoi = coKhoi()
        var i = 0
        while (i < apk.size) {
            val n = minOf(khoi, apk.size - i)
            o.write(apk, i, n)
            i += n
        }
        o.flush()
        return docToiKhi(stream, 180) { it.contains("Success") || it.contains("Failure") || it.contains("Error") }
    }

    /**
     * Cỡ mỗi lần write() khi đổ byte lên tivi (caiApk, doVaoLenh, ghiNhanh). Mỗi write() libadb chờ tivi trả OKAY rồi
     * mới gửi tiếp, nên khối 4000 byte cũ tốn một lượt chờ cho mỗi 4 KB. Khối bằng cỡ gói hai bên thống nhất lúc bắt tay
     * (maxData: 1 MB từ Android 9, 256 KB Android 7–8 — xem setApi ở init); gửi to hơn thì tivi cắt kết nối.
     */
    private fun coKhoi(): Int = (runCatching { adbConnection?.maxData }.getOrNull() ?: 4000).coerceIn(4000, 1024 * 1024)

    /**
     * Như caiApk nhưng đọc dần từ [nguon] (25/10/2026, cài app người dùng chọn: APK vài trăm MB không nạp hết vào RAM):
     * đổ đúng [co] byte vào stdin của [lenh] (`cmd package install -S` / `install-write -S`), [tienDo] nhận số byte đã
     * gửi. File ngắn hơn [co] thì dừng ngay — phía tivi còn chờ byte sẽ không bao giờ trả lời. [xong]: chữ máy in ra đã
     * đủ để biết kết quả (mặc định: dòng của pm install).
     */
    fun doVaoLenh(
        lenh: String, nguon: java.io.InputStream, co: Long, giay: Int = 300,
        xong: (String) -> Boolean = { it.contains("Success") || it.contains("Failure") || it.contains("Error") },
        tienDo: (Long) -> Unit = {},
    ): String {
        val stream = openStream("exec:$lenh")
        doHet(stream, nguon, co, tienDo)
        return docToiKhi(stream, giay, xong)
    }

    /**
     * Ghi file lên tivi từ luồng [nguon] cỡ [co] (25/10/2026, gửi ảnh / video): `head -c` biết chắc khi nào ghi xong.
     * Android 8 không có `head -c` (xem coHeadC): `cat` rồi đóng luồng để cat nhận EOF, chờ cỡ file trên tivi đủ.
     * [duongDan] không được chứa nháy kép, $, ` hay \ (đặt trong nháy kép của sh -c).
     */
    fun dayLen(duongDan: String, nguon: java.io.InputStream, co: Long, tienDo: (Long) -> Unit = {}) {
        require(duongDan.none { it in "\"'\$`\\" }) { "duongDan" }
        if (coHeadC()) {
            val dau = "__TVS_" + java.lang.Long.toHexString(System.nanoTime()) + "__"
            doVaoLenh("sh -c 'head -c $co > \"$duongDan\"; echo $dau'", nguon, co, 300, { it.contains(dau) }, tienDo)
        } else {
            val stream = openStream("exec:sh -c 'cat > \"$duongDan\"'")
            doHet(stream, nguon, co, tienDo)
            runCatching { stream.close() }
        }
        repeat(20) {
            if (shell("stat -c %s \"$duongDan\" 2>/dev/null").trim().toLongOrNull() == co) return
            Thread.sleep(500)
        }
        throw java.io.IOException("File trên tivi chưa đủ cỡ ($duongDan)")
    }

    /** Đổ đúng [co] byte từ [nguon] vào stdin của [stream], khối bằng coKhoi. */
    private fun doHet(stream: io.github.muntashirakon.adb.AdbStream, nguon: java.io.InputStream, co: Long, tienDo: (Long) -> Unit) {
        val o = stream.openOutputStream()
        val buf = ByteArray(coKhoi())
        var da = 0L
        while (da < co) {
            // Đọc đầy khối rồi mới gửi: luồng giải nén zip chỉ trả từng mẩu vài KB, gửi ngay thì lại chậm như khối nhỏ.
            val can = minOf(buf.size.toLong(), co - da).toInt()
            var n = 0
            while (n < can) {
                val r = nguon.read(buf, n, can - n)
                if (r < 0) break
                n += r
            }
            if (n < can) {
                runCatching { stream.close() }
                throw java.io.IOException("File ngắn hơn khai báo (${da + n}/$co byte)")
            }
            o.write(buf, 0, n)
            da += n
            tienDo(da)
        }
        o.flush()
    }

    /**
     * Ghi một file lên tivi. Đường nhanh: đẩy thẳng byte vào `head -c <cỡ>` như caiApk (24/10/2026: đường base64 cũ mỗi
     * 3000 ký tự một lệnh shell, theme 14 ảnh thành vài trăm lệnh — TCL 65Q646 chạy CleanTV trên chính tivi kẹt ở "Đang
     * gửi 14 ảnh"). Cỡ file sai thì quay về đường base64 (toybox có base64 từ Android 6).
     */
    fun ghiFile(duongDan: String, noiDung: ByteArray) {
        if (coHeadC() && runCatching { ghiNhanh(duongDan, noiDung) }.getOrDefault(false)) return
        shell("rm -f $duongDan $duongDan.b64")
        val b64 = android.util.Base64.encodeToString(noiDung, android.util.Base64.NO_WRAP)
        b64.chunked(3000).forEach { manh -> shell("printf '%s' '$manh' >> $duongDan.b64") }
        shell("base64 -d $duongDan.b64 > $duongDan; rm -f $duongDan.b64")
    }

    /** Tivi có `head -c` không, hỏi một lần mỗi kết nối. */
    @Volatile private var coHeadC: Boolean? = null

    /**
     * toybox của Android 8 chưa có `head -c` (24/10/2026, Sony BRAVIA 2015 Android 8 kẹt ở "Đang gửi ảnh nền"): lệnh
     * báo lỗi ngay, phía tivi đóng luồng giữa lúc app còn đẩy byte nên kẹt. Không có thì đi thẳng đường base64.
     */
    private fun coHeadC(): Boolean = coHeadC ?: (runCatching { shell("echo abc | head -c 2 2>/dev/null").trim() == "ab" }.getOrDefault(false)).also { coHeadC = it }

    private fun ghiNhanh(duongDan: String, noiDung: ByteArray): Boolean {
        val dau = "__TVS_" + java.lang.Long.toHexString(System.nanoTime()) + "__"
        val stream = openStream("exec:sh -c 'head -c ${noiDung.size} > $duongDan; echo $dau'")
        val o = stream.openOutputStream()
        val khoi = coKhoi()
        var i = 0
        while (i < noiDung.size) {
            val n = minOf(khoi, noiDung.size - i)
            o.write(noiDung, i, n)
            i += n
        }
        o.flush()
        docToiKhi(stream, 120) { it.contains(dau) }
        return shell("wc -c < $duongDan").trim().toIntOrNull() == noiDung.size
    }

    companion object {
        @Volatile private var hienTai: KetNoiAdb? = null

        fun lay(context: Context): KetNoiAdb = hienTai ?: synchronized(this) {
            hienTai ?: taoMoi(context).also { hienTai = it }
        }

        private fun taoMoi(context: Context): KetNoiAdb {
            val thuMuc = File(context.filesDir, "adb").apply { mkdirs() }
            val fKhoa = File(thuMuc, "khoa.der")
            val fCert = File(thuMuc, "cert.der")
            if (fKhoa.exists() && fCert.exists()) {
                runCatching {
                    val khoa = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(fKhoa.readBytes()))
                    val cert = CertificateFactory.getInstance("X.509").generateCertificate(fCert.inputStream())
                    return KetNoiAdb(khoa, cert)
                }
            }
            val gen = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
            val cap = gen.generateKeyPair()
            val ten = X500Name("CN=TiviKit")
            val tu = Date()
            val den = Date(tu.time + 3650L * 24 * 3600 * 1000)
            val builder = JcaX509v3CertificateBuilder(ten, BigInteger.valueOf(tu.time), tu, den, ten, cap.public)
            val signer = JcaContentSignerBuilder("SHA256withRSA").build(cap.private)
            val cert = JcaX509CertificateConverter().getCertificate(builder.build(signer))
            fKhoa.writeBytes(cap.private.encoded)
            fCert.writeBytes(cert.encoded)
            return KetNoiAdb(cap.private, cert)
        }
    }
}
