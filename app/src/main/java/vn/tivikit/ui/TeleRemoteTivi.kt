package vn.tivikit.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import vn.tivikit.dulieu.TeleRemote
import vn.tivikit.t

/**
 * Tele Remote (31/10/2026): cài app phụ vn.tivikit.teleremote lên tivi, nối bot Telegram riêng của tivi (token của
 * @BotFather), chọn ai được điều khiển tivi từ Telegram (chủ / thành viên), duyệt người xin dùng, nhắn thử, xem nhật ký.
 * Đổi người được dùng thì gửi lên tivi ngay (người đang chờ được duyệt khỏi chờ).
 */
class TeleRemoteTivi(private val vm: PhienLamViec) {
    var trangThai by mutableStateOf<TeleRemote.TrangThai?>(null)
        private set
    /** App phụ đã có trên tivi. */
    var daCai by mutableStateOf(false)
        private set
    var tokenNhap by mutableStateOf("")
    /** Đang đổi sang bot khác (hiện lại ô dán token). */
    var doiBot by mutableStateOf(false)
    var tenNhap by mutableStateOf("")
    var vaiTroNhap by mutableStateOf(TeleRemote.VaiTro.CHU)
    var bao by mutableStateOf("")
        private set
    /** Lời báo là lỗi / chưa như mong đợi (tô màu cam). */
    var baoLech by mutableStateOf(false)
        private set

    /** App phụ cần Android 8+. */
    val duocCai: Boolean get() = (vm.thongTin?.sdk ?: 0) >= 26

    /** Cấu hình đang có trên tivi; chưa cài thì cấu hình mới (bot nói theo ngôn ngữ của TiviKit). */
    val cauHinh: TeleRemote.CauHinh get() = trangThai?.cauHinh ?: TeleRemote.CauHinh(tiengAnh = !vn.tivikit.NgonNgu.tiengViet)

    fun mo() = vm.chay(t("Đang đọc Tele Remote trên tivi…", "Reading Tele Remote on the TV…")) {
        vm.damBaoKetNoi()
        bao = ""; baoLech = false; doiBot = false
        docLai()
        vm.man = Man.TELE_REMOTE
    }

    fun lamMoi() = vm.chay(t("Đang đọc Tele Remote trên tivi…", "Reading Tele Remote on the TV…")) {
        vm.damBaoKetNoi(); bao = ""; baoLech = false; docLai()
    }

    private suspend fun docLai() {
        daCai = vm.io { vm.tv.teleRemoteDaCai() }
        trangThai = if (daCai) vm.io { vm.tv.docTeleRemote() }?.let(TeleRemote::trangThai) else null
        // Người đầu tiên thêm vào là chủ tivi; có chủ rồi thì mặc định thành viên.
        vaiTroNhap = if (cauHinh.nguoiDung.none { it.vaiTro == TeleRemote.VaiTro.CHU }) TeleRemote.VaiTro.CHU else TeleRemote.VaiTro.THANH_VIEN
    }

    /** Cài / cập nhật app phụ, gửi cấu hình, bật dịch vụ; chờ app nối ADB của tivi rồi báo bước tiếp. */
    fun bat() = vm.chay(t("Đang cài Tele Remote lên tivi…", "Installing Tele Remote on the TV…")) {
        if (!duocCai) error(t("Tele Remote cần Android 8 trở lên, tivi này là Android ${vm.thongTin?.android}.",
            "Tele Remote needs Android 8 or later; this TV runs Android ${vm.thongTin?.android}."))
        vm.damBaoKetNoi()
        bao = ""; baoLech = false
        val apk = vm.io { vn.tivikit.CapNhat.moAsset(vm.ctx, TeleRemote.APK).readBytes() }
        val apkGo = vm.io { vn.tivikit.CapNhat.moAsset(vm.ctx, "go.apk").readBytes() }
        vm.io { vm.tv.caiTeleRemote(apk, apkGo, cauHinh.copy(tiengAnh = !vn.tivikit.NgonNgu.tiengViet)) { s -> vm.thongBao = s } }
        daCai = true
        vm.thongBao = t("Chờ Tele Remote nối ADB của tivi…", "Waiting for Tele Remote to reach the TV's ADB…")
        for (i in 1..10) {
            delay(1_500)
            docLai()
            if (trangThai?.adb in listOf("da-noi", "dang-hoi", "tu-choi", "khong-co")) break
        }
        val tt = trangThai
        baoLech = tt == null || !tt.dichVuChay || tt.adb != "da-noi"
        bao = when {
            tt == null -> t("Đã cài nhưng chưa đọc lại được từ tivi. Bấm Làm mới.", "Installed, but couldn't read it back from the TV. Tap Refresh.")
            !tt.dichVuChay -> t("Đã cài, nhưng dịch vụ Tele Remote chưa chạy: vào Cài đặt → Trợ năng của tivi, bật \"Tele Remote (TiviKit)\", rồi bấm Làm mới.",
                "Installed, but the Tele Remote service isn't running: open the TV's Settings → Accessibility, turn on \"Tele Remote (TiviKit)\", then tap Refresh.")
            tt.adb == "dang-hoi" -> t("Trên tivi đang hiện hộp \"Cho phép gỡ lỗi?\": tích \"Luôn cho phép từ máy tính này\" rồi chọn OK bằng remote. Xong bấm Làm mới.",
                "The TV is showing \"Allow debugging?\": tick \"Always allow from this computer\" and press OK with the remote. Then tap Refresh.")
            tt.adb == "da-noi" && !tt.coToken -> t("Xong phần tivi. Giờ tạo bot ở @BotFather rồi dán token vào ô bên dưới.",
                "The TV side is ready. Now create a bot with @BotFather and paste its token below.")
            tt.adb == "da-noi" -> t("Xong. Tele Remote đang chạy trên tivi.", "Done. Tele Remote is running on the TV.")
            else -> moTaAdb(tt.adb)
        }
    }

