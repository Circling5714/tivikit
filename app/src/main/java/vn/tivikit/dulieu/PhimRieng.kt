package vn.tivikit.dulieu

import org.json.JSONArray
import org.json.JSONObject

/**
 * Nút remote gán riêng (25/10/2026, muốn nút YouTube trên remote mở SmartTube): người dùng bấm nút thật, app đọc mã
 * bằng getevent rồi ghi một mục app_keys cho dịch vụ phím của Home sạch (HomeKeyService / InputKeyService, xem
 * RemoteKeys của launcher): "scan:<mã quét>:<gói>" theo mã Linux của nút, hoặc "KEYCODE_<nhãn>:<gói>" theo phím Android
 * khi remote gửi mã quét chung. Danh sách ghi thêm ở vgs_phim_rieng trong config.json để các lần ghi cấu hình sau
 * (Đổi giao diện) giữ lại.
 */
object PhimRieng {
    /** Khoá trong config.json: các mục app_keys người dùng gán ở màn Nút remote. */
    const val KHOA = "vgs_phim_rieng"

    /** Đã bỏ key_filter=false để phím riêng chạy (cần lọc phím); hết phím riêng thì trả lại key_filter=false. */
    const val KHOA_BO_TAT_LOC = "vgs_bo_tat_loc_phim"

    /**
     * KEY_UNKNOWN: kernel gán mã này cho mọi nút HID nó không biết (nút app của remote Bluetooth), Android phân biệt bằng
     * usage trong keylayout — gán theo mã quét này là gán cả loạt nút (upstream TROUBLESHOOTING, #853).
     */
    const val MA_CHUNG = 240

    /** Tên gói SmartTube qua các đời (build.gradle của yuliskov/SmartTube), bản hay dùng trước. */
    val GOI_SMARTTUBE = listOf(
        "org.smarttube.stable", "org.smarttube.beta", "app.smarttube.fdroid", "app.smarttube",
        "com.teamsmart.videomanager.tv", "com.liskovsoft.smarttubetv.beta", "com.liskovsoft.smarttubetv", "org.smartteam.smarttube.tv.orig",
    )

    /**
     * Một lần nhấn đọc từ getevent: thiết bị (/dev/input/eventN), mã quét Linux (mã EV_KEY, cũng là
     * KeyEvent.getScanCode()), usage HID (MSC_SCAN cùng lượt, nếu có). [tenThietBi] và [tenAndroid] (nhãn phím trong
     * keylayout, VD BUTTON_3) điền sau.
     */
    data class Nut(val thietBi: String, val maQuet: Int, val usage: Long? = null, val tenThietBi: String = "", val tenAndroid: String? = null)

    private val DONG = Regex("""^(/dev/input/event\d+): ([0-9a-f]{4}) ([0-9a-f]{4}) ([0-9a-f]{8})$""")

    /**
     * Lần nhấn đầu tiên trong chữ in ra của `getevent -q` (dòng "/dev/input/event3: 0001 00ac 00000001": EV_KEY, mã,
     * 1 = nhấn xuống), kèm MSC_SCAN cùng lượt (sau EV_SYN gần nhất) của thiết bị đó. [theo]: chỉ nhận đúng nút này
     * (cùng mã quét; cả hai có usage thì cùng usage).
     */
    fun docGetevent(chu: String, theo: Nut? = null): Nut? {
        val usage = HashMap<String, Long>()
        for (dong in chu.lineSequence()) {
            val m = DONG.matchEntire(dong.trim()) ?: continue
            val (tb, loai, ma, gt) = m.destructured
            when (loai) {
                "0000" -> usage.remove(tb)
                "0004" -> if (ma == "0004") usage[tb] = gt.toLong(16)
                "0001" -> if (gt == "00000001") {
                    val n = Nut(tb, ma.toInt(16), usage[tb])
                    if (theo == null || (n.maQuet == theo.maQuet && (n.usage == null || theo.usage == null || n.usage == theo.usage))) return n
                }
            }
        }
        return null
    }

