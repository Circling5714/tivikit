package vn.tivikit.dulieu

import org.json.JSONArray
import org.json.JSONObject

/**
 * Map Voice (29/10/2026): app phụ vn.tivikit.mapvoice trên tivi (module mapvoice/). Nói vào nút mic của remote như thường
 * (Google Assistant): câu tìm nhạc / video mở bằng app YouTube đã chọn (SmartTube, TizenTube, YouTube gốc) thay vì để Trợ lý
 * mở YouTube gốc, câu mở kênh mở bằng VTVgo / TV360 / FPT Play, câu riêng (31/10/2026: câu có chữ … thì để Trợ lý / mở bằng
 * app …) xét trước hết. App đọc câu Trợ lý vừa nghe qua ADB của chính tivi (cách của app Super Voice), nên tivi phải luôn
 * mở ADB qua mạng. Ở đây: tên gói, cấu hình, lệnh, trạng thái app phụ trả về.
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
    /** TizenTube cho Android TV (Cobalt). */
    const val TIZENTUBE = "io.gh.reisxd.tizentube.cobalt"
    const val YOUTUBE_GOC = "com.google.android.youtube.tv"
    /** App mở được YouTube, theo thứ tự hiện ở màn Map Voice. */
    val APP_YOUTUBE = SMARTTUBE + TIZENTUBE + YOUTUBE_GOC
    const val APP_VTVGO = "vtvgo"
    const val APP_TV360 = "tv360"
    const val APP_FPT = "fptplay"
    /** Đích câu riêng: để Trợ lý Google làm; app YouTube đã chọn. Còn lại là tên gói app. */
    const val DICH_TRO_LY = "tro-ly"
    const val DICH_YOUTUBE = "youtube"
    /** Như CauHinh của module mapvoice/: số câu riêng, độ dài chữ tối đa. */
    const val CAU_RIENG_TOI_DA = 30
    const val CHU_TOI_DA = 60

    /** Câu tìm nhạc / video mở thế nào (CachMo của module mapvoice/). */
    enum class CachMo(val ma: String) {
        THEO_CAU("theo-cau"), PHAT_LUON("phat-luon"), TIM_KIEM("tim-kiem");

        companion object {
            fun tuMa(s: String?): CachMo = entries.firstOrNull { it.ma == s } ?: THEO_CAU
        }
    }

    /** Câu riêng: câu có chữ [chu] thì làm theo [dich] (DICH_TRO_LY, DICH_YOUTUBE hay tên gói app), như CauRieng của app phụ. */
    data class CauRieng(val chu: String, val dich: String)

    /** Cấu hình gửi cho app phụ, như CauHinh của module mapvoice/. */
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
    }

    fun cauHinh(j: JSONObject?): CauHinh? = j?.let {
        CauHinh(
            bat = it.optBoolean("bat", true), appYouTube = it.optString("appYouTube"), cachMo = CachMo.tuMa(it.optString("cachMo")),
            nhacVideo = it.optBoolean("nhacVideo", true), kenh = it.optBoolean("kenh", true),
            appKenh = it.optString("appKenh").takeIf { a -> a == APP_TV360 || a == APP_FPT } ?: APP_VTVGO,
            cauRieng = it.optJSONArray("cauRieng")?.let { a ->
                (0 until a.length()).mapNotNull { i -> a.optJSONObject(i) }
                    .map { o -> CauRieng(o.optString("chu"), o.optString("dich")) }.filter { r -> r.chu.isNotBlank() && r.dich.isNotBlank() }
            }.orEmpty(),
        )
    }

    /** Chữ câu riêng gõ ở màn Map Voice: gọn khoảng trắng, bỏ nháy và dấu tách (đi trong lệnh content call), tối đa [CHU_TOI_DA]. */
    fun chuSach(s: String): String = s.replace(Regex("[\"'`\\\\|\\r\\n]"), " ").trim().replace(Regex("\\s+"), " ").take(CHU_TOI_DA).trim()

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
     * Danh sách kênh gửi lên tivi: kênh VTVgo (tên gọn, mã kênh), kênh TV360 biết số (gợi ý của Kênh nhanh, ô Kênh nhanh
     * TV360 trên tivi) và kênh FPT Play (mã kênh, danh sách đóng sẵn KenhFpt). App phụ gộp kênh cùng tên.
     */
    fun dsKenh(go: List<KenhVtvGo.Kenh>, tv360: List<KenhNhanh.Kenh>, fpt: List<KenhFpt.Kenh> = emptyList()): String {
        val a360 = JSONArray()
        tv360.filter { it.laTv360 && it.so.isNotEmpty() }.distinctBy { it.so }
            .forEach { a360.put(JSONObject().put("ten", KenhNhanh.tenSach(it.ten)).put("so", it.so)) }
        val aGo = JSONArray()
        go.distinctBy { it.ma }.forEach { aGo.put(JSONObject().put("ten", KenhNhanh.tenSach(it.tenO)).put("ma", it.ma)) }
        val j = JSONObject().put("tv360", a360).put("vtvgo", aGo)
        if (fpt.isNotEmpty()) {
            val aFpt = JSONArray()
            fpt.distinctBy { it.ma }.forEach { aFpt.put(JSONObject().put("ten", KenhNhanh.tenSach(it.ten)).put("ma", it.ma)) }
            j.put("fptplay", aFpt)
        }
        return j.toString()
    }

    /**
     * Việc app phụ đã / sẽ làm cho một câu ("loại|chi tiết"): [loai] = yt-phat / yt-tim / yt-app / kenh / mo-app / tro-ly /
     * bo-qua / loi; [chiTiet] = chữ tìm, tên kênh, gói app, chữ của câu riêng hay lý do lỗi; [app] = "vtvgo" / "tv360" /
     * "fptplay" với kênh; [goi] = gói app đã / sẽ mở (Map Voice 1.1 trở lên báo, "" nếu không có).
     */
    data class Viec(val loai: String, val chiTiet: String, val app: String = "", val goi: String = "")

    fun viec(s: String, goi: String = ""): Viec {
        val p = s.split('|')
        return when (p[0]) {
            "kenh" -> Viec("kenh", p.getOrElse(2) { "" }, p.getOrElse(1) { "" }, goi)
            else -> Viec(p[0], p.drop(1).joinToString("|"), goi = goi)
        }
    }

    /**
     * Việc trong kết quả của lệnh xem / thu ("Result: Bundle[{app=org.smarttube.stable, viec=yt-tim|nhạc piano, ok=1}]");
     * null nếu tivi báo lỗi.
     */
    fun viecTrong(ketQua: String): Viec? {
        val goi = Regex("[{ ]app=([A-Za-z0-9_.]+)").find(ketQua)?.groupValues?.get(1).orEmpty()
        return Regex("viec=([^,}]*)").find(ketQua)?.groupValues?.get(1)?.trim()?.let { viec(it, goi) }
    }

    /** Lỗi app phụ báo trong kết quả ("loi=dich-vu-tat"); "" nếu không có. */
    fun loiTrong(ketQua: String): String = Regex("loi=([^,}]*)").find(ketQua)?.groupValues?.get(1)?.trim().orEmpty()

    data class NhatKy(val luc: Long, val cau: String, val viec: Viec)

    /**
     * Trạng thái app phụ (content call … --method doc). [adb] = vòng nghe: da-noi (đang nghe), dang-noi, dang-hoi (tivi đang
     * hỏi cho phép gỡ lỗi), tu-choi (người dùng không cho phép), khong-co (tivi không mở ADB qua mạng), loi, tat.
     * [appYouTube] = app sẽ mở YouTube ("" = tivi chưa có app YouTube nào dùng được).
     */
    data class TrangThai(
        val phienBan: String, val dichVuChay: Boolean, val adb: String, val troLy: Boolean, val appYouTube: String,
        val cauHinh: CauHinh, val soKenh: Int, val nhatKy: List<NhatKy>,
    )

    fun trangThai(j: JSONObject): TrangThai {
        val nk = j.optJSONArray("nhatKy")?.let { a ->
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }
                .map { NhatKy(it.optLong("luc"), it.optString("cau"), viec(it.optString("viec"), it.optString("app"))) }
        }.orEmpty()
        return TrangThai(
            j.optString("phienBan"), j.optBoolean("dichVuChay"), j.optString("adb"), j.optBoolean("troLy"), j.optString("appYouTube"),
            cauHinh(j.optJSONObject("cauHinh")) ?: CauHinh(), j.optInt("soKenh"), nk,
        )
    }
}
