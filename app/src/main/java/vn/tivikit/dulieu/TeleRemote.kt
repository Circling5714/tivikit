package vn.tivikit.dulieu

import org.json.JSONArray
import org.json.JSONObject

/**
 * Tele Remote (31/10/2026): app phụ vn.tivikit.teleremote trên tivi (module teleremote/). Bot Telegram riêng của tivi chạy
 * ngay trên tivi (hỏi Telegram liên tục, không cần mở cổng hay máy chủ); người chủ tivi cho phép điều khiển tivi từ
 * Telegram: bàn phím remote, Kênh nhanh, YouTube, tình trạng tivi, tắt tivi (chỉ chủ). Mỗi tivi một bot (tạo ở @BotFather).
 * Ở đây: tên gói, cấu hình, lệnh, trạng thái app phụ trả về.
 */
object TeleRemote {
    const val GOI = "vn.tivikit.teleremote"
    const val DICH_VU = "$GOI/vn.tivikit.teleremote.TeleRemoteDichVu"
    const val APK = "teleremote.apk"
    /** Như CauHinh.NGUOI_TOI_DA của app phụ. */
    const val NGUOI_TOI_DA = 20

    enum class VaiTro(val ma: String) {
        CHU("chu"), THANH_VIEN("thanh-vien");

        companion object {
            fun tuMa(s: String?): VaiTro = if (s == CHU.ma) CHU else THANH_VIEN
        }
    }

    /** Người được dùng bot, như NguoiDung của app phụ: [id] = 0 khi người đó chưa nhắn bot lần nào. */
    data class NguoiDung(val ten: String, val id: Long, val vaiTro: VaiTro, val tenHien: String = "") {
        /** Tên cho người dùng xem: @username, không có thì tên hiện. */
        val tenXem: String get() = ten.ifEmpty { tenHien.ifEmpty { "ID $id" } }
    }

    data class CauHinh(
        val bat: Boolean = true,
        val nguoiDung: List<NguoiDung> = emptyList(),
        val chupManHinh: Boolean = false,
        val tiengAnh: Boolean = false,
    ) {
        fun json(): JSONObject = JSONObject().put("bat", bat).put("chupManHinh", chupManHinh).put("tiengAnh", tiengAnh)
            .put("nguoiDung", JSONArray().apply {
                nguoiDung.forEach { put(JSONObject().put("ten", it.ten).put("id", it.id).put("vaiTro", it.vaiTro.ma).put("tenHien", it.tenHien)) }
            })
    }

    fun cauHinh(j: JSONObject?): CauHinh? = j?.let {
        val a = it.optJSONArray("nguoiDung") ?: JSONArray()
        CauHinh(
            it.optBoolean("bat", true),
            (0 until a.length()).mapNotNull { i -> a.optJSONObject(i) }
                .map { o -> NguoiDung(o.optString("ten"), o.optLong("id"), VaiTro.tuMa(o.optString("vaiTro")), o.optString("tenHien")) },
            it.optBoolean("chupManHinh", false), it.optBoolean("tiengAnh", false),
        )
    }

    /** Người lạ đã nhắn bot, chờ chủ tivi duyệt. */
    data class XinDung(val id: Long, val ten: String, val tenHien: String, val luc: Long) {
        val tenXem: String get() = listOf(ten, tenHien).filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { "ID $id" }
    }

    /** Việc bot đã làm: [viec] = dieu-khien, kenh|<tên>, mo|<gói>, tat, xin-dung. */
    data class NhatKy(val luc: Long, val ai: String, val viec: String)

    /**
     * Trạng thái app phụ (content call … --method doc). [adb] như Map Voice (da-noi, dang-hoi, tu-choi, khong-co…); [tele] =
     * vòng hỏi Telegram: dang-nghe, chua-co-token, tam-tat, sai-token, trung-bot (bot đang chạy ở nơi khác), khong-mang, loi.
     */
    data class TrangThai(
        val phienBan: String, val dichVuChay: Boolean, val adb: String, val tele: String, val lanCuoi: Long, val coToken: Boolean,
        val bot: String, val cauHinh: CauHinh, val cho: List<XinDung>, val nhatKy: List<NhatKy>,
    )

    fun trangThai(j: JSONObject): TrangThai {
        fun <T> mang(ten: String, f: (JSONObject) -> T): List<T> =
            j.optJSONArray(ten)?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map(f) }.orEmpty()
        return TrangThai(
            j.optString("phienBan"), j.optBoolean("dichVuChay"), j.optString("adb"), j.optString("tele"), j.optLong("lanCuoi"),
            j.optBoolean("coToken"), j.optString("bot"), cauHinh(j.optJSONObject("cauHinh")) ?: CauHinh(),
            mang("cho") { XinDung(it.optLong("id"), it.optString("ten"), it.optString("tenHien"), it.optLong("luc")) },
            mang("nhatKy") { NhatKy(it.optLong("luc"), it.optString("ai"), it.optString("viec")) },
        )
    }

    /** "@caominh", "caominh", "t.me/caominh", "https://t.me/caominh" → "@caominh"; không phải username Telegram → null. */
    fun chuanTen(s: String): String? {
        val t = s.trim().removePrefix("https://").removePrefix("http://").removePrefix("t.me/").removePrefix("telegram.me/").removePrefix("@")
        return "@$t".takeIf { Regex("@[A-Za-z0-9_]{4,32}").matches(it) }
    }

    /** Token của @BotFather ("123456789:AA…"), bỏ khoảng trắng / xuống dòng dán thừa; sai dạng → null. */
    fun chuanToken(s: String): String? = s.replace(Regex("\\s+"), "").takeIf { Regex("\\d{5,12}:[A-Za-z0-9_-]{30,50}").matches(it) }

    /**
     * Lệnh content call tới app phụ: [arg] thành ASCII (chữ có dấu thành \uXXXX, libadb hỏng với chữ ngoài ASCII), không có
     * nháy đơn (đi trong nháy đơn của shell).
     */
    private fun lenh(method: String, arg: String? = null): String =
        "content call --uri content://$GOI --method $method" +
            (arg?.let { " --arg '" + TiviKitLauncher.chiAscii(it).replace("'", "\\u0027") + "'" } ?: "")

    fun lenhDat(c: CauHinh): String = lenh("dat", c.json().toString())
    fun lenhToken(token: String): String = lenh("token", JSONObject().put("token", token).toString())
    fun lenhXoaCho(id: Long): String = lenh("xoa-cho", JSONObject().put("id", id).toString())
    val LENH_NHAN_THU = lenh("nhan-thu")
    val LENH_NOI_LAI = lenh("noi-lai")

    /** Giá trị [khoa] trong kết quả content call ("Result: Bundle[{bot=TiviNha_bot, ok=1}]"); "" nếu không có. */
    fun trong(ketQua: String, khoa: String): String = Regex("[{ ]$khoa=([^,}]*)").find(ketQua)?.groupValues?.get(1)?.trim().orEmpty()
}
