package vn.tivikit.dulieu

/**
 * Quản lý launcher (27/10/2026, anh chủ nhà muốn quản lý tập trung các launcher trên tivi, sắp dùng thêm Projectivy):
 * đọc các app nhận nút Home từ chữ lệnh shell, phân loại, đoán nút Home đang mở gì và launcher nào chặn launcher khác
 * làm Home mặc định.
 *
 * Android chọn Home thế nào (ResolveIntentHelper.chooseBestActivity): màn HOME đang bật có mức ưu tiên bộ lọc cao nhất
 * thắng luôn; chỉ khi nhiều màn cùng mức cao nhất thì Home mặc định (`cmd package set-home-activity`) mới có tác dụng.
 * Launcher của hãng thường có mức cao (Google TV: launcherx 2, setupwraith 1; Android TV: tvlauncher 2), launcher tự cài
 * luôn ở mức 0 — nên muốn Projectivy hay Home sạch làm Home mặc định thì phải tắt Home của hãng, mà Google TV còn
 * setupwraith thì không tắt được (sự cố 1.1). Bật thêm một launcher cùng mức cũng làm Android quên Home mặc định, hỏi
 * chọn lại ở lần bấm Home sau.
 * Chạy lệnh: adb/ThaoTacTv.kt (docLauncher, datHome); màn hình: ui/QuanLyLauncher.kt.
 */
object Launcher {
    /** Thứ tự là thứ tự trên màn hình: Home sạch, launcher tự cài (Projectivy…), Home của hãng. */
    enum class Loai { HOME_SACH, CAI_THEM, GOC }

    /** Một màn HOME: "gói/lớp" và mức ưu tiên bộ lọc HOME của nó. */
    data class ManHome(val cmp: String, val uuTien: Int) {
        val goi: String get() = cmp.substringBefore('/')
    }

    /** Một launcher: [cmp] màn HOME (gói đang tắt trên Android 10 trở xuống thì chưa biết, null), [uuTien] mức ưu tiên. */
    data class Muc(val goi: String, val cmp: String?, val uuTien: Int, val loai: Loai, val dangTat: Boolean)

    data class TrangThai(
        val ds: List<Muc>,
        /** Mọi màn HOME đang bật, cả màn không phải launcher (setupwraith cũng chặn), theo thứ tự tivi trả về. */
        val batNay: List<ManHome>,
        /** Home mặc định của Android ("gói/lớp"); gói "android" = tivi đang hỏi chọn Home; "" = không đọc được. */
        val macDinh: String,
        /**
         * Gói nút Home mở (đoán): gói của [macDinh], hoặc khi Home sạch đang mở đè lên Home mặc định thì Home sạch / launcher
         * nó được báo mở thay ([dichHome]).
         */
        val homeMo: String,
        /** stock_home_redirect của Home sạch: gói Home cũ mà Home sạch mở đè mỗi khi nó hiện ra; "" nếu không. */
        val chuyenDe: String,
        /** home_target của Home sạch ("gói/lớp"): launcher Home sạch mở thay chính nó; "" nếu không. */
        val dichHome: String,
        /** Khoá [KHOA_GIANH_HOME] đang bật trong config.json của Home sạch. */
        val khoaGianh: List<String>,
        /** Launcher khác Home sạch đang bật dịch vụ trợ năng: có thể tự bắt nút Home (Projectivy "ghi đè launcher"). */
        val troNangKhac: List<String>,
        /** Dịch vụ trợ năng của từng launcher khác Home sạch ("gói/lớp" đầy đủ, [dayDu]). */
        val troNangCua: Map<String, List<String>>,
        /** Dịch vụ trợ năng đang chạy ("gói/lớp" đầy đủ). */
        val troNangDangBat: Set<String>,
        /** Google TV còn bật setupwraith: Home Google không bao giờ được tắt. */
        val setupBat: Boolean,
    ) {
        val goiMacDinh: String get() = macDinh.substringBefore('/')
        val dangHoiChon: Boolean get() = goiMacDinh == "android"
        fun cua(goi: String): Muc? = ds.firstOrNull { it.goi == goi }
    }

