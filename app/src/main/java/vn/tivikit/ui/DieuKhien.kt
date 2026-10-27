package vn.tivikit.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import vn.tivikit.adb.PhimTv
import vn.tivikit.adb.ThaoTacTv
import vn.tivikit.dulieu.GoChu
import vn.tivikit.t

/**
 * Remote trên điện thoại và ô gõ chữ (25/10/2026). Phím không đi qua vm.chay (khoá cả màn và hiện thanh "đang làm"):
 * remote phải bấm liên tục được. Phím vào một hàng đợi; luồng gửi lấy hết phím đang chờ gửi chung một lệnh, để tivi
 * Android 9–10 (mỗi lệnh `input` ~0,5 giây) không trễ dồn.
 */
class DieuKhien(private val vm: PhienLamViec) {
    /** Chữ trong ô gõ (gửi lên ô nhập đang chọn trên tivi). */
    var chu by mutableStateOf("")
    /** Dòng trạng thái nhỏ dưới remote: lỗi kết nối, đã gửi chữ… */
    var bao by mutableStateOf("")
    /** Loại dòng trạng thái, để tô màu: đang gửi, xong, xong một phần (gửi không dấu), lỗi. */
    enum class LoaiBao { DANG, XONG, MOT_PHAN, LOI }
    var loaiBao by mutableStateOf(LoaiBao.XONG)
        private set
    /** Đang gửi chữ: bấm Gửi lần nữa không gửi chồng. */
    var dangGui by mutableStateOf(false)
        private set

    private fun bao(chu: String, loai: LoaiBao) { bao = chu; loaiBao = loai }

    private class Phim(val ma: Int, val giu: Boolean)
    private val hang = Channel<Phim>(Channel.UNLIMITED)
    private var guiPhim: Job? = null

    fun bam(ma: Int, giu: Boolean = false) {
        // Đang xem màn hình tivi: phím đi qua kênh điều khiển của scrcpy, tivi nhận ngay. Nhấn giữ vẫn đi đường cũ
        // (`--longpress`): kênh scrcpy chỉ có nhấn / nhả, không tự sinh sự kiện giữ phím.
        if (!giu && vm.xemManHinh.phim(ma)) return
        hang.trySend(Phim(ma, giu))
        if (guiPhim?.isActive != true) guiPhim = vm.viewModelScope.launch {
            for (p in hang) {
                var dau: Phim? = p
                while (dau != null) {
                    val (lenh, conLai) = gom(dau)
                    gui(lenh)
                    dau = conLai
                }
            }
        }
    }

    /**
     * Lệnh cho phím đầu + các phím thường đang chờ, đúng thứ tự bấm. Nhấn giữ thì gửi riêng (`--longpress` chỉ nhận
     * một phím): gặp nó giữa chừng thì dừng gom, trả nó về làm phím đầu của lượt sau.
     */
    private fun gom(dau: Phim): Pair<String, Phim?> {
        if (dau.giu) return PhimTv.lenhPhim(listOf(dau.ma), giu = true) to null
        val ds = mutableListOf(dau.ma)
        while (true) {
            val k = hang.tryReceive().getOrNull() ?: break
            if (k.giu) return PhimTv.lenhPhim(ds) to k
            ds += k.ma
        }
        return PhimTv.lenhPhim(ds) to null
    }

    /** Chạy lệnh trên tivi; rớt kết nối thì nối lại đúng tivi đó một lần rồi chạy lại. */
    private suspend fun gui(lenh: String): Boolean {
        val lan1 = runCatching { vm.io { vm.adb.shell(lenh) } }
        if (lan1.isSuccess) { if (loaiBao != LoaiBao.DANG) bao("", LoaiBao.XONG); return true }
        val e = lan1.exceptionOrNull()!!
        if (ThaoTacTv.laMatKetNoi(e) && vm.io { vm.adb.ketNoiLai() && vm.adb.conSong() } &&
            runCatching { vm.io { vm.adb.shell(lenh) } }.isSuccess) { if (loaiBao != LoaiBao.DANG) bao("", LoaiBao.XONG); return true }
        bao(if (ThaoTacTv.laMatKetNoi(e)) t("Mất kết nối với tivi. Kiểm tra tivi còn bật và cùng Wi-Fi rồi bấm lại.", "Lost the TV. Check it is on and on the same Wi-Fi, then press again.")
            else t("Tivi không nhận lệnh: ${e.message}", "The TV rejected the command: ${e.message}"), LoaiBao.LOI)
        return false
    }

