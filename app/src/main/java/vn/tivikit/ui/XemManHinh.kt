package vn.tivikit.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import android.view.TextureView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import vn.tivikit.adb.GiaoThucScrcpy
import vn.tivikit.adb.ManHinhTv
import vn.tivikit.t
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Xem màn hình tivi trên màn Điều khiển (26/10/2026, kiểu scrcpy — xem ManHinhTv): luồng H.264 giải mã bằng MediaCodec ra
 * TextureView. Chạm lên hình là chạm đúng chỗ đó trên tivi; phím remote đi qua kênh điều khiển của scrcpy khi đang xem
 * (nhanh hơn `input keyevent`). Màn hình tivi đứng yên thì không có khung mới, hình giữ khung cuối; chưa có khung nào sau
 * 1,5 giây thì hiện tạm một ảnh chụp. Tivi không quay được video thì dùng ảnh chụp làm mới 1,5 giây một lần.
 */
class XemManHinh(private val vm: PhienLamViec) {
    /** Người dùng đang bật xem: app xuống nền thì tạm dừng, lên lại thì xem tiếp ([tiepTuc]). */
    var muonXem by mutableStateOf(false)
        private set
    var trangThai by mutableStateOf("")
        private set
    var laLoi by mutableStateOf(false)
        private set
    /** Cỡ khung video (theo phiên của máy chủ) hay cỡ ảnh chụp: giữ đúng tỉ lệ hình và đổi toạ độ chạm. */
    var rong by mutableStateOf(1280)
        private set
    var cao by mutableStateOf(720)
        private set
    /** Đã vẽ ít nhất một khung video. */
    var coHinh by mutableStateOf(false)
        private set
    /** Ảnh chụp: hình tạm lúc chưa có khung video, hoặc hình chính ở chế độ ảnh. */
    var anh by mutableStateOf<Bitmap?>(null)
        private set
    /** Tivi không quay được video: dùng ảnh chụp làm mới liên tục. */
    var cheDoAnh by mutableStateOf(false)
        private set

    private val khoa = Any()
    @Volatile private var phien: ManHinhTv.Phien? = null
    @Volatile private var beMat: Surface? = null
    /** Bộ giải mã gắn với [beMat]; chỉ đụng tới khi giữ [khoa]. */
    private var giaiMa: GiaiMa? = null
    private var viecAnh: Job? = null
    /** Tăng mỗi lần mở / dừng: luồng nhận của lần cũ thấy khác số thì tự thôi. */
    @Volatile private var lanChay = 0
    @Volatile private var dangChay = false

    fun bat() { muonXem = true; chay() }

    /** Tắt hẳn: nút Tắt, rời màn Điều khiển, đổi tivi. */
    fun tat() { muonXem = false; dung() }

    /** App xuống nền: dừng luồng, giữ ý muốn xem. */
    fun tamDung() = dung()

    /** App lên lại: đang muốn xem mà đã dừng thì mở lại. */
    fun tiepTuc() { if (muonXem && !dangChay) chay() }

    /** Phím remote qua kênh điều khiển của scrcpy (đang xem); không có phiên thì false để gửi đường cũ. */
    fun phim(ma: Int): Boolean {
        val p = phien ?: return false
        return p.gui(GiaoThucScrcpy.goiPhim(GiaoThucScrcpy.NHAN, ma)) && p.gui(GiaoThucScrcpy.goiPhim(GiaoThucScrcpy.NHA, ma))
    }

    /** Chạm tại ([fx], [fy]) tính theo tỉ lệ 0..1 của khung hình trên điện thoại. */
    fun cham(fx: Float, fy: Float) {
        val x = (fx.coerceIn(0f, 1f) * rong).toInt().coerceAtMost(rong - 1)
        val y = (fy.coerceIn(0f, 1f) * cao).toInt().coerceAtMost(cao - 1)
        if (cheDoAnh) {
            // Ảnh chụp đúng cỡ màn hình tivi: chạm bằng `input tap`, rồi chụp lại cho thấy kết quả.
            vm.viewModelScope.launch { runCatching { vm.io { vm.adb.shell("input tap $x $y") } } }
            return
        }
        val p = phien ?: return
        p.gui(GiaoThucScrcpy.goiCham(GiaoThucScrcpy.NHAN, x, y, rong, cao))
        p.gui(GiaoThucScrcpy.goiCham(GiaoThucScrcpy.NHA, x, y, rong, cao))
    }

