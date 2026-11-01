package vn.tivikit.dulieu

import org.json.JSONArray
import org.json.JSONObject

/**
 * Kênh nhanh (25/10/2026, muốn một nút mở thẳng VTV1 trên TV360; cùng ngày mở rộng thành nhiều ô): mỗi ô kênh là một
 * app phụ nhỏ trên tivi (module kenh/, flavor o1 … o6) mở thẳng một kênh rồi tự đóng. Mỗi ô một app vì Home sạch mở app
 * và gán nút remote theo tên gói. Ô TV360: kênh chọn theo số kênh TV360 hiện trong danh sách kênh (shortcutKeyboard, 3
 * chữ số), tên dự phòng khi TV360 đổi số. Ô VTVgo (26/10/2026, trước là ô link của Home sạch, KenhVtvGo): mã kênh của
 * VTVgo. Ô FPT Play (31/10/2026, KenhFpt): mã kênh của FPT Play. Ô M3U (1/11/2026): mã kênh (số) của M3UAndroid, lấy từ
 * phím tắt người dùng tạo trong M3U (kenhM3u). Ô trên Home sạch mang tên kênh qua app_labels.
 */
object KenhNhanh {
    /** Số ô kênh tối đa (số flavor của module kenh/). */
    const val SO_O = 6
    /** Gói của ô 1 (giữ tên của bản một ô trước đây); ô n là GOI + n. */
    const val GOI = "vn.tivikit.kenh"
    const val TV360 = "com.viettel.tv360.tv"
    const val VTVGO = KenhVtvGo.GOI
    const val FPTPLAY = KenhFpt.GOI
    /** M3UAndroid (1/11/2026): trình phát IPTV mã nguồn mở, người dùng tự thêm danh sách kênh (Gợi ý app). */
    const val M3U = "com.m3u.smartphone"
    /** Các app xem kênh của ô (biểu tượng của chúng làm huy hiệu ở góc ô, AnhOKenh). */
    val APP_XEM = listOf(TV360, VTVGO, FPTPLAY, M3U, Iptv.GOI)
    /** Loại ô (khoá "app" trong JSON của app phụ; ô TV360 không ghi). */
    const val LOAI_TV360 = "tv360"
    const val LOAI_VTVGO = "vtvgo"
    const val LOAI_FPT = "fptplay"
    const val LOAI_M3U = "m3u"
    /** Ô Auto (1/11/2026): một kênh, nhiều nguồn; Map Voice mở nguồn phát được, mất tiếng thì đổi nguồn. */
    const val LOAI_AUTO = "auto"
    /** Ô IPTV (1/11/2026): kênh trong danh sách IPTV của người dùng, phát bằng app IPTV của TiviKit (Iptv); [Kenh.ma] = mã kênh. */
    const val LOAI_IPTV = "iptv"

    fun goiCua(o: Int): String = if (o == 1) GOI else "$GOI$o"
    /** Màn mở kênh của ô [o] (bấm ô / nút remote cũng vào đây). */
    fun moCua(o: Int): String = "${goiCua(o)}/vn.tivikit.kenh.MoKenh"
    /** File APK của ô [o] trong assets. */
    fun apkCua(o: Int): String = if (o == 1) "kenh.apk" else "kenh$o.apk"
    /** Số ô của gói [p], null nếu không phải ô Kênh nhanh. */
    fun oCua(p: String): Int? = (1..SO_O).firstOrNull { goiCua(it) == p }
    fun laGoiKenh(p: String): Boolean = oCua(p) != null

