package vn.tivikit.teleremote

import org.json.JSONArray
import org.json.JSONObject

/**
 * Hiểu tin Telegram gửi tới bot của tivi (31/10/2026): ai nhắn (khớp danh sách người được dùng), nhắn gì (nút bàn phím của
 * bot, lệnh /…, nút trong tin nhắn), và dựng bàn phím của bot. Không đụng tới tivi hay mạng: LamViec làm việc thật.
 */
object XuLyLenh {
    /** Người nhắn: [id] số của Telegram, [ten] = @username ("" nếu không đặt), [tenHien] = tên hiện. */
    data class Nguoi(val id: Long, val ten: String, val tenHien: String)

    /**
     * Một tin tới bot: tin chữ ([chu]) hay nút bấm trong tin nhắn ([nut] = dữ liệu nút, [idNut] để trả lời nút, [idTin] =
     * tin chứa nút). [rieng] = chat riêng với bot (nhóm thì bỏ qua). [luc] = giờ gửi (giây) của tin chữ.
     */
    data class Den(
        val idCapNhat: Long, val nguoi: Nguoi, val chat: Long, val rieng: Boolean, val luc: Long,
        val chu: String? = null, val nut: String? = null, val idNut: String = "", val idTin: Long = 0,
    )

    sealed interface Viec
    data object Menu : Viec
    data object DieuKhien : Viec
    data object Kenh : Viec
    data object YouTube : Viec
    data object Tivi : Viec
    data object HoiTat : Viec
    data class Phim(val ma: Int) : Viec
    data class MoKenh(val o: Int) : Viec
    data object Tat : Viec
    data object ThoiTat : Viec
    data object LamMoi : Viec
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

    /** Tin chữ cũ hơn chừng này giây thì bỏ (tivi vừa có mạng lại: lệnh gõ từ lâu không làm nữa). */
    const val CU_QUA = 120L

    /** Chữ trên bàn phím chính của bot. */
    class Nhan(val dieuKhien: String, val kenh: String, val youTube: String, val tivi: String, val tat: String)

    val VI = Nhan("🎮 Điều khiển", "📺 Kênh nhanh", "▶️ YouTube", "ℹ️ Tivi", "📴 Tắt tivi")
    val EN = Nhan("🎮 Remote", "📺 Quick channel", "▶️ YouTube", "ℹ️ TV", "📴 Turn off TV")

    fun nhan(en: Boolean) = if (en) EN else VI

