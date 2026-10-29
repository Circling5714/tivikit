package vn.tivikit.dulieu

import org.json.JSONArray
import org.json.JSONObject

/**
 * Map Voice (29/10/2026): app phụ vn.tivikit.mapvoice trên tivi (module mapvoice/). Nói vào nút mic của remote như thường
 * (Google Assistant): câu tìm nhạc / video mở bằng SmartTube thay vì YouTube gốc, câu mở kênh mở bằng VTVgo / TV360. App đọc
 * câu Trợ lý vừa nghe qua ADB của chính tivi (cách của app Super Voice), nên tivi phải luôn mở ADB qua mạng. Ở đây: tên gói,
 * cấu hình, lệnh, trạng thái app phụ trả về.
 */
object MapVoice {
    const val GOI = "vn.tivikit.mapvoice"
    const val DICH_VU = "$GOI/vn.tivikit.mapvoice.MapVoiceDichVu"
    const val APK = "mapvoice.apk"
    /** Trợ lý Google trên Android TV: ghi câu vừa nghe vào logcat. */
    const val TRO_LY = "com.google.android.katniss"
    /** App cùng việc (Super Voice, MapVoice của com.linor.vn): chạy cùng thì một câu bị hai app làm. */
    val APP_TRUNG = listOf("com.linor.vn.supperVoice", "com.linor.vn.mapVoice")
    val SMARTTUBE = CastSmartTube.SMARTTUBE
    const val APP_VTVGO = "vtvgo"
    const val APP_TV360 = "tv360"

    /** Câu tìm nhạc / video mở thế nào (CachMo của module mapvoice/). */
    enum class CachMo(val ma: String) {
        THEO_CAU("theo-cau"), PHAT_LUON("phat-luon"), TIM_KIEM("tim-kiem");

        companion object {
            fun tuMa(s: String?): CachMo = entries.firstOrNull { it.ma == s } ?: THEO_CAU
        }
    }

    /** Cấu hình gửi cho app phụ, như CauHinh của module mapvoice/. */
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
    }

    fun cauHinh(j: JSONObject?): CauHinh? = j?.let {
        CauHinh(
            bat = it.optBoolean("bat", true), appYouTube = it.optString("appYouTube"), cachMo = CachMo.tuMa(it.optString("cachMo")),
            nhacVideo = it.optBoolean("nhacVideo", true), kenh = it.optBoolean("kenh", true),
            appKenh = if (it.optString("appKenh") == APP_TV360) APP_TV360 else APP_VTVGO,
        )
    }

    /**
     * Lệnh content call tới app phụ: [arg] thành ASCII (chữ có dấu thành \uXXXX, libadb hỏng với chữ ngoài ASCII), không có
     * nháy đơn (đi trong nháy đơn của shell).
     */
    private fun lenh(method: String, arg: String? = null): String =
        "content call --uri content://$GOI --method $method" +
            (arg?.let { " --arg '" + TiviKitLauncher.chiAscii(it).replace("'", "\\u0027") + "'" } ?: "")

    fun lenhDat(c: CauHinh): String = lenh("dat", c.json().toString())
    fun lenhKenh(json: String): String = lenh("kenh", json)
    fun lenhXem(cau: String): String = lenh("xem", JSONObject().put("cau", cau.take(200)).toString())
    fun lenhThu(cau: String): String = lenh("thu", JSONObject().put("cau", cau.take(200)).toString())
    val LENH_NOI_LAI = lenh("noi-lai")

    /**
     * Danh sách kênh gửi lên tivi: kênh VTVgo (tên gọn, mã kênh) và kênh TV360 biết số (gợi ý của Kênh nhanh, ô Kênh nhanh
     * TV360 trên tivi). App phụ gộp kênh cùng tên.
     */
    fun dsKenh(go: List<KenhVtvGo.Kenh>, tv360: List<KenhNhanh.Kenh>): String {
        val a360 = JSONArray()
        tv360.filter { !it.laVtvGo && it.so.isNotEmpty() }.distinctBy { it.so }
            .forEach { a360.put(JSONObject().put("ten", KenhNhanh.tenSach(it.ten)).put("so", it.so)) }
        val aGo = JSONArray()
        go.distinctBy { it.ma }.forEach { aGo.put(JSONObject().put("ten", KenhNhanh.tenSach(it.tenO)).put("ma", it.ma)) }
        return JSONObject().put("tv360", a360).put("vtvgo", aGo).toString()
    }

    /**
     * Việc app phụ đã / sẽ làm cho một câu ("loại|chi tiết"): [loai] = yt-phat / yt-tim / yt-app / kenh / bo-qua / loi;
     * [chiTiet] = chữ tìm, tên kênh hay lý do lỗi; [app] = "vtvgo" / "tv360" với kênh.
     */
    data class Viec(val loai: String, val chiTiet: String, val app: String = "")

    fun viec(s: String): Viec {
        val p = s.split('|')
        return when (p[0]) {
            "kenh" -> Viec("kenh", p.getOrElse(2) { "" }, p.getOrElse(1) { "" })
            else -> Viec(p[0], p.drop(1).joinToString("|"))
        }
    }

    /** Việc trong kết quả của lệnh xem / thu ("Result: Bundle[{viec=yt-tim|nhạc piano, ok=1}]"); null nếu tivi báo lỗi. */
    fun viecTrong(ketQua: String): Viec? = Regex("viec=([^,}]*)").find(ketQua)?.groupValues?.get(1)?.trim()?.let(::viec)

    /** Lỗi app phụ báo trong kết quả ("loi=dich-vu-tat"); "" nếu không có. */
    fun loiTrong(ketQua: String): String = Regex("loi=([^,}]*)").find(ketQua)?.groupValues?.get(1)?.trim().orEmpty()

    data class NhatKy(val luc: Long, val cau: String, val viec: Viec)

    /**
     * Trạng thái app phụ (content call … --method doc). [adb] = vòng nghe: da-noi (đang nghe), dang-noi, dang-hoi (tivi đang
     * hỏi cho phép gỡ lỗi), tu-choi (người dùng không cho phép), khong-co (tivi không mở ADB qua mạng), loi, tat.
     * [appYouTube] = app sẽ mở YouTube ("" = tivi chưa có SmartTube).
     */
    data class TrangThai(
        val phienBan: String, val dichVuChay: Boolean, val adb: String, val troLy: Boolean, val appYouTube: String,
        val cauHinh: CauHinh, val soKenh: Int, val nhatKy: List<NhatKy>,
    )

    fun trangThai(j: JSONObject): TrangThai {
        val nk = j.optJSONArray("nhatKy")?.let { a ->
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { NhatKy(it.optLong("luc"), it.optString("cau"), viec(it.optString("viec"))) }
        }.orEmpty()
        return TrangThai(
            j.optString("phienBan"), j.optBoolean("dichVuChay"), j.optString("adb"), j.optBoolean("troLy"), j.optString("appYouTube"),
            cauHinh(j.optJSONObject("cauHinh")) ?: CauHinh(), j.optInt("soKenh"), nk,
        )
    }
}