    /** File keylayout Android dùng cho [thietBi], từ các dòng "Path:" / "KeyLayoutFile:" của `dumpsys input`. */
    fun fileKeylayout(dump: String, thietBi: String): String? {
        var dung = false
        for (dong in dump.lineSequence()) {
            val s = dong.trim()
            when {
                s.startsWith("Path:") -> dung = s.removePrefix("Path:").trim() == thietBi
                dung && s.startsWith("KeyLayoutFile:") -> return s.removePrefix("KeyLayoutFile:").trim().takeIf { it.startsWith("/") }
            }
        }
        return null
    }

    /**
     * Nhãn phím Android của nút trong một file .kl: dòng "key usage <usage> <NHÃN>" trước (Android ưu tiên usage), rồi
     * "key <mã quét> <NHÃN>". null nếu không có.
     */
    fun tenTrongKl(kl: String, maQuet: Int, usage: Long?): String? {
        var theoMa: String? = null
        for (dong in kl.lineSequence()) {
            val p = dong.substringBefore('#').trim().split(Regex("\\s+"))
            if (p.size < 3 || p[0] != "key") continue
            if (p[1] == "usage") {
                if (p.size >= 4 && usage != null && so(p[2]) == usage && NHAN.matches(p[3])) return p[3]
            } else if (theoMa == null && so(p[1]) == maQuet.toLong() && NHAN.matches(p[2])) theoMa = p[2]
        }
        return theoMa
    }

    private val NHAN = Regex("[A-Z][A-Z0-9_]*")
    private fun so(s: String): Long? = if (s.startsWith("0x", ignoreCase = true)) s.substring(2).toLongOrNull(16) else s.toLongOrNull()

    /** Mục app_keys cho [nut] mở [goi]; null nếu không gán được (mã quét chung mà keylayout không có nhãn). */
    fun muc(nut: Nut, goi: String): String? = when {
        nut.maQuet > 0 && nut.maQuet != MA_CHUNG -> "scan:${nut.maQuet}:$goi"
        nut.tenAndroid != null -> "KEYCODE_${nut.tenAndroid}:$goi"
        else -> null
    }

    /** Nhãn keylayout của các nút không gán đè: điều hướng, OK, Back, Home, menu, âm lượng, nguồn. */
    private val TEN_CO_BAN = setOf(
        "DPAD_UP", "DPAD_DOWN", "DPAD_LEFT", "DPAD_RIGHT", "DPAD_CENTER", "ENTER", "NUMPAD_ENTER", "BACK", "ESCAPE", "HOME",
        "ALL_APPS", "MENU", "VOLUME_UP", "VOLUME_DOWN", "VOLUME_MUTE", "MUTE", "POWER", "TV_POWER", "SLEEP", "WAKEUP",
    )

    /**
     * Mã quét Linux của các nút đó, khi không đọc được keylayout: ESC, ENTER, KPENTER, HOME, UP, LEFT, RIGHT, DOWN, MUTE,
     * VOLUMEDOWN, VOLUMEUP, POWER, MENU, SLEEP, WAKEUP, BACK, HOMEPAGE, REPLY, OK, SELECT.
     */
    private val MA_CO_BAN = setOf(1, 28, 96, 102, 103, 105, 106, 108, 113, 114, 115, 116, 139, 142, 143, 158, 172, 232, 352, 353)

    fun laNutCoBan(nut: Nut): Boolean = nut.tenAndroid?.let { it in TEN_CO_BAN } ?: (nut.maQuet in MA_CO_BAN)

