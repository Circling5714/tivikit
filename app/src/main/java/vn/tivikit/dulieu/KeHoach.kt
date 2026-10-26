package vn.tivikit.dulieu

import vn.tivikit.t

/** Nhóm hiển thị trên màn chọn gói. */
enum class LoaiMuc(private val vi: Pair<String, String>, private val en: Pair<String, String>) {
    MAU("Theo danh sách đã thử" to "Người dùng cùng dòng tivi đã tắt và chạy ổn.",
        "From a tested list" to "Owners of this TV model disabled these and it kept working."),
    GOOGLE_HOME("Google Home & truyền từ điện thoại" to "Tắt là app Google Home trên điện thoại không thấy tivi, không điều khiển hay cast được. Chỉ tích nếu nhà không dùng.",
        "Google Home & casting from phones" to "Disable these and the Google Home phone app can't find, control or cast to the TV. Tick only if nobody uses that."),
    GIONG_NOI("Tìm kiếm giọng nói & Trợ lý Google" to "Tắt là nút mic trên remote hết dùng được (chỉ còn tìm bằng giọng trong YouTube). Chỉ tích nếu nhà không dùng.",
        "Voice search & Google Assistant" to "Disable these and the remote's mic button stops working (voice still works inside YouTube). Tick only if nobody uses it."),
    UNG_DUNG("Ứng dụng có biểu tượng" to "Hỏi chủ nhà: chỉ tích app họ KHÔNG dùng.",
        "Apps with an icon" to "Ask the owner: tick only apps they do NOT use."),
    CHUNG("Gói hệ thống hay bị tắt" to "Đã tắt trên nhiều mẫu tivi khác.",
        "Commonly disabled system packages" to "Disabled on many other TV models.");

    val tieuDe: String get() = t(vi.first, en.first)
    val moTa: String get() = t(vi.second, en.second)
}

data class MucGoi(
    val pkg: String,
    val loai: LoaiMuc,
    val nhom: String,
    val ghiChu: String,
    val soMauTat: Int,
    val soMauGiu: Int,
    val chonSan: Boolean,
)

/** Ảnh chụp trạng thái gói trên tivi, đủ để lập kế hoạch. */
data class TrangThaiGoi(
    val caiDat: Set<String>,
    val daTat: Set<String>,
    val coBieuTuong: Set<String>,
    val homeHienTai: String,
    val banPhim: Set<String>,
)

object KeHoach {
    /**
     * Gói người dùng TỰ tích mà nhiều danh sách ghi phải giữ (23/10/2026: TCL BeyondTV4 tắt com.tcl.suspension = nút
     * Input/bảng nhanh, 30 danh sách giữ). App không tích sẵn những gói này; bấm Dọn thì hỏi lại.
     */
    const val GIU_CANH_BAO = 10
    fun nenGiu(m: MucGoi): Boolean = !m.chonSan && m.soMauGiu >= GIU_CANH_BAO && m.soMauGiu > m.soMauTat


    /** Không bao giờ đưa vào danh sách tắt: tivi cần để chạy, hoặc app này cần để làm việc. */
    private val GIU_CHINH_XAC = setOf(
        "android", "com.android.systemui", "com.android.vending", "com.google.android.gms",
        "com.google.android.gsf", "com.android.bluetooth", "com.android.tv.settings", "com.android.settings",
        "com.android.shell", "com.android.phone", "com.android.packageinstaller",
        "com.google.android.packageinstaller", "com.android.permissioncontroller",
        "com.google.android.permissioncontroller", "com.android.inputdevices", "com.google.android.webview",
        "com.android.webview", "com.android.externalstorage", "com.android.keychain", "com.android.location.fused",
        "com.google.android.tv.remote.service", "com.google.android.tvlauncher",
        "com.google.android.apps.tv.launcherx", "com.google.android.leanbacklauncher", "com.mitv.tvhome",
        "com.gathin.tvhome", "com.ts.launcher",
    )
    private val GIU_DAU = listOf("com.android.providers.", "com.android.networkstack", "com.google.android.networkstack")

