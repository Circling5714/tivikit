package vn.tivikit.adb

import vn.tivikit.t
import vn.tivikit.dulieu.BanSaoLuu
import vn.tivikit.dulieu.CastSmartTube
import vn.tivikit.dulieu.DnsRieng
import vn.tivikit.dulieu.GoChu
import vn.tivikit.dulieu.GoiCu
import vn.tivikit.dulieu.HenGio
import vn.tivikit.dulieu.KeHoach
import vn.tivikit.dulieu.KenhNhanh
import vn.tivikit.dulieu.KenhVtvGo
import vn.tivikit.dulieu.KhoSaoLuu
import vn.tivikit.dulieu.Launcher
import vn.tivikit.dulieu.PhanApk
import vn.tivikit.dulieu.PhimRieng
import vn.tivikit.dulieu.ThongTinTv
import vn.tivikit.dulieu.TiviKitLauncher
import vn.tivikit.dulieu.TrangThaiGoi
import vn.tivikit.dulieu.tenDep

/**
 * Mọi việc làm trên tivi. Thứ tự và các lớp bảo vệ lấy từ tools/disable.sh và
 * launcher/build-and-install.sh của cleanyourtv; ghi chú "(#số)" là số issue bên đó.
 */
class ThaoTacTv(private val adb: KetNoiAdb, private val kho: KhoSaoLuu) {

    /**
     * Tên gói Home sạch trên tivi này. Tivi Xiaomi bản Trung Quốc (Home gốc com.mitv.tvhome) chỉ nhận Home có tên trong
     * danh sách của hãng: dùng bản dựng tên com.ts.launcher (upstream docs/devices/xiaomi-mitv-aetp0-dofus.md), trừ khi
     * tivi đã có app khác mang tên đó. 22/10/2026, MiTV-ASTP0: bản com.gathin.tvhome cài được nhưng nút Home ra màn đen.
     */
    private var pkgHomeDaDo: String? = null
    val pkgHome: String get() = pkgHomeDaDo ?: run {
        val cuaMinh = sh("dumpsys package $PKG_HOME_XIAOMI | grep -c '$PKG_HOME_XIAOMI/$NS_HOME.MainActivity'").trim().toIntOrNull()?.let { it > 0 } == true
        val coTen = sh("pm path $PKG_HOME_XIAOMI").isNotBlank()
        val xiaomi = sh("pm list packages com.mitv.tvhome").lines().any { it.trim() == "package:com.mitv.tvhome" }
        if (cuaMinh || (xiaomi && !coTen)) PKG_HOME_XIAOMI else PKG_HOME
    }.also { pkgHomeDaDo = it }

    /** Gọi khi kết nối tivi mới: quên những gì đã dò của tivi trước. */
    fun quenTivi() { pkgHomeDaDo = null; cvteDaDo = null; cauHinhTruocGo = null; anhKenh = emptyList() }
    val cmpHome get() = "$pkgHome/$NS_HOME.MainActivity"
    /** File APK trong assets cho pkgHome. */
    val apkHome get() = if (pkgHome == PKG_HOME_XIAOMI) "tvhome-xiaomi.apk" else "tvhome.apk"

    /** Khởi động lại Home để đọc cấu hình; bật lại dịch vụ trợ năng nếu force-stop làm rơi (theme.sh). */
    private val KHOI_DONG_LAI_HOME get() =
            "a=\$(settings get secure enabled_accessibility_services); am force-stop $pkgHome; am start -n $cmpHome >/dev/null; " +
                "case \"\$a\" in *$pkgHome*) sleep 1; settings put secure enabled_accessibility_services ''; settings put secure accessibility_enabled 0; " +
                "sleep 1; settings put secure enabled_accessibility_services \"\$a\"; settings put secure accessibility_enabled 1 ;; esac"

    /**
     * Tắt hẳn Home sạch mà không mở lại màn Home, rồi bật lại dịch vụ trợ năng của nó: force-stop làm Android gỡ dịch vụ
     * trợ năng của gói khỏi danh sách (TX3 Mini, Android 9: HomeKeyService mất), nên đọc danh sách trước như trên.
     */
    private val TAT_HAN_HOME get() =
            "a=\$(settings get secure enabled_accessibility_services); am force-stop $pkgHome; " +
                "case \"\$a\" in *$pkgHome*) sleep 1; settings put secure enabled_accessibility_services ''; settings put secure accessibility_enabled 0; " +
                "sleep 1; settings put secure enabled_accessibility_services \"\$a\"; settings put secure accessibility_enabled 1 ;; esac"


    private fun sh(lenh: String): String = adb.shell(lenh).replace("\r", "")

    // ---------- Đọc ----------

    fun docThongTin(): Pair<ThongTinTv, String> {
        val gp = sh("getprop")
        return ThongTinTv.tuGetprop(gp) to gp
    }

    /** Máy đang nối có phải tivi / hộp tivi / máy chiếu không (laTiviTheoFeatures); lệnh lỗi thì coi như tivi. */
    fun laTivi(): Boolean = laTiviTheoFeatures(runCatching { sh("pm list features") }.getOrDefault(""))

    fun docTrangThai(): TrangThaiGoi {
        fun goi(lenh: String) = sh(lenh).lines().mapNotNull { l ->
            l.trim().removePrefix("package:").takeIf { it.isNotEmpty() && TEN_GOI.matches(it) }
        }.toSet()
        val caiDat = goi("pm list packages")
        val daTat = goi("pm list packages -d")
        val coBieuTuong = (lopActivity("android.intent.category.LEANBACK_LAUNCHER") +
            lopActivity("android.intent.category.LAUNCHER"))
        val banPhim = sh("ime list -s; settings get secure default_input_method").lines()
            .mapNotNull { it.trim().substringBefore('/').takeIf { p -> TEN_GOI.matches(p) } }.toSet()
        return TrangThaiGoi(caiDat, daTat, coBieuTuong, homeHienTai().substringBefore('/'), banPhim)
    }

    /** Gói đang tắt và app người dùng tự cài, trong một lệnh (danh sách gửi CleanYourTV, dulieu/GopNguoc). */
    fun goiTatVaTuCai(): Pair<Set<String>, Set<String>> {
        fun ds(s: String) = s.lines().mapNotNull { l -> l.trim().removePrefix("package:").takeIf { it.isNotEmpty() && TEN_GOI.matches(it) } }.toSet()
        val out = sh("pm list packages -d; echo @@; pm list packages -3")
        return ds(out.substringBefore("@@")) to ds(out.substringAfter("@@", ""))
    }

    /** Các gói có activity thuộc category này (app hiện biểu tượng). */
    private fun lopActivity(cat: String): Set<String> =
        sh("cmd package query-activities --brief -a android.intent.action.MAIN -c $cat").lines()
            .mapNotNull { Regex("""^\s*([A-Za-z0-9_.]+)/""").find(it)?.groupValues?.get(1) }.toSet()

    /** Vài dòng chẩn đoán Home (gửi kèm danh sách khi người dùng bấm gửi, 23/10/2026): Home mặc định, app nhận Home, màn đang hiện. */
    fun chanDoanHome(): String = runCatching {
        sh("echo '# resolve'; $HOME_QUERY 2>&1 | tail -2; echo '# query'; cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME 2>&1 | grep '/' | head -12; " +
            "echo '# focus'; dumpsys window 2>/dev/null | grep -m2 -E 'mCurrentFocus|mFocusedApp'; echo '# a11y'; settings get secure enabled_accessibility_services")
            .lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n").take(2500)
    }.getOrDefault("")

    fun homeHienTai(): String = sh(HOME_QUERY).lines().lastOrNull { it.isNotBlank() }?.trim() ?: ""

    fun chupManHinh(): ByteArray = adb.chupPng()

    // ---------- Máy chiếu ----------

    /** Có dấu hiệu là máy chiếu: tên máy hoặc tên gói kiểu focus / keystone / hãng máy chiếu. */
    fun coVeMayChieu(tt: TrangThaiGoi, tv: ThongTinTv): Boolean =
        TU_MAY_CHIEU.containsMatchIn("${tv.brand} ${tv.manufacturer} ${tv.model} ${tv.device}") ||
            tt.caiDat.any { TU_MAY_CHIEU.containsMatchIn(it) }

    /**
     * Dò các màn (activity) của hãng có tên giống lấy nét / chỉnh hình / thu phóng trong mọi gói hệ thống.
     * Một lệnh shell chạy vòng lặp trên chính máy chiếu cho nhanh.
     */
    fun doMayChieu(): List<String> {
        val loc = "focus|keystone|trapez|tixing|jiaozheng|duijiao|correct|projector|projection|zoom|scale|calibr|geometr|ladder|picturesize|screensize"
        val out = sh("for p in \$(pm list packages -s | cut -d: -f2 | grep -v ^com.google); do dumpsys package \$p | grep -oE \"\$p/[A-Za-z0-9_.\\\$]+\" ; done | sort -u | grep -iE '$loc'; true")
        return out.lines().map { it.trim() }.filter { it.contains('/') && TEN_GOI.matches(it.substringBefore('/')) }
            // Chỉ lấy màn (activity): bỏ dịch vụ, bộ nhận, nhà cung cấp — không mở được bằng am start.
            .filterNot { Regex("(Service|Receiver|Provider)$").containsMatchIn(it) }.distinct().take(40)
    }

    /** Mở thử một màn của hãng; trả lỗi nếu Android từ chối. */
    fun moThu(comp: String): String? {
        val out = sh("am start -n '$comp' 2>&1")
        return if (out.contains("Error") || out.contains("Exception")) out.trim().lines().lastOrNull() else null
    }

    // ---------- Sao lưu ----------

    /** Lấy bản sao lưu đã có của tivi này, chưa có thì chụp trạng thái hiện tại và ghi xuống máy. */
    /**
     * Ghi bản sao lưu vào điện thoại VÀ một bản lên chính tivi ([TEP_SAO_LUU_TV]), để gỡ app / đổi điện thoại vẫn khôi
     * phục được (22/10/2026). Bản trên tivi bỏ getprop + danh sách gói (chỉ để tham khảo) cho nhẹ: ghi lại sau mỗi gói.
     * Ghi file tạm rồi mv, mất kết nối giữa chừng không hỏng bản cũ. Lỗi ghi lên tivi không chặn việc đang làm.
     */
    fun luuSaoLuu(b: BanSaoLuu) {
        kho.ghi(b)
        runCatching {
            val gon = b.copy(getprop = "", goiTruoc = emptyList()).toJson().toString().toByteArray()
            adb.ghiFile("$TEP_SAO_LUU_TV.tam", gon)
            sh("mv -f $TEP_SAO_LUU_TV.tam $TEP_SAO_LUU_TV")
        }
    }

    /** Bản sao lưu nằm trên tivi (do điện thoại nào cũng được ghi), đúng tivi này; null nếu không có / hỏng. */
    fun docSaoLuuTrenTv(khoa: String): BanSaoLuu? = runCatching {
        BanSaoLuu.tuJson(org.json.JSONObject(sh("cat $TEP_SAO_LUU_TV 2>/dev/null").trim()))
    }.getOrNull()?.takeIf { it.khoa == khoa }

    fun damBaoSaoLuu(tv: ThongTinTv, getprop: String, ip: String): BanSaoLuu {
        (kho.doc(tv.khoaLuu) ?: docSaoLuuTrenTv(tv.khoaLuu))?.let { cu -> cu.ip = ip; luuSaoLuu(cu); return cu }
        val tt = docTrangThai()
        val b = BanSaoLuu(
            khoa = tv.khoaLuu, ten = tv.tenHienThi, ip = ip, luc = System.currentTimeMillis(),
            daTatTruoc = tt.daTat, homeTruoc = homeHienTai(),
            dnsModeTruoc = sh("settings get global private_dns_mode").trim(),
            dnsHostTruoc = sh("settings get global private_dns_specifier").trim(),
            truyCapTruoc = sh("settings get secure enabled_accessibility_services").trim(),
            getprop = getprop, goiTruoc = tt.caiDat.sorted(),
        )
        luuSaoLuu(b)
        return b
    }

    // ---------- Tắt gói ----------

    data class KetQuaTat(val daTat: List<String>, val boQua: List<Pair<String, String>>, val loi: List<Pair<String, String>>)

    /**
     * Lọc qua các lớp bảo vệ rồi tắt từng gói một (mỗi lệnh pm một lần gọi, như PROMPT.md dặn).
     * [epSetupwraith]: tivi chạy dây mạng, cho tắt setupwraith dù nó giữ Wi-Fi.
     */
    fun tatGoi(
        goi: List<String>,
        sl: BanSaoLuu,
        epSetupwraith: Boolean,
        bao: (String) -> Unit,
    ): KetQuaTat {
        val boQua = mutableListOf<Pair<String, String>>()
        var ds = goi.filter { TEN_GOI.matches(it) }.distinct()

        // Chốt cuối như repo CleanYourTV: không bao giờ tắt gói lõi Android (#884) hay gói đang giữ nút Home (Home guard).
        val homeNay = runCatching { homeHienTai().substringBefore('/') }.getOrDefault("")
        ds.filter { vn.tivikit.dulieu.KeHoach.laGoiLoiAndroid(it) }.forEach {
            boQua += it to t("Gói lõi của Android: tắt có thể làm tivi không khởi động lại được.", "Core Android package: disabling it can stop the TV from booting.")
        }
        if (homeNay.isNotBlank() && homeNay in ds) boQua += homeNay to t("Đang là màn Home của tivi: tắt là không còn Home.", "It is the TV's Home right now: disabling it leaves no Home.")
        ds = ds.filter { p -> boQua.none { it.first == p } }

        // setupwraith: Wi-Fi lưu lúc cài đặt ban đầu thuộc về nó; tắt nó là Android xoá mạng đó và
        // tivi rớt Wi-Fi, mất luôn kết nối này.
        if (SETUP in ds && !epSetupwraith) {
            bao(t("Kiểm tra ai giữ mạng Wi-Fi đã lưu…", "Checking which app owns the saved Wi-Fi…"))
            // Chỉ xét mạng ĐANG kết nối (23/10/2026, Sony VU3: quên + vào lại mạng nhà rồi mà vẫn bị chặn, vì tivi còn lưu
            // mạng cũ do setupwraith tạo). Không đọc được mạng đang dùng thì quay về cách cũ: xét mọi mạng đã lưu.
            val dump = sh("dumpsys wifi")
            val chuDangDung = chuWifiDangDung(dump)
            val dayLan = chuDangDung == null && sh("cat /sys/class/net/eth0/operstate 2>/dev/null").trim() == "up"
            val chu = when {
                dayLan -> "lan"
                chuDangDung != null -> chuDangDung.ifBlank { Regex("cname=[^ ]*").findAll(dump).joinToString(" ") { it.value } }
                else -> Regex("cname=[^ ]*").findAll(dump).joinToString(" ") { it.value }
            }
            val lyDo = when {
                chu.isBlank() -> t("Không đọc được mạng Wi-Fi đã lưu thuộc app nào, giữ lại cho chắc.", "Could not tell which app owns the saved Wi-Fi, keeping it to be safe.")
                chu.contains("setupwraith") -> t("Wi-Fi đang lưu thuộc về nó: tắt là tivi rớt mạng. Vào Cài đặt > Mạng, ", "It owns the saved Wi-Fi: disabling it drops the TV off the network. In Settings > Network, ") +
                    t("quên mạng rồi kết nối lại, sau đó chạy lại.", "forget the network and join it again, then run this again.")
                else -> null
            }
            if (lyDo != null) { boQua += SETUP to lyDo; ds = ds - SETUP }
        }

        // Không tắt app đang làm màn Home khi không còn app nào khác nhận Home (#239).
        val home = homeHienTai().substringBefore('/')
        if (home.isNotEmpty() && home in ds) {
            val khac = sh("cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME")
                .lines().mapNotNull { Regex("""^\s*([A-Za-z0-9_.]+)/([A-Za-z0-9_.$]+)""").find(it) }
                .filterNot { it.groupValues[2].endsWith("FallbackHome") }
                .map { it.groupValues[1] }.toSet() - ds.toSet() - home - KHONG_PHAI_HOME
            if (khac.isEmpty()) {
                boQua += home to t("Đang là màn Home và chưa có Home nào khác. Cài Home sạch trước rồi mới tắt.", "It is the Home screen and nothing else can take Home. Install the clean Home first.")
                ds = ds - home
            }
        }

        // Gói xin quyền TV_INPUT_HARDWARE là lớp phần mềm dưới cổng HDMI/AV/ăng-ten: tắt là cổng
        // báo "Không có tín hiệu" mãi, bật lại từng gói cũng không cứu được (#321).
        if (ds.isNotEmpty()) {
            bao(t("Kiểm tra gói nào điều khiển cổng HDMI…", "Checking which packages drive the HDMI inputs…"))
            val ten = ds.joinToString(" ")
            val hw = sh(
                "for p in $ten; do dumpsys package \$p | sed -n -E '/requested permissions:/,/(install|runtime) permissions:|User [0-9]+:/p' " +
                    "| grep -q TV_INPUT_HARDWARE && echo \$p; done; true"
            ).lines().map { it.trim() }.filter { it in ds }
            hw.forEach { boQua += it to t("Điều khiển cổng HDMI/AV/ăng-ten (TV_INPUT_HARDWARE): tắt là mất hình.", "Drives the HDMI/AV/tuner inputs (TV_INPUT_HARDWARE): disabling it means no picture.") }
            ds = ds - hw.toSet()
        }

        // App MỞ cổng HDMI / kênh (nhận VIEW vnd.android.cursor.item/channel) hay mở Cài đặt: tắt là
        // Home sạch báo "TV này không hỗ trợ" khi chọn HDMI, nút Cài đặt không mở (22/10/2026: Sony KDL-43W800G mất HDMI,
        // Sony W800C không vào Cài đặt). Hỏi chính tivi đang dùng app nào rồi giữ, không đoán theo tên.
        if (ds.isNotEmpty()) {
            val q = "cmd package query-activities --brief"
            val giuHdmi = goiTrongDong(sh("$q -a android.intent.action.VIEW -t vnd.android.cursor.item/channel 2>/dev/null; true"))
            val giuCaiDat = goiTrongDong(sh("$q -a android.settings.SETTINGS 2>/dev/null; $q -a android.intent.action.MAIN -c android.intent.category.PREFERENCE 2>/dev/null; true"))
            ds.filter { it in giuHdmi }.forEach { boQua += it to t("Tivi dùng app này để mở cổng HDMI / kênh truyền hình: tắt là chọn HDMI báo không hỗ trợ.", "The TV opens HDMI inputs / channels with this app: disabling it breaks input switching.") }
            ds.filter { it in giuCaiDat && it !in giuHdmi }.forEach { boQua += it to t("Tivi dùng app này để mở Cài đặt: tắt là nút Cài đặt không mở được.", "The TV opens Settings with this app: disabling it breaks the Settings button.") }
            ds = ds - giuHdmi - giuCaiDat
        }

        val daTat = mutableListOf<String>()
        val loi = mutableListOf<Pair<String, String>>()
        val heThong0 = pidHeThong()
        ds.forEachIndexed { i, p ->
            bao(t("Tắt ${i + 1}/${ds.size}: $p", "Disabling ${i + 1}/${ds.size}: $p"))
            var matKetNoi = false
            var out = try {
                sh("pm disable-user --user 0 $p")
            } catch (e: Exception) {
                if (!laMatKetNoi(e)) t("lỗi: ${e.message}", "error: ${e.message}") else { matKetNoi = true; "" }
            }
            // Tivi đang tắt máy: ADB còn trả lời nhưng dịch vụ gói đã chết ("Failure calling service package: Broken pipe").
            if (DICH_VU_CHET.containsMatchIn(out)) matKetNoi = true
            if (matKetNoi) {
                // 22/10/2026, Sony KD-55X8500D: rớt ADB giữa lúc dọn, app báo "connect() must be called first" rồi
                // dừng. Chờ tivi trả lời lại; tivi vừa khởi động lại thì dừng hẳn, gói vừa tắt là nghi phạm.
                bao(t("Mất kết nối với tivi, đang chờ tivi trả lời lại…", "Lost the connection to the TV, waiting for it to answer…"))
                if (!choTiviTraLoi(bao)) throw MatKetNoiTvException(daTat.size, p)
                val daTatRoi = sh("pm list packages -d").lines().any { it.trim() == "package:$p" }
                if (daTatRoi) { daTat += p; if (p !in sl.daTatTruoc) sl.daTatBoiApp += p; luuSaoLuu(sl) }
                val heThong = pidHeThong()
                if (heThong0 != null && heThong != null && heThong != heThong0)
                    throw TiviKhoiDongLaiException(if (daTatRoi) p else daTat.lastOrNull() ?: p, daTat.size)
                out = if (daTatRoi) "new state: disabled-user" else runCatching { sh("pm disable-user --user 0 $p") }.getOrElse { "lỗi: ${it.message}" }
                if (daTatRoi) return@forEachIndexed
            }
            if (out.contains("disabled-user") || out.contains("new state: disabled")) {
                daTat += p
                if (p !in sl.daTatTruoc) sl.daTatBoiApp += p
                luuSaoLuu(sl) // ghi sau MỖI gói: rớt mạng giữa chừng vẫn khôi phục đúng.
            } else {
                loi += p to out.trim().lines().lastOrNull().orEmpty()
            }
        }
        return KetQuaTat(daTat, boQua, loi)
    }

