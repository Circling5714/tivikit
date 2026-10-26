package vn.tivikit.dulieu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cài đặt nhanh: đọc nhiều khoá một lệnh, nhận ra mức hiệu ứng, ô đang chọn. */
class CaiDatNhanhTest {
    private fun doc(vararg gt: String) = CaiDatNhanh.tuDoc(gt.joinToString("\n@@\n"))

    @Test fun docTheoThuTu() {
        val g = doc("0.5", "0.5", "0.5", "14400000", "null", "0")
        assertEquals("0.5", g[CaiDatNhanh.HIEU_UNG[0]])
        assertEquals("14400000", g[CaiDatNhanh.TU_TAT])
        assertNull(g[CaiDatNhanh.MAN_CHO])
        assertEquals("0", g[CaiDatNhanh.TIENG_BAM])
        // Chữ lạ (lỗi lệnh) coi như chưa đặt.
        assertNull(doc("Error: bad", "1", "1", "", "", "")[CaiDatNhanh.HIEU_UNG[0]])
        assertTrue(CaiDatNhanh.lenhDoc().contains("settings get secure attentive_timeout"))
    }

    @Test fun mucHieuUng() {
        assertEquals(CaiDatNhanh.HieuUng.MAC_DINH, CaiDatNhanh.hieuUng(doc("null", "null", "null", "", "", "")))
        assertEquals(CaiDatNhanh.HieuUng.MAC_DINH, CaiDatNhanh.hieuUng(doc("1.0", "1", "1.0", "", "", "")))
        assertEquals(CaiDatNhanh.HieuUng.NHANH, CaiDatNhanh.hieuUng(doc("0.5", "0.5", "0.5", "", "", "")))
        assertEquals(CaiDatNhanh.HieuUng.TAT, CaiDatNhanh.hieuUng(doc("0", "0.0", "0", "", "", "")))
        assertEquals(CaiDatNhanh.HieuUng.KHAC, CaiDatNhanh.hieuUng(doc("0.5", "1", "1", "", "", "")))
        assertEquals("0.5", CaiDatNhanh.giaTriHieuUng(CaiDatNhanh.HieuUng.NHANH))
        assertNull(CaiDatNhanh.giaTriHieuUng(CaiDatNhanh.HieuUng.MAC_DINH))
    }

    @Test fun oDangChon() {
        val chon = CaiDatNhanh.TU_TAT_CHON
        assertNull(CaiDatNhanh.dangChon(null, chon))
        assertEquals(8 * CaiDatNhanh.GIO, CaiDatNhanh.dangChon("28800000", chon))
        assertEquals(-1L, CaiDatNhanh.dangChon("0", chon))
        assertEquals(CaiDatNhanh.KHONG_KHOP, CaiDatNhanh.dangChon("7200000", chon))
        // Màn hình chờ không có ô "không bao giờ": số âm là giá trị lạ.
        assertEquals(CaiDatNhanh.KHONG_KHOP, CaiDatNhanh.dangChon("-1", CaiDatNhanh.MAN_CHO_CHON))
        assertFalse(CaiDatNhanh.GIA_TRI.matches("1; reboot"))
    }
}
