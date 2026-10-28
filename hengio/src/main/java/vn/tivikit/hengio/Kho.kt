package vn.tivikit.hengio

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

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

    /** Việc một lần còn hạn, đưa vào Lich.keTiep. */
    fun motLan(): List<Pair<Long, Viec>> = listOfNotNull(
        hoanDen.takeIf { it > 0 }?.let { it to Viec(0, 0, (1..7).toSet(), Viec.TAT) },
        thuDen.takeIf { it > 0 }?.let { it to Viec(0, 0, (1..7).toSet(), Viec.BAT) },
    )

    /** Bỏ các việc một lần đã tới lúc [luc]. */
    fun xongMotLan(luc: Long) {
        if (hoanDen in 1..luc) hoanDen = 0
        if (thuDen in 1..luc) thuDen = 0
    }

    /** 20 dòng gần nhất: {luc, viec, kq} để TiviKit xem lần chạy có được không. */
    val nhatKy: JSONArray get() = runCatching { JSONArray(p.getString("nhat_ky", "[]")) }.getOrDefault(JSONArray())

    fun ghi(viec: String, kq: String) {
        val a = nhatKy
        a.put(JSONObject().put("luc", System.currentTimeMillis()).put("viec", viec).put("kq", kq))
        val bot = JSONArray(); for (i in maxOf(0, a.length() - 20) until a.length()) bot.put(a.get(i))
        p.edit().putString("nhat_ky", bot.toString()).apply()
    }
}
