package vn.tivikit.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import vn.tivikit.adb.KetQuaMang
import vn.tivikit.adb.MangTv
import vn.tivikit.t

/** Kiểm tra mạng tivi (25/10/2026): kết nối, sóng Wi-Fi, ping router / Internet / YouTube, nhận xét. */
class MangTivi(private val vm: PhienLamViec) {
    private val mang by lazy { MangTv(vm.adb) }

    var ketQua by mutableStateOf<KetQuaMang?>(null)
        private set

    /** Mở màn rồi đo luôn (khoảng 10 giây). */
    fun mo() { ketQua = null; vm.man = Man.MANG; doLai() }

    fun doLai() = vm.chay(t("Đang kiểm tra mạng tivi…", "Checking the TV's network…")) {
        vm.damBaoKetNoi()
        ketQua = vm.io { mang.kiemTra { s -> vm.thongBao = s } }
    }

    fun quen() { ketQua = null }
}
