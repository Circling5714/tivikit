package vn.tivikit.hengio

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZoneId

/** Trạng thái lưu trên tivi: lịch, việc một lần (hoãn tắt, thử đánh thức), báo thức kế tiếp, nhật ký vài lần chạy gần nhất. */
class Kho(ctx: Context) {
    private val p = ctx.applicationContext.getSharedPreferences("hen_gio", Context.MODE_PRIVATE)

    var lich: List<Viec>
        get() = Lich.docJson(p.getString("lich", null)) ?: emptyList()
        set(v) { p.edit().putString("lich", Lich.json(v).toString()).apply() }

    /** Tắt tivi đã hoãn tới lúc này (ms); 0 = không hoãn. */
    var hoanDen: Long
        get() = p.getLong("hoan_den", 0)
        set(v) { p.edit().putLong("hoan_den", v).apply() }

    /** Thử đánh thức (nút Thử ở TiviKit) lúc này (ms); 0 = không thử. */
    var thuDen: Long
        get() = p.getLong("thu_den", 0)
        set(v) { p.edit().putLong("thu_den", v).apply() }

    /** Giờ tắt (ms) của lần tắt gần nhất đã báo trước: không báo hai lần cho cùng một lần tắt (Lich.tatLoBao). */
    var daBaoTat: Long
        get() = p.getLong("da_bao_tat", 0)
        set(v) { p.edit().putLong("da_bao_tat", v).apply() }

    /** Báo thức đang đặt (ms); 0 = không có việc nào. */
    var keTiep: Long
        get() = p.getLong("ke_tiep", 0)
        set(v) { p.edit().putLong("ke_tiep", v).apply() }

    /** Việc một lần đang chờ (hẹn tắt sau X phút, việc một lần lúc HH:MM), theo giờ chạy. */
    var motLanDs: List<MotLan>
        get() = Lich.docMotLanDs(p.getString("mot_lan", null)).sortedBy { it.luc }
        set(v) { p.edit().putString("mot_lan", Lich.jsonMotLan(v).toString()).apply() }

    /** Tên app TiviKit gửi (ô Kênh nhanh mang tên kênh): màn tivi hiện thay cho tên app chung "Kênh nhanh 2". */
    var tenApp: Map<String, String>
        get() = runCatching { JSONObject(p.getString("ten_app", null) ?: "{}").let { o -> o.keys().asSequence().associateWith { o.getString(it) } } }
            .getOrDefault(emptyMap())
        set(v) { p.edit().putString("ten_app", JSONObject(v).toString()).apply() }

    /** Auto Reboot (2/11/2026): giờ, ngày, bật / tắt. */
    var kdl: AutoReboot
        get() = AutoReboot.doc(p.getString("kdl", null)) ?: AutoReboot()
        set(v) { p.edit().putString("kdl", v.json().toString()).apply() }

    /** Giờ hẹn Auto Reboot đang chờ tivi tắt (ms); 0 = không chờ. */
    var kdlCho: Long
        get() = p.getLong("kdl_cho", 0)
        set(v) { p.edit().putLong("kdl_cho", v).apply() }

    /** Lần kiểm lại kế tiếp khi đang chờ tivi tắt (ms). */
    var kdlKiemLuc: Long
        get() = p.getLong("kdl_kiem", 0)
        set(v) { p.edit().putLong("kdl_kiem", v).apply() }

    /** Lúc vừa gửi lệnh khởi động lại (ms); 0 = không. Lên lại trong ít phút thì tắt màn hình (ChayAutoReboot.sauKhoiDong). */
    val kdlVuaLam: Long get() = p.getLong("kdl_vua_lam", 0)

    /** Ghi chắc (commit, không apply): tivi khởi động lại ngay sau khi gửi lệnh, ghi chậm thì mất. */
    fun datCoKhoiDong(luc: Long) { p.edit().putLong("kdl_vua_lam", luc).commit() }

    /** ADB riêng của Hẹn giờ: "" chưa thử, dang-thu, cho-cho-phep (tivi đang hỏi), da-noi, khong-co, chua-cho-phep, loi. */
    var kdlAdb: String
        get() = p.getString("kdl_adb", "").orEmpty()
        set(v) { p.edit().putString("kdl_adb", v).apply() }

    /** Lần Auto Reboot kế tiếp sau [tu]: đang chờ tivi tắt thì lần kiểm lại, không thì giờ hẹn tới; null nếu đang tắt. */
    fun kdlKeTiep(tu: Long, zone: ZoneId): Long? {
        val c = kdl
        if (!c.bat) return null
        if (kdlCho > 0) return kdlKiemLuc.takeIf { it > tu }
        return AutoReboot.lanToi(c, tu, zone)
    }

    /**
     * Việc một lần còn hạn, đưa vào Lich.keTiep: hoãn tắt, thử đánh thức, Auto Reboot, việc một lần (theo giờ báo). Auto
     * Reboot tính từ [tu]: lúc chạy báo thức [tu] là ngay trước giờ báo, nên lần đó vẫn có trong danh sách.
     */
    fun motLan(tu: Long, zone: ZoneId): List<Pair<Long, Viec>> = listOfNotNull(
        hoanDen.takeIf { it > 0 }?.let { it to Viec(0, 0, (1..7).toSet(), Viec.TAT) },
        thuDen.takeIf { it > 0 }?.let { it to Viec(0, 0, (1..7).toSet(), Viec.BAT) },
        kdlKeTiep(tu, zone)?.let { it to Viec(0, 0, (1..7).toSet(), Viec.KDL) },
    ) + motLanDs.map { it.bao to it.viec() }

    /** Bỏ các việc một lần đã tới lúc [luc]. */
    fun xongMotLan(luc: Long) {
        if (hoanDen in 1..luc) hoanDen = 0
        if (thuDen in 1..luc) thuDen = 0
        val ds = motLanDs
        if (ds.any { it.bao <= luc }) motLanDs = ds.filter { it.bao > luc }
    }

    /** 20 dòng gần nhất: {luc, viec, kq} để TiviKit xem lần chạy có được không. */
    val nhatKy: JSONArray get() = runCatching { JSONArray(p.getString("nhat_ky", "[]")) }.getOrDefault(JSONArray())

    /** Ghi một dòng; [chac]: ghi xong mới trả (ngay trước khi tivi khởi động lại). */
    fun ghi(viec: String, kq: String, chac: Boolean = false) {
        val a = nhatKy
        a.put(JSONObject().put("luc", System.currentTimeMillis()).put("viec", viec).put("kq", kq))
        val bot = JSONArray(); for (i in maxOf(0, a.length() - 20) until a.length()) bot.put(a.get(i))
        val e = p.edit().putString("nhat_ky", bot.toString())
        if (chac) e.commit() else e.apply()
    }
}
