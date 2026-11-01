package vn.tivikit.iptv

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import kotlin.concurrent.thread

/**
 * Màn phát kênh IPTV (1/11/2026): mở với tên kênh [TEN] và mã kênh [MA] (tvg-id), tìm kênh trong danh sách theo link
 * người dùng (Kho), phát bằng Media3 với User-Agent / Referer riêng của kênh. Kiểu luồng theo đuôi link (kieuHls); lỗi phát
 * thì thử kiểu còn lại (link không đuôi .m3u8), rồi tải lại danh sách một lần (nguồn kênh vừa đổi) trước khi báo lỗi. Lên / xuống (hay CH+ / CH−) đổi kênh
 * theo thứ tự danh sách, OK hiện tên kênh, Back thoát; rời màn (Home, app khác lên) thì thôi phát, hiện lại thì phát tiếp
 * kênh đó.
 */
@OptIn(UnstableApi::class)
class PhatKenh : Activity() {
    private lateinit var kho: Kho
    private lateinit var hinh: SurfaceView
    private lateinit var chu: TextView
    private val chinh = Handler(Looper.getMainLooper())
    private var player: ExoPlayer? = null
    private var ds: List<KenhIptv> = emptyList()
    private var kenh: KenhIptv? = null
    /** Kênh này đã chuyển sang kiểu luồng còn lại (HLS ↔ file thường) sau lần phát lỗi. */
    private var doiKieu = false
    private var daTaiMoi = false
    /** Kênh đang mở đã phát được (lên STATE_READY) ít nhất một lần; [thuLai] = số lần mở lại luồng sau lỗi giữa chừng. */
    private var daPhat = false
    private var thuLai = 0
    /** Lần mở hiện tại: đổi kênh thì việc tải nền của lần trước thôi. */
    private var lan = 0
    /** Màn vừa bị che (onStop) nên đã thôi phát: hiện lại thì phát tiếp. */
    private var biChe = false
    private val anChu = Runnable { chu.visibility = View.GONE }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        kho = Kho(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val goc = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        hinh = SurfaceView(this)
        goc.addView(hinh, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER))
        chu = TextView(this).apply {
            textSize = 22f
            setTextColor(Color.WHITE)
            setBackgroundColor(0xB3000000.toInt())
            setPadding(36, 20, 36, 20)
            maxWidth = resources.displayMetrics.widthPixels * 2 / 3
        }
        goc.addView(chu, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START).apply {
            setMargins(64, 48, 0, 0)
        })
        setContentView(goc)
        moTheo(intent)
    }

    override fun onNewIntent(i: Intent) {
        super.onNewIntent(i)
        setIntent(i)
        moTheo(i)
    }

    /** Kênh theo lời gọi ([TEN], [MA]): tìm trong danh sách (tải nền nếu cần) rồi phát. */
    private fun moTheo(i: Intent) {
        val ten = i.getStringExtra(TEN)?.trim().orEmpty()
        val ma = i.getStringExtra(MA)?.trim().orEmpty()
        val l = ++lan
        biChe = false
        // Lần mở mới: không mang theo trạng thái lỗi của lần trước (đổi kiểu luồng, đã tải lại danh sách).
        kenh = null
        hien(getString(R.string.dang_mo, ten.ifEmpty { ma }), giu = true)
        if (kho.link.isEmpty()) { hien(getString(R.string.chua_co_link), giu = true); return }
        thread(name = "IPTV-tai") {
            val dsMoi = kho.choMoKenh()
            chinh.post {
                if (l != lan || isFinishing) return@post
                ds = dsMoi
                when {
                    ds.isEmpty() -> hien(getString(R.string.khong_tai_duoc), giu = true)
                    else -> DanhSach.tim(ds, ma, ten)?.let { phat(it) } ?: hien(getString(R.string.khong_thay, ten.ifEmpty { ma }), giu = true)
                }
            }
        }
    }

    private fun phat(k: KenhIptv, kieuKhac: Boolean = false) {
        if (k != kenh) { doiKieu = false; daTaiMoi = false; daPhat = false; thuLai = 0 }
        kenh = k
        doiKieu = doiKieu || kieuKhac
        player?.release()
        val nguon = DefaultHttpDataSource.Factory()
            .setUserAgent(k.ua.ifEmpty { UA_MAC_DINH })
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(15_000)
            .apply { if (k.referer.isNotEmpty()) setDefaultRequestProperties(mapOf("Referer" to k.referer)) }
        val muc = MediaItem.fromUri(k.link)
        val nguonPhat = if (kieuHls(k.link, doiKieu)) HlsMediaSource.Factory(nguon).createMediaSource(muc)
            else ProgressiveMediaSource.Factory(nguon).createMediaSource(muc)
        val p = ExoPlayer.Builder(this).build()
        p.setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
        p.setVideoSurfaceView(hinh)
        p.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(trangThai: Int) {
                if (trangThai != Player.STATE_READY) return
                if (!daPhat) hien(k.ten)
                daPhat = true
            }

            override fun onVideoSizeChanged(c: VideoSize) = vuaKhung(c)

            override fun onPlayerError(e: PlaybackException) = loi(k, e)
        })
        p.setMediaSource(nguonPhat)
        p.prepare()
        p.playWhenReady = true
        player = p
        hien(getString(R.string.dang_mo, k.ten), giu = true)
    }

    /**
     * Lỗi phát: trễ khỏi cửa sổ trực tiếp thì về điểm phát mới nhất; kênh đã phát được rồi mới lỗi (mạng, máy chủ chập
     * chờn) thì mở lại đúng kiểu đó sau 1 giây, tối đa [THU_LAI] lần; chưa phát được lần nào mà link không đuôi .m3u8 thì thử
     * kiểu luồng còn lại; tải lại danh sách một lần; rồi mới báo. Trước đây lỗi giữa chừng của luồng HLS cũng chuyển sang kiểu
     * file thường, không bao giờ phát lại được (Skyworth 1/11/2026, VTV3 HD).
     */
    private fun loi(k: KenhIptv, e: PlaybackException) {
        val p = player ?: return
        when {
            e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> { p.seekToDefaultPosition(); p.prepare() }
            daPhat && thuLai < THU_LAI -> {
                thuLai++
                val l = lan
                chinh.postDelayed({ if (l == lan && kenh == k && player === p && !isFinishing) phat(k) }, 1_000)
            }
            !daPhat && !doiKieu && !laHls(k.link) -> phat(k, kieuKhac = true)
            !daTaiMoi -> {
                daTaiMoi = true
                val l = lan
                hien(getString(R.string.dang_mo, k.ten), giu = true)
                thread(name = "IPTV-tai-lai") {
                    val dsMoi = kho.taiLai()
                    chinh.post {
                        if (l != lan || isFinishing) return@post
                        if (dsMoi != null) ds = dsMoi
                        val moi = DanhSach.tim(ds, k.ma, k.ten)
                        if (moi != null && moi != k) { kenh = null; daTaiMoi = true; phatLai(moi) }
                        else hien(getString(R.string.loi_phat, k.ten), giu = true)
                    }
                }
            }
            else -> hien(getString(R.string.loi_phat, k.ten), giu = true)
        }
    }

    /** Phát kênh vừa tìm lại sau khi tải lại danh sách (không tải lại lần nữa nếu vẫn lỗi). */
    private fun phatLai(k: KenhIptv) {
        phat(k)
        daTaiMoi = true
    }

    /** Khung hình giữ đúng tỉ lệ video, nằm giữa màn. */
    private fun vuaKhung(c: VideoSize) {
        if (c.width <= 0 || c.height <= 0) return
        val goc = hinh.parent as? View ?: return
        val w = goc.width.takeIf { it > 0 } ?: return
        val h = goc.height.takeIf { it > 0 } ?: return
        val tiLe = c.width * c.pixelWidthHeightRatio / c.height
        val (rw, rh) = if (w.toFloat() / h > tiLe) (h * tiLe).toInt() to h else w to (w / tiLe).toInt()
        hinh.layoutParams = (hinh.layoutParams as FrameLayout.LayoutParams).apply { width = rw; height = rh; gravity = Gravity.CENTER }
    }

    /** Hiện chữ ở góc trên trái; [giu] = giữ tới lần hiện sau, không thì tắt sau 4 giây. */
    private fun hien(s: String, giu: Boolean = false) {
        chinh.removeCallbacks(anChu)
        chu.text = s
        chu.visibility = View.VISIBLE
        if (!giu) chinh.postDelayed(anChu, 4_000)
    }

    override fun onKeyDown(code: Int, e: KeyEvent): Boolean {
        val buoc = when (code) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> -1
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> 1
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_INFO -> { kenh?.let { hien(it.ten) }; return true }
            else -> return super.onKeyDown(code, e)
        }
        val hienTai = kenh ?: return true
        if (ds.isEmpty()) return true
        val i = ds.indexOf(hienTai).takeIf { it >= 0 } ?: 0
        lan++
        phat(ds[(i + buoc + ds.size) % ds.size])
        return true
    }

    override fun onStart() {
        super.onStart()
        // Hiện lại sau khi bị che (màn bảo vệ vừa tắt, quay lại từ app khác): phát tiếp kênh đang xem. Chờ một nhịp: được mở
        // bằng kênh khác (onNewIntent ngay sau) thì thôi.
        if (biChe) chinh.post { if (biChe) { biChe = false; kenh?.let { k -> kenh = null; phat(k) } ?: moTheo(intent) } }
    }

    override fun onStop() {
        super.onStop()
        // Truyền hình trực tiếp: rời màn là thôi phát, khỏi phát tiếng ngầm. Không đóng màn: mở lúc tivi đang ở màn bảo vệ
        // (Hẹn giờ, câu thử của Map Voice) thì màn bị che ngay từ đầu, đóng thì không bao giờ phát (TX3, 1/11/2026).
        player?.release()
        player = null
        lan++
        biChe = true
    }

    override fun onDestroy() {
        chinh.removeCallbacksAndMessages(null)
        player?.release()
        player = null
        super.onDestroy()
    }

    companion object {
        const val TEN = "ten"
        const val MA = "ma"
        /** User-Agent khi danh sách không ghi riêng cho kênh: như HttpURLConnection của Android (nhiều nguồn chỉ nhận dạng này). */
        val UA_MAC_DINH = "Dalvik/2.1.0 (Linux; U; Android ${Build.VERSION.RELEASE}; ${Build.MODEL} Build/${Build.ID})"

        /** Link HLS theo đuôi (.m3u8, kể cả có tham số sau dấu ?). */
        fun laHls(link: String): Boolean = link.substringBefore('?').substringBefore('#').endsWith(".m3u8", ignoreCase = true) ||
            link.contains(".m3u8?", ignoreCase = true)

        /** Số lần mở lại luồng của kênh đã phát được rồi mới lỗi, trước khi tải lại danh sách. */
        private const val THU_LAI = 3

        /** Đuôi của file nhạc / phim phát thẳng (không phải danh sách HLS). */
        private val DUOI_FILE = setOf("mp3", "aac", "m4a", "ogg", "opus", "flac", "wav", "mp4", "m4v", "mkv", "webm", "mov", "flv", "ts")

        /**
         * Phát [link] kiểu HLS không: đuôi .m3u8 thì luôn HLS; đuôi file (mp3, mp4…) thì file thường, lỗi mới thử HLS
         * ([doiKieu]); không đuôi thì HLS trước — phần lớn link IPTV không đuôi là HLS (Skyworth 1/11/2026: VTV3 HD đi
         * kiểu file thường mất 9 giây mới báo lỗi), luồng khác thì HLS báo lỗi ngay từ dòng đầu rồi thử file thường.
         */
        fun kieuHls(link: String, doiKieu: Boolean = false): Boolean {
            if (laHls(link)) return true
            val ten = link.substringBefore('?').substringBefore('#').substringAfterLast('/')
            val laFile = ten.substringAfterLast('.', "").lowercase() in DUOI_FILE
            return laFile == doiKieu
        }
    }
}
