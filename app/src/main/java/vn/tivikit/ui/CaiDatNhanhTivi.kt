package vn.tivikit.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import vn.tivikit.adb.CaiDatTv
import vn.tivikit.dulieu.CaiDatNhanh
import vn.tivikit.t

/** Cài đặt nhanh tivi (25/10/2026): hiệu ứng, tự tắt, màn hình chờ, tiếng bấm remote; bấm là ghi lên tivi ngay. */
class CaiDatNhanhTivi(private val vm: PhienLamViec) {
    private val tvCd by lazy { CaiDatTv(vm.adb) }

    /** Giá trị đang có trên tivi; null = chưa đọc. */
    var giaTri by mutableStateOf<Map<CaiDatNhanh.Khoa, String?>?>(null)
        private set
    var bao by mutableStateOf("")
        private set

    fun mo() = vm.chay(t("Đang đọc cài đặt tivi…", "Reading the TV's settings…")) {
        vm.damBaoKetNoi()
        bao = ""
        giaTri = vm.io { tvCd.doc() }
        vm.man = Man.CAI_DAT_NHANH
    }

    fun datHieuUng(h: CaiDatNhanh.HieuUng) = ghi { CaiDatNhanh.HIEU_UNG.forEach { tvCd.dat(it, CaiDatNhanh.giaTriHieuUng(h)) } }
    fun datTuTat(ms: Long?) = ghi { tvCd.dat(CaiDatNhanh.TU_TAT, ms?.toString()) }
    fun datManCho(ms: Long?) = ghi { tvCd.dat(CaiDatNhanh.MAN_CHO, ms?.toString()) }
    fun datTiengBam(bat: Boolean) = ghi { tvCd.dat(CaiDatNhanh.TIENG_BAM, if (bat) "1" else "0") }

    /** Ghi rồi đọc lại từ tivi (thấy đúng giá trị tivi nhận). */
    private fun ghi(viec: () -> Unit) = vm.chay(t("Đang lưu trên tivi…", "Saving on the TV…")) {
        vm.damBaoKetNoi()
        vm.io { viec() }
        giaTri = vm.io { tvCd.doc() }
        bao = t("Đã lưu, có tác dụng ngay.", "Saved, effective right away.")
    }

    fun quen() { giaTri = null; bao = "" }
}
