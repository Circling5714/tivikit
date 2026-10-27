package vn.tivikit.dulieu

/**
 * Làm mới tivi (25/10/2026): đọc RAM (/proc/meminfo) và thời gian chạy từ lần khởi động gần nhất (/proc/uptime), cùng
 * các ngưỡng: bộ nhớ gần đầy mới xoá cache (cache giúp app mở nhanh, Android cũng tự dọn khi thiếu chỗ), chạy liên tục
 * lâu thì gợi ý khởi động lại (tắt bằng remote chỉ là chờ, tivi vẫn chạy).
 */
object LamMoi {
    /** RAM, KB: tổng và còn dùng được (MemAvailable: trống + bộ đệm thu lại được). */
    data class Ram(val tongKb: Long, val conKb: Long)

    /** Chạy liên tục từ chừng này ngày thì gợi ý khởi động lại. */
    const val NGAY_CHAY_LAU = 7

    fun tuMeminfo(s: String): Ram? {
        fun kb(ten: String) = Regex("""(?m)^$ten:\s+(\d+)\s*kB""").find(s)?.groupValues?.get(1)?.toLongOrNull()
        val tong = kb("MemTotal")?.takeIf { it > 0 } ?: return null
        // Nhân Linux cũ (trước 3.14) chưa có MemAvailable: cộng trống + bộ đệm.
        val con = kb("MemAvailable") ?: listOfNotNull(kb("MemFree"), kb("Buffers"), kb("Cached")).takeIf { it.isNotEmpty() }?.sum() ?: return null
        return Ram(tong, con.coerceAtMost(tong))
    }

    /** Giây từ lần khởi động gần nhất: số đầu của /proc/uptime (tính cả lúc tivi ở chế độ chờ). */
    fun tuUptime(s: String): Long? = s.trim().substringBefore(' ').toDoubleOrNull()?.toLong()?.takeIf { it >= 0 }

    /** Bộ nhớ gần đầy: trống dưới 1 GB hoặc dưới 15% (Android tự báo thiếu chỗ ở 10%, tối đa 500 MB). */
    fun boNhoGanDay(tongKb: Long, conKb: Long): Boolean = conKb < maxOf(1_048_576L, tongKb * 15 / 100)

    fun ramThap(r: Ram): Boolean = r.conKb * 100 < r.tongKb * 15

    fun chayLau(giay: Long): Boolean = giay >= NGAY_CHAY_LAU * 86_400L

    /** (ngày, giờ, phút) của [giay]. */
    fun tach(giay: Long): Triple<Long, Long, Long> = Triple(giay / 86_400, giay % 86_400 / 3_600, giay % 3_600 / 60)

    // ---------- Ai đang ăn CPU (26/10/2026, rút từ box TX3: CH Play cập nhật dồn làm mở video chậm) ----------

    /** Một tiến trình trong `dumpsys cpuinfo`; [phanTram] tính theo một nhân (bốn nhân bận hết là 400%). */
    data class TienTrinh(val ten: String, val phanTram: Double)

    /** Nhóm tiến trình để nói bằng lời: tivi đang bận việc gì. */
    enum class Nhom { CAP_NHAT, GOOGLE, MAN_HINH, VIDEO, AM_THANH, THIEU_RAM, HE_THONG, APP }

    /** Tải trung bình 1 phút / số nhân từ chừng này trở lên là quá tải (box TX3 lúc cập nhật dồn: 12–17 trên 4 nhân). */
    const val TAI_MOI_NHAN = 1.5

    /** CPU bận (phần trăm cả chip, đo /proc/stat) từ chừng này trở lên là quá tải. */
    const val BAN_QUA_TAI = 85

    /** Nhiệt độ chip (°C) từ chừng này trở lên là nóng, chip bắt đầu tự giảm xung. */
    const val NONG = 80

    /**
     * Dòng "  63% 4640/com.android.vending:background: 53% user + …" của `dumpsys cpuinfo` (tiến trình mới sinh / vừa
     * thoát có dấu + / - đứng trước), nhiều nhất trước; bỏ dòng TOTAL.
     */
    fun tuCpuinfo(s: String): List<TienTrinh> = s.lineSequence().mapNotNull { l ->
        val m = Regex("""^\s*[+-]?([\d.]+)% \d+/(.+?): """).find(l) ?: return@mapNotNull null
        TienTrinh(m.groupValues[2].trim(), m.groupValues[1].toDoubleOrNull() ?: return@mapNotNull null)
    }.filter { it.phanTram > 0 }.sortedByDescending { it.phanTram }.toList()

    /** Tải trung bình 1 phút: số đầu của /proc/loadavg. */
    fun tuLoadavg(s: String): Double? = s.trim().substringBefore(' ').toDoubleOrNull()?.takeIf { it >= 0 }

