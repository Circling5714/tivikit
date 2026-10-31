package vn.tivikit.teleremote

import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import org.json.JSONObject
import vn.tivikit.teleremote.XuLyLenh.Den

/**
 * Làm việc người dùng vừa chọn trên tivi rồi trả lời qua [ra]: menu, bảng điều khiển (bấm phím qua ADB của tivi), gõ chữ,
 * ô Kênh nhanh, YouTube, danh sách app, tình trạng tivi, Làm mới, Hẹn giờ, Khoá trẻ em, chụp màn hình, tắt / khởi động lại
 * tivi; câu gõ như nói (Map Voice, không thì Trợ lý Google), link, ảnh / video chiếu lên tivi. Chạy trên luồng của Bot (một
 * việc một lúc).
 */
class LamViec(private val dv: TeleRemoteDichVu, private val ra: Ra) {
    /**
     * Việc đang chờ tin chữ kế tiếp của một chat: chữ để gõ lên tivi ([thay]: thay chữ đang có trong ô, không thì viết tiếp),
     * hay mã mở khoá trẻ em ([phut] phút mở tạm).
     */
    sealed interface Cho { val het: Long }
    data class ChoGo(override val het: Long, val thay: Boolean = true) : Cho
    data class ChoMa(val phut: Int, override val het: Long) : Cho

    private val ch = dv.kho.cauHinh
    private val en = ch.tiengAnh

    private fun t(vi: String, enS: String) = if (en) enS else vi

    fun lam(d: Den, nd: NguoiDung) {
        val viec = when {
            d.nut != null -> XuLyLenh.viecTuNut(d.nut) ?: run { ra.traLoiNut(d.idNut); return }
            else -> XuLyLenh.viecTuTin(d)
        }
        // Bot đang chờ chữ để gõ / mã mở khoá: câu chữ kế tiếp là câu trả lời. Chờ gõ chữ thì bấm phím trên bảng điều khiển
        // (đi tới ô nhập) vẫn chờ; chọn việc khác thì thôi chờ.
        val cho = dv.choTin[d.chat]?.takeIf { it.het > System.currentTimeMillis() }
        if (cho != null && d.nut == null && (viec is XuLyLenh.Noi || viec is XuLyLenh.Link)) {
            dv.choTin.remove(d.chat)
            traLoiCho(d, nd, cho)
            return
        }
        if (!(cho is ChoGo && (viec is XuLyLenh.Phim || viec == XuLyLenh.CapNhat))) dv.choTin.remove(d.chat)
        if (XuLyLenh.chiChu(viec) && nd.vaiTro != VaiTro.CHU) {
            val loi = if (viec == XuLyLenh.HoiTat || viec == XuLyLenh.Tat) t("Chỉ chủ tivi mới tắt được tivi.", "Only the TV's owner can turn it off.")
                else t("Việc này chỉ chủ tivi làm được.", "Only the TV's owner can do this.")
            if (d.nut != null) ra.traLoiNut(d.idNut, loi, canhBao = true) else ra.gui(d.chat, loi)
            return
        }
        when (viec) {
            XuLyLenh.Menu -> ra.gui(d.chat, chao(nd), banPhim(nd))
            XuLyLenh.DieuKhien -> {
                ra.gui(d.chat, chuDieuKhien(null), XuLyLenh.bangDieuKhien(en))
                ghi(nd, "dieu-khien")
            }
            is XuLyLenh.Phim -> phim(d, viec.ma)
            XuLyLenh.CapNhat -> {
                ra.traLoiNut(d.idNut)
                ra.sua(d.chat, d.idTin, chuDieuKhien(null), XuLyLenh.bangDieuKhien(en))
            }
            XuLyLenh.GoChu -> hoiGo(d, thay = true)
            XuLyLenh.GoTiep -> hoiGo(d, thay = false)
            XuLyLenh.Kenh -> kenh(d)
            is XuLyLenh.MoKenh -> moKenh(d, nd, viec.o)
            XuLyLenh.YouTube -> youTube(d, nd)
            is XuLyLenh.UngDung -> ungDung(d, nd, viec.trang)
            is XuLyLenh.MoUngDung -> moUngDung(d, nd, viec.goi)
            XuLyLenh.DungApp -> dungApp(d, nd)
            XuLyLenh.Tivi -> ra.gui(d.chat, tinhTrang())
            XuLyLenh.LamMoiTv -> lamMoi(d)
            XuLyLenh.DongAppNen -> dongAppNen(d, nd)
            XuLyLenh.DonCache -> donCache(d, nd)
            XuLyLenh.HenGio -> hienBang(d, bangHenGio())
            is XuLyLenh.TatSau -> tatSau(d, nd, viec.phut)
            is XuLyLenh.HuyHen -> huyHen(d, nd, viec.id)
            XuLyLenh.Khoa -> hienBang(d, bangKhoa())
            XuLyLenh.KhoaNgay -> khoaNgay(d, nd)
            is XuLyLenh.MoTam -> hoiMa(d, nd, viec.phut)
            XuLyLenh.ChupManHinh -> chup(d, nd)
            XuLyLenh.HoiTat -> {
                if (d.nut != null) ra.traLoiNut(d.idNut)
                ra.gui(d.chat, t("Tắt hay khởi động lại tivi?", "Turn the TV off or restart it?"), XuLyLenh.hoiTat(en))
            }
            XuLyLenh.Tat -> tat(d, nd)
            XuLyLenh.KhoiDongLai -> khoiDongLai(d, nd)
            XuLyLenh.ThoiTat -> {
                ra.traLoiNut(d.idNut)
                ra.sua(d.chat, d.idTin, t("Đã thôi, tivi vẫn bật.", "Cancelled; the TV stays on."))
            }
            is XuLyLenh.Noi -> noi(d, nd, viec.cau)
            is XuLyLenh.Link -> link(d, nd, viec.url)
            XuLyLenh.ChieuTep -> chieuTep(d, nd)
            XuLyLenh.KhongRo -> ra.gui(d.chat, t("Chọn việc ở bàn phím bên dưới, hay gõ như nói với Trợ lý (VD \"mở nhạc trịnh\"). Gõ /start nếu không thấy bàn phím.",
                "Pick from the keyboard below, or type like talking to the Assistant (e.g. \"play piano music\"). Send /start if you don't see the keyboard."), banPhim(nd))
        }
    }

