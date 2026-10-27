package vn.tivikit.ui

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import vn.tivikit.adb.GiaoThucScrcpy
import vn.tivikit.adb.ManHinhTv
import vn.tivikit.adb.ThaoTacTv

/**
 * Kênh điều khiển scrcpy không hình (ManHinhTv.PhienDieuKhien) của màn Điều khiển. Mở khi vào màn / app lên lại, đóng khi
 * rời màn / app xuống nền / đổi tivi:
 * - phím remote đi qua đây (27/10/2026): tivi nhận ngay, không chờ lệnh `input` khởi động một máy ảo Java (~0,5 giây mỗi
 *   lệnh trên Android 9–10). Chưa mở xong, đứt, hay tivi không chạy được máy chủ thì phím đi đường `input` như cũ;
 * - dán chữ có dấu vào ô đang chọn khi bộ gõ tiếng Việt của TiviKit không chạy được trên tivi.
 * Thử trên TX3 Mini (Android 9): máy chủ mở trong khoảng 1 giây, tivi báo đã đặt clipboard sau ~20 ms.
 */
class KenhDieuKhien(private val vm: PhienLamViec) {
    private val khoa = Mutex()
    @Volatile private var phien: ManHinhTv.PhienDieuKhien? = null
    /** Màn Điều khiển đang hiện: giữ phiên, đứt thì mở lại. */
    @Volatile private var muonMo = false
    /** Lần mở trước tivi không chạy được máy chủ (không phải mất kết nối): thôi thử tới lần vào màn sau. */
    @Volatile private var hong = false
    private var viecMo: Job? = null

    /** Vào màn Điều khiển / app lên lại: mở phiên ở nền. */
    fun mo() {
        muonMo = true
        hong = false
        moNen()
    }

    /**
     * Rời màn / app xuống nền / đổi tivi: đóng phiên, máy chủ trên tivi tự thoát. Không huỷ lần mở đang chạy (huỷ giữa chừng
     * thì phiên vừa mở bị bỏ rơi): mở xong thấy không còn muốn thì nó tự đóng.
     */
    fun dong() {
        muonMo = false
        phien?.dong()
        phien = null
    }

    /** Một lần bấm phím qua phiên đang mở; chưa có / đã đứt thì false để gửi đường `input`, và mở lại ở nền. */
    fun phim(ma: Int): Boolean {
        val p = phien?.takeIf { !it.daDong }
        if (p == null) {
            phien = null
            moNen()
            return false
        }
        return p.phim(ma)
    }

    /**
     * Dán [chu] vào ô đang chọn (máy chủ scrcpy đặt clipboard rồi bấm KEYCODE_PASTE, từ Android 7). true = tivi báo đã đặt
     * clipboard. Mất kết nối thì ném lỗi như các lệnh khác; máy chủ không chạy được thì false.
     */
    suspend fun dan(chu: String): Boolean {
        val p = layPhien() ?: return false
        return vm.io {
            val ok = p.dan(chu)
            // Rời màn trong lúc mở phiên: không giữ. Máy chủ bấm dán rồi mới báo nhưng phím đi bất đồng bộ: chờ chút rồi đóng.
            if (!muonMo && phien !== p) { if (ok) Thread.sleep(200); p.dong() }
            ok
        }
    }

    /** Mở phiên ở nền (mất kết nối thì thôi, phím sau thử lại); mở xong mà màn đã rời thì đóng luôn. */
    private fun moNen() {
        if (!muonMo || hong || viecMo?.isActive == true) return
        viecMo = vm.viewModelScope.launch {
            val p = runCatching { layPhien() }.getOrNull()
            if (p != null && phien !== p) p.dong()
        }
    }

    /** Phiên đang mở, hay mở mới (chờ máy chủ ~1 giây); null nếu tivi không chạy được máy chủ. */
    private suspend fun layPhien(): ManHinhTv.PhienDieuKhien? = khoa.withLock {
        phien?.takeIf { !it.daDong }?.let { return@withLock it }
        val p = try {
            vm.io { ManHinhTv(vm.adb).moDieuKhien { vn.tivikit.CapNhat.moAsset(vm.ctx, GiaoThucScrcpy.ASSET).readBytes() } }
        } catch (e: Exception) {
            if (ThaoTacTv.laMatKetNoi(e) || e is kotlinx.coroutines.CancellationException) throw e
            hong = true
            return@withLock null
        }
        if (muonMo) phien = p
        p
    }
}