    /**
     * Khoá config.json làm Home sạch tự mở chính nó: mở đè Home gốc, bắt mọi lần bấm Home, bật tivi là về Home sạch.
     * Người dùng chọn launcher khác làm Home thì gỡ (ThaoTacTv.datHome), không thì Home sạch mở đè lên launcher đó.
     */
    val KHOA_GIANH_HOME = listOf("stock_home_redirect", "power_on_home_always", "power_on_home", "home_long_press_home")

    /**
     * Màn cài đặt riêng của launcher tự cài (action mở được từ ngoài): Projectivy 4.72 khai tv.projectivy.SETTINGS — nơi
     * bật "Ghi đè trình khởi chạy hiện tại" (app_settings_launcher_override_home).
     */
    val CAI_DAT = mapOf("com.spocky.projengmenu" to "tv.projectivy.SETTINGS")

    /**
     * Launcher gắn widget (đồng hồ, thời tiết…) mà tivi không có hộp hỏi "cho phép tạo widget": cấp sẵn bằng
     * `appwidget grantbind` (LMATV cũng làm vậy cho ATV Launcher Pro).
     */
    val WIDGET = setOf("ca.dstudio.atvlauncher.pro")

    /** "gói/.Lớp" → "gói/gói.Lớp" để so dịch vụ trợ năng ghi theo hai kiểu. */
    fun dayDu(cmp: String): String {
        val g = cmp.substringBefore('/')
        val l = cmp.substringAfter('/', "")
        return if (l.startsWith(".")) "$g/$g$l" else cmp
    }

    /** Nhận Home nhưng không phải màn Home dùng được (ThaoTacTv.KHONG_PHAI_HOME, cộng hộp chọn của hệ thống). */
    private val KHONG_PHAI = setOf(
        "android", "com.google.android.tungsten.setupwraith", "com.android.tv.settings", "com.android.settings", "com.android.provision",
    )
    /** Trình cài đặt ban đầu, app kiểm tra xuất xưởng: cũng khai HOME (Xiaomi FactoryLauncher) nhưng không phải launcher. */
    private val TEN_KHONG_PHAI = Regex("factorytest|factory_test|factorymode|setupwizard|setupwraith|provision|oobe", RegexOption.IGNORE_CASE)

    fun laLauncher(goi: String, lop: String = ""): Boolean =
        goi !in KHONG_PHAI && !TEN_KHONG_PHAI.containsMatchIn(goi) && !lop.endsWith("FallbackHome")

    private const val HOME = "-a android.intent.action.MAIN -c android.intent.category.HOME"
    private const val SETUP = "com.google.android.tungsten.setupwraith"

    /**
     * Một lệnh cho cả lượt; mỗi phần sau một dòng dấu. `--query-flags 512` (MATCH_DISABLED_COMPONENTS) liệt kê cả launcher
     * đang tắt, chỉ có từ Android 10–11 (TX3 Mini Android 9 báo "Unknown option"): khi đó dùng [LENH_TAT_CO_HOME].
     */
    fun lenhDoc(goiHomeSach: String): String = listOf(
        "echo @@BAT", "cmd package query-activities --brief $HOME",
        "echo @@TAT", "cmd package query-activities --brief --query-flags 512 $HOME 2>&1",
        "echo @@MD", "cmd package resolve-activity --brief $HOME",
        "echo @@D", "pm list packages -d",
        "echo @@S", "pm list packages -s",
        "echo @@A", "settings get secure enabled_accessibility_services; settings get secure accessibility_enabled",
        "echo @@SETUP", "pm list packages -e $SETUP",
        "echo @@TN", "cmd package query-services --brief -a android.accessibilityservice.AccessibilityService 2>/dev/null",
        // config.json không có dòng mới ở cuối: echo để dấu sau đứng riêng một dòng (thử trên Skyworth, TX3 27/10/2026).
        "echo @@CH", "(content read --uri content://$goiHomeSach.theme/config.json 2>/dev/null || cat /sdcard/Android/data/$goiHomeSach/files/config.json 2>/dev/null)",
        "echo", "echo @@HET",
    ).joinToString("; ")