    /**
     * Gói lõi của Android (upstream 22dc48f, #884: Mi TV Stick không khởi động lại được sau khi một danh sách tắt chúng):
     * ext services/shared, module metadata, overlay mainline. Trình cấp quyền / cài app đã nằm trong GIU_CHINH_XAC.
     */
    private val GIU_LOI = Regex("""^com\.(google\.)?android\.(ext\.services|ext\.shared|modulemetadata)$|^com\.google\.android\.overlay\.modules""")
    fun laGoiLoi(pkg: String) = GIU_LOI.containsMatchIn(pkg)

    /**
     * Khối gói lõi của repo CleanYourTV (tools/disable.sh CORE_RE / CORE_OK, tests/test_core_packages.py, #884), chép
     * nguyên văn: tatGoi từ chối mọi gói khớp ngay trước lệnh tắt, dù gói đến từ danh sách mẫu, luật chung hay người
     * dùng tự tích (23/10/2026, Gathin muốn CleanTV có đủ các chốt an toàn của repo).
     */
    private val CORE_RE = Regex("""^(android|com\.android\.systemui|com\.android\.shell|com\.android\.phone|com\.android\.settings|com\.android\.tv\.settings|com\.(google\.)?android\.(permissioncontroller|packageinstaller|ext\.services|ext\.shared|modulemetadata)|com\.google\.android\.overlay\.modules.*|com\.android\.providers\..*)$""")
    private val CORE_OK = Regex("""^com\.android\.providers\.(calendar|contacts|userdictionary|blockednumber)$""")
    fun laGoiLoiAndroid(pkg: String) = CORE_RE.matches(pkg) && !CORE_OK.matches(pkg)

    /** Tên kiểu này thường là đường hình HDMI, remote, bàn phím: chỉ tắt khi danh sách đúng mẫu ghi rõ. */
    private val TU_NGUY_HIEM = listOf(
        "tvinput", "tvservice", "tv.service", "inputmethod", "bluetooth", "rcu", "remote", "autopair",
        "keyboard", "ime", "livetv", "tvcenter", "hdmi", "cec", "setupwizard", "provision",
    )

    /**
     * Gói để Google Home trên điện thoại thấy / điều khiển / cast tới tivi (22/10/2026: người dùng báo dọn xong Google
     * Home không bắt được tivi — mediashell có trong 31/213 danh sách). Luôn tách nhóm riêng, KHÔNG BAO GIỜ tự tích,
     * kể cả khi danh sách đúng mẫu có. Sony seeds.iot / Shield ask = điều khiển nhà thông minh (Google Home, Alexa).
     */
    private val GOOGLE_HOME = Regex("""^com\.google\.android\.apps\.mediashell$|chromecast|googlecast|seeds\.iot|shield\.ask|^com\.google\.android\.tungsten\.setupwraith$""")
    fun laGoiGoogleHome(pkg: String) = GOOGLE_HOME.containsMatchIn(pkg)

    /** Nút mic / tìm bằng giọng nói (22/10/2026: Mi Box S mất giọng nói vì danh sách tắt katniss "personal choice"). */
    private val GIONG_NOI = Regex("""^com\.google\.android\.katniss$|^com\.google\.android\.speech\.pumpkin$|^com\.google\.android\.(apps\.)?tv\.assistant$|^com\.google\.android\.googlequicksearchbox$""")

    /**
     * Nhóm trong danh sách đúng mẫu mà người làm danh sách ghi là tắt THEO Ý RIÊNG ("off by choice", "personal choice",
     * "optional"…): ~1.100 dòng trên 213 danh sách. Không tự tích — mỗi nhà một khác.
     */
    private val THEO_Y_RIENG = Regex("""(by|owner'?s?|personal)\s+choice|\boptional\b(?! (vendor|home-automation))|not used|does not use|keep what you use|comment out what you use|if you (don't|do not) use""", RegexOption.IGNORE_CASE)

