package vn.tivikit.dulieu

import java.time.Instant
import java.time.ZoneId

/**
 * Gửi ngược cho CleanYourTV (26/10/2026): TiviKit lấy mọi danh sách tivi từ kho đó, nay người dùng gửi lại được danh
 * sách của tivi mình. Lấy các gói TiviKit đã tắt trên một tivi (BanSaoLuu.daTatBoiApp), viết đúng định dạng
 * examples/<hãng>-<mẫu>/ của họ — debloat.txt và tv.json (CONTRIBUTING.md mục 1, docs/workflows/tv-list.md, kiểm tra
 * bằng tests/test_examples.py và tools/gen_tables.py) — và link trang tạo issue "My TV"
 * (.github/ISSUE_TEMPLATE/tv-list.yml) điền sẵn các ô theo id. App không gửi gì: người dùng xem lại rồi tự bấm gửi trên
 * GitHub. Chữ xuất ra viết tiếng Anh như kho đó; không có IP, số serial hay app người dùng tự cài.
 */
object GopNguoc {
    const val KHO = "Gathin23/cleanyourtv"
    /** GitHub trả 414 khi link quá dài: quá ngưỡng này thì ô danh sách trên trang chỉ nhắc dán (app đã chép sẵn). */
    const val LINK_TOI_DA = 6000
    const val DAN_VAO = "# TiviKit copied the list to the clipboard (too long for the link): paste it here."

    /** PACKAGE trong tests/test_examples.py của CleanYourTV. */
    private val GOI = Regex("""^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$""")
    private val GOI_ANDROID_TV = setOf("com.android.tv.settings", "com.google.android.tvlauncher",
        "com.google.android.leanbacklauncher", "com.google.android.tv.remote.service")
    private const val SETUP_GOOGLE_TV = "com.google.android.tungsten.setupwraith"

    class BanXuat(
        /** Thư mục gợi ý: examples/<thuMuc>/. */
        val thuMuc: String,
        /** Tên tivi: ô "tv" của tv.json, ô "TV model" của issue. */
        val tv: String,
        val os: String,
        /** Bản firmware (ro.build.display.id, như tools/tv.py --probe in ra). */
        val banDung: String,
        val tieuDe: String,
        val debloat: String,
        val tvJson: String,
        /** Số gói trong debloat.txt. */
        val soGoi: Int,
        /** Số ngày từ lúc sao lưu (ngay trước lần tắt đầu tiên) tới nay. */
        val soNgay: Int,
        /** TiviKit đã tắt mà tivi nay bật lại (firmware bật lúc khởi động, hay bật tay): ghi chú cuối file, không tắt. */
        val daBatLai: List<String>,
        /** App người dùng tự cài, không có ở danh sách nào: không đưa ra ngoài. */
        val boTuCai: List<String>,
        /** Mẫu CleanYourTV khớp với tivi này (null = kho chưa có). */
        val mau: MauTv?,
        /** Đã đối chiếu với tivi (đang nối) hay chỉ theo bản sao lưu. */
        val daKiemTra: Boolean,
    )

