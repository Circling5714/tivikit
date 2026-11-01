package vn.tivikit.mapvoice

import org.json.JSONArray
import org.json.JSONObject

/**
 * Kênh tự chọn nguồn (Kênh nhanh Auto, 1/11/2026): một kênh xem được trên nhiều app (VTVgo, TV360, FPT Play, M3U), bấm ô
 * Auto hay nói "mở VTV1" (app kênh "Tự động") thì mở lần lượt từng nguồn tới khi có tiếng (TuChon). Phần ở đây không đụng
 * Android: nguồn của một kênh, thứ tự thử (nguồn lần trước lên tiếng nhanh nhất trước, nguồn vừa lỗi xuống cuối), đọc
 * `dumpsys audio` (app nào đang phát) và phím tắt kênh của M3U.
 */
object TuChonNguon {
    const val VTVGO = "vtvgo"
    const val TV360 = "tv360"
    const val FPT = "fptplay"
    const val M3U = "m3u"
    /** Kênh trong danh sách IPTV của người dùng, phát bằng app IPTV của TiviKit (1/11/2026). */
    const val IPTV = "iptv"
    /** Thứ tự thử khi chưa có lịch sử (anh chọn 1/11/2026; IPTV đứng chỗ M3U, M3U sau cùng). */
    val MAC_DINH = listOf(VTVGO, TV360, FPT, IPTV, M3U)
    const val GOI_IPTV = "vn.tivikit.iptv"
    const val IPTV_PHAT = "vn.tivikit.iptv.PhatKenh"
    /** M3UAndroid: màn phát nhận mã kênh qua extra, như phím tắt Create Shortcut của nó (kenh/MoKenh). */
    const val GOI_M3U = "com.m3u.smartphone"
    const val M3U_PHAT = "com.m3u.smartphone.ui.business.channel.PlayerActivity"
    const val M3U_MA_KENH = "shortcut:channel-id"
    /** Phần phím tắt M3U trong `dumpsys shortcut` (như ThaoTacTv.docKenhM3u của TiviKit). */
    const val LENH_PHIM_TAT_M3U = "dumpsys shortcut | sed -n '/Package: $GOI_M3U/,/Package: /p'"

    /**
     * Một nguồn của kênh: [app] (VTVGO…), [ten] = tên kênh trong app đó, [ma] = mã kênh của VTVgo / FPT Play / M3U, [so] =
     * số kênh TV360 ("" = TV360 tìm theo tên), [url] = link luồng (phím tắt M3U, để thử trước khi mở).
     */
    data class Nguon(val app: String, val ten: String, val ma: String = "", val so: String = "", val url: String = "") {
        /** Khoá thống kê: hai kênh M3U khác mã là hai nguồn khác nhau. */
        val khoa: String get() = "$app:" + ma.ifEmpty { so }
        val goi: String get() = goiCua(app)
    }

    /** Các lần mở một nguồn: [tb] = thời gian lên tiếng trung bình (ms, 0 = chưa lần nào), [loi] = số lần lỗi liền, [lucLoi] = lần lỗi cuối. */
    data class ThongKe(val tb: Long = 0, val loi: Int = 0, val lucLoi: Long = 0) {
        fun duoc(ms: Long) = ThongKe(tb = if (tb <= 0) ms else (tb * 2 + ms) / 3)
        fun hong(bay: Long) = copy(loi = loi + 1, lucLoi = bay)
    }

    fun goiCua(app: String): String = when (app) {
        VTVGO -> XuLyCau.VTVGO
        TV360 -> XuLyCau.TV360
        FPT -> XuLyCau.FPTPLAY
        IPTV -> GOI_IPTV
        else -> GOI_M3U
    }

    /** Nguồn vừa lỗi bị đẩy xuống cuối trong 30 phút, gấp đôi mỗi lần lỗi liền, tối đa một ngày. */
    fun phat(loi: Int): Long = if (loi <= 0) 0 else minOf(30 * 60_000L shl minOf(loi - 1, 6), 24 * 3_600_000L)

    /**
     * Thứ tự thử các nguồn [ds]: nguồn đã từng lên tiếng (nhanh trước), rồi nguồn chưa thử (theo [MAC_DINH]), sau cùng nguồn
     * đang bị phạt vì vừa lỗi (lỗi lâu nhất trước). [tk] theo Nguon.khoa.
     */
    fun xep(ds: List<Nguon>, tk: Map<String, ThongKe>, bay: Long): List<Nguon> {
        val goc = ds.distinctBy { it.khoa }.sortedBy { MAC_DINH.indexOf(it.app) }
        fun biPhat(n: Nguon) = tk[n.khoa]?.let { it.loi > 0 && bay - it.lucLoi < phat(it.loi) } == true
        val phat = goc.filter(::biPhat).sortedBy { tk[it.khoa]?.lucLoi ?: 0 }
        val con = goc.filterNot(::biPhat)
        val daPhat = con.filter { (tk[it.khoa]?.tb ?: 0) > 0 }.sortedBy { tk[it.khoa]?.tb ?: 0 }
        return daPhat + con.filter { it !in daPhat } + phat
    }

