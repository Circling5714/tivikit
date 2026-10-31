package vn.tivikit.teleremote

import java.time.ZoneId
import vn.tivikit.teleremote.XuLyLenh.Den

/*
 * Việc thêm của bot (1/11/2026): lời nhắn hiện lên tivi, menu ⚙️ Thêm, bật màn hình, âm lượng bằng câu, kiểm tra mạng,
 * nhật ký, người dùng (duyệt người xin dùng, đổi vai trò, bỏ, thêm theo @username), tạm khoá thành viên. Hàm mở rộng của
 * LamViec, gọi từ LamViec.lam (quyền chủ / thành viên đã xét ở đó).
 */

private val mui: ZoneId get() = ZoneId.systemDefault()

// ---------- 📢 Lời nhắn lên tivi ----------

internal fun LamViec.hoiNhan(d: Den) {
    if (d.nut != null) ra.traLoiNut(d.idNut)
    dv.choTin[d.chat] = LamViec.ChoNhan(System.currentTimeMillis() + LamViec.CHO_MS)
    ra.gui(d.chat, t("📢 Gõ lời nhắn để hiện lên màn hình tivi:", "📢 Type the message to show on the TV screen:"),
        XuLyLenh.choTraLoi(t("Lời nhắn", "Message")))
}

internal fun LamViec.hienLoiNhan(d: Den, nd: NguoiDung, chu: String) {
    val loi = chu.trim().take(500)
    val ok = loi.isNotEmpty() && dv.hienLoiNhan(loi, nd.tenHien.ifEmpty { nd.ten }, d.chat)
    ra.gui(d.chat, if (ok) t("📢 Đã hiện lời nhắn trên tivi. Có người bấm OK trên remote thì bot báo.",
        "📢 The message is on the TV screen. The bot will tell you when someone presses OK on the remote.")
        else t("Không hiện được lời nhắn trên tivi.", "Couldn't show the message on the TV."), banPhim(nd))
    if (ok) ghi(nd, "nhan")
}

// ---------- ⚙️ Thêm, bật màn hình, âm lượng, mạng ----------

internal fun LamViec.them(d: Den, nd: NguoiDung, traLoi: Boolean = true) {
    val bay = System.currentTimeMillis()
    val khoaTv = dv.kho.khoaThanhVienDen
    val chu = nd.vaiTro == VaiTro.CHU
    val dong = mutableListOf("⚙️ ${t("Thêm", "More")} · ${dv.tenTivi()}")
    if (chu && khoaTv > bay) dong += t("⏸ Thành viên đang bị khoá tới ${ChuBot.gio(khoaTv, bay, mui)}.", "⏸ Members are paused until ${ChuBot.gio(khoaTv, bay, mui)}.")
    hienBang(d, dong.joinToString("\n") to XuLyLenh.bangThem(chu, en, khoaTv, bay), traLoi)
}

internal fun LamViec.batManHinh(d: Den, nd: NguoiDung) {
    if (d.nut != null) ra.traLoiNut(d.idNut)
    val ok = dv.batManHinh()
    ra.gui(d.chat, if (ok) t("☀️ Màn hình tivi đã bật.", "☀️ The TV screen is on.")
        else t("Chưa bật được màn hình: tivi ngủ sâu hay tắt nguồn thì phải bật bằng remote.", "Couldn't turn the screen on: a TV in deep sleep or powered off needs its remote."))
    if (ok) ghi(nd, "bat-man-hinh")
}

internal fun LamViec.amLuong(d: Den, nd: NguoiDung, v: XuLyLenh.AmLuong) {
    val (muc, toiDa, tatTieng) = dv.amLuong(v.lam, v.so)
    ra.gui(d.chat, if (tatTieng) t("🔇 Đã tắt tiếng (âm lượng $muc/$toiDa).", "🔇 Muted (volume $muc/$toiDa).")
        else t("🔊 Âm lượng: $muc/$toiDa", "🔊 Volume: $muc/$toiDa"))
    ghi(nd, "am-luong|" + (if (tatTieng) "0" else "$muc"))
}

internal fun LamViec.kiemTraMang(d: Den, nd: NguoiDung) {
    if (d.nut != null) ra.traLoiNut(d.idNut, t("Đang đo mạng, chừng 10 giây…", "Measuring the network, about 10 seconds…"))
    ra.dangLam(d.chat, "typing")
    val kq = dv.kiemTraMang()
    ra.gui(d.chat, kq?.let { MangTr.chu(it, dv.tenTivi(), en) } ?: chuChuaAdb())
    if (kq != null) ghi(nd, "kiem-tra-mang")
}

// ---------- 📜 Nhật ký ----------

