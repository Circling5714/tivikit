package vn.tivikit.teleremote

/**
 * 🗂 Tắt đa nhiệm từ Telegram (2/11/2026): đóng mọi app người dùng cài đang chạy trên tivi, như vuốt hết app ở màn đa nhiệm
 * của điện thoại. Giữ app hệ thống (cài sẵn trong tivi), app của TiviKit (cả Home sạch), app Home và app đang giữ một vai trò
 * cho tivi: dịch vụ trợ năng, bàn phím, màn bảo vệ, VPN luôn bật, đọc thông báo. Buộc dừng những app đó làm tivi mất tính
 * năng (TX3, Android 9: buộc dừng còn bỏ dịch vụ trợ năng của app khỏi danh sách bật).
 */
object DaNhiemTr {
    /**
     * Một lệnh shell đọc đủ, các phần cách nhau "@@": app người dùng cài, tiến trình của app đang chạy (USER, lệnh), rồi các
     * cài đặt ghi app đang giữ vai trò (mỗi cài đặt một phần, "null" nếu chưa đặt). Dấu tách in bằng `echo @''@` và `ps` chỉ
     * giữ dòng của app: `ps` in cả dòng "sh -c <lệnh này>", có "@@" trong đó thì phần tiến trình bị cắt đôi (TX3, 2/11/2026:
     * số PID quay vòng, dòng sh đứng trước SmartTube nên bot báo không có app nào).
     */
    const val LENH_DOC = "pm list packages -3; echo @''@; ps -A -o USER,ARGS | grep '^u[0-9]'; echo @''@; " +
        "settings get secure enabled_accessibility_services; echo @''@; settings get secure default_input_method; echo @''@; " +
        "settings get secure screensaver_components; echo @''@; settings get secure always_on_vpn_app; echo @''@; " +
        "settings get secure enabled_notification_listeners"

    private val GOI = Regex("""^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$""")
    /** Tiến trình của app (u0_a123, u10_a45…); u0_i… là tiến trình cô lập, chạy dưới tên app khác. */
    private val USER_APP = Regex("""^u\d+_a\d+$""")
    /** Gói trong một cài đặt dạng "gói/lớp" (cách nhau ":" hay ","), hay chỉ "gói" (VPN luôn bật). */
    private val GOI_TRONG = Regex("""(?:^|[:,\s])([A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+)(?=/|[:,\s]|$)""")

    fun laTiviKit(goi: String): Boolean = goi == "vn.tivikit" || goi.startsWith("vn.tivikit.") || goi == "com.gathin.tvhome"

    /**
     * Gói cần đóng theo kết quả [kq] của [LENH_DOC]: app người dùng cài có tiến trình đang chạy, trừ TiviKit, [giuThem] (app
     * Home, chính bot) và app đang giữ vai trò; xếp theo tên gói. Rỗng nếu đọc không ra (chưa nối ADB, kết quả hỏng).
     */
    fun chon(kq: String, giuThem: Set<String>): List<String> {
        val phan = kq.replace("\r", "").split("@@")
        if (phan.size < 2) return emptyList()
        val nguoiDung = phan[0].lines().map { it.trim() }.filter { it.startsWith("package:") }.map { it.removePrefix("package:") }
            .filter { GOI.matches(it) }.toSet()
        val dangChay = phan[1].lines().mapNotNull { dong ->
            val c = dong.trim().split(Regex("\\s+"), limit = 3)
            if (c.size < 2 || !USER_APP.matches(c[0])) null else c[1].substringBefore(':')
        }.toSet()
        val vaiTro = phan.drop(2).flatMap { p -> GOI_TRONG.findAll(p.trim()).map { it.groupValues[1] }.toList() }.toSet()
        return (dangChay intersect nguoiDung).filter { !laTiviKit(it) && it !in giuThem && it !in vaiTro }.sorted()
    }

    /** Lệnh buộc dừng các gói [ds] (đã qua [chon]) rồi đóng các tiến trình nền còn lại (am kill-all, như Đóng app nền). */
    fun lenhDong(ds: List<String>): String {
        require(ds.all { GOI.matches(it) }) { "goi" }
        return (ds.map { "am force-stop $it" } + "am kill-all").joinToString("; ")
    }
}
