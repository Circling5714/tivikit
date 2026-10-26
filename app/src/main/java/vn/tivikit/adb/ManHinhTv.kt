package vn.tivikit.adb

import io.github.muntashirakon.adb.AdbStream
import java.io.DataInputStream
import java.io.IOException
import java.util.concurrent.Executors

/**
 * Xem màn hình tivi kiểu scrcpy (26/10/2026): đặt máy chủ scrcpy lên tivi, chạy nó, rồi nối luồng video và luồng điều
 * khiển ngay trên kết nối ADB của app (luồng "localabstract:", không mở cổng nào). Thử trên box TX3 Mini (Android 9,
 * OMX.amlogic.video.encoder.avc): khung khoá đầu tiên tới sau ~0,3 giây; màn hình đứng yên thì Android chỉ lặp khung
 * cuối khoảng một giây rồi thôi gửi, nên đọc luồng không đặt hạn giờ.
 */
class ManHinhTv(private val adb: KetNoiAdb) {

    /** Một phiên xem: [dong] tắt cả hai luồng, máy chủ trên tivi tự thoát khi luồng video đóng. */
    class Phien internal constructor(
        val tenMay: String,
        private val video: AdbStream,
        private val vao: DataInputStream,
        private val dieuKhien: AdbStream,
        private val mayChu: AdbStream,
        private val nhatKy: StringBuffer,
    ) {
        private val ra = dieuKhien.openOutputStream()
        private val hangGui = Executors.newSingleThreadExecutor { r -> Thread(r, "scrcpy-dieu-khien").apply { isDaemon = true } }
        @Volatile var daDong = false
            private set

        /** Đầu gói kế tiếp; chặn tới khi tivi gửi (màn hình đứng yên thì có thể rất lâu). */
        fun docDau(): GiaoThucScrcpy.Dau {
            val b = ByteArray(GiaoThucScrcpy.CO_DAU)
            vao.readFully(b)
            return GiaoThucScrcpy.docDau(b)
        }

        fun docGoi(buf: ByteArray, co: Int) = vao.readFully(buf, 0, co)

        /** Gửi gói điều khiển ở luồng riêng, gọi được từ luồng giao diện. Phiên đã đóng thì false. */
        fun gui(goi: ByteArray): Boolean {
            if (daDong) return false
            runCatching { hangGui.execute { runCatching { ra.write(goi); ra.flush() } } }.onFailure { return false }
            return true
        }

        /** Dòng lỗi cuối cùng máy chủ in ra (bộ mã hoá không chạy…), rỗng nếu không có. */
        fun loiMayChu(): String = nhatKy.toString().lines().lastOrNull { it.contains("ERROR") }?.substringAfter("ERROR:")?.trim().orEmpty()

        fun dong() {
            if (daDong) return
            daDong = true
            hangGui.shutdownNow()
            runCatching { dieuKhien.close() }
            runCatching { video.close() }
            runCatching { mayChu.close() }
        }
    }

    /**
     * Mở một phiên: đặt máy chủ lên tivi nếu chưa có đúng bản ([layMayChu] chỉ gọi khi cần đẩy), chạy máy chủ, nối luồng
     * video rồi luồng điều khiển (đúng thứ tự máy chủ chờ), đọc tên máy và mã codec. Lỗi thì ném IOException kèm lý do.
     */
    fun mo(layMayChu: () -> ByteArray, canhLon: Int = 1280, bitRate: Int = 4_000_000, fps: Int = 30): Phien {
        datMayChu(layMayChu)
        val scid = java.util.Random().nextInt(Int.MAX_VALUE)
        val mayChu = adb.openStream("exec:" + GiaoThucScrcpy.lenhChay(scid, canhLon, bitRate, fps))
        // Chữ máy chủ in ra (dòng ERROR khi bộ mã hoá không chạy…), đọc tới khi máy chủ thoát.
        val nhatKy = StringBuffer()
        val doc = Thread {
            runCatching {
                val i = mayChu.openInputStream()
                val buf = ByteArray(4096)
                while (true) { val n = i.read(buf); if (n < 0) break; nhatKy.append(String(buf, 0, n)) }
            }
        }.apply { isDaemon = true; name = "scrcpy-nhat-ky"; start() }
        try {
            val ten = GiaoThucScrcpy.tenSocket(scid)
            // Máy chủ cần chừng một giây để mở socket: nối lại tới khi được.
            val han = System.currentTimeMillis() + 10_000
            var video: Pair<AdbStream, DataInputStream>? = null
            while (video == null) {
                video = noiVideo(ten)
                if (video == null) {
                    if (!doc.isAlive || System.currentTimeMillis() > han) throw IOException(lyDo(nhatKy, "máy chủ scrcpy không mở được"))
                    Thread.sleep(150)
                }
            }
            val dk = adb.openStream("localabstract:$ten")
            val (luongVideo, vao) = video
            val tenMay = ByteArray(GiaoThucScrcpy.CO_TEN_MAY).also { vao.readFully(it) }
            val codec = vao.readInt()
            if (codec != GiaoThucScrcpy.H264) throw IOException("codec không phải H.264 (0x%08x)".format(codec))
            return Phien(GiaoThucScrcpy.docTenMay(tenMay), luongVideo, vao, dk, mayChu, nhatKy)
        } catch (e: Exception) {
            runCatching { mayChu.close() }
            throw if (e is IOException) IOException(lyDo(nhatKy, e.message ?: "lỗi"), e) else e
        }
    }

    /**
     * Nối luồng video: máy chủ đã nghe thì nó gửi 1 byte 0 (byte giả của scrcpy); chưa nghe thì tivi đóng luồng ngay.
     * Đọc byte đó có hạn giờ: libadb 3.1.1 có lúc read() chờ mãi trên luồng tivi vừa đóng (xem KetNoiAdb.shell).
     */
    private fun noiVideo(ten: String): Pair<AdbStream, DataInputStream>? {
        val s = runCatching { adb.openStream("localabstract:$ten") }.getOrNull() ?: return null
        val vao = DataInputStream(s.openInputStream())
        val gia = java.util.concurrent.atomic.AtomicInteger(-2)
        Thread { gia.set(runCatching { vao.readUnsignedByte() }.getOrDefault(-1)) }.apply { isDaemon = true; start() }.join(3000)
        if (gia.get() == 0) return s to vao
        runCatching { s.close() }
        return null
    }

    /** Đẩy máy chủ lên tivi khi tivi chưa có file đúng cỡ (cỡ đổi theo bản). */
    private fun datMayChu(layMayChu: () -> ByteArray) {
        val co = adb.shell("stat -c %s ${GiaoThucScrcpy.DUONG_DAN} 2>/dev/null").trim().toLongOrNull()
        if (co == GiaoThucScrcpy.CO_MAY_CHU) return
        adb.ghiFile(GiaoThucScrcpy.DUONG_DAN, layMayChu())
    }

    private fun lyDo(nhatKy: StringBuffer, macDinh: String): String =
        nhatKy.toString().lines().lastOrNull { it.contains("ERROR") }?.substringAfter("ERROR:")?.trim()?.takeIf { it.isNotEmpty() } ?: macDinh
}
