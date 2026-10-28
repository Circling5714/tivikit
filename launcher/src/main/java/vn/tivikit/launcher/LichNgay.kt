package vn.tivikit.launcher

import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.PI
import kotlin.math.floor

/**
 * Lịch chi tiết của một ngày cho bảng lịch (bấm vào đồng hồ): can chi ngày / tháng / năm, tiết khí, trực, ngày hoàng đạo
 * hay hắc đạo (12 thần trực nhật), giờ hoàng đạo, ngày lễ, Tam nương / Nguyệt kỵ. Can chi, giờ hoàng đạo và tiết khí theo
 * lịch vạn niên của Hồ Ngọc Đức; thần và trực theo cách xem ngày phổ biến (đã so với ngaydep.com, xemlicham.com).
 */
object LichNgay {
    class Ngay(
        val duong: LocalDate,
        val am: AmLich.Ngay,
        /** Chỉ số trong AmLich.CAN / CHI. */
        val canNgay: Int, val chiNgay: Int, val canThang: Int, val chiThang: Int,
        /** 0 = Xuân phân … 23 = Kinh trập. */
        val tiet: Int,
        /** 0 = Kiến … 11 = Bế. */
        val truc: Int,
        /** 12 thần trực nhật: 0 = Thanh Long … 11 = Câu Trận. */
        val than: Int,
        /** Giờ hoàng đạo: chi của giờ (0 = Tý 23–1 giờ, 1 = Sửu 1–3 giờ…). */
        val gioTot: List<Int>,
        val le: List<Le>,
    ) {
        val hoangDao: Boolean get() = than in THAN_TOT
        val tamNuong: Boolean get() = am.ngay in TAM_NUONG
        val nguyetKy: Boolean get() = am.ngay in NGUYET_KY
        /** Mùng 1, rằm: ô lịch tô màu. */
        val soc: Boolean get() = am.ngay == 1 || am.ngay == 15
    }

    class Le(val vi: String, val en: String)

    private val TIET_KHI = listOf("Xuân phân", "Thanh minh", "Cốc vũ", "Lập hạ", "Tiểu mãn", "Mang chủng", "Hạ chí", "Tiểu thử",
        "Đại thử", "Lập thu", "Xử thử", "Bạch lộ", "Thu phân", "Hàn lộ", "Sương giáng", "Lập đông", "Tiểu tuyết", "Đại tuyết",
        "Đông chí", "Tiểu hàn", "Đại hàn", "Lập xuân", "Vũ thủy", "Kinh trập")
    private val TIET_KHI_EN = listOf("Spring Equinox", "Clear and Bright", "Grain Rain", "Start of Summer", "Grain Buds",
        "Grain in Ear", "Summer Solstice", "Minor Heat", "Major Heat", "Start of Autumn", "End of Heat", "White Dew",
        "Autumn Equinox", "Cold Dew", "Frost's Descent", "Start of Winter", "Minor Snow", "Major Snow", "Winter Solstice",
        "Minor Cold", "Major Cold", "Start of Spring", "Rain Water", "Awakening of Insects")
    private val TRUC = listOf("Kiến", "Trừ", "Mãn", "Bình", "Định", "Chấp", "Phá", "Nguy", "Thành", "Thu", "Khai", "Bế")
    private val THAN = listOf("Thanh Long", "Minh Đường", "Thiên Hình", "Chu Tước", "Kim Quỹ", "Bảo Quang", "Bạch Hổ",
        "Ngọc Đường", "Thiên Lao", "Huyền Vũ", "Tư Mệnh", "Câu Trận")
    private val THAN_TOT = setOf(0, 1, 4, 5, 7, 10)

    /** Giờ hoàng đạo theo chi của ngày (chi % 6): '1' ở vị trí chi của giờ tốt, từ giờ Tý. */
    private val GIO_HD = listOf("110100101100", "001101001011", "110011010010", "101100110100", "001011001101", "010010110011")
    private val TAM_NUONG = setOf(3, 7, 13, 18, 22, 27)
    private val NGUYET_KY = setOf(5, 14, 23)

    private val LE_DUONG = mapOf(
        101 to Le("Tết Dương lịch", "New Year's Day"),
        203 to Le("Thành lập Đảng Cộng sản Việt Nam", "Communist Party Founding Day"),
        214 to Le("Lễ Tình nhân", "Valentine's Day"),
        308 to Le("Quốc tế Phụ nữ", "International Women's Day"),
        430 to Le("Giải phóng miền Nam", "Reunification Day"),
        501 to Le("Quốc tế Lao động", "International Workers' Day"),
        519 to Le("Ngày sinh Chủ tịch Hồ Chí Minh", "President Ho Chi Minh's Birthday"),
        601 to Le("Quốc tế Thiếu nhi", "Children's Day"),
        727 to Le("Thương binh Liệt sĩ", "War Invalids and Martyrs' Day"),
        902 to Le("Quốc khánh", "National Day"),
        1020 to Le("Phụ nữ Việt Nam", "Vietnamese Women's Day"),
        1120 to Le("Nhà giáo Việt Nam", "Teachers' Day"),
        1222 to Le("Quân đội Nhân dân Việt Nam", "People's Army Day"),
        1224 to Le("Đêm Giáng sinh", "Christmas Eve"),
        1225 to Le("Giáng sinh", "Christmas Day"),
    )

