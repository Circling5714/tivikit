package vn.tivikit.mapvoice

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle

/**
 * Ô Kênh nhanh Auto (kenh/MoKenh, 1/11/2026) gọi vào đây với tên kênh ([TEN]) và nguồn TiviKit đã tìm khi lưu ô ([NGUON],
 * TuChonNguon.tuJson): dịch vụ Map Voice tự chọn nguồn (TuChon). Dịch vụ chưa chạy (Map Voice đang tắt trong Trợ năng) thì
 * mở ngay nguồn đầu tiên tivi có, không thử tiếp. Không hiện gì (Theme.NoDisplay). App khác gọi được cũng chỉ mở kênh, như
 * link vtvgo:// hay fptplay.vn.
 */
class MoTuDong : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val ten = intent.getStringExtra(TEN)?.trim()?.take(40).orEmpty()
        val nguon = TuChonNguon.tuJson(intent.getStringExtra(NGUON))
        val dv = MapVoiceDichVu.dangChay
        if (ten.isNotEmpty() && nguon.isNotEmpty()) {
            if (dv != null) dv.moTuDong(ten, nguon)
            else nguon.sortedBy { TuChonNguon.MAC_DINH.indexOf(it.app) }.firstOrNull { co(it.goi) }
                ?.let { runCatching { startActivity(TuChon.lenhMo(it).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        }
        finish()
    }

    private fun co(goi: String): Boolean = try {
        packageManager.getApplicationInfo(goi, 0).enabled
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    companion object {
        const val TEN = "ten"
        const val NGUON = "nguon"
    }
}
