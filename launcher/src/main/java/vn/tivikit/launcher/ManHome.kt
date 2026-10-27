package vn.tivikit.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat

/** Màn Home (bí danh .Home có bộ lọc HOME, tắt sẵn đến khi người dùng chọn TiviKit Launcher làm Home ở TiviKit). */
class ManHome : ComponentActivity() {
    private val tt = TrangThaiHome()
    private lateinit var dk: DieuKhien

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        dk = DieuKhien(this, tt)
        tt.hd = dk
        setContent { ManHinhChinh(tt) }
        dk.batDau()
    }

    override fun onResume() {
        super.onResume()
        dk.hien()
    }

    override fun onPause() {
        dk.an()
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        dk.veDau()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        dk.botAnh(level)
    }
}
