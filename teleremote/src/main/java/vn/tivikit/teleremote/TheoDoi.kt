package vn.tivikit.teleremote

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * Việc bot tự để ý (1/11/2026): mỗi [CHU_KY] hỏi app Cast (qua ADB) xem có điện thoại lạ vừa xin cast không; có máy mới thì
 * nhắn các chủ tivi kèm nút cho phép / chặn. Tivi cũng đang hỏi trên màn hình: ai trả lời trước cũng được.
 */
class TheoDoi(private val dv: TeleRemoteDichVu) {
    @Volatile private var chay = false
    /** Máy đã báo (id của app Cast → số trong nút), để không báo lại. */
    private val daBao = ConcurrentHashMap<String, Int>()
    private val soMoi = AtomicInteger(1)

    /** Số trong nút → (id máy của app Cast, tên máy): nút Telegram chỉ chứa 64 byte nên không đưa id vào. */
    val castMay = ConcurrentHashMap<Int, Pair<String, String>>()

    fun batDau() {
        if (chay) return
        chay = true
        thread(name = "TeleRemote-theodoi", isDaemon = true) {
            while (chay) {
                runCatching { xemCast() }
                Thread.sleep(CHU_KY)
            }
        }
    }

    fun dung() {
        chay = false
    }

    private fun xemCast() {
        if (!dv.botDangNghe || !dv.coAdb() || !dv.coApp(LamViec.CAST)) return
        if (dv.kho.cauHinh.nguoiDung.none { it.vaiTro == VaiTro.CHU && it.id != 0L }) return
        val j = dv.goiAppPhu(LamViec.CAST, "doc", choToiDa = 5000)?.let(XuLyLenh::jsonTrong) ?: return
        val bay = System.currentTimeMillis()
        val en = dv.kho.cauHinh.tiengAnh
        val ds = j.optJSONArray("dienThoai") ?: return
        for (i in 0 until ds.length()) {
            val p = ds.optJSONObject(i) ?: continue
            val id = p.optString("id")
            // Chỉ máy đang chờ và vừa xin (máy chờ từ lâu không ai duyệt thì thôi, khỏi báo lại mỗi lần dịch vụ chạy lại).
            if (id.isEmpty() || p.optString("trangThai") != "CHO" || bay - p.optLong("lanCuoi") > MOI_XIN || daBao.containsKey(id)) continue
            val so = soMoi.getAndIncrement()
            daBao[id] = so
            val ten = listOf(p.optString("ten"), p.optString("taiKhoan")).filter { it.isNotBlank() }.distinct().joinToString(" · ").ifEmpty { "?" }
            castMay[so] = id to ten
            dv.baoChuKemNut(if (en) "📱 \"$ten\" wants to cast to ${dv.tenTivi()}. Allow it?" else "📱 Máy \"$ten\" muốn cast lên tivi ${dv.tenTivi()}. Cho phép?",
                XuLyLenh.bangCast(so, en))
        }
    }

    companion object {
        private const val CHU_KY = 20_000L
        /** Máy xin cast trong chừng này (ms) mới báo. */
        private const val MOI_XIN = 10 * 60_000L
    }
}