    /** Gửi token bot lên tivi; tivi hỏi Telegram xem token có đúng không. */
    fun luuToken() = vm.chay(t("Đang nối bot trên tivi…", "Connecting the bot on the TV…")) {
        val token = TeleRemote.chuanToken(tokenNhap) ?: error(t("Token chưa đúng dạng: chép nguyên dòng @BotFather gửi, dạng 123456789:AAH….",
            "That isn't a bot token: copy the whole line @BotFather sent, like 123456789:AAH…."))
        vm.damBaoKetNoi()
        bao = ""; baoLech = false
        val kq = vm.io { vm.tv.datTokenTeleRemote(token) }
        when (val loi = TeleRemote.trong(kq, "loi")) {
            "" -> bao = t("Đã nối bot @${TeleRemote.trong(kq, "bot")}. Thêm người được dùng bên dưới, rồi nhắn bot từ Telegram.",
                "Connected to @${TeleRemote.trong(kq, "bot")}. Add the people allowed below, then message the bot on Telegram.")
            "khong-mang" -> {
                baoLech = true
                bao = t("Đã lưu token, nhưng tivi chưa vào được Telegram. Kiểm tra mạng của tivi; nhà mạng chặn Telegram thì đặt DNS ở mục DNS chặn quảng cáo (NextDNS, AdGuard).",
                    "The token is saved, but the TV can't reach Telegram. Check the TV's network; if your ISP blocks Telegram, set a DNS under Ad-blocking DNS (NextDNS, AdGuard).")
            }
            "sai-token" -> error(t("Telegram báo token sai (hay bot đã bị xoá / đổi token). Lấy lại token ở @BotFather.",
                "Telegram says the token is wrong (or the bot was deleted / its token changed). Get the token again from @BotFather."))
            else -> error(t("Tivi không nhận token ($loi).", "The TV rejected the token ($loi)."))
        }
        tokenNhap = ""; doiBot = false
        docLai()
    }

    /** Thêm người theo @username (vai trò đang chọn) và gửi lên tivi ngay. */
    fun themNguoi() = vm.chay(t("Đang thêm…", "Adding…")) {
        val ten = TeleRemote.chuanTen(tenNhap) ?: error(t("Gõ username Telegram dạng @tenban (xem ở Cài đặt → Tên người dùng trong Telegram).",
            "Type a Telegram username like @name (see Settings → Username in Telegram)."))
        val c = cauHinh
        if (c.nguoiDung.any { it.ten.equals(ten, ignoreCase = true) }) error(t("$ten đã có trong danh sách.", "$ten is already on the list."))
        if (c.nguoiDung.size >= TeleRemote.NGUOI_TOI_DA) error(t("Tối đa ${TeleRemote.NGUOI_TOI_DA} người.", "At most ${TeleRemote.NGUOI_TOI_DA} people."))
        guiCauHinh(c.copy(nguoiDung = c.nguoiDung + TeleRemote.NguoiDung(ten, 0, vaiTroNhap)))
        tenNhap = ""
        bao = t("Đã thêm $ten. Nhờ người đó mở bot trên Telegram và bấm Start (Bắt đầu).", "Added $ten. Ask them to open the bot on Telegram and tap Start.")
    }

