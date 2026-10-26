package vn.tivikit

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import vn.tivikit.ui.UngDung

class MainActivity : ComponentActivity() {
    private val xinThongBao = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Conscrypt cho TLS của Gỡ lỗi không dây (ghép đôi + kết nối).
        runCatching { java.security.Security.insertProviderAt(org.conscrypt.Conscrypt.newProvider(), 1) }
        ManHinh.doc(this)
        CapNhat.donOtaCuNeuDoiBan(this)
        Dem.moApp(this)
        docYDinh(intent)
        ThongBaoCapNhat.hen(this)
        // Android 13+: xin quyền thông báo một lần để báo được khi có bản mới (thông báo duy nhất của app — tắt tự cập nhật thì thôi).
        if (BuildConfig.TU_CAP_NHAT && Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            val p = getSharedPreferences("ota", MODE_PRIVATE)
            if (!p.getBoolean("da_xin_tb", false)) {
                p.edit().putBoolean("da_xin_tb", true).apply()
                xinThongBao.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        // Thanh hệ thống theo sáng / tối của máy như giao diện; chạy trên tivi thì giao diện luôn tối nên biểu tượng luôn sáng.
        if (ManHinh.laTv) enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        else enableEdgeToEdge()
        setContent { UngDung() }
    }

    // App đang hiện trên màn hình: việc chạy nền không tự cài bản mới lúc này (cài là app bị đóng ngang).
    override fun onStart() { super.onStart(); CapNhat.dangMo = true }

    override fun onStop() { CapNhat.dangMo = false; super.onStop() }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        docYDinh(intent)
    }

    private fun docYDinh(i: Intent?) {
        if (i?.getBooleanExtra(ThongBaoCapNhat.MO_CAP_NHAT, false) == true) {
            CapNhat.moTuThongBao = true
            i.removeExtra(ThongBaoCapNhat.MO_CAP_NHAT)
        }
    }
}
