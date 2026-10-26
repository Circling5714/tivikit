package vn.tivikit.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import vn.tivikit.adb.AppTrenTv
import vn.tivikit.adb.TinhTrangTv
import vn.tivikit.dulieu.LamMoi
import vn.tivikit.t

/**
 * Làm mới tivi (25/10/2026): xem RAM, bộ nhớ, tivi chạy bao lâu từ lần khởi động gần nhất; làm mới một chạm (đóng app
 * nằm chờ trong bộ nhớ, xoá cache chỉ khi bộ nhớ gần đầy); chạy lâu thì gợi ý khởi động lại. Không tự chạy theo giờ:
 * phải để ADB mở cả ngày mới làm được, mà ADB nên tắt.
 */
class LamMoiTivi(private val vm: PhienLamViec) {
    private val tvApp by lazy { AppTrenTv(vm.adb) }

    var tinhTrang by mutableStateOf<TinhTrangTv?>(null)
        private set

    /** Lần làm mới gần nhất, KB: RAM trống trước / sau, có xoá cache không (bộ nhớ gần đầy), xoá được bao nhiêu, bộ nhớ còn trống. */
    data class KetQua(val ramTruoc: Long?, val ramSau: Long?, val ganDay: Boolean, val giaiPhong: Long?, val boNhoCon: Long?)
    var ketQua by mutableStateOf<KetQua?>(null)
        private set

    fun mo() = vm.chay(t("Đang đọc tình trạng tivi…", "Reading the TV's status…")) {
        vm.damBaoKetNoi()
        ketQua = null
        tinhTrang = vm.io { tvApp.tinhTrang() }
        vm.man = Man.LAM_MOI
    }

    fun docLai() = vm.chay(t("Đang đọc tình trạng tivi…", "Reading the TV's status…")) {
        vm.damBaoKetNoi()
        tinhTrang = vm.io { tvApp.tinhTrang() }
    }

    /** Đóng app nằm nền, xoá cache nếu bộ nhớ gần đầy, đọc lại để báo được bao nhiêu. */
    fun lamMoi() = vm.chay(t("Đang đóng các app nằm chờ trong bộ nhớ…", "Closing apps waiting in memory…")) {
        vm.damBaoKetNoi()
        ketQua = null
        val truoc = vm.io { tvApp.tinhTrang() }
        vm.io { tvApp.dongAppNen() }
        // Tiến trình vừa đóng nhả RAM sau một nhịp.
        kotlinx.coroutines.delay(2000)
        var sau = vm.io { tvApp.tinhTrang() }
        val bn = sau.boNho ?: truoc.boNho
        val ganDay = bn != null && LamMoi.boNhoGanDay(bn.tongKb, bn.conKb)
        var giaiPhong: Long? = null
        if (ganDay) {
            vm.thongBao = t("Bộ nhớ gần đầy, đang xoá cache các app (có thể mất một phút)…", "Storage is nearly full, clearing app caches (may take a minute)…")
            vm.io { tvApp.donCacheTatCa() }
            sau = vm.io { tvApp.tinhTrang() }
            giaiPhong = sau.boNho?.let { s -> (s.conKb - bn!!.conKb).coerceAtLeast(0) }
        }
        tinhTrang = sau
        ketQua = KetQua(truoc.ram?.conKb, sau.ram?.conKb, ganDay, giaiPhong, sau.boNho?.conKb)
    }

    /** Kết nối tivi khác. */
    fun quen() { tinhTrang = null; ketQua = null }
}
