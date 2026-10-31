package vn.tivikit.teleremote

import java.text.Normalizer
import org.json.JSONArray
import org.json.JSONObject

/**
 * Hiểu tin Telegram gửi tới bot của tivi (31/10/2026): ai nhắn (khớp danh sách người được dùng), nhắn gì (nút bàn phím của
 * bot, lệnh /…, nút trong tin nhắn, câu gõ như nói với Trợ lý, link, ảnh / video), và dựng bàn phím của bot. Không đụng tới
 * tivi hay mạng: LamViec làm việc thật.
 */
object XuLyLenh {
    /** Người nhắn: [id] số của Telegram, [ten] = @username ("" nếu không đặt), [tenHien] = tên hiện. */
    data class Nguoi(val id: Long, val ten: String, val tenHien: String)

    /** Ảnh / video người dùng gửi: [id] = file_id của Telegram, [co] = cỡ (byte, 0 = không rõ). */
    data class Tep(val id: String, val laVideo: Boolean, val co: Long)

    /**
     * Một tin tới bot: tin chữ ([chu]; gửi ảnh / video thì [tep]) hay nút bấm trong tin nhắn ([nut] = dữ liệu nút, [idNut] để
     * trả lời nút). [idTin] = tin chứa nút (nút bấm) hay chính tin vừa nhắn (tin chữ). [rieng] = chat riêng với bot (nhóm thì
     * bỏ qua). [luc] = giờ gửi (giây) của tin chữ.
     */
    data class Den(
        val idCapNhat: Long, val nguoi: Nguoi, val chat: Long, val rieng: Boolean, val luc: Long,
        val chu: String? = null, val nut: String? = null, val idNut: String = "", val idTin: Long = 0, val tep: Tep? = null,
    )

    sealed interface Viec
    data object Menu : Viec
    data object DieuKhien : Viec
    data class Phim(val ma: Int) : Viec
    /** Làm mới bảng điều khiển (app đang mở). */
    data object CapNhat : Viec
    /** Hỏi chữ để gõ vào ô nhập đang chọn trên tivi (thay chữ đang có trong ô). */
    data object GoChu : Viec
    /** Hỏi chữ để gõ tiếp vào ô vừa gõ (viết nối vào sau). */
    data object GoTiep : Viec
    data object Kenh : Viec
    data class MoKenh(val o: Int) : Viec
    data object YouTube : Viec
    data class UngDung(val trang: Int) : Viec
    data class MoUngDung(val goi: String) : Viec
    /** Buộc dừng app đang mở (app treo). */
    data object DungApp : Viec
    data object Tivi : Viec
    data object LamMoiTv : Viec
    data object DongAppNen : Viec
    data object DonCache : Viec
    data object HenGio : Viec
    data class TatSau(val phut: Int) : Viec
    data class HuyHen(val id: String) : Viec
    data object Khoa : Viec
    data object KhoaNgay : Viec
    data class MoTam(val phut: Int) : Viec
    data object ChupManHinh : Viec
    data object HoiTat : Viec
    data object Tat : Viec
    data object KhoiDongLai : Viec
    data object ThoiTat : Viec
    /** Câu gõ như nói với Trợ lý ("mở nhạc trịnh", "mở VTV3"): Map Voice làm, không thì Trợ lý Google của tivi. */
    data class Noi(val cau: String) : Viec
    data class Link(val url: String) : Viec
    data object ChieuTep : Viec
    data object KhongRo : Viec

    /**
     * Phím của bảng điều khiển (mã KeyEvent → chữ trên nút), theo thứ tự ba nút một hàng: Back ▲ Home / ◀ OK ▶ / phát-dừng
     * ▼ Menu / nhỏ, tắt tiếng, to. Dữ liệu nút ngoài danh sách này (client sửa) thì bỏ.
     */
    val PHIM: Map<Int, String> = linkedMapOf(
        4 to "↩️", 19 to "⬆️", 3 to "🏠",
        21 to "⬅️", 23 to "OK", 22 to "➡️",
        85 to "⏯", 20 to "⬇️", 82 to "☰",
        25 to "🔉", 164 to "🔇", 24 to "🔊",
    )