    /**
     * Phần trăm CPU bận trên cả chip giữa hai dòng "cpu …" của /proc/stat (27/10/2026). Tải trung bình không đo được CPU trên
     * tivi chip MStar / MediaTek: luồng nhân của driver video nằm chờ (trạng thái D) cũng tính vào tải — Skyworth SWTV-22AE
     * đang phát SmartTube báo tải 70 trên 4 nhân mà CPU chỉ bận 42%. Bận = tổng − rảnh − chờ đĩa (8 cột đầu; guest đã nằm
     * trong user). TiviKit chạy ngay trên tivi thì lệnh in thêm dòng "utime stime" của chính nó sau mỗi dòng cpu: phần đó
     * trừ ra (TX3: vẽ màn vừa mở + vòng chờ làm số đo lên 81% trong khi tivi chỉ bận 13–24%). Không đủ hai dòng / không đổi
     * gì thì null.
     */
    fun tuProcStat(s: String): Int? {
        val ds = s.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val dong = ds.filter { it.startsWith("cpu ") }
        if (dong.size < 2) return null
        fun cot(l: String) = l.split(Regex("\\s+")).drop(1).take(8).mapNotNull { it.toLongOrNull() }
        val a = cot(dong[0]); val b = cot(dong[dong.size - 1])
        if (a.size < 5 || b.size != a.size) return null
        val tong = b.sum() - a.sum()
        val ranh = (b[3] + b[4]) - (a[3] + a[4])
        if (tong <= 0) return null
        val rieng = ds.filter { DONG_RIENG.matches(it) }.map { l -> l.split(' ').sumOf { it.toLong() } }
        val tuDung = if (rieng.size == 2) (rieng[1] - rieng[0]).coerceAtLeast(0) else 0
        return ((tong - ranh - tuDung).coerceAtLeast(0) * 100 / tong).toInt().coerceIn(0, 100)
    }

    private val DONG_RIENG = Regex("""^\d+ \d+$""")

    /** Số nhân từ /sys/devices/system/cpu/possible: "0-3" → 4, "0-1,4-7" → 6. */
    fun tuSoNhan(s: String): Int? = s.trim().takeIf { it.isNotEmpty() }?.split(',')?.sumOf { k ->
        val dau = k.substringBefore('-').trim().toIntOrNull() ?: return null
        val cuoi = if ('-' in k) k.substringAfter('-').trim().toIntOrNull() ?: return null else dau
        if (cuoi < dau) return null
        cuoi - dau + 1
    }?.takeIf { it > 0 }

    /** Nhiệt độ thermal_zone0: phần nghìn độ (70000) hoặc độ (45) tuỳ máy → °C; ngoài 1..150 °C là đọc sai. */
    fun tuNhietDo(s: String): Int? {
        val v = s.trim().toLongOrNull() ?: return null
        return (if (v >= 1000) v / 1000 else v).toInt().takeIf { it in 1..150 }
    }

    fun quaTai(tai: Double, soNhan: Int): Boolean = soNhan > 0 && tai >= soNhan * TAI_MOI_NHAN

    /** Quá tải: đo được CPU bận thì theo nó; không thì theo tải trung bình như trước (máy không đọc được /proc/stat). */
    fun quaTai(cpuBan: Int?, tai: Double?, soNhan: Int?): Boolean = when {
        cpuBan != null -> cpuBan >= BAN_QUA_TAI
        tai != null && soNhan != null -> quaTai(tai, soNhan)
        else -> false
    }

    fun nhom(ten: String): Nhom = when {
        ten.startsWith("dex2oat") || ten == "installd" || ten.startsWith("com.android.vending") -> Nhom.CAP_NHAT
        ten.startsWith("com.google.android.gms") || ten.startsWith("com.google.process.gapps") -> Nhom.GOOGLE
        ten.startsWith("kswapd") -> Nhom.THIEU_RAM
        ten == "surfaceflinger" || ten.contains("graphics.composer") -> Nhom.MAN_HINH
        ten == "mediaserver" || ten.startsWith("media.") || ten.contains("mediacodec") || ten.contains("omx") -> Nhom.VIDEO
        ten == "audioserver" || ten.contains("hardware.audio") -> Nhom.AM_THANH
        // Tiến trình app luôn mang tên gói (có dấu chấm); không có dấu chấm là dịch vụ / luồng nhân (adbd, kworker, irq/44…).
        ten == "system_server" || !ten.contains('.') || ten.startsWith("android.hardware.") || ten.startsWith("vendor.") -> Nhom.HE_THONG
        else -> Nhom.APP
    }

    /**
     * Việc làm tivi bận nhất trong [ds] (cộng % theo nhóm). Thiếu RAM tính trước nếu kswapd ăn từ 10% một nhân: dọn bộ nhớ
     * liên tục là dấu hiệu rõ nhất, dù không phải nhóm lớn nhất.
     */
    fun banViec(ds: List<TienTrinh>): Nhom? {
        if (ds.any { nhom(it.ten) == Nhom.THIEU_RAM && it.phanTram >= 10 }) return Nhom.THIEU_RAM
        return ds.groupBy { nhom(it.ten) }.mapValues { (_, v) -> v.sumOf { it.phanTram } }.maxByOrNull { it.value }?.key
    }
}