    fun docCapNhat(u: JSONObject): Den? {
        val id = u.optLong("update_id")
        u.optJSONObject("message")?.let { m ->
            val tu = m.optJSONObject("from") ?: return null
            val chat = m.optJSONObject("chat") ?: return null
            return Den(id, nguoi(tu), chat.optLong("id"), chat.optString("type") == "private", m.optLong("date"), chu = m.optString("text", ""))
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

    private val NUT_DIEU_KHIEN = setOf(VI.dieuKhien, EN.dieuKhien)
    private val NUT_KENH = setOf(VI.kenh, EN.kenh)
    private val NUT_YOUTUBE = setOf(VI.youTube, EN.youTube)
    private val NUT_TIVI = setOf(VI.tivi, EN.tivi)
    private val NUT_TAT = setOf(VI.tat, EN.tat)

    /** Việc cho một tin chữ: nút bàn phím của bot (cả hai thứ tiếng) hay lệnh /…; chữ khác thì KhongRo. */
    fun viecTuChu(chu: String): Viec {
        val c = chu.trim()
        val lenh = c.takeIf { it.startsWith("/") }?.substringBefore(' ')?.substringBefore('@')?.lowercase()
        return when {
            lenh == "/start" || lenh == "/menu" -> Menu
            lenh == "/remote" || c in NUT_DIEU_KHIEN -> DieuKhien
            lenh == "/kenh" || lenh == "/channels" || c in NUT_KENH -> Kenh
            lenh == "/youtube" || c in NUT_YOUTUBE -> YouTube
            lenh == "/tivi" || lenh == "/tv" || c in NUT_TIVI -> Tivi
            lenh == "/tat" || lenh == "/off" || c in NUT_TAT -> HoiTat
            else -> KhongRo
        }
    }

    /** Việc cho một nút trong tin nhắn; null nếu dữ liệu nút lạ (bản bot cũ, client sửa). */
    fun viecTuNut(du: String): Viec? = when {
        du == "r" -> LamMoi
        du == "t:ok" -> Tat
        du == "t:x" -> ThoiTat
        du.startsWith("k:") -> du.drop(2).toIntOrNull()?.takeIf { it in PHIM }?.let(::Phim)
        du.startsWith("o:") -> du.drop(2).toIntOrNull()?.takeIf { it in 1..SO_O }?.let(::MoKenh)
        else -> null
    }

    /** Việc chỉ chủ tivi được làm. */
    fun chiChu(v: Viec): Boolean = v == HoiTat || v == Tat

    /** Bàn phím chính (nằm cố định dưới ô gõ tin): hai cột; chủ có thêm Tắt tivi. */
    fun banPhimChinh(vt: VaiTro, en: Boolean): JSONObject {
        val n = nhan(en)
        val hang = mutableListOf(listOf(n.dieuKhien, n.kenh), listOf(n.youTube, n.tivi))
        if (vt == VaiTro.CHU) hang += listOf(n.tat)
        return JSONObject().put("keyboard", JSONArray(hang.map { h -> JSONArray(h.map { JSONObject().put("text", it) }) }))
            .put("resize_keyboard", true).put("is_persistent", true)
    }

    /** Bảng điều khiển: nút trong một tin nhắn, bấm thì bot sửa chính tin đó (không làm trôi khung chat). */
    fun bangDieuKhien(en: Boolean): JSONObject =
        nutTrongTin(PHIM.entries.chunked(3).map { h -> h.map { (ma, chu) -> chu to "k:$ma" } } + listOf(listOf((if (en) "🔄 Refresh" else "🔄 Cập nhật") to "r")))

    /** Các ô Kênh nhanh đang có trên tivi: (số ô, tên kênh), hai nút một hàng. */
    fun bangKenh(ds: List<Pair<Int, String>>): JSONObject = nutTrongTin(ds.chunked(2).map { h -> h.map { (o, ten) -> "📺 $ten" to "o:$o" } })

    fun hoiTat(en: Boolean): JSONObject =
        nutTrongTin(listOf(listOf((if (en) "📴 Turn off now" else "📴 Tắt ngay") to "t:ok", (if (en) "Cancel" else "Thôi") to "t:x")))

    private fun nutTrongTin(hang: List<List<Pair<String, String>>>) = JSONObject().put("inline_keyboard",
        JSONArray(hang.map { h -> JSONArray(h.map { (chu, du) -> JSONObject().put("text", chu).put("callback_data", du) }) }))

    /** Số ô Kênh nhanh tối đa (KenhNhanh.SO_O của TiviKit). */
    const val SO_O = 6

    /** Gói của ô Kênh nhanh [o] (KenhNhanh.goiCua của TiviKit). */
    fun goiKenh(o: Int): String = if (o == 1) "vn.tivikit.kenh" else "vn.tivikit.kenh$o"

    /**
     * Tên kênh trong kết quả lệnh doc của một ô Kênh nhanh ("Result: Bundle[{json={"ten":"VTV1",…}}]"); null nếu không đọc
     * được. Lấy từ sau "json=" tới "}]" cuối như docQua của TiviKit: dấu "}" cuối cùng là của Bundle, không phải của JSON.
     */
    fun tenKenh(kq: String): String? {
        val a = kq.indexOf("json="); val b = kq.lastIndexOf("}]")
        if (a < 0 || b <= a) return null
        return runCatching { JSONObject(kq.substring(a + 5, b)).optString("ten") }.getOrNull()?.takeIf { it.isNotBlank() }
    }
}
