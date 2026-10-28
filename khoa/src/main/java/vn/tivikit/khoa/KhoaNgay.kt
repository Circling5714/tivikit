package vn.tivikit.khoa

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle

/**
 * Khoá trẻ em ngay (28/10/2026): mục trong menu ⏻ của TiviKit Launcher. Tivi khoá như trong giờ khoá, tới khi bố mẹ nhập
 * mã (kể cả khi tivi khởi động lại). Gọi bằng startActivityForResult; chỉ nhận app ký cùng khoá TiviKit. RESULT_OK = đã
 * khoá; RESULT_CANCELED kèm "loi": "chua-co-ma" (chưa cài mã bằng TiviKit), "dich-vu" (dịch vụ khoá không chạy), "ky".
 */
class KhoaNgay : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val goi = callingPackage
        val loi = when {
            goi == null || packageManager.checkSignatures(goi, packageName) != PackageManager.SIGNATURE_MATCH -> "ky"
            else -> when (KhoaDichVu.dangChay?.khoaNgay()) {
                null -> "dich-vu"
                false -> "chua-co-ma"
                true -> null
            }
        }
        if (loi == null) setResult(RESULT_OK) else setResult(RESULT_CANCELED, Intent().putExtra("loi", loi))
        finish()
    }
}