    /**
     * Soạn danh sách cho tivi của [sl]. [getprop]: đầu ra getprop (bản sao lưu lấy từ tivi không có, khi đó lấy lúc nối);
     * [goiCai]: gói đã cài, để biết Google TV / Android TV; [conTat], [tuCai]: gói đang tắt / app tự cài (pm list
     * packages -3) đọc trên tivi lúc này, null = không nối tivi đó nên chưa đối chiếu được.
     */
    fun tao(sl: BanSaoLuu, getprop: String, goiCai: Collection<String>, du: DuLieu, mau: MauTv?, conTat: Set<String>?,
            tuCai: Set<String>?, banApp: String, bayGio: Long): BanXuat {
        val tv = ThongTinTv.tuGetprop(getprop)
        val gp = ThongTinTv.bangGetprop(getprop)
        // ro.build.display.id như tools/tv.py --probe; hộp tivi hay ghi tên tiếp thị ở đó thì kèm mã build thật.
        val maBuild = motDong(gp["ro.build.id"].orEmpty())
        val banDung = motDong(gp["ro.build.display.id"].orEmpty()).let { hien ->
            when { hien.isBlank() -> maBuild; maBuild.isBlank() || maBuild in hien -> hien; else -> "$hien ($maBuild)" }
        }
        val hang = tenHang(tv.manufacturer.ifBlank { tv.brand }.ifBlank { sl.ten.substringBefore(' ') })
        val model = motDong(tv.model)
        val tenMay = when {
            model.isBlank() -> motDong(sl.ten).ifBlank { hang }
            hang.isBlank() || model.startsWith(hang, ignoreCase = true) -> model
            else -> "$hang $model"
        }.ifBlank { "Android TV" }
        val device = motDong(tv.device).takeIf { it.isNotBlank() && !it.equals(model, ignoreCase = true) }
        val os = heDieuHanh(tv, goiCai)
        val thuMuc = thuMuc(hang, model.ifBlank { tenMay })

        // Gói lõi Android (CleanYourTV từ chối cả danh sách có chúng) thì TiviKit không bao giờ tắt; lọc lại cho chắc.
        val goi = sl.daTatBoiApp.filter { GOI.matches(it) && !KeHoach.laGoiLoiAndroid(it) && !KeHoach.laGoiLoi(it) }.distinct()
        val trongMau = mau?.goi?.map { it.pkg }?.toSet().orEmpty()
        val boTuCai = if (tuCai == null) emptyList()
            else goi.filter { it in tuCai && it !in trongMau && (du.thongKe[it]?.soMauTat ?: 0) == 0 }.sorted()
        val daBatLai = if (conTat == null) emptyList() else goi.filter { it !in conTat && it !in boTuCai }.sorted()
        val con = goi.filter { it !in boTuCai && it !in daBatLai }.toSet()

        // Nhóm như danh sách của họ: nhóm của mẫu khớp trước (giữ tên nhóm và thứ tự), rồi phần TiviKit tự xếp.
        val nhom = LinkedHashMap<String, MutableList<String>>()
        fun them(ten: String, p: String, ghiChu: String) { nhom.getOrPut(ten) { mutableListOf() } += dong(p, ghiChu) }
        val daXep = HashSet<String>()
        if (mau != null) for (g in mau.goi) if (g.pkg in con && daXep.add(g.pkg)) them(motDong(g.nhom).ifBlank { "From examples/${mau.id}" }, g.pkg, g.ghiChu)
        // Home gốc TiviKit tắt khi đặt Home sạch; trên Google TV setupwraith phải tắt cùng (không thì nó nhận Home).
        val home = listOfNotNull(sl.homeGocDaTat.takeIf { it.isNotBlank() }, SETUP_GOOGLE_TV.takeIf { sl.launcherDaCai })
            .filter { it in con && it !in daXep }
        val conLai = con.filter { it !in daXep && it !in home }.sorted()
        val (giongNoi, r1) = conLai.partition(KeHoach::laGoiGiongNoi)
        val (cast, r2) = r1.partition(KeHoach::laGoiGoogleHome)
        val (dsKhac, chuaCo) = r2.partition { (du.thongKe[it]?.soMauTat ?: 0) > 0 }
        giongNoi.forEach { them("Voice search and Google Assistant, off by choice (the remote's mic button stops working)", it, du.ghiChu[it].orEmpty()) }
        cast.forEach { them("Casting and Google Home, off by choice (phones can no longer find or cast to the TV)", it, du.ghiChu[it].orEmpty()) }
        dsKhac.forEach { them(if (mau != null) "Not in examples/${mau.id}, disabled on other lists" else "Disabled on other CleanYourTV lists too", it, du.ghiChu[it].orEmpty()) }
        chuaCo.forEach { them("On no CleanYourTV list yet", it, "") }
        home.forEach { them("Stock launcher, replaced by the CleanYourTV launcher (TiviKit build)", it, if (it == SETUP_GOOGLE_TV) "takes Home while enabled" else "") }

        val ngayTat = ngay(sl.luc)
        val homNay = ngay(bayGio)
        // Chưa có Home mặc định thì Android trả hộp chọn (android/…ResolverActivity): khi đó không ghi dòng Home gốc.
        val homeTruoc = sl.homeTruoc.substringBefore('/').trim().takeIf { it != "android" }.orEmpty()
        val debloat = buildString {
            append("# $tenMay")
            device?.let { append(" (codename $it)") }
            append(", $os")
            if (banDung.isNotBlank()) append(", firmware $banDung")
            append('\n')
            append("# Exported by TiviKit $banApp (github.com/Circling5714/tivikit), which disables with pm disable-user over\n")
            append("# wireless ADB: the packages it disabled on this TV since $ngayTat")
            append(if (conTat != null) ", still disabled on $homNay.\n" else "; not re-checked on the TV since.\n")
            if (homeTruoc.isNotBlank()) append("# Stock launcher: $homeTruoc" +
                (if (sl.launcherDaCai) ", replaced as Home by the CleanYourTV launcher.\n" else ", kept as Home.\n"))
            mau?.let { append("# TiviKit matched this TV to examples/${it.id}/ by its ro.product names; that list's groups come first.\n") }
            append("#\n")
            append("# Apply:   TV_HOST=<tv ip> tools/disable.sh examples/$thuMuc/debloat.txt\n")
            append("# Undo:    TV_HOST=<tv ip> tools/restore.sh examples/$thuMuc/debloat.txt\n")
            append("# Everything is DISABLED, not uninstalled. Comment out any line you want to keep.\n")
            for ((ten, dong) in nhom) {
                append("\n# $ten\n")
                dong.forEach { append(it).append('\n') }
            }
            if (daBatLai.isNotEmpty()) {
                append("\n# Disabled by TiviKit, then found enabled again (the firmware at boot, or by hand), so left out:\n")
                daBatLai.forEach { append("# $it\n") }
            }
        }

        val soGoi = con.size
        val ghiChu = listOfNotNull(
            "$soGoi packages",
            "stock launcher replaced by the CleanYourTV launcher".takeIf { sl.launcherDaCai },
            "voice search off by choice".takeIf { giongNoi.isNotEmpty() },
            "casting off by choice".takeIf { cast.isNotEmpty() },
            "${daBatLai.size} came back enabled".takeIf { daBatLai.isNotEmpty() },
        ).joinToString("; ")
        val tenTv = device?.let { "$tenMay ($it)" } ?: tenMay
        val tvJson = json("tv" to tenTv, "os" to os, "brand" to hang.ifBlank { tenMay.substringBefore(' ') }, "notes" to ghiChu)
        return BanXuat(
            thuMuc = thuMuc, tv = tenTv, os = os, banDung = banDung, tieuDe = "[TV] $tenMay ($os)",
            debloat = debloat, tvJson = tvJson, soGoi = soGoi,
            soNgay = ((bayGio - sl.luc) / 86_400_000L).toInt().coerceAtLeast(0),
            daBatLai = daBatLai, boTuCai = boTuCai, mau = mau, daKiemTra = conTat != null,
        )
    }