    /** Tên gói trong kết quả `query-activities --brief` (dòng dạng gói/lớp). */
    private fun goiTrongDong(out: String): Set<String> =
        out.lines().mapNotNull { Regex("""^\s*([A-Za-z_][A-Za-z0-9_.]*)/""").find(it)?.groupValues?.get(1) }.filter { TEN_GOI.matches(it) }.toSet()

    /**
     * PID của system_server: đổi = tivi vừa khởi động lại, kể cả kiểu chỉ khởi động lại phần mềm (tắt nhầm gói làm
     * system_server chết) mà /proc/uptime không về 0.
     */
    private fun pidHeThong(): String? = runCatching { sh("pidof system_server").trim().ifBlank { null } }.getOrNull()

    /**
     * Chờ tivi trả lời lại tới mức dịch vụ gói chạy được: tối đa 12 lần × 8 giây (~1,5 phút, đủ cho tivi khởi động
     * lại xong). Lệnh lỗi kiểu mất kết nối thì nối lại ADB.
     */
    private fun choTiviTraLoi(bao: (String) -> Unit): Boolean {
        repeat(12) { lan ->
            Thread.sleep(8000)
            bao(t("Chờ tivi, lần ${lan + 1}/12…", "Waiting for the TV, try ${lan + 1}/12…"))
            val ok = try { sh("pm path android").contains("package:") } catch (e: Exception) { runCatching { adb.ketNoiLai() }; false }
            if (ok) return true
        }
        return false
    }

    // ---------- Home sạch (launcher) ----------

    /**
     * Cài launcher và giao màn Home cho nó, theo đúng thứ tự của build-and-install.sh:
     * cài → mở thử → tắt Home gốc → đặt Home → kiểm tra, sai thì trả Home gốc lại.
     */
    fun caiLauncher(
        apk: ByteArray,
        sl: BanSaoLuu,
        cauHinh: String,
        anhNen: ByteArray?,
        boAnh: List<Pair<String, ByteArray>>?,
        nutInput: Boolean,
        nutHome: Boolean,
        apkXiaomi: (() -> ByteArray)? = null,
        bao: (String) -> Unit,
    ): String? {
        val home = homeHienTai().substringBefore('/')
        val homeGoc = when {
            home == pkgHome || home in HOME_SACH -> "none"
            // Tivi đang không có Home nào (Home gốc bị tắt từ trước, Android rơi về FallbackHome của Cài đặt — 23/10/2026,
            // Xiaomi MiTV-AXSP0): không có gì để tắt, cài Home sạch thẳng.
            home == "com.android.tv.settings" || home == "com.android.settings" -> "none"
            home in HOME_GOC_BIET -> home
            home == "android" -> {
                val ung = sh("cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME")
                val tim = HOME_GOC_BIET.filter { ung.contains("$it/") }
                if (tim.size == 1) tim[0] else return t("Tivi đang hỏi chọn Home (có ${tim.size} Home gốc). Chọn một Home trên tivi rồi thử lại.", "The TV is asking which Home to use (${tim.size} stock launchers). Pick one on the TV and try again.")
            }
            home == SETUP -> return t("Home đang là trình cài đặt của Google TV (setupwraith). Xem lại phần tắt gói: setupwraith phải tắt cùng Home gốc.", "Home is the Google TV setup wizard (setupwraith). Disable setupwraith together with the stock Home.")
            laHomeHangLa(home) -> home
            // Launcher CÀI THÊM (Dangbei trên tivi nội địa Trung, Projectivy, ATV Launcher…): không thuộc phần mềm gốc,
            // thay được — giữ nó bật, đặt Home sạch làm mặc định; nút Home vẫn về nó thì mới tắt (ghi sao lưu). 22/10/2026.
            laLauncherCaiThem(home) -> home
            else -> return t("Home hiện là '$home', không phải Home gốc app biết. Để an toàn app không đổi.", "Home is '$home', not a stock launcher this app knows. Leaving it alone to be safe.")
        }
        // Home của hãng lạ (Fengmi, máy chiếu, tivi nội địa…): thử giữ nó bật, chỉ đặt Home mặc định + bắt nút Home trên
        // remote; không được mới tắt (21/10/2026, Formovie Nemo: com.tvfengmi.launcher).
        val hangLa = homeGoc != "none" && homeGoc !in HOME_GOC_BIET
        // Bo mạch CVTE: chỉ đặt Home mặc định, không tắt Home gốc, không bật dịch vụ bắt phím cùng lượt (upstream #741).
        val cvte = laBoMachCvte()
        val giuHomeGoc = KeHoach.laHomeCvte(homeGoc)

        bao(t("Đẩy và cài Home sạch (${apk.size / 1024} KB)…", "Pushing and installing the clean Home (${apk.size / 1024} KB)…"))
        var out = caiApkHome(apk, bao)
        if (!out.contains("Success")) {
            // Dự phòng: đẩy file rồi pm install (Android 14 đôi khi treo kiểu cài luồng).
            bao(t("Cài kiểu luồng không được, thử đẩy file…", "Streamed install failed, trying a file push…"))
            if (!out.contains("TIMEOUT")) {
                adb.ghiFile("/data/local/tmp/tvhome.apk", apk)
                out = voiHan(120) { sh("pm install -r /data/local/tmp/tvhome.apk; rm -f /data/local/tmp/tvhome.apk") }
            }
        }
        if (out.contains("TIMEOUT")) return t("Cài Home sạch quá 2 phút chưa xong: Android có thể đang hỏi xác minh ứng dụng (Play Protect) mà màn hỏi bị che. Vào Tuỳ chọn nhà phát triển, tắt \"Xác minh ứng dụng qua USB\" (hoặc tắt quét của Play Protect trong CH Play), rồi thử lại. Home gốc vẫn nguyên.",
            "Installing the clean Home took over 2 minutes: Android may be showing an app-verification prompt (Play Protect) behind the app. In Developer options turn off \"Verify apps over USB\" (or Play Protect scanning), then retry. The stock Home is untouched.")
        if (!out.contains("Success")) {
            return if (out.contains("UPDATE_INCOMPATIBLE"))
                t("Tivi đã có Home sạch ký bằng khoá khác (cài từ máy khác). Gỡ bản cũ trước: pm uninstall $pkgHome.", "The TV has a clean Home signed with another key (installed from another computer). Uninstall it first: pm uninstall $pkgHome.")
            else t("Cài không được: ${out.trim().lines().lastOrNull()}. Home gốc vẫn nguyên.", "Install failed: ${out.trim().lines().lastOrNull()}. The stock Home is untouched.")
        }
        sl.launcherDaCai = true; luuSaoLuu(sl)

        bao(t("Mở thử Home sạch…", "Starting the clean Home…"))
        val start = sh("am start -W -n $cmpHome")
        if (!start.contains("Status: ok") || start.lines().any { it.startsWith("Error") }) {
            // Xiaomi gỡ ngay launcher không có tên trong danh sách của hãng ("Activity class … does not exist" ngay sau
            // Success; CleanTV 3.0.0, MiTV-MOOR2): cài lại bằng bản tên com.ts.launcher mà hãng cho phép.
            val xiaomi = Regex("xiaomi|redmi", RegexOption.IGNORE_CASE).containsMatchIn(sh("getprop ro.product.brand; getprop ro.product.manufacturer"))
            if (apkXiaomi != null && pkgHome == PKG_HOME && xiaomi && start.contains("does not exist")) {
                bao(t("Tivi Xiaomi gỡ mất Home sạch, cài lại bằng tên Xiaomi cho phép…", "The Xiaomi TV removed the clean Home; reinstalling under a name Xiaomi allows…"))
                pkgHomeDaDo = PKG_HOME_XIAOMI
                return caiLauncher(apkXiaomi(), sl, cauHinh, anhNen, boAnh, nutInput, nutHome, null, bao)
            }
            return t("Home sạch không mở được, Home gốc vẫn giữ nguyên.\n$start", "The clean Home did not start; the stock Home is untouched.\n$start")
        }
        sh("pm grant $pkgHome android.permission.ACCESS_FINE_LOCATION 2>/dev/null; true")
        bao(t("Tối ưu cho tivi yếu…", "Optimising for slow TVs…"))
        runCatching { sh("cmd package compile -m speed -f $pkgHome") }
        runCatching { sh(lenhGiuSong(listOf(pkgHome))) }

        fun tatHomeGoc() {
            bao(t("Tắt Home gốc ($homeGoc)…", "Disabling the stock Home ($homeGoc)…"))
            gacThem(homeGoc)
            sh("pm disable-user --user 0 $homeGoc")
            sl.homeGocDaTat = homeGoc
            if (homeGoc !in sl.daTatTruoc) sl.daTatBoiApp += homeGoc
            luuSaoLuu(sl)
        }
        if (homeGoc != "none" && !hangLa && !giuHomeGoc) tatHomeGoc()
        sh("cmd package set-home-activity --user 0 $cmpHome")
        if (hangLa && !giuHomeGoc) {
            // Bắt nút Home trên remote về Home sạch dù hãng có dịch vụ bắt phím riêng (bo CVTE: không, xem trên).
            if (!cvte) batDichVu("HomeKeyService")
            if (!homeHienTai().startsWith("$pkgHome/")) {
                bao(t("Home của hãng vẫn giữ quyền, tắt nó…", "The maker's Home still wins, disabling it…"))
                tatHomeGoc()
                sh("cmd package set-home-activity --user 0 $cmpHome")
            }
        }

        var bayGio = homeHienTai()
        // Hộp "chọn Home" (android/ResolverActivity): tivi còn launcher người dùng tự cài (Launchpad TV, Home TV…) tranh
        // Home, set-home-activity không thắng được (CleanTV 2.10.7, Xiaomi ASTR0 nội địa: app trả Home gốc mỗi lần cài).
        // Tắt các launcher tự cài đó (ghi vào sao lưu, Khôi phục bật lại) rồi đặt lại Home sạch.
        if (bayGio.startsWith("android/") && !giuHomeGoc) {
            val khac = sh("cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME")
                .lines().map { it.trim().substringBefore('/') }.filter { it.isNotBlank() && it != pkgHome }.distinct().filter { laLauncherCaiThem(it) }
            if (khac.isNotEmpty()) {
                bao(t("Tivi có launcher khác tranh Home (${khac.joinToString()}), tắt để Home sạch làm Home…", "Other launchers compete for Home (${khac.joinToString()}), disabling them…"))
                khac.forEach { p ->
                    gacThem(p)
                    sh("pm disable-user --user 0 $p")
                    if (p !in sl.daTatTruoc && p !in sl.daTatBoiApp) sl.daTatBoiApp += p
                }
                luuSaoLuu(sl)
                sh("cmd package set-home-activity --user 0 $cmpHome")
                Thread.sleep(800)
                bayGio = homeHienTai()
            }
        }
        if (!bayGio.startsWith("$pkgHome/") && giuHomeGoc) {
            traHomeGoc(sl)
            return t("Tivi bo mạch CVTE ('${bayGio.ifBlank { "không rõ" }}' vẫn nhận Home). Với loại bo này app không tắt Home gốc vì đã có tivi treo ở màn khởi động sau khi tắt. Tivi vẫn như cũ; Home sạch vẫn mở được từ danh sách ứng dụng.",
                "CVTE board ('${bayGio.ifBlank { "unknown" }}' still claims Home). On this board the app never disables the stock Home: one TV hung on its boot animation after that. The TV is as before; open the clean Home from the app list.")
        }
        if (!bayGio.startsWith("$pkgHome/")) {
            bao(t("Home không chuyển được, trả lại Home gốc…", "Home did not switch, restoring the stock Home…"))
            traHomeGoc(sl)
            // Google TV còn setupwraith (2.6.2): Home gốc đã trả lại, vẫn ghi cấu hình rồi để thuPhimHome cho Home sạch mở đè
            // (damBaoPhimHome → chuyenDe); không được thì nó báo cách quên Wi-Fi.
            if (!(bayGio.startsWith("$SETUP/") || dangBat(SETUP)))
                return t("Home sau khi đổi là '${bayGio.ifBlank { "không rõ" }}'. Đã trả Home gốc, tivi vẫn như cũ. Báo hãng, model tivi cho CleanYourTV (github.com/Gathin23/cleanyourtv) để được thêm hỗ trợ.",
                "Home resolved to '${bayGio.ifBlank { "unknown" }}'. The stock Home is back, the TV is as before. Report the brand and model to CleanYourTV (github.com/Gathin23/cleanyourtv) to get support added.")
        }

        // Xiaomi: bản com.gathin.tvhome cài từ trước (bị hãng chặn) thì gỡ, để không còn hai Home sạch.
        if (pkgHome != PKG_HOME && sh("pm path $PKG_HOME").isNotBlank()) {
            bao(t("Gỡ Home sạch bản cũ ($PKG_HOME)…", "Removing the old clean Home ($PKG_HOME)…"))
            sh("pm uninstall $PKG_HOME")
        }
        bao(t("Ghi cấu hình dock…", "Writing the dock settings…"))
        dayCauHinh(cauHinh, anhNen, boAnh)
        if (cvte && (nutInput || nutHome)) {
            bao(t("Tivi bo mạch CVTE: chưa bật bắt phím remote lượt này. Tắt mở tivi một lần, thấy Home sạch lên bình thường rồi vào Đổi giao diện bật lại.",
                "CVTE board: remote-key helpers stay off this pass. Restart the TV once and, once the clean Home comes up, turn them on under Change look."))
        } else {
            if (nutInput) batDichVu("InputKeyService")
            if (nutHome) batDichVu("HomeKeyService")
        }
        return null
    }

    /**
     * Cài Home sạch; tivi đã có Home sạch ký khoá khác (dọn bằng CleanYourTV gốc / Claude, mỗi máy dựng một khoá) thì
     * Android chối cài đè → gỡ bản cũ rồi cài lại, trả Home + dịch vụ trợ năng như trước. Cấu hình nằm trong thư mục
     * của app nên mất theo, người gọi tự đẩy lại (21/10/2026, Formovie L206 nhà anh: app báo xong mà Home vẫn bản 2.2).
     */
    /**
     * Chạy việc cài với giới hạn thời gian (23/10/2026, hộp RockChip Android 14 chạy CleanTV trên chính nó xoay mãi ở
     * bước cài: màn hỏi "Xác minh ứng dụng qua USB" bị che). Quá hạn thì nối lại ADB và trả "Failure TIMEOUT".
     */
    private fun voiHan(giay: Long, viec: () -> String): String {
        val ex = java.util.concurrent.Executors.newSingleThreadExecutor()
        val f = ex.submit<String> { runCatching(viec).getOrElse { "Failure ${it.message}" } }
        return try { f.get(giay, java.util.concurrent.TimeUnit.SECONDS) }
        catch (e: java.util.concurrent.TimeoutException) { f.cancel(true); runCatching { adb.ketNoiLai() }; "Failure TIMEOUT" }
        finally { ex.shutdownNow() }
    }

    private fun caiApkHome(apk: ByteArray, bao: (String) -> Unit = {}): String {
        val out = voiHan(120) { adb.caiApk(apk) }
        if (out.contains("TIMEOUT")) return out
        if (!out.contains("UPDATE_INCOMPATIBLE")) return out
        bao(t("Home sạch cũ ký khoá khác, gỡ rồi cài lại…", "The old clean Home has another signature, reinstalling…"))
        val troNang = sh("settings get secure enabled_accessibility_services").trim()
        val laHome = homeHienTai().startsWith("$pkgHome/")
        // Gỡ là mất config.json: giữ lại để dayCauHinh lấy các khoá app không tự biết (chuyển đè Home cũ, nút gán riêng,
        // tên ô Kênh nhanh). 26/10/2026: Home sạch đổi sang khoá TiviKit, mọi tivi đã cài đi qua đây một lần.
        cauHinhTruocGo = docCauHinhTv()
        sh("pm uninstall $pkgHome")
        val lai = runCatching { adb.caiApk(apk) }.getOrElse { "Failure ${it.message}" }
        if (lai.contains("Success")) {
            if (laHome) sh("cmd package set-home-activity --user 0 $cmpHome")
            if (troNang.contains(pkgHome)) sh("settings put secure enabled_accessibility_services '$troNang'; settings put secure accessibility_enabled 1")
        }
        return lai
    }

    /** Home sạch trên tivi đúng là bản [apk] đi kèm app (so SHA-256 của file APK đã cài). */
    fun homeLaBanDiKem(apk: ByteArray): Boolean {
        val bam = java.security.MessageDigest.getInstance("SHA-256").digest(apk).joinToString("") { "%02x".format(it) }
        val coSan = runCatching { sh("sha256sum \$(pm path $pkgHome | head -n 1 | sed 's/^package://') 2>/dev/null").trim().substringBefore(' ') }.getOrDefault("")
        return coSan == bam
    }

    /** Cài đè Home sạch bản đi kèm app; bật lại dịch vụ trợ năng mà Android tắt khi cài lại. */
    fun capNhatLauncher(apk: ByteArray): Boolean {
        // Trên tivi đã đúng bản đi kèm thì thôi (24/10/2026): cài lại Home đang chạy làm Home bật lên trước, đẩy CleanTV
        // xuống nền — chạy CleanTV ngay trên TCL 65Q646 kẹt ở "Đang cập nhật Home sạch…" mỗi lần Áp dụng.
        if (homeLaBanDiKem(apk)) return true
        val truoc = sh("settings get secure enabled_accessibility_services").trim()
        val out = caiApkHome(apk)
        if (truoc.contains(pkgHome)) {
            sh("settings put secure enabled_accessibility_services '$truoc'; settings put secure accessibility_enabled 1")
        }
        return out.contains("Success")
    }

    /**
     * Màn hình chờ cho nút trên Home sạch: bật lại Backdrop (Android TV) / Ambient (Google TV) nếu chính app đã tắt,
     * rồi đặt nó làm màn chờ và bật màn chờ. Gói vốn tắt từ trước khi dọn thì để nguyên.
     */
    fun chuanBiManCho(sl: BanSaoLuu?) {
        val ung = listOf("com.google.android.apps.tv.dreamx", "com.google.android.backdrop")
        // Bật lại cả khi gói bị tắt từ lần dọn bằng công cụ khác (Formovie nhà anh: dreamx tắt sẵn → nút màn chờ im).
        val daTat = sh("pm list packages -d").lines().map { it.substringAfter("package:").trim() }
        for (p in ung) if (p in daTat) {
            sh("pm enable --user 0 $p")
            if (sl != null && p in sl.daTatBoiApp) { sl.daTatBoiApp -= p; luuSaoLuu(sl) }
        }
        val dv = sh("cmd package query-services --brief -a android.service.dreams.DreamService").lines().map { it.trim() }
        val chon = ung.firstNotNullOfOrNull { p -> dv.firstOrNull { it.startsWith("$p/") } }
        if (chon != null) sh("settings put secure screensaver_components $chon")
        sh("settings put secure screensaver_enabled 1")
    }

