package vn.tivikit.dulieu

import org.json.JSONArray
import org.json.JSONObject

/**
 * Kênh nhanh (25/10/2026, muốn một nút mở thẳng VTV1 trên TV360; cùng ngày mở rộng thành nhiều ô): mỗi ô kênh là một
 * app phụ nhỏ trên tivi (module kenh/, flavor o1 … o6) gửi lệnh chuyển kênh cho TV360 rồi tự đóng. Mỗi ô một app vì
 * Home sạch mở app và gán nút remote theo tên gói. Kênh chọn theo số kênh TV360 hiện trong danh sách kênh
 * (shortcutKeyboard, 3 chữ số), tên dự phòng khi TV360 đổi số. Ô trên Home sạch mang tên kênh qua app_labels.
 */
object KenhNhanh {
    /** Số ô kênh tối đa (số flavor của module kenh/). */
    const val SO_O = 6
    /** Gói của ô 1 (giữ tên của bản một ô trước đây); ô n là GOI + n. */
    const val GOI = "vn.tivikit.kenh"
    const val TV360 = "com.viettel.tv360.tv"

    fun goiCua(o: Int): String = if (o == 1) GOI else "$GOI$o"
    /** Màn mở kênh của ô [o] (bấm ô / nút remote cũng vào đây). */
    fun moCua(o: Int): String = "${goiCua(o)}/vn.tivikit.kenh.MoKenh"
    /** File APK của ô [o] trong assets. */
    fun apkCua(o: Int): String = if (o == 1) "kenh.apk" else "kenh$o.apk"
    /** Số ô của gói [p], null nếu không phải ô Kênh nhanh. */
    fun oCua(p: String): Int? = (1..SO_O).firstOrNull { goiCua(it) == p }
    fun laGoiKenh(p: String): Boolean = oCua(p) != null

    data class Kenh(val so: String, val ten: String)

    /** Kênh hay xem, số theo danh sách kênh trên tv360.vn (25/10/2026). */
    val GOI_Y = listOf(
        Kenh("001", "VTV1"), Kenh("002", "VTV2"), Kenh("003", "VTV3"), Kenh("004", "VTV4"), Kenh("005", "VTV5"),
        Kenh("052", "VTV6"), Kenh("009", "VTV7"), Kenh("010", "VTV8"), Kenh("011", "VTV9"), Kenh("006", "VTV10"),
        Kenh("073", "HTV7 HD"), Kenh("074", "HTV9 HD"), Kenh("098", "Hà Nội 1"), Kenh("012", "ANTV"), Kenh("008", "QPVN HD"),
    )

    /** "1" / "01" / "001" → "001" (dạng số kênh của TV360); ngoài 1..999 → null. */
    fun chuanSo(s: String?): String? = s?.trim()?.toIntOrNull()?.takeIf { it in 1..999 }?.toString()?.padStart(3, '0')

    /**
     * Tên kênh dùng được ở mọi chỗ: bỏ dấu phẩy và xuống dòng (app_labels ở Nâng cao tách mục bằng dấu phẩy), nháy và
     * ký tự shell (đi trong lệnh content call bọc nháy đơn), "=" (dấu tách gói=tên).
     */
    fun tenSach(s: String): String = s.replace(Regex("[,'\"`\$\\\\=\\r\\n]"), " ").trim().replace(Regex("\\s+"), " ").take(30)

    /** Cấu hình gửi cho app phụ (content call … --method dat). */
    fun json(k: Kenh): String = JSONObject().put("so", k.so).put("ten", k.ten).toString()

    fun tuJson(j: JSONObject?): Kenh? {
        val so = chuanSo(j?.optString("so")) ?: return null
        return Kenh(so, j!!.optString("ten").trim())
    }

    private fun chu(a: JSONArray?): List<String> = if (a == null) emptyList()
        else (0 until a.length()).mapNotNull { a.opt(it) as? String }.map { it.trim() }.filter { it.isNotEmpty() }

    /** Tên các ô Kênh nhanh trong app_labels của config.json ("vn.tivikit.kenh3=VTV3"): gói → tên. */
    fun tenTrong(j: JSONObject): Map<String, String> = chu(j.optJSONArray("app_labels")).mapNotNull { m ->
        val goi = m.substringBefore('=').trim(); val ten = m.substringAfter('=', "").trim()
        if (laGoiKenh(goi) && ten.isNotEmpty()) goi to ten else null
    }.toMap()

    /** Đặt / bỏ ([ten] null) tên ô [goi] trong app_labels, giữ nguyên tên người dùng đặt cho app khác. */
    fun datTen(j: JSONObject, goi: String, ten: String?): JSONObject {
        val giu = chu(j.optJSONArray("app_labels")).filter { it.substringBefore('=').trim() != goi }
        val ds = if (ten.isNullOrBlank()) giu else giu + "$goi=$ten"
        if (ds.isEmpty()) j.remove("app_labels") else j.put("app_labels", JSONArray(ds))
        return j
    }

    /**
     * Thêm (cuối dock, có rồi thì giữ chỗ) / bỏ ô [goi] trong dock_apps. true = xong, false = dock đã đủ [toiDa] ô,
     * null = config.json không có dock_apps (Home sạch đang dùng dock mặc định, ghi vào là mất dock đó).
     */
    fun datDock(j: JSONObject, goi: String, trongDock: Boolean, toiDa: Int): Boolean? {
        val ds = chu(j.optJSONArray("dock_apps") ?: return null)
        val moi = when {
            trongDock && goi in ds -> ds
            trongDock -> if (ds.size >= toiDa) return false else ds + goi
            else -> ds - goi
        }
        j.put("dock_apps", JSONArray(moi))
        return true
    }

    /** Các gói có trong dock_apps (để biết ô nào đang ở dock). */
    fun trongDock(j: JSONObject): Set<String> = chu(j.optJSONArray("dock_apps")).toSet()
}
