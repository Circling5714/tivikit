package vn.tivikit.mapvoice

import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

/**
 * Hiểu câu Trợ lý Google vừa nghe (29/10/2026): câu nói tới YouTube, câu mở kênh, câu tìm nhạc / video. Cách đọc câu theo
 * app Super Voice (com.linor.vn.supperVoice): Trợ lý Google trên Android TV ghi câu đã nhận xong vào logcat, dòng
 * "setFinalRecognizedText(<câu>)". Khác Super Voice: chỉ nhận câu cần chuyển, câu khác (tắt tivi, thời tiết, mở Netflix…)
 * để Trợ lý làm như thường.
 */
object XuLyCau {
    sealed interface Viec
    /** Mở app YouTube (SmartTube) tìm [tim] ("" = chỉ mở app); [phatLuon] = phát luôn video đầu tiên. */
    data class YouTube(val tim: String, val phatLuon: Boolean) : Viec
    /** Mở kênh [ten] bằng [app]: "vtvgo" với mã kênh [ma], hay "tv360" với số kênh [so] ("" = TV360 tìm theo tên). */
    data class MoKenh(val ten: String, val app: String, val ma: String = "", val so: String = "") : Viec
    data object BoQua : Viec

    const val TRO_LY = "com.google.android.katniss"
    const val YOUTUBE_GOC = "com.google.android.youtube.tv"
    const val VTVGO = "vn.vtv.vtvgotv"
    const val TV360 = "com.viettel.tv360.tv"
    val SMARTTUBE = listOf("org.smarttube.stable", "org.smarttube.beta", "com.liskovsoft.smarttubetv", "com.liskovsoft.smarttubetv.beta")

    /** Chữ trong dòng logcat của câu Trợ lý vừa nhận xong (katniss: StreamingTextView). */
    const val DAU_LOG = "setFinalRecognizedText"

    /**
     * Lệnh logcat chạy suốt trên tivi: chỉ dòng có [DAU_LOG] (lọc ngay trên tivi, Map Voice không nhận dòng nào khác), từ
     * lúc bắt đầu (không đọc lại câu cũ).
     */
    const val LENH_LOGCAT = "exec logcat -v brief -T \"\$(date +'%m-%d %H:%M:%S.000')\" -e $DAU_LOG"

    private val VI = Locale("vi")

    /** Câu trong một dòng logcat: "I/katniss_interactor_StreamingTextView( 123): setFinalRecognizedText(mở nhạc piano)". */
    fun cauTuLog(dong: String): String? {
        val i = dong.indexOf(DAU_LOG)
        if (i < 0) return null
        var r = dong.substring(i + DAU_LOG.length).trim()
        if (r.startsWith("(")) r = r.drop(1).let { if (it.endsWith(")")) it.dropLast(1) else it }
        else r = r.removePrefix(":").removePrefix("=")
        return r.trim().takeIf { it.isNotEmpty() && it.length <= 300 && it != "null" }
    }

    /** Chữ thường, dấu dựng sẵn, bỏ dấu câu, gọn khoảng trắng. */
    fun thuong(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFC).lowercase(VI)
        .replace(Regex("[\\p{Punct}“”‘’…«»]"), " ").replace(Regex("\\s+"), " ").trim()

    /** Bỏ dấu từng chữ, giữ đúng số ký tự (để cắt câu gốc theo chỗ tìm thấy trên câu không dấu). */
    fun khongDau(s: String): String = buildString(s.length) {
        for (c in s) append(
            when (c) {
                'đ' -> 'd'
                'Đ' -> 'D'
                else -> Normalizer.normalize(c.toString(), Normalizer.Form.NFD).firstOrNull() ?: c
            }
        )
    }