    /** Phím bấm được qua nút nhưng không nằm trên bảng điều khiển: Enter sau khi gõ chữ (tìm luôn trong ô tìm kiếm). */
    val PHIM_THEM: Map<Int, String> = mapOf(66 to "↵")

    /** Tin chữ cũ hơn chừng này giây thì bỏ (tivi vừa có mạng lại: lệnh gõ từ lâu không làm nữa). */
    const val CU_QUA = 120L

    /** Bot chỉ tải được tệp tới 20 MB (getFile của Bot API). */
    const val TEP_TOI_DA = 20L * 1024 * 1024

    /** Nút hẹn tắt tivi sau bao nhiêu phút (Hẹn giờ). */
    val SAU_PHUT = listOf(15, 30, 60, 90)

    /** Nút mở tạm Khoá trẻ em bao nhiêu phút. */
    val MO_TAM = listOf(30, 60, 120)

    const val APP_MOI_TRANG = 12

    /** Chữ trên bàn phím chính của bot. */
    class Nhan(
        val dieuKhien: String, val kenh: String, val youTube: String, val ungDung: String, val henGio: String, val khoa: String,
        val lamMoi: String, val tivi: String, val manHinh: String, val tat: String,
    )

    val VI = Nhan("🎮 Điều khiển", "📺 Kênh nhanh", "▶️ YouTube", "📱 Ứng dụng", "⏰ Hẹn giờ", "🔒 Khoá trẻ em", "🧹 Làm mới",
        "ℹ️ Tivi", "📸 Màn hình", "📴 Tắt tivi")
    val EN = Nhan("🎮 Remote", "📺 Quick channel", "▶️ YouTube", "📱 Apps", "⏰ Timer", "🔒 Kids lock", "🧹 Refresh TV",
        "ℹ️ TV", "📸 Screenshot", "📴 Turn off TV")

    fun nhan(en: Boolean) = if (en) EN else VI

    fun docCapNhat(u: JSONObject): Den? {
        val id = u.optLong("update_id")
        u.optJSONObject("message")?.let { m ->
            val tu = m.optJSONObject("from") ?: return null
            val chat = m.optJSONObject("chat") ?: return null
            return Den(id, nguoi(tu), chat.optLong("id"), chat.optString("type") == "private", m.optLong("date"),
                chu = m.optString("text", ""), idTin = m.optLong("message_id"), tep = tep(m))
        }
        u.optJSONObject("callback_query")?.let { q ->
            val tu = q.optJSONObject("from") ?: return null
            val m = q.optJSONObject("message")
            val chat = m?.optJSONObject("chat")
            return Den(
                id, nguoi(tu), chat?.optLong("id") ?: tu.optLong("id"), chat == null || chat.optString("type") == "private",
                m?.optLong("date") ?: 0, nut = q.optString("data", ""), idNut = q.optString("id"), idTin = m?.optLong("message_id") ?: 0,
            )
        }
        return null
    }

    private fun nguoi(tu: JSONObject) = Nguoi(
        tu.optLong("id"),
        tu.optString("username", "").takeIf { it.isNotEmpty() }?.let { "@$it" }.orEmpty(),
        listOf(tu.optString("first_name", ""), tu.optString("last_name", "")).filter { it.isNotBlank() }.joinToString(" ").take(64),
    )

    /** Ảnh (cỡ lớn nhất bot tải được), video, hay tệp ảnh / video gửi dạng file (giữ nguyên chất lượng). */
    private fun tep(m: JSONObject): Tep? {
        m.optJSONArray("photo")?.let { a ->
            // Các cỡ của cùng một ảnh, nhỏ tới lớn.
            val ds = (0 until a.length()).mapNotNull { a.optJSONObject(it) }
            val o = ds.lastOrNull { it.optLong("file_size") in 1..TEP_TOI_DA } ?: ds.lastOrNull() ?: return null
            return Tep(o.optString("file_id"), false, o.optLong("file_size"))
        }
        m.optJSONObject("video")?.let { return Tep(it.optString("file_id"), true, it.optLong("file_size")) }
        m.optJSONObject("document")?.let { d ->
            val loai = d.optString("mime_type")
            if (loai.startsWith("image/") || loai.startsWith("video/")) return Tep(d.optString("file_id"), loai.startsWith("video/"), d.optLong("file_size"))
        }
        return null
    }

