package vn.tivikit.dulieu

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.math.roundToInt

/**
 * Ảnh nền riêng từ điện thoại (góp ý 23/10/2026): thu về 1920×1080, JPEG 95. Canh ảnh (25/10/2026, học LG TV
 * Vibehack): giữ ảnh gốc đã thu nhỏ, người dùng kéo / chụm trên khung xem trước để chọn phần ảnh nằm trong khung 16:9
 * (chỗ đồng hồ, dock che); mặc định cắt giữa như trước. Hoặc vừa khung, không cắt (vuaKhung).
 */
object AnhNen {
    private const val RONG = 1920
    private const val CAO = 1080
    /** 95 thay vì 85 (CleanTV 2.10.9, ảnh riêng kém nét): ảnh 1080p chỉ to thêm vài trăm KB. */
    private const val CHAT_LUONG = 95
    /** Cạnh dài tối đa của ảnh gốc giữ lại để canh: phóng tới ZOOM_MAX vẫn đủ nét cho 1920×1080. */
    private const val GOC_MAX = 3000
    const val ZOOM_MAX = 3f

    /** Khung cắt: [zoom] ≥ 1 so với vừa phủ kín 16:9; tâm khung tại ([x], [y]) theo tỉ lệ 0..1 của ảnh gốc. */
    data class Khung(val zoom: Float = 1f, val x: Float = 0.5f, val y: Float = 0.5f)

    /** Vùng cắt trên ảnh gốc [w]×[h]: (trái, trên, rộng, cao), đúng tỉ lệ 16:9 và nằm trọn trong ảnh. */
    fun vung(w: Int, h: Int, k: Khung): FloatArray {
        val rong0 = minOf(w.toFloat(), h * RONG.toFloat() / CAO)
        val z = k.zoom.coerceIn(1f, ZOOM_MAX)
        val rong = rong0 / z
        val cao = rong * CAO / RONG
        val cx = (k.x * w).coerceIn(rong / 2, w - rong / 2)
        val cy = (k.y * h).coerceIn(cao / 2, h - cao / 2)
        return floatArrayOf(cx - rong / 2, cy - cao / 2, rong, cao)
    }

    /** Khung đã kẹp: tâm dời vào trong để vùng cắt không lòi ra ngoài ảnh. */
    fun kep(w: Int, h: Int, k: Khung): Khung {
        val v = vung(w, h, k)
        return Khung(k.zoom.coerceIn(1f, ZOOM_MAX), (v[0] + v[2] / 2) / w, (v[1] + v[3] / 2) / h)
    }

    /** Kéo ảnh [dx], [dy] điểm ảnh trên khung xem trước rộng [rongXem]: ảnh đi theo ngón tay, khung đi ngược lại. */
    fun keo(w: Int, h: Int, k: Khung, dx: Float, dy: Float, rongXem: Float): Khung {
        val v = vung(w, h, k)
        val ty = rongXem / v[2]
        return kep(w, h, Khung(k.zoom, (v[0] + v[2] / 2 - dx / ty) / w, (v[1] + v[3] / 2 - dy / ty) / h))
    }

    /** Chụm phóng [g] lần quanh điểm ([px], [py]) của khung xem trước: điểm dưới ngón tay đứng yên. */
    fun phong(w: Int, h: Int, k: Khung, g: Float, px: Float, py: Float, rongXem: Float): Khung {
        val v = vung(w, h, k)
        val ty = rongXem / v[2]
        val gx = v[0] + px / ty; val gy = v[1] + py / ty
        val z = (k.zoom * g).coerceIn(1f, ZOOM_MAX)
        val rong = minOf(w.toFloat(), h * RONG.toFloat() / CAO) / z
        val cao = rong * CAO / RONG
        val ty2 = rongXem / rong
        return kep(w, h, Khung(z, (gx - px / ty2 + rong / 2) / w, (gy - py / ty2 + cao / 2) / h))
    }

