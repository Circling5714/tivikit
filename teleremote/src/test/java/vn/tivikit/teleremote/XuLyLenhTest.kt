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
        assertEquals(XuLyLenh.UngDung(0), XuLyLenh.viecTuChu("📱 Ứng dụng"))
        assertEquals(XuLyLenh.HenGio, XuLyLenh.viecTuChu("⏰ Timer"))
        assertEquals(XuLyLenh.Khoa, XuLyLenh.viecTuChu("🔒 Khoá trẻ em"))
        assertEquals(XuLyLenh.LamMoiTv, XuLyLenh.viecTuChu("🧹 Làm mới"))
        assertEquals(XuLyLenh.ChupManHinh, XuLyLenh.viecTuChu("/manhinh"))
        assertEquals(XuLyLenh.GoChu, XuLyLenh.viecTuChu("/go"))
        // Chữ khác là câu nhắn như nói; có link thì mở link; lệnh / lạ và tin rỗng thì không rõ.
        assertEquals(XuLyLenh.Noi("mở nhạc trịnh"), XuLyLenh.viecTuChu("  mở nhạc trịnh "))
        assertEquals(XuLyLenh.Link("https://youtu.be/dQw4w9WgXcQ"), XuLyLenh.viecTuChu("xem này https://youtu.be/dQw4w9WgXcQ!"))
        assertEquals(XuLyLenh.KhongRo, XuLyLenh.viecTuChu("/abc"))
        assertEquals(XuLyLenh.KhongRo, XuLyLenh.viecTuChu(""))
    }

    @Test
    fun nutThanhViec() {
        assertEquals(XuLyLenh.Phim(23), XuLyLenh.viecTuNut("k:23"))
        assertEquals(XuLyLenh.MoKenh(3), XuLyLenh.viecTuNut("o:3"))
        assertEquals(XuLyLenh.Tat, XuLyLenh.viecTuNut("t:ok"))
        assertEquals(XuLyLenh.ThoiTat, XuLyLenh.viecTuNut("t:x"))
        assertEquals(XuLyLenh.CapNhat, XuLyLenh.viecTuNut("r"))
        assertEquals(XuLyLenh.GoChu, XuLyLenh.viecTuNut("g"))
        assertEquals(XuLyLenh.GoTiep, XuLyLenh.viecTuNut("g+"))
        assertEquals(XuLyLenh.KhoiDongLai, XuLyLenh.viecTuNut("t:rb"))
        assertEquals(XuLyLenh.UngDung(2), XuLyLenh.viecTuNut("ap:2"))
        assertEquals(XuLyLenh.MoUngDung("com.netflix.ninja"), XuLyLenh.viecTuNut("a:com.netflix.ninja"))
        assertEquals(XuLyLenh.TatSau(30), XuLyLenh.viecTuNut("h:t:30"))
        assertEquals(XuLyLenh.HuyHen("tat-1793422740000"), XuLyLenh.viecTuNut("h:x:tat-1793422740000"))
        assertEquals(XuLyLenh.MoTam(60), XuLyLenh.viecTuNut("kh:m:60"))
        assertEquals(XuLyLenh.KhoaNgay, XuLyLenh.viecTuNut("kh:k"))
        // Gói lạ (có lệnh shell), phút ngoài danh sách nút: bỏ.
        assertNull(XuLyLenh.viecTuNut("a:com.x;reboot"))
        assertNull(XuLyLenh.viecTuNut("h:t:7"))
        assertNull(XuLyLenh.viecTuNut("h:x:tat-1'; reboot"))
        assertNull(XuLyLenh.viecTuNut("kh:m:999"))
        // Enter sau khi gõ chữ: không nằm trên bảng điều khiển nhưng bấm được.
        assertEquals(XuLyLenh.Phim(66), XuLyLenh.viecTuNut("k:66"))
        // Phím ngoài bảng (nút nguồn, phím lạ) và ô ngoài 1..6: dữ liệu giả, bỏ.
        assertNull(XuLyLenh.viecTuNut("k:26"))
        assertNull(XuLyLenh.viecTuNut("k:abc"))
        assertNull(XuLyLenh.viecTuNut("o:7"))
        assertNull(XuLyLenh.viecTuNut("xyz"))
        assertTrue(XuLyLenh.chiChu(XuLyLenh.Tat))
        assertTrue(XuLyLenh.chiChu(XuLyLenh.HoiTat))
        assertFalse(XuLyLenh.chiChu(XuLyLenh.Phim(23)))
        listOf(XuLyLenh.KhoiDongLai, XuLyLenh.HenGio, XuLyLenh.TatSau(30), XuLyLenh.Khoa, XuLyLenh.MoTam(30), XuLyLenh.ChupManHinh,
            XuLyLenh.LamMoiTv, XuLyLenh.DungApp).forEach { assertTrue(it.toString(), XuLyLenh.chiChu(it)) }
        listOf(XuLyLenh.GoChu, XuLyLenh.UngDung(0), XuLyLenh.MoUngDung("a.b"), XuLyLenh.Noi("x"), XuLyLenh.Link("https://a.b"), XuLyLenh.ChieuTep)
            .forEach { assertFalse(it.toString(), XuLyLenh.chiChu(it)) }
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
        fun nut(b: JSONObject) = b.getJSONArray("keyboard").let { a ->
            (0 until a.length()).flatMap { i -> (0 until a.getJSONArray(i).length()).map { a.getJSONArray(i).getJSONObject(it).getString("text") } }
        }
        // Chủ, tivi chưa có Hẹn giờ / Khoá trẻ em, chưa bật chụp: 8 nút, hai cột, Tắt tivi cuối.
        val chuPhim = XuLyLenh.banPhimChinh(VaiTro.CHU, en = false)
        assertEquals(4, chuPhim.getJSONArray("keyboard").length())
        assertEquals("📴 Tắt tivi", nut(chuPhim).last())
        assertEquals(listOf("🎮 Điều khiển", "📺 Kênh nhanh", "▶️ YouTube", "📱 Ứng dụng", "📢 Nhắn tivi", "ℹ️ Tivi", "⚙️ Thêm"),
            nut(XuLyLenh.banPhimChinh(VaiTro.THANH_VIEN, en = false)))
        // Đủ tính năng: mỗi nút của bàn phím chính đọc lại ra đúng việc (cả tiếng Anh).
        val du = XuLyLenh.Co(henGio = true, khoa = true, manHinh = true)
        assertEquals(6, XuLyLenh.banPhimChinh(VaiTro.CHU, en = true, co = du).getJSONArray("keyboard").length())
        assertEquals(listOf(XuLyLenh.DieuKhien, XuLyLenh.Kenh, XuLyLenh.YouTube, XuLyLenh.UngDung(0), XuLyLenh.HenGio, XuLyLenh.Khoa,
            XuLyLenh.NhanTivi(), XuLyLenh.Tivi, XuLyLenh.ChupManHinh, XuLyLenh.Them, XuLyLenh.HoiTat),
            nut(XuLyLenh.banPhimChinh(VaiTro.CHU, en = true, co = du)).map(XuLyLenh::viecTuChu))
        // Nút cũ còn trong Telegram (bàn phím trước): 🧹 Làm mới vẫn hiểu.
        assertEquals(XuLyLenh.LamMoiTv, XuLyLenh.viecTuChu("🧹 Làm mới"))
        // Bảng điều khiển: 4 hàng ba phím + hàng Cập nhật; nút nào cũng đọc lại được.
        val b = XuLyLenh.bangDieuKhien(en = false).getJSONArray("inline_keyboard")
        assertEquals(5, b.length())
        for (i in 0 until b.length()) for (k in 0 until b.getJSONArray(i).length()) {
            val du = b.getJSONArray(i).getJSONObject(k).getString("callback_data")
            assertTrue(du, XuLyLenh.viecTuNut(du) != null && du.length <= 64)
        }
        assertEquals("o:2", XuLyLenh.bangKenh(listOf(1 to "VTV1", 2 to "VTV3")).getJSONArray("inline_keyboard").getJSONArray(0).getJSONObject(1).getString("callback_data"))
        // Mọi nút trong các bảng khác cũng đọc lại được và vừa giới hạn 64 byte của Telegram.
        val ch2 = CauHinh(nguoiDung = listOf(NguoiDung("@caominh", 111, VaiTro.CHU), NguoiDung("@be_na", 0, VaiTro.THANH_VIEN)))
        listOf(XuLyLenh.bangThem(true, false, 0, 1), XuLyLenh.bangThem(true, true, 99, 1), XuLyLenh.bangNguoi(ch2, listOf(XinDung(333, "", "Ông", 1)), false),
            XuLyLenh.bangMotNguoi(ch2.nguoiDung[1], false), XuLyLenh.bangXinDung(333, true), XuLyLenh.nutBatManHinh(false),
            XuLyLenh.sauKhiChup(false), XuLyLenh.nutDatNen(true), XuLyLenh.nutBoNen(false), XuLyLenh.bangCast(7, false),
            XuLyLenh.bangTatCaKenh(1), XuLyLenh.bangKenh(listOf(1 to "VTV1"), tatCa = true),
            XuLyLenh.sauKhiGo(en = false), XuLyLenh.bangHenGio(listOf("tat-1793422740000" to "22:30 tắt tivi"), en = false), XuLyLenh.bangKhoa(en = true, coMa = true),
            XuLyLenh.bangLamMoi(en = false, ganDay = true), XuLyLenh.hoiTat(en = false),
            XuLyLenh.bangUngDung((1..30).map { "com.app.so$it" to "App $it" }, 1, en = false, chu = true)).forEach { bp ->
            val h = bp.getJSONArray("inline_keyboard")
            for (i in 0 until h.length()) for (k in 0 until h.getJSONArray(i).length()) {
                val d = h.getJSONArray(i).getJSONObject(k).getString("callback_data")
                assertTrue(d, XuLyLenh.viecTuNut(d) != null && d.toByteArray().size <= 64)
            }
        }
        // Chưa có mã bố mẹ thì không có nút Khoá ngay.
        assertEquals(2, XuLyLenh.bangKhoa(en = false, coMa = false).getJSONArray("inline_keyboard").length())
    }

    @Test
    fun trangUngDung() {
        val ds = (1..30).map { "com.app.so$it" to "App $it" }
        assertEquals(3, XuLyLenh.soTrang(30))
        assertEquals(1, XuLyLenh.soTrang(0))
        val h = XuLyLenh.bangUngDung(ds, 1, en = false, chu = false).getJSONArray("inline_keyboard")
        // 12 app (6 hàng hai app) rồi hàng chuyển trang ◀️ 2/3 ▶️.
        assertEquals(7, h.length())
        assertEquals("a:com.app.so13", h.getJSONArray(0).getJSONObject(0).getString("callback_data"))
        assertEquals(listOf("ap:0", "ap:1", "ap:2"), (0 until 3).map { h.getJSONArray(6).getJSONObject(it).getString("callback_data") })
        // Trang cuối: chỉ nút lùi; chủ có thêm nút buộc dừng.
        val cuoi = XuLyLenh.bangUngDung(ds, 9, en = false, chu = true).getJSONArray("inline_keyboard")
        assertEquals("as", cuoi.getJSONArray(cuoi.length() - 1).getJSONObject(0).getString("callback_data"))
        assertEquals(2, cuoi.getJSONArray(cuoi.length() - 2).length())
    }

    @Test
    fun anhVideoNguoiDungGui() {
        val anh = JSONObject("""{"update_id":9,"message":{"message_id":12,"date":5,"from":{"id":111,"first_name":"Minh"},"chat":{"id":111,"type":"private"},
            "photo":[{"file_id":"nho","file_size":900},{"file_id":"vua","file_size":90000},{"file_id":"to","file_size":25000000}]}}""")
        val d = XuLyLenh.docCapNhat(anh)!!
        // Cỡ lớn nhất bot tải được (bỏ cỡ quá 20 MB).
        assertEquals(XuLyLenh.Tep("vua", false, 90000), d.tep)
        assertEquals(12L, d.idTin)
        assertEquals(XuLyLenh.ChieuTep, XuLyLenh.viecTuTin(d))
        val tep = JSONObject("""{"update_id":10,"message":{"message_id":13,"date":5,"from":{"id":111},"chat":{"id":111,"type":"private"},
            "document":{"file_id":"v1","mime_type":"video/mp4","file_size":5000}}}""")
        assertEquals(XuLyLenh.Tep("v1", true, 5000), XuLyLenh.docCapNhat(tep)!!.tep)
        val pdf = JSONObject("""{"update_id":11,"message":{"message_id":14,"date":5,"from":{"id":111},"chat":{"id":111,"type":"private"},
            "document":{"file_id":"p","mime_type":"application/pdf"}}}""")
        assertNull(XuLyLenh.docCapNhat(pdf)!!.tep)
        assertEquals(XuLyLenh.KhongRo, XuLyLenh.viecTuTin(XuLyLenh.docCapNhat(pdf)!!))
    }

    @Test
    fun link() {
        assertEquals("dQw4w9WgXcQ", XuLyLenh.maYouTube("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=42s"))
        assertEquals("dQw4w9WgXcQ", XuLyLenh.maYouTube("https://youtu.be/dQw4w9WgXcQ?si=abc"))
        assertEquals("dQw4w9WgXcQ", XuLyLenh.maYouTube("https://m.youtube.com/shorts/dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", XuLyLenh.maYouTube("https://music.youtube.com/watch?list=RD1&v=dQw4w9WgXcQ"))
        assertNull(XuLyLenh.maYouTube("https://www.youtube.com/playlist?list=PL123"))
        assertTrue(XuLyLenh.laYouTube("https://www.youtube.com/playlist?list=PL123"))
        assertFalse(XuLyLenh.laYouTube("https://notyoutube.com/watch?v=dQw4w9WgXcQ"))
        assertTrue(XuLyLenh.laFpt("https://fptplay.vn/xem-truyen-hinh/vtv1-hd"))
        assertFalse(XuLyLenh.laFpt("https://fptplay.vn.evil.com/x"))
        assertEquals("fptplay.vn", XuLyLenh.tenMien("https://FPTPlay.vn/xem-video/abc?x=1"))
        assertEquals("https://vnexpress.net/a.html", XuLyLenh.timLink("đọc (https://vnexpress.net/a.html)."))
        assertNull(XuLyLenh.timLink("không có link"))
    }

    @Test
    fun appTheoTen() {
        val ds = listOf("com.netflix.ninja" to "Netflix", "net.fptplay.ottbox" to "FPT Play", "com.android.tv.settings" to "Cài đặt",
            "org.videolan.vlc" to "VLC")
        assertEquals("com.netflix.ninja", XuLyLenh.timApp("mở Netflix", ds))
        assertEquals("com.netflix.ninja", XuLyLenh.timApp("NETFLIX", ds))
        assertEquals("net.fptplay.ottbox", XuLyLenh.timApp("mở app fpt play", ds))
        assertEquals("com.android.tv.settings", XuLyLenh.timApp("vào cài đặt", ds))
        assertEquals("org.videolan.vlc", XuLyLenh.timApp("open vlc", ds))
        assertNull(XuLyLenh.timApp("mở nhạc trịnh", ds))
        assertNull(XuLyLenh.timApp("mở", ds))
    }

    @Test
    fun maMoKhoa() {
        assertEquals("123456", XuLyLenh.maKhoa("123 456"))
        assertEquals("12345678", XuLyLenh.maKhoa("1234-5678"))
        assertNull(XuLyLenh.maKhoa("12345"))
        assertNull(XuLyLenh.maKhoa("mã 123456"))
    }

    @Test
    fun ketQuaContentCall() {
        val kq = "Result: Bundle[{viec=yt-tim|nhạc trịnh, bolero, app=org.smarttube.stable, ok=1}]"
        assertEquals("yt-tim|nhạc trịnh, bolero", XuLyLenh.giaTri(kq, "viec"))
        assertEquals("org.smarttube.stable", XuLyLenh.giaTri(kq, "app"))
        assertEquals("1", XuLyLenh.giaTri(kq, "ok"))
        assertEquals("", XuLyLenh.giaTri(kq, "loi"))
        assertEquals("1793422740000", XuLyLenh.giaTri("Result: Bundle[{id=tat-1793422740000, ok=1, luc=1793422740000}]", "luc"))
        assertEquals(true, XuLyLenh.jsonTrong("Result: Bundle[{json={\"ok\":true,\"phien\":\"ab\"}}]")?.optBoolean("ok"))
        // Tham số gửi qua ADB thuần ASCII, không có nháy đơn, đọc lại ra đúng chữ.
        val ts = XuLyLenh.thamSo(JSONObject().put("cau", "mở nhạc 'Trịnh' 🎵"))
        assertTrue(ts, ts.all { it.code in 0x20..0x7e } && '\'' !in ts)
        assertEquals("mở nhạc 'Trịnh' 🎵", JSONObject(ts).getString("cau"))
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

    @Test
    fun cauBotTuLam() {
        assertEquals(XuLyLenh.AmLuong("dat", 10), XuLyLenh.viecTuChu("Âm lượng 10"))
        assertEquals(XuLyLenh.AmLuong("dat", 5), XuLyLenh.viecTuChu("chỉnh âm lượng xuống 5"))
        assertEquals(XuLyLenh.AmLuong("to"), XuLyLenh.viecTuChu("to lên"))
        assertEquals(XuLyLenh.AmLuong("nho"), XuLyLenh.viecTuChu("Nhỏ xuống!"))
        assertEquals(XuLyLenh.AmLuong("tat"), XuLyLenh.viecTuChu("tắt tiếng"))
        assertEquals(XuLyLenh.AmLuong("bat"), XuLyLenh.viecTuChu("bật tiếng"))
        assertEquals(XuLyLenh.BatManHinh, XuLyLenh.viecTuChu("bật tivi"))
        assertEquals(XuLyLenh.HoiTat, XuLyLenh.viecTuChu("Tắt tivi đi"))
        // Câu dài hơn thì vẫn để Map Voice / Trợ lý (không bắt nhầm).
        assertEquals(XuLyLenh.Noi("bật nhạc to lên"), XuLyLenh.viecTuChu("bật nhạc to lên"))
        assertEquals(XuLyLenh.NhanTivi("ăn cơm thôi"), XuLyLenh.viecTuChu("/nhan ăn cơm thôi"))
        assertEquals(XuLyLenh.NhanTivi(), XuLyLenh.viecTuChu("📢 Nhắn tivi"))
        assertEquals(XuLyLenh.Them, XuLyLenh.viecTuChu("⚙️ More"))
        // Chữ tự do (trả lời lúc bot đang chờ) khác nút / lệnh.
        assertTrue(XuLyLenh.laChuTuDo("to lên"))
        assertFalse(XuLyLenh.laChuTuDo("🎮 Điều khiển"))
        assertFalse(XuLyLenh.laChuTuDo("/start"))
        assertFalse(XuLyLenh.laChuTuDo(" "))
    }

    @Test
    fun nutNguoiDung() {
        assertEquals(XuLyLenh.NguoiXem("111"), XuLyLenh.viecTuNut("ud:111"))
        assertEquals(XuLyLenh.NguoiXem("@be_na"), XuLyLenh.viecTuNut("ud:@be_na"))
        assertEquals(XuLyLenh.DatVaiTro("@be_na", true), XuLyLenh.viecTuNut("uv:@be_na:c"))
        assertEquals(XuLyLenh.Duyet(333, false), XuLyLenh.viecTuNut("uy:333:t"))
        assertEquals(XuLyLenh.BoQuaXin(333), XuLyLenh.viecTuNut("ux:333"))
        assertEquals(XuLyLenh.KhoaThanhVien(XuLyLenh.TOI_SANG), XuLyLenh.viecTuNut("kt:s"))
        assertEquals(XuLyLenh.KhoaThanhVien(0), XuLyLenh.viecTuNut("kt:0"))
        assertNull(XuLyLenh.viecTuNut("kt:999"))
        assertNull(XuLyLenh.viecTuNut("ud:x;reboot"))
        assertNull(XuLyLenh.viecTuNut("uv:111:z"))
        // Quản lý người dùng chỉ chủ làm; lời nhắn, mạng, âm lượng, bật màn hình thì thành viên cũng được.
        listOf(XuLyLenh.NguoiDs, XuLyLenh.NguoiXem("1"), XuLyLenh.DatVaiTro("1", true), XuLyLenh.BoNguoi("1"), XuLyLenh.Duyet(1, false),
            XuLyLenh.BoQuaXin(1), XuLyLenh.ThemNguoi, XuLyLenh.NhatKyXem, XuLyLenh.KhoaThanhVien(60)).forEach { assertTrue(it.toString(), XuLyLenh.chiChu(it)) }
        listOf(XuLyLenh.NhanTivi(), XuLyLenh.Them, XuLyLenh.KiemTraMang, XuLyLenh.AmLuong("to"), XuLyLenh.BatManHinh)
            .forEach { assertFalse(it.toString(), XuLyLenh.chiChu(it)) }
    }

    @Test
    fun suaNguoiDung() {
        val chu = NguoiDung("@caominh", 111, VaiTro.CHU, "Minh")
        val ch = CauHinh(nguoiDung = listOf(chu, NguoiDung("@be_na", 0, VaiTro.THANH_VIEN)))
        // Tivi phải còn ít nhất một chủ.
        assertNull(XuLyLenh.doiVaiTro(ch, "111", chu = false))
        assertNull(XuLyLenh.boNguoi(ch, "111"))
        val hai = XuLyLenh.doiVaiTro(ch, "@be_na", chu = true)!!
        assertEquals(VaiTro.CHU, hai.nguoiDung[1].vaiTro)
        assertEquals(1, XuLyLenh.boNguoi(hai, "111")!!.nguoiDung.size)
        assertNull(XuLyLenh.boNguoi(ch, "@khong_co"))
        // Duyệt người xin dùng theo ID; thêm theo @username.
        val duyet = XuLyLenh.duyet(ch, XinDung(333, "", "Ông", 1), chu = false)!!
        assertEquals(NguoiDung("", 333, VaiTro.THANH_VIEN, "Ông"), duyet.nguoiDung.last())
        assertEquals("@ba_noi", XuLyLenh.chuanTen("https://t.me/ba_noi"))
        assertEquals("@ba_noi", XuLyLenh.chuanTen(" ba_noi "))
        assertNull(XuLyLenh.chuanTen("bà nội"))
        assertEquals("@ba_noi", XuLyLenh.themTen(ch, "@ba_noi")!!.nguoiDung.last().ten)
        assertNull(XuLyLenh.themTen(ch, "@BE_NA"))
        assertEquals("Minh · @caominh", XuLyLenh.tenXem(chu))
    }

    @Test
    fun khoaThanhVienToiSang() {
        val mui = java.time.ZoneId.of("Asia/Ho_Chi_Minh")
        fun luc(ngay: Int, gio: Int) = java.time.LocalDateTime.of(2026, 11, ngay, gio, 0).atZone(mui).toInstant().toEpochMilli()
        assertEquals(luc(2, 6), XuLyLenh.khoaTvToi(XuLyLenh.TOI_SANG, luc(1, 21), mui))
        assertEquals(luc(1, 6), XuLyLenh.khoaTvToi(XuLyLenh.TOI_SANG, luc(1, 2), mui))
        assertEquals(luc(1, 22), XuLyLenh.khoaTvToi(60, luc(1, 21), mui))
    }

    @Test
    fun nhom2() {
        assertEquals(XuLyLenh.ChupManHinh, XuLyLenh.viecTuNut("cm"))
        assertEquals(XuLyLenh.QuayManHinh, XuLyLenh.viecTuNut("qv"))
        assertEquals(XuLyLenh.DatNen, XuLyLenh.viecTuNut("nen"))
        assertEquals(XuLyLenh.BoNen, XuLyLenh.viecTuNut("nen:x"))
        assertEquals(XuLyLenh.TatCaKenh(2), XuLyLenh.viecTuNut("kd:2"))
        assertEquals(XuLyLenh.MoKenhTen(0), XuLyLenh.viecTuNut("kc:0"))
        assertNull(XuLyLenh.viecTuNut("kc:${XuLyLenh.KENH_TV.size}"))
        assertEquals(XuLyLenh.CastDuyet(7, "hom-nay"), XuLyLenh.viecTuNut("cd:7:hom-nay"))
        assertNull(XuLyLenh.viecTuNut("cd:7:xoa"))
        listOf(XuLyLenh.QuayManHinh, XuLyLenh.DatNen, XuLyLenh.BoNen, XuLyLenh.CastDuyet(1, "chan")).forEach { assertTrue(it.toString(), XuLyLenh.chiChu(it)) }
        listOf(XuLyLenh.TatCaKenh(0), XuLyLenh.MoKenhTen(1)).forEach { assertFalse(it.toString(), XuLyLenh.chiChu(it)) }
        // Tất cả kênh: 34 kênh, 15 kênh một trang (ba một hàng), trang cuối chỉ có nút lùi.
        val h = XuLyLenh.bangTatCaKenh(0).getJSONArray("inline_keyboard")
        assertEquals(6, h.length())
        assertEquals("VTV1", h.getJSONArray(0).getJSONObject(0).getString("text"))
        val cuoi = XuLyLenh.bangTatCaKenh(99).getJSONArray("inline_keyboard")
        assertEquals("kd:1", cuoi.getJSONArray(cuoi.length() - 1).getJSONObject(0).getString("callback_data"))
    }
}
