package vn.tivikit.launcher

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.io.InputStream
import kotlin.math.max
import kotlin.math.roundToInt

/** Ảnh nền đúng cỡ màn hình và bản thu 1/8 đã làm mờ, để các khung kính vẽ phần nền ngay dưới chúng. */
class NenKinh(val anh: ImageBitmap, val mo: ImageBitmap)

val LocalNen = staticCompositionLocalOf<NenKinh?> { null }

/**
 * Kính mờ kiểu iPhone: phần ảnh nền đã làm mờ (tăng độ đậm màu) ngay dưới khung, phủ lớp tối [toi] và sáng [sang], viền
 * sáng mảnh nhạt dần xuống dưới. Ảnh nền đứng yên nên chỉ cần biết vị trí khung trên màn hình.
 */
fun Modifier.kinh(nen: NenKinh?, hinh: Shape, toi: Float = 0.18f, sang: Float = 0.08f, vien: Float = 0.38f): Modifier = composed {
    var goc by remember { mutableStateOf(Offset.Zero) }
    var coGoc by remember { mutableStateOf(IntSize.Zero) }
    val dam = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.45f) }) }
    onGloballyPositioned { c ->
        goc = c.positionInRoot()
        coGoc = c.findRootCoordinates().size
    }.drawWithCache {
        val outline = hinh.createOutline(size, layoutDirection, this)
        val duong = Path().apply { addOutline(outline) }
        val net = Stroke(1.dp.toPx())
        val vienMau = Brush.verticalGradient(listOf(Color.White.copy(alpha = vien), Color.White.copy(alpha = vien * 0.22f)))
        onDrawBehind {
            clipPath(duong) {
                if (nen != null && coGoc.width > 0) {
                    drawImage(nen.mo, IntOffset.Zero, IntSize(nen.mo.width, nen.mo.height),
                        IntOffset(-goc.x.roundToInt(), -goc.y.roundToInt()), coGoc, colorFilter = dam, filterQuality = FilterQuality.Low)
                } else drawRect(Color(0xFF2A2D3A))
                if (toi > 0f) drawRect(Color.Black.copy(alpha = toi))
                if (sang > 0f) drawRect(Color.White.copy(alpha = sang))
            }
            drawOutline(outline, vienMau, style = net)
        }
    }
}

/** Đọc và chuẩn bị ảnh nền (chạy ngoài luồng giao diện). */
object AnhNen {
    /** Thu ảnh về đúng cỡ màn hình [w]×[h] (phủ kín, cắt phần thừa ở giữa) rồi làm bản kính mờ. */
    fun tao(goc: Bitmap, w: Int, h: Int): NenKinh {
        val anh = phuKin(goc, w, h)
        val nho = Bitmap.createScaledBitmap(anh, max(1, w / 8), max(1, h / 8), true)
        val px = IntArray(nho.width * nho.height)
        nho.getPixels(px, 0, nho.width, 0, 0, nho.width, nho.height)
        lamMo(px, nho.width, nho.height, 3)
        val mo = Bitmap.createBitmap(px, nho.width, nho.height, Bitmap.Config.ARGB_8888)
        return NenKinh(anh.asImageBitmap(), mo.asImageBitmap())
    }

    /** Giải mã ảnh với cỡ vừa đủ phủ [w]×[h] (ảnh chụp 12 MP không làm đầy RAM của tivi). */
    fun giaiMa(mo: () -> InputStream, w: Int, h: Int): Bitmap? {
        val bien = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        mo().use { BitmapFactory.decodeStream(it, null, bien) }
        if (bien.outWidth <= 0 || bien.outHeight <= 0) return null
        var mau = 1
        while (bien.outWidth / (mau * 2) >= w && bien.outHeight / (mau * 2) >= h) mau *= 2
        return mo().use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = mau }) }
    }

    private fun phuKin(src: Bitmap, w: Int, h: Int): Bitmap {
        if (src.width == w && src.height == h) return src
        val tiLe = max(w.toFloat() / src.width, h.toFloat() / src.height)
        val sw = (src.width * tiLe).roundToInt().coerceAtLeast(w)
        val sh = (src.height * tiLe).roundToInt().coerceAtLeast(h)
        val to = Bitmap.createScaledBitmap(src, sw, sh, true)
        return Bitmap.createBitmap(to, (sw - w) / 2, (sh - h) / 2, w, h)
    }
}
