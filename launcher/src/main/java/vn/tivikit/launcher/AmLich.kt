package vn.tivikit.launcher

import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

/**
 * Âm lịch Việt Nam (tính theo UTC+7), không cần ICU. Thuật toán công khai của Hồ Ngọc Đức (trăng non và tiết khí theo
 * công thức của Jean Meeus), chép từ Lunar.java của Home sạch (CleanYourTV, MIT), giữ nguyên từng số hạng và dấu của nó.
 */
object AmLich {
    data class Ngay(val ngay: Int, val thang: Int, val nam: Int, val nhuan: Boolean)

    private val CAN = listOf("Giáp", "Ất", "Bính", "Đinh", "Mậu", "Kỷ", "Canh", "Tân", "Nhâm", "Quý")
    private val CHI = listOf("Tý", "Sửu", "Dần", "Mão", "Thìn", "Tỵ", "Ngọ", "Mùi", "Thân", "Dậu", "Tuất", "Hợi")
    // Con giáp kiểu Việt Nam: Mão là mèo, Sửu là trâu.
    private val CON_GIAP = listOf("Rat", "Buffalo", "Tiger", "Cat", "Dragon", "Snake", "Horse", "Goat", "Monkey", "Rooster", "Dog", "Pig")

    fun canChi(nam: Int) = CAN[Math.floorMod(nam + 6, 10)] + " " + CHI[Math.floorMod(nam + 8, 12)]

    /** "Âm lịch 18/09 (Bính Ngọ)" / "Lunar 18/09 · Year of the Horse". */
    fun chu(d: LocalDate, viet: Boolean): String {
        val a = tuDuongLich(d.dayOfMonth, d.monthValue, d.year)
        val dm = "%02d/%02d".format(a.ngay, a.thang) + if (a.nhuan) (if (viet) " nhuận" else " (leap)") else ""
        return if (viet) "Âm lịch $dm (${canChi(a.nam)})" else "Lunar $dm · Year of the ${CON_GIAP[Math.floorMod(a.nam + 8, 12)]}"
    }

    /** Ngày dương lịch → ngày / tháng / năm âm lịch ở múi giờ [tz] (Việt Nam: 7). */
    fun tuDuongLich(dd: Int, mm: Int, yy: Int, tz: Double = 7.0): Ngay {
        val soNgay = jd(dd, mm, yy)
        var k = floor((soNgay - 2415021.076998695) / 29.530588853).toInt() + 1
        var dauThang = trangNon(k, tz)
        // Ước lượng theo tháng trung bình có thể vượt một trăng non khi trăng non thật rơi ngay sau nửa đêm.
        while (dauThang > soNgay) dauThang = trangNon(--k, tz)
        var a11 = thang11(yy, tz)
        var b11 = a11
        var nam: Int
        if (a11 >= dauThang) {
            nam = yy
            a11 = thang11(yy - 1, tz)
        } else {
            nam = yy + 1
            b11 = thang11(yy + 1, tz)
        }
        val ngay = (soNgay - dauThang + 1).toInt()
        val lech = floor((dauThang - a11) / 29.0).toInt()
        var nhuan = false
        var thang = lech + 11
        if (b11 - a11 > 365) {
            val thangNhuan = viTriThangNhuan(a11, tz)
            if (lech >= thangNhuan) {
                thang = lech + 10
                if (lech == thangNhuan) nhuan = true
            }
        }
        if (thang > 12) thang -= 12
        if (thang >= 11 && lech < 4) nam -= 1
        return Ngay(ngay, thang, nam, nhuan)
    }

    internal fun jd(dd: Int, mm: Int, yy: Int): Long {
        val a = (14 - mm) / 12
        val y = (yy + 4800 - a).toLong()
        val m = mm + 12 * a - 3
        var jd = dd + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045
        if (jd < 2299161) jd = dd + (153 * m + 2) / 5 + 365 * y + y / 4 - 32083
        return jd
    }