    /** Ghi config.json cho launcher rồi khởi động lại Home (theo tools/theme.sh). */
    /**
     * Ghi một file theme vào thư mục của Home sạch. ĐI QUA ThemeProvider TRƯỚC (chạy dưới quyền Home sạch nên file thuộc
     * về nó); `cp` của shell chỉ là dự phòng. 22/10/2026: `cp` tạo file mới mang uid shell (2000) — Android 14 cấm Home
     * sạch đọc (log "TvHome.Config: ignoring … EACCES"), theme/dock người dùng chọn không được áp sau lần cài đầu hoặc
     * sau khi gỡ-cài lại Home. File cũ của shell phải xoá trước, không thì provider cũng không ghi đè được.
     */
    private fun ghiFileTheme(ten: String, du: ByteArray) {
        val dir = "/sdcard/Android/data/$pkgHome/files"
        val tam = "/data/local/tmp/cyt-${ten.replace('/', '-')}"
        adb.ghiFile(tam, du)
        if (ten.contains('/')) sh("mkdir -p $dir/${ten.substringBeforeLast('/')} 2>/dev/null; true")
        sh("rm -f $dir/$ten 2>/dev/null; content write --uri content://$pkgHome.theme/$ten < $tam 2>/dev/null || cp $tam $dir/$ten 2>/dev/null")
        // Kiểm file thật sự tới nơi (CleanTV 2.10.8, Sony 2015 Android 8: đổi theme không ăn — `content write` có thể chưa
        // có mà vẫn trả mã 0, file cũ đã xoá). Sai cỡ thì chép thẳng và mở quyền đọc cho Home sạch.
        val co = sh("wc -c < $dir/$ten 2>/dev/null").trim().toIntOrNull()
        if (co != du.size) sh("cp $tam $dir/$ten 2>/dev/null; chmod 666 $dir/$ten 2>/dev/null; true")
        sh("rm -f $tam")
    }

    /**
     * Ảnh có sẵn trên tivi / USB cắm vào tivi (24/10/2026): tivi không có trình chọn ảnh ("không có ứng dụng phù hợp"),
     * người dùng iPhone chạy CleanTV ngay trên tivi chỉ chọn ảnh được kiểu này. Tối đa 200 file.
     */
    fun timAnhTrenTv(): List<String> = sh(
        "for d in /sdcard/Download /sdcard/Pictures /sdcard/DCIM /sdcard/Movies /storage/*-*; do [ -d \"\$d\" ] && find \"\$d\" -maxdepth 4 -type f " +
            "\\( -iname '*.jpg' -o -iname '*.jpeg' -o -iname '*.png' -o -iname '*.webp' -o -iname '*.heic' \\) 2>/dev/null; done | head -n 200"
    ).lines().map { it.trim() }.filter { it.startsWith("/") && !it.contains('\'') }

    /** Đọc một file trên tivi (qua base64); quá 25 MB hoặc lỗi thì null. */
    fun docFileTv(duongDan: String): ByteArray? = runCatching {
        if (duongDan.contains('\'')) return null
        val co = sh("wc -c < '$duongDan'").trim().toLongOrNull() ?: return null
        if (co <= 0 || co > 25_000_000) return null
        val b64 = sh("base64 '$duongDan' | tr -d '\\n'").trim()
        java.util.Base64.getDecoder().decode(b64)
    }.getOrNull()

    /** Ảnh nền đang có trên tivi (wallpaper.jpg của Home sạch), qua base64 trong shell; null nếu không có / lỗi. */
    fun docAnhNenTv(): ByteArray? = runCatching {
        val b64 = sh("content read --uri content://$pkgHome.theme/wallpaper.jpg 2>/dev/null | base64 | tr -d '\\n'").trim()
        if (b64.length < 100) null else java.util.Base64.getDecoder().decode(b64)
    }.getOrNull()

    /** config.json đọc ngay trước khi gỡ Home sạch khác khoá (caiApkHome); dayCauHinh dùng thay khi tivi chưa có cấu hình. */
    private var cauHinhTruocGo: org.json.JSONObject? = null

    /** Ảnh ô kênh (kenh-<slug>.jpg) cho link_tiles của bản vá launcher; PhienLamViec đặt trước khi đẩy cấu hình. */
    var anhKenh: List<Pair<String, ByteArray>> = emptyList()

    fun dayCauHinh(json: String, anhNen: ByteArray? = null, boAnh: List<Pair<String, ByteArray>>? = null) {
        giaHanGac()
        val dir = "/sdcard/Android/data/$pkgHome/files"
        val uri = "content://$pkgHome.theme"
        sh("mkdir -p $dir 2>/dev/null; true")
        // restore_key_services (Nâng cao) chỉ chạy khi Home sạch có WRITE_SECURE_SETTINGS (upstream build-and-install.sh
        // RESTORE_KEY_SERVICES=1). Tắt thì thu lại quyền cho gọn.
        val moi = runCatching { org.json.JSONObject(json) }.getOrNull()
        val khoiPhucPhim = moi?.optBoolean("restore_key_services") ?: false
        sh("pm ${if (khoiPhucPhim) "grant" else "revoke"} $pkgHome android.permission.WRITE_SECURE_SETTINGS 2>/dev/null; true")
        // Khoá chuyển đè Home cũ do damBaoPhimHome gắn (tivi không cho tắt Home gốc), nút remote gán riêng (màn Nút
        // remote, vgs_phim_rieng) và tên ô Kênh nhanh (app_labels) — app chưa đọc cấu hình tivi thì không biết: đổi giao
        // diện không được làm mất.
        val ghi = moi?.let { m ->
            val cu = docCauHinhTv() ?: cauHinhTruocGo ?: return@let null
            if (!m.has("stock_home_redirect")) cu.optString("stock_home_redirect").takeIf { it.isNotBlank() }?.let { m.put("stock_home_redirect", it) }
            // Launcher chọn ở Quản lý launcher mà Home sạch mở thay chính nó (27/10/2026).
            if (!m.has("home_target")) cu.optString("home_target").takeIf { it.isNotBlank() }?.let { m.put("home_target", it) }
            if (!m.has(PhimRieng.KHOA)) PhimRieng.docTu(cu).takeIf { it.isNotEmpty() }?.let { PhimRieng.apVao(m, it) }
            // Ô kênh nào cấu hình mới chưa có tên / logo thì lấy cái đang có trên tivi.
            val coTen = KenhNhanh.tenTrong(m)
            KenhNhanh.tenTrong(cu).filterKeys { it !in coTen }.forEach { (goi, ten) -> KenhNhanh.datTen(m, goi, ten) }
            val coAnh = KenhNhanh.anhTrong(m)
            KenhNhanh.anhTrong(cu).filterKeys { it !in coAnh }.forEach { (goi, anh) -> KenhNhanh.datAnh(m, goi, anh) }
            m.toString()
        } ?: json
        cauHinhTruocGo = null
        ghiFileTheme("config.json", ghi.toByteArray())
        if (anhNen != null) ghiFileTheme("wallpaper.jpg", anhNen)
        else sh("rm -f $dir/wallpaper.jpg 2>/dev/null; content delete --uri $uri/wallpaper.jpg 2>/dev/null; true")
        // Bộ ảnh xoay vòng (wallpapers/): xoá bộ cũ trước, theme không có bộ ảnh thì chỉ xoá.
        sh("rm -rf $dir/wallpapers 2>/dev/null; content delete --uri $uri/wallpapers 2>/dev/null; true")
        boAnh?.forEach { (ten, du) -> ghiFileTheme("wallpapers/$ten", du) }
        anhKenh.forEach { (ten, du) -> ghiFileTheme("kenh-$ten", du) }
        ghiFileTheme("theme_revision", java.util.UUID.randomUUID().toString().replace("-", "").toByteArray())
        sh(KHOI_DONG_LAI_HOME)
    }

    /**
     * Home lạ mà app vẫn dám nhận là Home gốc: app HỆ THỐNG, có màn HOME thật, không phải trình cài đặt / Cài đặt /
     * hộp chọn. App bên thứ ba (launcher người dùng tự cài) thì để nguyên.
     */
    /** Kết quả thử nút Home thật: [gianh] = các gói đã tắt vì giành nút Home, [conLai] = gói vẫn giành (không dám tắt). */
    data class KetQuaPhimHome(val ok: Boolean, val gianh: List<String>, val conLai: String, val lyDo: String = "", val daBatBatPhim: Boolean = false)

    /** config.json đang có trên tivi (để "Đổi giao diện" mở ra đúng lựa chọn cũ); null nếu chưa có / đọc không được. */
    fun docCauHinhTv(): org.json.JSONObject? {
        val s = sh("content read --uri content://$pkgHome.theme/config.json 2>/dev/null || cat /sdcard/Android/data/$pkgHome/files/config.json 2>/dev/null").trim()
        return runCatching { org.json.JSONObject(s) }.getOrNull()
    }

    /**
     * Lệnh mở launcher mà Home sạch mở đè Home gốc (home_target, không có thì chính Home sạch), y như Home sạch làm 250 ms
     * sau phím Home thật; "" nếu tivi không có Home gốc phải mở đè (stock_home_redirect). Phím Home bơm từ remote trên
     * điện thoại (`input` / scrcpy) không qua bộ lọc phím của Home sạch, chỉ còn mở đè theo cửa sổ: Google TV mở lại
     * launcherx ngay sau đó thì kẹt ở Home gốc (28/10/2026, Skyworth).
     */
    fun lenhSauHome(): String {
        val j = docCauHinhTv() ?: return ""
        val goc = j.optString("stock_home_redirect").trim().takeIf { TEN_GOI.matches(it) } ?: return ""
        val dich = j.optString("home_target").trim().takeIf { Regex("[A-Za-z0-9_.]+/[A-Za-z0-9_.]+").matches(it) } ?: cmpHome
        val mo = "am start -n $dich -c android.intent.category.HOME -f 0x10200000 >/dev/null 2>&1"
        // launcherx có lúc tự mở lại ngay sau lần mở đè: 1 giây sau còn thấy Home gốc thì mở thêm lần nữa.
        return "$mo; sleep 1; dumpsys window | grep -m1 mCurrentFocus | grep -q '$goc/' && $mo; true"
    }

    /** Gói của màn đang hiện trên tivi (đọc cửa sổ đang giữ tiêu điểm — chạy được từ Android 8 tới 14). */
    fun goiDangHien(): String {
        val ten = Regex("""([A-Za-z0-9_.]+)/[A-Za-z0-9_.$]+""")
        repeat(5) {
            val w = sh("dumpsys window | grep -E 'mCurrentFocus|mFocusedApp' | head -2")
            ten.find(w)?.groupValues?.get(1)?.let { return it }
            // Sony BRAVIA BF1 (Google TV 12, 22/10/2026): Home Google mở chậm, cửa sổ chưa nhận tiêu điểm — đọc activity
            // đang chạy trên cùng thay vào.
            val a = sh("dumpsys activity activities | grep -E 'topResumedActivity|mResumedActivity' | head -2")
            ten.find(a)?.groupValues?.get(1)?.let { return it }
            Thread.sleep(1000) // đang chuyển màn, tiêu điểm tạm trống
        }
        return ""
    }

    /** Gói có màn HOME đang bật. */
    private fun coManHome(pkg: String): Boolean =
        sh("cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME").lines().any { it.trim().startsWith("$pkg/") }

    private fun dangBat(pkg: String) = sh("pm list packages -e $pkg").lines().any { it.trim() == "package:$pkg" }

    /**
     * Bấm Home THẬT trên tivi rồi xem màn nào hiện ra (21/10/2026: nhiều người báo cài xong bấm Home lại về màn cũ).
     * Gói hệ thống nào giành nút Home (có màn Home / tên kiểu launcher, không phải Cài đặt/HDMI/setupwraith) thì tắt
     * (ghi vào sao lưu), lặp tối đa 3 lần.
     *
     * 22/10/2026 (bản 1.1 làm tivi TCL Google TV đen màn): KHÔNG THÀNH CÔNG THÌ BẬT LẠI MỌI GÓI VỪA TẮT — tivi phải về
     * đúng như trước khi thử, không bao giờ bị bỏ lại không có Home. Và Home gốc của Google (launcherx/tvlauncher)
     * chỉ được tắt khi setupwraith đã tắt: còn bật thì nó nhận Home, tivi kẹt ở màn trình cài đặt.
     */
    fun damBaoPhimHome(sl: BanSaoLuu?, bao: (String) -> Unit = {}): KetQuaPhimHome {
        val gianh = mutableListOf<String>()
        var daBatBatPhim = false
        var lyDo = ""
        fun tra(pkg: String): KetQuaPhimHome {
            if (gianh.isNotEmpty()) {
                bao(t("Không chuyển được nút Home, bật lại ${gianh.joinToString()}…", "Couldn't take over the Home button, re-enabling ${gianh.joinToString()}…"))
                gianh.forEach { p -> sh("pm enable --user 0 $p"); sl?.daTatBoiApp?.remove(p); if (sl?.homeGocDaTat == p) sl.homeGocDaTat = "" }
                sl?.let { luuSaoLuu(it) }
            }
            // Home gốc đã tắt từ bước cài (caiLauncher) mà nút Home vẫn không ra Home sạch: bật lại + đặt làm mặc định.
            // 22/10/2026, Xiaomi MiTV-ASTP0 bản Trung Quốc: hãng chỉ nhận Home theo danh sách tên gói, nút Home ra
            // FallbackHome (com.android.tv.settings) = màn đen, mà app báo "để tivi như cũ" khi Home Xiaomi vẫn tắt.
            if (sl != null && sl.homeGocDaTat.isNotBlank()) {
                bao(t("Bật lại Home gốc (${sl.homeGocDaTat})…", "Re-enabling the stock Home (${sl.homeGocDaTat})…"))
                traHomeGoc(sl)
            }
            if (gianh.isNotEmpty() || sl != null) sh("input keyevent KEYCODE_HOME")
            return KetQuaPhimHome(false, gianh.toList(), pkg, lyDo)
        }
        /**
         * Home gốc không tắt được hoặc hãng tự bật lại (SCBC R3 Android 14, 23/10/2026: tắt tvlauncher 3 lần, lần nào bấm
         * Home cũng về nó): tắt tiếp là vô ích. Bật lại mọi gói vừa tắt, để Home sạch tự mở đè mỗi khi màn Home cũ hiện ra
         * (stock_home_redirect của HomeKeyService, upstream #479/#1116). Thử trên máy ảo Android TV 14: tvlauncher vẫn bật
         * và vẫn là Home mặc định, bấm Home từ Cài đặt 3/3 lần ra Home sạch. Không được thì gỡ khoá ra, trả null.
         */
        fun chuyenDe(pkg: String): KetQuaPhimHome? {
            if (laBoMachCvte()) return null
            bao(t("Không tắt '$pkg' được: cho Home sạch tự mở đè lên Home cũ…", "'$pkg' can't be disabled: letting the clean Home open over the old one…"))
            if (gianh.isNotEmpty()) {
                gianh.forEach { p -> sh("pm enable --user 0 $p"); sl?.daTatBoiApp?.remove(p); if (sl?.homeGocDaTat == p) sl.homeGocDaTat = "" }
                sl?.let { luuSaoLuu(it) }
                gianh.clear()
            }
            if (!ganChuyenHomeCu(pkg)) return null
            Thread.sleep(1500)
            sh("cmd package set-home-activity --user 0 $cmpHome")
            // Home cũ đang nằm sẵn trên màn (nhánh setupwraith không tắt gì) thì bấm Home không đổi cửa sổ, HomeKeyService
            // không có sự kiện để bắt (2.6.2–2.6.3 vì thế báo hỏng trên mọi Google TV; chẩn đoán 23/10/2026 cho thấy
            // focus đúng là HomeActivity của launcherx). Mở Cài đặt trước rồi mới bấm Home. Hai lần: dịch vụ vừa bật có thể
            // chưa kịp bind.
            repeat(2) { lan ->
                sh("am start -a android.settings.SETTINGS >/dev/null 2>&1")
                Thread.sleep(1500)
                sh("input keyevent KEYCODE_HOME")
                Thread.sleep(3000)
                if (goiDangHien() == pkgHome) return KetQuaPhimHome(true, emptyList(), "", "chuyen", true)
                if (lan == 0) { bao(t("Chờ Home sạch nhận lệnh, thử lại…", "Waiting for the clean Home to pick it up, retrying…")); Thread.sleep(3000) }
            }
            boChuyenHomeCu()
            return null
        }
        repeat(4) { lan ->
            giaHanGac()
            sh("cmd package set-home-activity --user 0 $cmpHome")
            sh("input keyevent KEYCODE_HOME")
            Thread.sleep(2500)
            val pkg = goiDangHien()
            if (pkg == pkgHome) return KetQuaPhimHome(true, gianh, "", "", daBatBatPhim)
            // Đã tắt mà vẫn hiện: hãng bật lại sau lưng.
            if (pkg in gianh) {
                chuyenDe(pkg)?.let { return it }
                lyDo = "khong-tat"
                return tra(pkg)
            }
            // Không đọc được màn nào đang hiện mà Google TV còn bật setupwraith: gần như chắc là ca setupwraith giữ Home,
            // báo đúng cách sửa (quên Wi-Fi…) thay vì "không rõ" (Sony 65S30, 22/10/2026).
            if (pkg.isBlank() && dangBat(SETUP)) {
                lyDo = "setupwraith"
                val goc = HOME_GOC_BIET.firstOrNull { dangBat(it) } ?: ""
                if (goc.isNotBlank()) chuyenDe(goc)?.let { return it }
                return tra(goc)
            }
            if (KeHoach.laHomeCvte(pkg)) {
                lyDo = "cvte"
                return tra(pkg)
            }
            // Nút Home mở một app KHÔNG có màn Home (hộp nhà mạng NES: com.nes.recents — 23/10/2026, SEI RT-G2): phần mềm
            // hãng tự đổi nút Home. Bật dịch vụ bắt phím Home của Home sạch rồi bấm thử lại, không tắt gì. Bo CVTE: không
            // bật dịch vụ bắt phím cùng lượt (upstream #741).
            if (!daBatBatPhim && pkg.isNotBlank() && pkg != SETUP && pkg !in KHONG_PHAI_HOME && !coManHome(pkg) && !laBoMachCvte()) {
                // Tivi chậm có thể còn hiện app cũ ngay sau khi bấm Home: chờ thêm rồi đọc lại, chỉ bật khi vẫn đúng app đó.
                Thread.sleep(2000)
                val lai = goiDangHien()
                if (lai == pkgHome) return KetQuaPhimHome(true, gianh, "", "")
                if (lai != pkg) return@repeat
                bao(t("Nút Home đang mở '$pkg' (không phải màn Home): bật bắt phím Home của Home sạch rồi thử lại…", "The Home button opens '$pkg' (not a home screen): turning on the clean Home's Home-key helper and trying again…"))
                batDichVu("HomeKeyService")
                daBatBatPhim = true
                Thread.sleep(1500)
                return@repeat
            }
            // Google TV còn setupwraith: không tắt Home Google (sự cố 1.1, TCL BeyondTV4 đen màn), chỉ cho Home sạch mở đè
            // (2.6.2, ~10 TCL Google TV G07–G15 tối 23/10/2026 báo loi:launcherx). Không được thì báo cách quên Wi-Fi như cũ.
            if (pkg in HOME_GOC_BIET && dangBat(SETUP)) {
                lyDo = "setupwraith"
                chuyenDe(pkg)?.let { return it }
                return tra(pkg)
            }
            // Hộp "chọn Home" của hệ thống (gói android, ResolverActivity): tivi có nhiều launcher (Launchpad TV, Home TV…)
            // mà chưa có Home mặc định. Đóng hộp rồi lượt sau đặt lại Home sạch và bấm Home (CleanTV 2.10.5, Xiaomi ASTR0
            // nội địa: mỗi lần đổi giao diện app lại trả Home gốc vì coi hộp này là hỏng).
            if (pkg == "android" && lan < 3) {
                bao(t("Tivi đang hỏi chọn Home: đóng hộp, đặt Home sạch làm mặc định rồi thử lại…", "The TV is asking which Home to use: closing it, setting the clean Home as default and retrying…"))
                sh("input keyevent KEYCODE_BACK")
                Thread.sleep(1000)
                return@repeat
            }
            // Trên màn vẫn là một app chứ chưa phải Home nào (Cài đặt app vừa mở để thử, hộp cài đặt, app người dùng đang
            // xem): phím Home chưa ăn, chưa phải Home cũ giành nút. Chờ rồi bấm lại thay vì báo hỏng ngay lượt đầu (CleanTV
            // 2.10.3: Sony VH21 / BF1, Xiaomi MOOR2, onn 4K… báo "Nút Home vẫn mở com.android.tv.settings").
            if (lan < 3 && pkg.isNotBlank() && pkg != SETUP && (pkg in KHONG_PHAI_HOME || pkg.contains("packageinstaller") || !coManHome(pkg))) {
                Thread.sleep(2000)
                return@repeat
            }
            if (lan == 3 || sl == null || pkg == SETUP || !nenTatVuiGianhHome(pkg)) return tra(pkg)
            bao(t("Nút Home vẫn mở '$pkg', tắt nó…", "The Home button still opens '$pkg', disabling it…"))
            gacThem(pkg)
            sh("pm disable-user --user 0 $pkg")
            if (pkg !in sl.daTatTruoc && pkg !in sl.daTatBoiApp) sl.daTatBoiApp += pkg
            // Nhớ là Home gốc: lần "Đổi giao diện" sau ghi đè config.json vẫn gắn lại stock_home_redirect cho nó.
            if (sl.homeGocDaTat.isBlank() && pkg in HOME_GOC_BIET + listOf(sl.homeTruoc.substringBefore('/'))) sl.homeGocDaTat = pkg
            luuSaoLuu(sl)
            gianh += pkg
            // Lệnh tắt bị từ chối (gói được hãng bảo vệ) hoặc vừa tắt đã bật lại.
            Thread.sleep(800)
            if (dangBat(pkg)) {
                chuyenDe(pkg)?.let { return it }
                lyDo = "khong-tat"
                return tra(pkg)
            }
        }
        return tra(goiDangHien())
    }