    private fun banPhim(nd: NguoiDung) = XuLyLenh.banPhimChinh(nd.vaiTro, en,
        XuLyLenh.Co(henGio = dv.coApp(HEN_GIO), khoa = dv.coApp(KHOA), manHinh = ch.chupManHinh))

    private fun chao(nd: NguoiDung): String {
        val ten = nd.tenHien.ifEmpty { nd.ten }
        val vai = if (nd.vaiTro == VaiTro.CHU) t("chủ", "the owner") else t("thành viên", "a member")
        return t("📺 ${dv.tenTivi()} · Tele Remote\nXin chào $ten! Bạn là $vai của tivi này.\n" +
            "• Chọn việc ở bàn phím bên dưới.\n• Gõ như nói với Trợ lý: \"mở nhạc trịnh\", \"mở VTV3\", \"mở Netflix\".\n" +
            "• Gửi link YouTube / FPT Play để mở trên tivi; gửi ảnh, video để chiếu lên tivi.",
            "📺 ${dv.tenTivi()} · Tele Remote\nHi $ten! You're $vai of this TV.\n" +
            "• Pick from the keyboard below.\n• Type like talking to the Assistant: \"play piano music\", \"open Netflix\".\n" +
            "• Send a YouTube / FPT Play link to open it on the TV; send a photo or video to show it on the TV.")
    }

    /** Chữ của bảng điều khiển: tivi, app đang mở, nút vừa bấm kèm giờ (để mỗi lần sửa tin đều khác tin trước). */
    private fun chuDieuKhien(daBam: String?): String {
        val app = dv.appDangMo().ifEmpty { t("không rõ", "unknown") }
        val dong = mutableListOf("🎮 ${t("Điều khiển", "Remote")} · ${dv.tenTivi()}", t("Đang mở: $app", "Showing: $app"))
        val gio = SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(Date())
        dong += if (daBam != null) t("Vừa bấm: $daBam ($gio)", "Pressed: $daBam ($gio)") else t("Cập nhật lúc $gio", "Updated at $gio")
        return dong.joinToString("\n")
    }

    private fun phim(d: Den, ma: Int) {
        if (!dv.bamPhim(ma)) {
            ra.traLoiNut(d.idNut, chuChuaAdb(), canhBao = true)
            return
        }
        ra.traLoiNut(d.idNut)
        // Phím đổi màn (Back, Home, OK) thì đợi lâu hơn cho tên app mới kịp hiện (TX3: Home hiện sau chừng nửa giây).
        Thread.sleep(if (ma == 3 || ma == 4 || ma == 23) 800L else 150L)
        ra.sua(d.chat, d.idTin, chuDieuKhien(XuLyLenh.PHIM[ma] ?: XuLyLenh.PHIM_THEM[ma]), XuLyLenh.bangDieuKhien(en))
    }

    // ---------- Gõ chữ, mã mở khoá: chờ câu trả lời ----------

    private fun hoiGo(d: Den, thay: Boolean) {
        if (d.nut != null) ra.traLoiNut(d.idNut)
        dv.choTin[d.chat] = ChoGo(System.currentTimeMillis() + CHO_MS, thay)
        ra.gui(d.chat, if (thay) t("⌨️ Gõ chữ cần nhập vào ô đang chọn trên tivi (ô tìm kiếm, mật khẩu Wi-Fi…) rồi gửi: chữ đang có trong ô được thay. Chưa chọn ô thì dùng ⬆️⬇️⬅️➡️ trên bảng điều khiển để chọn trước, bot vẫn chờ.",
            "⌨️ Type the text for the field selected on the TV (search box, Wi-Fi password…) and send it; it replaces what's in the field. If no field is selected yet, use ⬆️⬇️⬅️➡️ on the remote pad first; the bot keeps waiting.")
            else t("⌨️ Gõ chữ để viết tiếp vào ô vừa gõ.", "⌨️ Type the text to add to the field."),
            XuLyLenh.choTraLoi(t("Chữ gõ lên tivi", "Text for the TV")))
    }

