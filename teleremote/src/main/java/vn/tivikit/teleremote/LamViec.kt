package vn.tivikit.teleremote

import android.app.ActivityManager
import android.content.Intent
import android.media.AudioManager
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import vn.tivikit.teleremote.XuLyLenh.Den

/**
 * Làm việc người dùng vừa chọn trên tivi rồi trả lời qua [ra]: mở menu, bảng điều khiển (bấm phím qua ADB của tivi), ô Kênh
 * nhanh, app YouTube, tình trạng tivi, tắt tivi (chỉ chủ). Chạy trên luồng của Bot (một việc một lúc).
 */
class LamViec(private val dv: TeleRemoteDichVu, private val ra: Ra) {
    private val en = dv.kho.cauHinh.tiengAnh

    private fun t(vi: String, enS: String) = if (en) enS else vi

    fun lam(d: Den, nd: NguoiDung) {
        val viec = when {
            d.nut != null -> XuLyLenh.viecTuNut(d.nut) ?: run { ra.traLoiNut(d.idNut); return }
            else -> XuLyLenh.viecTuChu(d.chu.orEmpty())
        }
        if (XuLyLenh.chiChu(viec) && nd.vaiTro != VaiTro.CHU) {
            val loi = t("Chỉ chủ tivi mới tắt được tivi.", "Only the TV's owner can turn it off.")
            if (d.nut != null) ra.traLoiNut(d.idNut, loi, canhBao = true) else ra.gui(d.chat, loi)
            return
        }
        when (viec) {
            XuLyLenh.Menu -> ra.gui(d.chat, chao(nd), XuLyLenh.banPhimChinh(nd.vaiTro, en))
            XuLyLenh.DieuKhien -> {
                ra.gui(d.chat, chuDieuKhien(null), XuLyLenh.bangDieuKhien(en))
                ghi(nd, "dieu-khien")
            }
            is XuLyLenh.Phim -> phim(d, viec.ma)
            XuLyLenh.LamMoi -> {
                ra.traLoiNut(d.idNut)
                ra.sua(d.chat, d.idTin, chuDieuKhien(null), XuLyLenh.bangDieuKhien(en))
            }
            XuLyLenh.Kenh -> kenh(d)
            is XuLyLenh.MoKenh -> moKenh(d, nd, viec.o)
            XuLyLenh.YouTube -> youTube(d, nd)
            XuLyLenh.Tivi -> ra.gui(d.chat, tinhTrang())
            XuLyLenh.HoiTat -> ra.gui(d.chat, t("Tắt tivi bây giờ?", "Turn the TV off now?"), XuLyLenh.hoiTat(en))
            XuLyLenh.Tat -> tat(d, nd)
            XuLyLenh.ThoiTat -> {
                ra.traLoiNut(d.idNut)
                ra.sua(d.chat, d.idTin, t("Đã thôi, tivi vẫn bật.", "Cancelled; the TV stays on."))
            }
            XuLyLenh.KhongRo -> ra.gui(d.chat, t("Chọn việc ở bàn phím bên dưới (gõ /start nếu không thấy bàn phím).",
                "Pick from the keyboard below (send /start if you don't see it)."), XuLyLenh.banPhimChinh(nd.vaiTro, en))
        }
    }

    private fun chao(nd: NguoiDung): String {
        val ten = nd.tenHien.ifEmpty { nd.ten }
        val vai = if (nd.vaiTro == VaiTro.CHU) t("chủ", "the owner") else t("thành viên", "a member")
        return t("📺 ${dv.tenTivi()} · Tele Remote\nXin chào $ten! Bạn là $vai của tivi này. Chọn việc ở bàn phím bên dưới.",
            "📺 ${dv.tenTivi()} · Tele Remote\nHi $ten! You're $vai of this TV. Pick from the keyboard below.")
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
        ra.sua(d.chat, d.idTin, chuDieuKhien(XuLyLenh.PHIM[ma]), XuLyLenh.bangDieuKhien(en))
    }