    private fun nenTatVuiGianhHome(pkg: String): Boolean {
        if (!TEN_GOI.matches(pkg) || pkg in HOME_SACH || KeHoach.laHomeCvte(pkg) || pkg in KHONG_PHAI_HOME || pkg == "android" || APP_XUAT_XUONG.containsMatchIn(pkg) ||
            pkg.startsWith("com.android.systemui") || pkg.contains("packageinstaller") || pkg.contains("permissioncontroller")) return false
        if (!sh("pm list packages -s $pkg").lines().any { it.trim() == "package:$pkg" }) return laLauncherCaiThem(pkg)
        if (sh("dumpsys package $pkg | grep -c TV_INPUT_HARDWARE").trim().toIntOrNull()?.let { it > 0 } == true) return false
        val coHome = sh("cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME")
            .lines().any { it.trim().startsWith("$pkg/") }
        return coHome || Regex("launcher|tvhome|home", RegexOption.IGNORE_CASE).containsMatchIn(pkg.substringAfterLast('.').ifBlank { pkg }) ||
            Regex("launcher", RegexOption.IGNORE_CASE).containsMatchIn(pkg)
    }

    private fun laHomeHangLa(pkg: String): Boolean {
        if (!TEN_GOI.matches(pkg) || pkg in KHONG_PHAI_HOME || pkg == "android" || APP_XUAT_XUONG.containsMatchIn(pkg)) return false
        val heThong = sh("pm list packages -s $pkg").lines().any { it.trim() == "package:$pkg" }
        val coHome = sh("cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME")
            .lines().any { it.trim().startsWith("$pkg/") && !it.trim().endsWith("FallbackHome") }
        return heThong && coHome
    }

    /**
     * Bật dịch vụ trợ năng của Home sạch. TCL (TclAppBoot) không cho bind dịch vụ của app chưa chạy nếu thiếu app-op
     * AUTO_START (upstream #526): cấp trước; dịch vụ đã có trong danh sách thì xoá đi ghi lại để Android bind lại.
     */
    /** App NGƯỜI DÙNG (không phải hệ thống) có màn HOME thật: launcher cài thêm như Dangbei. */
    private fun laLauncherCaiThem(pkg: String): Boolean {
        if (!TEN_GOI.matches(pkg) || pkg in HOME_SACH || pkg in KHONG_PHAI_HOME || APP_XUAT_XUONG.containsMatchIn(pkg)) return false
        if (sh("pm list packages -s $pkg").lines().any { it.trim() == "package:$pkg" }) return false
        return sh("cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME")
            .lines().any { it.trim().startsWith("$pkg/") }
    }

    /** Tivi dùng bo mạch CVTE (có gói com.cvte.* / cn.cvte.*). Nhớ theo kết nối. */
    private var cvteDaDo: Boolean? = null
    fun laBoMachCvte(): Boolean = cvteDaDo ?: sh("pm list packages | grep -c -E 'package:(com|cn)\\.cvte\\.'").trim()
        .toIntOrNull().let { (it ?: 0) > 0 }.also { cvteDaDo = it }

    private fun batDichVu(ten: String) {
        val svc = "$pkgHome/$NS_HOME.$ten"
        sh("cmd appops set $pkgHome AUTO_START allow 2>/dev/null; s=\$(settings get secure enabled_accessibility_services); case \"\$s\" in *$ten*) settings put secure enabled_accessibility_services ''; settings put secure accessibility_enabled 0; sleep 1; settings put secure enabled_accessibility_services \"\$s\" ;; null|'') settings put secure enabled_accessibility_services $svc ;; *) settings put secure enabled_accessibility_services \"\$s:$svc\" ;; esac; settings put secure accessibility_enabled 1")
    }

    /**
     * stock_home_redirect (upstream #479): hãng tự bật lại Home cũ (XGIMI bật lại launcherx mỗi lần bấm nút app trên
     * remote) thì HomeKeyService thấy màn Home cũ hiện lên là mở lại Home sạch. Ghi khoá vào config.json đang có trên
     * tivi + bật HomeKeyService.
     */
    /**
     * Nút "Đang phát" của Home sạch (24/10/2026, góp ý nhiều app phát chồng tiếng): cho phép NowPlayingListener của
     * launcher làm trình nghe thông báo, nhờ đó launcher đọc được các phiên phát nhạc / video. Lỗi thì nút không hiện.
     */
    fun choPhepDangPhat() { runCatching { sh("cmd notification allow_listener $pkgHome/$NS_HOME.NowPlayingListener 2>/dev/null; true") } }

    /** Mở màn Cài đặt launcher của Home sạch trên tivi (chỗ chọn "Mở khi khởi động"; CleanTV 2.10.4). */
    fun moCaiDatLauncher() { sh("am start -a com.gathin.tvhome.action.LAUNCHER_SETTINGS -n $cmpHome >/dev/null 2>&1; true") }

    /** Bật HomeKeyService cho phím remote của mẫu (nút app, Cài đặt). Bo CVTE: không (upstream #741). */
    fun batBatPhimHome() { if (!laBoMachCvte()) batDichVu("HomeKeyService") }

    // ---------- Nút remote gán riêng (25/10/2026) ----------

    /**
     * Chờ người dùng bấm một nút trên remote thật, đọc mã bằng getevent (shell đọc được /dev/input/event*): nút nhấn
     * xuống đầu tiên, kèm tên thiết bị và nhãn phím trong keylayout tivi dùng cho thiết bị đó. null = [giay] giây không
     * có nút nào. getevent chỉ nghe, không chặn: nút vẫn làm việc cũ của nó trên tivi.
     */
    fun doNutRemote(giay: Int = 20): PhimRieng.Nut? {
        val chu = ngheNut(giay, null) ?: return null
        val n = PhimRieng.docGetevent(chu) ?: error(
            if (chu.isBlank()) t("Tivi ngắt luồng đọc nút giữa chừng. Dò lại.", "The TV closed the button stream midway. Detect again.")
            else t("Tivi không chạy được getevent để đọc nút remote: ${chu.trim().take(160)}", "The TV can't run getevent to read remote buttons: ${chu.trim().take(160)}"))
        val ten = sh("cat /sys/class/input/${n.thietBi.substringAfterLast('/')}/device/name 2>/dev/null").trim().lineSequence().firstOrNull().orEmpty()
        val kl = PhimRieng.fileKeylayout(sh("dumpsys input 2>/dev/null | grep -E 'Path:|KeyLayoutFile:'"), n.thietBi)
        val nhan = kl?.takeIf { f -> f.none { it in "'\"\$`\\ " } }?.let { PhimRieng.tenTrongKl(sh("cat '$it' 2>/dev/null"), n.maQuet, n.usage) }
        return n.copy(tenThietBi = ten, tenAndroid = nhan)
    }

    /** Chạy getevent tới khi có nút nhấn xuống (đúng [theo] nếu có) hoặc hết [giay] giây. */
    private fun ngheNut(giay: Int, theo: PhimRieng.Nut?): String? {
        // getevent lần trước chỉ biết luồng đã đóng khi in thêm dòng (lúc có phím kế tiếp): dừng trước và sau.
        dungGetevent()
        return try { adb.nghe("getevent -q", giay) { PhimRieng.docGetevent(it, theo) != null } } finally { dungGetevent() }
    }

    private fun dungGetevent() { runCatching { sh("pkill -x getevent 2>/dev/null; true") } }

    /**
     * Ghi danh sách gán riêng vào config.json ĐANG CÓ trên tivi — chỉ sửa app_keys / vgs_phim_rieng, giữ nguyên giao
     * diện, dock, ảnh nền (không qua dayCauHinh: hàm đó xoá ảnh nền và ghi theme_revision mới) — rồi bật / khởi động lại
     * dịch vụ phím của Home sạch để nó đọc lại. Bo CVTE: người gọi hỏi trước (xem batBatPhimHome, upstream #741).
     */
    fun ghiPhimRieng(ds: List<String>) {
        ghiFileTheme("config.json", PhimRieng.apVao(cauHinhDeSua(), ds).toString().toByteArray())
        // Dịch vụ phím chỉ đọc app_keys lúc khởi động. Đã có HomeKeyService (hay hết phím riêng): tắt bật lại cả danh
        // sách; chưa có: thêm vào, và khởi động lại InputKeyService đang chạy để nó cũng đọc bảng mới.
        val truoc = sh("settings get secure enabled_accessibility_services").trim()
        if (ds.isEmpty() || truoc.contains("HomeKeyService")) khoiDongLaiDichVuPhim()
        else {
            batDichVu("HomeKeyService")
            if (truoc.contains("$pkgHome/")) khoiDongLaiDichVuPhim()
        }
    }

    /**
     * config.json đang có trên tivi, để sửa vài khoá rồi ghi lại (nút gán riêng, ô Kênh nhanh). Có file mà không đọc được
     * thì dừng: ghi đè bằng một file gần như trống là mất cả giao diện.
     */
    private fun cauHinhDeSua(): org.json.JSONObject = docCauHinhTv() ?: run {
        if (sh("ls /sdcard/Android/data/$pkgHome/files/config.json 2>/dev/null").isNotBlank())
            error(t("Không đọc được cấu hình Home sạch trên tivi nên chưa ghi gì. Thử lại.", "Couldn't read the clean Home's settings on the TV, so nothing was written. Try again."))
        org.json.JSONObject()
    }

    /** Tắt bật lại danh sách trợ năng nếu có dịch vụ của Home sạch (để dịch vụ phím đọc lại config.json). */
    private fun khoiDongLaiDichVuPhim() {
        sh("s=\$(settings get secure enabled_accessibility_services); case \"\$s\" in *$pkgHome/*) settings put secure enabled_accessibility_services ''; " +
            "settings put secure accessibility_enabled 0; sleep 1; settings put secure enabled_accessibility_services \"\$s\"; settings put secure accessibility_enabled 1 ;; esac")
    }

    /** Dịch vụ phím của Home sạch (HomeKeyService / InputKeyService) đang được bật. */
    fun dichVuPhimDangBat(): Boolean {
        val d = sh("settings get secure enabled_accessibility_services; settings get secure accessibility_enabled").lines().map { it.trim() }
        val ds = d.getOrNull(0).orEmpty()
        return d.getOrNull(1) == "1" && ds.contains("$pkgHome/") && (ds.contains("HomeKeyService") || ds.contains("InputKeyService"))
    }

    /** Bấm thử nút vừa gán: [daBam] = thấy người dùng nhấn đúng nút đó, [goi] = app trước mặt mấy giây sau. */
    data class KetQuaThuNut(val daBam: Boolean, val goi: String)

    /**
     * Bấm thử (25/10/2026): đưa Home sạch lên ([veHome]; app đích đang mở sẵn thì không biết nút có tác dụng không), chờ
     * người dùng bấm đúng [nut] trên remote ([giay] giây), rồi xem app nào lên trước mặt (dịch vụ phím mở app lúc thả
     * nút; app nặng mở mất vài giây).
     */
    fun thuNut(nut: PhimRieng.Nut, goi: String, giay: Int = 20, veHome: Boolean = true): KetQuaThuNut {
        if (veHome) sh("am start -n $cmpHome >/dev/null 2>&1; true")
        val chu = ngheNut(giay, nut)
        if (chu == null || PhimRieng.docGetevent(chu, nut) == null) return KetQuaThuNut(false, "")
        var g = ""
        repeat(8) {
            Thread.sleep(800)
            g = goiDangHien()
            if (g == goi) return KetQuaThuNut(true, g)
        }
        return KetQuaThuNut(true, g)
    }

    fun ganChuyenHomeCu(pkg: String): Boolean {
        if (!TEN_GOI.matches(pkg)) return false
        val j = docCauHinhTv() ?: return false
        if (j.optString("stock_home_redirect") != pkg) {
            j.put("stock_home_redirect", pkg)
            ghiFileTheme("config.json", j.toString().toByteArray())
            ghiFileTheme("theme_revision", java.util.UUID.randomUUID().toString().replace("-", "").toByteArray())
        }
        batDichVu("HomeKeyService")
        return true
    }

    private fun boChuyenHomeCu() {
        val j = docCauHinhTv() ?: return
        if (j.remove("stock_home_redirect") == null) return
        ghiFileTheme("config.json", j.toString().toByteArray())
        batDichVu("HomeKeyService")
    }

    private fun traHomeGoc(sl: BanSaoLuu) {
        val goc = sl.homeGocDaTat.ifBlank { sl.homeTruoc.substringBefore('/') }
        if (goc.isBlank()) return
        sh("pm enable --user 0 $goc")
        sh("cmd package set-home-activity --user 0 \$(cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME $goc | tail -n 1)")
        sl.daTatBoiApp -= goc; sl.homeGocDaTat = ""; luuSaoLuu(sl)
    }

    // ---------- Quản lý launcher (27/10/2026) ----------

    /** Các launcher trên tivi, Home mặc định, nút Home đang mở gì (dulieu/Launcher). */
    fun docLauncher(): Launcher.TrangThai {
        val chu = adb.shell(Launcher.lenhDoc(pkgHome), 60).replace("\r", "")
        // Android 9 trở xuống không liệt kê được màn HOME đang tắt: dò từng gói đang tắt (TX3 Mini: 22 gói, ~2,5 giây).
        val tat = if (Launcher.thieuManTat(chu)) runCatching { adb.shell(Launcher.LENH_TAT_CO_HOME, 120).replace("\r", "") }.getOrNull() else null
        return Launcher.tuChu(chu, pkgHome, HOME_SACH, tat)
    }

    /** versionName của các gói [goi], một lệnh. */
    fun banCua(goi: Collection<String>): Map<String, String> {
        val ds = goi.filter { TEN_GOI.matches(it) }
        if (ds.isEmpty()) return emptyMap()
        return sh("for p in ${ds.joinToString(" ")}; do echo \"\$p \$(dumpsys package \$p 2>/dev/null | grep -m1 -o 'versionName=[^ ]*')\"; done").lines().mapNotNull { l ->
            val p = l.trim().split(' ')
            p.getOrNull(1)?.removePrefix("versionName=")?.takeIf { it.isNotBlank() }?.let { p[0] to it }
        }.toMap()
    }

    /**
     * Launcher tự cài người dùng đang dùng làm Home (chọn ở Quản lý launcher hay tự đặt trên tivi): là Home mặc định, hoặc
     * đang bật dịch vụ trợ năng (Projectivy "ghi đè launcher"). Đổi giao diện Home sạch thì không giành lại Home của nó:
     * damBaoPhimHome sẽ coi nó là app giành nút Home và tắt.
     */
    fun launcherKhacDangDung(): String? = runCatching {
        val tt = docLauncher()
        tt.ds.firstOrNull { it.loai == Launcher.Loai.CAI_THEM && !it.dangTat && (it.goi == tt.goiMacDinh || it.goi in tt.troNangKhac) }?.goi
    }.getOrNull()

    /**
     * Thử nút Home như người dùng bấm: mở Cài đặt, bấm Home thật rồi đọc gói hiện ra. Launcher mở đè (Home sạch mở chính
     * nó hay Projectivy lên Home gốc) đến sau Home gốc, chậm trên máy bận (TX3 Mini 27/10/2026: ~4 giây): đọc lại mỗi giây
     * tới 6 giây, thấy [cho] (gói app đoán nút Home mở) thì thôi.
     */
    fun thuNutHome(cho: String): String {
        sh("am start -a android.settings.SETTINGS >/dev/null 2>&1")
        Thread.sleep(1500)
        sh("input keyevent KEYCODE_HOME")
        var g = ""
        for (giay in 1..6) {
            Thread.sleep(1000)
            g = goiDangHien()
            if (g == cho) break
        }
        return g
    }

    /**
     * Bật lại launcher [goi]. Android thấy thêm một Home cùng mức ưu tiên là quên Home mặc định và hỏi chọn ở lần bấm Home
     * sau: đặt lại Home mặc định cũ. Launcher mức cao hơn thì thành Home luôn (màn hình đã hỏi trước), ghi vào sao lưu để
     * Khôi phục trả Home như trước.
     */
    fun batLauncher(goi: String, sl: BanSaoLuu): Boolean {
        val macDinh = homeHienTai()
        if (!batLaiGoi(goi, sl)) return false
        if (sl.homeGocDaTat == goi) sl.homeGocDaTat = ""
        if (macDinh.contains('/') && !macDinh.startsWith("android/")) sh("cmd package set-home-activity --user 0 $macDinh")
        if (homeHienTai() != macDinh) sl.homeDaDoi = true
        luuSaoLuu(sl)
        return true
    }

    /**
     * Sửa vài khoá config.json của Home sạch đang có trên tivi (giá trị null = xoá khoá) rồi cho dịch vụ phím đọc lại. Trả
     * giá trị cũ của đúng các khoá đó để trả lại; null = tivi không có cấu hình Home sạch (chưa cài / không đọc được).
     */
    private fun suaCauHinh(doi: Map<String, Any?>): Map<String, Any?>? {
        val j = docCauHinhTv() ?: return null
        val cu = doi.keys.associateWith { j.opt(it) }
        if (doi.all { (k, v) -> cu[k] == v }) return cu
        doi.forEach { (k, v) -> if (v == null) j.remove(k) else j.put(k, v) }
        ghiFileTheme("config.json", j.toString().toByteArray())
        khoiDongLaiDichVuPhim()
        return cu
    }

    /** Bỏ home_target: Home sạch lại tự mở chính nó (Khôi phục mà giữ Home sạch). */
    fun boDichHome() { runCatching { suaCauHinh(mapOf("home_target" to null)) } }

    /**
     * Kết quả đặt Home ở màn Quản lý launcher: [mo] = gói nút Home mở khi thử; [daTat] = launcher đã tắt để nhường Home;
     * [boKhoa] = khoá Home sạch đã gỡ vì Home sạch trên tivi là bản cũ chưa biết home_target (mở đè Home gốc, tự về Home
     * sạch khi bật tivi…); [quaHomeSach] = Home của hãng không nhường được nên Home sạch mở launcher đó đè lên; [lyDo] khi
     * không được.
     */
    data class KetQuaDatHome(
        val ok: Boolean, val mo: String, val daTat: List<String> = emptyList(), val boKhoa: List<String> = emptyList(),
        val quaHomeSach: Boolean = false, val lyDo: String = "",
    )

