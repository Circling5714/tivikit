package vn.tivikit.dulieu

/**
 * Kịch bản khôi phục dùng bằng máy tính (24/10/2026, học cleanyourtv-app "Save a copy"): mất điện thoại, gỡ CleanTV
 * hay app hỏng vẫn đưa được tivi về như cũ chỉ với adb. Mỗi dòng là một lệnh adb thuần, không dùng tính năng riêng
 * của shell nào, nên cùng một file chạy được trên Windows (đuôi .bat) lẫn Mac / Linux (`sh file.bat`). Dòng chú thích
 * bắt đầu bằng ": " — Windows coi là nhãn, sh coi là lệnh rỗng — nên cả hai đều bỏ qua.
 */
object KichBanKhoiPhuc {
    private val HOME_SACH = listOf("com.gathin.tvhome", "com.ts.launcher")
    private const val PKG_KHOA = "vn.tivikit.khoa"
    private val TEN_GOI = Regex("^[A-Za-z0-9_.]+$")
    private val THANH_PHAN = Regex("^[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+$")

    fun tao(sl: BanSaoLuu, vi: Boolean): String {
        fun t(v: String, e: String) = if (vi) v else e
        val dong = mutableListOf<String>()
        fun chu(s: String) { dong += ": $s" }
        chu(t("TiviKit: kịch bản khôi phục cho ${sl.ten}", "TiviKit: restore script for ${sl.ten}"))
        chu(t("Cần adb (Android platform-tools). Windows: bấm đúp file này. Mac / Linux: sh tên-file.bat",
            "Needs adb (Android platform-tools). Windows: double-click this file. Mac / Linux: sh file-name.bat"))
        chu(t("Tivi phải bật Gỡ lỗi USB. Sai IP thì sửa dòng adb connect bên dưới.", "USB debugging must be on. If the IP changed, edit the adb connect line below."))
        dong += "adb connect ${sl.ip}:5555"
        val goi = sl.daTatBoiApp.filter { TEN_GOI.matches(it) }
        if (goi.isNotEmpty()) chu(t("Bật lại ${goi.size} gói TiviKit đã tắt", "Re-enable the ${goi.size} packages TiviKit disabled"))
        goi.forEach { dong += "adb shell pm enable --user 0 $it" }
        if ((sl.launcherDaCai || sl.homeDaDoi) && THANH_PHAN.matches(sl.homeTruoc) && !sl.homeTruoc.startsWith("android/") &&
            HOME_SACH.none { sl.homeTruoc.startsWith("$it/") }) {
            chu(t("Trả Home gốc", "Restore the stock Home"))
            dong += "adb shell cmd package set-home-activity --user 0 ${sl.homeTruoc}"
        }
        if (sl.launcherDaCai) {
            chu(t("Trả dịch vụ trợ năng như trước", "Put the accessibility services back as before"))
            val tc = sl.truyCapTruoc
            dong += if (tc.isBlank() || tc == "null" || !Regex("^[A-Za-z0-9_.:/$]+$").matches(tc)) "adb shell settings delete secure enabled_accessibility_services"
            else "adb shell settings put secure enabled_accessibility_services $tc"
            chu(t("Muốn gỡ luôn Home sạch thì bỏ \": \" ở đầu dòng dưới", "To also uninstall the clean Home, remove the \": \" at the start of the next line"))
            HOME_SACH.forEach { chu("adb uninstall $it") }
        }
        if (sl.dnsDaDoi) {
            chu(t("Trả DNS như cũ", "Restore DNS"))
            val mode = sl.dnsModeTruoc.takeIf { it.isNotBlank() && it != "null" && TEN_GOI.matches(it) } ?: "opportunistic"
            dong += "adb shell settings put global private_dns_mode $mode"
            // Tên máy chủ có "-" (VD Ten-abc123.dns.nextdns.io): TEN_GOI không nhận, xét bằng DnsRieng.hostHopLe.
            dong += if (sl.dnsHostTruoc.isBlank() || sl.dnsHostTruoc == "null" || !DnsRieng.hostHopLe(sl.dnsHostTruoc)) "adb shell settings delete global private_dns_specifier"
            else "adb shell settings put global private_dns_specifier ${sl.dnsHostTruoc}"
        }
        // App phụ cả tên gói mới lẫn tên trước khi đổi sang vn.tivikit (GoiCu): kịch bản không biết tivi đang có cái nào.
        chu(t("Gỡ app phụ của TiviKit nếu có cài: Khoá trẻ em, Hẹn giờ, Kênh nhanh, bộ gõ tiếng Việt, TiviKit Launcher, Cast SmartTube (chưa cài thì báo lỗi, không sao)",
            "Remove TiviKit's helper apps if installed: Kids lock, Timer, Quick channel, Vietnamese typing, TiviKit Launcher, Cast to SmartTube (errors here are fine)"))
        (listOf(PKG_KHOA, HenGio.GOI, GoChu.GOI, TiviKitLauncher.GOI, CastSmartTube.GOI) + (1..KenhNhanh.SO_O).map { KenhNhanh.goiCua(it) } + GoiCu.APP_PHU)
            .forEach { dong += "adb uninstall $it" }
        dong += "adb shell input keyevent KEYCODE_HOME"
        chu(t("Xong. Nên tắt Gỡ lỗi USB trên tivi.", "Done. Turn USB debugging off on the TV."))
        return dong.joinToString("\r\n", postfix = "\r\n")
    }
}