    private fun traLoiCho(d: Den, nd: NguoiDung, cho: Cho) {
        val chu = d.chu.orEmpty()
        when (cho) {
            is ChoGo -> {
                ra.dangLam(d.chat, "typing")
                val kq = dv.goChu(chu, cho.thay)
                if (kq == "ok") {
                    ra.gui(d.chat, t("⌨️ Đã gõ lên tivi.", "⌨️ Typed on the TV."), XuLyLenh.sauKhiGo(en))
                    // Không ghi chữ đã gõ (có thể là mật khẩu).
                    ghi(nd, "go-chu")
                    return
                }
                ra.gui(d.chat, when (kq) {
                    "khong-o-nhap" -> t("Trên tivi chưa chọn ô nhập chữ: dùng ⬆️⬇️⬅️➡️ trên bảng điều khiển chọn ô (VD ô tìm kiếm) rồi bấm ⌨️ Gõ chữ lại.",
                        "No text field is selected on the TV: use ⬆️⬇️⬅️➡️ on the remote pad to select one (e.g. a search box), then tap ⌨️ Type again.")
                    "can-bo-go" -> t("Chữ có dấu cần bộ gõ tiếng Việt của TiviKit trên tivi: mở TiviKit → Tele Remote → Cài lại / cập nhật. Chữ không dấu thì gõ được ngay.",
                        "Accented text needs TiviKit's Vietnamese typing helper on the TV: open TiviKit → Tele Remote → Reinstall / update. Plain text works now.")
                    "chua-adb" -> chuChuaAdb()
                    else -> t("Chưa gõ được ($kq).", "Couldn't type it ($kq).")
                }, banPhim(nd))
            }
            is ChoMa -> moTam(d, nd, cho.phut, chu)
        }
    }

    // ---------- Kênh nhanh, YouTube, ứng dụng ----------

    private fun kenh(d: Den) {
        val ds = dv.dsKenh()
        if (ds.isEmpty()) ra.gui(d.chat, t("Tivi chưa có ô Kênh nhanh nào. Thêm ở TiviKit → Kênh nhanh, hay gõ \"mở VTV3\" (Map Voice).",
            "The TV has no Quick channel tiles yet. Add them in TiviKit → Quick channel, or type \"open VTV3\" (Map Voice)."))
        else ra.gui(d.chat, t("📺 Kênh nhanh: bấm một kênh để mở.", "📺 Quick channel: tap a channel to open it."), XuLyLenh.bangKenh(ds))
    }

    private fun moKenh(d: Den, nd: NguoiDung, o: Int) {
        val ten = dv.dsKenh().firstOrNull { it.first == o }?.second
        if (ten == null) {
            ra.traLoiNut(d.idNut, t("Ô này không còn trên tivi.", "This tile is no longer on the TV."), canhBao = true)
            return
        }
        val ok = dv.mo(Intent().setClassName(XuLyLenh.goiKenh(o), "vn.tivikit.kenh.MoKenh"))
        ra.traLoiNut(d.idNut, if (ok) t("Đang mở $ten", "Opening $ten") else t("Không mở được $ten", "Couldn't open $ten"), canhBao = !ok)
        if (ok) ghi(nd, "kenh|$ten")
    }

    /**
     * App YouTube: app Map Voice đang dùng (app chọn ở TiviKit → Map Voice, chưa chọn thì SmartTube / TizenTube có trên tivi),
     * không có Map Voice thì SmartTube, TizenTube, YouTube gốc.
     */
    private fun appYouTube(): String? {
        val chon = if (dv.coApp(MAP_VOICE)) dv.goiAppPhu(MAP_VOICE, "doc")?.let(XuLyLenh::jsonTrong)?.optString("appYouTube") else null
        return chon?.takeIf { it.isNotEmpty() && dv.coApp(it) } ?: APP_YOUTUBE.firstOrNull(dv::coApp)
    }

    private fun youTube(d: Den, nd: NguoiDung) {
        val app = appYouTube()
        if (app == null) {
            ra.gui(d.chat, t("Tivi chưa có SmartTube, TizenTube hay YouTube.", "The TV has no SmartTube, TizenTube or YouTube."))
            return
        }
        val ten = dv.tenApp(app)
        val ok = dv.mo(dv.moApp(app))
        ra.gui(d.chat, if (ok) t("▶️ Đã mở $ten. Gõ \"mở nhạc …\" để tìm và phát luôn.", "▶️ $ten is open. Type \"play … music\" to search and play.")
            else t("Không mở được $ten.", "Couldn't open $ten."))
        if (ok) ghi(nd, "mo|$app")
    }

    private fun ungDung(d: Den, nd: NguoiDung, trang: Int) {
        val ds = dv.dsApp()
        val chu = t("📱 Ứng dụng trên tivi (${ds.size}): bấm để mở. Gõ \"mở <tên app>\" cũng được.",
            "📱 Apps on the TV (${ds.size}): tap one to open it, or type \"open <app name>\".")
        val bp = XuLyLenh.bangUngDung(ds, trang, en, nd.vaiTro == VaiTro.CHU)
        if (d.nut != null) {
            ra.traLoiNut(d.idNut)
            ra.sua(d.chat, d.idTin, chu, bp)
        } else ra.gui(d.chat, chu, bp)
    }