    /**
     * Đặt launcher [goi] làm Home (màn Quản lý launcher, 27/10/2026). Bật gói nếu đang tắt; tắt các launcher [nhuong]
     * người dùng đã đồng ý (mức ưu tiên cao hơn nên Android luôn chọn chúng; tivi canh gác như lúc cài Home sạch); báo
     * Home sạch mở launcher đó mỗi khi nó tự mở đè (home_target; [homeSachMoi] = Home sạch trên tivi là bản đi kèm app,
     * bản cũ thì gỡ các khoá làm nó tự mở đè); đặt Home mặc định; rồi mở Cài đặt và bấm Home thật để thử, hộp chọn Home
     * hiện ra thì đóng và đặt lại. Home của hãng vẫn thắng mà không tắt được (Google TV): Home sạch mở launcher đó (hay
     * chính nó) đè lên Home của hãng, như damBaoPhimHome. Không được thì trả mọi thứ như trước.
     */
    fun datHome(goi: String, sl: BanSaoLuu, nhuong: List<String>, homeSachMoi: Boolean, bao: (String) -> Unit): KetQuaDatHome {
        require(TEN_GOI.matches(goi)) { goi }
        val laHomeSach = goi in HOME_SACH
        val macDinhCu = homeHienTai()
        val daTat = mutableListOf<String>()
        // Giá trị cũ của các khoá config.json đã sửa (lần sửa đầu của mỗi khoá) để trả lại.
        var cauHinhCu = emptyMap<String, Any?>()
        var boKhoa = emptyList<String>()
        var quaHomeSach = false

        fun suaGiuCu(doi: Map<String, Any?>): Boolean {
            val cu = suaCauHinh(doi) ?: return false
            cauHinhCu = cu + cauHinhCu
            return true
        }

        fun traLai(lyDo: String, mo: String): KetQuaDatHome {
            bao(t("Không được, đang trả tivi như trước…", "It didn't work, putting the TV back as it was…"))
            daTat.forEach { p -> sh("pm enable --user 0 $p"); sl.daTatBoiApp.remove(p) }
            if (daTat.isNotEmpty()) luuSaoLuu(sl)
            if (macDinhCu.contains('/') && !macDinhCu.startsWith("android/")) sh("cmd package set-home-activity --user 0 $macDinhCu")
            if (cauHinhCu.isNotEmpty()) runCatching { suaCauHinh(cauHinhCu) }
            sh("input keyevent KEYCODE_HOME")
            boGac()
            return KetQuaDatHome(false, mo, lyDo = lyDo)
        }

        if (dangTat(goi)) {
            bao(t("Bật ${tenDep(goi)}…", "Enabling ${tenDep(goi)}…"))
            sh("pm enable --user 0 $goi")
            sl.daTatBoiApp.remove(goi)
            if (sl.homeGocDaTat == goi) sl.homeGocDaTat = ""
            luuSaoLuu(sl)
        }
        val cmp = sh("$HOME_QUERY $goi | tail -n 1").trim()
        if (!cmp.startsWith("$goi/")) return KetQuaDatHome(false, "", lyDo = "khong-co-man")
        for (p in nhuong) {
            // Chốt lại dù màn hình đã lọc (Launcher.tatDuocDeNhuong): không bao giờ tắt setupwraith, Cài đặt, Home Google khi
            // còn setupwraith (sự cố 1.1), Home bo CVTE.
            if (!TEN_GOI.matches(p) || p == goi || p in HOME_SACH || p in KHONG_PHAI_HOME || KeHoach.laHomeCvte(p) || (p in HOME_GOC_BIET && dangBat(SETUP))) continue
            if (!dangBat(p)) continue
            bao(t("Tắt ${tenDep(p)} để nhường Home…", "Disabling ${tenDep(p)} so it gives up Home…"))
            gacThem(p)
            sh("pm disable-user --user 0 $p")
            if (!dangBat(p)) {
                daTat += p
                if (p !in sl.daTatTruoc && p !in sl.daTatBoiApp) sl.daTatBoiApp += p
                luuSaoLuu(sl)
            }
        }
        bao(t("Đặt ${tenDep(goi)} làm Home…", "Making ${tenDep(goi)} the Home…"))
        sh("cmd package set-home-activity --user 0 $cmp")
        Thread.sleep(800)
        // Hộp chọn ("android") hay không đọc được: để lượt thử bên dưới quyết.
        val thang = homeHienTai().takeIf { it != cmp }?.substringBefore('/')?.takeIf { it.isNotBlank() && it != "android" }
        // Home của hãng mức ưu tiên cao hơn vẫn thắng (Google TV): Home sạch mở đè lên nó — chính nó, hay launcher đã chọn
        // nhờ home_target (cần Home sạch bản đi kèm app).
        if (thang != null && ((!laHomeSach && !homeSachMoi) || laBoMachCvte())) return traLai("chan:$thang", "")
        // Mọi khoá config.json đổi một lần, dịch vụ phím khởi động lại một lần: 27/10/2026 trên TX3 Mini (vừa cài lại Home
        // sạch, máy đang bận) ba lần khởi động lại liền nhau làm dịch vụ chưa kịp chạy lúc thử nút Home.
        val chuyenDang = docCauHinhTv()?.optString("stock_home_redirect").orEmpty()
        val doi = linkedMapOf<String, Any?>()
        when {
            laHomeSach -> doi["home_target"] = null
            // Đích chính là Home gốc Home sạch đang mở đè (Về Home gốc): thôi mở đè luôn, không thì Home sạch mở nó mỗi khi
            // nó hiện ra, lặp mãi (Home sạch cũng tự chặn trường hợp này).
            homeSachMoi && chuyenDang == goi -> { doi["home_target"] = null; doi["stock_home_redirect"] = null }
            // Home sạch tự mở đè (Home gốc hiện ra, Home của remote Xiaomi, lúc bật tivi…): mở launcher đã chọn thay chính nó.
            homeSachMoi -> doi["home_target"] = cmp
            else -> docCauHinhTv()?.let { j -> Launcher.KHOA_GIANH_HOME.filter { j.has(it) } }.orEmpty().forEach { doi[it] = null }
        }
        if (thang != null) {
            bao(t("${tenDep(thang)} vẫn giữ Home: cho Home sạch mở ${tenDep(goi)} đè lên nó…", "${tenDep(thang)} still holds Home: letting the clean Home open ${tenDep(goi)} over it…"))
            doi["stock_home_redirect"] = thang
        } else if (chuyenDang.isNotEmpty() && chuyenDang in daTat) {
            // Home gốc Home sạch đang mở đè vừa bị tắt để nhường Home: thôi mở đè, như lúc cài Home sạch tắt được Home gốc.
            doi["stock_home_redirect"] = null
        }
        if (doi.isNotEmpty() && !suaGiuCu(doi) && thang != null) return traLai("chan:$thang", "")
        if (!laHomeSach && !homeSachMoi) boKhoa = doi.keys.filter { it in Launcher.KHOA_GIANH_HOME && cauHinhCu[it] != null }
        if (thang != null) {
            // Dịch vụ phím chưa bật thì bật; đang bật thì suaCauHinh vừa khởi động lại nó để đọc khoá mới.
            if (!sh("settings get secure enabled_accessibility_services").contains("$pkgHome/$NS_HOME.HomeKeyService")) batDichVu("HomeKeyService")
            quaHomeSach = !laHomeSach
            Thread.sleep(2000)
        }
        var mo = ""
        repeat(4) { lan ->
            giaHanGac()
            sh("am start -a android.settings.SETTINGS >/dev/null 2>&1")
            Thread.sleep(1500)
            sh("input keyevent KEYCODE_HOME")
            // Home sạch mở đè sau khi Home gốc hiện ra (chậm trên máy bận): đợi tới 6 giây.
            for (giay in 1..6) {
                Thread.sleep(1000)
                mo = goiDangHien()
                if (mo == goi) {
                    boGac()
                    sl.homeDaDoi = true
                    luuSaoLuu(sl)
                    // Launcher khác đã là Home: Home sạch chỉ còn lo nút remote / mở đè, bỏ màn Home cũ của nó khỏi RAM.
                    if (!laHomeSach) giaiPhongHomeSach()
                    return KetQuaDatHome(true, mo, daTat, boKhoa, quaHomeSach)
                }
                if (mo == "android") break
            }
            if (mo == "android") {
                // Hộp chọn Home (vừa bật thêm một launcher cùng mức): đóng, đặt lại rồi thử lần nữa.
                sh("input keyevent KEYCODE_BACK")
                Thread.sleep(800)
                sh("cmd package set-home-activity --user 0 $cmp")
            } else if (lan == 0) bao(t("Chờ thêm rồi thử lại…", "Waiting a little, then trying again…"))
        }
        return traLai("mo:$mo", mo)
    }

    /**
     * Tắt hẳn Home sạch rồi bật lại dịch vụ phím của nó (TAT_HAN_HOME): chỉ còn dịch vụ chạy, màn Home cũ thôi nằm trong RAM.
     * Đo trên TX3 Mini: Home sạch giữ màn Home cũ 18–32 MB, chỉ dịch vụ phím 5 MB (27/10/2026).
     */
    private fun giaiPhongHomeSach() {
        if (!coGoi(pkgHome) || runCatching { goiDangHien() }.getOrDefault("") == pkgHome) return
        runCatching { sh(TAT_HAN_HOME) }
    }

    /**
     * Bật dịch vụ trợ năng [dv] ("gói/lớp") của một launcher (Projectivy dùng nó để tự thay Home của hãng): thêm vào danh
     * sách, giữ các dịch vụ đang có — LMATV ghi đè cả danh sách, mất luôn dịch vụ phím của Home sạch. [lamMoi]: gỡ ra rồi
     * thêm lại, cách chữa Projectivy thôi thay Home sau bản cập nhật Google TV tháng 3/2026.
     */
    fun batTroNang(dv: String, lamMoi: Boolean) {
        val day = Launcher.dayDu(dv)
        require(Regex("""^[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+$""").matches(day)) { dv }
        val ds = dsTroNang()
        val khac = ds.filter { Launcher.dayDu(it) != day }
        if (lamMoi && khac.size != ds.size) { ghiTroNang(khac); Thread.sleep(1000) }
        ghiTroNang(khac + day)
        sh("settings put secure accessibility_enabled 1")
    }

    /** Tắt dịch vụ trợ năng [dv], giữ các dịch vụ khác. */
    fun tatTroNang(dv: String) {
        val day = Launcher.dayDu(dv)
        ghiTroNang(dsTroNang().filter { Launcher.dayDu(it) != day })
    }

    private fun dsTroNang(): List<String> =
        sh("settings get secure enabled_accessibility_services").trim().split(':').filter { it.isNotBlank() && it != "null" }

    private fun ghiTroNang(ds: List<String>) {
        if (ds.isEmpty()) sh("settings delete secure enabled_accessibility_services")
        else sh("settings put secure enabled_accessibility_services '${ds.joinToString(":")}'")
    }

    /** Cho launcher [goi] gắn widget không cần hộp hỏi (Launcher.WIDGET); false nếu tivi từ chối. */
    fun choPhepWidget(goi: String): Boolean {
        require(TEN_GOI.matches(goi)) { goi }
        val out = sh("appwidget grantbind --package $goi --user 0 2>&1")
        return !Regex("Error|Exception|usage", RegexOption.IGNORE_CASE).containsMatchIn(out)
    }

    /** Mở màn cài đặt riêng của launcher [goi] (Launcher.CAI_DAT); false nếu app không có hay tivi từ chối. */
    fun moCaiDatCua(goi: String): Boolean {
        val hanhDong = Launcher.CAI_DAT[goi] ?: return false
        return !sh("am start -a $hanhDong -p $goi 2>&1").contains("Error")
    }

    // ---------- Canh gác Home trên tivi (24/10/2026) ----------

    /**
     * Trước khi tắt một Home trên tivi, hẹn giờ NGAY TRÊN TIVI: quá GAC_GIAY giây không được gia hạn (điện thoại rớt
     * Wi-Fi, hết pin, app bị tắt giữa chừng) thì tivi tự bật lại các Home vừa tắt và đặt lại Home gốc. Sự cố 1.1 (TCL
     * BeyondTV4 tắt launcherx rồi mất kết nối → đen màn) mà có nó thì tivi đã tự cứu. Học từ cleanyourtv-app
     * (udhaykumarbala, watchdog 150 giây). Kịch bản chạy bằng setsid nên sống qua lúc phiên adb đóng.
     */
    private var gacToken: String? = null

    /** Dời hạn canh gác thêm GAC_GIAY tính từ bây giờ (giờ của tivi); chưa hẹn thì thôi. */
    fun giaHanGac() {
        if (gacToken != null) runCatching { sh("echo \$((\$(date +%s) + $GAC_GIAY)) > $GAC-han") }
    }

    /** Ghi [pkg] vào danh sách tivi sẽ tự bật lại; lần đầu thì đặt kịch bản canh gác. Gói đầu danh sách là Home gốc. */
    fun gacThem(pkg: String) {
        if (!TEN_GOI.matches(pkg)) return
        if (gacToken == null) {
            val tk = java.util.UUID.randomUUID().toString().replace("-", "").take(12)
            runCatching {
                adb.ghiFile("$GAC.sh", KICH_BAN_GAC.toByteArray())
                sh(": > $GAC-list; echo $tk > $GAC-token")
                gacToken = tk
                giaHanGac()
                // Trong ngoặc: KetNoiAdb.shell nối "; echo <dấu>" vào sau, "… &; echo" là lỗi cú pháp. setsid: tách khỏi
                // phiên shell của app để sống qua lúc phiên đóng (thử trên máy ảo 24/10/2026).
                // Đợi 1 giây trong cùng lệnh: app đóng phiên ngay khi thấy dấu kết thúc, đóng sớm quá là tiến trình
                // con chết trước khi kịp tách. Không thấy chạy thì thử lại một lần.
                repeat(2) {
                    if (sh("(setsid sh $GAC.sh $tk </dev/null >/dev/null 2>&1 &); sleep 1; ps -A -o ARGS | grep -c '^sh $GAC.sh $tk'")
                            .trim().lines().lastOrNull()?.toIntOrNull()?.let { it > 0 } == true) return@runCatching
                }
            }
        }
        runCatching { sh("grep -qx '$pkg' $GAC-list || echo '$pkg' >> $GAC-list") }
        giaHanGac()
    }

    /** Việc với Home đã xong (thành công hay đã tự lui): huỷ canh gác. */
    fun boGac() {
        gacToken = null
        runCatching { sh("rm -f $GAC-token $GAC-list $GAC-han") }
    }

    // ---------- DNS chặn quảng cáo ----------

    /** Tivi đang dùng DNS chặn quảng cáo của AdGuard (do CleanTV hay người dùng đặt). */
    fun dnsDangChan(): Boolean = runCatching {
        sh("settings get global private_dns_specifier").trim() == "dns.adguard-dns.com" &&
            sh("settings get global private_dns_mode").trim() == "hostname"
    }.getOrDefault(false)

    /**
     * Tắt DNS riêng app đã đặt: trả DNS như trước khi app đổi (bản sao lưu), không có thì về tự động. DNS riêng người dùng
     * tự đặt từ trước (VD NextDNS của họ) thì trả lại nguyên chế độ bắt buộc; còn máy chủ của app (AdGuard) ghi trong sao
     * lưu nghĩa là sao lưu chụp sau khi app đã đổi — không phải DNS gốc.
     */
    fun tatDnsChanQc(sl: BanSaoLuu?) {
        val hostCu = sl?.dnsHostTruoc?.takeIf { it.isNotBlank() && it != "null" && !it.equals(DnsRieng.ADGUARD, true) && !it.equals(DnsRieng.ADGUARD_GIA_DINH, true) }
        val modeCu = sl?.dnsModeTruoc?.takeIf { it.isNotBlank() && it != "null" && (it != "hostname" || hostCu != null) } ?: "opportunistic"
        datDnsTho(modeCu, hostCu ?: "")
        sl?.let { it.dnsDaDoi = false; luuSaoLuu(it) }
    }

    fun batDnsChanQc(sl: BanSaoLuu) = batDnsRieng(sl, DnsRieng.ADGUARD)

    // ---------- DNS riêng: AdGuard / NextDNS / tự nhập (25/10/2026) ----------

    /** (chế độ, tên máy chủ) DNS riêng đang đặt; chế độ: off / opportunistic / hostname. */
    fun dnsHienTai(): Pair<String, String> =
        sh("settings get global private_dns_mode").trim() to sh("settings get global private_dns_specifier").trim()

    /** Tên máy chủ DoT đang bắt buộc dùng, null nếu tivi dùng DNS thường. */
    fun dnsRiengDangDung(): String? = dnsHienTai().let { (mode, host) -> host.takeIf { mode == "hostname" && it.isNotBlank() && it != "null" } }

    /** Đặt DNS riêng (DoT) [host] ở chế độ bắt buộc; ghi vào sao lưu để Khôi phục trả lại DNS trước khi app đổi. */
    fun batDnsRieng(sl: BanSaoLuu, host: String) {
        require(DnsRieng.hostHopLe(host)) { "host" }
        sh("settings put global private_dns_specifier $host")
        sh("settings put global private_dns_mode hostname")
        sl.dnsDaDoi = true; luuSaoLuu(sl)
    }

    /** Đặt thẳng chế độ + tên máy chủ (trả lại trạng thái cũ). Chế độ bắt buộc mà thiếu tên máy chủ hợp lệ thì về tự động. */
    fun datDnsTho(mode: String, host: String) {
        val coHost = host.isNotBlank() && host != "null" && DnsRieng.hostHopLe(host)
        val m = mode.takeIf { it in setOf("off", "opportunistic", "hostname") && (it != "hostname" || coHost) } ?: "opportunistic"
        if (coHost) sh("settings put global private_dns_specifier $host") else sh("settings delete global private_dns_specifier")
        sh("settings put global private_dns_mode $m")
    }

    /**
     * Chờ Android kiểm tra máy chủ DoT [host], tối đa [giay] giây: true dùng được, false hỏng, null chưa rõ. Android 9–10
     * không báo "hỏng" (DnsRieng.daXacNhan): hết giờ thì thử phân giải một tên thật bằng resolver của tivi (ping in
     * "unknown host" khi DNS hỏng, dòng "PING" khi phân giải được — kể cả mạng chặn ICMP).
     */
    fun kiemTraDns(host: String, giay: Int = 20): Boolean? {
        val het = System.currentTimeMillis() + giay * 1000L
        while (System.currentTimeMillis() < het) {
            DnsRieng.daXacNhan(sh("dumpsys connectivity | grep -E 'PrivateDns|Private DNS'"), host)?.let { return it }
            Thread.sleep(2000)
        }
        val ping = sh("ping -c 1 -W 3 example.com 2>&1")
        return when {
            ping.contains("unknown host", ignoreCase = true) -> false
            ping.contains("PING", ignoreCase = true) -> true
            else -> null
        }
    }

    /**
     * Đặt DNS riêng [host] an toàn: đặt, chờ Android kiểm tra; hỏng (sai tên máy chủ, mạng chặn cổng 853) thì trả lại
     * đúng DNS trước đó và cờ sao lưu, để tivi không mất mạng. Trả về như kiemTraDns.
     */
    fun datDnsAnToan(sl: BanSaoLuu, host: String): Boolean? {
        val (modeCu, hostCu) = dnsHienTai()
        val daDoiCu = sl.dnsDaDoi
        batDnsRieng(sl, host)
        val kq = kiemTraDns(host)
        if (kq == false) {
            datDnsTho(modeCu, hostCu)
            sl.dnsDaDoi = daDoiCu; luuSaoLuu(sl)
        }
        return kq
    }

    // ---------- Khôi phục ----------

    fun xoaSaoLuuTrenTv() { runCatching { sh("rm -f $TEP_SAO_LUU_TV") } }

    /** Gói đang bị tắt bằng tay / bằng shell (enabled=3: disable-user), không gồm gói hãng tắt sẵn từ xưởng. */
    /** Bật lại một gói; trả true khi tivi báo đã bật. Có bản sao lưu thì bỏ gói đó khỏi danh sách CleanTV đã tắt. */
    fun batLaiGoi(p: String, sl: BanSaoLuu?): Boolean {
        val ok = runCatching { sh("pm enable --user 0 $p") }.getOrDefault("").contains("enabled")
        if (ok && sl != null && p in sl.daTatBoiApp) { sl.daTatBoiApp -= p; luuSaoLuu(sl) }
        return ok
    }

    fun goiTatBoiNguoiDung(): List<String> =
        sh("for p in \$(pm list packages -d | cut -d: -f2); do dumpsys package \$p | grep -q 'User 0:.*enabled=3' && echo \$p; done")
            .lines().map { it.trim() }.filter { TEN_GOI.matches(it) }.sorted()