    /** Tin chữ gửi quá [CU_QUA] giây trước [bay] (giây)? Nút bấm không xét (tin chứa nút có thể cũ mà bấm vừa xong). */
    fun cu(d: Den, bay: Long): Boolean = d.chu != null && d.luc > 0 && bay - d.luc > CU_QUA

    /** Người dùng khớp [n]: theo ID nếu mục đó đã ghi ID, không thì theo @username (không phân biệt hoa thường). */
    fun tim(ch: CauHinh, n: Nguoi): NguoiDung? =
        ch.nguoiDung.firstOrNull { it.id != 0L && it.id == n.id }
            ?: ch.nguoiDung.firstOrNull { it.id == 0L && n.ten.isNotEmpty() && it.ten.equals(n.ten, ignoreCase = true) }

    /**
     * Cấu hình sau khi ghi ID (lần đầu khớp theo @username), username và tên hiện mới của [n] vào mục [nd]; null nếu không
     * đổi gì. Người bỏ username thì giữ tên cũ để chủ tivi còn nhận ra (khớp đã theo ID).
     */
    fun ghiNguoi(ch: CauHinh, nd: NguoiDung, n: Nguoi): CauHinh? {
        val moi = nd.copy(id = n.id, ten = n.ten.ifEmpty { nd.ten }, tenHien = n.tenHien.ifEmpty { nd.tenHien })
        if (moi == nd) return null
        return ch.copy(nguoiDung = ch.nguoiDung.map { if (it === nd) moi else it })
    }

    private fun nut(lay: (Nhan) -> String) = setOf(lay(VI), lay(EN))
    private val NUT_DIEU_KHIEN = nut { it.dieuKhien }
    private val NUT_KENH = nut { it.kenh }
    private val NUT_YOUTUBE = nut { it.youTube }
    private val NUT_UNG_DUNG = nut { it.ungDung }
    private val NUT_HEN_GIO = nut { it.henGio }
    private val NUT_KHOA = nut { it.khoa }
    private val NUT_LAM_MOI = nut { it.lamMoi }
    private val NUT_TIVI = nut { it.tivi }
    private val NUT_MAN_HINH = nut { it.manHinh }
    private val NUT_TAT = nut { it.tat }

    /**
     * Việc cho một tin: ảnh / video thì chiếu lên tivi; nút bàn phím của bot (cả hai thứ tiếng) hay lệnh /…; tin có link
     * thì mở link; chữ khác là câu nhắn như nói. Lệnh / lạ và tin rỗng (sticker…) thì KhongRo.
     */
    fun viecTuTin(d: Den): Viec = if (d.tep != null) ChieuTep else viecTuChu(d.chu.orEmpty())

    fun viecTuChu(chu: String): Viec {
        val c = chu.trim()
        val lenh = c.takeIf { it.startsWith("/") }?.substringBefore(' ')?.substringBefore('@')?.lowercase()
        return when {
            lenh == "/start" || lenh == "/menu" -> Menu
            lenh == "/remote" || c in NUT_DIEU_KHIEN -> DieuKhien
            lenh == "/kenh" || lenh == "/channels" || c in NUT_KENH -> Kenh
            lenh == "/youtube" || c in NUT_YOUTUBE -> YouTube
            lenh == "/apps" || lenh == "/ungdung" || c in NUT_UNG_DUNG -> UngDung(0)
            lenh == "/hengio" || lenh == "/timer" || c in NUT_HEN_GIO -> HenGio
            lenh == "/khoa" || lenh == "/kidslock" || c in NUT_KHOA -> Khoa
            lenh == "/lammoi" || lenh == "/refresh" || c in NUT_LAM_MOI -> LamMoiTv
            lenh == "/tivi" || lenh == "/tv" || c in NUT_TIVI -> Tivi
            lenh == "/manhinh" || lenh == "/screenshot" || c in NUT_MAN_HINH -> ChupManHinh
            lenh == "/go" || lenh == "/type" -> GoChu
            lenh == "/tat" || lenh == "/off" || c in NUT_TAT -> HoiTat
            lenh != null || c.isEmpty() -> KhongRo
            else -> timLink(c)?.let(::Link) ?: Noi(c.take(300))
        }
    }