    /** Ô "What happened" của issue (hiện dạng shell): thư mục gợi ý, số ngày, debloat.txt, rồi tv.json dạng chú thích. */
    fun chiTiet(b: BanXuat): String = buildString {
        append("# Suggested folder: examples/${b.thuMuc}/ (debloat.txt below, then tv.json)\n")
        append("# Off for ${b.soNgay} day${if (b.soNgay == 1) "" else "s"} so far.\n\n")
        append(b.debloat)
        append("\n# tv.json\n")
        b.tvJson.trimEnd().lines().forEach { append("# ").append(it).append('\n') }
    }

    /** Trang tạo issue "My TV" điền sẵn; danh sách quá dài cho link thì ô đó chỉ nhắc dán ([DAN_VAO]). */
    fun linkIssue(b: BanXuat): String {
        val android = listOfNotNull(b.os, b.banDung.takeIf { it.isNotBlank() }?.let { "build $it" }).joinToString(", ")
        fun link(chiTiet: String) = "https://github.com/$KHO/issues/new?" + listOf(
            "template" to "tv-list.yml", "title" to b.tieuDe, "model" to b.tv, "android" to android, "details" to chiTiet,
        ).joinToString("&") { (k, v) -> k + "=" + ma(v) }
        return link(chiTiet(b)).takeIf { it.length <= LINK_TOI_DA } ?: link(DAN_VAO)
    }

