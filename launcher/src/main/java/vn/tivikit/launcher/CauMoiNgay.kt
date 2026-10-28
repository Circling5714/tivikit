package vn.tivikit.launcher

import java.nio.charset.Charset

/**
 * Câu kinh mỗi ngày dưới âm lịch (28/10/2026): mặc định Kinh Pháp Cú bản thơ lục bát (assets/phap-cu.txt, 423 câu), TiviKit
 * gửi được bộ khác (cau.txt qua CauHinhProvider) hay tắt hẳn ("cau" trong cấu hình). Mỗi ngày một câu, mọi tivi như nhau;
 * mỗi vòng đi hết bộ theo một thứ tự xáo riêng nên chưa hết bộ thì không câu nào lặp lại.
 */
object CauMoiNgay {
    /** Một câu: [so] số câu ("21–22"; bộ không đánh số thì số thứ tự), [dong] các dòng, [chuThich] lời chú thích "(*) …". */
    data class Cau(val so: String, val dong: List<String>, val chuThich: List<String> = emptyList()) {
        /** Số câu kinh trong khối: "137–140" là 4 câu. */
        val soLuong: Int get() = so.split('–').mapNotNull(String::toIntOrNull).let { if (it.size == 2 && it[1] >= it[0]) it[1] - it[0] + 1 else 1 }
    }

    const val TOI_DA_CAU = 5000
    private const val TOI_DA_DONG = 40
    private const val TOI_DA_CHU = 300
    private val TIEU_DE = Regex("""^\((\d{1,4})(?:\s*[-–]\s*(\d{1,4}))?\)$""")
    private val CHU_THICH = Regex("""^\(\*+\)""")
    private val TEN_PHAM = Regex("""^\d{1,3}\.\s+\S""")
    private val DAU_SAO = Regex("""\s*\(\*+\)""")

    /** Chữ của file: UTF-8, hay UTF-16 có BOM (Notepad lưu "Unicode"). */
    fun giaiMa(b: ByteArray): String = when {
        b.size >= 2 && b[0] == 0xFF.toByte() && b[1] == 0xFE.toByte() -> String(b, 2, b.size - 2, Charsets.UTF_16LE)
        b.size >= 2 && b[0] == 0xFE.toByte() && b[1] == 0xFF.toByte() -> String(b, 2, b.size - 2, Charsets.UTF_16BE)
        else -> String(b, Charset.forName("UTF-8")).removePrefix("﻿")
    }

    /**
     * Tách bộ câu: có các dòng "(n)" / "(n-m)" như file Kinh Pháp Cú thì mỗi khối là một câu (bỏ tên phẩm in hoa, giữ lời
     * chú "(*) …" riêng); không thì mỗi đoạn cách nhau bằng dòng trống là một câu, file không có dòng trống thì mỗi dòng.
     */
    fun tach(chu: String): List<Cau> {
        val dong = chu.removePrefix("﻿").replace("\r\n", "\n").replace('\r', '\n').replace('\t', ' ').split('\n').map { it.trim() }
        val ds = if (dong.count { TIEU_DE.matches(it) } >= 2) theoSo(dong) else theoDoan(dong)
        return ds.take(TOI_DA_CAU)
    }

    private fun cau(so: String, dong: List<String>, chuThich: List<String> = emptyList()) =
        Cau(so, dong.take(TOI_DA_DONG).map { it.take(TOI_DA_CHU) }, chuThich.take(4).map { it.take(TOI_DA_CHU * 2) })

    private fun theoSo(dong: List<String>): List<Cau> {
        val ds = mutableListOf<Cau>()
        var so: String? = null
        val cauNay = mutableListOf<String>()
        val chuThich = mutableListOf<String>()
        // Đang đọc: câu, lời chú (tới dòng trống), hay tên phẩm ("2. PHẨM" / "KHÔNG BUÔNG LUNG", tới dòng trống).
        var trong = DOC_CAU
        fun xong() {
            val s = so
            if (s != null && cauNay.isNotEmpty()) ds += cau(s, cauNay, chuThich)
            cauNay.clear(); chuThich.clear()
        }
        for (d in dong) {
            val m = TIEU_DE.matchEntire(d)
            when {
                m != null -> {
                    xong()
                    so = m.groupValues[1] + m.groupValues[2].takeIf { it.isNotEmpty() }?.let { "–$it" }.orEmpty()
                    trong = DOC_CAU
                }
                d.isEmpty() -> trong = DOC_CAU
                CHU_THICH.containsMatchIn(d) -> { chuThich += d; trong = DOC_CHU_THICH }
                trong == DOC_CHU_THICH -> chuThich[chuThich.lastIndex] = chuThich.last() + " " + d
                TEN_PHAM.containsMatchIn(d) && inHoa(d) -> trong = DOC_TEN_PHAM
                trong == DOC_TEN_PHAM && inHoa(d) -> {}
                else -> { cauNay += d; trong = DOC_CAU }
            }
        }
        xong()
        return ds
    }

    private fun theoDoan(dong: List<String>): List<Cau> {
        val doan = mutableListOf<MutableList<String>>()
        var moi = true
        for (d in dong) {
            if (d.isEmpty()) { moi = true; continue }
            if (moi) { doan += mutableListOf<String>(); moi = false }
            doan[doan.lastIndex].add(d)
        }
        val ds = if (doan.size == 1) doan[0].map { listOf(it) } else doan
        return ds.mapIndexed { i, d -> cau("${i + 1}", d) }
    }

    private fun inHoa(s: String) = s.any(Char::isLetter) && s == s.uppercase()

    /** Các dòng để hiện ở thẻ nhỏ: bỏ dấu chú thích "(*)". */
    fun boDauSao(s: String) = DAU_SAO.replace(s, "")

    /** Câu của ngày [ngay] (LocalDate.toEpochDay) trong bộ [n] câu. */
    fun chiSo(ngay: Long, n: Int): Int {
        require(n > 0)
        val vong = Math.floorDiv(ngay, n.toLong())
        return thuTu(n, vong)[Math.floorMod(ngay, n.toLong()).toInt()]
    }

    private fun thuTu(n: Int, vong: Long): IntArray {
        val a = xao(n, vong)
        // Câu đầu vòng mới trùng câu cuối vòng trước (hai ngày liền một câu): đổi chỗ với câu kế.
        if (n >= 3 && a[0] == xao(n, vong - 1)[n - 1]) { val t = a[0]; a[0] = a[1]; a[1] = t }
        return a
    }

    /** Thứ tự xáo của vòng [vong] (Fisher–Yates, số ngẫu nhiên SplitMix64 gieo theo vòng): như nhau trên mọi tivi, mọi bản. */
    private fun xao(n: Int, vong: Long): IntArray {
        val a = IntArray(n) { it }
        var s = vong * -0x61C8864680B583EBL + n
        for (i in n - 1 downTo 1) {
            s += -0x61C8864680B583EBL
            var z = s
            z = (z xor (z ushr 30)) * -0x40A7B892E31B1A47L
            z = (z xor (z ushr 27)) * -0x6B2FB644ECCEEE15L
            z = z xor (z ushr 31)
            val j = Math.floorMod(z, (i + 1).toLong()).toInt()
            val t = a[i]; a[i] = a[j]; a[j] = t
        }
        return a
    }

    private const val DOC_CAU = 0
    private const val DOC_CHU_THICH = 1
    private const val DOC_TEN_PHAM = 2
}
