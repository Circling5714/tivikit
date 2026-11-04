package vn.tivikit.launcher

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Chỉ để có quyền đọc phiên phát nhạc / video của app khác (MediaSessionManager.getActiveSessions cần một dịch vụ đọc
 * thông báo đã được cho phép). Không đọc thông báo nào. TiviKit cho phép qua ADB: cmd notification allow_listener.
 */
class NgheNhac : NotificationListenerService()

/** Bài đang phát cho thẻ Đang phát: [anh] = ảnh bìa nếu app gửi, không thì vẽ biểu tượng app. */
data class BaiDangPhat(val goi: String, val tieuDe: String, val phu: String, val dangPhat: Boolean, val anh: ImageBitmap? = null)

/**
 * App nào đang phát nền, cho thẻ Đang phát (TiviKit Launcher 1.5.1, 4/11/2026).
 *
 * App không phát nền được (TV360, FPT Play…) về Home là tự dừng, nhưng phiên phát của nó vẫn còn: ở trạng thái tạm dừng,
 * có app vẫn báo "đang phát" dù đã im tiếng. Trước đây thẻ hiện cả các phiên đó, chỉ có tên và biểu tượng app. Giờ thẻ chỉ
 * hiện khi:
 * 1. Tivi đang có tiếng, một phiên báo đang phát, và màn Home đã hiện được [CHO_HOME]: tới lúc đó app không phát nền đã tự
 *    dừng. App đó được ghi nhận là đang phát nền.
 * 2. Hay app đã được ghi nhận phát nền trong [GIU] vừa qua, và chưa lên màn hình lại từ lúc đó (đang phát tiếp, đang tải
 *    bài sau, hay tạm dừng bằng giữ OK): thẻ còn để phát tiếp. Mở app lên màn hình là thôi, tới khi lại nghe thấy nó phát nền.
 */
class NgheNen {
    /** Một phiên phát, theo thứ tự Android trả về (phiên vừa phát lên đầu). */
    data class Phien(val goi: String, val trangThai: Int)

    /** Phiên thẻ hiện: vị trí trong danh sách, và có đang phát không (sóng nhạc hay dấu tạm dừng). */
    data class Chon(val chiSo: Int, val dangPhat: Boolean)

    /**
     * Màn Home hiện từ lúc nào (giờ máy, ms); null = đang ẩn. Lúc ẩn không ghi nhận gì: app đang phát có thể đang ở trên
     * màn hình.
     */
    @Volatile var homeTu: Long? = null

    /** Gói → lần gần nhất nghe thấy nó phát nền (giờ máy). */
    private val lucNghe = HashMap<String, Long>()

    /** Gói → lần nghe nền mà sau đó app đã lên màn hình (khỏi tra thống kê lại). */
    private val daLen = HashMap<String, Long>()

    /** Các gói đã nghe thấy phát nền (lệnh doc, để xem trên tivi thật). */
    @Synchronized fun daNghe(): List<String> = lucNghe.keys.sorted()

    /**
     * Phiên nên hiện trên thẻ; null = không có app nào đang phát nền.
     * - [coTieng]: tivi đang phát ra tiếng nhạc / video.
     * - [bay]: giờ máy.
     * - [lenSau] (gói, từ lúc): app có lên màn hình sau lúc đó không, theo thống kê sử dụng.
     */
    @Synchronized
    fun chon(ds: List<Phien>, coTieng: Boolean, bay: Long, lenSau: (String, Long) -> Boolean): Chon? {
        val tu = homeTu
        if (coTieng && tu != null && bay - tu >= CHO_HOME) {
            val dangPhat = ds.indices.filter { ds[it].trangThai == PlaybackState.STATE_PLAYING }
            // Có tiếng mà nhiều phiên cùng báo đang phát (phiên cũ của app không phát nền): app đã biết phát nền trước.
            val i = dangPhat.firstOrNull { ds[it].goi in lucNghe } ?: dangPhat.firstOrNull()
            if (i != null) {
                lucNghe[ds[i].goi] = bay
                return Chon(i, true)
            }
        }
        for ((i, p) in ds.withIndex()) {
            if (p.trangThai !in CON_THE) continue
            val nghe = lucNghe[p.goi] ?: continue
            if (bay - nghe > GIU || daLen[p.goi] == nghe) continue
            if (lenSau(p.goi, nghe)) {
                daLen[p.goi] = nghe
                continue
            }
            // Sóng nhạc khi đang phát và có tiếng (hay vừa có tiếng: lúc chuyển bài, lúc vừa bấm phát tiếp).
            return Chon(i, p.trangThai == PlaybackState.STATE_PLAYING && (coTieng || bay - nghe <= GIAN_DOAN))
        }
        return null
    }

    companion object {
        /** Chờ sau khi màn Home hiện: app không phát nền dừng ở onPause / onStop, có khi gần một giây sau. */
        const val CHO_HOME = 2_500L

        /** Thẻ của app phát nền đã tạm dừng (hay im tiếng) còn bao lâu. */
        const val GIU = 30 * 60_000L

        /** Im tiếng bao lâu thì thôi vẽ sóng nhạc. */
        const val GIAN_DOAN = 10_000L

        private val CON_THE = setOf(PlaybackState.STATE_PLAYING, PlaybackState.STATE_PAUSED, PlaybackState.STATE_BUFFERING)

        /** Dùng chung cho màn Home và lệnh doc (cùng tiến trình). */
        val chung = NgheNen()
    }
}

