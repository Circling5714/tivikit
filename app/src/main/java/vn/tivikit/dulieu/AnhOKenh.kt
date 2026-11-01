package vn.tivikit.dulieu

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import java.io.ByteArrayOutputStream

/**
 * Ảnh ô Kênh nhanh 320×180 (1/11/2026: ô kênh có nhiều nguồn TV360, VTVgo, FPT Play, M3U, nhìn ô là phải biết kênh của app
 * nào): logo kênh (KenhVtvGo.anhO) kèm huy hiệu app nguồn ở góc phải dưới — biểu tượng tròn viền trắng (mẫu 2 anh chọn)
 * của chính app đó trên tivi (TrenTv.bieuTuong), app không đóng sẵn logo của hãng nào. Kênh không có logo thì ô chữ: tên
 * kênh in đậm trên nền trắng như logo kênh, cũng kèm huy hiệu.
 */
object AnhOKenh {
    private const val RONG = 320
    private const val CAO = 180
    /** Huy hiệu, px trên ảnh 320×180 (Home sạch vẽ ô 180×101 dp centerCrop, gần như không cắt mép): đường kính, viền, cách mép. */
    private const val HH = 62f
    private const val VIEN = 3f
    private const val LE = 8f
    /** Ô chữ: bề ngang dành cho chữ, tâm khối chữ (trên giữa ô một chút cho khỏi chạm huy hiệu), giãn dòng. */
    private const val CHU_RONG = 264f
    private const val CHU_TAM = 80f
    private const val GIAN_DONG = 1.15f

    /**
     * Ảnh ô JPEG: [logo] (JPEG 320×180; null = ô chữ [ten]) kèm huy hiệu [bieuTuong] (PNG). Không có biểu tượng thì trả
     * nguyên [logo] (null nếu cũng không có logo: launcher vẽ ô biểu tượng kèm tên như trước); vẽ lỗi cũng trả [logo].
     */
    fun ve(logo: ByteArray?, ten: String, bieuTuong: ByteArray?): ByteArray? {
        if (bieuTuong == null) return logo
        return runCatching {
            val bt = BitmapFactory.decodeByteArray(bieuTuong, 0, bieuTuong.size) ?: return logo
            val nen = if (logo == null) null else BitmapFactory.decodeByteArray(logo, 0, logo.size) ?: return logo
            val ra = Bitmap.createBitmap(RONG, CAO, Bitmap.Config.ARGB_8888)
            val cv = Canvas(ra)
            if (nen != null) cv.drawBitmap(nen, null, Rect(0, 0, RONG, CAO), Paint(Paint.FILTER_BITMAP_FLAG))
            else { cv.drawColor(Color.WHITE); veChu(cv, ten) }
            veHuyHieu(cv, bt)
            ByteArrayOutputStream().also { ra.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
        }.getOrElse { logo }
    }

    /** Huy hiệu tròn ở góc phải dưới: bóng mờ, đĩa trắng làm viền, biểu tượng [bt] cắt tròn bên trong (chỗ trong suốt thành trắng). */
    private fun veHuyHieu(cv: Canvas, bt: Bitmap) {
        val r = HH / 2
        val cx = RONG - LE - r
        val cy = CAO - LE - r
        cv.drawCircle(cx, cy + 1.5f, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(80, 0, 0, 0); maskFilter = BlurMaskFilter(3f, BlurMaskFilter.Blur.NORMAL)
        })
        cv.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        val trong = r - VIEN
        val m = Matrix().apply {
            setRectToRect(RectF(0f, 0f, bt.width.toFloat(), bt.height.toFloat()), RectF(cx - trong, cy - trong, cx + trong, cy + trong), Matrix.ScaleToFit.CENTER)
        }
        // Tô hình tròn bằng ảnh (BitmapShader) cho mép tròn mịn; clipPath trên Canvas của Bitmap không khử răng cưa.
        cv.drawCircle(cx, cy, trong, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            shader = BitmapShader(bt, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(m) }
        })
    }

    /** Tên kênh in đậm giữa ô, một hoặc hai dòng (xepChu). */
    private fun veChu(cv: Canvas, ten: String) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1C1C1E.toInt(); typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
        // Đo ở cỡ 100 rồi chia: đo thẳng ở cỡ 1 bị làm tròn.
        p.textSize = 100f
        val (co, dong) = xepChu(ten, CHU_RONG) { p.measureText(it) / 100f }
        p.textSize = co
        val fm = p.fontMetrics
        dong.forEachIndexed { i, s ->
            val tam = CHU_TAM + (i - (dong.size - 1) / 2f) * co * GIAN_DONG
            cv.drawText(s, RONG / 2f, tam - (fm.ascent + fm.descent) / 2, p)
        }
    }

    /**
     * Cỡ chữ và các dòng cho tên kênh [ten] vừa bề ngang [rong] ([doRong] = bề ngang của chuỗi ở cỡ chữ 1): một dòng, cỡ
     * 52 lùi tới 30; không vừa thì hai dòng chia chỗ cân nhất, cỡ 36 lùi tới 22; vẫn không vừa thì một dòng thu cho vừa.
     */
    internal fun xepChu(ten: String, rong: Float, doRong: (String) -> Float): Pair<Float, List<String>> {
        val mot = doRong(ten)
        for (co in 52 downTo 30 step 2) if (mot * co <= rong) return co.toFloat() to listOf(ten)
        val tu = ten.split(' ').filter { it.isNotEmpty() }
        if (tu.size >= 2) {
            val (a, b) = (1 until tu.size).map { i -> tu.take(i).joinToString(" ") to tu.drop(i).joinToString(" ") }
                .minByOrNull { (a, b) -> maxOf(doRong(a), doRong(b)) }!!
            val hai = maxOf(doRong(a), doRong(b))
            for (co in 36 downTo 22 step 2) if (hai * co <= rong) return co.toFloat() to listOf(a, b)
        }
        return minOf(30f, rong / mot) to listOf(ten)
    }
}