internal fun LamViec.nhatKy(d: Den) {
    if (d.nut != null) ra.traLoiNut(d.idNut)
    val bay = System.currentTimeMillis()
    val ds = dv.kho.nhatKy.takeLast(15).reversed()
    val dong = listOf("📜 ${t("Việc vừa làm", "Recent activity")} · ${dv.tenTivi()}") +
        if (ds.isEmpty()) listOf(t("Chưa có việc nào.", "Nothing yet."))
        else ds.map { "${ChuBot.gio(it.luc, bay, mui)} · ${it.ai} · ${ChuBot.moTaViec(it.viec, en, dv::tenApp)}" }
    ra.gui(d.chat, dong.joinToString("\n"))
}

// ---------- 👥 Người dùng ----------

internal fun LamViec.nguoiDs(d: Den, traLoi: Boolean = true) {
    val ch = dv.kho.cauHinh
    val cho = dv.kho.cho
    val bay = System.currentTimeMillis()
    val dong = mutableListOf("👥 ${t("Người dùng bot", "People using the bot")} · ${dv.tenTivi()}")
    ch.nguoiDung.forEach { nd ->
        dong += (if (nd.vaiTro == VaiTro.CHU) "👑 " else "👤 ") + XuLyLenh.tenXem(nd) + (if (nd.id == 0L) t(" · chưa nhắn bot", " · hasn't messaged the bot") else "")
    }
    if (cho.isNotEmpty()) {
        dong += t("Đang xin dùng:", "Asking to use the bot:")
        cho.forEach { dong += "🔔 " + XuLyLenh.tenXin(it) }
    }
    dv.kho.khoaThanhVienDen.takeIf { it > bay }?.let { dong += t("⏸ Thành viên đang bị khoá tới ${ChuBot.gio(it, bay, mui)}.", "⏸ Members are paused until ${ChuBot.gio(it, bay, mui)}.") }
    dong += t("Bấm một người để đổi vai trò hay bỏ.", "Tap someone to change their role or remove them.")
    hienBang(d, dong.joinToString("\n") to XuLyLenh.bangNguoi(ch, cho, en), traLoi)
}

internal fun LamViec.nguoiXem(d: Den, k: String, traLoi: Boolean = true) {
    val nd = XuLyLenh.timTheoKhoa(dv.kho.cauHinh, k)
    if (nd == null) {
        ra.traLoiNut(d.idNut, t("Người này không còn trong danh sách.", "This person is no longer on the list."), canhBao = true)
        return
    }
    val chu = listOf(
        (if (nd.vaiTro == VaiTro.CHU) "👑 " else "👤 ") + XuLyLenh.tenXem(nd),
        t("Vai trò: ", "Role: ") + (if (nd.vaiTro == VaiTro.CHU) t("chủ", "owner") else t("thành viên", "member")) + " · " +
            (if (nd.id != 0L) t("đã nhắn bot", "has messaged the bot") else t("chưa nhắn bot", "hasn't messaged the bot yet")),
    ).joinToString("\n")
    hienBang(d, chu to XuLyLenh.bangMotNguoi(nd, en), traLoi)
}

internal fun LamViec.datVaiTro(d: Den, nd: NguoiDung, k: String, chu: Boolean) {
    val moi = XuLyLenh.doiVaiTro(dv.kho.cauHinh, k, chu)
    if (moi == null) {
        ra.traLoiNut(d.idNut, t("Tivi cần ít nhất một chủ.", "The TV needs at least one owner."), canhBao = true)
        return
    }
    dv.kho.cauHinh = moi
    val ten = XuLyLenh.timTheoKhoa(moi, k)?.let(XuLyLenh::tenXem).orEmpty()
    ra.traLoiNut(d.idNut, if (chu) t("👑 $ten giờ là chủ", "👑 $ten is now an owner") else t("👤 $ten giờ là thành viên", "👤 $ten is now a member"))
    ghi(nd, "vai-tro|$ten")
    nguoiXem(d, k, traLoi = false)
}

internal fun LamViec.boNguoi(d: Den, nd: NguoiDung, k: String) {
    val nguoi = XuLyLenh.timTheoKhoa(dv.kho.cauHinh, k)
    val moi = XuLyLenh.boNguoi(dv.kho.cauHinh, k)
    if (nguoi == null || moi == null) {
        ra.traLoiNut(d.idNut, if (nguoi == null) t("Người này không còn trong danh sách.", "This person is no longer on the list.")
            else t("Tivi cần ít nhất một chủ.", "The TV needs at least one owner."), canhBao = true)
        return
    }
    dv.kho.cauHinh = moi
    ra.traLoiNut(d.idNut, t("Đã bỏ ${XuLyLenh.tenXem(nguoi)}", "${XuLyLenh.tenXem(nguoi)} was removed"))
    ghi(nd, "bo-nguoi|${XuLyLenh.tenXem(nguoi)}")
    nguoiDs(d, traLoi = false)
}