object DangPhat {
    /** Dòng phụ: "SmartTube · Alan Walker" (tên app, rồi nghệ sĩ / kênh nếu có). */
    fun dongPhu(tenApp: String, phu: String?): String = listOfNotNull(tenApp.takeIf { it.isNotBlank() }, phu?.trim()?.takeIf { it.isNotBlank() })
        .distinct().joinToString(" · ")

    /** Tiếng nhạc / video; tiếng bấm phím, thông báo, Trợ lý không tính. */
    private val TIENG = setOf(AudioAttributes.USAGE_MEDIA, AudioAttributes.USAGE_GAME, AudioAttributes.USAGE_UNKNOWN)

    /**
     * Tivi đang phát ra tiếng nhạc / video không (tắt tiếng tivi vẫn tính). Xét các trình phát đang chạy: Android cho app
     * thường thấy loại tiếng, không biết của app nào. Trợ lý Google đang trả lời thì không tính, kẻo phiên "đang phát" cũ của
     * app khác bị coi là phát nền. Máy không báo trình phát nào thì xét luồng nhạc.
     */
    fun coTieng(ctx: Context): Boolean {
        val am = ctx.getSystemService(AudioManager::class.java) ?: return false
        val ds = runCatching { am.activePlaybackConfigurations }.getOrNull().orEmpty()
        if (ds.any { runCatching { it.audioAttributes.usage }.getOrNull() in TIENG }) return true
        return ds.isEmpty() && runCatching { am.isMusicActive }.getOrDefault(false)
    }

    /** [goi] có lên màn hình sau [tu] (giờ máy) không, theo thống kê sử dụng. Chưa có quyền thì coi như không. */
    @Suppress("DEPRECATION")
    fun lenSau(ctx: Context, goi: String, tu: Long): Boolean = runCatching {
        val ds = ctx.getSystemService(UsageStatsManager::class.java)?.queryEvents(tu, System.currentTimeMillis())
        val e = UsageEvents.Event()
        var co = false
        while (!co && ds != null && ds.hasNextEvent()) {
            ds.getNextEvent(e)
            co = e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND && e.packageName == goi
        }
        co
    }.getOrDefault(false)

    /**
     * Phiên app đang phát nền (xem [NgheNen]); null nếu không có. Vẫn là bài [cu] thì dùng lại ảnh bìa của nó (đọc mỗi 2
     * giây, khỏi giải mã lại ảnh). Ném SecurityException khi chưa được cho phép đọc. Box RAM thấp (ro.config.low_ram, VD TX3
     * Mini) không bao giờ cho: Android bỏ qua cmd notification allow_listener trên máy RAM thấp.
     */
    fun doc(ctx: Context, tenApp: (String) -> String, cu: BaiDangPhat? = null): Pair<BaiDangPhat, MediaController>? {
        val msm = ctx.getSystemService(MediaSessionManager::class.java) ?: return null
        val ds = msm.getActiveSessions(ComponentName(ctx, NgheNhac::class.java))
        if (ds.isEmpty()) return null
        val phien = ds.map { NgheNen.Phien(it.packageName, it.playbackState?.state ?: PlaybackState.STATE_NONE) }
        val chon = NgheNen.chung.chon(phien, coTieng(ctx), System.currentTimeMillis()) { g, tu -> lenSau(ctx, g, tu) } ?: return null
        val c = ds[chon.chiSo]
        val md = c.metadata
        val ten = tenApp(c.packageName)
        var tieuDeBai = (md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: md?.getString(MediaMetadata.METADATA_KEY_TITLE))?.trim()
            ?.takeIf { it.isNotBlank() }
        var phu = if (tieuDeBai == null) null else md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM)
        // SmartTube mở video từ đường link (video cast sang) không gửi tên bài, chỉ có mã video: tra tên theo mã. Chưa tra
        // được thì thẻ ghi tên app.
        if (tieuDeBai == null && c.packageName in TenVideo.GOI_SMARTTUBE) {
            md?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)?.let { TenVideo.lay(it) }?.let { (t, k) -> tieuDeBai = t; phu = k }
        }
        val tieuDe = tieuDeBai ?: ten
        val anh = if (cu != null && cu.goi == c.packageName && cu.tieuDe == tieuDe) cu.anh else md?.let { m ->
            listOf(MediaMetadata.METADATA_KEY_ART, MediaMetadata.METADATA_KEY_ALBUM_ART, MediaMetadata.METADATA_KEY_DISPLAY_ICON)
                .firstNotNullOfOrNull { k -> runCatching { m.getBitmap(k) }.getOrNull() }
        }?.let { b -> runCatching { thu(b).asImageBitmap() }.getOrNull() }
        return BaiDangPhat(c.packageName, tieuDe, if (tieuDeBai == null) "" else dongPhu(ten, phu), chon.dangPhat, anh) to c
    }

    /** Ảnh bìa có thể rất to (ảnh nền video): thu về tối đa 128 điểm ảnh. */
    private fun thu(b: Bitmap): Bitmap {
        val dai = maxOf(b.width, b.height)
        if (dai <= 128) return b
        return Bitmap.createScaledBitmap(b, b.width * 128 / dai, b.height * 128 / dai, true)
    }
}
