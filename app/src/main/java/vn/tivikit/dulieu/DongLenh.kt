package vn.tivikit.dulieu

import vn.tivikit.t

/** Tên gói Android (như ThaoTacTv.TEN_GOI). */
private val TEN_GOI = Regex("""^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$""")
/** Một thành phần gói/lớp, VD com.vidu/.DichVu. */
private val THANH_PHAN = Regex("""^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+/[A-Za-z0-9_.]+$""")
private val KHOA_CAI_DAT = Regex("""^[A-Za-z0-9_.:\-]+$""")
/** Bản sao lưu TiviKit trên tivi (như ThaoTacTv.TEP_SAO_LUU_TV). */
private const val SAO_LUU_TV = "/data/local/tmp/cleantv-sao-luu.json"

/**
 * Dòng lệnh ADB (27/10/2026): lệnh mẫu xếp từ hay dùng tới ít dùng, nhận ra lệnh nguy hiểm để hỏi lại trước khi chạy,
 * bọc lệnh để biết mã thoát và thư mục sau lệnh, tách các dòng dấu khỏi chữ tivi in ra. Chạy lệnh: KetNoiAdb.chayLenh;
 * màn hình: ui/ManDongLenh.kt.
 */
object DongLenh {

    // ---------- Chỗ trống trong lệnh mẫu ----------

    /**
     * Chỗ trống trong lệnh mẫu: [ma] trong nguồn, hiện bằng [vi] / [en]. Giá trị điền vào khớp [mau] thì đặt thẳng vào
     * lệnh; [mau] = null (chữ tự do: từ khoá, đường dẫn, link…) thì bọc nháy đơn, khoảng trắng hay ; $ không thành lệnh.
     */
    enum class Cho(val ma: String, val vi: String, val en: String, private val nhanVi: String, private val nhanEn: String, private val mau: Regex?) {
        GOI("{goi}", "<gói>", "<package>", "Tên gói, VD com.google.android.youtube.tv", "Package name, e.g. com.google.android.youtube.tv", TEN_GOI),
        TU_KHOA("{tukhoa}", "<từ khoá>", "<keyword>", "Từ khoá, VD youtube", "Keyword, e.g. youtube", null),
        FILE("{file}", "<file>", "<file>", "Đường dẫn file, VD /sdcard/Download/TiviKit/phim.mp4", "File path, e.g. /sdcard/Download/TiviKit/movie.mp4", null),
        DICH("{dich}", "<đích>", "<destination>", "Đường dẫn đích, VD /sdcard/Movies/", "Destination path, e.g. /sdcard/Movies/", null),
        THU_MUC("{thumuc}", "<thư mục>", "<folder>", "Thư mục, VD /sdcard/Download", "Folder, e.g. /sdcard/Download", null),
        DIA_CHI("{diachi}", "<địa chỉ>", "<address>", "IP hoặc tên miền, VD 192.168.1.1", "IP or domain, e.g. 192.168.1.1", Regex("""^[A-Za-z0-9.:\-]+$""")),
        LINK("{link}", "<link>", "<link>", "Đường link, VD https://www.youtube.com", "Link, e.g. https://www.youtube.com", null),
        SO("{so}", "<số>", "<number>", "Số, VD 240", "Number, e.g. 240", Regex("""^-?\d+(\.\d+)?$""")),
        QUYEN("{quyen}", "<quyền>", "<permission>", "Tên quyền, VD android.permission.READ_LOGS", "Permission, e.g. android.permission.READ_LOGS", Regex("""^[A-Za-z0-9_.]+$""")),
        DICH_VU("{dichvu}", "<gói/dịch vụ>", "<package/service>", "Gói/dịch vụ, VD com.vidu/.DichVu (lấy từ lệnh liệt kê ở trên)", "Package/service, e.g. com.example/.MyService (from the listing above)", THANH_PHAN),
        MAN("{man}", "<gói/màn>", "<package/activity>", "Gói/màn, VD com.vidu/.MainActivity (lấy từ lệnh liệt kê ở trên)", "Package/activity, e.g. com.example/.MainActivity (from the listing above)", THANH_PHAN),
        CHU("{chu}", "<chữ>", "<text>", "Chữ cần gõ (không dấu)", "Text to type", null),
        BANG("{bang}", "<bảng>", "<table>", "global, secure hoặc system", "global, secure or system", Regex("^(global|secure|system)$")),
        KHOA("{khoa}", "<tên cài đặt>", "<setting>", "Tên cài đặt, VD screen_off_timeout", "Setting name, e.g. screen_off_timeout", KHOA_CAI_DAT),
        GIA_TRI("{giatri}", "<giá trị>", "<value>", "Giá trị", "Value", null),
        THUOC_TINH("{thuoctinh}", "<thuộc tính>", "<property>", "Tên thuộc tính, VD ro.product.model", "Property name, e.g. ro.product.model", Regex("""^[A-Za-z0-9_.\-]+$""")),
        DV("{dv}", "<dịch vụ>", "<service>", "Tên dịch vụ, VD wifi, audio, display", "Service name, e.g. wifi, audio, display", Regex("""^[A-Za-z0-9_.\-]+$""")),
        PHIM("{phim}", "<phím>", "<key>", "Tên hoặc mã phím, VD KEYCODE_MENU hoặc 82", "Key name or code, e.g. KEYCODE_MENU or 82", Regex("^[A-Za-z0-9_]+$")),
        X("{x}", "<x>", "<x>", "Toạ độ ngang (điểm ảnh, theo wm size)", "Horizontal position (pixels, per wm size)", Regex("""^\d+$""")),
        Y("{y}", "<y>", "<y>", "Toạ độ dọc (điểm ảnh)", "Vertical position (pixels)", Regex("""^\d+$"""));

        val hien: String get() = t(vi, en)
        /** Nhãn ô điền ở hộp Điền lệnh. */
        val nhan: String get() = t(nhanVi, nhanEn)
        fun hopLe(s: String): Boolean = s.isNotBlank() && (mau?.matches(s.trim()) ?: true)
        /** Đã gõ ký tự không bao giờ hợp lệ (dấu cách, ; $…): báo đỏ ngay. Gõ dở (VD "vlc" chưa đủ tên gói) thì chưa báo. */
        fun saiRo(s: String): Boolean = mau != null && s.trim().any { !(it.isLetterOrDigit() || it in "._/:-") }
        /** Giá trị đặt vào lệnh: đúng mẫu thì để nguyên, chữ tự do thì bọc nháy đơn. */
        fun dat(s: String): String = if (mau != null) s.trim() else nhay(s)
    }

    /** Lệnh mẫu để hiện: chỗ trống thành <gói>, <từ khoá>… theo ngôn ngữ app. */
    fun hien(lenh: String): String = Cho.entries.fold(lenh) { l, c -> l.replace(c.ma, c.hien) }

    /** Chỗ trống trong lệnh mẫu, theo thứ tự xuất hiện. */
    fun choTrong(lenh: String): List<Cho> = Cho.entries.filter { lenh.contains(it.ma) }.sortedBy { lenh.indexOf(it.ma) }

    /** Điền chỗ trống bằng [giaTri] (Cho.dat). */
    fun dien(lenh: String, giaTri: Map<Cho, String>): String = Cho.entries.fold(lenh) { l, c -> giaTri[c]?.let { l.replace(c.ma, c.dat(it)) } ?: l }

    /** Lệnh còn chỗ trống chưa điền (<gói>, <package>…): không gửi lên tivi. */
    fun conCho(lenh: String): Boolean = Cho.entries.any { lenh.contains(it.vi) || lenh.contains(it.en) }