internal fun LamViec.duyet(d: Den, nd: NguoiDung, id: Long, chu: Boolean) {
    val x = dv.kho.cho.firstOrNull { it.id == id }
    if (x == null) {
        ra.traLoiNut(d.idNut, t("Người này không còn chờ duyệt (có thể chủ khác đã duyệt).", "This person is no longer waiting (another owner may have handled it)."), canhBao = true)
        nguoiDs(d, traLoi = false)
        return
    }
    val moi = XuLyLenh.duyet(dv.kho.cauHinh, x, chu)
    if (moi == null) {
        ra.traLoiNut(d.idNut, t("Đã đủ ${CauHinh.NGUOI_TOI_DA} người: bỏ bớt người trước.", "The list is full (${CauHinh.NGUOI_TOI_DA} people): remove someone first."), canhBao = true)
        return
    }
    dv.kho.cauHinh = moi
    dv.kho.xoaCho(id)
    val ten = XuLyLenh.tenXin(x)
    ra.traLoiNut(d.idNut, if (chu) t("👑 Đã duyệt $ten làm chủ", "👑 $ten approved as owner") else t("✅ Đã duyệt $ten", "✅ $ten approved"))
    // Báo người vừa được duyệt (họ đã nhắn bot nên bot nhắn lại được).
    ra.gui(id, t("✅ Chủ tivi đã cho bạn dùng bot của ${dv.tenTivi()}. Gõ /start để bắt đầu.", "✅ The TV's owner approved you for ${dv.tenTivi()}'s bot. Send /start to begin."))
    ghi(nd, "duyet|$ten")
    nguoiDs(d, traLoi = false)
}

internal fun LamViec.boQuaXin(d: Den, nd: NguoiDung, id: Long) {
    val x = dv.kho.cho.firstOrNull { it.id == id }
    dv.kho.xoaCho(id)
    ra.traLoiNut(d.idNut, t("Đã bỏ qua", "Ignored"))
    if (x != null) ghi(nd, "bo-qua|${XuLyLenh.tenXin(x)}")
    nguoiDs(d, traLoi = false)
}

internal fun LamViec.hoiThemNguoi(d: Den) {
    if (d.nut != null) ra.traLoiNut(d.idNut)
    dv.choTin[d.chat] = LamViec.ChoThem(System.currentTimeMillis() + LamViec.CHO_MS)
    ra.gui(d.chat, t("➕ Gõ @username Telegram của người cần thêm (vào làm thành viên; đổi làm chủ sau ở 👥):",
        "➕ Type the Telegram @username of the person to add (as a member; make them an owner later under 👥):"),
        XuLyLenh.choTraLoi("@username"))
}

internal fun LamViec.themNguoi(d: Den, nd: NguoiDung, chu: String) {
    val ten = XuLyLenh.chuanTen(chu)
    if (ten == null) {
        ra.gui(d.chat, t("Username dạng @tenban (xem ở Cài đặt → Tên người dùng trong Telegram). Bấm ➕ lại để thử lại.",
            "A username looks like @name (Settings → Username in Telegram). Tap ➕ again to retry."), banPhim(nd))
        return
    }
    val moi = XuLyLenh.themTen(dv.kho.cauHinh, ten)
    if (moi == null) {
        ra.gui(d.chat, t("$ten đã có trong danh sách, hay đã đủ ${CauHinh.NGUOI_TOI_DA} người.", "$ten is already on the list, or the list is full."), banPhim(nd))
        return
    }
    dv.kho.cauHinh = moi
    val bot = dv.kho.bot.takeIf { it.isNotEmpty() }?.let { " @$it" }.orEmpty()
    ra.gui(d.chat, t("✅ Đã thêm $ten làm thành viên. Nhờ người đó mở bot$bot trên Telegram và bấm Start.",
        "✅ $ten was added as a member. Ask them to open the bot$bot on Telegram and tap Start."), banPhim(nd))
    ghi(nd, "them|$ten")
}

// ---------- ⏸ Tạm khoá thành viên ----------

internal fun LamViec.khoaThanhVien(d: Den, nd: NguoiDung, phut: Int) {
    val bay = System.currentTimeMillis()
    val den = if (phut == 0) 0L else XuLyLenh.khoaTvToi(phut, bay, mui)
    dv.kho.khoaThanhVienDen = den
    val gio = ChuBot.gio(den, bay, mui)
    ra.traLoiNut(d.idNut, if (den == 0L) t("▶️ Thành viên dùng bot lại được", "▶️ Members can use the bot again")
        else t("⏸ Đã khoá thành viên tới $gio", "⏸ Members are paused until $gio"))
    ghi(nd, if (den == 0L) "mo-tv" else "khoa-tv|$gio")
    them(d, nd, traLoi = false)
}
