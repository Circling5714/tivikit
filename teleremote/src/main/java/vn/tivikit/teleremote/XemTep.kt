package vn.tivikit.teleremote

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.MediaController
import android.widget.VideoView
import java.io.File

/**
 * Chiếu ảnh / phát video người dùng gửi qua Telegram (31/10/2026), toàn màn hình nền đen: Back trên remote để tắt; video
 * phát xong thì tự đóng, ảnh tự đóng sau [ANH_TOI_DA] (màn này giữ màn hình sáng, không ai tắt thì tivi không về màn bảo vệ
 * hay chế độ chờ). Gửi tiếp tệp khác thì thay tệp đang chiếu.
 */
class XemTep : Activity() {
    private val dong = Runnable { finish() }
    private val tay = Handler(Looper.getMainLooper())

    override fun onDestroy() {
        tay.removeCallbacks(dong)
        super.onDestroy()
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hien(intent)
    }

    override fun onNewIntent(i: Intent) {
        super.onNewIntent(i)
        setIntent(i)
        hien(i)
    }

    private fun hien(i: Intent) {
        tay.removeCallbacks(dong)
        val tep = i.getStringExtra(TEP)?.let(::File)?.takeIf { it.isFile } ?: return finish()
        val khung = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        if (i.getBooleanExtra(VIDEO, false)) {
            val v = VideoView(this)
            khung.addView(v, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT, Gravity.CENTER))
            setContentView(khung)
            v.setMediaController(MediaController(this).also { it.setAnchorView(v) })
            v.setOnCompletionListener { finish() }
            v.setOnErrorListener { _, _, _ -> finish(); true }
            v.setVideoPath(tep.path)
            v.requestFocus()
            v.start()
        } else {
            val anh = docAnh(tep) ?: return finish()
            khung.addView(ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER; setImageBitmap(anh) },
                FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            setContentView(khung)
            tay.postDelayed(dong, ANH_TOI_DA)
        }
    }

    /** Ảnh thu nhỏ vừa màn hình (ảnh gốc 12 MP đọc nguyên dễ hết RAM của box), xoay theo EXIF (ảnh gửi dạng tệp). */
    private fun docAnh(tep: File): Bitmap? {
        val co = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(tep.path, co)
        if (co.outWidth <= 0 || co.outHeight <= 0) return null
        val man = resources.displayMetrics
        var mau = 1
        while (co.outWidth / (mau * 2) >= man.widthPixels && co.outHeight / (mau * 2) >= man.heightPixels) mau *= 2
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

    companion object {
        const val TEP = "tep"
        const val VIDEO = "video"
        /** Ảnh tự đóng sau chừng này (ms). */
        private const val ANH_TOI_DA = 10 * 60_000L
    }
}
