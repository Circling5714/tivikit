package vn.tivikit.launcher

import androidx.compose.foundation.focusable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

private class GiuPhim(var xuong: Boolean = false, var daGiu: Boolean = false)

/** true = lớp này đang bị trang / menu khác che: các ô không nhận lựa chọn, nút remote không đi lạc xuống dưới. */
val LocalKhoaChon = compositionLocalOf { false }

/**
 * Ô bấm bằng remote: nhả OK để mở, giữ OK (hoặc nút Menu) để hiện menu — remote tivi ít khi có nút Menu. Đặt sau
 * focusRequester (nếu có), trước các modifier vẽ. Lần nhả OK không đi kèm lần nhấn (ví dụ OK vừa mở menu đè lên) bị bỏ qua.
 */
fun Modifier.nutTv(onClick: () -> Unit, onGiu: (() -> Unit)? = null, onChon: (Boolean) -> Unit = {}): Modifier = composed {
    val p = remember { GiuPhim() }
    val khoa = LocalKhoaChon.current
    onFocusChanged { onChon(it.isFocused) }
        .onPreviewKeyEvent { e ->
            val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
            when {
                e.key == Key.Menu && onGiu != null -> { if (e.type == KeyEventType.KeyDown) onGiu(); true }
                !ok -> false
                e.type == KeyEventType.KeyDown -> {
                    if (e.nativeKeyEvent.repeatCount == 0) { p.xuong = true; p.daGiu = false }
                    else if (p.xuong && !p.daGiu && onGiu != null) { p.daGiu = true; onGiu() }
                    true
                }
                e.type == KeyEventType.KeyUp -> {
                    if (p.xuong && !p.daGiu) onClick()
                    p.xuong = false; p.daGiu = false
                    true
                }
                else -> false
            }
        }
        .focusProperties { canFocus = !khoa }
        .focusable()
}
