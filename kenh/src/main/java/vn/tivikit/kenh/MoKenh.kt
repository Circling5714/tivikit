package vn.tivikit.kenh

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast

/**
 * Ô / nút "Kênh nhanh" (25/10/2026): mở thẳng một kênh trực tiếp của TV360 rồi tự đóng, không hiện gì. Đi qua cổng nhận
 * lệnh trợ lý giọng nói của TV360 bản tivi (PostmanAssistant, cũng là đường Google Assistant gọi khi nói "Xem VTV1 trên
 * TV360"): lệnh SWITCH_CHANNEL kèm số kênh và tên kênh, TV360 tự tìm kênh trong danh sách của nó — đang tắt thì mở lên,
 * đang chạy nền thì đưa lên, đang xem kênh khác thì chuyển kênh. TV360 không nhận (bản sau đổi cổng) thì mở TV360 như
 * thường; chưa cài thì mở trang TV360 trên CH Play.
 */
class MoKenh : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val k = Kho(this).kenh
        val duoc = mo(lenhKenh(k)) || mo(packageManager.getLeanbackLaunchIntentForPackage(Kenh.TV360)) ||
            mo(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${Kenh.TV360}")))
        if (!duoc) Toast.makeText(this, R.string.chua_co_tv360, Toast.LENGTH_LONG).show()
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