    /**
     * Kênh của một ô: TV360 theo số kênh [so]; ô VTVgo / FPT Play ([loai]) theo mã kênh [ma] của app đó (vtvgo://play,
     * fptplay.vn/xem-truyen-hinh), [so] rỗng. Có [ma] mà không nói [loai] là ô VTVgo (như trước khi có ô FPT Play).
     */
    data class Kenh(
        val so: String, val ten: String, val ma: String = "", val loai: String = if (ma.isEmpty()) LOAI_TV360 else LOAI_VTVGO,
        /** Ô Auto: các nguồn (mỗi nguồn một Kenh VTVgo / TV360 / FPT Play / IPTV / M3U), theo thứ tự thử mặc định hay thứ tự riêng. */
        val nguon: List<Kenh> = emptyList(),
        /** Ô Auto có thứ tự nguồn riêng người dùng đặt (1/11/2026): Map Voice thử đúng thứ tự [nguon], không xếp theo các lần trước. */
        val coDinh: Boolean = false,
    ) {
        val laVtvGo: Boolean get() = loai == LOAI_VTVGO
        val laFpt: Boolean get() = loai == LOAI_FPT
        val laM3u: Boolean get() = loai == LOAI_M3U
        val laTv360: Boolean get() = loai == LOAI_TV360
        val laAuto: Boolean get() = loai == LOAI_AUTO
        val laIptv: Boolean get() = loai == LOAI_IPTV
        /** App mở kênh này (ô Auto: Map Voice). */
        val app: String get() = when (loai) {
            LOAI_VTVGO -> VTVGO; LOAI_FPT -> FPTPLAY; LOAI_M3U -> M3U; LOAI_IPTV -> Iptv.GOI; LOAI_AUTO -> MapVoice.GOI; else -> TV360
        }
        /** Tên app mở kênh này, cho người dùng. */
        val tenApp: String get() = tenAppCua(app)
        /** Cùng một kênh trên cùng app (để không làm hai ô một kênh); ô Auto: cùng tên kênh; ô IPTV: cùng mã và tên (tvg-id có khi chung). */
        fun trung(k: Kenh): Boolean = loai == k.loai && when {
            laTv360 -> so == k.so
            laAuto -> khoaTen(ten) == khoaTen(k.ten)
            laIptv -> ma == k.ma && ten == k.ten
            else -> ma == k.ma
        }
    }

    /** Tên app xem kênh [goi] (TV360 / VTVgo / FPT Play / M3U; Map Voice = ô Auto), cho người dùng. */
    fun tenAppCua(goi: String): String = when (goi) {
        VTVGO -> "VTVgo"; FPTPLAY -> "FPT Play"; M3U -> "M3U"; Iptv.GOI -> "IPTV"; MapVoice.GOI -> vn.tivikit.t("Tự động", "Auto"); else -> "TV360"
    }

    /**
     * Tên kênh để so giữa các nguồn (như khoaKenh của Map Voice): không dấu, bỏ "kênh", "truyền hình", HD / FHD / UHD / 4K,
     * chỉ giữ a-z 0-9, tên khác gộp về một ("QPVN HD" = "Quốc phòng Việt Nam", "HaNoiTV 1" = "Hà Nội 1").
     */
    fun khoaTen(ten: String): String {
        val k = XaPhuong.boDau(ten).replace(Regex("\\b(kenh|truyen hinh|hd|fhd|uhd|4k)\\b"), " ").replace(Regex("[^a-z0-9]"), "")
        return BI_DANH[k] ?: k
    }

    private val BI_DANH = mapOf(
        "qpvn" to "quocphongvietnam", "quocphongvn" to "quocphongvietnam", "antv" to "congannhandan",
        "hanoitv1" to "hanoi1", "hanoitv2" to "hanoi2",
        "thvl1" to "vinhlong1", "thvl2" to "vinhlong2", "thvl3" to "vinhlong3", "thvl4" to "vinhlong4",
    )