    /** Phần nút của một mục app_keys: "scan:490", "190", "KEYCODE_BUTTON_3"; mục "hold:" giữ tiền tố. */
    fun khoaCua(muc: String): String {
        val s = muc.trim()
        val giu = s.startsWith("hold:")
        val r = s.removePrefix("hold:").trim()
        val k = if (r.startsWith("scan:")) "scan:" + r.removePrefix("scan:").substringBefore(':').trim() else r.substringBefore(':').trim()
        return if (giu) "hold:$k" else k
    }

    /** Đích của một mục: gói, "gói/màn", "input:…", "intent:…" hay từ hành động. */
    fun dichCua(muc: String): String {
        val r = muc.trim().removePrefix("hold:").trim()
        return (if (r.startsWith("scan:")) r.removePrefix("scan:").substringAfter(':') else r.substringAfter(':')).trim()
    }

    /**
     * Đích "SmartTube – tìm bằng giọng nói" (27/10/2026, anh chủ nhà muốn nút 597 trên remote Skyworth bật tìm kiếm giọng
     * nói của SmartTube): Home sạch (bản vá TiviKit, RemoteKeys.launch) gửi link intent: này. SmartTube nhận
     * `youtube://search?launch=voice` thì mở màn tìm kiếm (IntentExtractor.isStartVoiceCommand), và bật mic ngay nếu trong
     * SmartTube bật Cài đặt → Tìm kiếm → "Tìm kiếm bằng giọng nói tức thì". Home sạch bản cũ không hiểu đích này.
     */
    fun dichTimGiong(goiSmartTube: String): String = "intent://search?launch=voice#Intent;scheme=youtube;package=$goiSmartTube;end"

    fun laTimGiong(dich: String): Boolean = dich.startsWith("intent:") && dich.contains("launch=voice")

    /** Gói app mà đích [dich] mở: chính gói, gói của "gói/màn", package= của "intent:…"; null với input:, guide:, từ hành động. */
    fun goiCuaDich(dich: String): String? = when {
        dich.startsWith("intent:") -> Regex(""";package=([A-Za-z0-9_.]+);""").find(dich)?.groupValues?.get(1)
        dich.startsWith("input:") || dich.startsWith("guide:") || '.' !in dich -> null
        else -> dich.substringBefore('/')
    }

    /** Các mục gán riêng ghi trong config.json. */
    fun docTu(j: JSONObject): List<String> = chu(j.optJSONArray(KHOA))

    private fun chu(a: JSONArray?): List<String> = if (a == null) emptyList()
        else (0 until a.length()).mapNotNull { a.opt(it) as? String }.map { it.trim() }.filter { it.isNotEmpty() }

    /**
     * Đưa danh sách gán riêng [ds] vào cấu hình [j] (sửa tại chỗ, trả lại chính nó): bỏ các mục gán riêng cũ và mục khác
     * cùng nút khỏi app_keys, thêm [ds] vào cuối (trùng nút thì launcher lấy mục sau cùng). Phím riêng cần dịch vụ lọc
     * phím: key_filter=false (TCL Google TV, để nút âm lượng giữ còn lặp) thì bỏ, nhớ lại để trả khi hết phím riêng.
     */
    fun apVao(j: JSONObject, ds: List<String>): JSONObject {
        val cu = docTu(j).toSet()
        val nut = ds.map { khoaCua(it) }.toSet()
        val giu = chu(j.optJSONArray("app_keys")).filter { it !in cu && khoaCua(it) !in nut }
        if (giu.isEmpty() && ds.isEmpty()) j.remove("app_keys") else j.put("app_keys", JSONArray(giu + ds))
        if (ds.isEmpty()) {
            j.remove(KHOA)
            if (j.optBoolean(KHOA_BO_TAT_LOC)) { j.remove(KHOA_BO_TAT_LOC); j.put("key_filter", false) }
        } else {
            j.put(KHOA, JSONArray(ds))
            if (j.has("key_filter") && !j.optBoolean("key_filter", true)) { j.remove("key_filter"); j.put(KHOA_BO_TAT_LOC, true) }
        }
        return j
    }
}