    /** Số ngày của trăng non thứ k kể từ 1/1/1900, theo giờ địa phương. */
    internal fun trangNon(k: Int, tz: Double): Long {
        val t = k / 1236.85
        val t2 = t * t
        val t3 = t2 * t
        val dr = PI / 180
        var jd1 = 2415020.75933 + 29.53058868 * k + 0.0001178 * t2 - 0.000000155 * t3
        jd1 += 0.00033 * sin((166.56 + 132.87 * t - 0.009173 * t2) * dr)
        val m = 359.2242 + 29.10535608 * k - 0.0000333 * t2 - 0.00000347 * t3
        val mpr = 306.0253 + 385.81691806 * k + 0.0107306 * t2 + 0.00001236 * t3
        val f = 21.2964 + 390.67050646 * k - 0.0016528 * t2 - 0.00000239 * t3
        var c1 = (0.1734 - 0.000393 * t) * sin(m * dr) + 0.0021 * sin(2 * dr * m)
        // Từng số hạng với dấu riêng như mã của Hồ Ngọc Đức: gộp hai số hạng vào một "c1 -= a + b" làm đổi dấu số hạng
        // sau, trăng non lệch vài giờ và tháng nhuận 2023 sớm một tháng (Lunar.java).
        c1 = c1 - 0.4068 * sin(mpr * dr) + 0.0161 * sin(dr * 2 * mpr)
        c1 = c1 - 0.0004 * sin(dr * 3 * mpr)
        c1 = c1 + 0.0104 * sin(dr * 2 * f) - 0.0051 * sin(dr * (m + mpr))
        c1 = c1 - 0.0074 * sin(dr * (m - mpr)) + 0.0004 * sin(dr * (2 * f + m))
        c1 = c1 - 0.0004 * sin(dr * (2 * f - m)) - 0.0006 * sin(dr * (2 * f + mpr))
        c1 += 0.0010 * sin(dr * (2 * f - mpr)) + 0.0005 * sin(dr * (2 * mpr + m))
        val deltaT = if (t < -11) 0.001 + 0.000839 * t + 0.0002261 * t2 - 0.00000845 * t3 - 0.000000081 * t * t3
        else -0.000278 + 0.000265 * t + 0.000262 * t2
        return floor(jd1 + c1 - deltaT + 0.5 + tz / 24).toLong()
    }

    /** Mặt trời đang ở cung 30 độ nào (0–11) lúc đầu ngày. */
    internal fun kinhDoMatTroi(jdn: Long, tz: Double): Int {
        val t = (jdn - 2451545.5 - tz / 24) / 36525
        val t2 = t * t
        val dr = PI / 180
        val m = 357.52910 + 35999.05030 * t - 0.0001559 * t2 - 0.00000048 * t * t2
        val l0 = 280.46645 + 36000.76983 * t + 0.0003032 * t2
        var dl = (1.914600 - 0.004817 * t - 0.000014 * t2) * sin(dr * m)
        dl += (0.019993 - 0.000101 * t) * sin(dr * 2 * m) + 0.000290 * sin(dr * 3 * m)
        var l = (l0 + dl) * dr
        l -= PI * 2 * floor(l / (PI * 2))
        return floor(l / PI * 6).toInt()
    }

    /** Ngày đầu tháng 11 âm lịch (tháng có đông chí) của năm dương lịch [yy]. */
    internal fun thang11(yy: Int, tz: Double): Long {
        val off = jd(31, 12, yy) - 2415021
        val k = floor(off / 29.530588853).toInt()
        var nm = trangNon(k, tz)
        if (kinhDoMatTroi(nm, tz) >= 9) nm = trangNon(k - 1, tz)
        return nm
    }

    /** Năm 13 tháng: tháng nhuận là tháng đầu tiên sau tháng 11 không có trung khí. */
    internal fun viTriThangNhuan(a11: Long, tz: Double): Int {
        val k = floor((a11 - 2415021.076998695) / 29.530588853 + 0.5).toInt()
        var i = 1
        var cung = kinhDoMatTroi(trangNon(k + i, tz), tz)
        var truoc: Int
        do {
            truoc = cung
            i++
            cung = kinhDoMatTroi(trangNon(k + i, tz), tz)
        } while (cung != truoc && i < 14)
        return i - 1
    }
}