    /**
     * Nguồn cho ô Auto kênh [ten] (1/11/2026), theo thứ tự thử mặc định VTVgo → TV360 → FPT Play → IPTV → M3U: kênh cùng tên
     * trong danh sách VTVgo [dsGo], FPT Play [dsFpt], danh sách IPTV [dsIptv], phím tắt M3U [dsM3u] (tên, mã); TV360 chỉ khi
     * biết số kênh ([tv360]: gợi ý và ô TV360 đang có) — TV360 tìm theo tên có khi mở nhầm kênh mà vẫn có tiếng.
     */
    fun nguonAuto(
        ten: String, dsGo: List<KenhVtvGo.Kenh>, dsFpt: List<KenhFpt.Kenh>, dsM3u: List<Pair<String, String>>, tv360: List<Kenh>,
        dsIptv: List<Iptv.Kenh> = emptyList(),
    ): List<Kenh> {
        val k = khoaTen(ten)
        if (k.isEmpty()) return emptyList()
        return listOfNotNull(
            dsGo.firstOrNull { khoaTen(it.ten) == k }?.let { Kenh("", tenSach(it.tenO), it.ma) },
            tv360.firstOrNull { it.laTv360 && it.so.isNotEmpty() && khoaTen(it.ten) == k }?.let { Kenh(it.so, tenSach(it.ten)) },
            dsFpt.firstOrNull { khoaTen(it.ten) == k }?.let { Kenh("", tenSach(it.tenO), it.ma, LOAI_FPT) },
            dsIptv.firstOrNull { khoaTen(it.ten) == k }?.let { Kenh("", tenSach(it.ten), it.maO, LOAI_IPTV) },
        ) + dsM3u.filter { khoaTen(it.first) == k }.take(2).map { (t, m) -> Kenh("", tenSach(t), m, LOAI_M3U) }
    }

    /**
     * Nguồn [ds] của ô Auto xếp theo thứ tự riêng [thuTu] (loại nguồn: LOAI_TV360, LOAI_VTVGO…): loại có trong [thuTu] theo
     * thứ tự đó, loại chưa có ở cuối; hai nguồn cùng loại (M3U) giữ thứ tự cũ.
     */
    fun theoThuTu(ds: List<Kenh>, thuTu: List<String>): List<Kenh> =
        ds.sortedBy { thuTu.indexOf(it.loai).let { i -> if (i < 0) Int.MAX_VALUE else i } }

    /** Kênh hay xem, số theo danh sách kênh trên tv360.vn (25/10/2026). */
    val GOI_Y = listOf(
        Kenh("001", "VTV1"), Kenh("002", "VTV2"), Kenh("003", "VTV3"), Kenh("004", "VTV4"), Kenh("005", "VTV5"),
        Kenh("052", "VTV6"), Kenh("009", "VTV7"), Kenh("010", "VTV8"), Kenh("011", "VTV9"), Kenh("006", "VTV10"),
        Kenh("073", "HTV7 HD"), Kenh("074", "HTV9 HD"), Kenh("098", "Hà Nội 1"), Kenh("012", "ANTV"), Kenh("008", "QPVN HD"),
    )

    /** "1" / "01" / "001" → "001" (dạng số kênh của TV360); ngoài 1..999 → null. */
    fun chuanSo(s: String?): String? = s?.trim()?.toIntOrNull()?.takeIf { it in 1..999 }?.toString()?.padStart(3, '0')

    /**
     * Tên kênh dùng được ở mọi chỗ: bỏ dấu phẩy và xuống dòng (app_labels ở Nâng cao tách mục bằng dấu phẩy), nháy và
     * ký tự shell (đi trong lệnh content call bọc nháy đơn), "=" (dấu tách gói=tên).
     */
    fun tenSach(s: String): String = s.replace(Regex("[,'\"`\$\\\\=\\r\\n]"), " ").trim().replace(Regex("\\s+"), " ").take(30)

    /**
     * Cấu hình gửi cho app phụ (content call … --method dat), như Kenh.json của module kenh/. Chữ có dấu thành \uXXXX như
     * TiviKitLauncher.cauHinh: lệnh qua libadb nên thuần ASCII ("Hà Nội 1" làm hỏng lệnh, 28/10/2026).
     */
    fun json(k: Kenh): String = TiviKitLauncher.chiAscii(jsonO(k).toString())