    private fun moUngDung(d: Den, nd: NguoiDung, goi: String) {
        val ten = dv.tenApp(goi)
        val ok = dv.mo(dv.moApp(goi))
        ra.traLoiNut(d.idNut, if (ok) t("Đang mở $ten", "Opening $ten") else t("Không mở được $ten", "Couldn't open $ten"), canhBao = !ok)
        if (ok) ghi(nd, "mo|$goi")
    }

    private fun dungApp(d: Den, nd: NguoiDung) {
        val goi = dv.goiDangMo()
        val loi = when {
            goi.isEmpty() -> t("Chưa biết app nào đang mở (tivi đang ở màn bảo vệ?).", "Not sure which app is showing (is the screensaver on?).")
            dv.laHome(goi) -> t("Tivi đang ở màn Home: không có app nào để dừng.", "The TV is on its Home screen: no app to stop.")
            goi.startsWith("vn.tivikit") -> t("Không buộc dừng app của TiviKit.", "TiviKit's own apps aren't force-stopped.")
            !dv.dungApp(goi) -> chuChuaAdb()
            else -> null
        }
        ra.traLoiNut(d.idNut, loi ?: t("⏹ Đã buộc dừng ${dv.tenApp(goi)}", "⏹ ${dv.tenApp(goi)} was force-stopped"), canhBao = loi != null)
        if (loi == null) ghi(nd, "dung|$goi")
    }

    // ---------- Tình trạng, Làm mới ----------

    /** Tình trạng tivi: màn hình, app đang mở, bài đang phát (Cast SmartTube), âm lượng, RAM, bộ nhớ, đã bật bao lâu, mạng, ADB. */
    private fun tinhTrang(): String {
        val am = dv.getSystemService(AudioManager::class.java)
        val sd = dv.soDo(doCpu = false)
        val man = when (dv.manHinh()) {
            "Awake" -> t("đang bật", "on")
            "Dreaming" -> t("đang ở màn bảo vệ", "screensaver")
            "Asleep", "Dozing" -> t("đang tắt", "off")
            else -> if (dv.dangSang()) t("đang bật", "on") else t("đang tắt", "off")
        }
        val dangPhat = if (dv.coApp(CAST)) dv.goiAppPhu(CAST, "dang-phat", choToiDa = 3000)?.let(XuLyLenh::jsonTrong)
            ?.let { j -> listOf(j.optString("tieuDe"), j.optString("kenh")).filter { it.isNotBlank() }.joinToString(" · ") }?.takeIf { it.isNotEmpty() } else null
        return listOfNotNull(
            "📺 ${dv.tenTivi()} (${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, Android ${android.os.Build.VERSION.RELEASE})",
            t("Màn hình: $man", "Screen: $man"),
            t("Đang mở: ", "Showing: ") + dv.appDangMo().ifEmpty { t("không rõ", "unknown") },
            dangPhat?.let { t("Đang phát: ", "Playing: ") + it },
            t("Âm lượng: ", "Volume: ") + "${am.getStreamVolume(AudioManager.STREAM_MUSIC)}/${am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)}",
            t("RAM trống: ", "Free RAM: ") + "${ChuBot.gb(sd.ramCon, en)} / ${ChuBot.gb(sd.ramTong, en)} · " + t("Bộ nhớ trống: ", "Free storage: ") + ChuBot.gb(sd.boNhoCon, en),
            t("Đã bật: ", "Up for: ") + ChuBot.thoiGian(sd.msChay, en),
            t("Mạng: ", "Network: ") + dv.ip().ifEmpty { t("không rõ", "unknown") },
            "ADB: " + if (dv.coAdb()) t("đã nối", "connected") else t("chưa nối (phím ▲▼◀▶ OK chưa dùng được)", "not connected (▲▼◀▶ OK won't work)"),
        ).joinToString("\n")
    }

    private fun lamMoi(d: Den) {
        if (d.nut != null) ra.traLoiNut(d.idNut)
        ra.dangLam(d.chat, "typing")
        val sd = dv.soDo(doCpu = true)
        hienBang(d, ChuBot.lamMoi(sd, dv.tenTivi(), en) to XuLyLenh.bangLamMoi(en, ChuBot.boNhoGanDay(sd.boNhoTong, sd.boNhoCon)), traLoi = false)
    }

    private fun dongAppNen(d: Den, nd: NguoiDung) {
        ra.traLoiNut(d.idNut, t("Đang đóng app nền…", "Closing background apps…"))
        val truoc = dv.soDo(doCpu = false).ramCon
        // am kill-all chỉ đóng tiến trình nền an toàn để đóng, không đụng app đang mở (như Làm mới tivi của TiviKit).
        if (dv.lenh("am kill-all", 10_000) == null) {
            ra.gui(d.chat, chuChuaAdb())
            return
        }
        Thread.sleep(1500)
        val sau = dv.soDo(doCpu = false).ramCon
        ra.gui(d.chat, t("🧹 Đã đóng các app chạy nền. RAM trống: ${ChuBot.gb(truoc, en)} → ${ChuBot.gb(sau, en)}.",
            "🧹 Background apps closed. Free RAM: ${ChuBot.gb(truoc, en)} → ${ChuBot.gb(sau, en)}."))
        ghi(nd, "dong-app-nen")
    }

