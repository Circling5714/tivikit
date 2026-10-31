package vn.tivikit.kenh

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast

/**
 * Ô / nút "Kênh nhanh" (25/10/2026): mở thẳng một kênh trực tiếp rồi tự đóng, không hiện gì.
 * - TV360: đi qua cổng nhận lệnh trợ lý giọng nói của TV360 bản tivi (PostmanAssistant, cũng là đường Google Assistant
 *   gọi khi nói "Xem VTV1 trên TV360"): lệnh SWITCH_CHANNEL kèm số kênh và tên kênh, TV360 tự tìm kênh trong danh sách
 *   của nó — đang tắt thì mở lên, đang chạy nền thì đưa lên, đang xem kênh khác thì chuyển kênh.
 * - VTVgo (26/10/2026): link vtvgo://play của kênh, như ô kênh của Home sạch.
 * - FPT Play (31/10/2026): link kênh fptplay.vn/xem-truyen-hinh/<mã>, FPT Play bản tivi mở thẳng màn xem kênh đó (chưa
 *   đăng nhập thì FPT Play hỏi đăng nhập).
 * App không nhận (bản sau đổi cổng / link) thì mở app như thường; chưa cài thì mở trang của app trên CH Play.
 */
class MoKenh : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val k = Kho(this).kenh
        val app = when {
            k.laFpt -> Kenh.FPTPLAY
            k.laVtvGo -> Kenh.VTVGO
            else -> Kenh.TV360
        }
        val lenh = when {
            k.laFpt -> Intent(Intent.ACTION_VIEW, Uri.parse(Kenh.linkFpt(k.ma))).setPackage(Kenh.FPTPLAY)
            k.laVtvGo -> Intent(Intent.ACTION_VIEW, Uri.parse(Kenh.linkVtvGo(k.ma))).setPackage(Kenh.VTVGO)
            else -> lenhKenh(k)
        }
        val duoc = mo(lenh) || mo(packageManager.getLeanbackLaunchIntentForPackage(app)) ||
            mo(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$app")))
        if (!duoc) Toast.makeText(this, when {
            k.laFpt -> R.string.chua_co_fptplay
            k.laVtvGo -> R.string.chua_co_vtvgo
            else -> R.string.chua_co_tv360
        }, Toast.LENGTH_LONG).show()
        finish()
    }

    private fun mo(i: Intent?): Boolean = i != null && try {
        startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

    /**
     * Lệnh chuyển kênh của TV360 (QueryContext.fromIntent / YGenw.onResume trong TV360 SmartTV 6.4): dữ liệu
     * content://com.viettel.tv360.tv/SWITCH_CHANNEL, kênh tìm theo number_channel (shortcutKeyboard) rồi name_channel.
     */
    private fun lenhKenh(k: Kenh): Intent = Intent("${Kenh.TV360}.VIEW", Uri.parse("content://${Kenh.TV360}/SWITCH_CHANNEL"))
        .setClassName(Kenh.TV360, "${Kenh.TV360}.assistant.PostmanAssistant")
        .putExtra("number_channel", k.so)
        .putExtra("name_channel", k.ten)
}
