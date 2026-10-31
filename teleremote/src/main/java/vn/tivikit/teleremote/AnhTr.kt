package vn.tivikit.teleremote

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File

/** Ảnh người dùng gửi qua Telegram (1/11/2026): đọc vừa màn hình (XemTep), làm ảnh nền 16:9 cho TiviKit Launcher. */
object AnhTr {
    /**
     * Ảnh thu nhỏ vừa đủ phủ [rong] x [cao] (ảnh gốc 12 MP đọc nguyên dễ hết RAM của box), xoay theo EXIF (ảnh gửi dạng
     * tệp); null nếu không phải ảnh.
     */
    fun doc(tep: File, rong: Int, cao: Int): Bitmap? {
        val co = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(tep.path, co)
        if (co.outWidth <= 0 || co.outHeight <= 0) return null
        var mau = 1
        while (co.outWidth / (mau * 2) >= rong && co.outHeight / (mau * 2) >= cao) mau *= 2
        val anh = BitmapFactory.decodeFile(tep.path, BitmapFactory.Options().apply { inSampleSize = mau }) ?: return null
        val huong = runCatching { ExifInterface(tep.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
            .getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val xoay = when (huong) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return anh
        }
        return Bitmap.createBitmap(anh, 0, 0, anh.width, anh.height, Matrix().apply { postRotate(xoay) }, true)
    }

    /** Ảnh nền [rong] x [cao] (16:9) từ [tep]: cắt giữa cho vừa khung rồi thu / phóng, JPEG; null nếu không đọc được ảnh. */
    fun nen(tep: File, rong: Int = 1920, cao: Int = 1080): ByteArray? {
        val anh = doc(tep, rong, cao) ?: return null
        val (x, y, w, h) = khungCat(anh.width, anh.height, rong, cao)
        val cat = Bitmap.createBitmap(anh, x, y, w, h)
        val vua = Bitmap.createScaledBitmap(cat, rong, cao, true)
        return ByteArrayOutputStream().also { vua.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    }

    /** Khung cắt giữa ảnh [w] x [h] cho đúng tỉ lệ [rong]:[cao]: (x, y, rộng, cao). */
    fun khungCat(w: Int, h: Int, rong: Int, cao: Int): List<Int> =
        if (w.toLong() * cao > h.toLong() * rong) {
            val r = (h.toLong() * rong / cao).toInt()
            listOf((w - r) / 2, 0, r, h)
        } else {
            val c = (w.toLong() * cao / rong).toInt()
            listOf(0, (h - c) / 2, w, c)
        }
}
