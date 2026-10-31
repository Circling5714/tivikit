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
 * Câu riêng (31/10/2026): câu có chữ [chu] thì làm theo [dich], trước mọi cách chuyển khác. [dich]: "tro-ly" = để Trợ lý
 * Google làm, "youtube" = app YouTube đã chọn, tên gói app YouTube (SmartTube, TizenTube, YouTube gốc) = tìm / phát bằng
 * app đó, tên gói app khác = mở app đó.
 */
data class CauRieng(val chu: String, val dich: String)

/**
 * Cấu hình TiviKit gửi qua ADB (content call … --method dat). [appYouTube] = gói app mở YouTube (SmartTube, TizenTube,
 * YouTube gốc; "" = SmartTube đầu tiên có trên tivi). [nhacVideo] = câu tìm nhạc, video không nói "YouTube" cũng chuyển.
 * [kenh] = bắt câu mở kênh, mở bằng [appKenh] ("vtvgo" / "tv360" / "fptplay"; app đó không có kênh thì dùng app khác).
 * [cauRieng] = câu riêng, xét trước hết theo thứ tự.
 */
data class CauHinh(
    val bat: Boolean = true,
    val appYouTube: String = "",
    val cachMo: CachMo = CachMo.THEO_CAU,
    val nhacVideo: Boolean = true,
    val kenh: Boolean = true,
    val appKenh: String = APP_VTVGO,
    val cauRieng: List<CauRieng> = emptyList(),
) {
    fun json(): JSONObject = JSONObject().put("bat", bat).put("appYouTube", appYouTube).put("cachMo", cachMo.ma)
        .put("nhacVideo", nhacVideo).put("kenh", kenh).put("appKenh", appKenh)
        .put("cauRieng", JSONArray().apply { cauRieng.forEach { put(JSONObject().put("chu", it.chu).put("dich", it.dich)) } })

    companion object {
        const val APP_VTVGO = "vtvgo"
        const val APP_TV360 = "tv360"
        const val APP_FPT = "fptplay"
        /** Số câu riêng tối đa, độ dài chữ tối đa của một câu riêng. */
        const val CAU_RIENG_TOI_DA = 30
        const val CHU_TOI_DA = 60
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
                appKenh = j.optString("appKenh").takeIf { it == APP_TV360 || it == APP_FPT } ?: APP_VTVGO,
                cauRieng = cauRieng(j.optJSONArray("cauRieng")),
            )
        }

        /** Câu riêng hợp lệ trong [a]: chữ không rỗng (tối đa [CHU_TOI_DA] ký tự), đích là "tro-ly", "youtube" hay tên gói. */
        private fun cauRieng(a: JSONArray?): List<CauRieng> = if (a == null) emptyList() else (0 until a.length())
            .mapNotNull { a.optJSONObject(it) }
            .mapNotNull { o ->
                val chu = o.optString("chu").trim().replace(Regex("\\s+"), " ")
                val dich = o.optString("dich").trim()
                val dung = dich == XuLyCau.DICH_TRO_LY || dich == XuLyCau.DICH_YOUTUBE || GOI.matches(dich)
                if (chu.isEmpty() || chu.length > CHU_TOI_DA || !dung) null else CauRieng(chu, dich)
            }
            .take(CAU_RIENG_TOI_DA)
    }
}

/**
 * Một kênh truyền hình Map Voice biết: [ma] = mã kênh của VTVgo (vtvgo://play), [so] = số kênh của TV360 (3 chữ số),
 * [fpt] = mã kênh của FPT Play (fptplay.vn/xem-truyen-hinh/<mã>); thiếu cái nào là app đó không biết kênh này (TV360 vẫn
 * tìm được theo tên). [khoa] = các cách gọi đã gọn (XuLyCau.khoaKenh). [ten360] = tên gửi cho TV360 (tên trong danh sách
 * của TV360 nếu có), [tenFpt] = tên kênh trong FPT Play.
 */
data class KenhTv(
    val ten: String, val ma: String, val so: String, val khoa: Set<String>, val ten360: String = ten, val fpt: String = "",
    val tenFpt: String = ten,
)

/** Một câu vừa nghe, việc đã làm và app đã mở ([app] = tên gói, "" nếu không mở app nào), cho TiviKit xem lại. */
data class NhatKy(val luc: Long, val cau: String, val viec: String, val app: String = "")

/** Cấu hình, danh sách kênh và nhật ký, lưu trên tivi. */
class Kho(ctx: Context) {
    private val p = ctx.applicationContext.getSharedPreferences("map_voice", Context.MODE_PRIVATE)

    var cauHinh: CauHinh
        get() = CauHinh.tuJson(p.getString("cau_hinh", null)) ?: CauHinh()
        set(v) { p.edit().putString("cau_hinh", v.json().toString()).apply() }

    /** Danh sách kênh dạng TiviKit gửi ({"vtvgo":[…],"tv360":[…],"fptplay":[…]}). */
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
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }
                .map { NhatKy(it.optLong("luc"), it.optString("cau"), it.optString("viec"), it.optString("app")) }
        }.getOrDefault(emptyList())

    /** Ghi một dòng nhật ký, giữ [GIU] dòng mới nhất (câu nói là chuyện riêng: không giữ lâu). */
    @Synchronized
    fun ghi(cau: String, viec: String, app: String = "") {
        val ds = (nhatKy + NhatKy(System.currentTimeMillis(), cau.take(200), viec.take(200), app)).takeLast(GIU)
        val a = JSONArray()
        ds.forEach { a.put(JSONObject().put("luc", it.luc).put("cau", it.cau).put("viec", it.viec).apply { if (it.app.isNotEmpty()) put("app", it.app) }) }
        p.edit().putString("nhat_ky", a.toString()).apply()
    }

    companion object {
        const val GIU = 15
        @Volatile private var dsKenhNho: Pair<String, List<KenhTv>>? = null
    }
}
