package vn.tivikit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.tivikit.dulieu.TeleRemote
import vn.tivikit.t

/**
 * Màn Tele Remote (31/10/2026): bot Telegram riêng của tivi chạy ngay trên tivi; người chủ tivi cho phép điều khiển tivi từ
 * Telegram (bàn phím remote, gõ chữ, Kênh nhanh, YouTube, ứng dụng, nhắn như nói, link, ảnh / video, Hẹn giờ, Khoá trẻ em,
 * Làm mới, chụp màn hình, tắt / khởi động lại tivi). Mỗi tivi một bot, hai vai trò chủ / thành viên.
 */
@Composable
internal fun ManTeleRemote(vm: PhienLamViec) = Cuon {
    val m = vm.teleRemote
    val uri = LocalUriHandler.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Tele Remote", style = KieuTieuDeLon)
        Phu(t("Điều khiển tivi từ Telegram, ở đâu cũng được: bot Telegram riêng của tivi chạy ngay trên tivi. Người bạn cho phép bấm bàn phím remote, gõ chữ lên tivi, mở Kênh nhanh, YouTube, ứng dụng; gõ như nói với Trợ lý (\"mở nhạc trịnh\", \"mở VTV3\": Map Voice làm); gửi link YouTube / FPT Play hay ảnh, video để mở trên tivi. Chủ tivi có thêm Hẹn giờ, Khoá trẻ em, Làm mới, chụp màn hình, tắt / khởi động lại tivi. Không cần mở cổng modem hay máy chủ, không cần điện thoại cài TiviKit khi dùng.",
            "Control the TV from Telegram, from anywhere: the TV's own Telegram bot runs on the TV itself. The people you allow can press remote keys, type on the TV, open Quick channel, YouTube and apps; type like talking to the Assistant (\"play piano music\": Map Voice does it); send a YouTube / FPT Play link or a photo or video to open it on the TV. Owners also get Timer, Kids lock, Refresh, screenshots and turning the TV off / restarting it. No port forwarding or server, and no TiviKit needed once it's set up."))
    }
    if (!m.duocCai) CanhBaoTr(t("Tele Remote cần Android 8 trở lên; tivi này là Android ${vm.thongTin?.android}.",
        "Tele Remote needs Android 8 or later; this TV runs Android ${vm.thongTin?.android}."))

    m.trangThai?.let { tt ->
        The(t("Trên tivi", "On the TV"), Icons.AutoMirrored.Rounded.Send) {
            DongTr(tt.dichVuChay, t("Dịch vụ Tele Remote đang chạy", "The Tele Remote service is running"),
                t("Dịch vụ Tele Remote đang tắt: bấm Bật lại bên dưới", "The Tele Remote service is off: tap Turn on again below"))
            if (tt.dichVuChay) {
                DongTr(tt.tele == "dang-nghe", TeleRemoteTivi.moTaTele(tt.tele), TeleRemoteTivi.moTaTele(tt.tele))
                DongTr(tt.adb == "da-noi", TeleRemoteTivi.moTaAdb(tt.adb), TeleRemoteTivi.moTaAdb(tt.adb))
            }
            if (tt.khoaThanhVienDen > System.currentTimeMillis()) {
                DongTr(false, "", t("Thành viên đang bị chủ tạm khoá tới ${vn.tivikit.dulieu.HenGio.gioCua(tt.khoaThanhVienDen, java.time.ZoneId.systemDefault()).first}",
                    "Members are paused by an owner until ${vn.tivikit.dulieu.HenGio.gioCua(tt.khoaThanhVienDen, java.time.ZoneId.systemDefault()).first}"))
                NutPhu(t("Mở lại cho thành viên", "Let members back in"), Icons.Rounded.LockOpen, enabled = !vm.dangBan) { m.moThanhVien() }
            }
            if (tt.dichVuChay && tt.adb in listOf("tu-choi", "khong-co", "loi")) NutPhu(t("Nối lại ADB", "Reconnect ADB"), Icons.Rounded.Sync, enabled = !vm.dangBan) { m.noiLai() }
            Hang(t("Bật Tele Remote", "Tele Remote on"), t("Tắt thì bot thôi nghe lệnh, app vẫn cài", "Off stops the bot listening; the app stays installed"), tt.cauHinh.bat) { m.batTat(it) }
            Hang(t("Chụp màn hình qua Telegram", "Screenshots over Telegram"), t("Chủ tivi bấm 📸 Màn hình để bot gửi ảnh màn hình tivi (ảnh đi qua máy chủ Telegram; phim có bản quyền thì ảnh đen)",
                "Owners tap 📸 Screenshot to get a picture of the TV screen (it goes through Telegram's servers; protected films come out black)"), tt.cauHinh.chupManHinh) { m.datChupManHinh(it) }
            Hang(t("Báo tin", "Notifications"), t("Bot nhắn các chủ tivi khi tivi vừa bật / vừa tắt", "The bot messages the owners when the TV turns on / off"), tt.cauHinh.baoTin) { m.datBaoTin(it) }
            if (tt.coToken) NutPhu(t("Bot nhắn thử cho chủ tivi", "Send a test message to the owners"), Icons.AutoMirrored.Rounded.Send, enabled = !vm.dangBan) { m.nhanThu() }
            if (tt.nhatKy.isNotEmpty()) Gap(t("Việc vừa làm", "Recent activity"), Icons.Rounded.History) {
                tt.nhatKy.reversed().forEach { n ->
                    Phu("${vn.tivikit.dulieu.HenGio.gioCua(n.luc, java.time.ZoneId.systemDefault()).first} · ${n.ai} · ${TeleRemoteTivi.moTa(n)}")
                }
            }
            TextButton(onClick = { m.lamMoi() }, enabled = !vm.dangBan) {
                Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(t("Làm mới", "Refresh"))
            }
        }
    }

    if (m.daCai) TheBotTr(vm, m) { uri.openUri(it) }
    if (m.daCai) TheNguoiTr(vm, m)

    val canBat = !m.daCai || m.trangThai?.dichVuChay != true
    NutChinh(if (!m.daCai) t("Bật Tele Remote", "Turn on Tele Remote") else if (canBat) t("Bật lại", "Turn on again") else t("Cài lại / cập nhật", "Reinstall / update"),
        enabled = !vm.dangBan && m.duocCai, icon = Icons.AutoMirrored.Rounded.Send) { m.bat() }
    if (m.bao.isNotEmpty()) Phu(m.bao, if (m.baoLech) Cam else Xanh)

    The(t("Cách dùng", "How to use"), Icons.Rounded.Info) {
        Phu(t("1. Bấm Bật Tele Remote. Lần đầu tivi hiện hộp \"Cho phép gỡ lỗi?\": tích Luôn cho phép rồi chọn OK bằng remote (cho riêng Tele Remote, chỉ một lần).",
            "1. Tap Turn on Tele Remote. The first time, the TV shows \"Allow debugging?\": tick Always allow and press OK with the remote (just for Tele Remote, only once)."))
        Phu(t("2. Tạo bot ở @BotFather trên Telegram (mỗi tivi một bot), dán token vào mục Bot Telegram.",
            "2. Create a bot with @BotFather on Telegram (one bot per TV) and paste its token under Telegram bot."))
        Phu(t("3. Thêm người được dùng bằng @username. Người đó mở bot, bấm Start (Bắt đầu), dưới ô gõ tin hiện bàn phím: 🎮 Điều khiển (có ⌨️ Gõ chữ), 📺 Kênh nhanh, ▶️ YouTube, 📱 Ứng dụng, 📢 Nhắn tivi (lời nhắn hiện to trên tivi), ℹ️ Tivi, ⚙️ Thêm (kiểm tra mạng, bật màn hình); chủ tivi có thêm ⏰ Hẹn giờ, 🔒 Khoá trẻ em, 📸 Màn hình, 📴 Tắt tivi, và trong ⚙️ Thêm: 🧹 Làm mới, 👥 Người dùng (duyệt, đổi vai trò, thêm), 📜 Việc vừa làm, ⏸ tạm khoá thành viên.",
            "3. Add people by @username. They open the bot and tap Start; a keyboard appears under the message box: 🎮 Remote (with ⌨️ Type), 📺 Quick channel, ▶️ YouTube, 📱 Apps, 📢 Message TV (shown big on the TV), ℹ️ TV, ⚙️ More (network check, screen on); owners also get ⏰ Timer, 🔒 Kids lock, 📸 Screenshot, 📴 Turn off TV, and under ⚙️ More: 🧹 Refresh TV, 👥 People (approve, change roles, add), 📜 Activity, ⏸ pausing members."))
        Phu(t("4. Gõ như nói với Trợ lý: \"mở nhạc trịnh\", \"mở VTV3\" thì Map Voice làm (cùng câu riêng, app đã chọn ở màn Map Voice); \"mở Netflix\" mở app trùng tên; \"âm lượng 10\", \"tắt tiếng\", \"bật tivi\" bot tự làm; câu khác đưa cho Trợ lý Google của tivi. Dán link YouTube / FPT Play để mở trên tivi; gửi ảnh, video (tới 20 MB) để chiếu lên tivi.",
            "4. Type like talking to the Assistant: \"play piano music\" and channel requests go to Map Voice (same custom phrases and apps as on the Map Voice screen); \"open Netflix\" opens the matching app; \"volume 10\", \"mute\", \"turn on the TV\" the bot does itself; anything else goes to the TV's Google Assistant. Paste a YouTube / FPT Play link to open it on the TV; send a photo or video (up to 20 MB) to show it on the TV."))
        Phu(t("Tele Remote cần ADB qua mạng của tivi luôn bật (cổng 5555) để bấm ▲▼◀▶ OK: đừng bấm Tắt ADB trong TiviKit. Tivi đang tắt hẳn (ngủ sâu, mất mạng) thì bot cũng nghỉ: lệnh gửi lúc đó bị bỏ, không làm khi tivi bật lại.",
            "Tele Remote needs the TV's network ADB to stay on (port 5555) for ▲▼◀▶ OK: don't tap Turn off ADB in TiviKit. When the TV is fully off (deep sleep, no network) the bot rests too: requests sent then are dropped, not run when the TV comes back."))
    }

    var hoiGo by remember { mutableStateOf(false) }
    if (m.daCai) TextButton(onClick = { hoiGo = true }, enabled = !vm.dangBan) {
        Icon(Icons.Rounded.DeleteOutline, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Gỡ Tele Remote khỏi tivi", "Remove Tele Remote from the TV"))
    }
    if (hoiGo) AlertDialog(
        onDismissRequest = { hoiGo = false },
        icon = { Icon(Icons.Rounded.DeleteOutline, null, tint = Cam) },
        title = { Text(t("Gỡ Tele Remote?", "Remove Tele Remote?")) },
        text = { Text(t("Tivi thôi nghe lệnh từ Telegram; token và danh sách người được dùng trên tivi bị xoá. Bot trên Telegram vẫn còn.",
            "The TV stops taking commands from Telegram; the token and the list of people on the TV are deleted. The bot itself stays on Telegram.")) },
        confirmButton = { TextButton(onClick = { hoiGo = false; m.go() }) { Text(t("Gỡ", "Remove"), color = Cam) } },
        dismissButton = { TextButton(onClick = { hoiGo = false }) { Text(t("Thôi", "Cancel")) } },
    )
    Phu(t("Bật sẽ cài app nhỏ \"Tele Remote (TiviKit)\" và bật dịch vụ trợ năng của nó (để chạy suốt từ lúc bật tivi, được mở app khi đang chạy nền và biết app nào đang hiện; không đọc nội dung màn hình, không bắt phím). App nối ADB của chính tivi bằng khoá riêng để bấm phím. Tin nhắn với bot (kể cả ảnh, video bạn gửi và ảnh chụp màn hình nếu bật) đi qua máy chủ Telegram; token chỉ lưu trên tivi. Lệnh tới từ người không có trong danh sách thì không làm; tivi chỉ nhận lệnh gửi từ lúc nó đang nghe. Gỡ hoặc Khôi phục tivi là xoá hết.",
        "Turning it on installs the small \"Tele Remote (TiviKit)\" app and turns on its accessibility service (to keep running from power-on, be allowed to open apps from the background and know which app is showing; it doesn't read the screen or catch keys). The app connects to the TV's own ADB with its own key to press keys. Messages with the bot (including photos and videos you send, and screenshots if turned on) go through Telegram's servers; the token is stored only on the TV. Requests from anyone not on the list are ignored, and the TV only takes requests sent while it's listening. Remove or Restore deletes everything."))
}