    private val GOI = Regex("""^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$""")
    private val ID_HEN = Regex("""^[a-z]+-\d{1,15}$""")

    /** Việc cho một nút trong tin nhắn; null nếu dữ liệu nút lạ (bản bot cũ, client sửa). */
    fun viecTuNut(du: String): Viec? = when {
        du == "r" -> CapNhat
        du == "g" -> GoChu
        du == "g+" -> GoTiep
        du == "as" -> DungApp
        du == "t:ok" -> Tat
        du == "t:x" -> ThoiTat
        du == "t:rb" -> KhoiDongLai
        du == "t:hoi" -> HoiTat
        du == "lm:r" -> LamMoiTv
        du == "lm:d" -> DongAppNen
        du == "lm:c" -> DonCache
        du == "h:r" -> HenGio
        du == "kh:r" -> Khoa
        du == "kh:k" -> KhoaNgay
        du.startsWith("k:") -> du.drop(2).toIntOrNull()?.takeIf { it in PHIM || it in PHIM_THEM }?.let(::Phim)
        du.startsWith("o:") -> du.drop(2).toIntOrNull()?.takeIf { it in 1..SO_O }?.let(::MoKenh)
        du.startsWith("ap:") -> du.drop(3).toIntOrNull()?.takeIf { it in 0..99 }?.let(::UngDung)
        du.startsWith("a:") -> du.drop(2).takeIf { GOI.matches(it) }?.let(::MoUngDung)
        du.startsWith("h:t:") -> du.drop(4).toIntOrNull()?.takeIf { it in SAU_PHUT }?.let(::TatSau)
        du.startsWith("h:x:") -> du.drop(4).takeIf { ID_HEN.matches(it) }?.let(::HuyHen)
        du.startsWith("kh:m:") -> du.drop(5).toIntOrNull()?.takeIf { it in MO_TAM }?.let(::MoTam)
        else -> null
    }

    /** Việc chỉ chủ tivi được làm (thành viên: điều khiển, gõ chữ, kênh, YouTube, ứng dụng, nhắn như nói, link, ảnh, xem tivi). */
    fun chiChu(v: Viec): Boolean = v is TatSau || v is HuyHen || v is MoTam || v in setOf(
        HoiTat, Tat, KhoiDongLai, HenGio, Khoa, KhoaNgay, ChupManHinh, LamMoiTv, DongAppNen, DonCache, DungApp,
    )

    /** Tính năng đang dùng được trên tivi, để bàn phím chỉ hiện nút có tác dụng: Hẹn giờ, Khoá trẻ em đã cài, chủ bật chụp. */
    data class Co(val henGio: Boolean = false, val khoa: Boolean = false, val manHinh: Boolean = false)

