package vn.tivikit.teleremote

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.tivikit.teleremote.XuLyLenh.Nguoi

class XuLyLenhTest {
    private val chu = NguoiDung("@CaoMinh", 0, VaiTro.CHU)
    private val con = NguoiDung("@be_na", 0, VaiTro.THANH_VIEN)
    private val ch = CauHinh(nguoiDung = listOf(chu, con))

    private fun tin(text: String, tu: String = """{"id":111,"username":"caominh","first_name":"Minh"}""", loai: String = "private", luc: Long = 1000) =
        JSONObject("""{"update_id":7,"message":{"message_id":5,"date":$luc,"from":$tu,"chat":{"id":111,"type":"$loai"},"text":${JSONObject.quote(text)}}}""")

    private fun nut(du: String) = JSONObject("""{"update_id":8,"callback_query":{"id":"q1","data":"$du","from":{"id":222,"first_name":"Na"},
        "message":{"message_id":9,"date":10,"chat":{"id":222,"type":"private"}}}}""")

    @Test
    fun docTinVaNut() {
        val d = XuLyLenh.docCapNhat(tin("/start"))!!
        assertEquals(Nguoi(111, "@caominh", "Minh"), d.nguoi)
        assertEquals("/start", d.chu)
        assertTrue(d.rieng)
        assertNull(d.nut)
        val n = XuLyLenh.docCapNhat(nut("k:19"))!!
        assertEquals("k:19", n.nut)
        assertEquals("q1", n.idNut)
        assertEquals(9L, n.idTin)
        assertEquals(222L, n.chat)
        assertEquals("", n.nguoi.ten)
        // Tin trong nhóm: không phải chat riêng.
        assertFalse(XuLyLenh.docCapNhat(tin("/start", loai = "group"))!!.rieng)
        assertNull(XuLyLenh.docCapNhat(JSONObject("""{"update_id":1,"edited_message":{}}""")))
    }

    @Test
    fun timTheoUsernameRoiTheoId() {
        val minh = Nguoi(111, "@caominh", "Minh")
        // Lần đầu: khớp @username (không phân biệt hoa thường), ghi lại ID.
        assertEquals(chu, XuLyLenh.tim(ch, minh))
        val sau = XuLyLenh.ghiNguoi(ch, chu, minh)!!
        assertEquals(NguoiDung("@caominh", 111, VaiTro.CHU, "Minh"), sau.nguoiDung[0])
        // Đã có ID: đổi username vẫn nhận, người khác lấy username cũ thì không.
        assertEquals(VaiTro.CHU, XuLyLenh.tim(sau, Nguoi(111, "@minh_moi", ""))?.vaiTro)
        assertNull(XuLyLenh.tim(sau, Nguoi(999, "@caominh", "Ai đó")))
        // Không đổi gì thì không ghi.
        assertNull(XuLyLenh.ghiNguoi(sau, sau.nguoiDung[0], minh))
        // Người không đặt username: chỉ vào được khi chủ duyệt theo ID.
        assertNull(XuLyLenh.tim(ch, Nguoi(333, "", "Ông")))
        assertEquals(VaiTro.THANH_VIEN, XuLyLenh.tim(ch.copy(nguoiDung = ch.nguoiDung + NguoiDung("", 333, VaiTro.THANH_VIEN)), Nguoi(333, "", "Ông"))?.vaiTro)
    }

    @Test
    fun chuThanhViec() {
        assertEquals(XuLyLenh.Menu, XuLyLenh.viecTuChu("/start"))
        assertEquals(XuLyLenh.Menu, XuLyLenh.viecTuChu("/start@TiviPhongKhach_bot"))
        assertEquals(XuLyLenh.DieuKhien, XuLyLenh.viecTuChu("🎮 Điều khiển"))
        assertEquals(XuLyLenh.DieuKhien, XuLyLenh.viecTuChu("🎮 Remote"))
        assertEquals(XuLyLenh.Kenh, XuLyLenh.viecTuChu("📺 Kênh nhanh"))
        assertEquals(XuLyLenh.YouTube, XuLyLenh.viecTuChu("▶️ YouTube"))
        assertEquals(XuLyLenh.Tivi, XuLyLenh.viecTuChu("/tivi"))
        assertEquals(XuLyLenh.HoiTat, XuLyLenh.viecTuChu("📴 Tắt tivi"))
        assertEquals(XuLyLenh.KhongRo, XuLyLenh.viecTuChu("xin chào"))
        assertEquals(XuLyLenh.KhongRo, XuLyLenh.viecTuChu(""))
    }