    private fun donCache(d: Den, nd: NguoiDung) {
        ra.traLoiNut(d.idNut, t("Đang dọn cache…", "Clearing the cache…"))
        ra.dangLam(d.chat, "typing")
        val truoc = dv.soDo(doCpu = false).boNhoCon
        if (dv.lenh("pm trim-caches 1000G", 120_000) == null) {
            ra.gui(d.chat, if (dv.coAdb()) t("Dọn cache lâu quá, chưa xong.", "Clearing the cache is taking too long.") else chuChuaAdb())
            return
        }
        val sau = dv.soDo(doCpu = false).boNhoCon
        ra.gui(d.chat, t("🗑 Đã dọn cache. Bộ nhớ trống: ${ChuBot.gb(truoc, en)} → ${ChuBot.gb(sau, en)}.",
            "🗑 Cache cleared. Free storage: ${ChuBot.gb(truoc, en)} → ${ChuBot.gb(sau, en)}."))
        ghi(nd, "don-cache")
    }

    // ---------- Hẹn giờ (app vn.tivikit.hengio) ----------

    /** Chữ và nút của bảng Hẹn giờ đọc từ app Hẹn giờ; chưa cài / chưa nối ADB thì chữ báo và không có nút. */
    private fun bangHenGio(): Pair<String, JSONObject?> {
        if (!dv.coApp(HEN_GIO)) return t("Tivi chưa có Hẹn giờ: cài ở TiviKit → Hẹn giờ.", "The TV has no Timer yet: install it in TiviKit → Timer.") to null
        val j = dv.goiAppPhu(HEN_GIO, "doc")?.let(XuLyLenh::jsonTrong) ?: return chuChuaAdb() to null
        val hg = ChuBot.henGio(j, dv.tenTivi(), en, ZoneId.systemDefault(), ::tenAppHen)
        return hg.chu to XuLyLenh.bangHenGio(hg.motLan, en)
    }

    /** Tên app trong lịch Hẹn giờ: ô Kênh nhanh thì "Kênh nhanh: VTV1". */
    private fun tenAppHen(goi: String): String {
        val o = (1..XuLyLenh.SO_O).firstOrNull { XuLyLenh.goiKenh(it) == goi }
        return o?.let { so -> dv.dsKenh().firstOrNull { it.first == so }?.let { t("Kênh nhanh: ", "Quick channel: ") + it.second } } ?: dv.tenApp(goi)
    }

    private fun tatSau(d: Den, nd: NguoiDung, phut: Int) {
        val kq = dv.goiAppPhu(HEN_GIO, "mot-lan", XuLyLenh.thamSo(JSONObject().put("loai", "tat").put("sauPhut", phut)))
        val luc = kq?.let { XuLyLenh.giaTri(it, "luc").toLongOrNull() }
        if (kq == null || !kq.contains("ok=1") || luc == null) {
            ra.traLoiNut(d.idNut, if (kq == null) chuChuaAdb() else t("Hẹn giờ không nhận (${XuLyLenh.giaTri(kq, "loi")}).",
                "The Timer refused it (${XuLyLenh.giaTri(kq, "loi")})."), canhBao = true)
            return
        }
        val gio = ChuBot.gio(luc, System.currentTimeMillis(), ZoneId.systemDefault())
        ra.traLoiNut(d.idNut, t("😴 Tivi sẽ tắt lúc $gio", "😴 The TV will turn off at $gio"))
        ghi(nd, "hen|tat-$phut")
        hienBang(d, bangHenGio(), traLoi = false)
    }

    private fun huyHen(d: Den, nd: NguoiDung, id: String) {
        val kq = dv.goiAppPhu(HEN_GIO, "huy-mot-lan", id)
        val ok = kq?.contains("ok=1") == true
        ra.traLoiNut(d.idNut, when {
            kq == null -> chuChuaAdb()
            ok -> t("Đã huỷ hẹn", "Cancelled")
            else -> t("Hẹn này không còn.", "That one is already gone.")
        }, canhBao = kq == null)
        if (ok) ghi(nd, "huy-hen")
        hienBang(d, bangHenGio(), traLoi = false)
    }

    // ---------- Khoá trẻ em (app vn.tivikit.khoa) ----------

    private fun bangKhoa(): Pair<String, JSONObject?> {
        if (!dv.coApp(KHOA)) return t("Tivi chưa có Khoá trẻ em: cài ở TiviKit → Khoá trẻ em.", "The TV has no Kids lock yet: install it in TiviKit → Kids lock.") to null
        val j = dv.goiAppPhu(KHOA, "doc")?.let(XuLyLenh::jsonTrong) ?: return chuChuaAdb() to null
        val chu = ChuBot.khoa(j, dv.tenTivi(), System.currentTimeMillis(), en, ZoneId.systemDefault(), dv::tenApp)
        return chu to (if (j.optBoolean("bat")) XuLyLenh.bangKhoa(en, j.optBoolean("coOtp")) else null)
    }

