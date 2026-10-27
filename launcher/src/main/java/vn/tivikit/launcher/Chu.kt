package vn.tivikit.launcher

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate
import java.util.Locale

/**
 * Tiếng Việt hay tiếng Anh: TiviKit gửi theo ngôn ngữ của app trên điện thoại ("ngon_ngu"), chưa gửi thì theo tivi.
 * Là state của Compose nên đổi ngôn ngữ thì màn Home vẽ lại ngay.
 */
object NgonNgu {
    var tiengViet by mutableStateOf(Locale.getDefault().language == "vi")
}

fun t(vi: String, en: String): String = if (NgonNgu.tiengViet) vi else en

/** Chữ trên màn Home (không phụ thuộc Android để thử được). */
object Chu {
    private val THU = listOf("THỨ HAI", "THỨ BA", "THỨ TƯ", "THỨ NĂM", "THỨ SÁU", "THỨ BẢY", "CHỦ NHẬT")
    private val DAY = listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY")
    private val MONTH = listOf("JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE", "JULY", "AUGUST", "SEPTEMBER",
        "OCTOBER", "NOVEMBER", "DECEMBER")

    enum class Buoi { SANG, TRUA, CHIEU, TOI, DEM }

    fun buoi(gio: Int): Buoi = when (gio) {
        in 4..10 -> Buoi.SANG
        in 11..12 -> Buoi.TRUA
        in 13..17 -> Buoi.CHIEU
        in 18..21 -> Buoi.TOI
        else -> Buoi.DEM
    }

    /** Tên tự nó đã là lời chào (Home sạch của Skyworth đặt "Chào CaoMinh Family"): hiện nguyên, khỏi "Chào buổi tối, Chào …". */
    private val DA_LA_LOI_CHAO = Regex("^(xin chào|chào|hello|hi|hey|good (morning|afternoon|evening|night))(?=[\\s,!.]|$)", RegexOption.IGNORE_CASE)

    /** "Chào buổi sáng, Nhà bác Hùng" / "Good morning, The Smiths"; không có tên thì chỉ lời chào. */
    fun loiChao(gio: Int, ten: String, viet: Boolean): String {
        if (DA_LA_LOI_CHAO.containsMatchIn(ten.trim())) return ten.trim()
        val chao = when (buoi(gio)) {
            Buoi.SANG -> if (viet) "Chào buổi sáng" else "Good morning"
            Buoi.TRUA -> if (viet) "Chào buổi trưa" else "Good afternoon"
            Buoi.CHIEU -> if (viet) "Chào buổi chiều" else "Good afternoon"
            Buoi.TOI -> if (viet) "Chào buổi tối" else "Good evening"
            Buoi.DEM -> if (viet) "Chúc ngủ ngon" else "Good night"
        }
        return if (ten.isBlank()) chao else "$chao, ${ten.trim()}"
    }

    /** "THỨ BA, 27 THÁNG 10" / "TUESDAY, OCTOBER 27". */
    fun ngay(d: LocalDate, viet: Boolean): String {
        val i = d.dayOfWeek.value - 1
        return if (viet) "${THU[i]}, ${d.dayOfMonth} THÁNG ${d.monthValue}" else "${DAY[i]}, ${MONTH[d.monthValue - 1]} ${d.dayOfMonth}"
    }

    /** "502 MB", "1,2 GB" (tiếng Anh "1.2 GB"); dưới 10 GB lấy một số lẻ. */
    fun dungLuong(b: Long, viet: Boolean): String {
        val mb = b / (1024.0 * 1024.0)
        if (mb < 1024) return "${mb.toLong()} MB"
        val gb = mb / 1024
        val s = if (gb < 10) "%.1f".format(Locale.ROOT, gb) else "%.0f".format(Locale.ROOT, gb)
        return (if (viet) s.replace('.', ',') else s) + " GB"
    }

    /** Tốc độ mạng mỗi giây: "0 KB/s", "640 KB/s", "2,4 MB/s". */
    fun tocDo(bPerS: Long, viet: Boolean): String {
        val kb = bPerS / 1024.0
        if (kb < 1000) return "${kb.toLong()} KB/s"
        val s = "%.1f".format(Locale.ROOT, kb / 1024)
        return (if (viet) s.replace('.', ',') else s) + " MB/s"
    }

    /** Dòng thông số dưới đồng hồ: "RAM trống 502 MB · Bộ nhớ trống 1,2 GB". */
    fun thongSo(ram: Long, boNho: Long, viet: Boolean): String =
        if (viet) "RAM trống ${dungLuong(ram, true)} · Bộ nhớ trống ${dungLuong(boNho, true)}"
        else "${dungLuong(ram, false)} RAM free · ${dungLuong(boNho, false)} storage free"
}