    /**
     * Khôi phục khi KHÔNG có bản sao lưu (gỡ app, đổi máy, người khác dọn — 22/10/2026): bật lại mọi gói đang tắt kiểu
     * disable-user (kể cả gói chủ tivi tự tắt trước đó, màn hình đã hỏi xác nhận), bỏ dịch vụ trợ năng của Home sạch,
     * trả DNS AdGuard của app về tự động, đặt lại Home hệ thống của hãng; tuỳ chọn gỡ Home sạch.
     */
    fun khoiPhucKhongSaoLuu(goLauncher: Boolean, bao: (String) -> Unit): List<String> {
        val loi = mutableListOf<String>()
        val ds = goiTatBoiNguoiDung()
        ds.forEachIndexed { i, p ->
            bao(t("Bật lại ${i + 1}/${ds.size}: $p", "Re-enabling ${i + 1}/${ds.size}: $p"))
            val out = runCatching { sh("pm enable --user 0 $p") }.getOrElse { "${it.message}" }
            if (!out.contains("enabled")) loi += "$p: ${out.trim()}"
        }
        val tc = sh("settings get secure enabled_accessibility_services").trim()
        if (HOME_SACH.any { tc.contains("$it/") }) {
            val con = tc.split(':').filter { d -> d.isNotBlank() && HOME_SACH.none { d.startsWith("$it/") } }.joinToString(":")
            if (con.isBlank()) sh("settings delete secure enabled_accessibility_services") else sh("settings put secure enabled_accessibility_services '$con'")
        }
        // Không có sao lưu: chỉ gỡ DNS do app đặt (AdGuard); NextDNS / DNS khác có thể là của chính chủ nhà.
        if (DnsRieng.loaiCua(sh("settings get global private_dns_specifier").trim()) in setOf(DnsRieng.Loai.ADGUARD, DnsRieng.Loai.ADGUARD_GIA_DINH)) {
            bao(t("Trả DNS về tự động…", "Setting DNS back to automatic…"))
            sh("settings put global private_dns_mode opportunistic; settings delete global private_dns_specifier")
        }
        if (khoaDaCai()) { bao(t("Gỡ Khoá trẻ em…", "Removing the Kids lock…")); runCatching { tatKhoa(go = true) } }
        oKenhDaCai().takeIf { it.isNotEmpty() }?.let { ds -> bao(t("Gỡ Kênh nhanh…", "Removing Quick channel…")); ds.forEach { runCatching { goKenh(it) } } }
        if (henGioDaCai()) { bao(t("Gỡ Hẹn giờ…", "Removing the Timer…")); runCatching { goHenGio() } }
        if (castDaCai()) { bao(t("Gỡ Cast SmartTube…", "Removing Cast to SmartTube…")); runCatching { goCast() } }
        if (boGoDaCai()) { bao(t("Gỡ bộ gõ tiếng Việt…", "Removing the Vietnamese typing helper…")); runCatching { goBoGo() } }
        // TiviKit Launcher đang là Home cũng gỡ: Home được đặt lại ở trên (có sao lưu) hay ngay bên dưới (không sao lưu).
        if (tklDaCai()) { bao(t("Gỡ TiviKit Launcher…", "Removing TiviKit Launcher…")); runCatching { sh("pm uninstall ${TiviKitLauncher.GOI}") } }
        goAppPhuCu(bao)
        if (goLauncher) HOME_SACH.forEach { p ->
            if (sh("pm path $p").isNotBlank()) { bao(t("Gỡ Home sạch ($p)…", "Removing the clean Home ($p)…")); sh("pm uninstall $p") }
        } else boDichHome()
        // Home hệ thống của hãng: ưu tiên Home gốc app biết, rồi app hệ thống đầu tiên có màn HOME dùng được.
        val heThong = sh("pm list packages -s").lines().map { it.trim().removePrefix("package:") }.toSet()
        val ung = sh("cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME")
            .lines().map { it.trim() }.filter { it.contains('/') && !it.endsWith("FallbackHome") }
            .filter { c -> val p = c.substringBefore('/'); p in heThong && p !in HOME_SACH && p !in KHONG_PHAI_HOME && !APP_XUAT_XUONG.containsMatchIn(p) }
        val home = HOME_GOC_BIET.firstNotNullOfOrNull { g -> ung.firstOrNull { it.startsWith("$g/") } } ?: ung.firstOrNull()
        if (home != null) {
            bao(t("Đặt Home gốc: $home", "Setting the stock Home: $home"))
            sh("cmd package set-home-activity --user 0 $home")
        }
        sh("input keyevent KEYCODE_HOME")
        return loi
    }

    /** Đưa tivi về như trước: bật lại gói app đã tắt, trả Home gốc, gỡ Home sạch nếu muốn, trả DNS. */
    fun khoiPhuc(sl: BanSaoLuu, goLauncher: Boolean, bao: (String) -> Unit): List<String> {
        val loi = mutableListOf<String>()
        val ds = sl.daTatBoiApp.toList()
        ds.forEachIndexed { i, p ->
            bao(t("Bật lại ${i + 1}/${ds.size}: $p", "Re-enabling ${i + 1}/${ds.size}: $p"))
            val out = runCatching { sh("pm enable --user 0 $p") }.getOrElse { t("lỗi ${it.message}", "error ${it.message}") }
            if (out.contains("enabled")) { sl.daTatBoiApp -= p; luuSaoLuu(sl) } else loi += "$p: ${out.trim()}"
        }
        if (sl.launcherDaCai) {
            bao(t("Trả Home gốc…", "Restoring the stock Home…"))
            val goc = sl.homeTruoc
            if (goc.isNotBlank() && HOME_SACH.none { goc.startsWith("$it/") }) {
                sh("cmd package set-home-activity --user 0 $goc")
            }
            if (goLauncher) {
                bao(t("Gỡ Home sạch…", "Removing the clean Home…"))
                HOME_SACH.filter { it == PKG_HOME || it == pkgHome }.forEach { sh("pm uninstall $it") }
                // Trả danh sách dịch vụ trợ năng như trước khi làm (app có thể đã bật HomeKeyService / InputKeyService).
                val tc = sl.truyCapTruoc
                if (tc.isBlank() || tc == "null") sh("settings delete secure enabled_accessibility_services")
                else sh("settings put secure enabled_accessibility_services '$tc'")
            } else boDichHome() // Home sạch ở lại: thôi mở launcher đã chọn ở Quản lý launcher thay chính nó.
            sl.launcherDaCai = !goLauncher && sl.launcherDaCai
            sl.homeGocDaTat = ""
            sl.homeDaDoi = false
            luuSaoLuu(sl)
        }
        if (sl.homeDaDoi) {
            // Home đổi ở màn Quản lý launcher, tivi chưa từng cài Home sạch bằng app (27/10/2026): đặt lại Home như trước.
            val goc = sl.homeTruoc
            if (goc.contains('/') && !goc.startsWith("android/") && HOME_SACH.none { goc.startsWith("$it/") }) {
                bao(t("Trả Home như trước…", "Restoring the previous Home…"))
                sh("cmd package set-home-activity --user 0 $goc")
            }
            boDichHome()
            sl.homeDaDoi = false
            luuSaoLuu(sl)
        }
        if (sl.dnsDaDoi) {
            bao(t("Trả DNS như cũ…", "Restoring DNS…"))
            val mode = sl.dnsModeTruoc.takeIf { it.isNotBlank() && it != "null" } ?: "opportunistic"
            sh("settings put global private_dns_mode $mode")
            if (sl.dnsHostTruoc.isBlank() || sl.dnsHostTruoc == "null") sh("settings delete global private_dns_specifier")
            else sh("settings put global private_dns_specifier ${sl.dnsHostTruoc}")
            sl.dnsDaDoi = false; luuSaoLuu(sl)
        }
        if (khoaDaCai()) { bao(t("Gỡ Khoá trẻ em…", "Removing the Kids lock…")); runCatching { tatKhoa(go = true) } }
        oKenhDaCai().takeIf { it.isNotEmpty() }?.let { ds -> bao(t("Gỡ Kênh nhanh…", "Removing Quick channel…")); ds.forEach { runCatching { goKenh(it) } } }
        if (henGioDaCai()) { bao(t("Gỡ Hẹn giờ…", "Removing the Timer…")); runCatching { goHenGio() } }
        if (castDaCai()) { bao(t("Gỡ Cast SmartTube…", "Removing Cast to SmartTube…")); runCatching { goCast() } }
        if (boGoDaCai()) { bao(t("Gỡ bộ gõ tiếng Việt…", "Removing the Vietnamese typing helper…")); runCatching { goBoGo() } }
        // TiviKit Launcher đang là Home cũng gỡ: Home được đặt lại ở trên (có sao lưu) hay ngay bên dưới (không sao lưu).
        if (tklDaCai()) { bao(t("Gỡ TiviKit Launcher…", "Removing TiviKit Launcher…")); runCatching { sh("pm uninstall ${TiviKitLauncher.GOI}") } }
        goAppPhuCu(bao)
        return loi
    }

    // ---------- Khoá trẻ em (app vn.tivikit.khoa, 23/10/2026) ----------

    private val khoaUri = "content://$PKG_KHOA"
    private val lopKhoa = "vn.tivikit.khoa.KhoaDichVu"
    private val khoaDv = "$PKG_KHOA/$lopKhoa"

    /**
     * App Khoá trẻ em đang có trên tivi: bản mới, không thì bản trước khi đổi tên gói (GoiCu.KHOA — vẫn khoá bằng PIN cũ,
     * chờ phụ huynh cài mã OTP để chuyển); null nếu không có.
     */
    private fun goiKhoa(): String? = when { coGoi(PKG_KHOA) -> PKG_KHOA; coGoi(GoiCu.KHOA) -> GoiCu.KHOA; else -> null }

    fun khoaDaCai(): Boolean = goiKhoa() != null

    /**
     * Trạng thái trên tivi (không có mã): {bat, khung, moTamPhut, moTamDen, dangKhoa, dichVuChay…}; app khoá 1.2 thêm
     * {coOtp, soDuPhong, choGiay, yeuCauGo, goDuoc}, bản trước đó có coPin. Thêm banCu=true khi là app bản trước khi đổi
     * tên gói; null nếu chưa cài.
     */
    fun docKhoa(): org.json.JSONObject? {
        val g = goiKhoa() ?: return null
        return docQua(g)?.apply { if (g == GoiCu.KHOA) put("banCu", true) }
    }

    /**
     * Nhập mã Khoá trẻ em (28/10/2026): tivi kiểm tra mã OTP / mã dự phòng — TiviKit không giữ mã gốc — đúng thì trả phiên
     * cho các lệnh đổi cấu hình. {ok, phien} / {loi: "sai", con} / {loi: "cho", giay}; null nếu không đọc được.
     */
    fun xacThucKhoa(ma: String): org.json.JSONObject? {
        require(ma.length in 6..8 && ma.all { it in '0'..'9' }) { "ma" }
        return docQua(PKG_KHOA, "xac-thuc --arg $ma")
    }

    /** Báo tivi quên mã: 24 giờ sau đổi được khoá không cần mã. {yeuCauGo, goDuocLuc}. */
    fun quenMaKhoa(): org.json.JSONObject? = docQua(PKG_KHOA, "quen-ma")

    /**
     * Hẹn khoá app (28/10/2026), cần phiên: {"phien","goi":[…],"sauPhut":N} khoá các app đó từ N phút nữa tới hết ngày;
     * {"phien","bo":i} bỏ hẹn thứ i. [json] không chứa nháy đơn.
     */
    fun henKhoaApp(json: String) {
        require(!json.contains('\'')) { "json" }
        val kq = sh("content call --uri $khoaUri --method hen-khoa --arg '$json'")
        kiemPhien(kq)
        if (!kq.contains("ok=1")) error(t("Tivi không nhận hẹn khoá: ${kq.trim()}", "The TV rejected the timer: ${kq.trim()}"))
    }

    /** Lệnh khoá trả "can-ma": phiên nhập mã không có / hết hạn. */
    private fun kiemPhien(kq: String) { if (kq.contains("can-ma")) throw CanMaException() }

    /**
     * Cài (nếu cần), cấp quyền tự canh gác, ghi cấu hình, bật dịch vụ khoá; tivi còn bản cũ (trước khi đổi tên gói) thì
     * tắt và gỡ bản đó khi bản mới đã chạy. [json] không chứa dấu nháy đơn.
     */
    fun caiKhoa(apk: ByteArray, json: String, bao: (String) -> Unit) {
        require(!json.contains('\'')) { "json" }
        // Chưa có, hoặc khác bản đi kèm (VD bản dựng trước khi mã nguồn đổi tên lớp) thì cài đè: giữ giờ khoá và mã.
        caiAppPhu(PKG_KHOA, apk) { bao(t("Cài app Khoá trẻ em lên tivi…", "Installing the Kids lock app on the TV…")) }
        sh("pm grant $PKG_KHOA android.permission.WRITE_SECURE_SETTINGS 2>/dev/null; cmd appops set $PKG_KHOA AUTO_START allow 2>/dev/null; $capThongKe")
        bao(t("Ghi giờ khoá và mã…", "Saving the schedule and code…"))
        val kq = sh("content call --uri $khoaUri --method dat --arg '$json'")
        kiemPhien(kq)
        if (!kq.contains("ok=1")) error(t("Tivi không nhận cấu hình: ${kq.trim()}", "The TV rejected the settings: ${kq.trim()}"))
        // Khoá tính theo giờ tivi: bật giờ tự động ngay (app khoá giữ nó bật từ đây, xem KhoaDichVu.giuGioTuDong).
        if (org.json.JSONObject(json).optBoolean("bat")) sh("settings put global auto_time 1")
        bao(t("Bật dịch vụ khoá…", "Turning the lock service on…"))
        // Bản cài đè từ bản có tên lớp cũ: bỏ tên dịch vụ cũ (không còn lớp đó) trước khi bật tên mới.
        boTroNang("$PKG_KHOA/${GoiCu.LOP_KHOA_CU}")
        sh("s=\$(settings get secure enabled_accessibility_services); case \"\$s\" in *$khoaDv*) ;; null|'') settings put secure enabled_accessibility_services $khoaDv ;; *) settings put secure enabled_accessibility_services \"\$s:$khoaDv\" ;; esac; settings put secure accessibility_enabled 1")
        if (coGoi(GoiCu.KHOA)) { bao(t("Gỡ Khoá trẻ em bản cũ…", "Removing the old Kids lock…")); tatKhoaGoi(GoiCu.KHOA, go = true) }
    }

    /** Báo cáo xem app gì cần quyền xem thống kê sử dụng của tivi (chỉ ADB cấp được). */
    private val capThongKe = "cmd appops set $PKG_KHOA GET_USAGE_STATS allow 2>/dev/null; true"

    /**
     * Báo cáo 7 ngày gần nhất của app khoá (25/10/2026): thời gian xem mỗi ngày và app đã mở (xem dulieu/ThoiGianXem);
     * null nếu tivi chưa có app khoá bản mới (bản trước 25/10/2026 chưa đếm giờ: Lưu lại để cập nhật).
     */
    fun docThoiGianKhoa(): org.json.JSONObject? {
        if (!coGoi(PKG_KHOA)) return null
        sh(capThongKe)
        return docQua(PKG_KHOA, "thoi-gian")
    }

    /**
     * Mở tạm [phut] phút từ điện thoại (0 = khoá lại ngay nếu đang trong giờ khoá / đã hết giờ xem). Tivi có mã OTP thì
     * kèm [phien] (app khoá 1.2 nhận JSON); không thì gửi số phút như bản cũ.
     */
    fun moTamKhoa(phut: Int, phien: String?) {
        val g = goiKhoa() ?: return
        val arg = if (phien == null) "$phut" else { require(PHIEN.matches(phien)) { "phien" }; "'{\"phut\":$phut,\"phien\":\"$phien\"}'" }
        kiemPhien(sh("content call --uri content://$g --method mo-tam --arg $arg"))
    }

    /**
     * Tắt khoá (bản mới, cả bản cũ nếu còn): cấu hình bat=false (kèm [phien] nếu có), bỏ dịch vụ khỏi danh sách trợ năng;
     * [go] = gỡ luôn app. Chỗ gọi lo hỏi mã trước (màn Khoá trẻ em, Khôi phục): shell gỡ được app dù không có phiên.
     */
    fun tatKhoa(go: Boolean, phien: String? = null) {
        listOf(PKG_KHOA, GoiCu.KHOA).filter { coGoi(it) }.forEach { tatKhoaGoi(it, go, phien) }
    }

    private fun tatKhoaGoi(goi: String, go: Boolean, phien: String? = null) {
        val p = phien?.takeIf { PHIEN.matches(it) }?.let { ",\"phien\":\"$it\"" }.orEmpty()
        sh("content call --uri content://$goi --method dat --arg '{\"bat\":false,\"khung\":[]$p}' 2>/dev/null; true")
        // Tên lớp dịch vụ tuỳ bản đang cài: mới, hoặc dựng trước khi mã nguồn đổi tên (GoiCu.LOP_KHOA_CU).
        boTroNang("$goi/$lopKhoa"); boTroNang("$goi/${GoiCu.LOP_KHOA_CU}")
        if (go) sh("pm uninstall $goi")
    }

    // ---------- Kênh nhanh (mỗi ô kênh một app: vn.tivikit.kenh, kenh2 … kenh6; 25/10/2026) ----------

    fun kenhDaCai(o: Int): Boolean = coGoi(KenhNhanh.goiCua(o))

    /** Các ô kênh đang có trên tivi. */
    fun oKenhDaCai(): List<Int> {
        val co = goiDaCai()
        return (1..KenhNhanh.SO_O).filter { KenhNhanh.goiCua(it) in co }
    }

    /** Kênh đang đặt trong ô [o] trên tivi ({so, ten}); null nếu chưa cài / đọc không được. */
    fun docKenh(o: Int): org.json.JSONObject? = if (!kenhDaCai(o)) null else docQua(KenhNhanh.goiCua(o))

    /**
     * Cài app của ô [o] (chưa có, hoặc khác bản đi kèm TiviKit thì cài đè — như capNhatLauncher) rồi đặt kênh.
     * [json] không chứa nháy đơn (KenhNhanh.tenSach).
     */
    fun caiKenh(o: Int, apk: ByteArray, json: String, bao: (String) -> Unit) {
        require(!json.contains('\'')) { "json" }
        caiAppPhu(KenhNhanh.goiCua(o), apk) { bao(t("Cài ô kênh $o lên tivi…", "Installing channel tile $o on the TV…")) }
        bao(t("Đặt kênh…", "Setting the channel…"))
        val kq = sh("content call --uri content://${KenhNhanh.goiCua(o)} --method dat --arg '$json'")
        if (!kq.contains("ok=1")) error(t("Tivi không nhận kênh: ${kq.trim()}", "The TV rejected the channel: ${kq.trim()}"))
    }

    /**
     * Ô kênh [goi] trên Home sạch: tên ô [ten] (app_labels; null = bỏ cả ô), logo [anh] (app_banners, tên ảnh đã gửi bằng
     * ghiAnhOKenh; null = không logo) và có trong dock không, ghi vào config.json ĐANG CÓ (như ghiPhimRieng) rồi khởi động
     * lại Home sạch cho thấy ngay. Trả kết quả của KenhNhanh.datDock: false = dock đã đủ [toiDa] ô, null = Home sạch đang
     * dùng dock mặc định (để nguyên).
     */
    fun ghiOKenh(goi: String, ten: String?, anh: String?, trongDock: Boolean, toiDa: Int): Boolean? {
        val j = cauHinhDeSua()
        val truoc = j.toString()
        KenhNhanh.datTen(j, goi, ten)
        KenhNhanh.datAnh(j, goi, anh.takeIf { ten != null })
        val dock = KenhNhanh.datDock(j, goi, trongDock, toiDa)
        // Không đổi gì thì thôi ghi (ghi config.json làm Home sạch đặt lại vài chỉnh sửa làm trên tivi) và khởi động lại.
        if (j.toString() != truoc) {
            ghiFileTheme("config.json", j.toString().toByteArray())
            sh(KHOI_DONG_LAI_HOME)
        }
        return dock
    }

    /** Gửi logo kênh của ô [o] (JPEG) cạnh config.json của Home sạch, tên KenhNhanh.tenAnh(o) + ".jpg". */
    fun ghiAnhOKenh(o: Int, anh: ByteArray) = ghiFileTheme(KenhNhanh.tenAnh(o) + ".jpg", anh)