    /** Tên thư mục kiểu họ: chữ thường, gạch nối, không lặp tên hãng (Xiaomi + MiTV-MOOQ1 → xiaomi-mitv-mooq1). */
    fun thuMuc(hang: String, model: String): String {
        fun g(s: String) = s.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        val h = g(hang)
        val m = g(model)
        return when {
            m.isEmpty() -> h
            h.isEmpty() || m == h || m.startsWith("$h-") -> m
            else -> "$h-$m"
        }.ifEmpty { "my-tv" }
    }

    /** Cột OS của họ: Google TV khi có launcherx, Android TV khi có gói của Android TV, Fire OS cho Amazon. */
    fun heDieuHanh(tv: ThongTinTv, goiCai: Collection<String>): String {
        val so = tv.android.takeIf { it.isNotBlank() }?.let { " ${motDong(it)}" }.orEmpty()
        return when {
            tv.manufacturer.equals("Amazon", ignoreCase = true) || tv.model.startsWith("AFT") -> "Fire OS, Android$so"
            "com.google.android.apps.tv.launcherx" in goiCai -> "Google TV$so"
            goiCai.isEmpty() || goiCai.any { it in GOI_ANDROID_TV } -> "Android TV$so"
            else -> "Android$so"
        }
    }

    /** Một dòng gói, ghi chú thẳng cột như examples/xiaomi-mi-tv-4a/debloat.txt. */
    private fun dong(p: String, ghiChu: String): String {
        // Ghi chú lấy từ tên nhóm của danh sách khác có thể mở bằng "--- " hay "# ": bỏ đi.
        val c = motDong(ghiChu).trimStart { it in "#-—*: " }.let { if (it.length > 70) it.take(69).trimEnd() + "…" else it }
        return if (c.isEmpty()) p else (if (p.length < 39) p.padEnd(39) else "$p  ") + "# " + c
    }

    /** Một dòng, không dấu | (gen_tables.py coi | là chia ô bảng). */
    private fun motDong(s: String) = s.replace(Regex("\\s+"), " ").replace('|', '/').trim()

    private fun tenHang(s: String) = motDong(s).let { if (it == it.lowercase()) it.replaceFirstChar(Char::uppercaseChar) else it }

    private fun ngay(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().toString()

    /** tv.json viết tay để giữ thứ tự khoá và thụt lề như mẫu của họ. */
    private fun json(vararg cap: Pair<String, String>) =
        cap.joinToString(",\n", "{\n", "\n}\n") { (k, v) -> "  \"$k\": \"${thoat(v)}\"" }

    private fun thoat(s: String) = buildString {
        for (c in s) when {
            c == '"' -> append("\\\"")
            c == '\\' -> append("\\\\")
            c < ' ' -> append("\\u%04x".format(c.code))
            else -> append(c)
        }
    }

    /** Mã hoá cho URL; dấu cách viết %20 thay cho "+" như LienHe. */
    private fun ma(s: String) = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