    /** Bàn phím chính (nằm cố định dưới ô gõ tin): hai cột; chủ có thêm Hẹn giờ, Khoá trẻ em, Làm mới, Màn hình, Tắt tivi. */
    fun banPhimChinh(vt: VaiTro, en: Boolean, co: Co = Co()): JSONObject {
        val n = nhan(en)
        val ds = if (vt == VaiTro.CHU) listOfNotNull(
            n.dieuKhien, n.kenh, n.youTube, n.ungDung, n.henGio.takeIf { co.henGio }, n.khoa.takeIf { co.khoa },
            n.lamMoi, n.tivi, n.manHinh.takeIf { co.manHinh }, n.tat,
        ) else listOf(n.dieuKhien, n.kenh, n.youTube, n.ungDung, n.tivi)
        return JSONObject().put("keyboard", JSONArray(ds.chunked(2).map { h -> JSONArray(h.map { JSONObject().put("text", it) }) }))
            .put("resize_keyboard", true).put("is_persistent", true)
            .put("input_field_placeholder", if (en) "Type like talking to the Assistant…" else "Gõ như nói với Trợ lý…")
    }

    /** Bảng điều khiển: nút trong một tin nhắn, bấm thì bot sửa chính tin đó (không làm trôi khung chat). */
    fun bangDieuKhien(en: Boolean): JSONObject = nutTrongTin(PHIM.entries.chunked(3).map { h -> h.map { (ma, chu) -> chu to "k:$ma" } } +
        listOf(listOf((if (en) "⌨️ Type" else "⌨️ Gõ chữ") to "g", (if (en) "🔄 Refresh" else "🔄 Cập nhật") to "r")))

    /** Các ô Kênh nhanh đang có trên tivi: (số ô, tên kênh), hai nút một hàng. */
    fun bangKenh(ds: List<Pair<Int, String>>): JSONObject = nutTrongTin(ds.chunked(2).map { h -> h.map { (o, ten) -> "📺 $ten" to "o:$o" } })

    /**
     * Một trang danh sách app ([ds]: gói, tên), hai app một hàng, [APP_MOI_TRANG] app một trang, hàng chuyển trang; chủ có
     * thêm nút buộc dừng app đang mở.
     */
    fun bangUngDung(ds: List<Pair<String, String>>, trang: Int, en: Boolean, chu: Boolean): JSONObject {
        val soTrang = soTrang(ds.size)
        val t = trang.coerceIn(0, soTrang - 1)
        val hang = ds.drop(t * APP_MOI_TRANG).take(APP_MOI_TRANG).map { (goi, ten) -> ten.take(28) to "a:$goi" }.chunked(2).toMutableList()
        if (soTrang > 1) hang += listOfNotNull(("◀️" to "ap:${t - 1}").takeIf { t > 0 }, "${t + 1}/$soTrang" to "ap:$t",
            ("▶️" to "ap:${t + 1}").takeIf { t < soTrang - 1 })
        if (chu) hang += listOf((if (en) "⏹ Force-stop the app showing" else "⏹ Buộc dừng app đang mở") to "as")
        return nutTrongTin(hang)
    }

    fun soTrang(soApp: Int): Int = maxOf(1, (soApp + APP_MOI_TRANG - 1) / APP_MOI_TRANG)

    /** Hẹn giờ: tắt tivi sau 15 / 30 / 60 / 90 phút, huỷ từng hẹn một lần ([motLan]: id, chữ), cập nhật. */
    fun bangHenGio(motLan: List<Pair<String, String>>, en: Boolean): JSONObject = nutTrongTin(
        SAU_PHUT.map { p -> (if (en) "😴 Off in $p min" else "😴 Tắt sau $p phút") to "h:t:$p" }.chunked(2) +
            motLan.map { (id, chu) -> listOf("✕ $chu" to "h:x:$id") } +
            listOf(listOf((if (en) "🔄 Refresh" else "🔄 Cập nhật") to "h:r")),
    )

    /** Khoá trẻ em: khoá ngay (tivi đã có mã), mở tạm 30 phút / 1 giờ / 2 giờ, cập nhật. */
    fun bangKhoa(en: Boolean, coMa: Boolean): JSONObject = nutTrongTin(listOfNotNull(
        listOf((if (en) "🔒 Lock now" else "🔒 Khoá ngay") to "kh:k").takeIf { coMa },
        MO_TAM.map { p -> "🔓 " + thoiLuong(p, en) to "kh:m:$p" },
        listOf((if (en) "🔄 Refresh" else "🔄 Cập nhật") to "kh:r"),
    ))

