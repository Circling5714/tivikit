package vn.tivikit.ui

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import vn.tivikit.dulieu.DangPhat

/**
 * Thẻ Đang phát của màn Điều khiển (27/10/2026): lúc màn hiện, 2 giây đọc `dumpsys media_session` một lần (DangPhat). Chạy cả
 * trên box RAM thấp không cho app nghe thông báo (TX3 Mini), vì đọc bằng quyền shell. Nút trên thẻ là phím media (bài
 * trước / phát-dừng / bài sau), đi qua kênh phím của remote nên app đang phát nhận ngay, kể cả khi nó chạy nền.
 */
class DangPhatTv(private val vm: PhienLamViec) {
    /** [viTri] tính tới lúc [nhanLuc] (elapsedRealtime của điện thoại); đang phát thì giao diện tự cộng thêm. */
    class Bai(val goi: String, val tt: Int, val tieuDe: String?, val phu: String?, val viTri: Long, val tocDo: Float, val nhanLuc: Long) {
        val dangPhat get() = tt == DangPhat.PHAT
        fun viTriLuc(bayGio: Long): Long = if (dangPhat) viTri + ((bayGio - nhanLuc) * tocDo).toLong() else viTri
    }

    var bai by mutableStateOf<Bai?>(null)
        private set
    private var viec: Job? = null

    /**
     * Mốc tới của từng app: YouTube trên Google TV báo mốc cập nhật lùi khoảng hai phút (tv-remote đo), video như bắt đầu ở
     * 2:15. Một trạng thái không thể cũ hơn lúc nó tới: mốc mới mà lùi hơn 10 giây trước lúc thấy thì lấy lúc thấy (đọc 2 giây
     * một lần nên mốc thật có thể cũ tới ~2 giây).
     */
    private val moc = HashMap<String, Pair<Long, Long>>()

    /** Màn hiện: đọc mỗi 2 giây. */
    fun mo() {
        if (viec?.isActive == true) return
        viec = vm.viewModelScope.launch {
            var lanDau = true
            while (isActive) {
                runCatching { doc(lanDau) }
                lanDau = false
                delay(2000)
            }
        }
    }

    /** Rời màn / app xuống nền / đổi tivi: thôi đọc. */
    fun dong() {
        viec?.cancel()
        viec = null
        bai = null
        moc.clear()
    }

    private suspend fun doc(lanDau: Boolean) {
        val out = vm.io { vm.adb.shell("dumpsys media_session; echo @@; cat /proc/uptime", 15) }
        val bayGio = (out.substringAfterLast("@@").trim().substringBefore(' ').toDoubleOrNull()?.times(1000))?.toLong() ?: return
        val ds = DangPhat.tuDump(out.substringBeforeLast("@@"))
        // Ghi mốc của mọi phiên (không chỉ phiên đang hiện): phiên lần đầu thấy giữa chừng là phiên mới bắt đầu phát.
        for (s in ds) {
            val cu = moc[s.goi]
            moc[s.goi] = s.capNhat to when {
                cu != null && cu.first == s.capNhat -> cu.second
                !lanDau && s.capNhat < bayGio - 10_000 -> bayGio
                else -> s.capNhat
            }
        }
        var p = DangPhat.chon(ds, null)
        // Chỉ có phiên đang dừng: hiện khi app của nó đang ở trước màn hình (rời app đang dừng thì phiên vẫn còn).
        if (p == null && ds.any { it.hoatDong && it.tt == DangPhat.DUNG }) p = DangPhat.chon(ds, vm.io { vm.tv.goiDangHien() })
        if (p == null) { bai = null; return }
        val tu = moc[p.goi]?.second ?: p.capNhat
        bai = Bai(p.goi, p.tt, p.tieuDe, p.phu, DangPhat.viTriLuc(p, bayGio, tu), p.tocDo, SystemClock.elapsedRealtime())
    }
}
