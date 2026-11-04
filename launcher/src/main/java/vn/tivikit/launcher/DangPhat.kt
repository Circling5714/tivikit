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
import android.os.Build
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
 * App không phát nền được (TizenTube, IPTV…) về Home là tự dừng, nhưng phiên phát của nó có thể còn: ở trạng thái tạm dừng,
 * có app vẫn báo "đang phát" dù đã im tiếng. Trước đây thẻ hiện cả các phiên đó, chỉ có tên và biểu tượng app. Giờ thẻ chỉ
 * hiện khi:
 * 1. Tivi đang có tiếng, một phiên báo đang phát, và app của phiên đó đã rời màn hình (mọi màn của nó đã dừng hẳn) mà vẫn
 *    phát thêm [Roi.cho]. App đó được ghi nhận là đang phát nền.
 * 2. Hay app đã được ghi nhận phát nền trong [GIU] vừa qua, và chưa lên màn hình lại từ lúc đó (đang phát tiếp, đang tải
 *    bài sau, hay tạm dừng bằng giữ OK): thẻ còn để phát tiếp. Mở app lên màn hình là thôi, tới khi lại nghe thấy nó phát nền.
 *
 * Không đếm từ lúc màn Home hiện: Android chỉ dừng hẳn app trước (onStop, lúc nhiều app mới dừng phát) khi màn Home đã rảnh,
 * có khi 2 giây sau đó, có khi lâu hơn (đo trên Skyworth với TizenTube, 4/11/2026).
 */
class NgheNen {
    /** Một phiên phát, theo thứ tự Android trả về (phiên vừa phát lên đầu). */
    data class Phien(val goi: String, val trangThai: Int)

    /** Phiên thẻ hiện: vị trí trong danh sách, và có đang phát không (sóng nhạc hay dấu tạm dừng). */
    data class Chon(val chiSo: Int, val dangPhat: Boolean)

    /**
     * App đã rời màn hình: [luc] = giờ sự kiện dừng (theo thống kê sử dụng; 0 = không có sự kiện nào gần đây, đã ở nền từ
     * lâu), [cho] = còn phát bao lâu sau khi thấy nó dừng thì tính là phát nền.
     */
    data class Roi(val luc: Long, val cho: Long)

    /** Lần ghi nhận phát nền: lúc nghe thấy (giờ máy) và lần dừng của app lúc đó (để biết sau đó app có lên màn hình lại). */
    private data class GhiNhan(val luc: Long, val dung: Long)

    /**
     * Màn Home đang hiện. Chỉ ghi nhận lúc đó: lệnh doc có thể được gọi lúc app khác đang mở, mà màn của nó hiện đã lâu
     * (sự kiện trôi khỏi khoảng tra thống kê).
     */
    @Volatile var homeHien = false

    private val ghiNhan = HashMap<String, GhiNhan>()

    /** Gói → (lần dừng, lúc launcher thấy lần dừng đó). */
    private val thayDung = HashMap<String, Pair<Long, Long>>()

    /** Các gói đã nghe thấy phát nền (lệnh doc, để xem trên tivi thật). */
    @Synchronized fun daNghe(): List<String> = ghiNhan.keys.sorted()