    private const val AI = "(toi|tao|minh|em|con|chau|bo|me|ong|ba|anh|chi|tui)"
    /** Động từ đầu câu: "mở", "phát", "bật", "cho tôi xem / nghe", "tìm (kiếm)", "xem", "nghe", "play"… */
    private val DONG_TU = Regex("^(hay |xin |lam on )?(mo|phat|bat|xem|nghe|tim kiem|tim|kiem|play|open|search|cho( $AI)? (xem|nghe)|cho $AI)( (giup|cho|ho) $AI)?( |$)")
    /** Câu bắt đầu thế này thì phát luôn video đầu tiên (khi chọn "theo câu nói"); "tìm …" hay không động từ thì mở trang tìm. */
    private val PHAT = Regex("^(hay |xin |lam on )?(mo|phat|bat|xem|nghe|play|open|cho( $AI)? (xem|nghe))( |$)")
    /** "YouTube" và cách Trợ lý hay viết sai, kèm giới từ đứng trước. */
    private val YOUTUBE = Regex("(^| )((tren|bang|o|qua|tu|vao|cua|voi|app|ung dung) )?(you ?tube|du ?tup|diu ?tup|yu ?tup|iu ?tup|giu ?tup)( |$)")
    /** Còn lại toàn chữ chỉ app: "mở ứng dụng YouTube" là mở app, không tìm gì. */
    private val CHI_APP = Regex("^(ung dung|app|trang|kenh)?$")
    /** Câu tìm nhạc / video (sau động từ đầu câu). */
    private val NHAC = Regex("^(nhac|bai hat|bai|ca khuc|lien khuc|video|clip|mv|karaoke|hat|hoat hinh|phim hoat hinh|podcast|album|playlist|danh sach phat)( |$)")
    private val DONG_TU_KENH = Regex("^(hay |xin |lam on )?(mo|xem|bat|chuyen( sang| qua| den| toi)?|sang|qua|cho( $AI)? xem)( |$)")
    private val CHU_KENH = Regex("^(kenh truyen hinh|kenh|truyen hinh)( |$)")
    private val APP_CUOI = Regex("(^| )((tren|bang|o|qua|voi) )?(tv ?360|ti ?vi ?360|vtv ?go)$")

    /** Câu đang xử lý: chữ thường có dấu và bản không dấu cùng độ dài, cắt cùng chỗ. */
    private class Cau(val goc: String, val kd: String) {
        /** Bỏ đoạn khớp [r] đầu tiên (tìm trên bản không dấu); null nếu không khớp. */
        fun bo(r: Regex): Cau? {
            val m = r.find(kd) ?: return null
            return Cau(gon(goc.replaceRange(m.range, " ")), gon(kd.replaceRange(m.range, " ")))
        }

        fun boDongTu(): Cau {
            var c = this
            repeat(3) { c = c.bo(DONG_TU) ?: return c }
            return c
        }

        companion object {
            fun tu(s: String): Cau? = thuong(s).takeIf { it.isNotEmpty() }?.let { Cau(it, khongDau(it)) }
            private fun gon(s: String) = s.replace(Regex("\\s+"), " ").trim()
        }
    }

    /**
     * Việc cho câu [cau]: nói tới YouTube → mở app YouTube đã chọn; mở kênh (có trong [ds]) → VTVgo / TV360 ([coApp] = gói có
     * trên tivi); câu tìm nhạc / video ([CauHinh.nhacVideo]) → app YouTube; còn lại bỏ qua cho Trợ lý.
     */
    fun quyet(cau: String, ch: CauHinh, ds: List<KenhTv>, coApp: (String) -> Boolean): Viec {
        if (!ch.bat) return BoQua
        val c = Cau.tu(cau) ?: return BoQua
        if (YOUTUBE.containsMatchIn(c.kd)) return youTube(cau, ch)
        if (ch.kenh) kenh(c, ch, ds, coApp)?.let { return it }
        if (ch.nhacVideo && NHAC.containsMatchIn(c.boDongTu().kd)) return youTube(cau, ch)
        return BoQua
    }

    /** Mở câu [cau] bằng app YouTube: bỏ chữ "YouTube" và động từ đầu câu, còn lại là chữ tìm. */
    fun youTube(cau: String, ch: CauHinh): YouTube {
        var c = Cau.tu(cau) ?: return YouTube("", false)
        val phat = when (ch.cachMo) {
            CachMo.PHAT_LUON -> true
            CachMo.TIM_KIEM -> false
            CachMo.THEO_CAU -> PHAT.containsMatchIn(c.kd)
        }
        for (i in 1..3) c = c.bo(YOUTUBE) ?: break
        c = c.boDongTu()
        val tim = if (CHI_APP.matches(c.kd)) "" else c.goc
        return YouTube(tim, phat && tim.isNotEmpty())
    }

    /** Câu mở kênh: "mở / xem / chuyển sang (kênh) VTV3 (trên TV360)"; null nếu không phải kênh Map Voice biết. */
    private fun kenh(c0: Cau, ch: CauHinh, ds: List<KenhTv>, coApp: (String) -> Boolean): MoKenh? {
        var c = c0
        for (i in 1..2) c = c.bo(DONG_TU_KENH) ?: break
        c = c.bo(CHU_KENH) ?: c
        val muon = APP_CUOI.find(c.kd)?.let { if (it.value.contains("360")) CauHinh.APP_TV360 else CauHinh.APP_VTVGO }
        if (muon != null) c = c.bo(APP_CUOI) ?: c
        val khoa = khoaKenh(c.goc)
        if (khoa.isEmpty()) return null
        val k = ds.firstOrNull { khoa in it.khoa } ?: return null
        return chonApp(k, muon, ch, coApp)
    }