    /**
     * Android 9 trở xuống (không có --query-flags): gói đang tắt nào có màn HOME, kèm mức ưu tiên đọc từ dumpsys (dòng
     * "pkg mPriority=2"). Tìm đúng dòng Category của bảng màn (không lẫn khối <queries> của app khác).
     */
    const val LENH_TAT_CO_HOME = "for p in \$(pm list packages -d | cut -d: -f2); do " +
        "d=\$(dumpsys package \$p 2>/dev/null | grep -A12 'Category: \"android.intent.category.HOME\"'); " +
        "[ -n \"\$d\" ] && echo \"\$p \$(echo \"\$d\" | grep -m1 -o 'mPriority=-*[0-9]*')\"; done; true"

    private val THANH_PHAN = Regex("""^([A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)*)/([A-Za-z0-9_.$]+)$""")
    private val UU_TIEN = Regex("""^priority=(-?\d+)""")

    /**
     * Đọc chữ `query-activities --brief` / `resolve-activity --brief`: mỗi màn là dòng "priority=N preferredOrder=…" rồi dòng
     * "gói/lớp". Không có dòng priority (bản Android khác) thì coi là 0.
     */
    fun docQuery(chu: String): List<ManHome> {
        val ds = mutableListOf<ManHome>()
        var uuTien = 0
        for (dong in chu.lines()) {
            val l = dong.trim()
            val u = UU_TIEN.find(l)
            if (u != null) uuTien = u.groupValues[1].toInt()
            else if (THANH_PHAN.matches(l)) { ds += ManHome(l, uuTien); uuTien = 0 }
        }
        return ds
    }

    /** Phần [ten] trong chữ lenhDoc (giữa dòng "@@ten" và dòng dấu kế tiếp); null nếu không có. */
    private fun phan(chu: String, ten: String): String? {
        val dong = chu.replace("\r", "").lines()
        val dau = dong.indexOfFirst { it.trim() == "@@$ten" }.takeIf { it >= 0 } ?: return null
        val cuoi = (dau + 1 until dong.size).firstOrNull { Regex("""^@@[A-Z]+$""").matches(dong[it].trim()) } ?: dong.size
        return dong.subList(dau + 1, cuoi).joinToString("\n")
    }

    private fun goiTrongDs(chu: String?): Set<String> =
        chu.orEmpty().lines().map { it.trim().removePrefix("package:") }.filter { it.isNotEmpty() && '.' in it && ' ' !in it }.toSet()

    /** Lượt đọc này thiếu danh sách màn HOME kể cả đang tắt (Android 9 trở xuống): chạy thêm [LENH_TAT_CO_HOME]. */
    fun thieuManTat(chu: String): Boolean = phan(chu, "TAT")?.contains("activities found", ignoreCase = true) != true

    /** Chữ của [LENH_TAT_CO_HOME] → gói → mức ưu tiên (không đọc được thì 0). */
    fun docTatCoHome(chu: String): Map<String, Int> = chu.lines().mapNotNull { l ->
        val p = l.trim().split(Regex("\\s+"))
        val goi = p.firstOrNull()?.takeIf { '.' in it && !it.contains('/') } ?: return@mapNotNull null
        goi to (p.getOrNull(1)?.removePrefix("mPriority=")?.toIntOrNull() ?: 0)
    }.toMap()