    /** Bọc nháy đơn cho sh: nháy đơn bên trong thành '\''. */
    fun nhay(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    /** Để nguyên nếu sh hiểu đúng một từ, không thì bọc nháy (in lệnh trả lại giá trị cũ). */
    fun nhayNeuCan(s: String): String = if (s.isNotEmpty() && s.all { it.isLetterOrDigit() || it in "_.:/,=+@%-" }) s else nhay(s)

    // ---------- Lệnh mẫu ----------

    /** Nhóm lệnh mẫu, theo thứ tự hay dùng tới ít dùng. */
    enum class Nhom(private val vi: String, private val en: String) {
        THONG_TIN("Thông tin tivi", "TV info"),
        APP("Ứng dụng", "Apps"),
        NHANH("Tăng tốc & dọn dẹp", "Speed & clean-up"),
        CAI_DAT("Mở màn cài đặt", "Open settings screens"),
        QUYEN("Cấp quyền cho app", "App permissions"),
        MANG("Mạng", "Network"),
        REMOTE("Phím remote & gõ chữ", "Remote keys & typing"),
        MAN_HINH("Màn hình, màn hình chờ & tự tắt", "Display, screensaver & auto-off"),
        FILE("Bộ nhớ & file", "Storage & files"),
        NHAT_KY("Nhật ký & chẩn đoán", "Logs & diagnostics"),
        HE_THONG("Cài đặt hệ thống", "System settings"),
        TIVIKIT("TiviKit trên tivi", "TiviKit on the TV"),
        NGUON("Khởi động lại & nguồn", "Restart & power");

        val ten: String get() = t(vi, en)
    }

    class Mau(val nhom: Nhom, val lenh: String, val vi: String, val en: String) {
        val moTa: String get() = t(vi, en)
        val coCho: Boolean get() = lenh.contains('{') && choTrong(lenh).isNotEmpty()
        /** Mức cảnh báo cao nhất khi chạy (chỗ trống chưa điền), null nếu chạy không cần hỏi. */
        val mucDo: MucDo? by lazy { kiemTra(lenh).maxOfOrNull { it.mucDo }?.takeIf { it >= MucDo.CAN_THAN } }
    }

    /** Lệnh mẫu: nhóm theo Nhom, trong nhóm cũng hay dùng ở trên. Lệnh đúng trên Android TV 7–14 qua ADB (không root). */
    val DS: List<Mau> = run {
        val ds = mutableListOf<Mau>()
        fun Nhom.l(lenh: String, vi: String, en: String) { ds += Mau(this, lenh, vi, en) }
        with(Nhom.THONG_TIN) {
            l("""getprop | grep -E 'ro\.product\.(brand|manufacturer|model|device)]|ro\.build\.version\.(release|sdk|security_patch)]'""",
                "Tóm tắt máy: hãng, model, bản Android, mức API, ngày vá bảo mật — đủ để hỏi lỗi ở nhóm hỗ trợ.",
                "Device summary: brand, model, Android version, API level, security patch — enough to ask for help.")
            l("getprop ro.product.model", "Tên model tivi.", "The TV's model name.")
            l("getprop ro.build.version.release", "Phiên bản Android.", "Android version.")
            l("getprop ro.product.cpu.abilist", "Kiểu chip mà app phải hợp (arm64-v8a, armeabi-v7a): chọn đúng bản APK khi tải app.",
                "CPU types apps must match (arm64-v8a, armeabi-v7a): pick the right APK when downloading.")
            l("cat /proc/meminfo | head -3", "RAM tổng, RAM trống, RAM còn dùng được (kB).", "Total, free and available RAM (kB).")
            l("df -h /data", "Bộ nhớ trong: tổng, đã dùng, còn trống.", "Internal storage: size, used, free.")
            l("uptime", "Tivi chạy liên tục bao lâu từ lần khởi động gần nhất, kèm độ tải CPU.", "How long since the last restart, plus CPU load.")
            l("date; getprop persist.sys.timezone", "Giờ và múi giờ trên tivi: lệch giờ là CH Play, YouTube báo lỗi đăng nhập.",
                "The TV's time and time zone: a wrong clock breaks Play Store and YouTube sign-in.")
            l("wm size; wm density", "Độ phân giải và mật độ điểm ảnh của giao diện.", "UI resolution and pixel density.")
            l("getprop ro.board.platform; getprop ro.hardware", "Tên dòng chip (VD amlogic, mt5867, realtek).", "Chip platform (e.g. amlogic, mt5867, realtek).")
            l("getprop ro.build.fingerprint", "Mã bản phần mềm đầy đủ của hãng (firmware).", "Full firmware build fingerprint.")
            l("id", "Shell đang chạy với quyền gì: uid=0 là root, uid=2000 là ADB thường.", "Which user the shell runs as: uid=0 is root, uid=2000 is normal ADB.")
            l("cat /sys/class/thermal/thermal_zone*/temp", "Nhiệt độ chip (chia 1000 ra °C).", "Chip temperature (divide by 1000 for °C).")
            l("cat /proc/cpuinfo", "Chi tiết CPU: số nhân, loại lõi.", "CPU details: cores, core types.")
            l("uname -a", "Phiên bản nhân Linux.", "Linux kernel version.")
            l("pm list features", "Tính năng tivi khai báo (android.software.leanback = Android TV, hdmi.cec…).",
                "Features the TV declares (android.software.leanback = Android TV, hdmi.cec…).")
            l("getprop", "Toàn bộ thuộc tính hệ thống (rất dài).", "Every system property (very long).")
        }
        with(Nhom.APP) {
            l("pm list packages -3", "App bạn tự cài (không gồm app hệ thống).", "Apps you installed (no system apps).")
            l("pm list packages | grep -i {tukhoa}", "Tìm tên gói theo từ khoá, VD youtube, netflix.", "Find package names by keyword, e.g. youtube, netflix.")
            l("monkey -p {goi} -c android.intent.category.LEANBACK_LAUNCHER 1", "Mở app trên tivi.", "Open an app on the TV.")
            l("am force-stop {goi}", "Buộc dừng app đang treo hoặc chạy lỗi.", "Force-stop a frozen or misbehaving app.")
            l("pm disable-user --user 0 {goi}", "Tắt app: biến khỏi tivi, không chạy nữa, dữ liệu giữ nguyên. TiviKit ghi vào bản sao lưu để Khôi phục tivi bật lại.",
                "Disable an app: hidden and stopped, data kept. TiviKit records it in the backup so Restore turns it back on.")
            l("pm enable --user 0 {goi}", "Bật lại app đã tắt.", "Re-enable a disabled app.")
            l("pm list packages -d", "Các gói đang bị tắt.", "Disabled packages.")
            l("pm clear {goi}", "Xoá toàn bộ dữ liệu app như mới cài (mất đăng nhập, cài đặt trong app). Hay chữa được app lỗi hoài.",
                "Clear all of an app's data as if freshly installed (sign-ins and settings are lost). Often fixes a broken app.")
            l("pm uninstall {goi}", "Gỡ app bạn tự cài, kèm dữ liệu.", "Uninstall an app you installed, with its data.")
            l("dumpsys package {goi} | grep -E 'versionName|versionCode|firstInstallTime|lastUpdateTime'",
                "Phiên bản app, ngày cài, ngày cập nhật gần nhất.", "App version, install date, last update.")
            l("dumpsys activity activities | grep -E 'ResumedActivity'", "App đang hiện trên màn hình tivi.", "The app on the TV screen right now.")
            l("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME",
                "Màn Home mặc định đang dùng.", "The default Home app in use.")
            l("cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME",
                "Mọi app Home có trên tivi (tên dùng cho lệnh đổi Home bên dưới).", "Every Home app on the TV (names for the set-Home command below).")
            l("cmd package set-home-activity --user 0 {man}", "Đặt app Home mặc định (gói/màn lấy từ lệnh trên).", "Set the default Home (package/activity from the command above).")
            l("monkey -p {goi} -c android.intent.category.LAUNCHER 1", "Mở app bản điện thoại (app không có biểu tượng trên màn hình tivi).",
                "Open a phone app (one with no icon on the TV home screen).")
            l("am start -a android.intent.action.VIEW -d \"youtube://search?launch=voice\" -p org.smarttube.stable",
                "Mở SmartTube vào tìm kiếm bằng giọng nói (mic bật ngay nếu trong SmartTube bật Tìm kiếm bằng giọng nói tức thì). Gán vào nút remote: Nút remote → SmartTube – tìm bằng giọng nói.",
                "Open SmartTube's voice search (the mic starts at once if Instant voice search is on in SmartTube). To put it on a remote button: Remote buttons → SmartTube – voice search.")
            l("am kill {goi}", "Đóng app nằm nền (không đụng app đang mở).", "Close an app sitting in the background (leaves the open app alone).")
            l("pm uninstall -k --user 0 {goi}", "Gỡ app hệ thống khỏi người dùng (vẫn nằm trong máy). Khôi phục tivi KHÔNG cài lại được: nên dùng lệnh tắt app ở trên.",
                "Remove a system app for the user (it stays in the firmware). Restore can NOT bring it back: prefer disabling above.")
            l("pm install-existing {goi}", "Cài lại app hệ thống đã gỡ bằng lệnh trên.", "Bring back a system app removed with the command above.")
            l("pm list packages -u", "Mọi gói, kể cả gói đã gỡ khỏi người dùng (so với danh sách thường để biết gói nào đã gỡ).",
                "All packages including ones removed for the user (compare with the normal list).")
            l("pm list packages -s", "App hệ thống.", "System apps.")
            l("pm path {goi}", "Đường dẫn file APK của app.", "Where the app's APK file is.")
            l("dumpsys meminfo {goi}", "RAM app đang dùng (dòng TOTAL).", "RAM used by an app (TOTAL row).")
            l("pm install -r {file}", "Cài file APK đã có trên tivi, VD file gửi bằng TiviKit vào /sdcard/Download/TiviKit (cài từ điện thoại thì dùng mục Cài app).",
                "Install an APK already on the TV, e.g. one sent by TiviKit into /sdcard/Download/TiviKit (from the phone, use Install apps).")
            l("pm set-app-link --user 0 {goi} always", "Luôn mở link của app bằng chính nó, không hỏi chọn app (Android 11 trở xuống; VD link YouTube mở bằng SmartTube). Dễ hơn: dùng Set App Link.",
                "Always open the app's links in it without asking (Android 11 and older; e.g. YouTube links in SmartTube). Easier: use Set App Link.")
            l("pm set-app-links-user-selection --user 0 --package {goi} true all", "Như lệnh trên, cho Android 12 trở lên.", "Same as above, for Android 12 and newer.")
            l("dumpsys package d", "App nào đang được đặt luôn mở link của trang nào (Android 11 trở xuống).", "Which apps are set to always open which sites' links (Android 11 and older).")
        }
        with(Nhom.NHANH) {
            l("settings put global window_animation_scale 0.5; settings put global transition_animation_scale 0.5; settings put global animator_duration_scale 0.5",
                "Hiệu ứng chuyển màn nhanh gấp đôi: tivi có cảm giác nhanh hẳn.", "Twice-as-fast animations: the TV feels much snappier.")
            l("settings put global window_animation_scale 0; settings put global transition_animation_scale 0; settings put global animator_duration_scale 0",
                "Tắt hẳn hiệu ứng chuyển màn.", "Turn animations off.")
            l("settings put global window_animation_scale 1; settings put global transition_animation_scale 1; settings put global animator_duration_scale 1",
                "Hiệu ứng về mặc định.", "Animations back to normal.")
            l("am kill-all", "Đóng mọi app đang nằm chờ trong bộ nhớ (như nút Làm mới ngay).", "Close every app waiting in memory (like Refresh now).")
            l("pm trim-caches 999G", "Xoá cache mọi app để lấy lại bộ nhớ trống (app mở lần đầu sau đó chậm hơn chút).",
                "Clear every app's cache to free storage (apps open a little slower the first time).")
            l("dumpsys cpuinfo | head -20", "App nào đang ăn CPU (trung bình vài phút gần nhất).", "Which apps use the CPU (average over the last few minutes).")
            l("top -n 1 -m 15", "15 tiến trình nặng nhất ngay lúc này.", "The 15 busiest processes right now.")
            l("ps -A -o RSS,NAME | sort -n -r | head -15", "15 tiến trình tốn RAM nhất (cột đầu tính bằng kB).", "The 15 processes using the most RAM (first column in kB).")
            l("dumpsys meminfo | head -40", "RAM theo từng app, nặng nhất ở trên.", "RAM per app, biggest first.")
            l("cmd package compile -m speed -f {goi}", "Tối ưu một app cho chạy nhanh nhất (mất vài chục giây).", "Fully optimise one app for speed (takes tens of seconds).")
            l("cmd package bg-dexopt-job", "Tối ưu lại mọi app, nên chạy sau khi cập nhật phần mềm tivi (vài phút, tivi chậm trong lúc chạy).",
                "Re-optimise every app; worth it after a firmware update (minutes; the TV is slow meanwhile).")
        }
        with(Nhom.CAI_DAT) {
            l("am start -a android.settings.SETTINGS", "Mở Cài đặt.", "Open Settings.")
            l("am start -a android.settings.APPLICATION_DETAILS_SETTINGS -d package:{goi}", "Trang thông tin của một app: buộc dừng, xoá dữ liệu, quyền.",
                "An app's info page: force stop, clear data, permissions.")
            l("am start -a android.settings.APPLICATION_DEVELOPMENT_SETTINGS", "Tuỳ chọn nhà phát triển.", "Developer options.")
            l("am start -a android.settings.DATE_SETTINGS", "Ngày giờ (giờ sai làm CH Play, YouTube lỗi).", "Date & time (a wrong clock breaks Play Store, YouTube).")
            l("am start -a android.settings.WIFI_SETTINGS", "Cài đặt Wi-Fi.", "Wi-Fi settings.")
            l("am start -a android.settings.BLUETOOTH_SETTINGS", "Bluetooth: ghép remote, tai nghe, loa.", "Bluetooth: pair remotes, headphones, speakers.")
            l("am start -a android.settings.MANAGE_APPLICATIONS_SETTINGS", "Danh sách ứng dụng.", "The apps list.")
            l("am start -a android.settings.INPUT_METHOD_SETTINGS", "Bàn phím.", "Keyboards.")
            l("am start -a android.settings.LOCALE_SETTINGS", "Ngôn ngữ.", "Language.")
            l("am start -a android.settings.ACCESSIBILITY_SETTINGS", "Hỗ trợ tiếp cận (dịch vụ của app đổi phím remote, đọc màn hình).",
                "Accessibility (remote-button app services, screen readers).")
            l("am start -a android.settings.SOUND_SETTINGS", "Âm thanh.", "Sound.")
            l("am start -a android.settings.DISPLAY_SETTINGS", "Hiển thị.", "Display.")
            l("am start -a android.settings.DREAM_SETTINGS", "Màn hình chờ.", "Screensaver.")
            l("am start -a android.settings.INTERNAL_STORAGE_SETTINGS", "Bộ nhớ.", "Storage.")
            l("am start -a android.settings.MANAGE_UNKNOWN_APP_SOURCES", "Cho app cài app khác (nguồn không xác định, Android 8+).", "Install unknown apps (Android 8+).")
            l("am start -a android.settings.SECURITY_SETTINGS", "Bảo mật & hạn chế (nguồn không xác định trên Android 7).", "Security & restrictions (unknown sources on Android 7).")
            l("am start -a android.settings.ADD_ACCOUNT_SETTINGS", "Thêm tài khoản (Google…).", "Add an account (Google…).")
            l("am start -a android.settings.DEVICE_INFO_SETTINGS", "Giới thiệu thiết bị (bấm Bản dựng 7 lần để mở Tuỳ chọn nhà phát triển).",
                "About (press Build 7 times to unlock Developer options).")
            l("am start -n com.android.tv.settings/.MainSettings", "Cài đặt Android TV gốc, khi hãng đã thay bằng Cài đặt riêng (nếu tivi còn).",
                "Stock Android TV settings when the maker replaced them (if still there).")
            l("am start -a android.intent.action.VIEW -d {link}", "Mở một đường link trên tivi (trình duyệt hoặc app hợp với link).",
                "Open a link on the TV (browser or the matching app).")
            l("am start -a android.intent.action.VIEW -d \"market://details?id={goi}\"", "Mở trang CH Play của một app.", "Open an app's Play Store page.")
            l("am start -a android.settings.HOME_SETTINGS", "Chọn app Home (nếu tivi có màn này).", "Choose the Home app (if the TV has this screen).")
        }
        with(Nhom.QUYEN) {
            l("appops set {goi} SYSTEM_ALERT_WINDOW allow", "Cho app hiện đè lên app khác — tivi thường không có mục này trong Cài đặt (app đổi phím remote, đồng hồ nổi, phụ đề…).",
                "Let an app draw over other apps — TVs usually lack this setting (remote-button apps, floating clocks, subtitles…).")
            l("pm grant {goi} android.permission.WRITE_SECURE_SETTINGS", "Cho app đổi cài đặt hệ thống bảo mật (app đổi phím remote, tự động hoá hay hướng dẫn chạy lệnh này).",
                "Let an app change secure system settings (remote-button and automation apps often ask for this).")
            l("appops set {goi} REQUEST_INSTALL_PACKAGES allow", "Cho app cài app khác (trình tải, kho app).", "Let an app install other apps (downloaders, app stores).")
            l("appops set {goi} MANAGE_EXTERNAL_STORAGE allow", "Cho app đọc ghi mọi file (Android 11+: trình quản lý file, Kodi…).", "All-files access (Android 11+: file managers, Kodi…).")
            l("appops set {goi} GET_USAGE_STATS allow", "Cho app xem thời gian dùng app (launcher, app thống kê).", "Usage access (launchers, stats apps).")
            l("pm grant {goi} android.permission.READ_LOGS", "Cho app đọc nhật ký hệ thống (app xem log, tự động hoá).", "Let an app read system logs (log viewers, automation).")
            l("cmd package query-services --brief -a android.accessibilityservice.AccessibilityService",
                "Các dịch vụ trợ năng đã cài: tên gói/dịch vụ cho lệnh dưới.", "Installed accessibility services: package/service names for the next command.")
            l("s=\$(settings get secure enabled_accessibility_services); case \"\$s\" in *{dichvu}*) ;; null|'') settings put secure enabled_accessibility_services {dichvu} ;; " +
                "*) settings put secure enabled_accessibility_services \"\$s:{dichvu}\" ;; esac; settings put secure accessibility_enabled 1",
                "Bật dịch vụ trợ năng của app mà vẫn giữ các dịch vụ đang bật (Home sạch, Hẹn giờ, Khoá trẻ em).",
                "Turn on an app's accessibility service while keeping the ones already on (clean Home, Timer, Kids lock).")
            l("cmd notification allow_listener {dichvu}", "Cho app đọc thông báo và nhạc đang phát (launcher, app điều khiển nhạc).",
                "Notification access (launchers, media controllers).")
            l("dumpsys deviceidle whitelist +{goi}", "Cho app chạy nền không bị Android hạn chế (hẹn giờ, đồng bộ, VPN).",
                "Exempt an app from Android's background limits (timers, sync, VPN).")
            l("pm grant {goi} {quyen}", "Cấp một quyền app có khai báo (quyền thường hỏi bằng hộp thoại, hoặc quyền đặc biệt cấp được qua ADB).",
                "Grant a permission the app declares (a runtime one, or a special one ADB may grant).")
            l("pm revoke {goi} {quyen}", "Thu hồi một quyền đã cấp.", "Revoke a granted permission.")
            l("dumpsys package {goi} | grep -E 'permission.*granted=true'", "Các quyền app đang có.", "Permissions the app holds.")
            l("appops get {goi}", "Các quyền đặc biệt (appops) của app: allow là cho, deny / ignore là chặn.", "The app's special permissions (app ops): allow, or deny / ignore.")
            l("pm grant {goi} android.permission.DUMP", "Cho app đọc thông tin chẩn đoán hệ thống (dumpsys).", "Let an app read system diagnostics (dumpsys).")
        }
        with(Nhom.MANG) {
            l("ip -4 addr show | grep inet", "Địa chỉ IP của tivi (wlan0 = Wi-Fi, eth0 = dây mạng).", "The TV's IP addresses (wlan0 = Wi-Fi, eth0 = cable).")
            l("ping -c 4 8.8.8.8", "Có vào được Internet không (không cần DNS).", "Can it reach the Internet (no DNS needed)?")
            l("ping -c 4 google.com", "Internet và DNS có chạy không: lệnh trên được mà lệnh này lỗi là DNS hỏng.",
                "Internet plus DNS: if the one above works and this fails, DNS is broken.")
            l("dumpsys wifi | grep -m 3 mWifiInfo", "Tên Wi-Fi, cường độ sóng (RSSI: gần 0 là mạnh, dưới -75 là yếu), tốc độ, băng tần.",
                "Wi-Fi name, signal (RSSI: near 0 is strong, below -75 is weak), speed, band.")
            l("settings get global private_dns_mode; settings get global private_dns_specifier",
                "DNS riêng đang dùng: off, opportunistic (tự động) hoặc hostname (máy chủ riêng, ở dòng dưới).",
                "Private DNS: off, opportunistic (automatic) or hostname (custom server, on the next line).")
            l("settings put global private_dns_mode opportunistic", "Trả DNS riêng về tự động: sửa mất mạng do máy chủ DNS sai.",
                "Private DNS back to automatic: fixes no internet caused by a bad DNS server.")
            l("settings get global http_proxy", "Proxy đang đặt (null hoặc :0 là không có).", "HTTP proxy in use (null or :0 means none).")
            l("settings put global http_proxy :0", "Bỏ proxy: sửa lỗi Wi-Fi vẫn nối mà mọi app báo mất mạng.", "Remove the proxy: fixes 'Wi-Fi connected but no app has internet'.")
            l("ping -c 4 {diachi}", "Ping một địa chỉ bất kỳ (modem, máy chủ…).", "Ping any address (router, server…).")
            l("ip route", "Router (cổng mạng) tivi đang dùng.", "The router (gateway) the TV uses.")
            l("""dumpsys connectivity | grep -o 'DnsAddresses: \[[^]]*]' | head -3""", "Máy chủ DNS tivi đang hỏi.", "DNS servers the TV is using.")
            l("cmd wifi status", "Tình trạng Wi-Fi gọn (Android 11+).", "Wi-Fi status summary (Android 11+).")
            l("cmd wifi list-networks", "Các mạng Wi-Fi đã lưu (Android 11+).", "Saved Wi-Fi networks (Android 11+).")
            l("ip neigh", "Thiết bị cùng mạng tivi đã liên lạc gần đây (IP, địa chỉ MAC).", "Devices on the network the TV talked to recently (IP, MAC).")
            l("netstat -tln", "Cổng TCP đang mở trên tivi (5555 là ADB).", "Open TCP ports on the TV (5555 is ADB).")
            l("svc wifi enable", "Bật Wi-Fi (khi tivi đang nối dây mạng).", "Turn Wi-Fi on (while connected by cable).")
            l("svc wifi disable", "Tắt Wi-Fi — nếu TiviKit nối qua Wi-Fi thì mất kết nối, phải bật lại bằng remote.",
                "Turn Wi-Fi off — if TiviKit uses Wi-Fi the link drops; turn it back on with the remote.")
        }
        with(Nhom.REMOTE) {
            l("input keyevent KEYCODE_HOME", "Bấm Home.", "Press Home.")
            l("input keyevent KEYCODE_BACK", "Bấm Quay lại.", "Press Back.")
            l("input keyevent KEYCODE_DPAD_CENTER", "Bấm OK (nút giữa).", "Press OK (centre).")
            l("input keyevent KEYCODE_DPAD_UP", "Mũi tên lên; đổi UP thành DOWN, LEFT, RIGHT cho hướng khác.", "Arrow up; use DOWN, LEFT, RIGHT for the others.")
            l("input keyevent KEYCODE_VOLUME_UP", "Tăng âm lượng (VOLUME_DOWN để giảm).", "Volume up (VOLUME_DOWN for down).")
            l("input keyevent KEYCODE_VOLUME_MUTE", "Tắt / mở tiếng.", "Mute / unmute.")
            l("input keyevent KEYCODE_MEDIA_PLAY_PAUSE", "Phát / tạm dừng.", "Play / pause.")
            l("input text {chu}", "Gõ chữ vào ô đang chọn trên tivi (không dấu; tiếng Việt có dấu thì dùng Điều khiển + gõ chữ).",
                "Type into the selected field on the TV (no accents; for Vietnamese use Remote + typing).")
            l("input keyevent KEYCODE_SETTINGS", "Mở bảng Cài đặt nhanh của tivi.", "Open the TV's quick settings.")
            l("input keyevent KEYCODE_SEARCH", "Mở tìm kiếm.", "Open search.")
            l("input keyevent KEYCODE_SLEEP", "Cho tivi ngủ (như tắt bằng remote).", "Put the TV to sleep (like the remote's power button).")
            l("input keyevent KEYCODE_WAKEUP", "Đánh thức tivi.", "Wake the TV.")
            l("input keyevent KEYCODE_TV_INPUT", "Chọn nguồn vào (HDMI…) trên tivi có hỗ trợ.", "Choose the input (HDMI…) on TVs that support it.")
            l("input keyevent {phim}", "Bấm phím bất kỳ theo tên hoặc mã (VD KEYCODE_MENU, KEYCODE_CHANNEL_UP, 82).",
                "Press any key by name or code (e.g. KEYCODE_MENU, KEYCODE_CHANNEL_UP, 82).")
            l("input tap {x} {y}", "Chạm vào một điểm trên màn hình (app không đi được bằng remote; toạ độ theo wm size).",
                "Tap a point on screen (for apps the remote can't drive; coordinates follow wm size).")
            l("ime list -s", "Các bàn phím đang bật.", "Enabled keyboards.")
            l("settings get secure default_input_method", "Bàn phím đang dùng.", "The keyboard in use.")
            l("ime set {dichvu}", "Đổi bàn phím mặc định (tên lấy từ lệnh ime list -s).", "Switch the default keyboard (name from ime list -s).")
            l("cat /proc/bus/input/devices | grep Name", "Remote, bàn phím, chuột tivi đang nhận.", "Remotes, keyboards and mice the TV sees.")
            l("getevent -lt", "Xem tín hiệu thô khi bấm remote (chạy liên tục, bấm Dừng khi xong).", "Raw remote events as you press keys (runs until you press Stop).")
        }
        with(Nhom.MAN_HINH) {
            l("settings put secure attentive_timeout -1", "Không tự tắt tivi khi lâu không bấm remote (Google TV / Android 10+, mặc định 4 giờ).",
                "Don't turn the TV off when idle (Google TV / Android 10+, default 4 hours).")
            l("settings delete secure attentive_timeout", "Tự tắt khi không bấm remote về như mặc định.", "Idle auto-off back to default.")
            l("settings put system screen_off_timeout 1800000", "Bật màn hình chờ sau 30 phút không bấm remote (tính bằng mili giây: 600000 = 10 phút).",
                "Start the screensaver after 30 idle minutes (milliseconds: 600000 = 10 min).")
            l("settings put secure screensaver_enabled 0", "Tắt màn hình chờ.", "Turn the screensaver off.")
            l("settings put secure screensaver_enabled 1", "Bật màn hình chờ.", "Turn the screensaver on.")
            l("cmd package query-services --brief -a android.service.dreams.DreamService", "Các màn hình chờ đã cài (tên dùng cho lệnh dưới).",
                "Installed screensavers (names for the command below).")
            l("settings put secure screensaver_components {dichvu}", "Chọn màn hình chờ (Google TV không cho chọn trong Cài đặt).",
                "Pick the screensaver (Google TV hides this setting).")
            l("settings put secure screensaver_components com.neilturner.aerialviews/.ui.screensaver.DreamActivity",
                "Dùng Aerial Views (có trong Gợi ý app) làm màn hình chờ.", "Use Aerial Views (in Suggested apps) as the screensaver.")
            l("settings put secure screensaver_components com.google.android.apps.tv.dreamx/.service.Backdrop",
                "Trả về màn hình chờ Chế độ xung quanh mặc định của Google TV.", "Back to Google TV's default Ambient mode screensaver.")
            l("settings put secure screensaver_components com.google.android.backdrop/.Backdrop",
                "Trả về màn hình chờ Backdrop của Android TV đời cũ.", "Back to older Android TV's Backdrop screensaver.")
            l("settings put system font_scale 1.15", "Chữ to hơn 15% cho dễ đọc (1.0 = mặc định).", "Text 15% larger (1.0 = default).")
            l("settings put system font_scale 1.0", "Cỡ chữ mặc định.", "Default text size.")
            l("wm density {so}", "Đổi mật độ điểm ảnh: số nhỏ hơn thì giao diện nhỏ lại, chứa được nhiều hơn. Trả lại bằng lệnh dưới.",
                "Change pixel density: a smaller number shrinks the UI. Undo with the command below.")
            l("wm density reset", "Mật độ điểm ảnh về gốc.", "Pixel density back to stock.")
            l("wm size reset", "Độ phân giải giao diện về gốc.", "UI resolution back to stock.")
            l("screencap -p /sdcard/Download/manhinh.png", "Chụp màn hình tivi vào Download/manhinh.png (xem nhanh thì dùng Xem màn hình tivi).",
                "Screenshot into Download/manhinh.png (for a quick look use TV screenshot).")
            l("screenrecord --time-limit 30 /sdcard/Download/quay.mp4", "Quay màn hình 30 giây vào Download/quay.mp4 (phim có bản quyền thường ra màu đen).",
                "Record 30 s of screen into Download/quay.mp4 (protected video records black).")
            l("settings get global hdmi_control_enabled", "HDMI-CEC có bật không (1 = có): remote box điều khiển được tivi, loa qua HDMI.",
                "Is HDMI-CEC on (1 = yes): the box remote can control the TV and soundbar.")
            l("settings put global hdmi_control_enabled 1", "Bật HDMI-CEC.", "Turn HDMI-CEC on.")
            l("dumpsys hdmi_control | head -40", "Thiết bị HDMI-CEC đang nối (tivi, loa soundbar…).", "Connected HDMI-CEC devices (TV, soundbar…).")
        }
        with(Nhom.FILE) {
            l("df -h", "Dung lượng mọi phân vùng và ổ USB.", "Space on every partition and USB drive.")
            l("ls -la /sdcard/Download", "File trong thư mục Download.", "Files in Download.")
            l("ls -la /sdcard/Download/TiviKit", "File đã gửi từ TiviKit.", "Files sent from TiviKit.")
            l("du -sh /sdcard/* 2>/dev/null", "Mỗi thư mục trong bộ nhớ trong chiếm bao nhiêu.", "How much each folder in internal storage takes.")
            l("find /sdcard -type f -size +100M 2>/dev/null", "File lớn hơn 100 MB (hay là phim tải về quên xoá).", "Files over 100 MB (often forgotten downloads).")
            l("ls /storage", "Ổ USB, thẻ nhớ đang cắm (tên dạng 1234-ABCD).", "Plugged-in USB drives and cards (named like 1234-ABCD).")
            l("sm list-volumes", "Các ổ lưu trữ và tình trạng (mounted = đang dùng được).", "Storage volumes and their state (mounted = usable).")
            l("ls -la {thumuc}", "Xem file trong một thư mục.", "List a folder.")
            l("cat {file}", "Xem nội dung file chữ.", "Show a text file.")
            l("rm {file}", "Xoá một file (không có thùng rác).", "Delete a file (no recycle bin).")
            l("cp {file} {dich}", "Chép file.", "Copy a file.")
            l("mv {file} {dich}", "Chuyển hoặc đổi tên file.", "Move or rename a file.")
            l("mkdir -p {thumuc}", "Tạo thư mục.", "Create a folder.")
            l("rm -r /sdcard/Download/TiviKit", "Xoá mọi file đã gửi từ TiviKit.", "Delete everything sent from TiviKit.")
            l("sha256sum {file}", "Mã SHA-256 của file: so với mã trên trang tải để biết file không hỏng, không bị sửa.",
                "A file's SHA-256: compare with the download page to make sure it's intact.")
        }
        with(Nhom.NHAT_KY) {
            l("logcat -t 200", "200 dòng nhật ký gần nhất.", "The last 200 log lines.")
            l("logcat -t 300 '*:E'", "Chỉ các lỗi trong nhật ký gần đây.", "Only errors from the recent log.")
            l("logcat -b crash -d", "App nào bị văng gần đây và vì sao.", "Which apps crashed recently and why.")
            l("logcat -d | grep -i {tukhoa}", "Tìm trong nhật ký theo từ khoá (tên app, chữ trong thông báo lỗi).", "Search the log for a keyword (app name, error text).")
            l("logcat", "Xem nhật ký trực tiếp (chạy liên tục, bấm Dừng khi đủ).", "Live log (runs until you press Stop).")
            l("logcat -c", "Xoá nhật ký cũ trước khi làm lại lỗi, cho dễ đọc.", "Clear the log before reproducing a problem, so it's easier to read.")
            l("dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'", "Cửa sổ đang được chọn (biết app nào giữ remote).", "The focused window (find what's grabbing the remote).")
            l("dumpsys media_session | grep -E 'package=|state=PlaybackState'", "App nào đang phát nhạc / video (state=3 là đang phát).",
                "What's playing (state=3 means playing).")
            l("dumpsys {dv}", "Thông tin chẩn đoán của một dịch vụ hệ thống (VD wifi, audio, display, package).",
                "Diagnostics of one system service (e.g. wifi, audio, display, package).")
            l("dumpsys -l", "Danh sách dịch vụ dùng được với dumpsys.", "Services you can dumpsys.")
            l("dmesg | tail -50", "Nhật ký nhân Linux (nhiều tivi chặn với quyền ADB).", "Kernel log (many TVs block it over ADB).")
            l("getprop | grep -i {tukhoa}", "Tìm thuộc tính hệ thống theo từ khoá.", "Search system properties by keyword.")
            l("cmd -l", "Các dịch vụ nhận lệnh cmd.", "Services that accept cmd.")
        }
        with(Nhom.HE_THONG) {
            l("settings get {bang} {khoa}", "Đọc một cài đặt (bảng: global, secure hoặc system).", "Read one setting (table: global, secure or system).")
            l("settings put {bang} {khoa} {giatri}", "Ghi một cài đặt; TiviKit in giá trị cũ ra trước để đổi lại được.",
                "Write a setting; TiviKit prints the old value first so you can undo.")
            l("settings delete {bang} {khoa}", "Xoá một cài đặt (tivi dùng lại mặc định).", "Delete a setting (the TV goes back to its default).")
            l("settings list global", "Toàn bộ cài đặt global.", "All global settings.")
            l("settings list secure", "Toàn bộ cài đặt secure.", "All secure settings.")
            l("settings list system", "Toàn bộ cài đặt system.", "All system settings.")
            l("settings put global auto_time 1", "Bật giờ tự động theo mạng (sửa giờ sai làm CH Play lỗi).", "Network time on (fixes the wrong clock that breaks the Play Store).")
            l("getprop {thuoctinh}", "Đọc một thuộc tính hệ thống.", "Read a system property.")
            l("setprop {thuoctinh} {giatri}", "Đặt thuộc tính hệ thống (đa số bị chặn với quyền ADB; persist.* giữ sau khi khởi động lại).",
                "Set a system property (most are blocked over ADB; persist.* survives restarts).")
            l("settings put secure install_non_market_apps 1", "Cho cài app ngoài CH Play (Android 7 trở xuống).", "Allow apps from outside the Play Store (Android 7 and older).")
            l("pm list users", "Người dùng trên tivi.", "Users on the TV.")
            l("cmd overlay list", "Lớp phủ giao diện (overlay) đang có.", "UI overlays.")
        }
        with(Nhom.TIVIKIT) {
            l("pm list packages | grep vn.tivikit", "App phụ TiviKit có trên tivi: Kênh nhanh, Hẹn giờ, Khoá trẻ em, bộ gõ.",
                "TiviKit helper apps on the TV: Quick channel, Timer, Kids lock, typing.")
            l("cat $SAO_LUU_TV", "Bản sao lưu TiviKit cất trên tivi: các gói đã tắt, Home cũ, DNS cũ.",
                "TiviKit's backup stored on the TV: disabled packages, old Home, old DNS.")
            l("dumpsys package com.gathin.tvhome | grep -E 'versionName|lastUpdateTime'", "Phiên bản Home sạch trên tivi (bản cho Xiaomi: com.ts.launcher).",
                "The clean Home's version on the TV (Xiaomi build: com.ts.launcher).")
            l("settings get secure enabled_accessibility_services", "Dịch vụ trợ năng đang bật (Home sạch, Hẹn giờ, Khoá trẻ em dùng mục này).",
                "Accessibility services that are on (the clean Home, Timer and Kids lock use them).")
            l("ls -la /data/local/tmp", "Thư mục tạm của ADB (TiviKit cất bản sao lưu ở đây).", "ADB's temp folder (where TiviKit keeps its backup).")
        }
        with(Nhom.NGUON) {
            l("reboot", "Khởi động lại tivi (nút Khởi động lại ở màn chính thì tự nối lại sau khi tivi lên).",
                "Restart the TV (the Reboot button on the main screen reconnects afterwards).")
            l("input keyevent KEYCODE_POWER", "Bấm nút nguồn: bật / tắt màn hình (chờ).", "Press power: screen on / off (standby).")
            l("reboot -p", "Tắt hẳn tivi: phải bật lại bằng nút nguồn.", "Shut the TV down: turn it back on with the power button.")
            l("reboot recovery", "Vào chế độ recovery (cập nhật / sửa phần mềm). Bấm nhầm trong đó có thể xoá sạch tivi.",
                "Boot into recovery (firmware updates / repair). A wrong choice there can wipe the TV.")
            l("reboot bootloader", "Vào bootloader (nạp phần mềm từ máy tính).", "Boot into the bootloader (flashing from a computer).")
        }
        ds
    }

    /** Bỏ "adb shell" (cả "adb -s <máy> shell") người dùng chép từ hướng dẫn cho máy tính. */
    private val ADB_SHELL = Regex("""^\s*adb(?:\s+-[sHPLt]\s+\S+|\s+-[de])*\s+shell\b\s*""")

    /**
     * Lệnh chạy được trên tivi từ chữ người dùng gõ / dán: bỏ "adb shell" ở đầu từng dòng (hướng dẫn trên mạng viết cho máy
     * tính), phần sau nằm gọn trong một cặp nháy thì bỏ nháy. Trả kèm có bỏ gì không, để báo người dùng.
     */
    fun chuanHoa(lenh: String): Pair<String, Boolean> {
        var bo = false
        val dong = lenh.replace("\r", "").lines().map { d ->
            val m = ADB_SHELL.find(d) ?: return@map d
            bo = true
            var con = d.substring(m.range.last + 1).trim()
            if (con.length >= 2 && con[0] in "'\"" && con.last() == con[0] && con.count { it == con[0] } == 2) con = con.substring(1, con.length - 1)
            con
        }
        return dong.joinToString("\n").trim() to bo
    }

    // ---------- Tách lệnh ----------

    /** Một lệnh đơn: các từ (đã bỏ nháy) và các file bị ghi vào bằng > / >>. */
    class Cau(val tu: List<String>, val ghiVao: List<String> = emptyList()) {
        val ten: String get() = tu.firstOrNull()?.substringAfterLast('/') ?: ""
        val doi: List<String> get() = tu.drop(1)
    }

    /**
     * Tách [lenh] thành lệnh đơn theo ; && || | & ( ) ` và xuống dòng (ngoài nháy), mỗi lệnh đơn thành các từ đã bỏ nháy.
     * File sau > / >> vào ghiVao; 2>&1, < file bỏ; # tới hết dòng là chú thích. Thô hơn sh thật nhưng đủ nhận ra lệnh
     * nguy hiểm; chỗ không chắc thì nghiêng về báo (thân heredoc bị coi là lệnh). $(…) và `…` trong nháy kép vẫn chạy: lệnh
     * bên trong được tách thêm (28/10/2026: echo "$(reboot -p)" hay r="$(pm uninstall …)" lọt qua).
     */
    fun tach(lenh: String): List<Cau> {
        val ra = mutableListOf<Cau>()
        val con = mutableListOf<String>()
        var tu = mutableListOf<String>()
        var ghi = mutableListOf<String>()
        val w = StringBuilder()
        var coW = false
        // Từ đang đọc là: 0 đối số; 1 file bị ghi (sau >); 2 bỏ (số fd sau >&, file sau <).
        var loaiW = 0
        fun hetW() {
            if (coW) {
                when (loaiW) { 0 -> tu += w.toString(); 1 -> ghi += w.toString() }
                loaiW = 0
            }
            w.setLength(0); coW = false
        }
        fun hetCau() {
            hetW(); loaiW = 0
            if (tu.isNotEmpty() || ghi.isNotEmpty()) ra += Cau(tu, ghi)
            tu = mutableListOf(); ghi = mutableListOf()
        }
        var nhay = ' '
        var i = 0
        while (i < lenh.length) {
            val c = lenh[i]
            if (nhay != ' ') {
                when {
                    c == nhay -> nhay = ' '
                    nhay == '"' && c == '\\' && i + 1 < lenh.length && lenh[i + 1] in "\"\\$`" -> { w.append(lenh[i + 1]); i++ }
                    nhay == '"' && (c == '`' || (c == '$' && i + 1 < lenh.length && lenh[i + 1] == '(')) -> {
                        val het = cuoiLenhCon(lenh, i)
                        con += lenh.substring(if (c == '`') i + 1 else i + 2, maxOf(if (c == '`') i + 1 else i + 2, het - 1))
                        w.append(lenh, i, het); i = het; continue
                    }
                    else -> w.append(c)
                }
                i++; continue
            }
            when {
                c == '\'' || c == '"' -> { nhay = c; coW = true }
                c == '\\' -> { if (i + 1 < lenh.length && lenh[i + 1] != '\n') { w.append(lenh[i + 1]); coW = true }; i++ }
                c == '#' && !coW -> { while (i + 1 < lenh.length && lenh[i + 1] != '\n') i++ }
                c == ' ' || c == '\t' -> hetW()
                c in ";|&\n()`" -> hetCau()
                c == '>' || c == '<' -> {
                    // 2>file, >&2: số fd viết dính trước dấu không phải đối số.
                    if (coW && w.all { it.isDigit() }) { w.setLength(0); coW = false } else hetW()
                    var j = i + 1
                    while (j < lenh.length && lenh[j] == c) j++
                    loaiW = if (j < lenh.length && lenh[j] == '&') { j++; 2 } else if (c == '>') 1 else 2
                    i = j; continue
                }
                else -> { w.append(c); coW = true }
            }
            i++
        }
        hetCau()
        con.forEach { ra += tach(it) }
        return ra
    }

    /** Vị trí ngay sau lệnh con `…` hay $(…) bắt đầu ở [i] (đếm ngoặc lồng nhau); không đóng thì hết chuỗi. */
    private fun cuoiLenhCon(lenh: String, i: Int): Int {
        if (lenh[i] == '`') {
            var j = i + 1
            while (j < lenh.length && lenh[j] != '`') { if (lenh[j] == '\\') j++; j++ }
            return minOf(j + 1, lenh.length)
        }
        var sau = 0
        var j = i + 1
        while (j < lenh.length) {
            when (lenh[j]) { '(' -> sau++; ')' -> { sau--; if (sau == 0) return j + 1 } }
            j++
        }
        return lenh.length
    }

    private val GAN_BIEN = Regex("""^[A-Za-z_][A-Za-z0-9_]*=.*""")
    private val TU_KHOA_SH = setOf("do", "then", "else", "elif", "if", "while", "until", "!", "{", "}", "time")
    private val BO_QUA_CAU = setOf("for", "case", "select", "function", "done", "fi", "esac", "in")
    private val LENH_BOC = setOf("nohup", "exec", "command", "builtin", "busybox", "toybox", "toolbox")

    /** Các lệnh đơn trong [lenh], đã gỡ lớp bọc (gán biến, từ khoá sh, nohup, timeout…); su -c / sh -c thì lấy lệnh bên trong. */
    fun cacLenh(lenh: String): List<Cau> = cacLenh(lenh, 0)

    private fun cacLenh(lenh: String, sau: Int): List<Cau> = if (sau > 4) emptyList() else tach(lenh).flatMap { goBoc(it, sau) }

    private fun goBoc(c: Cau, sau: Int): List<Cau> {
        val ghi = listOfNotNull(c.ghiVao.takeIf { it.isNotEmpty() }?.let { Cau(emptyList(), it) })
        var w = c.tu
        while (w.isNotEmpty()) {
            val dau = w[0]
            val ten = dau.substringAfterLast('/')
            w = when {
                GAN_BIEN.matches(dau) || dau in TU_KHOA_SH || ten in LENH_BOC -> w.drop(1)
                dau in BO_QUA_CAU -> return ghi
                ten == "nice" -> boTuyChon(w.drop(1), setOf("-n"))
                ten == "timeout" -> boTuyChon(w.drop(1), setOf("-s", "-k")).drop(1)
                ten == "env" -> w.drop(1).dropWhile { it.startsWith("-") || GAN_BIEN.matches(it) }
                ten == "xargs" -> boTuyChon(w.drop(1), setOf("-n", "-I", "-L", "-P", "-d", "-s", "-E", "-a"))
                ten in setOf("sh", "mksh", "bash", "su") -> {
                    val i = w.indexOf("-c")
                    if (i in 1 until w.size - 1) return cacLenh(w[i + 1], sau + 1) + ghi
                    // su 0 lệnh / su root lệnh (su của Magisk, SuperSU nhận lệnh sau tên người dùng).
                    val con = if (ten == "su") w.drop(1).dropWhile { it == "0" || it == "root" || it.startsWith("-") } else emptyList()
                    if (con.isNotEmpty()) con else break
                }
                else -> break
            }
        }
        return if (w.isEmpty()) ghi else listOf(Cau(w, c.ghiVao))
    }

    private fun boTuyChon(w: List<String>, coGiaTri: Set<String>): List<String> {
        var i = 0
        while (i < w.size && w[i].startsWith("-")) i += if (w[i] in coGiaTri) 2 else 1
        return w.drop(i)
    }

    /** Bỏ "--user N" (settings, pm). */
    private fun boUser(a: List<String>): List<String> {
        val i = a.indexOf("--user")
        return if (i < 0) a else a.take(i) + a.drop(i + 2)
    }

    // ---------- Kiểm tra trước khi chạy ----------

    /** BAO: chỉ in ra rồi chạy; CAN_THAN: hỏi lại; NGUY_HIEM: hỏi lại, phải tích "tôi hiểu"; KHONG_CHAY: không gửi lên tivi. */
    enum class MucDo { BAO, CAN_THAN, NGUY_HIEM, KHONG_CHAY }

    /** Một lời cảnh báo; [ma] cho biết luật nào bắt (thử nghiệm dùng); [matKetNoi]: chạy xong TiviKit mất kết nối với tivi. */
    class CanhBao(val ma: String, val mucDo: MucDo, private val vi: String, private val en: String, val matKetNoi: Boolean = false) {
        val chu: String get() = t(vi, en)
    }

    /**
     * Cảnh báo cho [lenh] (đã chuẩn hoá), nặng nhất trước, mỗi luật một lần. [canGiu]: gói tivi cần để chạy (lõi Android,
     * Home đang dùng, bàn phím… theo KeHoach) — đụng vào thì thành nguy hiểm. Rỗng thì chạy luôn.
     */
    fun kiemTra(lenh: String, canGiu: (String) -> Boolean = { false }): List<CanhBao> {
        val ra = LinkedHashMap<String, CanhBao>()
        val them: (CanhBao) -> Unit = { ra.putIfAbsent(it.ma, it) }
        // Lệnh mẫu bật dịch vụ trợ năng đọc danh sách cũ rồi ghép thêm ("$s:…"): không phải ghi đè. Chỉ đọc mà không ghép,
        // ghi thẳng giá trị khác, thì vẫn là ghi đè (28/10/2026: settings get …; settings put … a.b/.S lọt qua).
        val docTroNang = lenh.contains("get secure enabled_accessibility_services") &&
            Regex("""put secure enabled_accessibility_services\s+"?\$""").containsMatchIn(lenh)
        for (c in cacLenh(lenh)) xet(c, canGiu, docTroNang, them)
        return ra.values.sortedByDescending { it.mucDo }
    }

    private fun xet(c: Cau, canGiu: (String) -> Boolean, docTroNang: Boolean, them: (CanhBao) -> Unit) {
        if (c.ghiVao.any { laDuongHeThong(it) }) them(CB_HE_THONG)
        val a = c.doi
        val ten = c.ten
        if (ten in LENH_XOA_SACH && a.any { XOA_SACH.containsMatchIn(it) }) them(CB_XOA_SACH)
        if (ten.startsWith("mkfs") || ten in DINH_DANG) { them(CB_DINH_DANG); return }
        when (ten) {
            "pm" -> xetPm(a, canGiu, them)
            "cmd" -> when (a.getOrNull(0)) {
                "package" -> xetPm(a.drop(1), canGiu, them)
                "wifi" -> if (a.getOrNull(1) == "set-wifi-enabled" && a.getOrNull(2) == "disabled") them(CB_TAT_WIFI)
                "bluetooth_manager" -> if (a.getOrNull(1) == "disable") them(CB_TAT_BT)
                "connectivity" -> if (a.getOrNull(1) == "airplane-mode" && a.getOrNull(2) == "enable") them(CB_MAY_BAY)
                "overlay" -> if (a.getOrNull(1) == "disable") them(CB_OVERLAY)
                "role" -> if (a.getOrNull(1) in setOf("add-role-holder", "remove-role-holder", "clear-role-holders")) them(CB_ROLE)
                "device_config" -> if (a.getOrNull(1) in setOf("put", "delete", "reset")) them(CB_DEVICE_CONFIG)
                // `settings`, `appops`, `ime` chỉ là vỏ của các lệnh cmd này: cùng luật (28/10/2026: cmd settings put global
                // adb_enabled 0 chạy không hỏi).
                "settings" -> xetSettings(boUser(a.drop(1)), docTroNang, them)
                "appops" -> xetAppops(a.drop(1), them)
                "input_method" -> if (a.getOrNull(1) == "ime") xetIme(a.drop(2), them)
            }
            "am" -> if (a.getOrNull(0) == "monitor") them(CB_LIEN_TUC)
            "settings" -> xetSettings(boUser(a), docTroNang, them)
            "wm" -> if (a.getOrNull(0) in setOf("size", "density", "overscan")) {
                val gt = boTuyChon(a.drop(1), setOf("-d"))
                if (gt.isNotEmpty() && gt[0] != "reset") them(when (a[0]) { "size" -> CB_WM_SIZE; "density" -> CB_WM_DENSITY; else -> CB_WM_OVERSCAN })
            }
            "svc" -> when (a.getOrNull(0) to a.getOrNull(1)) {
                "wifi" to "disable" -> them(CB_TAT_WIFI)
                "ethernet" to "disable" -> them(CB_TAT_MANG)
                "bluetooth" to "disable" -> them(CB_TAT_BT)
                "power" to "shutdown" -> them(CB_TAT_HAN)
                "power" to "reboot" -> them(CB_KHOI_DONG_LAI)
            }
            "reboot" -> {
                val gt = a.firstOrNull { !it.startsWith("-") }
                them(when {
                    "-p" in a || gt == "shutdown" -> CB_TAT_HAN
                    gt == null || gt == "userspace" -> CB_KHOI_DONG_LAI
                    else -> CB_CHE_DO
                })
            }
            "setprop" -> a.getOrNull(0)?.let { k ->
                val v = a.getOrNull(1) ?: ""
                them(when {
                    k.startsWith("ctl.") && v == "adbd" -> if (k == "ctl.stop") CB_ADB_TAT else CB_ADB_CONG
                    k == "service.adb.tcp.port" || k == "persist.adb.tcp.port" || k.endsWith("usb.config") -> CB_ADB_CONG
                    k.startsWith("ctl.") -> CB_DICH_VU
                    else -> CB_SETPROP
                })
            }
            "rm" -> {
                val deQuy = a.any { it == "--recursive" || (it.startsWith("-") && !it.startsWith("--") && (it.contains('r') || it.contains('R'))) }
                val dich = a.filterNot { it.startsWith("-") }
                them(when {
                    dich.any { laDuongHeThong(it) } -> CB_HE_THONG
                    dich.any { laDuongRong(it) } && (deQuy || dich.any { '*' in it || '?' in it }) -> CB_RM_RONG
                    else -> CB_RM
                })
                if (dich.any { it.trimEnd('/') == SAO_LUU_TV }) them(CB_RM_SAO_LUU)
            }
            "find" -> if ("-delete" in a || a.windowed(2).any { it[0] == "-exec" && it[1].substringAfterLast('/') == "rm" }) them(CB_FIND_XOA)
            "dd" -> when {
                a.any { it.startsWith("of=/dev/") } -> them(CB_DD)
                a.any { it.startsWith("of=") && laDuongHeThong(it.removePrefix("of=")) } -> them(CB_HE_THONG)
            }
            "sm" -> when (a.getOrNull(0)) {
                "format", "partition" -> them(CB_SM_FORMAT)
                "unmount", "forget" -> them(CB_SM_THAO)
            }
            "mount" -> if (a.any { "remount" in it }) them(CB_REMOUNT)
            "cp" -> if (a.lastOrNull { !it.startsWith("-") }?.let { laDuongHeThong(it) } == true) them(CB_HE_THONG)
            "mv", "chmod", "chown", "chgrp", "chcon", "ln", "touch", "truncate", "tee", "sed", "restorecon", "mkdir", "rmdir" ->
                if (a.any { !it.startsWith("-") && laDuongHeThong(it) }) them(CB_HE_THONG)
            "kill", "killall", "pkill" -> {
                val d = a.filterNot { it.startsWith("-") }
                when {
                    "adbd" in d -> them(CB_ADB_TAT)
                    d.any { it in TIEN_TRINH_LOI } -> them(CB_GIET_LOI)
                }
            }
            "stop" -> them(when { a.isEmpty() -> CB_STOP_ALL; "adbd" in a -> CB_ADB_TAT; else -> CB_DICH_VU })
            "ifconfig" -> if ("down" in a) them(CB_TAT_MANG)
            "ip" -> if (a.any { it == "down" || it == "del" || it == "delete" || it == "flush" }) them(CB_TAT_MANG)
            "iptables", "ip6tables" -> if (a.any { it in SUA_TUONG_LUA }) them(CB_TUONG_LUA)
            "input" -> if (a.getOrNull(0) == "keyevent" && a.drop(1).any { it in PHIM_NGU }) them(CB_NGU)
            "ime" -> xetIme(a, them)
            "appops" -> xetAppops(a, them)
            "content" -> if (a.getOrNull(0) in setOf("insert", "update", "delete")) them(CB_CONTENT)
            "monkey" -> a.lastOrNull()?.toIntOrNull()?.takeIf { it > 1 }?.let { n ->
                them(CanhBao("monkey", MucDo.CAN_THAN, "Monkey sẽ bấm ngẫu nhiên $n lần: có thể bấm lung tung vào cài đặt, xoá dữ liệu, mua hàng. Để mở app chỉ cần 1.",
                    "Monkey will send $n random events: it can poke at settings, delete data or buy things. To open an app, 1 is enough."))
            }
            "logcat" -> if (a.none { it in setOf("-d", "-c", "-g", "-S", "-L", "--clear", "--last", "--help") || it.startsWith("-t") || it.startsWith("--dump") }) them(CB_LIEN_TUC)
            "ping", "ping6" -> if (a.none { it.startsWith("-c") || it.startsWith("-w") }) them(CB_PING)
            "tail" -> if (a.any { it == "--follow" || (it.startsWith("-") && !it.startsWith("--") && (it.contains('f') || it.contains('F'))) }) them(CB_LIEN_TUC)
            "getevent" -> if (a.none { it.startsWith("-c") }) them(CB_LIEN_TUC)
            "top" -> if (a.none { it.startsWith("-") && !it.startsWith("--") && it.contains('n') }) them(CB_LIEN_TUC)
            "watch", "yes" -> them(CB_LIEN_TUC)
            "vi", "vim", "nano", "htop", "ssh", "telnet", "ftp", "python", "python3" -> them(CB_TUONG_TAC)
            "sh", "mksh", "bash", "su" -> if (a.isEmpty()) them(CB_TUONG_TAC)
            "sqlite3" -> if (a.size < 2) them(CB_TUONG_TAC)
            "adb" -> them(canhBaoAdb(boTuyChon(a, setOf("-s", "-t", "-H", "-P", "-L")).firstOrNull()))
            "fastboot" -> them(CB_FASTBOOT)
        }
    }

    private fun xetPm(a: List<String>, canGiu: (String) -> Boolean, them: (CanhBao) -> Unit) {
        val lenh = a.getOrNull(0) ?: return
        val goi = goiTrong(a.drop(1))
        fun loi() = goi.map { it.substringBefore('/') }.filter { canGiu(it) }.forEach { p ->
            them(CanhBao("goi-loi:$p", MucDo.NGUY_HIEM,
                "$p là gói tivi cần để chạy (màn Home, Cài đặt, bàn phím, lõi Android…). Tắt, gỡ hay xoá dữ liệu nó có thể làm mất màn hình chính, treo ở logo khi khởi động, mất cả ADB.",
                "$p is a package the TV needs (Home, Settings, keyboard, Android core…). Disabling, removing or clearing it can leave no Home screen, hang the boot logo or cut ADB."))
        }
        fun tiviKit() = goi.filter { it.startsWith("vn.tivikit") }.forEach { p ->
            them(CanhBao("tivikit:$p", MucDo.BAO,
                "$p là app phụ của TiviKit (Kênh nhanh, Hẹn giờ, Khoá trẻ em, bộ gõ): tắt hoặc gỡ thì tính năng đó ngừng chạy.",
                "$p is a TiviKit helper (Quick channel, Timer, Kids lock, typing): disabling or removing it stops that feature."))
        }
        when (lenh) {
            "disable", "disable-user", "disable-until-used" -> {
                them(if (goi.any { TEN_GOI.matches(it) }) CB_PM_TAT else CB_PM_TAT_NHIEU)
                loi(); tiviKit()
            }
            "uninstall" -> {
                them(if ("--user" in a) CB_PM_GO_USER else CB_PM_GO)
                loi(); tiviKit()
            }
            "clear" -> { them(CB_PM_XOA); loi() }
            "hide", "suspend" -> { them(CB_PM_AN); loi() }
            "set-home-activity" -> them(CB_PM_HOME)
            "revoke", "reset-permissions" -> them(CB_PM_THU_QUYEN)
            "remove-user" -> them(CB_PM_XOA_USER)
            "uninstall-system-updates" -> them(CB_PM_GO_CAP_NHAT)
            "bg-dexopt-job" -> them(CB_PM_TOI_UU)
        }
    }

    /** Tên gói trong đối số của pm: bỏ tuỳ chọn, --user / --versionCode kèm số. */
    private fun goiTrong(a: List<String>): List<String> {
        val ra = mutableListOf<String>()
        var i = 0
        while (i < a.size) {
            val s = a[i]
            if (s == "--user" || s == "--versionCode") i++ else if (!s.startsWith("-")) ra += s
            i++
        }
        return ra
    }

    private fun xetIme(a: List<String>, them: (CanhBao) -> Unit) {
        if (a.getOrNull(0) in setOf("set", "disable", "reset")) them(CB_BAN_PHIM)
    }

    private fun xetAppops(a: List<String>, them: (CanhBao) -> Unit) {
        if (a.getOrNull(0) == "reset" || (a.getOrNull(0) == "set" && a.lastOrNull() in setOf("deny", "ignore", "errored"))) them(CB_APPOPS)
    }

    private fun xetSettings(a: List<String>, docTroNang: Boolean, them: (CanhBao) -> Unit) {
        val lenh = a.getOrNull(0) ?: return
        if (lenh == "reset") { them(CB_SETTINGS_RESET); return }
        if (lenh != "put" && lenh != "delete") return
        val khoa = a.getOrNull(2) ?: return
        val gt = a.getOrNull(3)
        val tat = lenh == "delete" || gt == "0"
        when (khoa) {
            "adb_enabled", "adb_wifi_enabled" -> if (tat) them(CB_ADB_TAT)
            "development_settings_enabled" -> if (tat) them(CB_DEV_TAT)
            "airplane_mode_on" -> if (gt == "1") them(CB_MAY_BAY)
            "http_proxy", "global_http_proxy_host", "global_http_proxy_port" -> if (lenh == "put" && gt != ":0") them(CB_PROXY)
            "private_dns_mode", "private_dns_specifier" -> them(CB_DNS)
            "enabled_accessibility_services" -> if (!docTroNang) them(CB_TRO_NANG)
            "accessibility_enabled" -> if (tat) them(CB_TRO_NANG)
            "device_provisioned", "user_setup_complete" -> if (tat) them(CB_CHUA_CAI_DAT)
            "default_input_method", "enabled_input_methods" -> them(CB_BAN_PHIM)
            "package_verifier_enable", "verifier_verify_adb_installs" -> if (gt == "0") them(CB_PLAY_PROTECT)
            "font_scale" -> gt?.toFloatOrNull()?.let { if (it < 0.5f || it > 2f) them(CB_CO_CHU) }
        }
    }

    private fun canhBaoAdb(lenh: String?): CanhBao {
        val (vi, en) = when (lenh) {
            "install", "install-multiple", "install-multi-package" -> "Cài app: dùng mục Cài app của TiviKit, hoặc pm install với file APK đã có trên tivi." to
                "To install apps use TiviKit's Install apps, or pm install with an APK already on the TV."
            "push", "pull", "sync" -> "Chép file lên tivi: dùng Gửi ảnh, video của TiviKit." to "To copy files to the TV, use TiviKit's Send photos, videos."
            "reboot" -> "Khởi động lại: gõ reboot (bỏ chữ adb)." to "To restart, type reboot (without adb)."
            "devices", "connect", "disconnect", "pair", "tcpip", "usb", "kill-server", "start-server", "root", "unroot" ->
                "TiviKit đã tự nối với tivi, không cần lệnh này." to "TiviKit is already connected; this isn't needed."
            else -> "Ở đây chỉ gõ phần sau chữ \"adb shell\"." to "Here, type only what comes after \"adb shell\"."
        }
        return CanhBao("adb", MucDo.KHONG_CHAY, "Lệnh adb chạy trên máy tính, không chạy trên tivi. $vi", "adb commands run on a computer, not on the TV. $en")
    }

    private val LENH_XOA_SACH = setOf("am", "cmd", "recovery", "echo", "printf", "setprop", "uncrypt", "vdc", "wipe")
    private val XOA_SACH = Regex("(?i)wipe[_-]?data|master_clear|factory_reset|factoryreset|--wipe")
    private val DINH_DANG = setOf("mke2fs", "make_ext4fs", "make_f2fs", "newfs_msdos", "blkdiscard", "sgdisk", "fdisk", "parted", "flash_image", "erase_image")
    private val TIEN_TRINH_LOI = setOf("system_server", "zygote", "zygote64", "surfaceflinger", "servicemanager", "init", "1")
    private val SUA_TUONG_LUA = setOf("-F", "-A", "-I", "-D", "-P", "-X", "--flush", "--append", "--insert", "--delete", "--policy")
    private val PHIM_NGU = setOf("KEYCODE_POWER", "POWER", "26", "KEYCODE_SLEEP", "SLEEP", "223", "KEYCODE_SOFT_SLEEP", "276")

    /** Phân vùng hệ thống: chỉ máy root mới ghi được, ghi sai là hỏng khởi động. */
    private val DUONG_HE_THONG = listOf("/system", "/vendor", "/product", "/odm", "/oem", "/apex", "/boot", "/dev/block", "/system_ext", "/persist", "/firmware", "/metadata", "/cache/recovery")
    private fun laDuongHeThong(p: String) = DUONG_HE_THONG.any { p == it || p.startsWith("$it/") }

    private val DUONG_RONG = setOf("", "/sdcard", "/storage", "/storage/emulated", "/storage/emulated/0", "/storage/self/primary", "/mnt", "/mnt/sdcard",
        "/data", "/data/media", "/data/media/0", "/data/data", "/data/app", "/data/local", "/data/local/tmp")
    private val GOC_BO_NHO = listOf("/sdcard/", "/storage/emulated/0/", "/storage/self/primary/", "/mnt/sdcard/", "/data/media/0/")
    private val THU_MUC_CHUNG = setOf("Download", "DCIM", "Pictures", "Movies", "Music", "Documents", "Android", "Podcasts", "Alarms", "Notifications", "Ringtones", "Recordings")

    /** Đường dẫn xoá là mất hàng loạt: bộ nhớ trong, thư mục chung (Download, ảnh…), dữ liệu app, ., ~, biến, hay * ngay trong các chỗ đó. */
    private fun laDuongRong(p: String): Boolean {
        val s = p.trimEnd('/')
        if ('*' in s || '?' in s) {
            if ('/' !in s) return true
            val thu = s.substringBeforeLast('/')
            return '*' in thu || '?' in thu || laDuongRong(thu)
        }
        if (s in DUONG_RONG || s == "." || s == ".." || s.startsWith("~") || s.startsWith("$")) return true
        val goc = GOC_BO_NHO.firstOrNull { s.startsWith(it) } ?: return false
        return s.removePrefix(goc) in THU_MUC_CHUNG
    }

    private val CB_HE_THONG = CanhBao("he-thong", MucDo.NGUY_HIEM,
        "Ghi / sửa file trong phân vùng hệ thống (/system, /vendor…, máy đã root): sai một file là tivi có thể không khởi động được nữa.",
        "Writes to the system partition (/system, /vendor…, rooted devices): one wrong file can stop the TV from booting.")
    private val CB_XOA_SACH = CanhBao("xoa-sach", MucDo.NGUY_HIEM,
        "Lệnh này có thể xoá sạch tivi về như lúc mới mua: mất mọi app, tài khoản, cài đặt và file.",
        "This can factory-reset the TV: every app, account, setting and file is erased.", matKetNoi = true)
    private val CB_DINH_DANG = CanhBao("dinh-dang", MucDo.NGUY_HIEM,
        "Định dạng / xoá phân vùng: mất toàn bộ dữ liệu trên đó, có thể hỏng tivi.",
        "Formats or erases a partition: everything on it is lost, and the TV may be bricked.")
    private val CB_TAT_WIFI = CanhBao("tat-wifi", MucDo.NGUY_HIEM,
        "Tắt Wi-Fi: TiviKit đang nối qua Wi-Fi sẽ mất kết nối ngay; phải dùng remote vào Cài đặt bật lại Wi-Fi.",
        "Turns Wi-Fi off: if TiviKit connects over Wi-Fi it loses the TV at once; turn Wi-Fi back on in Settings with the remote.", matKetNoi = true)
    private val CB_TAT_MANG = CanhBao("tat-mang", MucDo.NGUY_HIEM,
        "Tắt cổng mạng: nếu TiviKit đang đi qua cổng này thì mất kết nối, thường phải khởi động lại tivi mạng mới lên lại.",
        "Takes a network interface down: if TiviKit goes through it the link drops, usually until the TV restarts.", matKetNoi = true)
    private val CB_TAT_BT = CanhBao("tat-bt", MucDo.CAN_THAN,
        "Tắt Bluetooth: remote Bluetooth (loại có nút mic), loa, tai nghe ngừng chạy. Vẫn điều khiển được bằng Điều khiển + gõ chữ của TiviKit.",
        "Turns Bluetooth off: Bluetooth remotes (the ones with a mic), speakers and headphones stop working. TiviKit's Remote + typing still works.")
    private val CB_MAY_BAY = CanhBao("may-bay", MucDo.NGUY_HIEM,
        "Chế độ máy bay tắt Wi-Fi và Bluetooth: TiviKit mất kết nối, remote Bluetooth ngừng chạy.",
        "Airplane mode turns Wi-Fi and Bluetooth off: TiviKit loses the TV and Bluetooth remotes stop working.", matKetNoi = true)
    private val CB_OVERLAY = CanhBao("overlay", MucDo.CAN_THAN,
        "Tắt một lớp phủ giao diện của hệ thống: có thể làm vỡ giao diện hoặc mất chữ tiếng Việt.",
        "Disables a system UI overlay: layouts or translations may break.")
    private val CB_ROLE = CanhBao("role", MucDo.CAN_THAN,
        "Đổi app mặc định (Home, trình duyệt…): chọn sai thì nút Home hoặc link không mở đúng app.",
        "Changes a default app (Home, browser…): a wrong pick breaks the Home button or links.")
    private val CB_DEVICE_CONFIG = CanhBao("device-config", MucDo.CAN_THAN,
        "Đổi cấu hình ẩn của hệ thống: hiệu quả khó đoán, có khi chỉ mất đi sau khi khởi động lại.",
        "Changes hidden system flags: effects are hard to predict and may only clear after a restart.")
    private val CB_ADB_TAT = CanhBao("adb-tat", MucDo.NGUY_HIEM,
        "Tắt ADB: TiviKit mất kết nối ngay và không tự nối lại được; phải vào Tuỳ chọn nhà phát triển trên tivi bật lại Gỡ lỗi. Muốn khoá ADB cho an toàn thì dùng nút Tắt ADB ở màn chính.",
        "Turns ADB off: TiviKit loses the TV at once and can't reconnect until debugging is turned back on in Developer options. To lock ADB safely, use Turn off ADB on the main screen.", matKetNoi = true)
    private val CB_DEV_TAT = CanhBao("dev-tat", MucDo.NGUY_HIEM,
        "Tắt Tuỳ chọn nhà phát triển: nhiều tivi tắt luôn ADB, TiviKit mất kết nối.",
        "Turns Developer options off: many TVs switch ADB off too, and TiviKit loses the TV.", matKetNoi = true)
    private val CB_ADB_CONG = CanhBao("adb-cong", MucDo.CAN_THAN,
        "Khởi động lại / đổi cổng ADB: kết nối đang dùng có thể rớt; nối lại thì phải dùng đúng cổng mới.",
        "Restarts ADB or changes its port: the current link may drop; reconnect on the new port.", matKetNoi = true)
    private val CB_KHOI_DONG_LAI = CanhBao("khoi-dong-lai", MucDo.CAN_THAN,
        "Tivi khởi động lại, mất kết nối 1–3 phút; bật Tự nối lại thì app tự nối khi tivi lên. Nút Khởi động lại ở màn chính lo việc này trọn gói.",
        "The TV restarts and the link drops for 1–3 minutes; with Auto-reconnect on, the app reconnects when it's back. The Reboot button on the main screen handles all of this.", matKetNoi = true)
    private val CB_TAT_HAN = CanhBao("tat-han", MucDo.NGUY_HIEM,
        "Tắt hẳn tivi: TiviKit mất kết nối, phải bật lại bằng nút nguồn hoặc remote.",
        "Shuts the TV down: TiviKit loses it, and it has to be turned on with the power button or remote.", matKetNoi = true)
    private val CB_CHE_DO = CanhBao("che-do-dac-biet", MucDo.NGUY_HIEM,
        "Tivi vào chế độ đặc biệt (recovery, bootloader…): có thể không tự về màn hình thường, phải rút điện hoặc dùng nút trên tivi. Bấm nhầm trong recovery là xoá sạch tivi.",
        "The TV boots into a special mode (recovery, bootloader…): it may not come back by itself and need unplugging or its buttons. One wrong choice in recovery wipes the TV.", matKetNoi = true)
    private val CB_DICH_VU = CanhBao("dich-vu-he-thong", MucDo.CAN_THAN,
        "Bật / tắt dịch vụ hệ thống: tắt nhầm có thể làm tivi treo tới khi khởi động lại.",
        "Starts or stops a system service: stopping the wrong one can freeze the TV until it restarts.")
    private val CB_SETPROP = CanhBao("setprop", MucDo.CAN_THAN,
        "Đổi thuộc tính hệ thống: phần lớn bị Android chặn với quyền ADB; thuộc tính persist.* giữ cả sau khi khởi động lại.",
        "Changes a system property: most are blocked over ADB; persist.* ones survive restarts.")
    private val CB_RM = CanhBao("rm", MucDo.CAN_THAN,
        "Xoá file, không có thùng rác: xoá rồi là mất.", "Deletes files with no recycle bin: once gone, they're gone.")
    private val CB_RM_RONG = CanhBao("rm-rong", MucDo.NGUY_HIEM,
        "Xoá cả thư mục lớn (bộ nhớ trong, Download, ảnh, phim, dữ liệu app…) hoặc xoá theo mẫu * ở đó: mất hàng loạt file, không lấy lại được.",
        "Deletes a whole big folder (internal storage, Download, photos, movies, app data…) or everything matching * there: loads of files gone for good.")
    private val CB_RM_SAO_LUU = CanhBao("rm-sao-luu", MucDo.CAN_THAN,
        "Đây là bản sao lưu TiviKit cất trên tivi: xoá rồi thì đổi điện thoại hoặc cài lại TiviKit sẽ không Khôi phục tivi được.",
        "This is TiviKit's backup kept on the TV: without it, a new phone or a reinstalled TiviKit can't restore the TV.")
    private val CB_FIND_XOA = CanhBao("find-xoa", MucDo.CAN_THAN,
        "Xoá mọi file tìm được, không có thùng rác.", "Deletes every file it finds, with no recycle bin.")
    private val CB_DD = CanhBao("dd-phan-vung", MucDo.NGUY_HIEM,
        "Ghi thẳng vào phân vùng / ổ đĩa: có thể làm tivi không khởi động được nữa, hoặc xoá sạch ổ.",
        "Writes straight to a partition or disk: it can leave the TV unbootable or wipe the drive.")
    private val CB_SM_FORMAT = CanhBao("sm-format", MucDo.NGUY_HIEM,
        "Định dạng ổ (USB, thẻ nhớ hoặc bộ nhớ trong): mất hết dữ liệu trên ổ đó.",
        "Formats a drive (USB, card or internal): everything on it is lost.")
    private val CB_SM_THAO = CanhBao("sm-thao", MucDo.CAN_THAN,
        "Tháo / quên ổ lưu trữ: app đang dùng file trên đó có thể lỗi; quên ổ đã gộp vào bộ nhớ trong là mất dữ liệu trên ổ.",
        "Unmounts or forgets a drive: apps using it may break; forgetting adopted storage loses its data.")
    private val CB_REMOUNT = CanhBao("remount", MucDo.NGUY_HIEM,
        "Mở ghi phân vùng hệ thống (máy đã root): sửa sai một file là tivi có thể không khởi động được.",
        "Makes the system partition writable (rooted devices): one wrong edit can stop the TV from booting.")
    private val CB_GIET_LOI = CanhBao("giet-loi", MucDo.CAN_THAN,
        "Dừng tiến trình lõi của Android: giao diện khởi động lại (màn đen vài giây tới một phút), có tivi khởi động lại hẳn.",
        "Kills a core Android process: the interface restarts (black screen for seconds to a minute), and some TVs reboot.")
    private val CB_STOP_ALL = CanhBao("stop-all", MucDo.NGUY_HIEM,
        "Dừng toàn bộ Android (máy đã root): màn hình đen tới khi khởi động lại tivi.",
        "Stops all of Android (rooted devices): the screen stays black until the TV restarts.")
    private val CB_TUONG_LUA = CanhBao("tuong-lua", MucDo.CAN_THAN,
        "Sửa tường lửa (máy đã root): chặn nhầm là mất mạng hoặc mất kết nối ADB.",
        "Changes the firewall (rooted devices): a wrong rule cuts the network or ADB.", matKetNoi = true)
    private val CB_NGU = CanhBao("ngu", MucDo.BAO,
        "Tivi sẽ ngủ: nhiều tivi tắt Wi-Fi khi ngủ nên TiviKit mất kết nối tới khi bật lại.",
        "The TV goes to sleep: many TVs drop Wi-Fi while asleep, so TiviKit loses the link until it wakes.", matKetNoi = true)
    private val CB_BAN_PHIM = CanhBao("ban-phim", MucDo.CAN_THAN,
        "Đổi bàn phím: sai tên là tivi không gõ được chữ (ô tìm kiếm, mật khẩu Wi-Fi). Xem tên đúng bằng ime list -s.",
        "Changes the keyboard: a wrong name leaves the TV unable to type (search, Wi-Fi passwords). See valid names with ime list -s.")
    private val CB_APPOPS = CanhBao("appops-chan", MucDo.CAN_THAN,
        "Chặn quyền đặc biệt của app: app có thể lỗi ở phần cần quyền đó.", "Blocks a special permission: the app may break where it needs it.")
    private val CB_CONTENT = CanhBao("content", MucDo.CAN_THAN,
        "Sửa thẳng dữ liệu của app hệ thống (cài đặt, danh bạ…): sai là app hoặc tivi lỗi.",
        "Edits a system app's data directly (settings, contacts…): mistakes break the app or the TV.")
    private val CB_LIEN_TUC = CanhBao("chay-lien-tuc", MucDo.BAO,
        "Lệnh chạy liên tục: bấm Dừng khi đủ.", "This keeps running: press Stop when you have enough.")
    private val CB_PING = CanhBao("ping-mai", MucDo.BAO,
        "ping chạy mãi tới khi bấm Dừng; thêm -c 4 để tự dừng sau 4 lần.", "ping runs until you press Stop; add -c 4 to stop after 4.")
    private val CB_TUONG_TAC = CanhBao("tuong-tac", MucDo.KHONG_CHAY,
        "Lệnh này chờ gõ tiếp (trình soạn chữ, shell lồng, su…), mà màn này không gõ tương tác được nên lệnh sẽ đứng im. Gõ cả lệnh trên một dòng, VD su -c 'lệnh', sh -c 'lệnh', hoặc cat thay cho vi.",
        "This waits for interactive input (editors, nested shells, su…), which this screen can't give, so it would just hang. Put the whole command on one line, e.g. su -c 'command', sh -c 'command', or cat instead of vi.")
    private val CB_FASTBOOT = CanhBao("fastboot", MucDo.KHONG_CHAY,
        "fastboot chạy trên máy tính khi tivi ở chế độ bootloader, không chạy được ở đây.",
        "fastboot runs on a computer while the TV is in bootloader mode; it can't run here.")
    private val CB_PM_TAT = CanhBao("pm-tat", MucDo.CAN_THAN,
        "Tắt gói: app biến khỏi tivi và không chạy nữa, dữ liệu vẫn giữ. TiviKit ghi vào bản sao lưu để Khôi phục tivi bật lại được.",
        "Disables a package: the app disappears and stops running, its data is kept. TiviKit records it in the backup so Restore can turn it back on.")
    private val CB_PM_TAT_NHIEU = CanhBao("pm-tat-nhieu", MucDo.CAN_THAN,
        "Không rõ gói nào sẽ bị tắt (tên gói nằm trong biến, vòng lặp hoặc lấy từ lệnh khác): kiểm tra kỹ trước khi chạy. Gói tắt kiểu này không được ghi vào bản sao lưu.",
        "Can't tell which packages get disabled (names come from a variable, a loop or another command): check carefully. Packages disabled this way aren't recorded in the backup.")
    private val CB_PM_GO_USER = CanhBao("pm-go-user", MucDo.CAN_THAN,
        "Gỡ gói khỏi người dùng: app biến mất như bị xoá (app hệ thống vẫn nằm trong máy). Khôi phục tivi KHÔNG tự cài lại được; cài lại bằng: pm install-existing <gói>. An toàn hơn: tắt gói (pm disable-user) thay vì gỡ.",
        "Removes the package for the user: the app vanishes (a system app stays in the firmware). Restore can NOT bring it back; reinstall with: pm install-existing <package>. Safer: disable it (pm disable-user) instead.")
    private val CB_PM_GO = CanhBao("pm-go", MucDo.CAN_THAN,
        "Gỡ app và xoá luôn dữ liệu của nó (đăng nhập, cài đặt trong app). App hệ thống thì chỉ gỡ các bản cập nhật.",
        "Uninstalls the app and deletes its data (sign-ins, in-app settings). For a system app only its updates are removed.")
    private val CB_PM_XOA = CanhBao("pm-xoa-du-lieu", MucDo.CAN_THAN,
        "Xoá toàn bộ dữ liệu app như lúc mới cài: mất đăng nhập, cài đặt, file tải trong app. Không lấy lại được.",
        "Wipes all of the app's data as if freshly installed: sign-ins, settings and downloads are gone for good.")
    private val CB_PM_AN = CanhBao("pm-an", MucDo.CAN_THAN,
        "Ẩn / tạm khoá app: app không mở được tới khi bỏ (pm unhide / pm unsuspend). Không có trong bản sao lưu nên Khôi phục tivi không bỏ.",
        "Hides or suspends the app: it can't be opened until undone (pm unhide / pm unsuspend). It's not in the backup, so Restore won't undo it.")
    private val CB_PM_HOME = CanhBao("pm-home", MucDo.CAN_THAN,
        "Đổi màn Home mặc định. Chọn sai màn thì bấm Home không về được màn chính; đổi lại bằng chính lệnh này với Home cũ (lệnh mẫu \"Màn Home mặc định đang dùng\" cho biết tên).",
        "Changes the default Home. Pick the wrong activity and the Home button stops working; switch back with this command and the old Home (the \"default Home app in use\" example shows its name).")
    private val CB_PM_THU_QUYEN = CanhBao("pm-thu-quyen", MucDo.CAN_THAN,
        "Thu hồi quyền: app có thể lỗi hoặc ngừng phần cần quyền đó.", "Revokes permissions: the app may break or lose the features that need them.")
    private val CB_PM_XOA_USER = CanhBao("pm-xoa-user", MucDo.NGUY_HIEM,
        "Xoá người dùng cùng toàn bộ app và dữ liệu của người đó.", "Deletes a user with all of their apps and data.")
    private val CB_PM_GO_CAP_NHAT = CanhBao("pm-go-cap-nhat", MucDo.CAN_THAN,
        "Gỡ bản cập nhật của app hệ thống, đưa về bản gốc của hãng (mất tính năng mới, có thể mất dữ liệu).",
        "Removes system app updates, back to the factory versions (new features go, and data may too).")
    private val CB_PM_TOI_UU = CanhBao("pm-toi-uu", MucDo.BAO,
        "Chạy vài phút, tivi chậm trong lúc chạy.", "Takes a few minutes; the TV is slow meanwhile.")
    private val CB_SETTINGS_RESET = CanhBao("settings-reset", MucDo.NGUY_HIEM,
        "Đặt lại hàng loạt cài đặt về mặc định: có thể tắt luôn Gỡ lỗi (mất kết nối), đổi Home, DNS, dịch vụ trợ năng.",
        "Resets a whole batch of settings: it can switch debugging off (losing the link) and change Home, DNS and accessibility services.", matKetNoi = true)
    private val CB_PROXY = CanhBao("proxy", MucDo.CAN_THAN,
        "Đặt proxy: proxy sai hoặc đã tắt là mọi app trên tivi mất mạng. Bỏ proxy: settings put global http_proxy :0",
        "Sets a proxy: a wrong or dead proxy cuts every app off the internet. To remove it: settings put global http_proxy :0")
    private val CB_DNS = CanhBao("dns", MucDo.BAO,
        "Đổi DNS riêng: tên máy chủ sai là tivi mất mạng. Màn DNS chặn quảng cáo của TiviKit kiểm tra máy chủ trước khi đổi.",
        "Changes private DNS: a wrong server name cuts the TV off. TiviKit's Ad-blocking DNS screen checks the server first.")
    private val CB_TRO_NANG = CanhBao("tro-nang", MucDo.CAN_THAN,
        "Lệnh này thay cả danh sách dịch vụ trợ năng: dịch vụ của Home sạch (nút remote), Hẹn giờ, Khoá trẻ em đang bật sẽ bị tắt. Muốn thêm mà giữ cái cũ thì dùng lệnh mẫu bật dịch vụ trợ năng ở nhóm Cấp quyền cho app.",
        "This replaces the whole accessibility service list: the clean Home (remote buttons), Timer and Kids lock services get switched off. To add one and keep the rest, use the accessibility example under App permissions.")
    private val CB_CHUA_CAI_DAT = CanhBao("chua-cai-dat", MucDo.NGUY_HIEM,
        "Tivi sẽ coi như chưa cài đặt xong: nút Home, thông báo, cài đặt có thể bị khoá. Đổi lại bằng giá trị 1.",
        "The TV will think setup isn't finished: Home, notifications and settings may be locked. Set it back to 1.")
    private val CB_PLAY_PROTECT = CanhBao("play-protect", MucDo.CAN_THAN,
        "Tắt bước quét app trước khi cài (Play Protect): tivi dễ dính app độc hơn.",
        "Turns off app scanning before install (Play Protect): malware gets in more easily.")
    private val CB_CO_CHU = CanhBao("co-chu", MucDo.CAN_THAN,
        "Cỡ chữ quá to hoặc quá nhỏ làm vỡ giao diện, khó đọc để sửa lại. Trả lại: settings put system font_scale 1.0",
        "A very large or small font scale breaks layouts and is hard to undo on screen. Reset: settings put system font_scale 1.0")
    private val CB_WM_SIZE = CanhBao("wm-size", MucDo.CAN_THAN,
        "Đổi độ phân giải giao diện: sai thì hình méo, chữ tràn hoặc không bấm tới được. Trả lại: wm size reset",
        "Changes the UI resolution: wrong values stretch the picture or push things off screen. Undo: wm size reset")
    private val CB_WM_DENSITY = CanhBao("wm-density", MucDo.CAN_THAN,
        "Đổi mật độ điểm ảnh: sai thì chữ, nút quá to hoặc quá nhỏ, có app không mở được. Trả lại: wm density reset",
        "Changes pixel density: wrong values make text and buttons huge or tiny, and some apps won't open. Undo: wm density reset")
    private val CB_WM_OVERSCAN = CanhBao("wm-overscan", MucDo.CAN_THAN,
        "Đổi viền hiển thị (overscan): sai thì mất mép màn hình. Trả lại: wm overscan reset",
        "Changes overscan: wrong values cut off the screen edges. Undo: wm overscan reset")

    // ---------- Ghi vào bản sao lưu, in giá trị cũ ----------

    /** Lệnh pm trong [lenh] (kể cả cmd package): lệnh con → đối số. */
    private fun lenhPm(lenh: String): List<Pair<String, List<String>>> = cacLenh(lenh).mapNotNull { c ->
        when {
            c.ten == "pm" -> c.doi
            c.ten == "cmd" && c.doi.getOrNull(0) == "package" -> c.doi.drop(1)
            else -> null
        }?.takeIf { it.isNotEmpty() }?.let { it[0] to it.drop(1) }
    }

    private fun goiCua(lenh: String, dung: (String, List<String>) -> Boolean): List<String> =
        lenhPm(lenh).filter { (l, a) -> dung(l, a) }.flatMap { goiTrong(it.second) }.filter { TEN_GOI.matches(it) }.distinct()

    /** Gói [lenh] tắt bằng pm disable… — chỉ tên gói viết rõ (tắt một thành phần gói/lớp thì gói vẫn chạy, không tính). */
    fun goiTat(lenh: String): List<String> = goiCua(lenh) { l, _ -> l == "disable" || l == "disable-user" || l == "disable-until-used" }

    /** Gói [lenh] bật lại bằng pm enable. */
    fun goiBat(lenh: String): List<String> = goiCua(lenh) { l, _ -> l == "enable" }

    /** Gói [lenh] gỡ khỏi người dùng (pm uninstall --user): Khôi phục không cài lại được, báo cách cài lại. */
    fun goiGo(lenh: String): List<String> = goiCua(lenh) { l, a -> l == "uninstall" && "--user" in a }

    /** Cài đặt [lenh] ghi / xoá (settings put|delete <bảng> <khoá>): in giá trị cũ ra trước khi chạy. */
    fun caiDatDoi(lenh: String): List<Pair<String, String>> = cacLenh(lenh).mapNotNull { c ->
        when {
            c.ten == "settings" -> c.doi
            c.ten == "cmd" && c.doi.firstOrNull() == "settings" -> c.doi.drop(1)
            else -> null
        }
    }.mapNotNull { doi ->
        val a = boUser(doi)
        val khoa = a.getOrNull(2)
        if (a.getOrNull(0) in setOf("put", "delete") && a.getOrNull(1) in setOf("global", "secure", "system") && khoa != null && KHOA_CAI_DAT.matches(khoa)) a[1] to khoa else null
    }.distinct()

    // ---------- Bọc lệnh, tách chữ in ra ----------

    /**
     * Lệnh gửi lên tivi cho [lenh] người dùng gõ: chạy trong [thuMuc] bằng một sh riêng (lệnh lỗi cú pháp, exit không làm
     * mất phần sau), in "<dau>P<pid>" trước, "<dau>D<thư mục>" và "<dau>X<mã thoát>" sau cùng. Mỗi dấu một dòng, có `echo`
     * trống đứng trước nên luôn ở đầu dòng dù lệnh in dở dòng. Thư mục giữ qua các lệnh: cd ở lệnh trước vẫn còn.
     */
    fun boc(lenh: String, thuMuc: String, dau: String): String {
        val trong = "echo \"${dau}P\$\$\"\n$lenh\n__r=\$?; echo; echo \"${dau}D\$PWD\"; exit \$__r"
        // Giữ mã thoát của sh -c trước khi echo (28/10/2026: trước đây in $? của echo, mã thoát luôn 0).
        return "cd ${nhay(thuMuc)} 2>/dev/null; sh -c ${nhay(trong)} 2>&1; __x=\$?; echo; echo \"${dau}X\$__x\""
    }

    /**
     * Tách chữ tivi gửi về của lệnh đã bọc (boc): bỏ các dòng dấu, giữ PID / thư mục / mã thoát. Chữ đến từng mẩu: mẩu dở
     * cuối mà có thể là đầu một dòng dấu thì giữ lại chờ mẩu sau, còn lại đưa ra ngay (ping, logcat hiện tức thì). Dòng
     * trống ngay trước dòng dấu D / X là của `echo` trong boc: bỏ đúng một dòng.
     */
    class BoTach(private val dau: String) {
        /** Đọc từ luồng chính khi bấm Dừng, ghi từ luồng đọc. */
        @Volatile var pid: String? = null
            private set
        var thuMuc: String? = null
            private set
        var maThoat: Int? = null
            private set
        val xong: Boolean get() = maThoat != null

        private val cho = StringBuilder()
        /** Số dòng trống đang giữ (chưa biết của lệnh hay của echo trong boc). */
        private var trong = 0
        /** Đã đưa ra đầu dòng hiện tại, chưa tới hết dòng. */
        private var dangDo = false

        /** Nạp một mẩu chữ, trả phần đưa ra màn hình được. */
        fun nap(manh: String): String {
            if (xong) return ""
            cho.append(manh.replace("\r", ""))
            val ra = StringBuilder()
            while (!xong) {
                val i = cho.indexOf("\n")
                if (i < 0) {
                    if (cho.isEmpty() || (!dangDo && (dau.startsWith(cho) || cho.startsWith(dau)))) break
                    if (!dangDo) xaTrong(ra)
                    ra.append(cho); cho.setLength(0); dangDo = true
                    break
                }
                val dong = cho.substring(0, i)
                cho.delete(0, i + 1)
                when {
                    dangDo -> { ra.append(dong).append('\n'); dangDo = false }
                    dong.startsWith(dau) && dong.length > dau.length -> {
                        val gt = dong.substring(dau.length + 1)
                        when (dong[dau.length]) {
                            'P' -> pid = gt.takeIf { s -> s.isNotEmpty() && s.all { it.isDigit() } }
                            'D' -> { if (trong > 0) trong--; thuMuc = gt.ifEmpty { null } }
                            'X' -> { if (trong > 0) trong--; maThoat = gt.trim().toIntOrNull() ?: -1 }
                        }
                        xaTrong(ra)
                    }
                    dong.isEmpty() -> trong++
                    else -> { xaTrong(ra); ra.append(dong).append('\n') }
                }
            }
            return ra.toString()
        }

        /** Luồng đã đóng trước dấu kết thúc (Dừng, mất kết nối): đưa nốt phần còn giữ. */
        fun ket(): String {
            val ra = StringBuilder()
            if (!xong) { xaTrong(ra); ra.append(cho) }
            cho.setLength(0)
            return ra.toString()
        }

        private fun xaTrong(ra: StringBuilder) {
            repeat(trong) { ra.append('\n') }
            trong = 0
        }
    }
}