    // Sony bo SVP-DTV15 (BRAVIA 2015, Android 8): Cài đặt đọc provider hotelsettings của b2b.hotelmode, tắt là Cài đặt
    // mở ra màn trống (upstream examples/sony-kdl-43w950d; CleanTV 2.10.2). Đời Sony mới tắt được, nên chỉ không tự tích.
    /** Gói Sony nằm sau màn Cài đặt (theo upstream sony-kdl-43w950d). shopsettings / b2b.prosettings là chế độ cửa hàng, tắt được. */
    private val SONY_CAI_DAT = Regex("""^com\.sony\.dtv\.(?!shopsettings$|b2b\.prosettings$)(b2b\.hotelmode|.*settings?.*|softwarelicense|homenetwork|handlingnetworksetup|quicksetup)$""")

    private val KHONG_TU_TICH = setOf("com.google.android.backdrop", "com.google.android.apps.tv.dreamx", "com.sony.dtv.b2b.hotelmode")

    /** Máy chiếu: gói lo lấy nét, chỉnh hình, motor — không bao giờ tắt (21/10/2026). */
    private val GIU_MAY_CHIEU = Regex("focus|keystone|trapez|tixing|jiaozheng|duijiao|motor|calibr|projector|projection", RegexOption.IGNORE_CASE)

    /**
     * Home gốc của bo mạch CVTE (JVC, Vision, Super General, Artel…): tắt nó cùng lúc bật dịch vụ bắt phím làm JVC
     * LT-43N7115 treo ở màn khởi động, mất cả ADB (upstream #741, 22/10/2026). Không bao giờ tắt, kể cả khi danh sách
     * đúng mẫu ghi tắt — Home sạch chỉ đặt làm mặc định.
     */
    fun laHomeCvte(pkg: String) = pkg.startsWith("com.cvte.tv.launcher")

    fun laGoiCanGiu(pkg: String, tt: TrangThaiGoi): Boolean =
        pkg in GIU_CHINH_XAC || GIU_DAU.any { pkg.startsWith(it) } || pkg in tt.banPhim || pkg == tt.homeHienTai ||
            GIU_MAY_CHIEU.containsMatchIn(pkg) || laHomeCvte(pkg) || laGoiLoi(pkg)

