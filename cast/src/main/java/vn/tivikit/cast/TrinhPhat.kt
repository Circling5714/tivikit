package vn.tivikit.cast

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

    private fun tieuDe(p: MediaController?): String? = p?.metadata?.description?.title?.toString()

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

    /** SmartTube đang phát mà không ở trên màn hình: người xem đã bấm Home để nghe nền. */
    private fun dangNgheNen(): Boolean {
        val tren = goiTren ?: return false
        if (tren in GOI_SMARTTUBE) return false
        val st = phien()?.playbackState?.state
        return if (st != null) st == PlaybackState.STATE_PLAYING || st == PlaybackState.STATE_BUFFERING
        else videoId != null && am.isMusicActive
    }

    /**
     * Video mới tới lúc SmartTube đang phát nền: SmartTube phải lên màn hình để mở video, chờ video mới phát (có phiên media
     * thì chờ tên bài đổi khỏi [tieuDeCu]) rồi cho SmartTube rời màn hình như người xem bấm Home (cách nghe nền của SmartTube
     * chạy lúc đó), về launcher [ve]. Quá 12 giây thì cứ về.
     */
    private fun choVeNen(ve: String?, tieuDeCu: String?) {
        val het = SystemClock.elapsedRealtime() + 12_000
        tay.postDelayed(object : Runnable {
            override fun run() {
                val bay = SystemClock.elapsedRealtime()
                val tren = goiTren in GOI_SMARTTUBE
                val p = phien()
                val daPhat = if (p != null) p.playbackState?.state == PlaybackState.STATE_PLAYING && (tieuDeCu == null || tieuDe(p) != tieuDeCu)
                    else am.isMusicActive && bay - mocTren > 2_500
                when {
                    tren && daPhat && bay - mocTren > 1_000 -> veHome?.invoke(ve)
                    bay > het -> if (tren) veHome?.invoke(ve)
                    else -> tay.postDelayed(this, 500)
                }
            }
        }, 1_000)
    }

    private fun goiSmartTube(): String? =
        GOI_SMARTTUBE.firstOrNull { p -> runCatching { ctx.packageManager.getPackageInfo(p, 0) }.isSuccess }

    /**
     * Bật màn hình như bấm remote: tắt màn bảo vệ (không thì video phát phía sau nó), tivi đang chờ thì bật lên như app
     * YouTube gốc. Android 14+ cần quyền TURN_SCREEN_ON (TiviKit cấp qua ADB).
     */
    @Suppress("DEPRECATION")
    private fun danhThuc() = runCatching {
        ctx.getSystemService(PowerManager::class.java).newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
            "tivikit:cast").acquire(5_000)
    }

    /** Mở [id] từ giây [tu]: SmartTube nếu có, không thì app nào mở được link YouTube. */
    private fun mo(id: String, tu: Double): Boolean {
        danhThuc()
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

    @Synchronized fun phat(id: String, tu: Double): Boolean {
        if (!MA_VIDEO.matches(id)) return false
        videoId = id
        viTriGoc = tu
        moc = SystemClock.elapsedRealtime()
        trangThai = PHAT
        cpn = UUID.randomUUID().toString().replace("-", "").take(16)
        ttTheo = PHAT
        val nen = dangNgheNen()
        // Đang nghe nền: về lại launcher đang hiện (màn bảo vệ đang che thì launcher hiện gần nhất), cùng tên bài đang phát.
        val ve = goiTren?.takeIf { laHome(it) } ?: launcherCuoi
        val tieuDeCu = if (nen) tieuDe(phien()) else null
        // SmartTube tạo / đổi phiên media khi bắt đầu phát.
        tay.postDelayed({ theoDoi() }, 3_000)
        val ok = mo(id, tu)
        if (ok && nen) choVeNen(ve, tieuDeCu)
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
        if (p != null) p.transportControls.seekTo((giay * 1000).toLong()) else mo(id, giay)
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
