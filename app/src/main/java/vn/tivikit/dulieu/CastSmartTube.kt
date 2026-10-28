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

    data class NhatKy(val luc: Long, val viec: String)

    /** Trạng thái app phụ (content call … --method doc). */
    data class TrangThai(
        val dichVuChay: Boolean, val ten: String, val cong: Int, val daNoi: Boolean, val dienThoai: Int, val dangPhat: String,
        val nhatKy: List<NhatKy>,
    )

    fun trangThai(j: JSONObject): TrangThai {
        val nk = j.optJSONArray("nhatKy")?.let { a ->
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { NhatKy(it.optLong("luc"), it.optString("viec")) }
        }.orEmpty()
        return TrangThai(j.optBoolean("dichVuChay"), j.optString("ten"), j.optInt("cong"), j.optBoolean("daNoi"),
            j.optInt("dienThoai"), j.optString("dangPhat"), nk)
    }
}