    fun doiVaiTro(nd: TeleRemote.NguoiDung, vt: TeleRemote.VaiTro) = vm.chay(t("Đang lưu…", "Saving…")) {
        val c = cauHinh
        if (vt == TeleRemote.VaiTro.THANH_VIEN && nd.vaiTro == TeleRemote.VaiTro.CHU && c.nguoiDung.count { it.vaiTro == TeleRemote.VaiTro.CHU } <= 1)
            error(t("Tivi cần ít nhất một chủ.", "The TV needs at least one owner."))
        guiCauHinh(c.copy(nguoiDung = c.nguoiDung.map { if (it == nd) it.copy(vaiTro = vt) else it }))
    }

    fun boNguoi(nd: TeleRemote.NguoiDung) = vm.chay(t("Đang bỏ…", "Removing…")) {
        val c = cauHinh
        guiCauHinh(c.copy(nguoiDung = c.nguoiDung - nd))
        bao = t("Đã bỏ ${nd.tenXem}: người đó không điều khiển được tivi nữa.", "${nd.tenXem} is removed and can't control the TV anymore.")
    }

    /** Duyệt người xin dùng (theo ID của họ, dùng được cả khi họ không đặt username). */
    fun duyet(x: TeleRemote.XinDung, vt: TeleRemote.VaiTro) = vm.chay(t("Đang duyệt…", "Approving…")) {
        val c = cauHinh
        if (c.nguoiDung.size >= TeleRemote.NGUOI_TOI_DA) error(t("Tối đa ${TeleRemote.NGUOI_TOI_DA} người.", "At most ${TeleRemote.NGUOI_TOI_DA} people."))
        guiCauHinh(c.copy(nguoiDung = c.nguoiDung.filter { it.id != x.id } + TeleRemote.NguoiDung(x.ten, x.id, vt, x.tenHien)))
        bao = t("Đã duyệt ${x.tenXem}. Họ gõ /start trong chat với bot là dùng được.", "Approved ${x.tenXem}. They can send /start to the bot now.")
    }

    fun boQua(x: TeleRemote.XinDung) = vm.chay(t("Đang bỏ qua…", "Ignoring…")) {
        vm.damBaoKetNoi()
        vm.io { vm.tv.xoaChoTeleRemote(x.id) }
        docLai()
    }

    /** Tạm tắt / bật lại bot (app vẫn cài, tivi không hỏi Telegram khi tắt). */
    fun batTat(bat: Boolean) = vm.chay(t("Đang lưu…", "Saving…")) { guiCauHinh(cauHinh.copy(bat = bat)) }

    /** Cho chủ tivi chụp màn hình tivi gửi qua Telegram (tắt sẵn: ảnh màn hình đi qua máy chủ Telegram). */
    fun datChupManHinh(bat: Boolean) = vm.chay(t("Đang lưu…", "Saving…")) { guiCauHinh(cauHinh.copy(chupManHinh = bat)) }

    /** Bot nhắn các chủ tivi khi tivi vừa bật / vừa tắt. */
    fun datBaoTin(bat: Boolean) = vm.chay(t("Đang lưu…", "Saving…")) { guiCauHinh(cauHinh.copy(baoTin = bat)) }

    /** Bot nhắn chủ tivi một tin thử. */
    fun nhanThu() = vm.chay(t("Bot đang nhắn thử…", "The bot is sending a test message…")) {
        vm.damBaoKetNoi()
        val kq = vm.io { vm.tv.nhanThuTeleRemote() }
        baoLech = false
        bao = when (TeleRemote.trong(kq, "loi")) {
            "" -> t("Bot đã nhắn ${TeleRemote.trong(kq, "so")} chủ tivi: xem Telegram.", "The bot messaged ${TeleRemote.trong(kq, "so")} owner(s): check Telegram.")
            "chua-co-chu" -> { baoLech = true; t("Chưa có chủ nào nhắn bot: chủ tivi mở bot trên Telegram, bấm Start, rồi thử lại.", "No owner has messaged the bot yet: open the bot on Telegram, tap Start, then try again.") }
            "khong-mang" -> { baoLech = true; t("Tivi chưa vào được Telegram.", "The TV can't reach Telegram.") }
            "sai-token" -> { baoLech = true; t("Token của bot không còn đúng: đổi bot.", "The bot token is no longer valid: change the bot.") }
            else -> { baoLech = true; t("Tivi chưa nhắn được (${TeleRemote.trong(kq, "loi")}).", "The TV couldn't send it (${TeleRemote.trong(kq, "loi")}).") }
        }
        docLai()
    }

