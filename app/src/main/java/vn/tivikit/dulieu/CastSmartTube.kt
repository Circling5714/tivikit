package vn.tivikit.dulieu

import org.json.JSONObject

/**
 * Cast SmartTube (28/10/2026): app phụ vn.tivikit.cast trên tivi (module cast/) cho app YouTube trên điện thoại cùng Wi-Fi
 * thấy tivi trong nút Cast mà không cần liên kết bằng mã TV, video gửi sang mở bằng SmartTube. Ở đây: tên gói, lệnh đặt
 * tên, trạng thái app phụ trả về.
 */
object CastSmartTube {
    const val GOI = "vn.tivikit.cast"
    /** Dịch vụ trợ năng: giữ máy nhận chạy suốt, được mở SmartTube khi chạy nền. */
    const val DICH_VU = "$GOI/vn.tivikit.cast.CastDichVu"
    /** Quyền nghe thông báo: chỉ để đọc / điều khiển phiên media của SmartTube (tua, đồng bộ tạm dừng). */
    const val NGHE = "$GOI/vn.tivikit.cast.NgheThongBao"
    val SMARTTUBE = listOf("org.smarttube.stable", "org.smarttube.beta", "com.liskovsoft.smarttubetv", "com.liskovsoft.smarttubetv.beta")
    const val TEN_TOI_DA = 60

    /** Tên hiện trong nút Cast: bỏ ký tự điều khiển, gọn khoảng trắng, tối đa [TEN_TOI_DA] ký tự. */
    fun chuanTen(s: String): String = s.replace(Regex("\\s+"), " ").filter { !it.isISOControl() }.trim().take(TEN_TOI_DA)

    /**
     * Lệnh đặt tên ("" = tên mặc định "SmartTube · <tên tivi>"): chỉ ASCII (chữ có dấu thành \uXXXX, libadb hỏng với chữ
     * ngoài ASCII), không có nháy đơn.
     */
    fun lenhDatTen(ten: String): String {
        val json = TiviKitLauncher.chiAscii(JSONObject().put("ten", chuanTen(ten)).toString()).replace("'", "\\u0027")
        return "content call --uri content://$GOI --method dat --arg '$json'"
    }

    /**
     * Xin mã TV để điện thoại liên kết thủ công (YouTube → Cast → Liên kết bằng mã TV): cho tivi có máy Cast của hãng trả
     * lời trước (điện thoại không thấy tivi qua Wi-Fi). Mã sống chừng 5 phút.
     */
    const val LENH_MA_TV = "content call --uri content://$GOI --method ma-tv"

    /** Mã TV trong kết quả của [LENH_MA_TV] ("Result: Bundle[{ma=123456789012}]"); "" nếu chưa có. */
    fun maTv(ketQua: String): String = Regex("ma=(\\d{6,16})").find(ketQua)?.groupValues?.get(1).orEmpty()

    /** Mã TV chia nhóm 3 số cho dễ gõ, như app YouTube trên tivi: "123 456 789 012". */
    fun hienMa(ma: String): String = ma.chunked(3).joinToString(" ")

    data class NhatKy(val luc: Long, val viec: String)

    /**
     * Một điện thoại đã cast lên tivi (app Cast 2.0, 28/10/2026): [trangThai] CHO (chờ duyệt) / CHO_PHEP / CHAN, [hetHan] =
     * khách được cast tới lúc này (0 = mãi). YouTube không cho tivi biết mọi máy đã liên kết, chỉ biết máy đã nối vào.
     */
    data class May(
        val id: String, val ten: String, val taiKhoan: String, val trangThai: String, val hetHan: Long, val lanDau: Long, val lanCuoi: Long,
    ) {
        /** Tài khoản Google và tên máy (bỏ trùng); "" nếu không có. */
        val tenHien: String get() = listOf(taiKhoan, ten).map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(" · ")
        val biChan get() = trangThai == "CHAN"
        val choDuyet get() = trangThai != "CHAN" && trangThai != "CHO_PHEP"
        fun quaHan(bay: Long) = trangThai == "CHO_PHEP" && hetHan in 1..bay
    }

    /** Việc với một điện thoại: cho phép luôn / tới hết hôm nay / 24 giờ, chặn, xoá khỏi danh sách (lần sau tivi hỏi lại). */
    enum class Lam(val ma: String) { CHO_PHEP("cho-phep"), HOM_NAY("hom-nay"), MOT_NGAY("24h"), CHAN("chan"), XOA("xoa") }

    /** Lệnh làm [lam] với máy [id] (chỉ ASCII: mã máy theo tài khoản có thể có chữ có dấu). */
    fun lenhDienThoai(id: String, lam: Lam): String {
        val json = TiviKitLauncher.chiAscii(JSONObject().put("id", id).put("lam", lam.ma).toString()).replace("'", "\\u0027")
        return "content call --uri content://$GOI --method dien-thoai --arg '$json'"
    }

    /** Lệnh bật / tạm ngừng nhận cast (tắt thì tivi bỏ qua video điện thoại gửi, vẫn giữ liên kết). */
    fun lenhNhanCast(bat: Boolean) = "content call --uri content://$GOI --method dat --arg '{\"nhanCast\":$bat}'"

    /** Huỷ mọi liên kết: tivi đổi screen với YouTube, mọi điện thoại phải nhập mã TV mới. */
    const val LENH_HUY_LIEN_KET = "content call --uri content://$GOI --method huy-lien-ket"

    /**
     * Trạng thái app phụ (content call … --method doc). [biChe] = tivi có máy Cast / DIAL của hãng trả lời trước nên app
     * YouTube không thấy tivi qua Wi-Fi (Skyworth Google TV, 28/10/2026): phải liên kết bằng mã TV. [coQuanLy] = app Cast
     * 2.0 trở lên (danh sách điện thoại, tạm ngừng nhận, huỷ mọi liên kết).
     */
    data class TrangThai(
        val dichVuChay: Boolean, val ten: String, val cong: Int, val daNoi: Boolean, val dienThoai: Int, val dangPhat: String,
        val nhatKy: List<NhatKy>, val biChe: Boolean = false,
        val coQuanLy: Boolean = false, val nhanCast: Boolean = true, val dsMay: List<May> = emptyList(), val dangNoi: Set<String> = emptySet(),
    )

    fun trangThai(j: JSONObject): TrangThai {
        val nk = j.optJSONArray("nhatKy")?.let { a ->
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { NhatKy(it.optLong("luc"), it.optString("viec")) }
        }.orEmpty()
        val may = j.optJSONArray("dienThoai")?.let { a ->
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }.filter { it.optString("id").isNotEmpty() }.map {
                May(it.optString("id"), it.optString("ten"), it.optString("taiKhoan"), it.optString("trangThai"),
                    it.optLong("hetHan"), it.optLong("lanDau"), it.optLong("lanCuoi"))
            }
        }
        val noi = j.optJSONArray("dangNoi")?.let { a -> (0 until a.length()).map { a.optString(it) }.toSet() }.orEmpty()
        return TrangThai(j.optBoolean("dichVuChay"), j.optString("ten"), j.optInt("cong"), j.optBoolean("daNoi"),
            j.optInt("dienThoai"), j.optString("dangPhat"), nk, j.optBoolean("biChe"),
            coQuanLy = may != null, nhanCast = j.optBoolean("nhanCast", true), dsMay = may.orEmpty(), dangNoi = noi)
    }
}
