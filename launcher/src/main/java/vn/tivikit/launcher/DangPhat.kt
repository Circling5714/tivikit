package vn.tivikit.launcher

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
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

object DangPhat {
    /**
     * Phiên nên hiện: đang phát trước, rồi đang tạm dừng / đang tải; [trangThai] theo thứ tự Android trả về (mới dùng
     * trước). null = không có gì để hiện.
     */
    fun chonPhien(trangThai: List<Int>): Int? {
        val i = trangThai.indexOfFirst { it == PlaybackState.STATE_PLAYING }
        if (i >= 0) return i
        val j = trangThai.indexOfFirst { it == PlaybackState.STATE_PAUSED || it == PlaybackState.STATE_BUFFERING }
        return j.takeIf { it >= 0 }
    }

    /** Dòng phụ: "SmartTube · Alan Walker" (tên app, rồi nghệ sĩ / kênh nếu có). */
    fun dongPhu(tenApp: String, phu: String?): String = listOfNotNull(tenApp.takeIf { it.isNotBlank() }, phu?.trim()?.takeIf { it.isNotBlank() })
        .distinct().joinToString(" · ")

    /**
     * Phiên đang có trên tivi; null nếu không có phiên nào. Vẫn là bài [cu] thì dùng lại ảnh bìa của nó (đọc mỗi 2 giây,
     * khỏi giải mã lại ảnh). Ném SecurityException khi chưa được cho phép đọc — box RAM thấp (ro.config.low_ram, VD TX3
     * Mini) không bao giờ cho: Android bỏ qua cmd notification allow_listener trên máy RAM thấp.
     */
    fun doc(ctx: Context, tenApp: (String) -> String, cu: BaiDangPhat? = null): Pair<BaiDangPhat, MediaController>? {
        val msm = ctx.getSystemService(MediaSessionManager::class.java) ?: return null
        val ds = msm.getActiveSessions(ComponentName(ctx, NgheNhac::class.java))
        val i = chonPhien(ds.map { it.playbackState?.state ?: PlaybackState.STATE_NONE }) ?: return null
        val c = ds[i]
        val md = c.metadata
        val ten = tenApp(c.packageName)
        // SmartTube trên Skyworth phát mà không gửi tên bài (metadata chỉ có thời lượng, ảnh…): thẻ ghi tên app.
        val tieuDeBai = (md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: md?.getString(MediaMetadata.METADATA_KEY_TITLE))?.trim()
        val tieuDe = tieuDeBai?.takeIf { it.isNotBlank() } ?: ten
        val phu = if (tieuDeBai.isNullOrBlank()) null else md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM)
        val anh = if (cu != null && cu.goi == c.packageName && cu.tieuDe == tieuDe) cu.anh else md?.let { m ->
            listOf(MediaMetadata.METADATA_KEY_ART, MediaMetadata.METADATA_KEY_ALBUM_ART, MediaMetadata.METADATA_KEY_DISPLAY_ICON)
                .firstNotNullOfOrNull { k -> runCatching { m.getBitmap(k) }.getOrNull() }
        }?.let { b -> runCatching { thu(b).asImageBitmap() }.getOrNull() }
        val dangPhat = c.playbackState?.state == PlaybackState.STATE_PLAYING
        return BaiDangPhat(c.packageName, tieuDe, if (tieuDeBai.isNullOrBlank()) "" else dongPhu(ten, phu), dangPhat, anh) to c
    }

    /** Ảnh bìa có thể rất to (ảnh nền video): thu về tối đa 128 điểm ảnh. */
    private fun thu(b: Bitmap): Bitmap {
        val dai = maxOf(b.width, b.height)
        if (dai <= 128) return b
        return Bitmap.createScaledBitmap(b, b.width * 128 / dai, b.height * 128 / dai, true)
    }
}