    /**
     * Thứ tự riêng người dùng đặt cho ô (1/11/2026): giữ nguyên thứ tự [ds], không xếp theo các lần trước. [hong] = nguồn vừa
     * mất tiếng lúc đang xem: thử các nguồn sau nó trước, rồi các nguồn trước nó, nó sau cùng.
     */
    fun theoThuTu(ds: List<Nguon>, hong: Nguon? = null): List<Nguon> {
        val goc = ds.distinctBy { it.khoa }
        val i = goc.indexOfFirst { it.khoa == hong?.khoa }
        return if (i < 0) goc else goc.drop(i + 1) + goc.take(i) + goc[i]
    }

    /**
     * Nguồn của kênh [k] mà Map Voice biết (VTVgo, FPT Play theo mã kênh; TV360 chỉ khi biết số kênh — TV360 tìm theo tên có
     * khi mở nhầm kênh mà vẫn có tiếng, tự chọn tưởng là được) và các phím tắt M3U cùng tên ([m3u]).
     */
    fun nguonCua(k: KenhTv, m3u: List<Nguon>, iptv: Nguon? = null): List<Nguon> = buildList {
        if (k.ma.isNotEmpty()) add(Nguon(VTVGO, k.ten, ma = k.ma))
        if (k.so.isNotEmpty()) add(Nguon(TV360, k.ten360, so = k.so))
        if (k.fpt.isNotEmpty()) add(Nguon(FPT, k.tenFpt, ma = k.fpt))
        if (iptv != null) add(iptv)
        addAll(m3u.filter { XuLyCau.khoaKenh(it.ten) in k.khoa })
    }

    /**
     * Kênh IPTV cùng tên trong kết quả lệnh "tim" của app IPTV ("Result: Bundle[{ma=vtv3hd, ok=1, ten=VTV3 HD}]"); null nếu
     * app trả lỗi / không có kênh đó. Danh sách không ghi tvg-id thì mã là tên đã gọn (app IPTV tìm lại theo tên).
     */
    fun iptvTu(out: String): Nguon? {
        if (!out.contains("ok=1")) return null
        // Ngoặc nhọn phải escape: regex của Android (ICU) báo lỗi cú pháp với "}" trần, regex của JVM (unit test) thì không.
        val ma = Regex("""[\{ ]ma=([A-Za-z0-9_.@:+-]{1,80})[,\}]""").find(out)?.groupValues?.get(1).orEmpty()
        val ten = Regex("""[\{ ]ten=(.*?)(?:, \w+=|\}\])""").find(out)?.groupValues?.get(1)?.trim().orEmpty()
        if (ten.isEmpty()) return null
        return Nguon(IPTV, ten, ma = ma.ifEmpty { XuLyCau.khoaKenh(ten) })
    }

    /** JSON đi trong lệnh content call qua ADB của Map Voice: chữ ngoài ASCII thành \uXXXX, nháy đơn cũng thế. */
    fun jsonAscii(o: JSONObject): String = buildString {
        for (c in o.toString()) if (c.code in 0x20..0x7e && c != '\'') append(c) else append(String.format("\\u%04x", c.code))
    }

    private val CHAY = Regex("""u/pid:(\d+)/\d+.*?state:(\w+).*?usage=(\w+)""")
    /** Loại tiếng của app xem phim / truyền hình (bỏ tiếng bấm phím, thông báo, Trợ lý). */
    private val TIENG_XEM = setOf("USAGE_MEDIA", "USAGE_UNKNOWN", "USAGE_GAME")

    /**
     * App có [uid] đang phát tiếng phim / truyền hình theo `dumpsys audio` (mục players). Dòng dạng Android 10+:
     * "AudioPlaybackConfiguration piid:63 type:android.media.AudioTrack u/pid:10145/3043 state:started attr:AudioAttributes:
     * usage=USAGE_MEDIA …", Android 8–9: "ID:63 -- type:… -- u/pid:10145/3043 -- state:started -- attr:AudioAttributes: usage=…".
     */
    fun dangPhat(dump: String, uid: Int): Boolean = dump.lineSequence().any { l ->
        val m = CHAY.find(l) ?: return@any false
        m.groupValues[1].toIntOrNull() == uid && m.groupValues[2] == "started" && m.groupValues[3] in TIENG_XEM
    }

    /**
     * App có [uid] đã tạo trình phát tiếng phim (trạng thái nào cũng được: idle, paused…): đang chuẩn bị phát, chờ thêm chút
     * (Skyworth 1/11/2026: VTVgo mở nguội có trình phát "idle" rồi mới "started" ở giây 17).
     */
    fun coTrinhPhat(dump: String, uid: Int): Boolean = dump.lineSequence().any { l ->
        val m = CHAY.find(l) ?: return@any false
        m.groupValues[1].toIntOrNull() == uid && m.groupValues[2] != "released" && m.groupValues[3] in TIENG_XEM
    }