    private fun khoaNgay(d: Den, nd: NguoiDung) {
        val loi = dv.khoaNgay()
        ra.traLoiNut(d.idNut, when (loi) {
            "" -> t("🔒 Đã khoá tivi: mở lại cần mã của bố mẹ.", "🔒 The TV is locked: unlocking needs the parent's code.")
            "chua-co-ma" -> t("Tivi chưa có mã bố mẹ: đặt mã ở TiviKit → Khoá trẻ em trước.", "The TV has no parent code yet: set one in TiviKit → Kids lock first.")
            "dich-vu" -> t("Dịch vụ Khoá trẻ em trên tivi đang tắt.", "The Kids lock service on the TV is off.")
            else -> t("Chưa khoá được ($loi).", "Couldn't lock it ($loi).")
        }, canhBao = loi.isNotEmpty())
        if (loi.isNotEmpty()) return
        ghi(nd, "khoa-ngay")
        Thread.sleep(500)
        hienBang(d, bangKhoa(), traLoi = false)
    }

    /** Mở tạm [phut] phút: tivi có mã bố mẹ thì hỏi mã (tin kế tiếp), chưa có mã thì mở luôn như TiviKit. */
    private fun hoiMa(d: Den, nd: NguoiDung, phut: Int) {
        ra.traLoiNut(d.idNut)
        val j = dv.goiAppPhu(KHOA, "doc")?.let(XuLyLenh::jsonTrong)
        if (j == null) {
            ra.gui(d.chat, chuChuaAdb())
            return
        }
        if (!j.optBoolean("coOtp")) {
            moTamLuon(d, nd, phut, null)
            return
        }
        dv.choTin[d.chat] = ChoMa(phut, System.currentTimeMillis() + CHO_MS)
        ra.gui(d.chat, t("🔓 Mở tạm ${XuLyLenh.thoiLuong(phut, false)}: gõ mã 6 số trong app xác thực của bố mẹ (hay mã dự phòng 8 số).",
            "🔓 Unlock for ${XuLyLenh.thoiLuong(phut, true)}: type the 6-digit code from the parent's authenticator app (or an 8-digit backup code)."),
            XuLyLenh.choTraLoi(t("Mã 6 số", "6-digit code")))
    }

    private fun moTam(d: Den, nd: NguoiDung, phut: Int, chu: String) {
        // Mã đã gửi thì xoá khỏi khung chat (mã dự phòng dùng một lần, mã 6 số hết hạn sau 30 giây); không ghi mã vào nhật ký.
        ra.xoaTin(d.chat, d.idTin)
        val ma = XuLyLenh.maKhoa(chu)
        if (ma == null) {
            ra.gui(d.chat, t("Mã gồm 6 số (hay 8 số dự phòng). Bấm 🔓 lại để thử lại.", "The code is 6 digits (or 8 for a backup code). Tap 🔓 again to retry."), banPhim(nd))
            return
        }
        val xt = dv.goiAppPhu(KHOA, "xac-thuc", ma)?.let(XuLyLenh::jsonTrong)
        if (xt == null) {
            ra.gui(d.chat, chuChuaAdb(), banPhim(nd))
            return
        }
        val phien = xt.optString("phien")
        if (!xt.optBoolean("ok") || phien.isEmpty()) {
            ra.gui(d.chat, when (xt.optString("loi")) {
                "sai" -> t("Mã sai (còn ${xt.optInt("con")} lần thử). Bấm 🔓 lại để nhập lại.", "Wrong code (${xt.optInt("con")} tries left). Tap 🔓 again to retry.")
                "cho" -> t("Nhập sai nhiều lần: chờ ${xt.optInt("giay")} giây rồi thử lại.", "Too many wrong codes: wait ${xt.optInt("giay")} seconds and try again.")
                else -> t("Tivi không nhận mã.", "The TV didn't accept the code.")
            }, banPhim(nd))
            return
        }
        moTamLuon(d, nd, phut, phien)
    }

    private fun moTamLuon(d: Den, nd: NguoiDung, phut: Int, phien: String?) {
        val kq = dv.goiAppPhu(KHOA, "mo-tam", XuLyLenh.thamSo(JSONObject().put("phut", phut).apply { if (phien != null) put("phien", phien) }))
        if (kq?.contains("ok=1") != true) {
            ra.gui(d.chat, if (kq == null) chuChuaAdb() else t("Chưa mở được (${XuLyLenh.giaTri(kq, "loi")}).", "Couldn't unlock (${XuLyLenh.giaTri(kq, "loi")})."), banPhim(nd))
            return
        }
        val gio = ChuBot.gio(System.currentTimeMillis() + phut * 60_000L, System.currentTimeMillis(), ZoneId.systemDefault())
        ra.gui(d.chat, t("🔓 Đã mở khoá tới $gio.", "🔓 Unlocked until $gio."), banPhim(nd))
        ghi(nd, "mo-tam|$phut")
    }

    // ---------- Chụp màn hình, tắt / khởi động lại ----------

    private fun chup(d: Den, nd: NguoiDung) {
        if (!ch.chupManHinh) {
            ra.gui(d.chat, t("Chụp màn hình đang tắt: bật ở TiviKit → Tele Remote.", "Screenshots are off: turn them on in TiviKit → Tele Remote."))
            return
        }
        ra.dangLam(d.chat, "upload_photo")
        val anh = dv.chup()
        if (anh == null) {
            ra.gui(d.chat, if (dv.coAdb()) t("Chưa chụp được màn hình tivi.", "Couldn't take a screenshot.") else chuChuaAdb())
            return
        }
        ra.guiAnh(d.chat, anh, "📸 ${dv.tenTivi()} · " + SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(Date()))
        ghi(nd, "chup")
    }