    /** Làm mới: đóng app nền, dọn cache (bộ nhớ gần đầy), chủ có thêm khởi động lại (hỏi lại trước), đo lại. */
    fun bangLamMoi(en: Boolean, ganDay: Boolean): JSONObject = nutTrongTin(listOfNotNull(
        listOfNotNull((if (en) "🧹 Close background apps" else "🧹 Đóng app nền") to "lm:d",
            ((if (en) "🗑 Clear cache" else "🗑 Dọn cache") to "lm:c").takeIf { ganDay }),
        listOf((if (en) "🔁 Restart…" else "🔁 Khởi động lại…") to "t:hoi", (if (en) "🔄 Measure again" else "🔄 Đo lại") to "lm:r"),
    ))

    fun hoiTat(en: Boolean): JSONObject = nutTrongTin(listOf(
        listOf((if (en) "📴 Turn off now" else "📴 Tắt ngay") to "t:ok", (if (en) "🔁 Restart" else "🔁 Khởi động lại") to "t:rb"),
        listOf((if (en) "Cancel" else "Thôi") to "t:x"),
    ))

    /** Sau khi gõ chữ lên tivi: Enter (tìm luôn), gõ tiếp (viết nối vào sau), gõ lại (thay hết). */
    fun sauKhiGo(en: Boolean): JSONObject = nutTrongTin(listOf(listOf((if (en) "↵ Enter" else "↵ Enter (tìm)") to "k:66"),
        listOf((if (en) "⌨️ Type more" else "⌨️ Gõ tiếp") to "g+", (if (en) "⌨️ Retype" else "⌨️ Gõ lại") to "g")))

    /** Ô gõ trả lời ngay (bot đang chờ chữ để gõ lên tivi / mã mở khoá): [goiY] hiện mờ trong ô gõ. */
    fun choTraLoi(goiY: String): JSONObject = JSONObject().put("force_reply", true).put("input_field_placeholder", goiY.take(64))

    private fun nutTrongTin(hang: List<List<Pair<String, String>>>) = JSONObject().put("inline_keyboard",
        JSONArray(hang.map { h -> JSONArray(h.map { (chu, du) -> JSONObject().put("text", chu).put("callback_data", du) }) }))

    /** "30 phút" / "1 giờ" / "2 giờ" ("1 h"…). */
    fun thoiLuong(phut: Int, en: Boolean): String = when {
        phut % 60 == 0 -> if (en) "${phut / 60} h" else "${phut / 60} giờ"
        else -> if (en) "$phut min" else "$phut phút"
    }

    /** Số ô Kênh nhanh tối đa (KenhNhanh.SO_O của TiviKit). */
    const val SO_O = 6

    /** Gói của ô Kênh nhanh [o] (KenhNhanh.goiCua của TiviKit). */
    fun goiKenh(o: Int): String = if (o == 1) "vn.tivikit.kenh" else "vn.tivikit.kenh$o"

    /**
     * Tên kênh trong kết quả lệnh doc của một ô Kênh nhanh ("Result: Bundle[{json={"ten":"VTV1",…}}]"); null nếu không đọc
     * được. Lấy từ sau "json=" tới "}]" cuối như docQua của TiviKit: dấu "}" cuối cùng là của Bundle, không phải của JSON.
     */
    fun tenKenh(kq: String): String? = jsonTrong(kq)?.optString("ten")?.takeIf { it.isNotBlank() }

    /** JSON trong kết quả content call ("Result: Bundle[{json={…}}]"); null nếu không có / hỏng. */
    fun jsonTrong(kq: String): JSONObject? {
        val a = kq.indexOf("json="); val b = kq.lastIndexOf("}]")
        if (a < 0 || b <= a) return null
        return runCatching { JSONObject(kq.substring(a + 5, b)) }.getOrNull()
    }