/** Bot Telegram của tivi: hướng dẫn tạo bot ở @BotFather, ô dán token; có bot rồi thì tên bot, mở trên Telegram, đổi bot. */
@Composable
private fun TheBotTr(vm: PhienLamViec, m: TeleRemoteTivi, mo: (String) -> Unit) = The(t("Bot Telegram", "Telegram bot"), Icons.Rounded.SmartToy) {
    val tt = m.trangThai
    val bot = tt?.bot.orEmpty()
    if (tt?.coToken == true && !m.doiBot) {
        Text(if (bot.isNotEmpty()) "@$bot" else t("Bot đã nối (chưa đọc được tên)", "Bot connected (name not read yet)"), fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (bot.isNotEmpty()) TextButton(onClick = { runCatching { mo("https://t.me/$bot") } }) { Text(t("Mở bot trên Telegram", "Open the bot in Telegram")) }
            TextButton(onClick = { m.doiBot = true }, enabled = !vm.dangBan) { Text(t("Đổi bot", "Change bot")) }
        }
        return@The
    }
    Phu(t("1. Mở @BotFather trên Telegram, gửi /newbot.", "1. Open @BotFather on Telegram and send /newbot."))
    Phu(t("2. Đặt tên bot (VD \"Tivi phòng khách\") rồi username kết thúc bằng bot (VD tivi_nha_minh_bot).",
        "2. Name the bot (e.g. \"Living room TV\"), then give it a username ending in bot (e.g. our_tv_bot)."))
    Phu(t("3. @BotFather gửi token dạng 123456789:AAH…: chép rồi dán vào ô dưới. Mỗi tivi một bot riêng; giữ token như mật khẩu.",
        "3. @BotFather replies with a token like 123456789:AAH…: copy it into the box below. One bot per TV; keep the token like a password."))
    TextButton(onClick = { runCatching { mo("https://t.me/BotFather") } }) { Text(t("Mở @BotFather", "Open @BotFather")) }
    OutlinedTextField(m.tokenNhap, { m.tokenNhap = it.take(120) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp),
        label = { Text(t("Token của bot", "Bot token")) })
    NutPhu(t("Nối bot", "Connect the bot"), Icons.Rounded.Link, enabled = !vm.dangBan && m.tokenNhap.isNotBlank()) { m.luuToken() }
    if (m.doiBot) TextButton(onClick = { m.doiBot = false; m.tokenNhap = "" }, enabled = !vm.dangBan) { Text(t("Thôi", "Cancel")) }
}