    /**
     * Thứ tự riêng của các ô Auto trong [ds] gửi Map Voice (MapVoice.lenhThuTu, 11/2026: anh muốn câu nói "mở VTV1" cũng theo
     * thứ tự của ô): [{"ten":"VTV1","app":"auto","nguon":[…],"coDinh":true},…]; chỉ ô có thứ tự riêng. Chưa ASCII (lệnh lo).
     */
    fun jsonThuTu(ds: List<Kenh>): String = JSONArray().apply { ds.filter { it.laAuto && it.coDinh }.forEach { put(jsonO(it)) } }.toString()

    /** Ô Auto: nguồn ghi rõ app (cả TV360), như Kenh.jsonNguon của app phụ. */
    private fun jsonO(k: Kenh): JSONObject = JSONObject().put("ten", k.ten).apply {
        when {
            k.laTv360 -> put("so", k.so)
            k.laAuto -> put("app", k.loai).put("nguon", JSONArray().apply { k.nguon.forEach { n -> put(jsonO(n).put("app", n.loai)) } })
                .apply { if (k.coDinh) put("coDinh", true) }
            else -> put("app", k.loai).put("ma", k.ma)
        }
    }

    private val MA_GO = Regex("[A-Za-z0-9_-]{1,60}")
    /** Mã kênh của M3U: số nguyên dương (như app phụ, đi vào extra kiểu int của M3U). */
    private val MA_M3U = Regex("[1-9][0-9]{0,8}")

    fun tuJson(j: JSONObject?): Kenh? {
        if (j == null) return null
        val ten = j.optString("ten").trim()
        val loai = j.optString("app")
        if (loai == LOAI_AUTO) {
            val a = j.optJSONArray("nguon") ?: return null
            val ds = (0 until a.length()).mapNotNull { a.optJSONObject(it) }.mapNotNull { o -> tuJson(o)?.takeIf { !it.laAuto } }
            return if (ten.isNotEmpty() && ds.isNotEmpty()) Kenh("", ten, "", LOAI_AUTO, ds, j.optBoolean("coDinh")) else null
        }
        if (loai == LOAI_VTVGO || loai == LOAI_FPT || loai == LOAI_M3U || loai == LOAI_IPTV) {
            val ma = j.optString("ma").trim()
            val dung = when (loai) { LOAI_M3U -> MA_M3U; LOAI_IPTV -> Iptv.MA; else -> MA_GO }
            return if (dung.matches(ma) && ten.isNotEmpty()) Kenh("", ten, ma, loai) else null
        }
        val so = chuanSo(j.optString("so")) ?: return null
        return Kenh(so, ten)
    }

    private val PHIM_TAT_M3U = Regex("""ShortcutInfo \{id=channel_([1-9][0-9]{0,8}),""")
    private val TEN_PHIM_TAT = Regex("""shortLabel=(.*), resId=""")

    /**
     * Kênh M3U (1/11/2026): M3UAndroid không cho app khác đọc danh sách kênh, chỉ mở được kênh theo mã (số) của nó. Mã lấy
     * từ phím tắt người dùng tạo trong M3U (giữ OK trên kênh → Create Shortcut: id "channel_<mã>", tên kênh) đọc trong
     * `dumpsys shortcut` của tivi; [dump] là phần của gói M3U. Trả (tên, mã) theo thứ tự trong bản dump, bỏ trùng mã; phím
     * tắt "channel_recently" (kênh vừa xem) không tính.
     */
    fun kenhM3u(dump: String): List<Pair<String, String>> {
        val ds = LinkedHashMap<String, String>()
        var ma: String? = null
        for (l in dump.lines()) {
            val id = PHIM_TAT_M3U.find(l)
            if (id != null) { ma = id.groupValues[1]; continue }
            if (l.contains("ShortcutInfo {")) { ma = null; continue }
            val ten = TEN_PHIM_TAT.find(l)?.groupValues?.get(1)?.trim()
            if (ma != null && !ten.isNullOrEmpty()) { ds.putIfAbsent(ma, ten); ma = null }
        }
        return ds.map { (m, ten) -> ten to m }
    }

