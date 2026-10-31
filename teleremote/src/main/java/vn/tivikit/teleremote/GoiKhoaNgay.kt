package vn.tivikit.teleremote

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import java.util.concurrent.CompletableFuture

/**
 * Màn trong suốt gọi Khoá trẻ em ngay (vn.tivikit.khoa/.KhoaNgay) bằng startActivityForResult rồi báo kết quả cho dịch vụ
 * (31/10/2026): KhoaNgay chỉ nhận app ký cùng khoá TiviKit, mà dịch vụ trợ năng không gọi được startActivityForResult.
 */
class GoiKhoaNgay : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (b != null) return
        try {
            startActivityForResult(Intent().setClassName(KHOA, "$KHOA.KhoaNgay"), 1)
        } catch (_: Exception) {
            xong("khong-co")
        }
    }

    override fun onActivityResult(ma: Int, kq: Int, d: Intent?) {
        super.onActivityResult(ma, kq, d)
        xong(if (kq == RESULT_OK) "" else d?.getStringExtra("loi") ?: "huy")
    }

    private fun xong(kq: String) {
        ketQua?.complete(kq)
        finish()
    }

    companion object {
        private const val KHOA = "vn.tivikit.khoa"
        @Volatile var ketQua: CompletableFuture<String>? = null
    }
}