    /**
     * Ghép trạng thái từ chữ lenhDoc ([chu]) và, trên Android 9 trở xuống, chữ LENH_TAT_CO_HOME ([tatDump]). [homeSach]: tên
     * gói Home sạch (hai bản: thường và Xiaomi), [goiHomeSach] bản đang dùng trên tivi này.
     */
    fun tuChu(chu: String, goiHomeSach: String, homeSach: Set<String>, tatDump: String? = null): TrangThai {
        val bat = docQuery(phan(chu, "BAT").orEmpty())
        val caTat = if (thieuManTat(chu)) null else docQuery(phan(chu, "TAT").orEmpty())
        val goiTat = goiTrongDs(phan(chu, "D"))
        val heThong = goiTrongDs(phan(chu, "S"))
        fun loai(g: String) = when { g in homeSach -> Loai.HOME_SACH; g in heThong -> Loai.GOC; else -> Loai.CAI_THEM }

        val muc = LinkedHashMap<String, Muc>()
        for (m in bat) if (m.goi !in muc && m.goi !in goiTat && laLauncher(m.goi, m.cmp)) muc[m.goi] = Muc(m.goi, m.cmp, m.uuTien, loai(m.goi), dangTat = false)
        // Gói tắt cả gói thì hiện để bật lại; màn tắt lẻ trong gói đang bật (chế độ "chỉ ứng dụng" của Google TV, HomeAlias
        // của Sideload Launcher chưa bật) thì bỏ: app không quản lý từng màn.
        for (m in caTat.orEmpty()) if (m.goi !in muc && m.goi in goiTat && laLauncher(m.goi, m.cmp)) muc[m.goi] = Muc(m.goi, m.cmp, m.uuTien, loai(m.goi), dangTat = true)
        if (caTat == null && tatDump != null) for ((g, u) in docTatCoHome(tatDump))
            if (g !in muc && g in goiTat && laLauncher(g)) muc[g] = Muc(g, null, u, loai(g), dangTat = true)

        val macDinh = phan(chu, "MD").orEmpty().lines().map { it.trim() }.lastOrNull { THANH_PHAN.matches(it) }.orEmpty()
        val a = phan(chu, "A").orEmpty().lines().map { it.trim() }.filter { it.isNotEmpty() }
        val troNang = a.getOrNull(0)?.takeIf { it != "null" }.orEmpty()
        val troNangBat = a.getOrNull(1) == "1"
        val dichVu = if (troNangBat) troNang.split(':').filter { it.contains('/') } else emptyList()
        val cauHinh = runCatching { org.json.JSONObject(phan(chu, "CH").orEmpty().trim()) }.getOrNull()
        val chuyenDe = cauHinh?.optString("stock_home_redirect")?.takeIf { it.isNotBlank() && it != "null" }.orEmpty()
        // Khoá bật: chuỗi không rỗng hoặc true (power_on_home_always…); false / "" coi như tắt.
        val khoaGianh = KHOA_GIANH_HOME.filter { k -> cauHinh?.opt(k)?.let { it == true || (it is String && it.isNotBlank()) } == true }
        val homeSachBat = muc[goiHomeSach]?.dangTat == false
        val phimHome = dichVu.any { it.startsWith("$goiHomeSach/") && it.endsWith("HomeKeyService") }
        val goiMacDinh = macDinh.substringBefore('/')
        val dichHome = cauHinh?.optString("home_target")?.takeIf { it.contains('/') }.orEmpty()
        // Home sạch mở đè: mở launcher nó được báo (home_target) nếu launcher đó còn bật, không thì chính nó.
        val dichMo = dichHome.substringBefore('/').takeIf { g -> g != goiHomeSach && muc[g]?.dangTat == false }
        val homeMo = if (homeSachBat && phimHome && chuyenDe.isNotEmpty() && chuyenDe == goiMacDinh) dichMo ?: goiHomeSach else goiMacDinh
        val troNangKhac = dichVu.map { it.substringBefore('/') }.distinct().filter { g -> muc[g]?.let { it.loai != Loai.HOME_SACH && !it.dangTat } == true }
        val troNangCua = phan(chu, "TN").orEmpty().lines().map { it.trim() }.filter { THANH_PHAN.matches(it) }.map { dayDu(it) }
            .groupBy { it.substringBefore('/') }.filterKeys { g -> muc[g]?.let { it.loai != Loai.HOME_SACH } == true }

        return TrangThai(
            ds = muc.values.sortedWith(compareBy<Muc>({ it.loai.ordinal }, { it.dangTat }, { it.goi })),
            batNay = bat, macDinh = macDinh, homeMo = homeMo, chuyenDe = chuyenDe, dichHome = dichHome, khoaGianh = khoaGianh,
            troNangKhac = troNangKhac, troNangCua = troNangCua, troNangDangBat = dichVu.map { dayDu(it) }.toSet(),
            setupBat = goiTrongDs(phan(chu, "SETUP")).contains(SETUP),
        )
    }