    /** Lễ âm lịch (không tính tháng nhuận); giao thừa xét riêng. */
    private val LE_AM = mapOf(
        101 to Le("Tết Nguyên Đán", "Lunar New Year"),
        102 to Le("Mùng 2 Tết", "Lunar New Year (day 2)"),
        103 to Le("Mùng 3 Tết", "Lunar New Year (day 3)"),
        115 to Le("Tết Nguyên Tiêu (Rằm tháng Giêng)", "Lantern Festival"),
        303 to Le("Tết Hàn thực", "Cold Food Festival"),
        310 to Le("Giỗ Tổ Hùng Vương", "Hung Kings' Commemoration Day"),
        415 to Le("Lễ Phật Đản", "Buddha's Birthday"),
        505 to Le("Tết Đoan ngọ", "Double Fifth Festival"),
        715 to Le("Lễ Vu Lan", "Vu Lan Festival"),
        815 to Le("Tết Trung thu", "Mid-Autumn Festival"),
        1223 to Le("Ông Công Ông Táo", "Kitchen Gods' Day"),
    )

    fun tinh(d: LocalDate): Ngay {
        val jd = AmLich.jd(d.dayOfMonth, d.monthValue, d.year)
        val am = AmLich.tuDuongLich(d.dayOfMonth, d.monthValue, d.year)
        val chiNgay = Math.floorMod(jd + 1, 12L).toInt()
        // Tiết khí của ngày: kinh độ mặt trời lúc hết ngày (đầu ngày hôm sau), như lịch của Hồ Ngọc Đức.
        val l = AmLich.kinhDoRad(jd + 1, 7.0)
        val tiet = floor(l / PI * 12).toInt().coerceIn(0, 23)
        // Tháng theo tiết: Dần từ Lập xuân, Mão từ Kinh trập, Thìn từ Thanh minh…; ngày cùng chi với tháng là trực Kiến.
        val chiThangTiet = ((tiet + 1) / 2 + 3) % 12
        val bat = ((am.thang - 1) % 6) * 2
        val gio = GIO_HD[chiNgay % 6]
        return Ngay(
            duong = d, am = am,
            canNgay = Math.floorMod(jd + 9, 10L).toInt(), chiNgay = chiNgay,
            canThang = Math.floorMod(am.nam * 12 + am.thang + 3, 10), chiThang = (am.thang + 1) % 12,
            tiet = tiet,
            truc = Math.floorMod(chiNgay - chiThangTiet, 12),
            than = Math.floorMod(chiNgay - bat, 12),
            gioTot = (0 until 12).filter { gio[it] == '1' },
            le = le(d, am),
        )
    }

    private fun le(d: LocalDate, am: AmLich.Ngay): List<Le> {
        val ds = mutableListOf<Le>()
        LE_DUONG[d.monthValue * 100 + d.dayOfMonth]?.let(ds::add)
        if (!am.nhuan) LE_AM[am.thang * 100 + am.ngay]?.let(ds::add)
        if (am.thang == 12 && !am.nhuan) {
            val mai = d.plusDays(1)
            val amMai = AmLich.tuDuongLich(mai.dayOfMonth, mai.monthValue, mai.year)
            if (amMai.ngay == 1 && amMai.thang == 1) ds += Le("Giao thừa", "Lunar New Year's Eve")
        }
        return ds
    }

    /** Các ô của bảng tháng [thang]: 6 tuần từ thứ Hai, kể cả ngày của tháng trước / sau. */
    fun oThang(thang: YearMonth): List<LocalDate> {
        val dau = thang.atDay(1)
        val batDau = dau.minusDays((dau.dayOfWeek.value - 1).toLong())
        return (0 until 42).map { batDau.plusDays(it.toLong()) }
    }

    fun canChi(can: Int, chi: Int) = AmLich.CAN[can] + " " + AmLich.CHI[chi]

    fun tiet(i: Int, viet: Boolean) = if (viet) TIET_KHI[i] else TIET_KHI_EN[i]

    fun truc(i: Int) = TRUC[i]

    fun than(i: Int) = THAN[i]

    fun chi(i: Int) = AmLich.CHI[i]

    /** Khung giờ của giờ âm [chi]: Tý "23–1h", Sửu "1–3h"… */
    fun khungGio(chi: Int) = "${(chi * 2 + 23) % 24}–${(chi * 2 + 1) % 24}h"

    /** "tháng Giêng", "tháng 9", "tháng Chạp", "tháng 6 nhuận". */
    fun tenThangAm(thang: Int, nhuan: Boolean): String {
        val t = when (thang) { 1 -> "tháng Giêng"; 12 -> "tháng Chạp"; else -> "tháng $thang" }
        return if (nhuan) "$t nhuận" else t
    }
}
