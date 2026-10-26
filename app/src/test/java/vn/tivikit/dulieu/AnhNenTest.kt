package vn.tivikit.dulieu

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** Canh ảnh nền: khung cắt 16:9 trên ảnh gốc, kéo, chụm phóng, luôn nằm trọn trong ảnh. */
class AnhNenTest {
    private val E = 0.01f

    @Test fun macDinh_catGiuaVuaKin() {
        // Ảnh ngang 3:2: rộng hết, cắt bớt trên dưới.
        assertArrayEquals(floatArrayOf(0f, 156.25f, 3000f, 1687.5f), AnhNen.vung(3000, 2000, AnhNen.Khung()), E)
        // Ảnh dọc: rộng hết, chọn dải giữa.
        assertArrayEquals(floatArrayOf(0f, 1078.125f, 1500f, 843.75f), AnhNen.vung(1500, 3000, AnhNen.Khung()), E)
        // Ảnh đúng 16:9: lấy trọn.
        assertArrayEquals(floatArrayOf(0f, 0f, 1920f, 1080f), AnhNen.vung(1920, 1080, AnhNen.Khung()), E)
    }

    @Test fun kepTrongAnh() {
        assertArrayEquals(floatArrayOf(0f, 0f, 3000f, 1687.5f), AnhNen.vung(3000, 2000, AnhNen.Khung(x = 0f, y = 0f)), E)
        val k = AnhNen.kep(3000, 2000, AnhNen.Khung(zoom = 9f, x = 2f, y = -1f))
        assertEquals(AnhNen.ZOOM_MAX, k.zoom, E)
        val v = AnhNen.vung(3000, 2000, k)
        assertEquals(3000f, v[0] + v[2], E); assertEquals(0f, v[1], E)
    }

    @Test fun keo_anhTheoNgonTay() {
        // Khung xem rộng 300: 1 điểm trên khung xem = 10 điểm ảnh gốc. Kéo xuống 10 → khung cắt đi lên 100.
        val k = AnhNen.keo(3000, 2000, AnhNen.Khung(), 0f, 10f, 300f)
        assertEquals(56.25f, AnhNen.vung(3000, 2000, k)[1], E)
        // Kéo quá tay thì dừng ở mép ảnh.
        assertEquals(0f, AnhNen.vung(3000, 2000, AnhNen.keo(3000, 2000, AnhNen.Khung(), 0f, 500f, 300f))[1], E)
        // Rộng đã kín ảnh: kéo ngang không đổi.
        assertEquals(0f, AnhNen.vung(3000, 2000, AnhNen.keo(3000, 2000, AnhNen.Khung(), 40f, 0f, 300f))[0], E)
    }

    @Test fun phong_diemDuoiNgonTayDungYen() {
        // Phóng 2 lần quanh giữa khung xem: khung cắt nhỏ một nửa, cùng tâm.
        val giua = AnhNen.vung(3000, 2000, AnhNen.phong(3000, 2000, AnhNen.Khung(), 2f, 150f, 84.375f, 300f))
        assertArrayEquals(floatArrayOf(750f, 578.125f, 1500f, 843.75f), giua, E)
        // Phóng quanh góc trên trái: góc đó đứng yên.
        val goc = AnhNen.vung(3000, 2000, AnhNen.phong(3000, 2000, AnhNen.Khung(), 2f, 0f, 0f, 300f))
        assertEquals(0f, goc[0], E); assertEquals(156.25f, goc[1], E); assertEquals(1500f, goc[2], E)
        // Không phóng quá 3 lần, không thu nhỏ hơn vừa kín.
        assertEquals(AnhNen.ZOOM_MAX, AnhNen.phong(3000, 2000, AnhNen.Khung(), 10f, 0f, 0f, 300f).zoom, E)
        assertEquals(1f, AnhNen.phong(3000, 2000, AnhNen.Khung(zoom = 2f), 0.1f, 0f, 0f, 300f).zoom, E)
    }
}
