package vn.tivikit.cast

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Trạng thái lưu trên tivi: mã thiết bị DIAL, screen YouTube, tên tự đặt, nhật ký vài việc gần nhất. */
class Kho(ctx: Context) {
    private val p = ctx.applicationContext.getSharedPreferences("cast", Context.MODE_PRIVATE)

    private fun maCo(khoa: String): String =
        p.getString(khoa, null) ?: UUID.randomUUID().toString().also { p.edit().putString(khoa, it).apply() }

    /** UUID của thiết bị DIAL (UDN): giữ nguyên để điện thoại nhận ra đúng tivi cũ. */
    val uuid: String get() = maCo("uuid")

    /** Mã thiết bị của screen bên YouTube (tham số id của kênh Lounge). */
    val maThietBi: String get() = maCo("ma_thiet_bi")

    /** Screen YouTube cấp (generate_screen_id); null = chưa có, xin mới. */
    var screenId: String?
        get() = p.getString("screen_id", null)
        set(v) { p.edit().putString("screen_id", v).apply() }

    /** Tên hiện trong nút Cast do TiviKit đặt; null = tự đặt theo tên tivi. */
    var ten: String?
        get() = p.getString("ten", null)
        set(v) { p.edit().putString("ten", v).apply() }

    /** Lần dò trước thấy máy DIAL của hãng cùng IP trả lời trước (Dial.biChe): bật máy là im lặng trên SSDP ngay. */
    var biChe: Boolean
        get() = p.getBoolean("bi_che", false)
        set(v) { p.edit().putBoolean("bi_che", v).apply() }

    /** Điện thoại đã nối vào tivi: trạng thái duyệt / chặn / hạn khách (DienThoai, DanhBa). */
    var dienThoai: List<DienThoai>
        @Synchronized get() = DanhBa.tuJson(p.getString("dien_thoai", null))
        @Synchronized set(v) { p.edit().putString("dien_thoai", DanhBa.json(v)).apply() }

    /** Đổi danh sách điện thoại một lần (đọc, đổi, ghi liền nhau). */
    @Synchronized fun doiDienThoai(f: (List<DienThoai>) -> List<DienThoai>): List<DienThoai> = f(dienThoai).also { dienThoai = it }

    /**
     * Tên máy đã từng cast trước bản 2.0 (DanhBa.tenTuNhatKy, lấy một lần lúc lên bản 2.0); null = chưa lấy. Máy có tên này
     * nối lần đầu thì được phép luôn, rồi bỏ tên khỏi đây.
     */
    var tenMayCu: Set<String>?
        get() = p.getStringSet("ten_may_cu", null)
        set(v) { p.edit().putStringSet("ten_may_cu", v).apply() }

    /** Đang nhận cast (tắt thì tivi bỏ qua mọi video điện thoại gửi, vẫn giữ liên kết). */
    var nhanCast: Boolean
        get() = p.getBoolean("nhan_cast", true)
        set(v) { p.edit().putBoolean("nhan_cast", v).apply() }

    /** 30 dòng gần nhất {luc, viec}: TiviKit đọc để biết điện thoại nào đã nối, video nào đã mở. */
    val nhatKy: JSONArray get() = runCatching { JSONArray(p.getString("nhat_ky", "[]")) }.getOrDefault(JSONArray())

    @Synchronized fun ghi(viec: String) {
        val a = nhatKy
        a.put(JSONObject().put("luc", System.currentTimeMillis()).put("viec", viec))
        val bot = JSONArray(); for (i in maxOf(0, a.length() - 30) until a.length()) bot.put(a.get(i))
        p.edit().putString("nhat_ky", bot.toString()).apply()
    }
}
