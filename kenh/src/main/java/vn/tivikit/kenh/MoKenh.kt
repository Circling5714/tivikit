package vn.tivikit.kenh

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
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
 * - M3U (1/11/2026): màn phát của M3UAndroid kèm mã kênh, như phím tắt Create Shortcut của M3U (Skyworth: mở là phát ngay).
 * - Auto (1/11/2026): chuyển tên kênh, các nguồn và cờ thứ tự riêng cho Map Voice (MoTuDong), Map Voice thử lần lượt tới
 *   khi có tiếng; tivi chưa có Map Voice 1.2 thì mở nguồn đầu tiên tivi có (VTVgo, TV360, FPT Play, IPTV, M3U, hay theo
 *   thứ tự riêng của ô).
 * - IPTV (1/11/2026): màn phát của app IPTV của TiviKit với tên và mã kênh; app đó tự tìm link kênh trong danh sách mới nhất.
 * App không nhận (bản sau đổi cổng / link) thì mở app như thường; chưa cài thì mở trang của app trên CH Play (M3U không có
 * trên CH Play: báo cài từ TiviKit).
 */
class MoKenh : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val k = Kho(this).kenh
        if (k.laAuto) moAuto(k) else moO(k)
        finish()
    }

    private fun moO(k: Kenh) {
        val app = goiCua(k)
        val duoc = mo(lenh(k)) || mo(packageManager.getLeanbackLaunchIntentForPackage(app)) ||
            !k.laM3u && !k.laIptv && mo(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$app")))
        if (!duoc) Toast.makeText(this, when {
            k.laIptv -> R.string.chua_co_iptv
            k.laM3u -> R.string.chua_co_m3u
            k.laFpt -> R.string.chua_co_fptplay
            k.laVtvGo -> R.string.chua_co_vtvgo
            else -> R.string.chua_co_tv360
        }, Toast.LENGTH_LONG).show()
    }

    private fun moAuto(k: Kenh) {
        val quaMapVoice = Intent().setClassName(Kenh.MAP_VOICE, Kenh.MAP_VOICE_MO)
            .putExtra("ten", k.ten).putExtra("nguon", k.jsonNguon()).putExtra("coDinh", k.coDinh)
        if (mo(quaMapVoice)) return
        val dau = (if (k.coDinh) k.nguon else k.nguon.sortedBy { Kenh.THU_TU.indexOf(it.app) }).firstOrNull { co(goiCua(it)) }
        if (dau == null || !(mo(lenh(dau)) || mo(packageManager.getLeanbackLaunchIntentForPackage(goiCua(dau)))))
            Toast.makeText(this, R.string.chua_co_app_kenh, Toast.LENGTH_LONG).show()
    }

    private fun goiCua(k: Kenh): String = when {
        k.laIptv -> Kenh.IPTV
        k.laM3u -> Kenh.M3U
        k.laFpt -> Kenh.FPTPLAY
        k.laVtvGo -> Kenh.VTVGO
        else -> Kenh.TV360
    }

    private fun lenh(k: Kenh): Intent = when {
        k.laIptv -> Intent().setClassName(Kenh.IPTV, Kenh.IPTV_PHAT).putExtra("ma", k.ma).putExtra("ten", k.ten)
        k.laM3u -> Intent(Intent.ACTION_VIEW).setClassName(Kenh.M3U, Kenh.M3U_PHAT).putExtra(Kenh.M3U_MA_KENH, k.ma.toInt())
        k.laFpt -> Intent(Intent.ACTION_VIEW, Uri.parse(Kenh.linkFpt(k.ma))).setPackage(Kenh.FPTPLAY)
        k.laVtvGo -> Intent(Intent.ACTION_VIEW, Uri.parse(Kenh.linkVtvGo(k.ma))).setPackage(Kenh.VTVGO)
        else -> lenhKenh(k)
    }

    private fun co(goi: String): Boolean = try {
        packageManager.getApplicationInfo(goi, 0).enabled
    } catch (_: PackageManager.NameNotFoundException) {
        false
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
