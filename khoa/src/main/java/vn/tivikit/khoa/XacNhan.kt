package vn.tivikit.khoa

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle

/**
 * App khác của TiviKit xin mã bố mẹ trước một việc quản lý (28/10/2026, Cast SmartTube: duyệt / chặn điện thoại, huỷ mọi
 * liên kết, tạm ngừng nhận cast). Gọi bằng startActivityForResult, extra "viec" = việc định làm (hiện trên ô nhập mã). Chỉ
 * nhận app ký cùng khoá TiviKit. Khoá đang tắt hay chưa có mã thì trả RESULT_OK ngay; không thì dịch vụ khoá hiện ô nhập
 * mã: đúng mã thì RESULT_OK, Back hay để yên thì RESULT_CANCELED.
 */
class XacNhan : Activity() {
    private var daTra = false

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val goi = callingPackage
        if (goi == null || packageManager.checkSignatures(goi, packageName) != PackageManager.SIGNATURE_MATCH) { tra(false); return }
        // Dịch vụ khoá không chạy: khoá không có hiệu lực, không có gì để hỏi.
        val dv = KhoaDichVu.dangChay ?: run { tra(true); return }
        dv.xacNhanChoApp(intent.getStringExtra("viec").orEmpty().take(120)) { ok -> tra(ok) }
    }

    private fun tra(ok: Boolean) {
        if (daTra) return
        daTra = true
        setResult(if (ok) RESULT_OK else RESULT_CANCELED)
        finish()
    }
}