    private fun tat(d: Den, nd: NguoiDung) {
        ra.traLoiNut(d.idNut)
        val ok = dv.tatTivi()
        ra.sua(d.chat, d.idTin, if (ok) t("📴 Đã tắt tivi.", "📴 The TV is off.")
            else t("Không tắt được tivi (Android 8 cần ADB của tivi).", "Couldn't turn the TV off (Android 8 needs the TV's ADB)."))
        if (ok) ghi(nd, "tat")
    }

    private fun khoiDongLai(d: Den, nd: NguoiDung) {
        ra.traLoiNut(d.idNut)
        if (!dv.coAdb()) {
            ra.sua(d.chat, d.idTin, chuChuaAdb())
            return
        }
        // Trả lời và ghi trước: lệnh reboot làm bot nghỉ tới khi tivi lên lại.
        ghi(nd, "khoi-dong-lai")
        ra.sua(d.chat, d.idTin, t("🔁 Đang khởi động lại tivi: chừng một phút nữa bot nghe lệnh lại.", "🔁 Restarting the TV: the bot will be back in about a minute."))
        dv.khoiDongLai()
    }

    // ---------- Nhắn như nói, link, ảnh / video ----------

    /** Câu gõ như nói với Trợ lý: Map Voice làm (cùng câu riêng, app đã chọn); không thì app trùng tên, rồi Trợ lý Google. */
    private fun noi(d: Den, nd: NguoiDung, cau: String) {
        // Tin chỉ có 6 / 8 số mà bot không chờ mã (bấm việc khác giữa chừng): có thể là mã mở khoá, không đưa cho ai, không ghi.
        if (XuLyLenh.maKhoa(cau) != null) {
            ra.xoaTin(d.chat, d.idTin)
            ra.gui(d.chat, t("Mã mở khoá: bấm 🔒 Khoá trẻ em → 🔓 trước rồi mới gõ mã.", "Unlock code: tap 🔒 Kids lock → 🔓 first, then type the code."), banPhim(nd))
            return
        }
        ra.dangLam(d.chat, "typing")
        if (dv.coApp(MAP_VOICE)) {
            val kq = dv.goiAppPhu(MAP_VOICE, "thu", XuLyLenh.thamSo(JSONObject().put("cau", cau)), 20_000)
            val bao = kq?.let { ChuBot.mapVoice(XuLyLenh.giaTri(it, "viec"), XuLyLenh.giaTri(it, "app"), en, dv::tenApp) }
            if (bao != null) {
                ra.gui(d.chat, bao)
                ghi(nd, "noi|$cau")
                return
            }
        }
        XuLyLenh.timApp(cau, dv.dsApp())?.let { goi ->
            val ok = dv.mo(dv.moApp(goi))
            ra.gui(d.chat, if (ok) t("📱 Đã mở ${dv.tenApp(goi)}.", "📱 ${dv.tenApp(goi)} is open.") else t("Không mở được ${dv.tenApp(goi)}.", "Couldn't open ${dv.tenApp(goi)}."))
            if (ok) ghi(nd, "mo|$goi")
            return
        }
        if (dv.troLy(cau)) {
            ra.gui(d.chat, t("🔍 Đã đưa cho Trợ lý Google trên tivi: “$cau”", "🔍 Passed to the Google Assistant on the TV: “$cau”"))
            ghi(nd, "tro-ly|$cau")
            return
        }
        ra.gui(d.chat, if (dv.coApp(MAP_VOICE)) t("Bot chưa hiểu câu này, và Trợ lý Google trên tivi đang tắt. Thử: “mở nhạc trịnh”, “mở VTV3”, “mở Netflix”.",
            "The bot didn't get that, and the TV's Google Assistant is off. Try: “play piano music”, “open Netflix”.")
            else t("Bot chưa hiểu câu này. Cài Map Voice ở TiviKit để nhắn như nói: “mở nhạc trịnh”, “mở VTV3”…",
            "The bot didn't get that. Install Map Voice in TiviKit to type like talking: “play piano music”…"), banPhim(nd))
    }

    private fun link(d: Den, nd: NguoiDung, url: String) {
        val i = when {
            XuLyLenh.laYouTube(url) -> {
                val app = appYouTube()
                if (app == null) {
                    ra.gui(d.chat, t("Tivi chưa có SmartTube, TizenTube hay YouTube.", "The TV has no SmartTube, TizenTube or YouTube."))
                    return
                }
                Intent(Intent.ACTION_VIEW, Uri.parse(XuLyLenh.maYouTube(url)?.let { "https://www.youtube.com/watch?v=$it" } ?: url)).setPackage(app)
            }
            XuLyLenh.laFpt(url) -> {
                if (!dv.coApp(FPT)) {
                    ra.gui(d.chat, t("Tivi chưa có FPT Play.", "The TV doesn't have FPT Play."))
                    return
                }
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(FPT)
            }
            else -> Intent(Intent.ACTION_VIEW, Uri.parse(url))
        }
        val ok = dv.mo(i)
        val ten = i.`package`?.let(dv::tenApp)
        ra.gui(d.chat, if (!ok) t("Tivi không có app mở được link này.", "No app on the TV can open this link.")
            else if (ten != null) t("🔗 Đã mở link bằng $ten trên tivi.", "🔗 Opened the link in $ten on the TV.") else t("🔗 Đã mở link trên tivi.", "🔗 Opened the link on the TV."))
        if (ok) ghi(nd, "link|${XuLyLenh.tenMien(url)}")
    }

