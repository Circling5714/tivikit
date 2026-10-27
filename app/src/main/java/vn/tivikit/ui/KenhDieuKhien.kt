package vn.tivikit.ui

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import vn.tivikit.adb.GiaoThucScrcpy
import vn.tivikit.adb.ManHinhTv
import vn.tivikit.adb.ThaoTacTv

/**
 * Kênh điều khiển scrcpy không hình (27/10/2026, ManHinhTv.PhienDieuKhien) cho màn Điều khiển: dán chữ có dấu vào ô đang
 * chọn khi bộ gõ tiếng Việt của TiviKit không chạy được trên tivi. Thử trên TX3 Mini (Android 9): tivi báo đã đặt clipboard
 * sau ~20 ms, chữ có dấu hiện đủ trong ô địa chỉ của TV Bro.
 */
class KenhDieuKhien(private val vm: PhienLamViec) {
    private val khoa = Mutex()

    /**
     * Dán [chu] vào ô đang chọn (máy chủ scrcpy đặt clipboard rồi bấm KEYCODE_PASTE, từ Android 7): mở phiên riêng rồi đóng.
     * true = tivi báo đã đặt clipboard. Mất kết nối thì ném lỗi như các lệnh khác; máy chủ không chạy được thì false.
     */
    suspend fun dan(chu: String): Boolean = khoa.withLock {
        vm.io {
            val p = try {
                ManHinhTv(vm.adb).moDieuKhien { vn.tivikit.CapNhat.moAsset(vm.ctx, GiaoThucScrcpy.ASSET).readBytes() }
            } catch (e: Exception) {
                if (ThaoTacTv.laMatKetNoi(e)) throw e
                return@io false
            }
            try {
                // Máy chủ bấm dán rồi mới báo, nhưng phím đi bất đồng bộ: chờ chút rồi mới đóng phiên.
                p.dan(chu).also { ok -> if (ok) Thread.sleep(200) }
            } finally {
                p.dong()
            }
        }
    }
}
