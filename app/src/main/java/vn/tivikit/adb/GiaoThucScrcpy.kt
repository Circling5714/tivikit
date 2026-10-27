package vn.tivikit.adb

import java.nio.ByteBuffer

/**
 * Giao thức của scrcpy (Genymobile, Apache 2.0) bản [BAN], phần TiviKit dùng để xem màn hình tivi ngay trên điện thoại
 * (26/10/2026): lệnh chạy máy chủ trên tivi, đầu gói của luồng video, gói phím / chạm gửi lên tivi. Máy chủ phải đúng
 * bản [BAN] (scrcpy đổi giao thức giữa các bản, xem doc/develop.md của bản đó); đổi bản thì sửa cả scripts/tai-scrcpy.sh.
 */
object GiaoThucScrcpy {
    const val BAN = "5.0.1"
    /** Máy chủ trong assets (scripts/tai-scrcpy.sh tải và kiểm SHA-256) và cỡ của nó: tivi có file đúng cỡ thì khỏi đẩy lại. */
    const val ASSET = "scrcpy-server.jar"
    const val CO_MAY_CHU = 733_930L
    /** Chỗ đặt máy chủ trên tivi; chạy với cleanup=false để file còn lại cho lần sau (mặc định scrcpy tự xoá file). */
    const val DUONG_DAN = "/data/local/tmp/tivikit-scrcpy-$BAN.jar"
    /** Mã codec "h264" ở đầu luồng video. */
    const val H264 = 0x68323634
    /** Tên máy gửi kèm ở đầu luồng đầu tiên (64 byte, UTF-8, đệm 0). */
    const val CO_TEN_MAY = 64
    const val CO_DAU = 12

    /** Socket máy chủ mở trên tivi (tunnel_forward): app nối vào bằng luồng ADB "localabstract:<tên>". */
    fun tenSocket(scid: Int): String = "scrcpy_%08x".format(scid)

    /**
     * Lệnh chạy máy chủ qua ADB. Video H.264 cạnh dài [canhLon] px, [bitRate] bit/giây, tối đa [fps] khung/giây; không
     * âm thanh; power_on=false (đang ở chế độ chờ thì không đánh thức màn hình); không đồng bộ clipboard.
     */
    fun lenhChay(scid: Int, canhLon: Int, bitRate: Int, fps: Int, dieuKhien: Boolean = true): String =
        "CLASSPATH=$DUONG_DAN app_process / com.genymobile.scrcpy.Server $BAN scid=%08x log_level=info".format(scid) +
            " tunnel_forward=true audio=false control=$dieuKhien video_codec=h264 max_size=$canhLon video_bit_rate=$bitRate" +
            " max_fps=$fps power_on=false cleanup=false clipboard_autosync=false"

    /**
     * Lệnh chạy máy chủ chỉ có kênh điều khiển (27/10/2026): không hình, không tiếng, không gửi tên máy — luồng nối vào nhận
     * byte giả 0 là dùng được ngay. Để dán chữ có dấu khi không xem màn hình tivi.
     */
    fun lenhChayDieuKhien(scid: Int): String =
        "CLASSPATH=$DUONG_DAN app_process / com.genymobile.scrcpy.Server $BAN scid=%08x log_level=info".format(scid) +
            " tunnel_forward=true video=false audio=false control=true send_device_meta=false power_on=false cleanup=false" +
            " clipboard_autosync=false"

    /** Đầu 12 byte trước mỗi gói của luồng video. */
    sealed class Dau {
        /** Phiên quay mới (lúc bắt đầu, khi màn hình đổi cỡ / xoay): khung video [rong]×[cao]. */
        data class Phien(val rong: Int, val cao: Int) : Dau()
        /** Gói của bộ mã hoá: [cauHinh] = SPS/PPS, [khoa] = khung khoá, [co] byte theo sau. */
        data class Goi(val cauHinh: Boolean, val khoa: Boolean, val pts: Long, val co: Int) : Dau()
    }