    fun lap(du: DuLieu, mau: MauTv?, tt: TrangThaiGoi): List<MucGoi> {
        val ketQua = LinkedHashMap<String, MucGoi>()
        fun conBat(p: String) = p in tt.caiDat && p !in tt.daTat

        // 1) Danh sách đúng mẫu: tin theo người đã thử, nhưng app có biểu tượng vẫn để chủ nhà chọn.
        mau?.goi?.forEach { g ->
            val p = g.pkg
            if (!conBat(p) || laGoiCanGiu(p, tt) || p in ketQua) return@forEach
            val tk = du.thongKe[p] ?: ThongKeGoi(0, 0)
            val laApp = p in tt.coBieuTuong
            ketQua[p] = MucGoi(
                pkg = p,
                loai = if (laApp) LoaiMuc.UNG_DUNG else LoaiMuc.MAU,
                nhom = g.nhom,
                ghiChu = if (THEO_Y_RIENG.containsMatchIn(g.nhom))
                    listOf(t("Người làm danh sách tắt theo ý riêng.", "Off by the list author's own choice."), g.ghiChu).filter { it.isNotBlank() }.joinToString(" ")
                else g.ghiChu,
                soMauTat = tk.soMauTat, soMauGiu = tk.soMauGiu,
                chonSan = !laApp && !THEO_Y_RIENG.containsMatchIn(g.nhom),
            )
        }

        // 2) Gói có ở danh sách các mẫu khác.
        for ((p, tk) in du.thongKe) {
            if (p in ketQua || !conBat(p) || laGoiCanGiu(p, tt) || tk.soMauTat == 0) continue
            val laApp = p in tt.coBieuTuong
            val nguyHiem = TU_NGUY_HIEM.any { p.lowercase().contains(it) }
            if (nguyHiem && !laApp) continue
            // Màn hình chờ (ảnh, đồng hồ) nhiều nhà thích dùng: hiện để chọn nhưng không tự tích (21/10/2026).
            val chacChan = tk.soMauTat >= 3 && tk.soMauGiu * 3 < tk.soMauTat && p !in KHONG_TU_TICH
            ketQua[p] = MucGoi(
                pkg = p,
                loai = if (laApp) LoaiMuc.UNG_DUNG else LoaiMuc.CHUNG,
                nhom = "", ghiChu = ghiChuChung(du, p),
                soMauTat = tk.soMauTat, soMauGiu = tk.soMauGiu,
                // Đã có danh sách đúng mẫu thì phần chung chỉ để tham khảo, không tự tích.
                chonSan = !laApp && mau == null && chacChan,
            )
        }

        // 3) App có biểu tượng chưa nằm trong danh sách nào: vẫn liệt kê để chủ nhà ẩn nếu muốn.
        for (p in tt.coBieuTuong) {
            if (p in ketQua || !conBat(p) || laGoiCanGiu(p, tt)) continue
            ketQua[p] = MucGoi(p, LoaiMuc.UNG_DUNG, "", "", 0, 0, chonSan = false)
        }

        // 4) Giọng nói / Trợ lý: nhóm riêng, không tự tích.
        for ((p, m) in ketQua.entries.toList()) {
            if (!GIONG_NOI.containsMatchIn(p)) continue
            val canh = t("Nút mic trên remote cần gói này.", "The remote's mic button needs this.")
            ketQua[p] = m.copy(loai = LoaiMuc.GIONG_NOI, chonSan = false,
                ghiChu = listOf(canh, m.ghiChu.ifBlank { m.nhom }).filter { it.isNotBlank() }.joinToString(" "))
        }

        // 6) Gói gần màn Cài đặt của Sony: không bao giờ tự tích, kể cả khi danh sách đúng mẫu tắt nó (CleanTV 2.10.4: Sony
        // 43W800C reset cứng, dọn lại mà Cài đặt vẫn mở ra trống — đã giữ hotelmode nhưng mẫu khác tắt networksettings…).
        // upstream examples/sony-kdl-43w950d cố ý để nguyên cả nhóm này trên bo SVP-DTV15.
        for ((p, m) in ketQua.entries.toList()) {
            if (!SONY_CAI_DAT.matches(p)) continue
            val canh = t("Màn Cài đặt của tivi Sony có thể cần gói này.", "Sony's Settings screen may need this.")
            ketQua[p] = m.copy(chonSan = false, ghiChu = listOf(canh, m.ghiChu.ifBlank { m.nhom }).filter { it.isNotBlank() }.joinToString(" "))
        }

        // 5) Google Home / cast: nhóm riêng, không tự tích, ghi rõ hậu quả.
        for ((p, m) in ketQua.entries.toList()) {
            if (!laGoiGoogleHome(p)) continue
            val canh = t("Google Home / cast cần gói này.", "Google Home / casting needs this.")
            ketQua[p] = m.copy(loai = LoaiMuc.GOOGLE_HOME, chonSan = false,
                ghiChu = listOf(canh, m.ghiChu.ifBlank { m.nhom }).filter { it.isNotBlank() }.joinToString(" "))
        }

        return ketQua.values.sortedWith(
            compareBy<MucGoi>({ it.loai.ordinal }, { !it.chonSan }, { -it.soMauTat }, { it.pkg })
        )
    }

    /** Ghi chú đầu tiên người khác viết cho gói này, để biết nó là gì. */
    private fun ghiChuChung(du: DuLieu, pkg: String): String = du.ghiChu[pkg] ?: ""
}