    /** Các gói có màn HOME đang bật mức ưu tiên cao hơn [m]: Android luôn chọn chúng, đặt [m] làm Home mặc định vô ích. */
    fun chan(m: Muc, tt: TrangThai): List<String> = tt.batNay
        .filter { it.goi != m.goi && it.uuTien > m.uuTien && !it.cmp.endsWith("FallbackHome") && it.goi != "android" }
        .map { it.goi }.distinct()

    /** Vì sao không tắt được một launcher ở màn này. */
    enum class LyDoGiu {
        /** Home sạch: tắt là mất cả nút remote gán riêng, ô Kênh nhanh; gỡ ở Khôi phục. */
        HOME_SACH,
        /** Đang là Home mặc định / nút Home đang mở nó: đặt launcher khác làm Home trước. */
        DANG_LA_HOME,
        /** Launcher bật cuối cùng: tắt là tivi không còn Home. */
        CON_MOT,
        /** Home Google khi còn setupwraith: tắt là tivi kẹt ở màn cài đặt ban đầu (sự cố 1.1). */
        GOOGLE_TV,
        /** Home của bo mạch CVTE: đã có tivi treo ở màn khởi động sau khi tắt. */
        CVTE,
        /** Không phải launcher (setupwraith, Cài đặt…), không bao giờ tắt ở đây. */
        HE_THONG,
    }

    /** [goiGocBiet]: Home gốc app biết (ThaoTacTv.HOME_GOC_BIET). null = tắt được (tatGoi còn các lớp bảo vệ chung). */
    fun lyDoGiu(goi: String, tt: TrangThai, goiGocBiet: Collection<String>): LyDoGiu? {
        val m = tt.cua(goi)
        return when {
            m?.loai == Loai.HOME_SACH -> LyDoGiu.HOME_SACH
            !laLauncher(goi) -> LyDoGiu.HE_THONG
            goi in goiGocBiet && tt.setupBat -> LyDoGiu.GOOGLE_TV
            KeHoach.laHomeCvte(goi) -> LyDoGiu.CVTE
            goi == tt.goiMacDinh || goi == tt.homeMo -> LyDoGiu.DANG_LA_HOME
            tt.ds.none { !it.dangTat && it.goi != goi } -> LyDoGiu.CON_MOT
            else -> null
        }
    }

    /**
     * Launcher [chan] (mức ưu tiên cao hơn) có tắt được để launcher khác làm Home không: được khi không phải setupwraith /
     * Cài đặt, không phải Home Google lúc còn setupwraith, không phải Home bo CVTE. Đang là Home cũng được: tắt nó chính là
     * để nhường Home (ThaoTacTv.datHome canh gác trên tivi và tự trả lại nếu hỏng).
     */
    fun tatDuocDeNhuong(chan: String, tt: TrangThai, goiGocBiet: Collection<String>): Boolean =
        laLauncher(chan) && !(chan in goiGocBiet && tt.setupBat) && !KeHoach.laHomeCvte(chan) && tt.cua(chan)?.loai != Loai.HOME_SACH

    /**
     * Bật lại [m] sẽ làm nó thành Home (mức ưu tiên cao hơn Home hiện tại, VD tvlauncher mức 2 trên TX3 Mini khi Home sạch
     * mức 0): màn hình hỏi trước khi bật.
     */
    fun batLaGianhHome(m: Muc, tt: TrangThai): Boolean {
        val hien = tt.batNay.firstOrNull { it.goi == tt.goiMacDinh }?.uuTien ?: return false
        return m.dangTat && m.uuTien > hien
    }
}