    /** Bỏ tạm khoá thành viên chủ đặt từ Telegram. */
    fun moThanhVien() = vm.chay(t("Đang mở lại…", "Letting them back in…")) {
        vm.damBaoKetNoi()
        vm.io { vm.tv.moThanhVienTeleRemote() }
        docLai()
    }

    fun noiLai() = vm.chay(t("Đang nối lại…", "Reconnecting…")) {
        vm.damBaoKetNoi()
        if (!vm.io { vm.tv.noiLaiTeleRemote() }) error(t("Dịch vụ Tele Remote trên tivi đang tắt: bấm Bật lại.", "The Tele Remote service on the TV is off: tap Turn on again."))
        for (i in 1..6) {
            delay(1_000)
            docLai()
            if (trangThai?.adb in listOf("da-noi", "dang-hoi")) break
        }
        bao = moTaAdb(trangThai?.adb.orEmpty())
        baoLech = trangThai?.adb != "da-noi"
    }

    fun go() = vm.chay(t("Đang gỡ Tele Remote…", "Removing Tele Remote…")) {
        vm.damBaoKetNoi()
        vm.io { vm.tv.goTeleRemote() }
        daCai = false; trangThai = null
        bao = t("Đã gỡ Tele Remote khỏi tivi. Bot trên Telegram vẫn còn: xoá ở @BotFather (/deletebot) nếu không dùng nữa.",
            "Tele Remote is removed from the TV. The bot still exists on Telegram: delete it with @BotFather (/deletebot) if you no longer need it.")
        baoLech = false
    }

    private suspend fun guiCauHinh(c: TeleRemote.CauHinh) {
        vm.damBaoKetNoi()
        bao = ""; baoLech = false
        if (!vm.io { vm.tv.datTeleRemote(c) }) error(t("Tivi không nhận cài đặt. Bấm Làm mới rồi thử lại.", "The TV rejected the settings. Tap Refresh and try again."))
        docLai()
    }

    fun quen() {
        trangThai = null; daCai = false; tokenNhap = ""; doiBot = false; tenNhap = ""; vaiTroNhap = TeleRemote.VaiTro.CHU; bao = ""; baoLech = false
    }