    /**
     * Gửi chữ trong ô lên tivi, [enter] = bấm Enter sau khi gõ. Chữ không dấu gõ bằng `input text` (nhanh); chữ có dấu
     * (25/10/2026) đi qua bộ gõ tiếng Việt của TiviKit trên tivi (GoChu); bộ gõ hỏng thì dán qua clipboard (27/10/2026), ô
     * nhập không nhận thì gõ không dấu như trước.
     */
    fun guiChu(enter: Boolean) {
        val s = GoChu.chuan(chu)
        if ((s.isEmpty() && !enter) || dangGui) return
        dangGui = true
        vm.viewModelScope.launch {
            try {
                var xong = t("Đã gửi.", "Sent.") to LoaiBao.XONG
                if (s.isNotEmpty()) {
                    val kq = if (GoChu.canBoGo(s)) goCoDau(s) ?: return@launch else "khong-dau"
                    if (kq != "ok" && GoChu.nenDan(kq) && danQuaClipboard(s)) {
                        xong = t("Đã dán \"$s\" vào ô đang chọn trên tivi (bộ gõ tiếng Việt chưa chạy được trên tivi này: $kq).",
                            "Pasted \"$s\" into the selected box on the TV (the Vietnamese typing helper doesn't work on this TV: $kq).") to LoaiBao.XONG
                    } else if (kq != "ok") {
                        for (lenh in PhimTv.lenhChu(s)) if (!gui(lenh)) return@launch
                        val bd = PhimTv.boDau(s)
                        if (kq == "khong-o-nhap") xong = t("Đã gửi \"$bd\" không dấu: ô đang chọn trên tivi không nhận chữ có dấu (hoặc chưa chọn ô nhập).",
                            "Sent \"$bd\" without accents: the selected box on the TV doesn't take accented letters (or no box is selected).") to LoaiBao.MOT_PHAN
                        else if (kq != "khong-dau") xong = t("Đã gửi \"$bd\" không dấu: tivi này chưa gõ được chữ có dấu ($kq).",
                            "Sent \"$bd\" without accents: this TV can't type accented letters yet ($kq).") to LoaiBao.MOT_PHAN
                    }
                }
                if (enter && !gui(PhimTv.lenhPhim(listOf(PhimTv.ENTER)))) return@launch
                chu = ""
                if (s.isEmpty()) bao("", LoaiBao.XONG) else bao(xong.first, xong.second)
            } finally { dangGui = false }
        }
    }

    /**
     * Bộ gõ không chạy được (GoChu.nenDan): dán [s] qua clipboard của tivi bằng máy chủ scrcpy (27/10/2026; chỉ Android 7+
     * mới bấm dán được). false = không dán được, gõ không dấu như trước.
     */
    private suspend fun danQuaClipboard(s: String): Boolean {
        if ((vm.thongTin?.sdk ?: 0) < 24) return false
        bao(t("Bộ gõ không chạy được, đang dán chữ qua bộ nhớ tạm của tivi…", "The typing helper failed; pasting through the TV's clipboard…"), LoaiBao.DANG)
        return runCatching { vm.kenhDieuKhien.dan(s) }.getOrDefault(false)
    }

    /**
     * Gõ chữ có dấu qua bộ gõ của TiviKit trên tivi (lần đầu cài bộ gõ); rớt kết nối thì nối lại một lần. Trả "ok" / mã
     * lỗi của bộ gõ ("loi" nếu cài không được…), null nếu mất kết nối (đã báo).
     */
    private suspend fun goCoDau(s: String): String? {
        bao(t("Đang gõ…", "Typing…"), LoaiBao.DANG)
        val apk = runCatching { vm.io { vn.tivikit.CapNhat.moAsset(vm.ctx, "go.apk").readBytes() } }.getOrNull() ?: return "khong-co-bo-go"
        suspend fun mot() = runCatching { vm.io { vm.tv.goChuCoDau(apk, s) { bao(it, LoaiBao.DANG) } } }
        var kq = mot()
        if (kq.exceptionOrNull()?.let { ThaoTacTv.laMatKetNoi(it) } == true && vm.io { vm.adb.ketNoiLai() && vm.adb.conSong() }) kq = mot()
        val e = kq.exceptionOrNull() ?: return kq.getOrNull()
        if (!ThaoTacTv.laMatKetNoi(e)) return "loi"
        bao(t("Mất kết nối với tivi. Kiểm tra tivi còn bật và cùng Wi-Fi rồi bấm lại.", "Lost the TV. Check it is on and on the same Wi-Fi, then press again."), LoaiBao.LOI)
        return null
    }
}
