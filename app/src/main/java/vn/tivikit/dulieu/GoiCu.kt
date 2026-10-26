package vn.tivikit.dulieu

import org.json.JSONArray
import org.json.JSONObject

/**
 * Tên gói trước khi TiviKit đổi sang vn.tivikit (25/10/2026). App phụ cài từ bản cũ (com.icm.cleantv.*) vẫn chạy trên
 * tivi nhưng bản mới không nhận là của mình: lúc nối tivi, ô Kênh nhanh và lịch Hẹn giờ được chép sang gói mới (tên ô,
 * dock, nút remote đã gán trong config.json Home sạch đổi theo) rồi gỡ bản cũ. Khoá trẻ em thì chờ phụ huynh đặt lại
 * PIN ở màn Khoá trẻ em: tivi không cho đọc PIN, kể cả dạng đã mã hoá.
 */
object GoiCu {
    /** App chính bản cũ (trên điện thoại, hay trên tivi khi chạy TiviKit ngay trên tivi). */
    const val APP = "com.icm.cleantv"
    const val KHOA = "$APP.khoa"
    const val HEN_GIO = "$APP.hengio"

    /**
     * Tên lớp dịch vụ trợ năng trong app phụ dựng trước khi mã nguồn chuyển từ zone.vgs.* sang vn.tivikit.* (25/10/2026):
     * mọi bản com.icm.cleantv.* và bản vn.tivikit.* đầu tiên. Bật / tắt dịch vụ trên tivi phải gọi đúng tên lớp đang cài.
     */
    const val LOP_KHOA_CU = "zone.vgs.khoa.KhoaDichVu"
    const val LOP_HEN_GIO_CU = "zone.vgs.hengio.HenGioDichVu"
    const val DICH_VU_KHOA = "$KHOA/$LOP_KHOA_CU"
    const val DICH_VU_HEN_GIO = "$HEN_GIO/$LOP_HEN_GIO_CU"

    /** Gói cũ của ô Kênh nhanh [o]. */
    fun kenh(o: Int): String = if (o == 1) "$APP.kenh" else "$APP.kenh$o"

    /** Mọi app phụ bản cũ. */
    val APP_PHU: List<String> = (1..KenhNhanh.SO_O).map { kenh(it) } + HEN_GIO + KHOA

    /** Thay gói cũ bằng gói mới ([doi]: cũ → mới) trong một chuỗi; chỉ thay nguyên tên gói ("scan:490:<gói>", "<gói>=VTV1", "<gói>/<màn>"). */
    fun doiChu(s: String, doi: Map<String, String>): String {
        if (doi.isEmpty()) return s
        // Không dính chữ / dấu chấm hai bên: com.icm.cleantv.kenh không khớp trong …kenh3 hay org.com.icm.cleantv.kenh.
        val re = Regex("(?<![A-Za-z0-9_.])(" + doi.keys.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) } + ")(?![A-Za-z0-9_.])")
        return re.replace(s) { doi.getValue(it.value) }
    }

    /**
     * Thay gói cũ bằng gói mới trong mọi chuỗi của config.json Home sạch (app_keys, vgs_phim_rieng, app_labels,
     * dock_apps, app ẩn…), bỏ mục trùng trong danh sách vừa đổi (ô mới có sẵn trong dock). Sửa tại chỗ; true nếu có đổi.
     */
    fun doiTrongCauHinh(j: JSONObject, doi: Map<String, String>): Boolean {
        var co = false
        for (k in j.keys().asSequence().toList()) {
            when (val v = j.opt(k)) {
                is String -> doiChu(v, doi).let { if (it != v) { j.put(k, it); co = true } }
                is JSONArray -> doiMang(v, doi)?.let { j.put(k, it); co = true }
                is JSONObject -> if (doiTrongCauHinh(v, doi)) co = true
            }
        }
        return co
    }

    /** Mảng đã đổi (bỏ chuỗi trùng, giữ mục đầu); null nếu không có gì đổi. */
    private fun doiMang(a: JSONArray, doi: Map<String, String>): JSONArray? {
        var co = false
        val ra = (0 until a.length()).map { i ->
            when (val x = a.opt(i)) {
                is String -> doiChu(x, doi).also { if (it != x) co = true }
                is JSONObject -> x.also { if (doiTrongCauHinh(it, doi)) co = true }
                is JSONArray -> doiMang(x, doi)?.also { co = true } ?: x
                else -> x
            }
        }
        if (!co) return null
        val daCo = mutableSetOf<String>()
        return JSONArray(ra.filter { it !is String || daCo.add(it) })
    }

    /** Lịch Hẹn giờ: việc mở ô kênh cũ thành mở ô mới đã chuyển. */
    fun doiLich(ds: List<HenGio.Viec>, doi: Map<String, String>): List<HenGio.Viec> =
        ds.map { v -> v.goi?.let { doi[it] }?.let { v.copy(goi = it) } ?: v }
}
