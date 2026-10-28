package vn.tivikit.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CauMoiNgayTest {
    private val phapCu by lazy { CauMoiNgay.tach(CauMoiNgay.giaiMa(File("src/main/assets/phap-cu.txt").readBytes())) }

    @Test
    fun kinhPhapCuDuCau() {
        // 423 câu: 386 khối một câu và 16 khối gộp như (21-22), (137-140), (188-192).
        assertEquals(402, phapCu.size)
        assertEquals(423, phapCu.sumOf { it.soLuong })
        assertEquals("1", phapCu.first().so)
        assertEquals("Việc làm của bản thân ta", phapCu.first().dong.first())
        assertEquals(6, phapCu.first().dong.size)
        assertEquals("423", phapCu.last().so)
        assertEquals(4, phapCu.single { it.so == "137–140" }.soLuong)
    }

    @Test
    fun boTenPhamGiuChuThich() {
        // Câu 20 là câu cuối phẩm 1: tên phẩm "2. PHẨM / KHÔNG BUÔNG LUNG" ngay sau nó không thuộc câu.
        val c20 = phapCu.single { it.so == "20" }
        assertEquals(6, c20.dong.size)
        assertTrue(phapCu.none { c -> c.dong.any { "PHẨM" in it } })
        val c21 = phapCu.single { it.so == "21–22" }
        assertEquals(10, c21.dong.size)
        assertEquals(1, c21.chuThich.size)
        assertTrue(c21.chuThich[0].startsWith("(*) Buông lung:"))
        assertTrue(c21.chuThich[0].endsWith("đồng nghĩa với chữ phóng dật."))
        assertEquals("Người chuyên niệm, chẳng buông lung", CauMoiNgay.boDauSao(c21.dong[0]))
    }

    @Test
    fun boTuViet() {
        val doan = CauMoiNgay.tach("Câu một\ndòng hai\n\n\nCâu hai\n")
        assertEquals(listOf("1", "2"), doan.map { it.so })
        assertEquals(listOf("Câu một", "dòng hai"), doan[0].dong)
        // Không có dòng trống: mỗi dòng một câu.
        assertEquals(3, CauMoiNgay.tach("A\r\nB\r\nC").size)
        assertTrue(CauMoiNgay.tach("  \n\n").isEmpty())
    }

    @Test
    fun giaiMaUtf16VaBom() {
        val chu = "Ý dẫn đầu các pháp"
        assertEquals(chu, CauMoiNgay.giaiMa(byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + chu.toByteArray(Charsets.UTF_16LE)))
        assertEquals(chu, CauMoiNgay.giaiMa(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + chu.toByteArray()))
    }

    @Test
    fun moiVongDiHetBoKhongLapLien() {
        for (n in listOf(1, 2, 3, 7, 402)) {
            for (vong in 0L..5L) {
                val ngay = (vong * n until (vong + 1) * n).map { CauMoiNgay.chiSo(it, n) }
                assertEquals("n=$n vong=$vong", (0 until n).toList(), ngay.sorted())
            }
            if (n >= 3) for (d in 1L..40L * n) assertNotEquals("n=$n ngay=$d", CauMoiNgay.chiSo(d - 1, n), CauMoiNgay.chiSo(d, n))
        }
        // Cố định: cùng ngày luôn ra cùng câu, ngày trước 1970 cũng được.
        assertEquals(CauMoiNgay.chiSo(20_754, 402), CauMoiNgay.chiSo(20_754, 402))
        assertTrue(CauMoiNgay.chiSo(-5, 402) in 0 until 402)
    }
}
