package vn.tivikit.khoa

import java.util.Calendar

/**
 * Thống kê app đã mở (25/10/2026): gộp sự kiện app lên / rời màn hình (UsageStatsManager) thành thời gian từng app ở
 * trên cùng, chia theo ngày của tivi. Không đụng Android để test được trên máy tính.
 */
object ThongKe {
    /** [loai]: LEN = app lên trên cùng, XUONG = app rời màn hình, TAT = màn hình tắt / tắt máy (app đang mở cũng thôi). */
    data class SuKien(val luc: Long, val goi: String, val loai: Int)
    const val LEN = 1
    const val XUONG = 2
    const val TAT = 3

    /** Ngày ("2026-10-25") → gói → mili giây ở trên cùng, chỉ tính phần nằm trong [tu, den). [ds] xếp theo thời gian. */
    fun gom(ds: List<SuKien>, tu: Long, den: Long): Map<String, Map<String, Long>> {
        val kq = sortedMapOf<String, MutableMap<String, Long>>()
        var dang: String? = null
        var tuLuc = 0L
        fun dong(luc: Long) {
            val g = dang ?: return
            chia(maxOf(tuLuc, tu), minOf(luc, den)).forEach { (ngay, ms) ->
                val m = kq.getOrPut(ngay) { mutableMapOf() }
                m[g] = (m[g] ?: 0L) + ms
            }
            dang = null
        }
        for (e in ds) when (e.loai) {
            LEN -> if (e.goi != dang) { dong(e.luc); dang = e.goi; tuLuc = e.luc }
            XUONG -> if (e.goi == dang) dong(e.luc)
            TAT -> dong(e.luc)
        }
        dong(den)
        return kq
    }

    /** Chia đoạn [a, b) theo nửa đêm giờ tivi: (ngày, mili giây). */
    fun chia(a: Long, b: Long): List<Pair<String, Long>> {
        val kq = mutableListOf<Pair<String, Long>>()
        val c = Calendar.getInstance().apply { timeInMillis = a }
        var dau = a
        while (dau < b) {
            val ngay = Luat.ngay(c)
            c.add(Calendar.DAY_OF_MONTH, 1)
            c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
            val cuoi = minOf(c.timeInMillis, b)
            kq += ngay to (cuoi - dau)
            dau = cuoi
        }
        return kq
    }
}
