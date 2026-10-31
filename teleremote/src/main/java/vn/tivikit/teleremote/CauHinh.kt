package vn.tivikit.teleremote

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Vai trò người dùng bot: chủ (mọi lệnh: tắt / khởi động lại tivi, Hẹn giờ, Khoá trẻ em, Làm mới, chụp màn hình) / thành
 * viên (điều khiển, gõ chữ, Kênh nhanh, YouTube, ứng dụng, nhắn như nói, gửi link / ảnh / video, xem tivi).
 */
enum class VaiTro(val ma: String) {
    CHU("chu"), THANH_VIEN("thanh-vien");

    companion object {
        fun tuMa(s: String?): VaiTro = if (s == CHU.ma) CHU else THANH_VIEN
    }
}

/**
 * Người được dùng bot. [ten] = tên Telegram dạng @username (chủ tivi nhập ở TiviKit; "" nếu duyệt người không đặt
 * username), [id] = ID số Telegram của người đó (0 = chưa nhắn bot lần nào), [tenHien] = tên hiện trên Telegram.
 * Khớp theo [id] khi đã có; chưa có thì theo [ten]. Lần đầu khớp theo [ten] thì ghi lại [id] và từ đó chỉ tin [id]: đổi
 * username vẫn dùng được, người khác lấy lại username cũ cũng không vào được.
 */
data class NguoiDung(val ten: String, val id: Long, val vaiTro: VaiTro, val tenHien: String = "")

/** Người lạ vừa nhắn bot (chưa có trong danh sách): TiviKit hiện "chờ duyệt". */
data class XinDung(val id: Long, val ten: String, val tenHien: String, val luc: Long)

/** Một việc bot đã làm, cho TiviKit xem lại: [ai] = @username / tên hiện. */
data class NhatKy(val luc: Long, val ai: String, val viec: String)

/**
 * Cấu hình TiviKit gửi qua ADB (content call … --method dat). [bat] = tạm tắt bot (không hỏi Telegram), [nguoiDung] =
 * người được dùng, [chupManHinh] = cho chủ chụp màn hình tivi gửi qua Telegram (tắt sẵn), [baoTin] = bot nhắn các chủ khi
 * tivi vừa bật / vừa tắt (tắt sẵn), [tiengAnh] = bot nói tiếng Anh (theo ngôn ngữ của TiviKit lúc cài). Token của bot gửi
 * riêng (lệnh token), không nằm trong cấu hình.
 */
data class CauHinh(
    val bat: Boolean = true,
    val nguoiDung: List<NguoiDung> = emptyList(),
    val chupManHinh: Boolean = false,
    val tiengAnh: Boolean = false,
    val baoTin: Boolean = false,
) {
    fun json(): JSONObject = JSONObject().put("bat", bat).put("chupManHinh", chupManHinh).put("tiengAnh", tiengAnh).put("baoTin", baoTin)
        .put("nguoiDung", JSONArray().apply {
            nguoiDung.forEach { put(JSONObject().put("ten", it.ten).put("id", it.id).put("vaiTro", it.vaiTro.ma).put("tenHien", it.tenHien)) }
        })

    companion object {
        const val NGUOI_TOI_DA = 20
        /** @username của Telegram: chữ, số, gạch dưới (tên mua ở Fragment có thể chỉ 4 ký tự). */
        val TEN = Regex("@[A-Za-z0-9_]{4,32}")

        fun tuJson(s: String?): CauHinh? {
            val j = runCatching { JSONObject(s ?: "") }.getOrNull() ?: return null
            val a = j.optJSONArray("nguoiDung") ?: JSONArray()
            val ds = mutableListOf<NguoiDung>()
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                val ten = o.optString("ten").trim()
                val id = o.optLong("id")
                if (!(TEN.matches(ten) || (ten.isEmpty() && id > 0)) || id < 0) continue
                // Trùng người (cùng ID, hay cùng username chưa có ID) thì giữ mục đầu.
                if (ds.any { (id > 0 && it.id == id) || (ten.isNotEmpty() && it.ten.equals(ten, ignoreCase = true)) }) continue
                ds += NguoiDung(ten, id, VaiTro.tuMa(o.optString("vaiTro")), o.optString("tenHien").trim().take(64))
                if (ds.size >= NGUOI_TOI_DA) break
            }
            return CauHinh(j.optBoolean("bat", true), ds, j.optBoolean("chupManHinh", false), j.optBoolean("tiengAnh", false), j.optBoolean("baoTin", false))
        }
    }
}

/** Token, cấu hình, người xin dùng và nhật ký, lưu trên tivi (riêng app, không sao lưu). */
class Kho(ctx: Context) {
    private val p = ctx.applicationContext.getSharedPreferences("tele_remote", Context.MODE_PRIVATE)

    /** Token của bot (từ @BotFather); "" = chưa có. */
    var token: String
        get() = p.getString("token", null).orEmpty()
        set(v) { p.edit().putString("token", v).apply() }

    /** Username của bot (getMe), không có @. */
    var bot: String
        get() = p.getString("bot", null).orEmpty()
        set(v) { p.edit().putString("bot", v).apply() }

    var cauHinh: CauHinh
        get() = CauHinh.tuJson(p.getString("cau_hinh", null)) ?: CauHinh()
        set(v) { p.edit().putString("cau_hinh", v.json().toString()).apply() }

    val cho: List<XinDung>
        get() = runCatching {
            val a = JSONArray(p.getString("cho", "[]"))
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }
                .map { XinDung(it.optLong("id"), it.optString("ten"), it.optString("tenHien"), it.optLong("luc")) }
        }.getOrDefault(emptyList())

    /** Thêm người lạ vào danh sách chờ duyệt (giữ [CHO_TOI_DA] người mới nhất); false nếu người này đã đang chờ. */
    @Synchronized
    fun themCho(x: XinDung): Boolean {
        val cu = cho
        if (cu.any { it.id == x.id }) return false
        luuCho((cu + x).takeLast(CHO_TOI_DA))
        return true
    }

    @Synchronized
    fun xoaCho(id: Long) = luuCho(cho.filter { it.id != id })

    private fun luuCho(ds: List<XinDung>) {
        val a = JSONArray()
        ds.forEach { a.put(JSONObject().put("id", it.id).put("ten", it.ten).put("tenHien", it.tenHien).put("luc", it.luc)) }
        p.edit().putString("cho", a.toString()).apply()
    }

    val nhatKy: List<NhatKy>
        get() = runCatching {
            val a = JSONArray(p.getString("nhat_ky", "[]"))
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { NhatKy(it.optLong("luc"), it.optString("ai"), it.optString("viec")) }
        }.getOrDefault(emptyList())

    /** Ghi một dòng nhật ký, giữ [GIU] dòng mới nhất. */
    @Synchronized
    fun ghi(ai: String, viec: String) {
        val ds = (nhatKy + NhatKy(System.currentTimeMillis(), ai.take(64), viec.take(120))).takeLast(GIU)
        val a = JSONArray()
        ds.forEach { a.put(JSONObject().put("luc", it.luc).put("ai", it.ai).put("viec", it.viec)) }
        p.edit().putString("nhat_ky", a.toString()).apply()
    }

    /** Chủ tạm khoá thành viên tới lúc này (ms); 0 = không khoá. Ngoài cấu hình: TiviKit gửi cấu hình không đụng tới nó. */
    var khoaThanhVienDen: Long
        get() = p.getLong("khoa_thanh_vien_den", 0)
        set(v) { p.edit().putLong("khoa_thanh_vien_den", v).apply() }

    companion object {
        const val CHO_TOI_DA = 10
        const val GIU = 40
    }
}
