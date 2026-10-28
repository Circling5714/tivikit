package vn.tivikit.cast

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Màu các màn của app Cast (28/10/2026): nền chàm tím, kính mờ trắng như TiviKit Launcher. */
object Mau {
    val nenTren = Color(0xFF0B1026)
    val nenDuoi = Color(0xFF2E1B5A)
    val chu = Color.White
    val phu = Color(0xFFC3BEEA)
    val mo = Color(0xFF8E89B8)
    val xanh = Color(0xFF4CD964)
    val cam = Color(0xFFFFB340)
    val do_ = Color(0xFFFF6B6B)
    val nhan = Color(0xFFB79CFF)
}

/** Nền cả màn: chàm sang tím. */
@Composable
fun NenCast(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) = Box(
    modifier.fillMaxSize().background(Brush.linearGradient(listOf(Mau.nenTren, Mau.nenDuoi))), content = content)

/** Chữ (chỉ dùng Compose foundation, không cần Material). */
@Composable
fun Chu(
    s: String, co: TextUnit = 16.sp, mau: Color = Mau.chu, dam: FontWeight = FontWeight.Normal, modifier: Modifier = Modifier,
    dong: Int = Int.MAX_VALUE, cachChu: TextUnit = TextUnit.Unspecified,
) = BasicText(s, modifier, TextStyle(color = mau, fontSize = co, fontWeight = dam, letterSpacing = cachChu,
    lineHeight = co * 1.3f), overflow = TextOverflow.Ellipsis, maxLines = dong)

/** Khung kính: nền trắng mờ, viền sáng mảnh. */
@Composable
fun The(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val hinh = RoundedCornerShape(24.dp)
    Column(
        modifier.clip(hinh).background(Color.White.copy(alpha = 0.07f)).border(1.dp, Color.White.copy(alpha = 0.16f), hinh)
            .padding(horizontal = 26.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

/** Phím OK của remote. */
private val PHIM_OK = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

/**
 * Ô bấm được bằng remote: sáng lên khi đang chọn; OK (hay chạm, qua màn chiếu của TiviKit) để bấm. Dùng focusable() thay
 * clickable(): clickable không nhận chọn sẵn khi tivi đang ở chế độ cảm ứng (TX3 vừa mở app bằng lệnh, chưa bấm phím nào).
 */
@Composable
fun ONhan(
    onClick: () -> Unit, modifier: Modifier = Modifier, fr: FocusRequester? = null,
    content: @Composable RowScope.(chon: Boolean) -> Unit,
) {
    var chon by remember { mutableStateOf(false) }
    val bam by rememberUpdatedState(onClick)
    val hinh = RoundedCornerShape(16.dp)
    Row(
        modifier
            .then(if (fr != null) Modifier.focusRequester(fr) else Modifier)
            .onFocusChanged { chon = it.isFocused }
            .onKeyEvent { e ->
                if (e.key !in PHIM_OK) return@onKeyEvent false
                if (e.type == KeyEventType.KeyUp) bam()
                true
            }
            .focusable()
            .pointerInput(Unit) { detectTapGestures { bam() } }
            .clip(hinh)
            .background(Color.White.copy(alpha = if (chon) 0.24f else 0.06f))
            .border(if (chon) 2.dp else 1.dp, Color.White.copy(alpha = if (chon) 0.9f else 0.12f), hinh)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content(chon) }
}

/** Nút chữ. */
@Composable
fun Nut(chu: String, onClick: () -> Unit, modifier: Modifier = Modifier, mau: Color = Mau.chu, fr: FocusRequester? = null) =
    ONhan(onClick, modifier, fr) { Chu(chu, 17.sp, mau, FontWeight.SemiBold, dong = 1) }
