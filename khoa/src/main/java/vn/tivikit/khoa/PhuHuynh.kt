package vn.tivikit.khoa

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import java.util.Locale

/**
 * Ô "Khoá trẻ em" trong danh sách app của tivi (28/10/2026): mở bảng phụ huynh (dịch vụ khoá vẽ, hỏi mã trước) rồi đóng
 * ngay. Gán được vào nút remote (Nút remote của TiviKit) để bố mẹ mở nhanh lúc không cầm điện thoại. Trẻ mở thì cũng chỉ
 * thấy ô nhập mã.
 */
class PhuHuynh : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val dv = KhoaDichVu.dangChay
        if (dv != null) dv.moBangPhuHuynh()
        else Toast.makeText(this, if (Locale.getDefault().language == "vi") "Khoá trẻ em chưa bật trên tivi này: bật bằng TiviKit trên điện thoại."
            else "The Kids lock isn't on for this TV: turn it on with TiviKit on a phone.", Toast.LENGTH_LONG).show()
        finish()
    }
}
