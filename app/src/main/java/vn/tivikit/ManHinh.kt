package vn.tivikit

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

/**
 * App đang chạy trên điện thoại hay trên chính tivi (22/10/2026: bản chạy thẳng trên tivi — người dùng iPhone không có
 * điện thoại Android vẫn dọn được). Trên tivi app tự kết nối ADB vào chính máy (127.0.0.1:5555), giao diện gọn giữa màn
 * hình cho remote, và tự quay lên sau các bước bấm Home.
 */
object ManHinh {
    var laTv = false
        private set

    fun doc(ctx: Context) {
        val um = ctx.getSystemService(UiModeManager::class.java)
        laTv = um?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }
}