    /**
     * Phiên nên hiện trên thẻ; null = không có app nào đang phát nền.
     * - [coTieng]: tivi đang phát ra tiếng nhạc / video.
     * - [bay]: giờ máy.
     * - [roi] (gói): app đã rời màn hình chưa (null = còn một màn đang hiện hay tạm dừng), theo thống kê sử dụng.
     */
    @Synchronized
    fun chon(ds: List<Phien>, coTieng: Boolean, bay: Long, roi: (String) -> Roi?): Chon? {
        val nho = HashMap<String, Roi?>()
        fun roiCua(goi: String): Roi? = if (goi in nho) nho[goi] else roi(goi).also { nho[goi] = it }

        /** Lần ghi nhận còn dùng được: chưa quá [GIU], app vẫn ở nền và chưa có lần dừng nào mới hơn (tức chưa lên màn hình lại). */
        fun conNghe(goi: String): GhiNhan? {
            val g = ghiNhan[goi] ?: return null
            if (bay - g.luc > GIU) return null
            val r = roiCua(goi) ?: return null
            // Sự kiện dừng cũ trôi ra khỏi khoảng tra (luc = 0) không phải lần dừng mới.
            return g.takeIf { r.luc == 0L || r.luc == g.dung }
        }

        if (coTieng && homeHien) {
            val dangPhat = ds.indices.filter { ds[it].trangThai == PlaybackState.STATE_PLAYING }
            // Có tiếng mà nhiều phiên cùng báo đang phát (phiên cũ của app không phát nền): app đã ghi nhận trước.
            for (i in dangPhat.sortedBy { if (conNghe(ds[it].goi) != null) 0 else 1 }) {
                val goi = ds[i].goi
                val g = conNghe(goi)
                if (g != null) {
                    ghiNhan[goi] = g.copy(luc = bay)
                    return Chon(i, true)
                }
                val r = roiCua(goi) ?: continue
                // Đếm từ lúc launcher thấy app đã dừng (giờ của thống kê có thể lệch giờ máy tới 2 giây); app dừng đã lâu
                // thì khỏi chờ.
                val thay = thayDung[goi]?.takeIf { it.first == r.luc } ?: (r.luc to bay).also { thayDung[goi] = it }
                if (r.luc == 0L || bay - r.luc >= r.cho + LECH_GIO || bay - thay.second >= r.cho) {
                    ghiNhan[goi] = GhiNhan(bay, r.luc)
                    return Chon(i, true)
                }
            }
        }
        for ((i, p) in ds.withIndex()) {
            if (p.trangThai !in CON_THE) continue
            val g = conNghe(p.goi) ?: continue
            // Sóng nhạc khi đang phát và có tiếng (hay vừa có tiếng: lúc chuyển bài, lúc vừa bấm phát tiếp).
            return Chon(i, p.trangThai == PlaybackState.STATE_PLAYING && (coTieng || bay - g.luc <= GIAN_DOAN))
        }
        return null
    }

    companion object {
        /** App rời màn hình (mọi màn đã dừng hẳn, Android 10+) mà còn phát thêm bấy lâu: phát nền. */
        const val CHO_DUNG = 2_500L

        /** Android 9 không báo lúc dừng hẳn, chỉ báo lúc tạm dừng: chờ lâu hơn (Android dừng hẳn app trước muộn nhất 10 giây). */
        const val CHO_TAM_DUNG = 8_000L

        /** Giờ sự kiện của thống kê sử dụng có thể lệch giờ máy (Android chỉ chỉnh khi lệch quá 2 giây). */
        const val LECH_GIO = 3_000L

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

    /** Sự kiện của từng màn (activity) trong thống kê sử dụng: hiện, tạm dừng, dừng hẳn (Android 10+). */
    private const val MAN_HIEN = 1
    private const val MAN_TAM_DUNG = 2
    private const val MAN_DUNG = 23

    /** Tra thống kê sử dụng bao xa: sự kiện cũ hơn thì app đã ở nền từ lâu. */
    private const val CUA_SO = 10 * 60_000L

    /**
     * App [goi] đã rời màn hình chưa, theo thống kê sử dụng 10 phút qua: mọi màn của nó đã dừng hẳn (Android 9 chỉ biết tạm
     * dừng). null = còn màn đang hiện hay tạm dừng. Không có sự kiện nào (ở nền từ lâu, hay chưa có quyền xem thống kê) thì
     * luc = 0.
     */
    fun roiManHinh(ctx: Context, goi: String): NgheNen.Roi? {
        val coDung = Build.VERSION.SDK_INT >= 29
        val cho = if (coDung) NgheNen.CHO_DUNG else NgheNen.CHO_TAM_DUNG
        val bay = System.currentTimeMillis()
        val ds = runCatching { ctx.getSystemService(UsageStatsManager::class.java)?.queryEvents(bay - CUA_SO, bay + 60_000L) }.getOrNull()
            ?: return NgheNen.Roi(0L, cho)
        val man = HashMap<String, Int>()
        var luc = 0L
        val e = UsageEvents.Event()
        while (ds.hasNextEvent()) {
            ds.getNextEvent(e)
            if (e.packageName != goi) continue
            val loai = e.eventType
            if (loai == MAN_HIEN || loai == MAN_TAM_DUNG || (coDung && loai == MAN_DUNG)) {
                man[e.className.orEmpty()] = loai
                luc = e.timeStamp
            }
        }
        if (man.isEmpty()) return NgheNen.Roi(0L, cho)
        val het = if (coDung) MAN_DUNG else MAN_TAM_DUNG
        return if (man.values.all { it == het }) NgheNen.Roi(luc, cho) else null
    }

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
        val chon = NgheNen.chung.chon(phien, coTieng(ctx), System.currentTimeMillis()) { g -> roiManHinh(ctx, g) } ?: return null
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