    private fun kenh(d: Den) {
        val ds = dv.dsKenh()
        if (ds.isEmpty()) ra.gui(d.chat, t("Tivi chưa có ô Kênh nhanh nào. Thêm ở TiviKit → Kênh nhanh.",
            "The TV has no Quick channel tiles yet. Add them in TiviKit → Quick channel."))
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

    private fun youTube(d: Den, nd: NguoiDung) {
        val app = APP_YOUTUBE.firstOrNull(dv::coApp)
        if (app == null) {
            ra.gui(d.chat, t("Tivi chưa có SmartTube, TizenTube hay YouTube.", "The TV has no SmartTube, TizenTube or YouTube."))
            return
        }
        val ten = dv.tenApp(app)
        val ok = dv.mo(dv.moApp(app))
        ra.gui(d.chat, if (ok) t("▶️ Đã mở $ten.", "▶️ $ten is open.") else t("Không mở được $ten.", "Couldn't open $ten."))
        if (ok) ghi(nd, "mo|$app")
    }

    private fun tat(d: Den, nd: NguoiDung) {
        ra.traLoiNut(d.idNut)
        val ok = dv.tatTivi()
        ra.sua(d.chat, d.idTin, if (ok) t("📴 Đã tắt tivi.", "📴 The TV is off.")
            else t("Không tắt được tivi (Android 8 cần ADB của tivi).", "Couldn't turn the TV off (Android 8 needs the TV's ADB)."))
        if (ok) ghi(nd, "tat")
    }

    /** Tình trạng tivi: màn hình, app đang mở, âm lượng, RAM, bộ nhớ, đã bật bao lâu, mạng, ADB. */
    private fun tinhTrang(): String {
        val am = dv.getSystemService(AudioManager::class.java)
        val mi = ActivityManager.MemoryInfo().also { dv.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
        val trong = runCatching { StatFs(Environment.getDataDirectory().path).availableBytes }.getOrDefault(0L)
        val man = when (dv.manHinh()) {
            "Awake" -> t("đang bật", "on")
            "Dreaming" -> t("đang ở màn bảo vệ", "screensaver")
            "Asleep", "Dozing" -> t("đang tắt", "off")
            else -> if (dv.dangSang()) t("đang bật", "on") else t("đang tắt", "off")
        }
        return listOf(
            "📺 ${dv.tenTivi()} (${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, Android ${android.os.Build.VERSION.RELEASE})",
            t("Màn hình: $man", "Screen: $man"),
            t("Đang mở: ", "Showing: ") + dv.appDangMo().ifEmpty { t("không rõ", "unknown") },
            t("Âm lượng: ", "Volume: ") + "${am.getStreamVolume(AudioManager.STREAM_MUSIC)}/${am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)}",
            t("RAM trống: ", "Free RAM: ") + "${gb(mi.availMem)} / ${gb(mi.totalMem)} · " + t("Bộ nhớ trống: ", "Free storage: ") + gb(trong),
            t("Đã bật: ", "Up for: ") + thoiGian(SystemClock.elapsedRealtime()),
            t("Mạng: ", "Network: ") + dv.ip().ifEmpty { t("không rõ", "unknown") },
            "ADB: " + if (dv.coAdb()) t("đã nối", "connected") else t("chưa nối (phím ▲▼◀▶ OK chưa dùng được)", "not connected (▲▼◀▶ OK won't work)"),
        ).joinToString("\n")
    }

    /** "1,3 GB" (tiếng Việt dùng dấu phẩy, như TiviKit Launcher) / "1.3 GB". */
    private fun gb(b: Long): String = if (b >= 1L shl 30) String.format(if (en) Locale.US else Locale("vi"), "%.1f GB", b / (1L shl 30).toDouble())
        else "${b / (1L shl 20)} MB"

    private fun thoiGian(ms: Long): String {
        val phut = ms / 60_000
        val gio = phut / 60
        val ngay = gio / 24
        return when {
            ngay > 0 -> t("$ngay ngày ${gio % 24} giờ", "${ngay}d ${gio % 24}h")
            gio > 0 -> t("$gio giờ ${phut % 60} phút", "${gio}h ${phut % 60}m")
            else -> t("$phut phút", "${phut}m")
        }
    }

    private fun chuChuaAdb() = t("Tivi chưa nối ADB của chính nó: mở TiviKit → Tele Remote xem cách nối.",
        "The TV isn't connected to its own ADB yet: open TiviKit → Tele Remote to fix it.")

    private fun ghi(nd: NguoiDung, viec: String) = dv.kho.ghi(nd.ten.ifEmpty { nd.tenHien }, viec)

    companion object {
        /** App YouTube mở bằng nút ▶️ YouTube, theo thứ tự ưu tiên. */
        val APP_YOUTUBE = listOf(
            "org.smarttube.stable", "org.smarttube.beta", "com.liskovsoft.smarttubetv", "com.liskovsoft.smarttubetv.beta",
            "io.gh.reisxd.tizentube.cobalt", "com.google.android.youtube.tv",
        )

        /** Lệnh hiện ở nút Menu của chat với bot. */
        fun lenhMenu(en: Boolean): List<Pair<String, String>> = if (en) listOf(
            "start" to "Open the menu", "remote" to "Remote control", "kenh" to "Quick channel", "tivi" to "TV status",
        ) else listOf(
            "start" to "Mở menu", "remote" to "Bảng điều khiển", "kenh" to "Kênh nhanh", "tivi" to "Tình trạng tivi",
        )
    }
}
