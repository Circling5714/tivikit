package vn.tivikit.mapvoice

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Câu tìm nhạc / video mở thế nào: theo câu nói ("mở / phát / bật …" thì phát luôn video đầu, còn lại trang tìm kiếm), luôn phát, luôn tìm. */
enum class CachMo(val ma: String) {
    THEO_CAU("theo-cau"), PHAT_LUON("phat-luon"), TIM_KIEM("tim-kiem");

    companion object {
        fun tuMa(s: String?): CachMo = entries.firstOrNull { it.ma == s } ?: THEO_CAU
    }
}

/**
 * Cấu hình TiviKit gửi qua ADB (content call … --method dat). [appYouTube] = gói app mở YouTube ("" = SmartTube đầu tiên có
 * trên tivi). [nhacVideo] = câu tìm nhạc, video không nói "YouTube" cũng chuyển. [kenh] = bắt câu mở kênh, mở bằng [appKenh]
 * ("vtvgo" / "tv360"; app đó không có kênh thì dùng app kia).
 */
data class CauHinh(
    val bat: Boolean = true,
    val appYouTube: String = "",
    val cachMo: CachMo = CachMo.THEO_CAU,
    val nhacVideo: Boolean = true,
    val kenh: Boolean = true,
    val appKenh: String = APP_VTVGO,
) {
    fun json(): JSONObject = JSONObject().put("bat", bat).put("appYouTube", appYouTube).put("cachMo", cachMo.ma)
        .put("nhacVideo", nhacVideo).put("kenh", kenh).put("appKenh", appKenh)

    companion object {
        const val APP_VTVGO = "vtvgo"
        const val APP_TV360 = "tv360"
        private val GOI = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")

        fun tuJson(s: String?): CauHinh? {
            val j = runCatching { JSONObject(s ?: "") }.getOrNull() ?: return null
            val app = j.optString("appYouTube").trim()
            return CauHinh(
                bat = j.optBoolean("bat", true),
                appYouTube = if (GOI.matches(app)) app else "",
                cachMo = CachMo.tuMa(j.optString("cachMo")),
                nhacVideo = j.optBoolean("nhacVideo", true),
                kenh = j.optBoolean("kenh", true),
                appKenh = if (j.optString("appKenh") == APP_TV360) APP_TV360 else APP_VTVGO,
            )
        }
    }
}

/**
 * Một kênh truyền hình Map Voice biết: [ma] = mã kênh của VTVgo (vtvgo://play), [so] = số kênh của TV360 (3 chữ số); thiếu
 * cái nào là app đó không biết kênh này (TV360 vẫn tìm được theo tên). [khoa] = các cách gọi đã gọn (XuLyCau.khoaKenh).
 * [ten360] = tên gửi cho TV360 (tên trong danh sách của TV360 nếu có).
 */
data class KenhTv(val ten: String, val ma: String, val so: String, val khoa: Set<String>, val ten360: String = ten)

/** Một câu vừa nghe và việc đã làm, cho TiviKit xem lại. */
data class NhatKy(val luc: Long, val cau: String, val viec: String)

/** Cấu hình, danh sách kênh và nhật ký, lưu trên tivi. */
class Kho(ctx: Context) {
    private val p = ctx.applicationContext.getSharedPreferences("map_voice", Context.MODE_PRIVATE)

    var cauHinh: CauHinh
        get() = CauHinh.tuJson(p.getString("cau_hinh", null)) ?: CauHinh()
        set(v) { p.edit().putString("cau_hinh", v.json().toString()).apply() }

    /** Danh sách kênh dạng TiviKit gửi ({"vtvgo":[…],"tv360":[…]}). */
    var kenhJson: String
        get() = p.getString("kenh", null) ?: ""
        set(v) { p.edit().putString("kenh", v).apply() }

    /**
     * Danh sách kênh đã đọc. Nhớ chung cả app theo đúng chuỗi JSON: dịch vụ và CauHinhProvider mỗi bên một Kho, nhớ riêng thì
     * dịch vụ không thấy danh sách TiviKit vừa gửi (thấy trên TX3: "Xem" mở được VTV3, "Chạy" lại bỏ qua).
     */
    val dsKenh: List<KenhTv>
        get() {
            val j = kenhJson
            dsKenhNho?.let { (cu, ds) -> if (cu == j) return ds }
            return XuLyCau.dsKenh(j).also { dsKenhNho = j to it }
        }

    val nhatKy: List<NhatKy>
        get() = runCatching {
            val a = JSONArray(p.getString("nhat_ky", "[]"))
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { NhatKy(it.optLong("luc"), it.optString("cau"), it.optString("viec")) }
        }.getOrDefault(emptyList())

    /** Ghi một dòng nhật ký, giữ [GIU] dòng mới nhất (câu nói là chuyện riêng: không giữ lâu). */
    @Synchronized
    fun ghi(cau: String, viec: String) {
        val ds = (nhatKy + NhatKy(System.currentTimeMillis(), cau.take(200), viec.take(200))).takeLast(GIU)
        val a = JSONArray()
        ds.forEach { a.put(JSONObject().put("luc", it.luc).put("cau", it.cau).put("viec", it.viec)) }
        p.edit().putString("nhat_ky", a.toString()).apply()
    }

    companion object {
        const val GIU = 15
        @Volatile private var dsKenhNho: Pair<String, List<KenhTv>>? = null
    }
}