    /**
     * Ô kênh VTVgo kiểu cũ (ô link của Home sạch): gửi ảnh ô ([anh]: tên file sau "kenh-", ảnh), rồi ghi lại link_tiles
     * và các ô ở đầu dock theo [kenh] vào config.json ĐANG CÓ (như ghiOKenh: không đụng giao diện, ảnh nền), khởi động lại
     * Home sạch. Kênh nhanh dùng để bỏ các ô đã chuyển thành ô Kênh nhanh. false = Home sạch đang dùng dock mặc định
     * (config.json không có dock_apps): chưa ghi gì.
     */
    fun ghiKenhVtvGo(kenh: List<Pair<String, String>>, anh: List<Pair<String, ByteArray>>): Boolean {
        val j = cauHinhDeSua()
        if (!KenhVtvGo.datVao(j, kenh)) return false
        anh.forEach { (ten, du) -> ghiFileTheme("kenh-$ten", du) }
        ghiFileTheme("config.json", j.toString().toByteArray())
        sh(KHOI_DONG_LAI_HOME)
        return true
    }

    /**
     * Mở ô kênh [o] trên tivi như bấm ô / nút remote; trả chữ am start in ra. Màn mở hỏi theo gói (ô cài từ bản dựng
     * trước khi mã nguồn đổi tên lớp vẫn mở được), không ra thì dùng tên lớp hiện tại.
     */
    fun moKenhNhanh(o: Int): String {
        val goi = KenhNhanh.goiCua(o)
        val c = sh("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER $goi | tail -n 1").trim()
        return sh("am start -n ${if (Regex("""^[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+$""").matches(c)) c else KenhNhanh.moCua(o)} 2>&1")
    }

    fun goKenh(o: Int) { if (kenhDaCai(o)) sh("pm uninstall ${KenhNhanh.goiCua(o)}") }

    /** Mở trang của [goi] trên CH Play của tivi (TV360 cho Kênh nhanh, app gợi ý chỉ có trên CH Play); trả chữ am start in ra. */
    fun moCHPlay(goi: String): String {
        require(TEN_GOI.matches(goi)) { "goi" }
        return sh("am start -a android.intent.action.VIEW -d 'market://details?id=$goi' 2>&1")
    }

    /**
     * Cài app phụ đi kèm TiviKit ([goi]: Kênh nhanh, Hẹn giờ, Khoá trẻ em) khi tivi chưa có hoặc đang có bản khác bản đi
     * kèm (so SHA-256 như capNhatLauncher) — cài đè giữ dữ liệu của nó. [bao] chỉ gọi khi thật sự cài.
     */
    private fun caiAppPhu(goi: String, apk: ByteArray, bao: () -> Unit) {
        val bam = java.security.MessageDigest.getInstance("SHA-256").digest(apk).joinToString("") { "%02x".format(it) }
        val coSan = if (!coGoi(goi)) "" else runCatching {
            sh("sha256sum \$(pm path $goi | head -n 1 | sed 's/^package://') 2>/dev/null").trim().substringBefore(' ')
        }.getOrDefault("")
        if (coSan == bam) return
        bao()
        val tam = "/data/local/tmp/$goi.apk"
        adb.ghiFile(tam, apk)
        val out = sh("pm install -r $tam; rm -f $tam")
        if (!out.contains("Success")) error(t("Cài không được: ${out.trim().lines().lastOrNull()}", "Install failed: ${out.trim().lines().lastOrNull()}"))
        if (goi in GIU_SONG) runCatching { sh(lenhGiuSong(listOf(goi))) }
    }

    /** JSON trạng thái của app phụ [goi] (content call … --method doc); null nếu không trả lời / đọc không được. */
    private fun docQua(goi: String, phuongThuc: String = "doc"): org.json.JSONObject? {
        val out = sh("content call --uri content://$goi --method $phuongThuc 2>/dev/null")
        val a = out.indexOf("json="); val b = out.lastIndexOf("}]")
        return if (a < 0 || b <= a) null else runCatching { org.json.JSONObject(out.substring(a + 5, b)) }.getOrNull()
    }

    /** Bỏ một dịch vụ khỏi danh sách trợ năng đang bật (các dịch vụ khác giữ nguyên). */
    private fun boTroNang(dv: String) {
        val tc = sh("settings get secure enabled_accessibility_services").trim()
        if (!tc.contains(dv)) return
        val con = tc.split(':').filter { it.isNotBlank() && it != dv && it != "null" }.joinToString(":")
        if (con.isBlank()) sh("settings delete secure enabled_accessibility_services") else sh("settings put secure enabled_accessibility_services '$con'")
    }

    // ---------- Hẹn giờ (app vn.tivikit.hengio, 25/10/2026) ----------

    private val henGioUri = "content://${HenGio.GOI}"

    fun henGioDaCai(): Boolean = coGoi(HenGio.GOI)

    /** Trạng thái app Hẹn giờ trên tivi (lịch, việc kế tiếp, dịch vụ, nhật ký); null nếu chưa cài / đọc không được. */
    fun docHenGio(): org.json.JSONObject? = if (!henGioDaCai()) null else docQua(HenGio.GOI)

    /**
     * Cài / cập nhật app Hẹn giờ, cấp hai quyền người dùng đã đồng ý ở màn Hẹn giờ — báo thức đúng giờ (Android 12) và bật
     * màn hình (Android 14+), không có lệnh nào trên máy cũ hơn thì bỏ qua — bật dịch vụ trợ năng của nó (hiện trong Cài
     * đặt → Trợ năng, tắt được ở đó), rồi gửi lịch. [json] không chứa nháy đơn (HenGio.json).
     */
    fun caiHenGio(apk: ByteArray, json: String, bao: (String) -> Unit) {
        require(!json.contains('\'')) { "json" }
        caiAppPhu(HenGio.GOI, apk) { bao(t("Cài app Hẹn giờ lên tivi…", "Installing the Timer app on the TV…")) }
        bao(t("Bật dịch vụ Hẹn giờ…", "Turning the Timer service on…"))
        sh("cmd appops set ${HenGio.GOI} SCHEDULE_EXACT_ALARM allow 2>/dev/null; cmd appops set ${HenGio.GOI} TURN_SCREEN_ON allow 2>/dev/null; true")
        // Cài đè từ bản có tên lớp cũ: bỏ tên dịch vụ cũ (không còn lớp đó) trước khi bật tên mới.
        boTroNang("${HenGio.GOI}/${GoiCu.LOP_HEN_GIO_CU}")
        val dv = HenGio.DICH_VU
        sh("s=\$(settings get secure enabled_accessibility_services); case \"\$s\" in *$dv*) ;; null|'') settings put secure enabled_accessibility_services $dv ;; *) settings put secure enabled_accessibility_services \"\$s:$dv\" ;; esac; settings put secure accessibility_enabled 1")
        bao(t("Gửi lịch…", "Sending the schedule…"))
        val kq = sh("content call --uri $henGioUri --method dat --arg '$json'")
        if (!kq.contains("ok=1")) error(t("Tivi không nhận lịch: ${kq.trim()}", "The TV rejected the schedule: ${kq.trim()}"))
    }

    /** Hẹn thử đánh thức sau [giay] giây (người dùng tắt tivi bằng remote ngay sau đó). */
    fun thuDanhThuc(giay: Int): Boolean = sh("content call --uri $henGioUri --method thu --arg $giay 2>&1").contains("ok=1")

    /** Gỡ app Hẹn giờ: bỏ dịch vụ khỏi danh sách trợ năng rồi gỡ (báo thức của nó mất theo). */
    fun goHenGio() {
        if (!henGioDaCai()) return
        boTroNang(HenGio.DICH_VU); boTroNang("${HenGio.GOI}/${GoiCu.LOP_HEN_GIO_CU}")
        sh("pm uninstall ${HenGio.GOI}")
    }

    // ---------- Cast SmartTube (app vn.tivikit.cast, 28/10/2026) ----------

    fun castDaCai(): Boolean = coGoi(CastSmartTube.GOI)

    /** Trạng thái app Cast SmartTube (dịch vụ chạy, đã nối YouTube, điện thoại đang nối…); null nếu chưa cài. */
    fun docCast(): org.json.JSONObject? = if (!castDaCai()) null else docQua(CastSmartTube.GOI)

    /** Có SmartTube trên tivi (bản nào cũng được). */
    fun coSmartTube(): Boolean = goiDaCai().any { it in CastSmartTube.SMARTTUBE }

    /**
     * Cài / cập nhật app Cast SmartTube, cho nó bật màn hình (Android 14+), đọc phiên media của SmartTube (tivi ít RAM không
     * cho thì thôi: vẫn chạy, chỉ không đồng bộ được khi bấm remote tivi), đặt [ten] (null = giữ), bật dịch vụ trợ năng.
     */
    fun caiCast(apk: ByteArray, ten: String?, bao: (String) -> Unit) {
        caiAppPhu(CastSmartTube.GOI, apk) { bao(t("Cài app Cast SmartTube lên tivi…", "Installing Cast to SmartTube on the TV…")) }
        sh("cmd appops set ${CastSmartTube.GOI} TURN_SCREEN_ON allow 2>/dev/null; cmd notification allow_listener ${CastSmartTube.NGHE} 2>/dev/null; true")
        if (ten != null) sh(CastSmartTube.lenhDatTen(ten))
        bao(t("Bật dịch vụ Cast…", "Turning the Cast service on…"))
        val dv = CastSmartTube.DICH_VU
        sh("s=\$(settings get secure enabled_accessibility_services); case \"\$s\" in *$dv*) ;; null|'') settings put secure enabled_accessibility_services $dv ;; *) settings put secure enabled_accessibility_services \"\$s:$dv\" ;; esac; settings put secure accessibility_enabled 1")
    }

    /** Đổi tên hiện trong nút Cast ("" = tên mặc định); điện thoại thấy tên mới ở lần tìm sau. */
    fun datTenCast(ten: String) { sh(CastSmartTube.lenhDatTen(ten)) }

    /** Gỡ app Cast SmartTube: bỏ dịch vụ trợ năng và quyền nghe thông báo rồi gỡ. */
    fun goCast() {
        if (!castDaCai()) return
        boTroNang(CastSmartTube.DICH_VU)
        sh("cmd notification disallow_listener ${CastSmartTube.NGHE} 2>/dev/null; pm uninstall ${CastSmartTube.GOI}")
    }

    // ---------- Gõ tiếng Việt (app vn.tivikit.go, 25/10/2026) ----------

    fun boGoDaCai(): Boolean = coGoi(GoChu.GOI)

    /**
     * Gõ [chu] có dấu vào ô nhập đang chọn trên tivi qua bộ gõ vn.tivikit.go (cài nếu chưa có / khác bản đi kèm; xem
     * GoChu). Trả "ok" hoặc mã lỗi của bộ gõ ("khong-o-nhap"…).
     */
    fun goChuCoDau(apk: ByteArray, chu: String, bao: (String) -> Unit): String {
        var vuaCai = false
        caiAppPhu(GoChu.GOI, apk) { vuaCai = true; bao(t("Cài bộ gõ tiếng Việt lên tivi (chỉ lần đầu)…", "Installing the Vietnamese typing helper on the TV (first time only)…")) }
        // Vừa cài: Android cần chút thời gian mới nhận app là bàn phím (`ime enable` lúc đó báo không biết bộ gõ).
        if (vuaCai) for (i in 1..6) { if (sh("ime list -a -s").contains(GoChu.BO_GO)) break; Thread.sleep(500) }
        var cu = sh("settings get secure default_input_method").trim()
        // Bộ gõ đang dùng là của TiviKit (lần trước bị ngắt giữa chừng): trả về bàn phím khác đang bật.
        if (!GoChu.laBoGoCu(cu)) cu = sh("ime list -s").lines().map { it.trim() }.firstOrNull { GoChu.laBoGoCu(it) } ?: ""
        return GoChu.ketQua(sh(GoChu.lenh(chu, cu)))
    }

    /** Gỡ bộ gõ (Khôi phục tivi); đang là bàn phím của tivi thì Android tự chọn bàn phím khác. */
    fun goBoGo() { if (boGoDaCai()) sh("pm uninstall ${GoChu.GOI}") }

    // ---------- TiviKit Launcher (app vn.tivikit.launcher, 27/10/2026) ----------

    fun tklDaCai(): Boolean = coGoi(TiviKitLauncher.GOI)

    /** TiviKit Launcher trên tivi: bản, bí danh Home đã bật chưa, có khác bản đi kèm [apk] không. */
    fun docTkl(apk: ByteArray): TiviKitLauncher.TrangThai {
        if (!tklDaCai()) return TiviKitLauncher.TrangThai(null)
        val j = TiviKitLauncher.docKetQua(sh(TiviKitLauncher.LENH_DOC))
        val coSan = runCatching {
            sh("sha256sum \$(pm path ${TiviKitLauncher.GOI} | head -n 1 | sed 's/^package://') 2>/dev/null").trim().substringBefore(' ')
        }.getOrDefault("")
        return TiviKitLauncher.trangThai(j, coSan != vn.tivikit.dulieu.sha256(apk))
    }

    /** Gửi cấu hình cho TiviKit Launcher (TiviKitLauncher.cauHinh). */
    fun datCauHinhTkl(cauHinh: String) { sh(TiviKitLauncher.lenhDat(cauHinh)) }

    /**
     * Ảnh nền của TiviKit Launcher; [anh] null = về ảnh mặc định đi kèm launcher. Gửi bằng content write như ảnh nền Home
     * sạch rồi đọc lại cỡ file launcher đang giữ (nó kiểm ảnh xong mới thay, có thể chậm một chút): false nếu không nhận.
     */
    fun datNenTkl(anh: ByteArray?): Boolean {
        if (anh == null) {
            sh("content delete --uri ${TiviKitLauncher.URI_NEN} 2>/dev/null; true")
            return true
        }
        val tam = "/data/local/tmp/tivikit-launcher-nen.jpg"
        adb.ghiFile(tam, anh)
        sh("content write --uri ${TiviKitLauncher.URI_NEN} < $tam 2>/dev/null; rm -f $tam")
        repeat(10) {
            if (TiviKitLauncher.docKetQua(sh(TiviKitLauncher.LENH_DOC))?.optLong("nen") == anh.size.toLong()) return true
            Thread.sleep(300)
        }
        return false
    }

    /**
     * Tên kênh và logo các ô Kênh nhanh trên TiviKit Launcher, như app_labels / app_banners của Home sạch: [o] gói → (tên
     * kênh, logo JPEG; logo null = ô không logo), tên null = bỏ ô (xoá tên và logo riêng). null = chưa cài launcher; false =
     * launcher bản cũ chưa biết ảnh / tên riêng (Cập nhật ở Quản lý launcher); true = đã gửi.
     */
    fun datOKenhTkl(o: Map<String, Pair<String?, ByteArray?>>): Boolean? {
        if (o.isEmpty() || !tklDaCai()) return null
        val lenh = StringBuilder()
        for ((goi, v) in o) {
            val uri = TiviKitLauncher.uriAnhApp(goi)
            val anh = v.second?.takeIf { v.first != null }
            if (anh == null) { lenh.append("content delete --uri $uri 2>/dev/null; "); continue }
            val tam = "/data/local/tmp/tivikit-launcher-$goi.jpg"
            adb.ghiFile(tam, anh)
            lenh.append("content write --uri $uri < $tam 2>/dev/null; rm -f $tam; ")
        }
        sh(lenh.append(TiviKitLauncher.lenhDat(TiviKitLauncher.tenApp(o.mapValues { it.value.first }))).toString())
        return TiviKitLauncher.nhanAnhApp(TiviKitLauncher.docKetQua(sh(TiviKitLauncher.LENH_DOC)))
    }

    /**
     * Cài / cập nhật TiviKit Launcher (chỉ khi khác bản đi kèm), cấp quyền (TiviKitLauncher.LENH_CAP_QUYEN: tên Wi-Fi, thẻ
     * Đang phát, app mở gần đây), biên dịch sẵn phần mã hay dùng sau mỗi lần cài (TiviKitLauncher.LENH_TOI_UU), rồi gửi
     * [cauHinh] (TiviKitLauncher.cauHinh: tên lời chào, nơi xem thời tiết, ngôn ngữ, dock khi mới cài). Launcher đang hiện
     * mà cài đè làm tivi về Home của hãng (Google TV: Home sạch mở nó đè lên Home của hãng; Skyworth 27/10/2026) thì mở lại.
     */
    fun caiTkl(apk: ByteArray, cauHinh: String, bao: (String) -> Unit) {
        var vuaCai = false
        var dangHien = false
        caiAppPhu(TiviKitLauncher.GOI, apk) {
            vuaCai = true
            dangHien = goiDangHien() == TiviKitLauncher.GOI
            bao(t("Cài TiviKit Launcher lên tivi…", "Installing TiviKit Launcher on the TV…"))
        }
        sh(TiviKitLauncher.LENH_CAP_QUYEN)
        if (vuaCai) {
            bao(t("Tối ưu cho tivi yếu…", "Optimising for slow TVs…"))
            runCatching { sh(TiviKitLauncher.LENH_TOI_UU) }
        }
        sh(TiviKitLauncher.lenhDat(cauHinh))
        if (vuaCai && dangHien && goiDangHien() != TiviKitLauncher.GOI) moThu(TiviKitLauncher.HOME)?.let { moThu(TiviKitLauncher.MAN) }
    }

    /**
     * Bật bí danh Home của TiviKit Launcher (launcher tự bật, xem TiviKitLauncher.HOME) để Đặt làm Home được, rồi chọn lại
     * ngay Home mặc định [giu] ("gói/lớp"): thêm một màn HOME cùng mức ưu tiên làm Android bỏ Home đã chọn và hỏi chọn ở lần
     * bấm Home sau, nếu người dùng thôi giữa chừng. false nếu launcher không trả lời.
     */
    fun batHomeTkl(giu: String): Boolean {
        val ok = sh(TiviKitLauncher.LENH_BAT_HOME).contains("ok=1")
        datLaiHome(giu)
        return ok
    }

    /** Gỡ TiviKit Launcher (không phải Home đang dùng), rồi chọn lại Home mặc định [giu]: bớt một màn HOME cũng làm Android bỏ Home đã chọn. */
    fun goTkl(giu: String): String {
        val out = sh("pm uninstall ${TiviKitLauncher.GOI}")
        if (!giu.startsWith("${TiviKitLauncher.GOI}/")) datLaiHome(giu)
        return out
    }

    private fun datLaiHome(giu: String) {
        if (giu.contains('/') && !giu.startsWith("android/") && TEN_GOI.matches(giu.substringBefore('/')) && !giu.contains('\''))
            sh("cmd package set-home-activity --user 0 '$giu'")
    }

    // ---------- App phụ bản cũ (trước khi đổi tên gói sang vn.tivikit, 25/10/2026) ----------

    /** App phụ bản cũ (GoiCu) đang có trên tivi. */
    fun appPhuCu(): List<String> { val co = goiDaCai(); return GoiCu.APP_PHU.filter { it in co } }

    /**
     * Kết quả chuyển: [kenh] = tên các ô kênh đã chuyển, [henGio] = đã chuyển lịch Hẹn giờ, [khoaCho] = Khoá trẻ em bản
     * cũ còn chờ đặt PIN, [loi] = việc chưa làm được (bản cũ của nó để nguyên, vẫn chạy; lần nối sau thử lại).
     */
    data class KetQuaChuyen(val kenh: List<String>, val henGio: Boolean, val khoaCho: Boolean, val loi: List<String>)

    /**
     * Chuyển app phụ bản cũ sang gói mới: mỗi ô Kênh nhanh cũ → cài ô mới cùng kênh, config.json Home sạch đổi tên gói
     * theo (tên ô, dock, nút remote đã gán); lịch Hẹn giờ chép sang app Hẹn giờ mới (việc mở ô kênh đổi theo). Bản cũ chỉ
     * gỡ khi bản mới đã xong. Khoá trẻ em không chuyển ở đây (cần PIN, xem caiKhoa). [apk] đọc file APK đi kèm theo tên.
     */
    fun chuyenAppPhuCu(apk: (String) -> ByteArray, bao: (String) -> Unit): KetQuaChuyen {
        val co = goiDaCai()
        val loi = mutableListOf<String>()
        val doi = mutableMapOf<String, String>()
        val tenKenh = mutableListOf<String>()
        // Ô mới đã có (lần chuyển trước dở dang): cùng kênh thì dùng lại, khác kênh thì đưa ô cũ sang ô trống.
        val daDung = (1..KenhNhanh.SO_O).filter { KenhNhanh.goiCua(it) in co }.toMutableSet()
        for (o in 1..KenhNhanh.SO_O) {
            val cu = GoiCu.kenh(o)
            if (cu !in co) continue
            val k = KenhNhanh.tuJson(docQua(cu))
            if (k == null) { loi += t("ô kênh cũ $o (không đọc được kênh)", "old channel tile $o (channel unreadable)"); continue }
            val s = if (o !in daDung || KenhNhanh.tuJson(docQua(KenhNhanh.goiCua(o)))?.so == k.so) o
                else (1..KenhNhanh.SO_O).firstOrNull { it !in daDung }
            if (s == null) { loi += t("ô ${k.ten} (đã đủ ${KenhNhanh.SO_O} ô mới)", "the ${k.ten} tile (all ${KenhNhanh.SO_O} new tiles used)"); continue }
            runCatching {
                bao(t("Chuyển ô kênh ${k.ten} sang TiviKit mới…", "Moving the ${k.ten} tile to the new TiviKit…"))
                caiKenh(s, apk(KenhNhanh.apkCua(s)), KenhNhanh.json(k)) {}
            }.onSuccess { doi[cu] = KenhNhanh.goiCua(s); daDung += s; tenKenh += k.ten }
                .onFailure { loi += "${k.ten}: ${it.message}" }
        }
        if (doi.isNotEmpty()) {
            // Có config.json mà không đọc được thì giữ ô cũ (nút / dock vẫn trỏ vào nó, vẫn chạy); lần nối sau làm tiếp.
            val j = runCatching { cauHinhDeSua() }.getOrNull()
            if (j == null) loi += t("Home sạch (không đọc được cấu hình, giữ ô kênh cũ)", "the clean Home (settings unreadable, old tiles kept)")
            else {
                val doiCauHinh = GoiCu.doiTrongCauHinh(j, doi)
                if (doiCauHinh) {
                    bao(t("Cập nhật Home sạch…", "Updating the clean Home…"))
                    ghiFileTheme("config.json", j.toString().toByteArray())
                }
                doi.keys.forEach { runCatching { sh("pm uninstall $it") } }
                if (doiCauHinh) {
                    // Chạy lúc vừa nối tivi, có thể đang có người xem: chỉ đưa Home lên khi Home đang ở trước mặt. Không thì tắt
                    // Home sạch (bấm Home là chạy lại với cấu hình mới) và khởi động lại dịch vụ phím cho nút đã gán trỏ sang ô mới.
                    val dangHien = runCatching { goiDangHien() }.getOrDefault("")
                    if (dangHien.isEmpty() || dangHien == pkgHome) sh(KHOI_DONG_LAI_HOME)
                    else sh(TAT_HAN_HOME)
                }
            }
        }
        var henGio = false
        if (GoiCu.HEN_GIO in co) runCatching {
            val j = docQua(GoiCu.HEN_GIO) ?: error(t("không đọc được lịch", "schedule unreadable"))
            val lich = GoiCu.doiLich(HenGio.trangThai(j).lich, doi)
            if (lich.isNotEmpty()) {
                bao(t("Chuyển lịch Hẹn giờ sang TiviKit mới…", "Moving the Timer schedule to the new TiviKit…"))
                // App Hẹn giờ mới đã có lịch (lần chuyển trước dở dang): gộp, bỏ việc trùng.
                val dangCo = docHenGio()?.let { HenGio.trangThai(it).lich }.orEmpty()
                caiHenGio(apk("hengio.apk"), HenGio.json(HenGio.sapXep((dangCo + lich).distinct()).take(HenGio.TOI_DA))) {}
                henGio = true
            }
            boTroNang(GoiCu.DICH_VU_HEN_GIO)
            sh("pm uninstall ${GoiCu.HEN_GIO}")
        }.onFailure { loi += t("Hẹn giờ: ${it.message}", "Timer: ${it.message}") }
        // Khoá trẻ em: có cả hai bản (đặt PIN ở bản mới xong mà gỡ bản cũ dở) thì gỡ bản cũ; chỉ có bản cũ thì chờ PIN.
        val coKhoaMoi = coGoi(PKG_KHOA)
        if (GoiCu.KHOA in co && coKhoaMoi) runCatching { tatKhoaGoi(GoiCu.KHOA, go = true) }
        return KetQuaChuyen(tenKenh, henGio, GoiCu.KHOA in co && !coKhoaMoi, loi)
    }

    /** Gỡ app phụ bản cũ còn sót (Khôi phục tivi), bỏ dịch vụ trợ năng của chúng trước. */
    private fun goAppPhuCu(bao: (String) -> Unit) {
        val con = appPhuCu()
        if (con.isEmpty()) return
        bao(t("Gỡ app phụ bản cũ…", "Removing the old helper apps…"))
        boTroNang(GoiCu.DICH_VU_KHOA); boTroNang(GoiCu.DICH_VU_HEN_GIO)
        con.forEach { runCatching { sh("pm uninstall $it") } }
    }

    // ---------- Đóng ADB ----------

    /**
     * Hẹn TIVI tự tắt ADB sau [giay] giây (24/10/2026): hẹn giờ trong app điện thoại không chạy được vì Android 14 đóng
     * băng app nằm nền, nên để tivi tự đếm. Huỷ bằng huyTatAdbTrenTv (xoá file hẹn).
     */
    fun henTatAdbTrenTv(giay: Int = 60) {
        sh("echo 1 > $HEN_TAT_ADB; (setsid sh -c 'sleep $giay; [ -f $HEN_TAT_ADB ] && { rm -f $HEN_TAT_ADB; settings put global adb_wifi_enabled 0; settings put global adb_enabled 0; }' </dev/null >/dev/null 2>&1 &); sleep 1")
    }

    fun huyTatAdbTrenTv() { sh("rm -f $HEN_TAT_ADB") }

    /** Tắt ADB qua mạng trên tivi. Lệnh cuối làm rớt kết nối này, nên lỗi ở đây là bình thường. */
    fun tatAdb() {
        runCatching { sh("settings put global adb_wifi_enabled 0") }
        runCatching { sh("settings put global adb_enabled 0") }
    }

    // ---------- Cài app người dùng chọn (25/10/2026) ----------

    /** ABI tivi chạy được, ưu tiên trước (ro.product.cpu.abilist), để bỏ phần APK cho chip khác. */
    fun abiTv(): List<String> =
        sh("getprop ro.product.cpu.abilist").trim().split(',').map { it.trim() }.filter { it.isNotEmpty() }
            .ifEmpty { listOf(sh("getprop ro.product.cpu.abi").trim()).filter { it.isNotEmpty() } }

    fun goiDaCai(): Set<String> = sh("pm list packages").lines().mapNotNull { l ->
        l.trim().removePrefix("package:").takeIf { TEN_GOI.matches(it) }
    }.toSet()

    private fun coGoi(pkg: String, co: String = "") = sh("pm list packages $co $pkg").lines().any { it.trim() == "package:$pkg" }

    /** Bản [pkg] đang có trên tivi ("1.2.3 (45)"), null nếu chưa cài. */
    fun banTrenTv(pkg: String): String? {
        if (!TEN_GOI.matches(pkg) || !coGoi(pkg)) return null
        val d = sh("dumpsys package $pkg | grep -m 2 -E 'versionCode=|versionName='")
        val ten = Regex("""versionName=(\S+)""").find(d)?.groupValues?.get(1)
        val so = Regex("""versionCode=(\d+)""").find(d)?.groupValues?.get(1)
        return listOfNotNull(ten, so?.let { "($it)" }).joinToString(" ").ifBlank { "?" }
    }

    fun laAppHeThong(pkg: String) = TEN_GOI.matches(pkg) && coGoi(pkg, "-s")
    fun dangTat(pkg: String) = TEN_GOI.matches(pkg) && coGoi(pkg, "-d")

    /**
     * Cài app: một phần như `adb install` (`cmd package install -S`), nhiều phần (gói chia nhỏ) như `adb install-multiple`
     * (install-create → install-write từng phần → install-commit; lỗi giữa chừng thì install-abandon). [mo] mở luồng
     * đọc từng phần, [tienDo] nhận số byte đã gửi trên tổng. Trả về chữ máy in ra ("Success" / "Failure [...]").
     */
    fun caiApp(phan: List<PhanApk>, mo: (PhanApk) -> java.io.InputStream, tienDo: (Long) -> Unit): String {
        if (phan.size == 1) return mo(phan[0]).use { adb.doVaoLenh("cmd package install -r -S ${phan[0].co}", it, phan[0].co, tienDo = tienDo) }
        val tao = sh("cmd package install-create -r -S ${phan.sumOf { it.co }}")
        val phien = Regex("""\[(\d+)]""").find(tao)?.groupValues?.get(1) ?: return tao.trim().ifEmpty { "Failure [install-create]" }
        try {
            var daGui = 0L
            phan.forEachIndexed { i, p ->
                val ten = "${i}_" + p.muc.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_").ifEmpty { "base.apk" }
                val out = mo(p).use { adb.doVaoLenh("cmd package install-write -S ${p.co} $phien $ten -", it, p.co) { n -> tienDo(daGui + n) } }
                if (!out.contains("Success")) { runCatching { sh("cmd package install-abandon $phien") }; return out }
                daGui += p.co
            }
            return adb.shell("cmd package install-commit $phien", 300).replace("\r", "")
        } catch (e: Throwable) {
            runCatching { sh("cmd package install-abandon $phien") }
            throw e
        }
    }

    /** Gỡ app người dùng (không bao giờ đụng app hệ thống — pm uninstall trên đó chỉ gỡ bản cập nhật, dễ hiểu nhầm). */
    fun goApp(pkg: String): String {
        require(TEN_GOI.matches(pkg) && !laAppHeThong(pkg)) { "goApp $pkg" }
        return sh("pm uninstall $pkg")
    }

    /** Mở app trên tivi bằng màn khởi động của nó (Android TV trước, rồi kiểu điện thoại); false nếu app không có. */
    fun moApp(pkg: String): Boolean {
        if (!TEN_GOI.matches(pkg)) return false
        for (cat in listOf("android.intent.category.LEANBACK_LAUNCHER", "android.intent.category.LAUNCHER")) {
            val c = sh("cmd package resolve-activity --brief -a android.intent.action.MAIN -c $cat $pkg | tail -n 1").trim()
            if (Regex("""^[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+$""").matches(c)) return !sh("am start -n '$c'").contains("Error")
        }
        return false
    }

    /**
     * File .apk có sẵn trên tivi / USB cắm vào tivi, kèm cỡ. Tối đa 100 file. Tìm từ /storage/emulated/0 chứ không từ
     * /sdcard: /sdcard là liên kết, find không đi vào liên kết ở điểm bắt đầu.
     */
    fun timApkTrenTv(): List<Pair<String, Long>> = sh(
        "for d in /storage/emulated/0 /storage/*-*; do [ -d \"\$d\" ] && find \"\$d\" -maxdepth 3 -type f -iname '*.apk' 2>/dev/null; done | " +
            "sort -u | head -n 100 | while read -r f; do echo \"\$(wc -c < \"\$f\") \$f\"; done"
    ).lines().mapNotNull { l ->
        val co = l.trim().substringBefore(' ').toLongOrNull() ?: return@mapNotNull null
        val p = l.trim().substringAfter(' ')
        if (p.startsWith("/") && !p.contains('\'')) p to co else null
    }

    /** Cài file .apk nằm sẵn trên tivi: pm đọc thẳng file, không gửi gì qua Wi-Fi. */
    fun caiApkTrenTv(duongDan: String): String {
        require(!duongDan.contains('\'')) { "duongDan" }
        return adb.shell("pm install -r '$duongDan'", 600).replace("\r", "")
    }

    /** Rớt kết nối lúc dọn mà nối lại không được: [daTat] gói đã tắt (đều có trong sao lưu), đang làm dở [goi]. */
    class MatKetNoiTvException(val daTat: Int, val goi: String) : Exception("mat-ket-noi")
    /** Tivi tự khởi động lại ngay sau khi tắt [goi] (gói tắt xong gần nhất): dừng dọn, [goi] là nghi phạm. */
    class TiviKhoiDongLaiException(val goi: String, val daTat: Int) : Exception("khoi-dong-lai")
    /** App khoá trên tivi đòi phiên nhập mã (không kèm, hay đã hết hạn): nhập lại mã Khoá trẻ em. */
    class CanMaException : Exception("can-ma")

    companion object {
        /**
         * App tạo ra mạng Wi-Fi ĐANG kết nối (cname trong khối cấu hình của SSID đó ở `dumpsys wifi`). null = không đang
         * dùng Wi-Fi hoặc không đọc được SSID; "" = thấy SSID nhưng không thấy khối cấu hình của nó.
         */
        fun chuWifiDangDung(dump: String): String? {
            val ssid = Regex("""mWifiInfo SSID: "([^"]*)"""").find(dump)?.groupValues?.get(1) ?: return null
            val dau = dump.indexOf("Configured networks Begin"); val cuoi = dump.indexOf("Configured networks End")
            if (dau < 0 || cuoi < dau) return ""
            val khoi = dump.substring(dau, cuoi).split(Regex("""\n(?=[\s*]*ID: \d+ )"""))
            // Cùng một tên Wi-Fi có thể lưu thành nhiều mạng (khác kiểu bảo mật): không biết đang nối mạng nào thì trả ""
            // để xét mọi mạng đã lưu (lỗ hổng @SaviorOneZero tìm ra ở Gathin23/cleanyourtv#1091, 23/10/2026).
            val trung = khoi.filter { it.contains("SSID: \"$ssid\"") }
            val cua = trung.singleOrNull() ?: return ""
            return Regex("""cname=([^ \n]+)""").find(cua)?.groupValues?.get(1) ?: ""
        }

        /**
         * Theo `pm list features`: Android TV khai android.software.leanback hoặc android.hardware.type.television, điện
         * thoại / máy tính bảng không có cả hai (CleanTV 2.10.12: một người dọn nhầm chính điện thoại, máy không khởi động
         * được). Không đọc được danh sách thì coi như tivi để không chặn nhầm.
         */
        fun laTiviTheoFeatures(f: String): Boolean {
            if (!f.contains("feature:")) return true
            return f.contains("feature:android.software.leanback") || f.contains("feature:android.hardware.type.television")
        }

        /** Lỗi kiểu kết nối ADB đã đứt (libadb: "connect() must be called first", luồng đóng, mạng reset). */
        fun laMatKetNoi(e: Throwable): Boolean {
            val m = listOfNotNull(e.message, e.cause?.message).joinToString(" ")
            return Regex("must be called first|not connected|Stream closed|closed|Broken pipe|EPIPE|reset|ECONNRESET|Socket", RegexOption.IGNORE_CASE).containsMatchIn(m) ||
                e is java.io.EOFException || e is java.net.SocketException
        }

        /** Dịch vụ hệ thống không trả lời: tivi đang tắt máy / khởi động lại. */
        private val DICH_VU_CHET = Regex("Failure calling service|Can't find service|DeadObjectException|DeadSystemException")

        val TEN_GOI = Regex("""^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$""")
        /** Tên máy / tên gói gợi ý máy chiếu (21/10/2026, Formovie Nemo). */
        val TU_MAY_CHIEU = Regex("projector|formovie|fengmi|xgimi|jmgo|dangbei|nebula|wanbo|byintek|keystone|autofocus|tixing|duijiao", RegexOption.IGNORE_CASE)
        const val PKG_HOME = "com.gathin.tvhome"
        const val PKG_HOME_XIAOMI = "com.ts.launcher"
        /** App Khoá trẻ em (module khoa/). */
        const val PKG_KHOA = "vn.tivikit.khoa"
        /** Phiên app khoá trả sau khi nhập đúng mã (khoa/XacThuc.moPhien): 16 byte viết hex. */
        private val PHIEN = Regex("^[0-9a-f]{32}$")
        /** Bản sao lưu trên tivi: thư mục của shell, còn sau khi gỡ app / Home sạch, chỉ mất khi khôi phục cài đặt gốc. */
        const val TEP_SAO_LUU_TV = "/data/local/tmp/cleantv-sao-luu.json"
        const val GAC = "/data/local/tmp/cleantv-gac"
        const val HEN_TAT_ADB = "/data/local/tmp/cleantv-tat-adb"
        const val GAC_GIAY = 180
        /** Kịch bản canh gác chạy trên tivi: `sh cleantv-gac.sh <token>`. Token đổi / mất thì tự thoát. */
        val KICH_BAN_GAC = """
            |# CleanTV: canh gác Home. Hết hạn mà điện thoại không gia hạn thì bật lại Home vừa tắt.
            |G=$GAC
            |while :; do
            |  sleep 5
            |  [ "${'$'}(cat ${'$'}G-token 2>/dev/null)" = "${'$'}1" ] || exit 0
            |  [ "${'$'}(date +%s)" -ge "${'$'}(cat ${'$'}G-han 2>/dev/null || echo 0)" ] && break
            |done
            |for p in ${'$'}(cat ${'$'}G-list); do pm enable --user 0 ${'$'}p; done
            |h=${'$'}(head -n 1 ${'$'}G-list)
            |[ -n "${'$'}h" ] && cmd package set-home-activity --user 0 ${'$'}(cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME ${'$'}h | tail -n 1)
            |input keyevent KEYCODE_HOME
            |echo "${'$'}(date) ${'$'}1 ${'$'}(cat ${'$'}G-list | tr '\n' ' ')" >> ${'$'}G.log
            |rm -f ${'$'}G-token ${'$'}G-list ${'$'}G-han
            |""".trimMargin()
        /** Namespace Java của launcher: không đổi theo tên gói. */
        const val NS_HOME = "com.gathin.tvhome"
        val HOME_SACH = setOf(PKG_HOME, PKG_HOME_XIAOMI)

        /**
         * App phụ phải chạy nền (27/10/2026): Home sạch (phím Home, mở đè), Khoá trẻ em, Hẹn giờ, TiviKit Launcher (thẻ Đang
         * phát, thời tiết). Cách tv-remote giữ app của nó trên box RAM thấp (họ đo trên box FPT Play, Android 14): thêm vào danh
         * sách bỏ qua tối ưu pin (Doze), xếp nhóm "đang dùng" (App Standby), cho chạy nền.
         */
        val GIU_SONG: Set<String> get() = HOME_SACH + setOf(PKG_KHOA, vn.tivikit.dulieu.HenGio.GOI, TiviKitLauncher.GOI, CastSmartTube.GOI)

        /**
         * Lệnh giữ [goi] chạy nền (xem GIU_SONG). Mỗi lệnh im lặng khi lỗi (Android 8 chưa có set-standby-bucket, gói vừa gỡ):
         * một lệnh hỏng không chặn lệnh sau. Nhóm "đang dùng" tự hạ dần nếu app ít được mở nên chạy lại mỗi lần nối tivi.
         */
        fun lenhGiuSong(goi: Collection<String>): String = goi.filter { TEN_GOI.matches(it) }.joinToString("") { p ->
            "dumpsys deviceidle whitelist +$p >/dev/null 2>&1; am set-standby-bucket $p active >/dev/null 2>&1; " +
                "cmd appops set $p RUN_ANY_IN_BACKGROUND allow >/dev/null 2>&1; "
        } + "true"
        const val SETUP = "com.google.android.tungsten.setupwraith"
        const val HOME_QUERY = "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"
        val HOME_GOC_BIET = listOf(
            "com.google.android.tvlauncher", "com.google.android.apps.tv.launcherx",
            "com.google.android.leanbacklauncher", "com.mitv.tvhome",
        )
        /** Nhận Home nhưng không phải màn Home dùng được. */
        private val KHONG_PHAI_HOME = setOf(SETUP, "com.android.tv.settings", "com.android.settings", "com.android.provision")
        /**
         * App kiểm tra xuất xưởng cũng khai báo HOME (Xiaomi Mi TV 4A: com.duokan.factorytest/.FactoryLauncher) — không bao
         * giờ là Home của hãng, không tắt (theo upstream 0357546, 22/10/2026).
         */
        private val APP_XUAT_XUONG = Regex("factorytest|factory_test|factorymode", RegexOption.IGNORE_CASE)
    }
}
