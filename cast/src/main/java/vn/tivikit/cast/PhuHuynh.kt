package vn.tivikit.cast

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Mã bố mẹ cho phần quản lý trên tivi (28/10/2026): duyệt / chặn điện thoại, huỷ mọi liên kết, tạm ngừng nhận, đổi tên.
 * Khoá trẻ em (vn.tivikit.khoa) đang chạy thì nhờ màn XacNhan của nó hỏi mã (nó tự cho qua khi khoá đang tắt hay chưa có
 * mã); không có Khoá trẻ em thì không hỏi. Xem mã TV thì ai cũng xem được.
 */
object PhuHuynh {
    const val GOI = "vn.tivikit.khoa"
    private const val LOP = "vn.tivikit.khoa.XacNhan"

    sealed interface Can
    object KhongCan : Can
    class HoiMa(val intent: Intent) : Can
    /** Khoá trẻ em đang chạy mà là bản cũ, chưa có màn xác nhận: không cho làm, nhắc cập nhật bằng TiviKit. */
    object KhoaCu : Can

    fun can(ctx: Context, viec: String): Can {
        val dangChay = runCatching { Settings.Secure.getString(ctx.contentResolver, "enabled_accessibility_services") }
            .getOrNull()?.contains("$GOI/") == true
        if (!dangChay) return KhongCan
        val i = Intent().setClassName(GOI, LOP).putExtra("viec", viec)
        return if (runCatching { ctx.packageManager.resolveActivity(i, 0) }.getOrNull() != null) HoiMa(i) else KhoaCu
    }
}

/** Màn của app Cast có việc cần mã bố mẹ: nhập đúng một lần thì 5 phút không hỏi lại (lúc màn còn mở). */
open class ManCoMa : ComponentActivity() {
    private var choMa: ((Boolean) -> Unit)? = null
    private val xinMa = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val f = choMa
        choMa = null
        f?.invoke(r.resultCode == RESULT_OK)
    }
    private var moDen = 0L

    /** Đang chờ màn nhập mã bố mẹ trả lời. */
    protected val dangChoMa: Boolean get() = choMa != null

    /** Làm [lam] sau khi có mã bố mẹ (nếu cần); [viec] hiện trên màn nhập mã. */
    fun canMa(viec: String, lam: () -> Unit) {
        if (SystemClock.elapsedRealtime() < moDen) { lam(); return }
        when (val c = PhuHuynh.can(this, viec)) {
            PhuHuynh.KhongCan -> lam()
            PhuHuynh.KhoaCu -> Toast.makeText(this, t("Khoá trẻ em trên tivi là bản cũ: mở TiviKit trên điện thoại, lưu lại Khoá trẻ em để cập nhật rồi làm lại.",
                "The Kids lock on this TV is outdated: open TiviKit on a phone and save the Kids lock to update it, then try again."), Toast.LENGTH_LONG).show()
            is PhuHuynh.HoiMa -> {
                choMa = { ok -> if (ok) { moDen = SystemClock.elapsedRealtime() + 5 * 60_000L; lam() } }
                runCatching { xinMa.launch(c.intent) }.onFailure { choMa = null }
            }
        }
    }
}