    private fun chu(a: JSONArray?): List<String> = if (a == null) emptyList()
        else (0 until a.length()).mapNotNull { a.opt(it) as? String }.map { it.trim() }.filter { it.isNotEmpty() }

    /** Các mục "gói=giá trị" của ô Kênh nhanh trong danh sách [khoa] của config.json: gói → giá trị. */
    private fun cuaO(j: JSONObject, khoa: String): Map<String, String> = chu(j.optJSONArray(khoa)).mapNotNull { m ->
        val goi = m.substringBefore('=').trim(); val v = m.substringAfter('=', "").trim()
        if (laGoiKenh(goi) && v.isNotEmpty()) goi to v else null
    }.toMap()

    /** Đặt / bỏ ([giaTri] null) mục của ô [goi] trong danh sách [khoa], giữ nguyên mục người dùng đặt cho app khác. */
    private fun datCuaO(j: JSONObject, khoa: String, goi: String, giaTri: String?): JSONObject {
        val giu = chu(j.optJSONArray(khoa)).filter { it.substringBefore('=').trim() != goi }
        val ds = if (giaTri.isNullOrBlank()) giu else giu + "$goi=$giaTri"
        if (ds.isEmpty()) j.remove(khoa) else j.put(khoa, JSONArray(ds))
        return j
    }

    /** Tên các ô Kênh nhanh trong app_labels của config.json ("vn.tivikit.kenh3=VTV3"): gói → tên. */
    fun tenTrong(j: JSONObject): Map<String, String> = cuaO(j, "app_labels")

    /** Đặt / bỏ ([ten] null) tên ô [goi] trong app_labels, giữ nguyên tên người dùng đặt cho app khác. */
    fun datTen(j: JSONObject, goi: String, ten: String?): JSONObject = datCuaO(j, "app_labels", goi, ten)

    /**
     * Logo kênh của ô [o] (26/10/2026): ảnh kenh-o<n>.jpg TiviKit gửi cạnh config.json, ô trỏ tới bằng app_banners
     * "vn.tivikit.kenh3=kenh-o3"; Home sạch của TiviKit đọc được (LinkTiles.picture trong bản vá launcher).
     */
    fun tenAnh(o: Int): String = "kenh-o$o"

    /** Logo các ô Kênh nhanh trong app_banners của config.json: gói → tên ảnh. */
    fun anhTrong(j: JSONObject): Map<String, String> = cuaO(j, "app_banners")

    /** Đặt / bỏ ([anh] null) logo của ô [goi] trong app_banners, giữ nguyên ảnh người dùng đặt cho app khác. */
    fun datAnh(j: JSONObject, goi: String, anh: String?): JSONObject = datCuaO(j, "app_banners", goi, anh)

    /**
     * Thêm (cuối dock, có rồi thì giữ chỗ) / bỏ ô [goi] trong dock_apps. true = xong, false = dock đã đủ [toiDa] app (ô
     * link kiểu cũ vgs.kenh.* không tính, như cauHinhHome), null = config.json không có dock_apps (Home sạch đang dùng dock
     * mặc định, ghi vào là mất dock đó).
     */
    fun datDock(j: JSONObject, goi: String, trongDock: Boolean, toiDa: Int): Boolean? {
        val ds = chu(j.optJSONArray("dock_apps") ?: return null)
        val moi = when {
            trongDock && goi in ds -> ds
            trongDock -> if (ds.count { !it.startsWith(KenhVtvGo.TIEN_TO) } >= toiDa) return false else ds + goi
            else -> ds - goi
        }
        j.put("dock_apps", JSONArray(moi))
        return true
    }

    /** Các gói có trong dock_apps (để biết ô nào đang ở dock). */
    fun trongDock(j: JSONObject): Set<String> = chu(j.optJSONArray("dock_apps")).toSet()
}