    private fun chieuTep(d: Den, nd: NguoiDung) {
        val tep = d.tep ?: return
        if (tep.co > XuLyLenh.TEP_TOI_DA) {
            ra.gui(d.chat, t("Tệp lớn quá: bot chỉ tải được tới 20 MB. Gửi video ngắn hơn.", "That file is too big: bots can only download up to 20 MB. Send a shorter video."))
            return
        }
        ra.dangLam(d.chat, if (tep.laVideo) "upload_video" else "upload_photo")
        val dich = dv.tepMoi()
        if (!ra.taiTep(tep.id, dich, XuLyLenh.TEP_TOI_DA)) {
            ra.gui(d.chat, t("Tivi chưa tải được tệp từ Telegram.", "The TV couldn't download the file from Telegram."))
            return
        }
        val ok = dv.chieu(dich, tep.laVideo)
        ra.gui(d.chat, when {
            !ok -> t("Không mở được màn chiếu trên tivi.", "Couldn't open the viewer on the TV.")
            tep.laVideo -> t("🎬 Đang phát video trên tivi. Bấm Back trên remote để tắt.", "🎬 Playing the video on the TV. Press Back on the remote to close it.")
            else -> t("🖼 Đang chiếu ảnh trên tivi. Bấm Back trên remote để tắt.", "🖼 Showing the photo on the TV. Press Back on the remote to close it.")
        })
        if (ok) ghi(nd, if (tep.laVideo) "video" else "anh")
    }

    /** Hiện bảng [bang] (chữ, nút): bấm nút trong tin thì sửa chính tin đó, gõ lệnh thì gửi tin mới. */
    private fun hienBang(d: Den, bang: Pair<String, JSONObject?>, traLoi: Boolean = true) {
        if (d.nut != null && traLoi) ra.traLoiNut(d.idNut)
        if (d.nut != null && d.idTin != 0L) ra.sua(d.chat, d.idTin, bang.first, bang.second) else ra.gui(d.chat, bang.first, bang.second)
    }

    private fun chuChuaAdb() = t("Tivi chưa nối ADB của chính nó: mở TiviKit → Tele Remote xem cách nối.",
        "The TV isn't connected to its own ADB yet: open TiviKit → Tele Remote to fix it.")

    private fun ghi(nd: NguoiDung, viec: String) = dv.kho.ghi(nd.ten.ifEmpty { nd.tenHien }, viec)

    companion object {
        const val MAP_VOICE = "vn.tivikit.mapvoice"
        const val HEN_GIO = "vn.tivikit.hengio"
        const val KHOA = "vn.tivikit.khoa"
        const val CAST = "vn.tivikit.cast"
        const val FPT = "net.fptplay.ottbox"

        /**
         * Chờ chữ để gõ / mã mở khoá tối đa chừng này (ms): đủ lâu để tìm mật khẩu Wi-Fi, vì quá hạn thì chữ gõ thành câu nhắn
         * như nói (đưa Map Voice / Trợ lý).
         */
        private const val CHO_MS = 10 * 60_000L

        /** App YouTube mở bằng nút ▶️ YouTube, theo thứ tự ưu tiên. */
        val APP_YOUTUBE = listOf(
            "org.smarttube.stable", "org.smarttube.beta", "com.liskovsoft.smarttubetv", "com.liskovsoft.smarttubetv.beta",
            "io.gh.reisxd.tizentube.cobalt", "com.google.android.youtube.tv",
        )

        /** Báo tin: tivi vừa bật / vừa tắt. */
        fun chuBat(dv: TeleRemoteDichVu): String =
            if (dv.kho.cauHinh.tiengAnh) "📺 ${dv.tenTivi()} just turned on (${gioPhut()})." else "📺 ${dv.tenTivi()} vừa bật (${gioPhut()})."

        fun chuTat(dv: TeleRemoteDichVu): String =
            if (dv.kho.cauHinh.tiengAnh) "📴 ${dv.tenTivi()} just turned off (${gioPhut()})." else "📴 ${dv.tenTivi()} vừa tắt (${gioPhut()})."

        private fun gioPhut() = SimpleDateFormat("HH:mm", Locale.ROOT).format(Date())

        /** Lệnh hiện ở nút Menu của chat với bot. */
        fun lenhMenu(en: Boolean): List<Pair<String, String>> = if (en) listOf(
            "start" to "Open the menu", "remote" to "Remote control", "go" to "Type text on the TV", "kenh" to "Quick channel",
            "apps" to "Apps on the TV", "tivi" to "TV status",
        ) else listOf(
            "start" to "Mở menu", "remote" to "Bảng điều khiển", "go" to "Gõ chữ lên tivi", "kenh" to "Kênh nhanh",
            "apps" to "Ứng dụng trên tivi", "tivi" to "Tình trạng tivi",
        )
    }
}