    fun docDau(b: ByteArray): Dau {
        require(b.size >= CO_DAU) { "đầu gói phải đủ $CO_DAU byte" }
        val bb = ByteBuffer.wrap(b, 0, CO_DAU)
        if (b[0].toInt() and 0x80 != 0) return Dau.Phien(bb.getInt(4), bb.getInt(8))
        val ptsCo = bb.getLong(0)
        return Dau.Goi(cauHinh = ptsCo and (1L shl 62) != 0L, khoa = ptsCo and (1L shl 61) != 0L,
            pts = ptsCo and ((1L shl 61) - 1), co = bb.getInt(8))
    }

    /** Tên máy ở 64 byte đầu luồng (bỏ phần đệm 0). */
    fun docTenMay(b: ByteArray): String = String(b, 0, b.indexOf(0.toByte()).let { if (it < 0) b.size else it }, Charsets.UTF_8)

    /** KeyEvent.ACTION_DOWN / ACTION_UP, MotionEvent.ACTION_DOWN / ACTION_UP / ACTION_MOVE. */
    const val NHAN = 0
    const val NHA = 1
    const val KEO = 2

    /** Gói phím (TYPE_INJECT_KEYCODE = 0): kiểu, action, mã phím Android, số lần lặp, metaState — 14 byte. */
    fun goiPhim(action: Int, ma: Int, lap: Int = 0, meta: Int = 0): ByteArray =
        ByteBuffer.allocate(14).put(0.toByte()).put(action.toByte()).putInt(ma).putInt(lap).putInt(meta).array()

    /**
     * Gói chạm (TYPE_INJECT_TOUCH_EVENT = 2) của một ngón tay (POINTER_ID_GENERIC_FINGER = -2) tại ([x], [y]) trên khung
     * video [rong]×[cao] — phải đúng cỡ khung hiện tại, máy chủ bỏ qua chạm tính theo cỡ khác. Áp lực 1 khi nhấn, 0 khi
     * nhả (u16 cố định: 0xFFFF = 1). 32 byte.
     */
    fun goiCham(action: Int, x: Int, y: Int, rong: Int, cao: Int): ByteArray =
        ByteBuffer.allocate(32).put(2.toByte()).put(action.toByte()).putLong(-2L).putInt(x).putInt(y)
            .putShort(rong.toShort()).putShort(cao.toShort()).putShort((if (action == NHA) 0 else -1).toShort())
            .putInt(0).putInt(0).array()

    /** Gói yêu cầu quay lại từ đầu (TYPE_RESET_VIDEO = 17): máy chủ gửi lại SPS/PPS và một khung khoá. */
    fun goiQuayLai(): ByteArray = byteArrayOf(17)

    /**
     * Gói đặt clipboard của tivi (TYPE_SET_CLIPBOARD = 9): [soThuTu] khác 0 thì máy chủ trả ACK_CLIPBOARD cùng số khi đặt
     * xong; [dan] = bấm luôn KEYCODE_PASTE vào ô đang chọn (máy chủ chỉ bấm từ Android 7). Chữ UTF-8, độ dài u32 đứng trước.
     */
    fun goiClipboard(soThuTu: Long, dan: Boolean, chu: String): ByteArray {
        val b = chu.toByteArray(Charsets.UTF_8)
        return ByteBuffer.allocate(14 + b.size).put(9.toByte()).putLong(soThuTu).put((if (dan) 1 else 0).toByte()).putInt(b.size).put(b).array()
    }

    /** Tin máy chủ gửi về trên luồng điều khiển (DeviceMessage): clipboard của tivi, ACK đặt clipboard, dữ liệu UHID. */
    const val TIN_CLIPBOARD = 0
    const val TIN_ACK_CLIPBOARD = 1
    const val TIN_UHID = 2
}