    /**
     * Giá trị [khoa] trong kết quả content call ("Result: Bundle[{viec=yt-tim|nhạc trịnh, app=…, ok=1}]"); "" nếu không
     * có. Giá trị có thể chứa dấu phẩy (câu nói): lấy tới khoá kế tiếp ", <chữ>=" hay "}]".
     */
    fun giaTri(kq: String, khoa: String): String {
        val m = Regex("""[{ ]$khoa=""").find(kq) ?: return ""
        val sau = kq.substring(m.range.last + 1)
        val het = listOfNotNull(Regex(""", [a-zA-Z]+=""").find(sau)?.range?.first, sau.lastIndexOf("}]").takeIf { it >= 0 }).minOrNull() ?: sau.length
        return sau.substring(0, het).trim()
    }

    /**
     * Tham số JSON cho content call qua ADB: ký tự ngoài ASCII viết \uXXXX (lệnh gửi qua ADB phải thuần ASCII, chữ có dấu
     * bị hỏng), nháy đơn viết ' (tham số nằm trong nháy đơn của shell).
     */
    fun thamSo(j: JSONObject): String = buildString {
        for (c in j.toString()) if (c.code > 0x7e || c == '\'') append("\\u%04x".format(c.code)) else append(c)
    }

    /** Link http(s) đầu tiên trong tin (bỏ dấu câu dính cuối); null nếu không có. */
    fun timLink(chu: String): String? = Regex("""https?://\S+""", RegexOption.IGNORE_CASE).find(chu)?.value?.trimEnd('.', ',', ')', '!', '?', '"', '\'')

    /** Mã video trong link YouTube (watch?v=, youtu.be/, shorts/, live/, embed/); null nếu không phải link video YouTube. */
    fun maYouTube(url: String): String? {
        val m = Regex("""^https?://(?:www\.|m\.|music\.)?(?:youtube\.com/(?:watch\?(?:.*&)?v=|shorts/|live/|embed/)|youtu\.be/)([A-Za-z0-9_-]{11})(?![A-Za-z0-9_-])""",
            RegexOption.IGNORE_CASE).find(url)
        return m?.groupValues?.get(1)
    }

    fun laYouTube(url: String): Boolean = Regex("""^https?://(?:[a-z]+\.)?(?:youtube\.com|youtu\.be)/""", RegexOption.IGNORE_CASE).containsMatchIn(url)

    fun laFpt(url: String): Boolean = Regex("""^https?://(?:[a-z0-9-]+\.)?fptplay\.vn(?:/|$)""", RegexOption.IGNORE_CASE).containsMatchIn(url)

    /** Tên miền của link (ghi nhật ký, không ghi cả link). */
    fun tenMien(url: String): String = url.substringAfter("://").substringBefore('/').substringBefore('?').lowercase().take(60)

    /** Bỏ dấu tiếng Việt, chữ thường (so tên app với câu gõ). */
    fun boDau(s: String): String = Normalizer.normalize(s.replace('đ', 'd').replace('Đ', 'D'), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "").lowercase()

    /**
     * App có tên khớp câu "mở Netflix" / "open Netflix" / "Netflix" ([ds]: gói, tên): bỏ chữ mở đầu, so không dấu không hoa
     * thường; trùng hẳn tên trước, rồi tên chứa câu (tên ngắn nhất). null nếu không có.
     */
    fun timApp(cau: String, ds: List<Pair<String, String>>): String? {
        val c = boDau(cau).trim().replace(Regex("""^(mo|bat|vao|chay|open|launch|start)\s+(app\s+|ung dung\s+)?"""), "").trim()
        if (c.length < 3) return null
        ds.firstOrNull { boDau(it.second).trim() == c }?.let { return it.first }
        return ds.filter { boDau(it.second).contains(c) }.minByOrNull { it.second.length }?.first
    }

    /** Mã mở khoá trong tin: 6 số (app xác thực) hay 8 số (mã dự phòng), cho phép dấu cách ("123 456"). */
    fun maKhoa(chu: String): String? = chu.filter { it.isDigit() }.takeIf { (it.length == 6 || it.length == 8) && chu.none { c -> c.isLetter() } }
}