    /**
     * App mở kênh [k]: app người dùng nói ("… trên TV360"), không thì app đã chọn ở TiviKit; app đó không có kênh / chưa cài
     * thì app kia. TV360 không biết số kênh thì tìm theo tên, nên khi chọn TV360 mà VTVgo biết chắc kênh này thì dùng VTVgo.
     */
    private fun chonApp(k: KenhTv, muon: String?, ch: CauHinh, coApp: (String) -> Boolean): MoKenh? {
        val coGo = k.ma.isNotEmpty() && coApp(VTVGO)
        val co360 = coApp(TV360)
        val go = MoKenh(k.ten, CauHinh.APP_VTVGO, ma = k.ma)
        val tv360 = MoKenh(k.ten360, CauHinh.APP_TV360, so = k.so)
        return when (muon ?: ch.appKenh) {
            CauHinh.APP_TV360 -> if (co360 && (k.so.isNotEmpty() || !coGo || muon != null)) tv360 else if (coGo) go else null
            else -> if (coGo) go else if (co360) tv360 else null
        }
    }

    /** Số đọc bằng chữ, so khi còn dấu: "năm" khác "Nam" (Việt Nam), "hai" khác "Hải", "ba" khác "Bà". */
    private val SO = mapOf("một" to "1", "hai" to "2", "ba" to "3", "bốn" to "4", "năm" to "5", "sáu" to "6", "bảy" to "7",
        "tám" to "8", "chín" to "9", "mười" to "10")

    /** Tên kênh để so: số đọc bằng chữ thành số, không dấu, bỏ "kênh", "truyền hình", "HD", chỉ giữ a-z 0-9 ("Hà Nội một" = "hanoi1"). */
    fun khoaKenh(s: String): String = khongDau(thuong(s).split(' ').joinToString(" ") { SO[it] ?: it })
        .replace(Regex("\\b(kenh|truyen hinh|hd|fhd|uhd|4k)\\b"), " ").replace(Regex("[^a-z0-9]"), "")

    /** Tên viết tắt hay nói mà VTVgo ghi đủ: tên gọn → tên gọn của VTVgo (như KenhVtvGo.TEN_KHAC của TiviKit). */
    private val BI_DANH = mapOf("qpvn" to "quocphongvietnam", "antv" to "congannhandan")

    /**
     * Danh sách kênh từ JSON TiviKit gửi: {"vtvgo":[{"ten","ma"}…],"tv360":[{"ten","so"}…]}. Kênh cùng tên (đã gọn) ở hai app
     * gộp làm một.
     */
    fun dsKenh(json: String): List<KenhTv> {
        val j = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        val theo = LinkedHashMap<String, KenhTv>()
        fun them(ten: String, ma: String, so: String) {
            val k = khoaKenh(ten)
            if (k.isEmpty() || ten.isBlank()) return
            val goc = BI_DANH[k] ?: k
            val khoa = setOf(k, goc) + BI_DANH.filterValues { it == goc }.keys
            val cu = theo[goc]
            theo[goc] = if (cu == null) KenhTv(ten.trim(), ma, so, khoa, ten.trim())
            else cu.copy(
                ma = cu.ma.ifEmpty { ma }, so = cu.so.ifEmpty { so }, khoa = cu.khoa + khoa,
                // TV360 tìm theo tên trong danh sách của nó: giữ tên TV360 đặt.
                ten360 = if (so.isNotEmpty() && cu.so.isEmpty()) ten.trim() else cu.ten360,
            )
        }
        fun mang(a: JSONArray?, f: (JSONObject) -> Unit) { if (a != null) for (i in 0 until a.length()) a.optJSONObject(i)?.let(f) }
        mang(j.optJSONArray("tv360")) { o -> chuanSo(o.optString("so"))?.let { them(o.optString("ten"), "", it) } }
        mang(j.optJSONArray("vtvgo")) { o -> o.optString("ma").trim().takeIf { MA.matches(it) }?.let { them(o.optString("ten"), it, "") } }
        return theo.values.toList()
    }

    private val MA = Regex("[A-Za-z0-9_-]{1,60}")

    /** "1" / "01" / "001" → "001" (số kênh của TV360); ngoài 1..999 → null. */
    fun chuanSo(s: String?): String? = s?.trim()?.toIntOrNull()?.takeIf { it in 1..999 }?.toString()?.padStart(3, '0')
}