/** Người được dùng: danh sách (vai trò, đã nhắn bot chưa), người đang xin dùng, thêm theo @username. */
@Composable
private fun TheNguoiTr(vm: PhienLamViec, m: TeleRemoteTivi) = The(t("Người được dùng", "People allowed"), Icons.Rounded.Group) {
    val c = m.cauHinh
    Phu(t("Chủ: mọi việc, kể cả tắt / khởi động lại tivi, Hẹn giờ, Khoá trẻ em (mở tạm vẫn cần mã bố mẹ), Làm mới, chụp màn hình, quản lý người dùng ngay trên Telegram, tạm khoá thành viên. Thành viên: điều khiển, gõ chữ, Kênh nhanh, YouTube, ứng dụng, nhắn như nói, âm lượng, link, ảnh / video, lời nhắn lên tivi, kiểm tra mạng, xem tivi.",
        "Owner: everything, including turning the TV off / restarting it, Timer, Kids lock (unlocking still needs the parent code), Refresh, screenshots, managing people right in Telegram and pausing members. Member: remote, typing, Quick channel, YouTube, apps, typed requests, volume, links, photos / videos, messages on the TV, network check, TV status."))
    if (c.nguoiDung.isEmpty()) Phu(t("Chưa có ai. Thêm chủ tivi trước (thường là bạn).", "Nobody yet. Add the owner first (usually you)."))
    c.nguoiDung.forEach { nd ->
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(The2).padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(nd.tenXem, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Phu(listOfNotNull(nd.tenHien.takeIf { it.isNotEmpty() && nd.ten.isNotEmpty() },
                        if (nd.id != 0L) t("đã nhắn bot", "has messaged the bot") else t("chưa nhắn bot: nhờ họ mở bot, bấm Start", "hasn't messaged the bot: ask them to open it and tap Start"))
                        .joinToString(" · "), if (nd.id != 0L) ChuPhu else Cam)
                }
                IconButton(onClick = { m.boNguoi(nd) }, enabled = !vm.dangBan) { Icon(Icons.Rounded.Close, t("Bỏ", "Remove"), tint = ChuPhu) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChonChip(t("Chủ", "Owner"), nd.vaiTro == TeleRemote.VaiTro.CHU) { if (nd.vaiTro != TeleRemote.VaiTro.CHU) m.doiVaiTro(nd, TeleRemote.VaiTro.CHU) }
                ChonChip(t("Thành viên", "Member"), nd.vaiTro == TeleRemote.VaiTro.THANH_VIEN) { if (nd.vaiTro != TeleRemote.VaiTro.THANH_VIEN) m.doiVaiTro(nd, TeleRemote.VaiTro.THANH_VIEN) }
            }
        }
    }
    val cho = m.trangThai?.cho.orEmpty()
    if (cho.isNotEmpty()) {
        Text(t("Đang xin dùng", "Asking to use the bot"), fontWeight = FontWeight.SemiBold)
        cho.forEach { x ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(NenCanhBao).padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(x.tenXem, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Phu(vn.tivikit.dulieu.HenGio.gioCua(x.luc, java.time.ZoneId.systemDefault()).first + t(" · đã nhắn bot", " · messaged the bot"))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { m.duyet(x, TeleRemote.VaiTro.THANH_VIEN) }, enabled = !vm.dangBan) { Text(t("Duyệt (thành viên)", "Approve (member)")) }
                    TextButton(onClick = { m.duyet(x, TeleRemote.VaiTro.CHU) }, enabled = !vm.dangBan) { Text(t("Duyệt làm chủ", "Approve as owner")) }
                    TextButton(onClick = { m.boQua(x) }, enabled = !vm.dangBan) { Text(t("Bỏ qua", "Ignore"), color = Cam) }
                }
            }
        }
    }
    OutlinedTextField(m.tenNhap, { m.tenNhap = it.take(40) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp),
        label = { Text(t("@username Telegram", "Telegram @username")) })
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChonChip(t("Chủ", "Owner"), m.vaiTroNhap == TeleRemote.VaiTro.CHU) { m.vaiTroNhap = TeleRemote.VaiTro.CHU }
        ChonChip(t("Thành viên", "Member"), m.vaiTroNhap == TeleRemote.VaiTro.THANH_VIEN) { m.vaiTroNhap = TeleRemote.VaiTro.THANH_VIEN }
    }
    NutPhu(t("Thêm người", "Add person"), Icons.Rounded.PersonAdd, enabled = !vm.dangBan && m.tenNhap.isNotBlank()) { m.themNguoi() }
    Phu(t("Người không đặt username: nhờ họ nhắn bot, họ hiện ở mục Đang xin dùng để bạn duyệt. Người đã nhắn bot thì từ đó tivi nhận ra họ theo ID của Telegram, đổi username vẫn dùng được.",
        "People without a username: ask them to message the bot; they show up under Asking to use the bot for you to approve. Once someone has messaged the bot, the TV knows them by their Telegram ID, even if they change their username."))
}

@Composable
private fun CanhBaoTr(chu: String) = Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(NenCanhBao).padding(12.dp)) {
    Icon(Icons.Rounded.Warning, null, tint = Cam); Spacer(Modifier.width(8.dp))
    Phu(chu, ChuChinh)
}

@Composable
private fun DongTr(ok: Boolean, coChu: String, khongChu: String) = Row(verticalAlignment = Alignment.CenterVertically) {
    Icon(if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.Warning, null, Modifier.size(16.dp), tint = if (ok) Xanh else Cam)
    Spacer(Modifier.width(6.dp))
    Text(if (ok) coChu else khongChu, fontSize = 13.sp, color = if (ok) Xanh else Cam)
}