    /** Nghe TextureView trên màn Điều khiển: có / mất chỗ vẽ thì làm lại bộ giải mã. */
    val ngheBeMat = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) = datBeMat(Surface(st))
        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean { datBeMat(null); return true }
        override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
    }

    private fun datBeMat(s: Surface?) {
        synchronized(khoa) {
            giaiMa?.dong(); giaiMa = null
            beMat?.release()
            beMat = s
        }
        // Chỗ vẽ mới: xin máy chủ gửi lại SPS/PPS và khung khoá để có hình ngay, không chờ màn hình tivi đổi.
        if (s != null) phien?.gui(GiaoThucScrcpy.goiQuayLai())
    }

    private fun chay() {
        dung()
        val lan = lanChay
        dangChay = true
        coHinh = false; anh = null; cheDoAnh = false; laLoi = false
        trangThai = t("Đang mở màn hình tivi…", "Opening the TV screen…")
        Thread({ vongNhan(lan) }, "scrcpy-nhan").apply { isDaemon = true; start() }
        // Chưa có khung nào sau 1,5 giây (màn hình tivi đứng yên): chụp một ảnh làm hình tạm.
        viecAnh = vm.viewModelScope.launch {
            delay(1500)
            if (lan == lanChay && !coHinh && !cheDoAnh) chup(lan)
        }
    }

    private fun dung() {
        lanChay++
        dangChay = false
        viecAnh?.cancel(); viecAnh = null
        phien?.dong(); phien = null
        synchronized(khoa) { giaiMa?.dong(); giaiMa = null }
    }

    /** Luồng nhận: mở phiên, đọc gói video, đưa vào bộ giải mã (chỉ khi có chỗ vẽ). */
    private fun vongNhan(lan: Int) {
        var p: ManHinhTv.Phien? = null
        try {
            p = ManHinhTv(vm.adb).mo({ vn.tivikit.CapNhat.moAsset(vm.ctx, GiaoThucScrcpy.ASSET).readBytes() })
            if (lan != lanChay) return
            phien = p
            if (!coHinh) trangThai = t("Đang chờ hình từ tivi…", "Waiting for the TV picture…")
            var buf = ByteArray(512 * 1024)
            var cauHinh: ByteArray? = null
            var choKhoa = true
            while (lan == lanChay) {
                when (val d = p.docDau()) {
                    is GiaoThucScrcpy.Dau.Phien -> {
                        rong = d.rong; cao = d.cao
                        synchronized(khoa) { giaiMa?.dong(); giaiMa = null }
                        choKhoa = true
                    }
                    is GiaoThucScrcpy.Dau.Goi -> {
                        if (d.co > buf.size) buf = ByteArray(d.co)
                        p.docGoi(buf, d.co)
                        if (d.cauHinh) cauHinh = buf.copyOf(d.co)
                        choKhoa = duaVao(p, lan, d, buf, cauHinh, choKhoa)
                    }
                }
            }
        } catch (e: Exception) {
            if (lan != lanChay) return
            val lyDo = p?.loiMayChu().orEmpty().ifBlank { e.message ?: e.javaClass.simpleName }
            // Chưa từng có hình: tivi không quay được video, chuyển sang ảnh chụp. Đang xem mà đứt: báo, cho xem lại.
            if (!coHinh) batCheDoAnh(lan, lyDo)
            else { trangThai = t("Mất hình từ tivi ($lyDo).", "Lost the TV picture ($lyDo)."); laLoi = true; dangChay = false }
        } finally {
            // Dừng chủ động thì dung() đã đóng bộ giải mã; lần chạy mới có thể đã gắn bộ của nó, không được đóng nhầm.
            synchronized(khoa) { if (lan == lanChay) { giaiMa?.dong(); giaiMa = null } }
            p?.dong()
            if (phien === p) phien = null
        }
    }

    /**
     * Đưa một gói vào bộ giải mã; trả lại choKhoa mới (đang chờ khung khoá thì bỏ các khung khác). Bộ giải mã gắn với chỗ
     * vẽ: chỗ vẽ mới thì làm bộ mới, nạp SPS/PPS gần nhất và xin khung khoá.
     */
    private fun duaVao(p: ManHinhTv.Phien, lan: Int, d: GiaoThucScrcpy.Dau.Goi, buf: ByteArray, cauHinh: ByteArray?, choKhoa: Boolean): Boolean =
        synchronized(khoa) {
            val s = beMat ?: run { giaiMa?.dong(); giaiMa = null; return true }
            var gm = giaiMa
            var cho = choKhoa
            if (gm == null || gm.beMat !== s) {
                gm?.dong()
                gm = runCatching { GiaiMa(s, rong, cao) { khiCoKhung(lan) } }.getOrNull() ?: run { giaiMa = null; return true }
                giaiMa = gm
                cho = true
                if (!d.cauHinh) cauHinh?.let { gm.dua(it, it.size, 0, true) }
                if (!d.cauHinh && !d.khoa) p.gui(GiaoThucScrcpy.goiQuayLai())
            }
            when {
                d.cauHinh -> { gm.dua(buf, d.co, 0, true); cho }
                cho && !d.khoa -> true
                gm.dua(buf, d.co, d.pts, false) -> false
                // Bộ giải mã nghẽn, gói bị bỏ: hình hỏng tới khung khoá sau, xin luôn khung khoá.
                else -> { p.gui(GiaoThucScrcpy.goiQuayLai()); true }
            }
        }

    private fun khiCoKhung(lan: Int) {
        if (lan != lanChay || coHinh) return
        coHinh = true
        anh = null
        trangThai = t("Đang xem · $rong×$cao · chạm lên hình để bấm", "Live · $rong×$cao · tap the picture to press")
    }

    private fun batCheDoAnh(lan: Int, lyDo: String) {
        cheDoAnh = true
        laLoi = false
        trangThai = t("Tivi không quay được video ($lyDo): dùng ảnh chụp, làm mới mỗi 1,5 giây.",
            "The TV can't stream video ($lyDo): showing screenshots, refreshed every 1.5 s.")
        viecAnh?.cancel()
        viecAnh = vm.viewModelScope.launch {
            while (isActive && lan == lanChay) {
                chup(lan)
                delay(1500)
            }
        }
    }

    /** Một ảnh chụp màn hình tivi (screencap): hình tạm, hoặc hình chính ở chế độ ảnh. */
    private suspend fun chup(lan: Int) {
        val b = runCatching { vm.io { vm.tv.chupManHinh() } }.getOrNull()
        val bm = b?.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size) }.getOrNull() }
        if (lan != lanChay || (coHinh && !cheDoAnh)) return
        if (bm != null) {
            anh = bm
            if (cheDoAnh) { rong = bm.width; cao = bm.height }
        } else if (cheDoAnh) {
            trangThai = t("Không chụp được màn hình tivi (mất kết nối, hoặc tivi đang ở cổng HDMI).",
                "Couldn't capture the TV screen (connection lost, or the TV is on an HDMI input).")
            laLoi = true
        }
    }

    /**
     * Bộ giải mã H.264 vẽ thẳng ra [beMat], chạy kiểu bất đồng bộ: khung giải xong là vẽ ngay, không chờ gói sau (màn hình
     * tivi đứng yên thì không có gói sau).
     */
    private class GiaiMa(val beMat: Surface, rong: Int, cao: Int, khiCoKhung: () -> Unit) {
        private val luong = HandlerThread("scrcpy-giai-ma").apply { start() }
        private val oVao = LinkedBlockingQueue<Int>()
        private val codec: MediaCodec
        @Volatile private var daCoKhung = false

        init {
            try {
                codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
                codec.setCallback(object : MediaCodec.Callback() {
                    override fun onInputBufferAvailable(c: MediaCodec, i: Int) { oVao.offer(i) }
                    override fun onOutputBufferAvailable(c: MediaCodec, i: Int, info: MediaCodec.BufferInfo) {
                        runCatching { c.releaseOutputBuffer(i, true) }
                        if (!daCoKhung) { daCoKhung = true; khiCoKhung() }
                    }
                    override fun onError(c: MediaCodec, e: MediaCodec.CodecException) {}
                    override fun onOutputFormatChanged(c: MediaCodec, f: MediaFormat) {}
                }, Handler(luong.looper))
                val f = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, rong, cao)
                f.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 2 * 1024 * 1024)
                f.setInteger(MediaFormat.KEY_PRIORITY, 0)
                if (Build.VERSION.SDK_INT >= 30) f.setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
                codec.configure(f, beMat, null, 0)
                codec.start()
            } catch (e: Exception) {
                luong.quitSafely()
                throw e
            }
        }

        /** Đưa một gói vào; chờ ô vào tối đa 0,1 giây, không có thì false (gói bị bỏ). */
        fun dua(du: ByteArray, co: Int, pts: Long, cauHinh: Boolean): Boolean {
            val i = oVao.poll(100, TimeUnit.MILLISECONDS) ?: return false
            return runCatching {
                val b = codec.getInputBuffer(i)!!
                b.clear(); b.put(du, 0, co)
                codec.queueInputBuffer(i, 0, co, pts, if (cauHinh) MediaCodec.BUFFER_FLAG_CODEC_CONFIG else 0)
            }.isSuccess
        }

        fun dong() {
            runCatching { codec.stop() }
            runCatching { codec.release() }
            luong.quitSafely()
        }
    }
}
