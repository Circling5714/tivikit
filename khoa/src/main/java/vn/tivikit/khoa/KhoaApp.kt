package vn.tivikit.khoa

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * Luật khoá theo app (28/10/2026): các gói [goi] khoá luôn ([luon]), trong [khung], hay khi cả nhóm đã mở đủ [phutNgay]
 * phút hôm nay (đếm riêng từng luật theo [id]).
 */
data class LuatApp(val id: String, val goi: Set<String>, val luon: Boolean = false, val khung: List<Khung> = emptyList(), val phutNgay: Int = 0)

/** Hẹn khoá một lần: [goi] khoá từ [tu] tới [den] (mili giây), VD "khoá SmartTube sau 30 phút, tới hết ngày". */
data class HenKhoa(val goi: Set<String>, val tu: Long, val den: Long)

/** Khoá theo app: luật thuần, không đụng Android để test được trên máy tính. */
object KhoaApp {
    /** Vì sao app đang khoá: hẹn giờ một lần, khoá luôn, trong khung giờ của app, hay nhóm đã dùng hết phút hôm nay. */
    enum class LyDo { HEN, LUON, GIO_KHOA, HET_GIO }

    /** Đang khoá vì [lyDo], mở lại lúc [den] (null = tới khi bố mẹ đổi luật). */
    data class Khoa(val lyDo: LyDo, val den: Long?, val phut: Int = 0)

    private fun phutCua(c: Calendar) = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)

    /** Nửa đêm kế tiếp sau [ms] (giờ tivi): hẹn khoá "tới hết ngày" và hết phút trong ngày tính tới đó. */
    fun nuaDem(ms: Long): Long = Calendar.getInstance().apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_MONTH, 1)
    }.timeInMillis

    /**
     * App [goi] lúc [luc] có khoá không; null = không. Chỉ khoá khi Khoá trẻ em đang bật và đã có mã (không thì không ai
     * mở được); bố mẹ đã nhập mã mở riêng app này tới [moDen] thì thôi. [giayDung]: số giây luật (theo id) đã dùng hôm nay.
     */
    fun khoa(c: CauHinh?, hen: List<HenKhoa>, goi: String, luc: Calendar, giayDung: (String) -> Long, moDen: Long = 0): Khoa? {
        if (c == null || !c.bat || !c.coMa) return null
        val ms = luc.timeInMillis
        if (ms < moDen) return null
        hen.filter { goi in it.goi && ms >= it.tu && ms < it.den }.maxByOrNull { it.den }?.let { return Khoa(LyDo.HEN, it.den) }
        val ds = c.apps.filter { goi in it.goi }
        if (ds.any { it.luon }) return Khoa(LyDo.LUON, null)
        val ngay = Luat.thu(luc); val p = phutCua(luc)
        if (ds.any { l -> l.khung.any { Luat.trongKhung(it, ngay, p) } }) return Khoa(LyDo.GIO_KHOA, hetKhung(ds, luc))
        ds.firstOrNull { it.phutNgay > 0 && giayDung(it.id) >= it.phutNgay * 60L }?.let { return Khoa(LyDo.HET_GIO, nuaDem(ms), it.phutNgay) }
        return null
    }

    /** Lúc khung giờ khoá của các luật [ds] đang chạy kết thúc (dò từng phút, tối đa 8 ngày). */
    private fun hetKhung(ds: List<LuatApp>, luc: Calendar): Long? {
        val x = (luc.clone() as Calendar).apply { set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        repeat(8 * 24 * 60) {
            x.add(Calendar.MINUTE, 1)
            val ngay = Luat.thu(x); val p = phutCua(x)
            if (ds.none { l -> l.khung.any { Luat.trongKhung(it, ngay, p) } }) return x.timeInMillis
        }
        return null
    }

    /**
     * Cứ dùng tiếp app [goi] thì còn mấy phút (1..[truoc]) nữa nó khoá, để báo trước; null nếu [truoc] phút tới không khoá.
     * Phút dùng của luật đang chạy cộng dần theo thời gian (đang mở app đó).
     */
    fun sapKhoa(c: CauHinh, hen: List<HenKhoa>, goi: String, bayGio: Calendar, giayDung: (String) -> Long, moDen: Long = 0, truoc: Int = 5): Int? {
        if (khoa(c, hen, goi, bayGio, giayDung, moDen) != null) return null
        val homNay = Luat.ngay(bayGio)
        for (m in 1..truoc) {
            val x = (bayGio.clone() as Calendar).apply { add(Calendar.MINUTE, m) }
            // Qua nửa đêm thì ngày mới đếm lại từ 0.
            val dung: (String) -> Long = if (Luat.ngay(x) == homNay) { id -> giayDung(id) + m * 60L } else { _ -> 0L }
            if (khoa(c, hen, goi, x, dung, moDen) != null) return m
        }
        return null
    }

    /** Các gói có nhắc tới trong luật / hẹn khoá (để biết app nào đang khoá). */
    fun goiCo(c: CauHinh?, hen: List<HenKhoa>): Set<String> = (c?.apps.orEmpty().flatMap { it.goi } + hen.flatMap { it.goi }).toSet()

    /** Luật mà app [goi] đang mở được đếm giờ (luật có giới hạn phút mỗi ngày). */
    fun luatDem(c: CauHinh?, goi: String): List<String> = c?.apps.orEmpty().filter { goi in it.goi && it.phutNgay > 0 }.map { it.id }

    /** Bỏ hẹn khoá đã hết. */
    fun conHan(hen: List<HenKhoa>, ms: Long): List<HenKhoa> = hen.filter { it.den > ms }

    // ---------- JSON ----------

    private val TEN_GOI = Regex("""^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$""")

    /** Danh sách tên gói trong JSON, bỏ chuỗi không phải tên gói. */
    fun dsGoi(a: JSONArray?): Set<String> = if (a == null) emptySet() else (0 until a.length()).map { a.getString(it) }.filter { TEN_GOI.matches(it) }.toSet()

    /** "apps" trong cấu hình TiviKit gửi; luật không có gói nào thì bỏ. */
    fun docLuat(a: JSONArray?): List<LuatApp> = if (a == null) emptyList() else (0 until a.length()).mapNotNull { i ->
        val o = a.getJSONObject(i)
        val ks = o.optJSONArray("khung") ?: JSONArray()
        LuatApp(
            id = o.optString("id").ifBlank { "l$i" },
            goi = dsGoi(o.optJSONArray("goi")),
            luon = o.optBoolean("luon"),
            khung = (0 until ks.length()).map { Luat.docKhung(ks.getJSONObject(it)) },
            phutNgay = o.optInt("phutNgay", 0).coerceIn(0, 24 * 60),
        ).takeIf { it.goi.isNotEmpty() }
    }

    fun docHen(s: String?): List<HenKhoa> = runCatching {
        val a = JSONArray(s ?: return emptyList())
        (0 until a.length()).map { val o = a.getJSONObject(it); HenKhoa(dsGoi(o.optJSONArray("goi")), o.getLong("tu"), o.getLong("den")) }.filter { it.goi.isNotEmpty() }
    }.getOrDefault(emptyList())

    fun jsonHen(hen: List<HenKhoa>): JSONArray = JSONArray(hen.map { JSONObject().put("goi", JSONArray(it.goi.sorted())).put("tu", it.tu).put("den", it.den) })
}