    companion object {
        /** Kết nối ADB của chính tivi, nói cho người dùng (như Map Voice). */
        fun moTaAdb(adb: String): String = when (adb) {
            "da-noi" -> t("Đã nối ADB của tivi (bấm phím được)", "Connected to the TV's ADB (keys work)")
            "dang-hoi" -> t("Tivi đang hỏi \"Cho phép gỡ lỗi?\": chọn Luôn cho phép rồi OK", "The TV is asking \"Allow debugging?\": choose Always allow, then OK")
            "tu-choi" -> t("Tivi đã không cho phép gỡ lỗi: bấm Nối lại rồi chọn Luôn cho phép trên tivi", "The TV didn't allow debugging: tap Reconnect and choose Always allow on the TV")
            "khong-co" -> t("Tivi không mở ADB qua mạng (cổng 5555): bật lại Gỡ lỗi USB / ADB qua mạng rồi bấm Nối lại. Chưa nối thì ▲▼◀▶ OK chưa dùng được (âm lượng, Back, Home, tắt tivi vẫn được).",
                "The TV's network ADB (port 5555) is off: turn USB debugging / ADB over network back on, then tap Reconnect. Until then ▲▼◀▶ OK won't work (volume, Back, Home and turning off still do).")
            "dang-noi" -> t("Đang nối ADB của tivi…", "Connecting to the TV's ADB…")
            "tat" -> t("Dịch vụ đang tắt", "The service is off")
            else -> t("Chưa nối được ADB của tivi, đang thử lại", "Couldn't reach the TV's ADB yet; retrying")
        }

        /** Vòng hỏi Telegram của bot, nói cho người dùng. */
        fun moTaTele(tele: String): String = when (tele) {
            "dang-nghe" -> t("Bot đang nghe lệnh", "The bot is listening")
            "chua-co-token" -> t("Chưa nối bot: dán token ở mục Bot Telegram", "No bot yet: paste a token under Telegram bot")
            "tam-tat" -> t("Đang tạm tắt", "Paused")
            "sai-token" -> t("Token sai hay bot đã bị xoá: đổi bot", "Wrong token or the bot was deleted: change the bot")
            "trung-bot" -> t("Bot này đang chạy ở tivi / máy khác: mỗi tivi cần một bot riêng", "This bot is running on another TV / device: each TV needs its own bot")
            "khong-mang" -> t("Tivi chưa vào được Telegram: kiểm tra mạng, hay đặt DNS ở mục DNS chặn quảng cáo nếu nhà mạng chặn Telegram",
                "The TV can't reach Telegram: check the network, or set a DNS under Ad-blocking DNS if your ISP blocks Telegram")
            "dang-noi" -> t("Đang nối Telegram…", "Connecting to Telegram…")
            "tat" -> t("Dịch vụ đang tắt", "The service is off")
            else -> t("Telegram báo lỗi, đang thử lại", "Telegram returned an error; retrying")
        }

        /** Một dòng nhật ký, nói cho người dùng. */
        fun moTa(n: TeleRemote.NhatKy): String {
            val sau = n.viec.substringAfter('|', "")
            return when (n.viec.substringBefore('|')) {
                "dieu-khien" -> t("mở bảng điều khiển", "opened the remote")
                "tat" -> t("tắt tivi", "turned the TV off")
                "khoi-dong-lai" -> t("khởi động lại tivi", "restarted the TV")
                "xin-dung" -> t("xin dùng bot", "asked to use the bot")
                "kenh" -> t("mở kênh ", "opened channel ") + sau
                "mo" -> t("mở ", "opened ") + (MapVoiceTivi.tenAppMv(sau) ?: sau)
                "dung" -> t("buộc dừng ", "force-stopped ") + (MapVoiceTivi.tenAppMv(sau) ?: sau)
                "noi" -> t("nhắn \"$sau\"", "typed \"$sau\"")
                "tro-ly" -> t("đưa Trợ lý: \"$sau\"", "asked the Assistant: \"$sau\"")
                "link" -> t("mở link ", "opened a link from ") + sau
                "anh" -> t("chiếu ảnh lên tivi", "showed a photo on the TV")
                "video" -> t("phát video lên tivi", "played a video on the TV")
                "go-chu" -> t("gõ chữ lên tivi", "typed text on the TV")
                "chup" -> t("chụp màn hình tivi", "took a screenshot")
                "hen" -> t("hẹn tắt tivi sau ", "set the TV to turn off in ") + sau.removePrefix("tat-") + t(" phút", " min")
                "huy-hen" -> t("huỷ một hẹn giờ", "cancelled a timer")
                "khoa-ngay" -> t("khoá trẻ em ngay", "locked the TV (Kids lock)")
                "mo-tam" -> t("mở khoá trẻ em $sau phút", "unlocked Kids lock for $sau min")
                "dong-app-nen" -> t("đóng app nền", "closed background apps")
                "don-cache" -> t("dọn cache", "cleared the cache")
                "nhan" -> t("nhắn lên màn hình tivi", "showed a message on the TV")
                "bat-man-hinh" -> t("bật màn hình", "turned the screen on")
                "am-luong" -> if (sau == "0") t("tắt tiếng", "muted") else t("chỉnh âm lượng $sau", "set the volume to $sau")
                "kiem-tra-mang" -> t("kiểm tra mạng", "checked the network")
                "duyet" -> t("duyệt $sau", "approved $sau")
                "bo-qua" -> t("bỏ qua $sau", "ignored $sau")
                "vai-tro" -> t("đổi vai trò của $sau", "changed $sau's role")
                "bo-nguoi" -> t("bỏ $sau khỏi danh sách", "removed $sau")
                "them" -> t("thêm $sau", "added $sau")
                "khoa-tv" -> t("khoá thành viên tới $sau", "paused members until $sau")
                "mo-tv" -> t("mở lại cho thành viên", "let members back in")
                "quay" -> t("quay màn hình 10 giây", "recorded the screen for 10 s")
                "xin-them" -> t("trẻ xin thêm giờ xem", "a child asked for more TV time")
                "khong-cho-them" -> t("không cho thêm giờ", "said no to more TV time")
                "hoan-tat" -> t("hoãn tắt tivi 30 phút", "postponed turning off for 30 min")
                "nen" -> t("đặt ảnh nền Home", "set the Home wallpaper")
                "bo-nen" -> t("về ảnh nền mặc định", "went back to the default wallpaper")
                "cast" -> sau.substringAfter('|').let { may ->
                    if (sau.substringBefore('|') == "chan") t("chặn máy cast \"$may\"", "blocked casting from \"$may\"")
                    else t("cho máy \"$may\" cast", "allowed \"$may\" to cast")
                }
                else -> n.viec
            }
        }
    }
}
