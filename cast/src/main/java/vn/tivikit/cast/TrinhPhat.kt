package vn.tivikit.cast

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

/**
 * Mở video điện thoại gửi bằng SmartTube và giữ trạng thái phát để báo lại điện thoại. Tivi cho app nghe thông báo
 * (NgheThongBao, TiviKit bật qua ADB) thì điều khiển và đọc vị trí thật qua phiên media của SmartTube; không thì bấm
 * phím media, tua bằng cách mở lại video ở giây mới, vị trí tự ước lượng.
 */
class TrinhPhat(private val ctx: Context) {
    companion object {
        /** Mã trạng thái kênh Lounge. */
        const val KHONG = -1
        const val PHAT = 1
        const val TAM_DUNG = 2
        const val DANG_TAI = 3
        const val DUNG = 4
        val GOI_SMARTTUBE = listOf("org.smarttube.stable", "org.smarttube.beta", "com.liskovsoft.smarttubetv", "com.liskovsoft.smarttubetv.beta")
        private val MA_VIDEO = Regex("^[A-Za-z0-9_-]{11}$")

        fun so(d: Double): String = String.format(Locale.US, "%.3f", d)

        /**
         * Bật màn hình như bấm remote: tắt màn bảo vệ (không thì video hay hộp hỏi XinPhep hiện phía sau nó), tivi đang
         * chờ thì bật lên như app YouTube gốc. Android 14+ cần quyền TURN_SCREEN_ON (TiviKit cấp qua ADB).
         */
        @Suppress("DEPRECATION")
        fun danhThuc(ctx: Context) = runCatching {
            ctx.getSystemService(PowerManager::class.java).newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE, "tivikit:cast").acquire(5_000)
        }
    }

    private val am = ctx.getSystemService(AudioManager::class.java)
    private val tay = Handler(Looper.getMainLooper())

    /** Trạng thái phát đổi ngoài lệnh của điện thoại (remote tivi, video hết): báo lại điện thoại. */
    var baoDoi: (() -> Unit)? = null
    /** Cho SmartTube rời màn hình để về nghe nền (dịch vụ trợ năng làm): mở lại launcher [goi], null = bấm Home. */
    var veHome: ((goi: String?) -> Unit)? = null
    /** App đang ở trên màn hình (dịch vụ trợ năng báo); null = chưa biết. */
    @Volatile private var goiTren: String? = null
    @Volatile private var mocTren = 0L
    /** Launcher (gói có màn HOME) hiện gần nhất: nơi trả về khi màn bảo vệ đang che lúc video tới. */
    @Volatile private var launcherCuoi: String? = null
    private val laHomeDaBiet = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    private var theo: MediaController? = null
    private var ttTheo = KHONG
    private val goiLai = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = doiTuPhien(state)
        override fun onSessionDestroyed() { theo = null }
    }

    var videoId: String? = null; private set
    private var ds: List<String> = emptyList()
    private var listId: String? = null
    private var chiSo = 0
    private var trangThai = KHONG
    private var viTriGoc = 0.0
    private var moc = 0L
    private var cpn = ""

    /** Phiên media của SmartTube nếu tivi cho đọc (cần quyền nghe thông báo). */
    private fun phien(): MediaController? = runCatching {
        ctx.getSystemService(MediaSessionManager::class.java)
            .getActiveSessions(ComponentName(ctx, NgheThongBao::class.java))
            .firstOrNull { it.packageName in GOI_SMARTTUBE }
    }.getOrNull()

    /** Theo dõi phiên media của SmartTube nếu tivi cho đọc (gọi một lần lúc bật). */
    fun batTheoDoi() = runCatching {
        ctx.getSystemService(MediaSessionManager::class.java)
            .addOnActiveSessionsChangedListener({ theoDoi() }, ComponentName(ctx, NgheThongBao::class.java), tay)
        theoDoi()
    }

    private fun theoDoi() = tay.post {
        val p = phien()
        if (p?.sessionToken == theo?.sessionToken) return@post
        runCatching { theo?.unregisterCallback(goiLai) }
        theo = p
        p?.registerCallback(goiLai, tay)
    }

    private fun doiTuPhien(st: PlaybackState?) {
        val moi = when (st?.state) {
            PlaybackState.STATE_PLAYING -> PHAT
            PlaybackState.STATE_PAUSED -> TAM_DUNG
            PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING -> DANG_TAI
            PlaybackState.STATE_STOPPED, PlaybackState.STATE_NONE, PlaybackState.STATE_ERROR -> DUNG
            else -> return
        }
        synchronized(this) {
            // Chỉ báo khi đang phát video điện thoại gửi, và trạng thái thật sự đổi.
            if (videoId == null || moi == ttTheo) return
            ttTheo = moi
            trangThai = moi
            viTriGoc = st.position.coerceAtLeast(0) / 1000.0
            moc = SystemClock.elapsedRealtime()
        }
        baoDoi?.invoke()
    }

    fun doiTren(goi: String) {
        if (goi == goiTren) return
        goiTren = goi
        mocTren = SystemClock.elapsedRealtime()
        if (laHome(goi)) launcherCuoi = goi
    }

    private fun laHome(goi: String): Boolean = laHomeDaBiet.getOrPut(goi) {
        runCatching {
            ctx.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(goi), 0).isNotEmpty()
        }.getOrDefault(false)
    }

    /** App có màn lên trước cuối cùng ([goi], lúc [luc] theo giờ máy) và launcher lên trước cuối cùng. */
    private class LenTruoc(val goi: String, val luc: Long, val launcher: String?)

    /**
     * App lên trước màn hình cuối cùng từ [tu] (giờ máy, ms) tới giờ, theo thống kê dùng app; null nếu không có lần nào
     * trong khoảng đó hay không đọc được (thiếu GET_USAGE_STATS, TiviKit cấp qua ADB). Thống kê ghi đúng từng lần một màn
     * lên trước; sự kiện cửa sổ của dịch vụ trợ năng thì gộp trong 100 ms và có lúc lệch khi launcher và SmartTube đổi nhau
     * nhanh (28/10/2026 trên Skyworth: cập nhật launcher, bấm Home, launcherx tự lên — máy nhận tưởng SmartTube đang hiện
     * nên không đưa video mới về nghe nền).
     */
    @Suppress("DEPRECATION")
    private fun lenTruoc(tu: Long): LenTruoc? = runCatching {
        val ds = ctx.getSystemService(UsageStatsManager::class.java).queryEvents(tu, System.currentTimeMillis())
        val e = UsageEvents.Event()
        var kq: LenTruoc? = null
        while (ds.hasNextEvent()) {
            ds.getNextEvent(e)
            if (e.eventType != UsageEvents.Event.MOVE_TO_FOREGROUND) continue
            val goi = e.packageName ?: continue
            kq = LenTruoc(goi, e.timeStamp, if (laHome(goi)) goi else kq?.launcher)
        }
        kq
    }.getOrNull()

    /**
     * Bài SmartTube đang phát (provider dang-phat, cho thẻ Đang phát của TiviKit): mã video, tên, kênh. Phiên của SmartTube
     * thiếu tên bài (video mở từ đường link) thì tra theo mã (TenVideo). Rỗng nếu tivi không cho đọc phiên phát.
     */
    fun baiDangPhat(): JSONObject {
        val md = phien()?.metadata ?: return JSONObject()
        val id = md.getString(MediaMetadata.METADATA_KEY_MEDIA_ID).orEmpty()
        val ten = md.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim().orEmpty()
        val (tieuDe, kenh) = if (ten.isNotEmpty()) ten to md.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
            else TenVideo.lay(id) ?: ("" to "")
        return JSONObject().put("videoId", id).put("tieuDe", tieuDe).put("kenh", kenh)
    }

    /**
     * SmartTube đang phát mà không ở trên màn hình ([tren] = app đang hiện): người xem đã bấm Home để nghe nền. Tivi không cho
     * đọc phiên phát thì chỉ biết có tiếng: chỉ tính khi tiếng đó là video đã cast trước đó ([daCast]), không phải app nhạc khác.
     */
    private fun dangNgheNen(tren: String?, daCast: Boolean): Boolean {
        if (tren == null || tren in GOI_SMARTTUBE) return false
        val st = phien()?.playbackState?.state
        return if (st != null) st == PlaybackState.STATE_PLAYING || st == PlaybackState.STATE_BUFFERING
        else daCast && am.isMusicActive
    }

    /**
     * Video mới tới lúc SmartTube đang phát nền: SmartTube phải lên màn hình để mở video, chờ video [id] phát (có phiên media
     * thì chờ phiên báo đúng mã video: video cast sang không có tên bài để so) rồi cho SmartTube rời màn hình như người xem bấm
     * Home (cách nghe nền của SmartTube chạy lúc đó), về launcher [ve]. Quá 12 giây thì cứ về. SmartTube lên trước màn hình
     * từ lúc nào: theo thống kê dùng app từ [tu] (giờ máy) nếu [coThongKe], không thì theo dịch vụ trợ năng.
     */
    private fun choVeNen(ve: String?, id: String, tu: Long, coThongKe: Boolean) {
        val batDau = SystemClock.elapsedRealtime()
        tay.postDelayed(object : Runnable {
            override fun run() {
                val bay = SystemClock.elapsedRealtime()
                // SmartTube đã ở trước màn hình bao lâu (ms); -1 = chưa lên, hay người xem đã sang app khác.
                val daLen = if (coThongKe) lenTruoc(tu)?.takeIf { it.goi in GOI_SMARTTUBE }?.let { System.currentTimeMillis() - it.luc } ?: -1L
                    else if (goiTren in GOI_SMARTTUBE) bay - mocTren else -1L
                val p = phien()
                val daPhat = if (p != null) p.playbackState?.state == PlaybackState.STATE_PLAYING &&
                        p.metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID).let { it == null || it == id }
                    else am.isMusicActive && daLen > 2_500
                val ve12 = bay - batDau > 12_000
                if (daLen > 1_000 && (daPhat || ve12)) {
                    Log.i(Dial.NHAN, "cho SmartTube về nghe nền sau ${bay - batDau} ms (${if (daPhat) "video mới đã phát" else "quá 12 giây"}), mở ${ve ?: "Home"}")
                    veHome?.invoke(ve)
                } else if (!ve12) tay.postDelayed(this, 500)
                else Log.i(Dial.NHAN, "thôi chờ về nghe nền: SmartTube không còn ở trước màn hình")
            }
        }, 1_000)
    }

    private fun goiSmartTube(): String? =
        GOI_SMARTTUBE.firstOrNull { p -> runCatching { ctx.packageManager.getPackageInfo(p, 0) }.isSuccess }

    /** Mở [id] từ giây [tu]: SmartTube nếu có, không thì app nào mở được link YouTube. */
    private fun mo(id: String, tu: Double): Boolean {
        danhThuc(ctx)
        val uri = Uri.parse("https://www.youtube.com/watch?v=$id" + if (tu >= 1) "&t=${tu.toInt()}s" else "")
        val i = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        goiSmartTube()?.let { i.setPackage(it) }
        return runCatching { ctx.startActivity(i) }.isSuccess
    }

    private fun phim(ma: Int) = runCatching {
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, ma))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, ma))
    }

    /** Danh sách setPlaylist / updatePlaylist; trả true nếu video đang xem đổi (cần mở video mới). */
    @Synchronized fun datDanhSach(du: JSONObject): Boolean {
        val id = du.optString("videoId")
        ds = du.optString("videoIds").split(',').map { it.trim() }.filter { MA_VIDEO.matches(it) }
        listId = du.optString("listId").takeIf { it.isNotEmpty() }
        val cu = chiSo
        chiSo = du.optString("currentIndex").toIntOrNull()?.takeIf { it in ds.indices } ?: ds.indexOf(id).coerceAtLeast(0)
        return id != videoId || cu != chiSo || trangThai == DUNG || trangThai == KHONG
    }

    @Synchronized fun capNhatDanhSach(du: JSONObject) {
        ds = du.optString("videoIds").split(',').map { it.trim() }.filter { MA_VIDEO.matches(it) }
        videoId?.let { id -> ds.indexOf(id).takeIf { it >= 0 }?.let { chiSo = it } }
    }

    /** App đang ở trước màn hình (thống kê dùng app, không được thì dịch vụ trợ năng). */
    fun appTren(): String? = lenTruoc(System.currentTimeMillis() - 24 * 3_600_000L)?.goi ?: goiTren

    /**
     * Mở video [id] từ giây [tu]. [trenTruoc] = app đang ở trước lúc video tới, khi video phải chờ tivi hỏi duyệt (lúc mở
     * thì hộp hỏi của app này mới là app ở trước).
     */
    @Synchronized fun phat(id: String, tu: Double, trenTruoc: String? = null): Boolean {
        if (!MA_VIDEO.matches(id)) return false
        // Trước khi mở: app nào đang ở trước (theo thống kê dùng app; không đọc được thì theo dịch vụ trợ năng), SmartTube có
        // đang nghe nền không, và launcher nào để về lại (màn bảo vệ không phải app: vẫn là launcher bên dưới).
        val luc = System.currentTimeMillis()
        val tk = lenTruoc(luc - 24 * 3_600_000L)
        val tren = trenTruoc ?: tk?.goi ?: goiTren
        val nen = dangNgheNen(tren, daCast = videoId != null)
        val ve = tren?.takeIf { laHome(it) } ?: tk?.launcher ?: launcherCuoi
        Log.i(Dial.NHAN, "video mới: đang hiện $tren (${if (tk != null) "thống kê" else "trợ năng"}), nghe nền: $nen, về: $ve")
        videoId = id
        viTriGoc = tu
        moc = SystemClock.elapsedRealtime()
        trangThai = PHAT
        cpn = UUID.randomUUID().toString().replace("-", "").take(16)
        ttTheo = PHAT
        // SmartTube tạo / đổi phiên media khi bắt đầu phát.
        tay.postDelayed({ theoDoi() }, 3_000)
        val ok = mo(id, tu)
        if (ok && nen) choVeNen(ve, id, luc - 500, coThongKe = tk != null)
        return ok
    }

    fun tiep(): Boolean = synchronized(this) { ds.getOrNull(chiSo + 1)?.also { chiSo++ } }?.let { phat(it, 0.0) } ?: false
    fun truoc(): Boolean = synchronized(this) { ds.getOrNull(chiSo - 1)?.also { chiSo-- } }?.let { phat(it, 0.0) } ?: false

    @Synchronized fun tamDung() {
        phien()?.transportControls?.pause() ?: phim(KeyEvent.KEYCODE_MEDIA_PAUSE)
        viTriGoc = viTri(); trangThai = TAM_DUNG
    }

    @Synchronized fun tiepTuc() {
        phien()?.transportControls?.play() ?: phim(KeyEvent.KEYCODE_MEDIA_PLAY)
        moc = SystemClock.elapsedRealtime(); trangThai = PHAT
    }

    @Synchronized fun tua(giay: Double) {
        val id = videoId ?: return
        val p = phien()
        if (p != null) p.transportControls.seekTo((giay * 1000).toLong())
        else {
            // Không đọc được phiên phát (tivi ít RAM): tua bằng cách mở lại video từ giây mới; đang nghe nền thì cho về nền như
            // phat, không thì SmartTube lên màn hình và ở luôn đó.
            val luc = System.currentTimeMillis()
            val tk = lenTruoc(luc - 24 * 3_600_000L)
            val tren = tk?.goi ?: goiTren
            val nen = dangNgheNen(tren, daCast = true)
            val ve = tren?.takeIf { laHome(it) } ?: tk?.launcher ?: launcherCuoi
            if (mo(id, giay) && nen) choVeNen(ve, id, luc - 500, coThongKe = tk != null)
        }
        viTriGoc = giay; moc = SystemClock.elapsedRealtime()
        if (p == null) trangThai = PHAT
    }

    @Synchronized fun dung() {
        phien()?.transportControls?.stop() ?: phim(KeyEvent.KEYCODE_MEDIA_STOP)
        trangThai = DUNG; viTriGoc = 0.0; videoId = null
    }

    /** Âm lượng 0–100 và tắt tiếng của luồng nhạc / video. */
    fun amLuong(): Pair<Int, Boolean> {
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return am.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max to am.isStreamMute(AudioManager.STREAM_MUSIC)
    }

    fun datAmLuong(phanTram: Int, tat: Boolean) = runCatching {
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, (phanTram.coerceIn(0, 100) * max + 50) / 100, 0)
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, if (tat) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE, 0)
    }

    // ---------- Trạng thái báo điện thoại ----------

    @Synchronized private fun viTri(): Double {
        val st = phien()?.playbackState
        if (st != null && st.position >= 0 && st.state != PlaybackState.STATE_NONE) {
            val them = if (st.state == PlaybackState.STATE_PLAYING) (SystemClock.elapsedRealtime() - st.lastPositionUpdateTime) * st.playbackSpeed else 0f
            return (st.position + them) / 1000.0
        }
        return if (trangThai == PHAT) viTriGoc + (SystemClock.elapsedRealtime() - moc) / 1000.0 else viTriGoc
    }

    private fun thoiLuong(): Double =
        (phien()?.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L).coerceAtLeast(0L) / 1000.0

    private fun trangThaiHien(): Int = when (phien()?.playbackState?.state) {
        PlaybackState.STATE_PLAYING -> PHAT
        PlaybackState.STATE_PAUSED -> TAM_DUNG
        PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING -> DANG_TAI
        else -> trangThai
    }

    @Synchronized fun onStateChange(): Map<String, String> {
        val tl = thoiLuong()
        val tt = trangThaiHien()
        return mapOf(
            "state" to "$tt", "currentTime" to so(viTri()), "duration" to so(tl),
            "loadedTime" to so(if (tt == PHAT || tt == TAM_DUNG || tt == DANG_TAI) tl else 0.0),
            "seekableStartTime" to "0", "seekableEndTime" to so(tl), "cpn" to cpn)
    }

    @Synchronized fun nowPlaying(): Map<String, String> {
        val id = videoId ?: return emptyMap()
        return onStateChange() + buildMap {
            put("videoId", id)
            listId?.let { put("listId", it) }
            if (ds.isNotEmpty()) put("currentIndex", "$chiSo")
        }
    }

    @Synchronized fun truocSau(): Map<String, String> =
        mapOf("hasPrevious" to "${ds.isNotEmpty() && chiSo > 0}", "hasNext" to "${chiSo + 1 < ds.size}")

    fun onVolumeChanged(): Map<String, String> = amLuong().let { (v, tat) -> mapOf("volume" to "$v", "muted" to "$tat") }
}
