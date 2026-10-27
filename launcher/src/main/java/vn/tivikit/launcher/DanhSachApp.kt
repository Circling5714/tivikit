package vn.tivikit.launcher

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import java.io.File
import java.text.Collator
import java.util.Locale

/** Một app mở được trên tivi. [capNhat] = lúc cài / cập nhật (đổi thì vẽ lại ảnh). */
data class AppTv(val goi: String, val ten: String, val heThong: Boolean = false, val capNhat: Long = 0)

/** Ảnh của app: banner ngang (app tivi) hoặc biểu tượng trên nền màu lấy từ chính biểu tượng. */
class AnhApp(val banner: ImageBitmap?, val bieuTuong: ImageBitmap?, val mau: Color)

object DanhSachApp {
    /** App có màn mở (app tivi trước, app điện thoại cài thêm sau), xếp theo tên kiểu tiếng Việt; bỏ các gói [boQua]. */
    fun doc(pm: PackageManager, boQua: Set<String>): List<AppTv> {
        val ds = LinkedHashMap<String, AppTv>()
        for (loai in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            for (ri in pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(loai), 0)) {
                val ai = ri.activityInfo ?: continue
                val goi = ai.packageName
                if (goi in boQua || goi in ds) continue
                val ten = runCatching { ri.loadLabel(pm).toString().trim() }.getOrDefault("").ifEmpty { goi }
                val capNhat = runCatching { pm.getPackageInfo(goi, 0).lastUpdateTime }.getOrDefault(0L)
                ds[goi] = AppTv(goi, ten, (ai.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0, capNhat)
            }
        }
        return xepTen(ds.values)
    }

    /** Đổi tên các app TiviKit đặt tên riêng ([ten]: gói → tên, như tên kênh của ô Kênh nhanh) rồi xếp lại theo tên. */
    fun doiTen(ds: List<AppTv>, ten: Map<String, String>): List<AppTv> =
        if (ds.none { it.goi in ten }) ds else xepTen(ds.map { a -> ten[a.goi]?.let { a.copy(ten = it) } ?: a })

    private fun xepTen(ds: Collection<AppTv>): List<AppTv> {
        val xep = Collator.getInstance(Locale("vi"))
        return ds.sortedWith { a, b -> xep.compare(a.ten, b.ten) }
    }

    fun ieMo(pm: PackageManager, goi: String): Intent? = pm.getLeanbackLaunchIntentForPackage(goi) ?: pm.getLaunchIntentForPackage(goi)

    /**
     * Banner [rong]×(rong·9/16) điểm ảnh — ảnh riêng TiviKit gửi ([rieng]: logo kênh của ô Kênh nhanh) hoặc banner của app
     * nếu có —, biểu tượng [co]×[co] và màu nền cho ô không có banner.
     */
    fun anh(pm: PackageManager, goi: String, rong: Int = 256, co: Int = 96, rieng: File? = null): AnhApp {
        val banner = runCatching { rieng?.takeIf { it.isFile }?.let { anhRieng(it, rong, rong * 9 / 16) } }.getOrNull() ?: runCatching {
            pm.getLeanbackLaunchIntentForPackage(goi)?.component?.let { pm.getActivityBanner(it) } ?: pm.getApplicationBanner(goi)
        }.getOrNull()?.let { runCatching { it.toBitmap(rong, rong * 9 / 16) }.getOrNull() }
        val bieuTuong = runCatching { pm.getApplicationIcon(goi).toBitmap(co, co) }.getOrNull()
        return AnhApp(banner?.asImageBitmap(), bieuTuong?.asImageBitmap(), bieuTuong?.let { Color(mauNen(it)) } ?: Color(0xFF3A4060))
    }

    /** Ảnh riêng phủ kín [w]×[h] (cắt phần thừa ở giữa, như ảnh nền); null nếu không giải mã được. */
    private fun anhRieng(f: File, w: Int, h: Int): Bitmap? {
        val goc = AnhNen.giaiMa({ f.inputStream() }, w, h) ?: return null
        return AnhNen.phuKin(goc, w, h).also { if (it !== goc) goc.recycle() }
    }

    /** Màu trung bình phần không trong suốt của biểu tượng, chỉnh về độ sáng vừa để chữ trắng đọc rõ. */
    private fun mauNen(b: Bitmap): Int {
        val nho = Bitmap.createScaledBitmap(b, 12, 12, true)
        val px = IntArray(144)
        nho.getPixels(px, 0, 12, 0, 0, 12, 12)
        return mauVua(px)
    }

    /** Trung bình các điểm đủ đậm (alpha > 128) rồi kéo độ sáng HSV về 0,38–0,62 (không phụ thuộc Android). */
    fun mauVua(px: IntArray): Int {
        var r = 0L; var g = 0L; var b = 0L; var n = 0
        for (p in px) {
            if ((p ushr 24) < 128) continue
            r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF; n++
        }
        if (n == 0) return 0xFF3A4060.toInt()
        val hsv = rgbSangHsv((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
        hsv[1] = hsv[1].coerceAtMost(0.75f)
        hsv[2] = hsv[2].coerceIn(0.38f, 0.62f)
        return hsvSangRgb(hsv)
    }

    private fun rgbSangHsv(r: Int, g: Int, b: Int): FloatArray {
        val rf = r / 255f; val gf = g / 255f; val bf = b / 255f
        val max = maxOf(rf, gf, bf); val min = minOf(rf, gf, bf); val d = max - min
        val h = when {
            d == 0f -> 0f
            max == rf -> 60f * (((gf - bf) / d) % 6f)
            max == gf -> 60f * ((bf - rf) / d + 2f)
            else -> 60f * ((rf - gf) / d + 4f)
        }.let { if (it < 0f) it + 360f else it }
        return floatArrayOf(h, if (max == 0f) 0f else d / max, max)
    }

    private fun hsvSangRgb(hsv: FloatArray): Int {
        val (h, s, v) = Triple(hsv[0], hsv[1], hsv[2])
        val c = v * s
        val x = c * (1 - kotlin.math.abs((h / 60f) % 2f - 1))
        val m = v - c
        val (r, g, b) = when ((h / 60f).toInt().coerceIn(0, 5)) {
            0 -> Triple(c, x, 0f); 1 -> Triple(x, c, 0f); 2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c); 4 -> Triple(x, 0f, c); else -> Triple(c, 0f, x)
        }
        fun k(f: Float) = ((f + m) * 255f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (k(r) shl 16) or (k(g) shl 8) or k(b)
    }
}