    private val GIU = Regex("""pack: (\S+)""")

    /**
     * Gói đang giữ tiếng (đầu ngăn xếp audio focus, dòng cuối của mục "Audio Focus stack entries") theo `dumpsys audio`; null
     * nếu không ai giữ. Khoá trẻ em hiện màn khoá thì nó giữ tiếng (và tạm dừng app đang xem): không phải nguồn hỏng.
     */
    fun giuTieng(dump: String): String? {
        var trong = false
        var cuoi: String? = null
        for (l in dump.lines()) {
            if (l.contains("Audio Focus stack entries")) { trong = true; continue }
            if (!trong) continue
            if (!l.trimStart().startsWith("source:")) break
            GIU.find(l)?.let { cuoi = it.groupValues[1] }
        }
        return cuoi
    }

    private val PHIM_TAT = Regex("""ShortcutInfo \{id=channel_([1-9][0-9]{0,8}),""")
    private val TEN = Regex("""shortLabel=(.*), resId=""")
    private val LINK = Regex("""longLabel=(https?://\S+?), resId=""")

    /** Kênh M3U có phím tắt (Create Shortcut trong M3U) trong phần `dumpsys shortcut` của M3U: tên, mã kênh, link luồng. */
    fun kenhM3u(dump: String): List<Nguon> {
        val ds = LinkedHashMap<String, Nguon>()
        var ma: String? = null
        for (l in dump.lines()) {
            val id = PHIM_TAT.find(l)
            if (id != null) { ma = id.groupValues[1]; continue }
            if (l.contains("ShortcutInfo {")) { ma = null; continue }
            val m = ma ?: continue
            TEN.find(l)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }?.let { ds.putIfAbsent(m, Nguon(M3U, it, ma = m)) }
            LINK.find(l)?.groupValues?.get(1)?.let { u -> ds[m]?.takeIf { it.url.isEmpty() }?.let { ds[m] = it.copy(url = u) } }
        }
        return ds.values.toList()
    }

    private val MA = Regex("[A-Za-z0-9_-]{1,60}")
    private val MA_M3U = Regex("[1-9][0-9]{0,8}")
    private val MA_IPTV = Regex("[A-Za-z0-9_.@:+-]{1,80}")

    /** Nguồn của ô Auto TiviKit gửi qua app ô (kenh/): [{"app":"vtvgo","ten":"VTV1","ma":"1"},{"app":"tv360","so":"001"}…]; mục hỏng thì bỏ. */
    fun tuJson(s: String?): List<Nguon> {
        val a = runCatching { JSONArray(s ?: "") }.getOrNull() ?: return emptyList()
        return (0 until a.length()).mapNotNull { a.optJSONObject(it) }.mapNotNull { o ->
            val app = o.optString("app")
            val ten = o.optString("ten").trim().take(40)
            val ma = o.optString("ma").trim()
            when (app) {
                VTVGO, FPT -> if (MA.matches(ma)) Nguon(app, ten, ma = ma) else null
                IPTV -> if (MA_IPTV.matches(ma) && ten.isNotEmpty()) Nguon(app, ten, ma = ma) else null
                M3U -> if (MA_M3U.matches(ma)) Nguon(app, ten, ma = ma) else null
                TV360 -> Nguon(app, ten, so = XuLyCau.chuanSo(o.optString("so")) ?: "").takeIf { it.so.isNotEmpty() || ten.isNotEmpty() }
                else -> null
            }
        }
    }

    fun json(ds: List<Nguon>): String = JSONArray().apply {
        ds.forEach { n ->
            put(JSONObject().put("app", n.app).put("ten", n.ten).apply { if (n.so.isNotEmpty()) put("so", n.so) else put("ma", n.ma) })
        }
    }.toString()

    /** Thống kê lưu trong Kho: {"vtvgo:1":{"tb":6200,"loi":0,"lucLoi":0},…}. */
    fun thongKeTu(s: String?): Map<String, ThongKe> {
        val j = runCatching { JSONObject(s ?: "") }.getOrNull() ?: return emptyMap()
        return j.keys().asSequence().mapNotNull { k ->
            val o = j.optJSONObject(k) ?: return@mapNotNull null
            k to ThongKe(o.optLong("tb"), o.optInt("loi"), o.optLong("lucLoi"))
        }.toMap()
    }

    fun thongKeJson(tk: Map<String, ThongKe>): String = JSONObject().apply {
        tk.forEach { (k, v) -> put(k, JSONObject().put("tb", v.tb).put("loi", v.loi).put("lucLoi", v.lucLoi)) }
    }.toString()
}