    /** Ảnh gốc để canh từ ảnh người dùng chọn: cạnh dài ≤ GOC_MAX, JPEG 90 (Android 9+ tự xoay theo EXIF). */
    fun goc(ctx: Context, uri: Uri): ByteArray? = runCatching {
        val bm: Bitmap = if (Build.VERSION.SDK_INT >= 28) giaiMa(ImageDecoder.createSource(ctx.contentResolver, uri))
        else {
            val bien = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bien) }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = chia(bien.outWidth, bien.outHeight) }) }
                ?: return null
        }
        nen(bm, 90)
    }.getOrNull()

    /** Như goc, cho ảnh đọc thẳng thành byte (ảnh trên tivi / USB, ThaoTacTv.docFileTv). */
    fun gocByte(du: ByteArray): ByteArray? = runCatching {
        val bm: Bitmap = if (Build.VERSION.SDK_INT >= 28) giaiMa(ImageDecoder.createSource(ByteBuffer.wrap(du)))
        else {
            val bien = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(du, 0, du.size, bien)
            BitmapFactory.decodeByteArray(du, 0, du.size, BitmapFactory.Options().apply { inSampleSize = chia(bien.outWidth, bien.outHeight) }) ?: return null
        }
        nen(bm, 90)
    }.getOrNull()

    /**
     * Vừa khung (CleanTV 3.0.0, góp ý hieuinter): cả ảnh gốc nằm trọn trong 1920×1080, không cắt; phần trống hai bên /
     * trên dưới là chính ảnh đó phủ kín, làm mờ (thu nhỏ 1/24 rồi phóng lại) và tối đi, như khung ảnh dọc trên điện thoại.
     */
    fun vuaKhung(goc: ByteArray): ByteArray? = runCatching {
        val bien = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(goc, 0, goc.size, bien)
        val anh = BitmapFactory.decodeByteArray(goc, 0, goc.size,
            BitmapFactory.Options().apply { inSampleSize = maxOf(1, minOf(bien.outWidth / RONG, bien.outHeight / CAO)) }) ?: return null
        val ty = maxOf(RONG.toFloat() / anh.width, CAO.toFloat() / anh.height)
        val pw = (anh.width * ty).toInt().coerceAtLeast(RONG); val ph = (anh.height * ty).toInt().coerceAtLeast(CAO)
        val phu = Bitmap.createBitmap(Bitmap.createScaledBitmap(anh, pw, ph, true), (pw - RONG) / 2, (ph - CAO) / 2, RONG, CAO)
        val mo = Bitmap.createScaledBitmap(Bitmap.createScaledBitmap(phu, RONG / 24, CAO / 24, true), RONG, CAO, true)
        val kq = mo.copy(Bitmap.Config.ARGB_8888, true)
        val cv = android.graphics.Canvas(kq)
        cv.drawColor(0x66000000)
        val tv = minOf(RONG.toFloat() / anh.width, CAO.toFloat() / anh.height)
        val w = (anh.width * tv).toInt(); val h = (anh.height * tv).toInt()
        cv.drawBitmap(anh, null, Rect((RONG - w) / 2, (CAO - h) / 2, (RONG + w) / 2, (CAO + h) / 2), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
        ByteArrayOutputStream().use { o -> kq.compress(Bitmap.CompressFormat.JPEG, CHAT_LUONG, o); o.toByteArray() }
    }.getOrNull()

    /** Cắt ảnh gốc theo khung [k], thu về 1920×1080 JPEG 95 (chỉ giải mã phần cần cắt). */
    fun cat(goc: ByteArray, k: Khung): ByteArray? = runCatching {
        val bien = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(goc, 0, goc.size, bien)
        val w = bien.outWidth; val h = bien.outHeight
        val v = vung(w, h, k)
        val r = Rect(v[0].roundToInt().coerceIn(0, w - 1), v[1].roundToInt().coerceIn(0, h - 1),
            (v[0] + v[2]).roundToInt().coerceIn(1, w), (v[1] + v[3]).roundToInt().coerceIn(1, h))
        val giai = if (Build.VERSION.SDK_INT >= 31) BitmapRegionDecoder.newInstance(goc, 0, goc.size)
            else @Suppress("DEPRECATION") BitmapRegionDecoder.newInstance(goc, 0, goc.size, false)
        val phan = try {
            giai!!.decodeRegion(r, BitmapFactory.Options().apply { inSampleSize = maxOf(1, minOf(r.width() / RONG, r.height() / CAO)) })
        } finally { giai?.recycle() }
        nen(Bitmap.createScaledBitmap(phan, RONG, CAO, true), CHAT_LUONG)
    }.getOrNull()

    @androidx.annotation.RequiresApi(28)
    private fun giaiMa(nguon: ImageDecoder.Source): Bitmap = ImageDecoder.decodeBitmap(nguon) { dec, info, _ ->
        dec.setTargetSampleSize(chia(info.size.width, info.size.height))
        dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }

    /** Hệ số thu nhỏ khi giải mã: cạnh dài còn khoảng GOC_MAX (giải mã chỉ thu theo luỹ thừa 2, nên có thể lớn hơn chút). */
    private fun chia(w: Int, h: Int): Int = maxOf(1, maxOf(w, h) / GOC_MAX)

    private fun nen(bm: Bitmap, chatLuong: Int): ByteArray {
        val dai = maxOf(bm.width, bm.height)
        val nho = if (dai <= GOC_MAX) bm else Bitmap.createScaledBitmap(bm, bm.width * GOC_MAX / dai, bm.height * GOC_MAX / dai, true)
        return ByteArrayOutputStream().use { o -> nho.compress(Bitmap.CompressFormat.JPEG, chatLuong, o); o.toByteArray() }
    }
}