    @Test
    fun nutThanhViec() {
        assertEquals(XuLyLenh.Phim(23), XuLyLenh.viecTuNut("k:23"))
        assertEquals(XuLyLenh.MoKenh(3), XuLyLenh.viecTuNut("o:3"))
        assertEquals(XuLyLenh.Tat, XuLyLenh.viecTuNut("t:ok"))
        assertEquals(XuLyLenh.ThoiTat, XuLyLenh.viecTuNut("t:x"))
        assertEquals(XuLyLenh.LamMoi, XuLyLenh.viecTuNut("r"))
        // Phím ngoài bảng (nút nguồn, phím lạ) và ô ngoài 1..6: dữ liệu giả, bỏ.
        assertNull(XuLyLenh.viecTuNut("k:26"))
        assertNull(XuLyLenh.viecTuNut("k:abc"))
        assertNull(XuLyLenh.viecTuNut("o:7"))
        assertNull(XuLyLenh.viecTuNut("xyz"))
        assertTrue(XuLyLenh.chiChu(XuLyLenh.Tat))
        assertTrue(XuLyLenh.chiChu(XuLyLenh.HoiTat))
        assertFalse(XuLyLenh.chiChu(XuLyLenh.Phim(23)))
    }

    @Test
    fun tinCuThiBo() {
        assertTrue(XuLyLenh.cu(XuLyLenh.docCapNhat(tin("/tat", luc = 1000))!!, 1000 + XuLyLenh.CU_QUA + 1))
        assertFalse(XuLyLenh.cu(XuLyLenh.docCapNhat(tin("/tat", luc = 1000))!!, 1030))
        // Nút bấm: tin chứa nút có thể cũ mà vừa bấm.
        assertFalse(XuLyLenh.cu(XuLyLenh.docCapNhat(nut("k:19"))!!, 999_999))
    }

    @Test
    fun banPhim() {
        val chuPhim = XuLyLenh.banPhimChinh(VaiTro.CHU, en = false)
        assertEquals(3, chuPhim.getJSONArray("keyboard").length())
        assertEquals("📴 Tắt tivi", chuPhim.getJSONArray("keyboard").getJSONArray(2).getJSONObject(0).getString("text"))
        assertEquals(2, XuLyLenh.banPhimChinh(VaiTro.THANH_VIEN, en = true).getJSONArray("keyboard").length())
        // Mỗi nút của bàn phím chính đọc lại ra đúng việc.
        val a = XuLyLenh.banPhimChinh(VaiTro.CHU, en = true).getJSONArray("keyboard")
        val viec = (0 until a.length()).flatMap { i -> (0 until a.getJSONArray(i).length()).map { XuLyLenh.viecTuChu(a.getJSONArray(i).getJSONObject(it).getString("text")) } }
        assertEquals(listOf(XuLyLenh.DieuKhien, XuLyLenh.Kenh, XuLyLenh.YouTube, XuLyLenh.Tivi, XuLyLenh.HoiTat), viec)
        // Bảng điều khiển: 4 hàng ba phím + hàng Cập nhật; nút nào cũng đọc lại được.
        val b = XuLyLenh.bangDieuKhien(en = false).getJSONArray("inline_keyboard")
        assertEquals(5, b.length())
        for (i in 0 until b.length()) for (k in 0 until b.getJSONArray(i).length()) {
            val du = b.getJSONArray(i).getJSONObject(k).getString("callback_data")
            assertTrue(du, XuLyLenh.viecTuNut(du) != null && du.length <= 64)
        }
        assertEquals("o:2", XuLyLenh.bangKenh(listOf(1 to "VTV1", 2 to "VTV3")).getJSONArray("inline_keyboard").getJSONArray(0).getJSONObject(1).getString("callback_data"))
    }

    @Test
    fun goiKenh() {
        assertEquals("vn.tivikit.kenh", XuLyLenh.goiKenh(1))
        assertEquals("vn.tivikit.kenh4", XuLyLenh.goiKenh(4))
    }

    @Test
    fun tenKenhTuO() {
        // Đúng chữ ô kênh trên TX3 trả (lần đầu lấy theo "}" cuối thì dính dấu "}" của Bundle, nút ra "Kênh nhanh 1").
        assertEquals("VTV1", XuLyLenh.tenKenh("Result: Bundle[{json={\"ten\":\"VTV1\",\"so\":\"001\"}}]\n"))
        assertEquals("VTV3", XuLyLenh.tenKenh("""Result: Bundle[{json={"ten":"VTV3","app":"fptplay","ma":"vtv3-hd"}}]"""))
        assertNull(XuLyLenh.tenKenh("Error while accessing provider:vn.tivikit.kenh"))
        assertNull(XuLyLenh.tenKenh("""Result: Bundle[{json={"so":"001"}}]"""))
    }
}
