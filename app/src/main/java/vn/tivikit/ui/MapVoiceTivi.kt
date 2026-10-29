package vn.tivikit.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import vn.tivikit.dulieu.KenhNhanh
import vn.tivikit.dulieu.KenhVtvGo
import vn.tivikit.dulieu.MapVoice
import vn.tivikit.t

/**
 * Map Voice (29/10/2026): cài app phụ vn.tivikit.mapvoice lên tivi để câu nói vào nút mic của remote (Google Assistant) tìm
 * nhạc / video thì mở bằng SmartTube, mở kênh thì mở bằng VTVgo / TV360; chọn cách mở, thử một câu gõ tay, xem câu vừa nghe.
 */
class MapVoiceTivi(private val vm: PhienLamViec) {
    var trangThai by mutableStateOf<MapVoice.TrangThai?>(null)
        private set
    /** App phụ đã có trên tivi. */
    var daCai by mutableStateOf(false)
        private set
    /** SmartTube (các bản) đang bật trên tivi. */
    var dsYouTube by mutableStateOf<List<String>>(emptyList())
        private set
    var coVtvGo by mutableStateOf(false)
        private set
    var coTv360 by mutableStateOf(false)
        private set
    /** Trợ lý Google có trên tivi; đang bật. */
    var coTroLy by mutableStateOf(true)
        private set
    var troLyBat by mutableStateOf(true)
        private set
    /** App cùng việc đang bật (Super Voice, MapVoice của com.linor.vn). */
    var appTrung by mutableStateOf<List<String>>(emptyList())
        private set
    /** Cấu hình đang chỉnh trên màn (Lưu mới gửi lên tivi). */
    var cauHinh by mutableStateOf(MapVoice.CauHinh())
    var cauThu by mutableStateOf("")
    var ketQuaThu by mutableStateOf<MapVoice.Viec?>(null)
        private set
    var bao by mutableStateOf("")
        private set

    /** App phụ cần Android 8+. */
    val duocCai: Boolean get() = (vm.thongTin?.sdk ?: 0) >= 26
    val doiChuaLuu: Boolean get() = trangThai != null && cauHinh != trangThai?.cauHinh

    fun mo() = vm.chay(t("Đang đọc Map Voice trên tivi…", "Reading Map Voice on the TV…")) {
        vm.damBaoKetNoi()
        bao = ""; ketQuaThu = null
        docLai()
        vm.man = Man.MAP_VOICE
    }

    fun lamMoi() = vm.chay(t("Đang đọc Map Voice trên tivi…", "Reading Map Voice on the TV…")) {
        vm.damBaoKetNoi(); bao = ""; docLai()
    }

    private suspend fun docLai() {
        val bat = vm.io { vm.tv.goiDangBat() }
        dsYouTube = MapVoice.SMARTTUBE.filter { it in bat }
        coVtvGo = KenhVtvGo.GOI in bat
        coTv360 = KenhNhanh.TV360 in bat
        troLyBat = MapVoice.TRO_LY in bat
        coTroLy = troLyBat || vm.io { vm.tv.goiDangTat(MapVoice.TRO_LY) }
        appTrung = MapVoice.APP_TRUNG.filter { it in bat }
        daCai = vm.io { vm.tv.mapVoiceDaCai() }
        docTrangThai()
        cauHinh = trangThai?.cauHinh ?: MapVoice.CauHinh(
            appYouTube = dsYouTube.firstOrNull().orEmpty(),
            appKenh = if (coVtvGo || !coTv360) MapVoice.APP_VTVGO else MapVoice.APP_TV360,
        )
    }

    private suspend fun docTrangThai() {
        trangThai = if (daCai) vm.io { vm.tv.docMapVoice() }?.let(MapVoice::trangThai) else null
    }

    /**
     * Danh sách kênh gửi lên tivi: mọi kênh VTVgo (tải từ VTVgo, nhớ trong máy) và kênh TV360 biết số (gợi ý của Kênh nhanh,
     * ô Kênh nhanh đã đọc). "" nếu tivi không có app xem kênh nào.
     */
    private suspend fun dsKenh(): String {
        if (!coVtvGo && !coTv360) return ""
        val go = vm.dsKenhGo.ifEmpty { vm.io { KenhVtvGo.docNho(vm.ctx).ifEmpty { KenhVtvGo.tai(vm.ctx) } } }
        val tv360 = KenhNhanh.GOI_Y + vm.kenhNhanh.ds.mapNotNull { it.kenh }
        return MapVoice.dsKenh(go, tv360)
    }

    /** Cài / cập nhật app phụ, gửi cài đặt và danh sách kênh, bật dịch vụ; chờ vòng nghe nối ADB rồi báo bước tiếp. */
    fun bat() = vm.chay(t("Đang cài Map Voice lên tivi…", "Installing Map Voice on the TV…")) {
        if (!duocCai) error(t("Map Voice cần Android 8 trở lên, tivi này là Android ${vm.thongTin?.android}.",
            "Map Voice needs Android 8 or later; this TV runs Android ${vm.thongTin?.android}."))
        vm.damBaoKetNoi()
        bao = ""; ketQuaThu = null
        val apk = vm.io { vn.tivikit.CapNhat.moAsset(vm.ctx, MapVoice.APK).readBytes() }
        vm.thongBao = t("Đang tải danh sách kênh…", "Loading the channel list…")
        val kenh = dsKenh()
        vm.io { vm.tv.caiMapVoice(apk, cauHinh, kenh) { s -> vm.thongBao = s } }
        daCai = true
        // Dịch vụ vừa bật thì nối ADB của chính tivi: tivi chưa biết khoá của Map Voice thì hỏi trên màn hình.
        vm.thongBao = t("Chờ Map Voice nối ADB của tivi…", "Waiting for Map Voice to reach the TV's ADB…")
        for (i in 1..10) {
            delay(1_500)
            docTrangThai()
            if (trangThai?.adb in listOf("da-noi", "dang-hoi", "tu-choi", "khong-co")) break
        }
        bao = baoTrangThai()
    }

    /** Lời báo theo trạng thái vòng nghe: xong rồi, hay người dùng cần làm gì trên tivi. */
    private fun baoTrangThai(): String {
        val tt = trangThai ?: return t("Đã cài nhưng chưa đọc lại được từ tivi. Bấm Làm mới.", "Installed, but couldn't read it back from the TV. Tap Refresh.")
        return when {
            !tt.dichVuChay -> t("Đã cài, nhưng dịch vụ Map Voice chưa chạy trên tivi: vào Cài đặt → Trợ năng của tivi, bật \"Map Voice (TiviKit)\", rồi bấm Làm mới.",
                "Installed, but the Map Voice service isn't running on the TV: open the TV's Settings → Accessibility, turn on \"Map Voice (TiviKit)\", then tap Refresh.")
            tt.adb == "dang-hoi" -> t("Trên tivi đang hiện hộp \"Cho phép gỡ lỗi?\": tích \"Luôn cho phép từ máy tính này\" rồi chọn OK bằng remote. Xong bấm Làm mới.",
                "The TV is showing \"Allow debugging?\": tick \"Always allow from this computer\" and press OK with the remote. Then tap Refresh.")
            tt.adb == "da-noi" -> t("Xong. Bấm nút mic trên remote và nói, VD \"mở nhạc piano trên YouTube\".",
                "Done. Press the mic button on the remote and speak, e.g. \"play piano music on YouTube\".")
            else -> moTaAdb(tt.adb)
        }
    }

    /** Gửi cài đặt đang chỉnh (và danh sách kênh mới) lên tivi. */
    fun luu() = vm.chay(t("Đang lưu lên tivi…", "Saving to the TV…")) {
        vm.damBaoKetNoi()
        bao = ""
        if (!vm.io { vm.tv.datMapVoice(cauHinh) }) error(t("Tivi không nhận cài đặt. Bấm Làm mới rồi thử lại.", "The TV rejected the settings. Tap Refresh and try again."))
        dsKenh().takeIf { it.isNotEmpty() }?.let { k -> vm.io { vm.tv.guiKenhMapVoice(k) } }
        docTrangThai()
        bao = t("Đã lưu lên tivi.", "Saved to the TV.")
    }

    /** Xem Map Voice hiểu câu gõ thử thế nào (tivi chưa làm gì). */
    fun xem() = vm.chay(t("Đang hỏi tivi…", "Asking the TV…")) {
        vm.damBaoKetNoi()
        bao = ""
        val (v, loi) = vm.io { vm.tv.xemMapVoice(cauThu.trim()) }
        ketQuaThu = v ?: error(t("Map Voice trên tivi không trả lời ($loi).", "Map Voice on the TV didn't answer ($loi)."))
    }

    /** Làm câu gõ thử trên tivi như vừa nói vào mic (mở SmartTube / kênh thật). */
    fun thu() = vm.chay(t("Đang chạy thử trên tivi…", "Trying it on the TV…")) {
        vm.damBaoKetNoi()
        bao = ""
        val (v, loi) = vm.io { vm.tv.thuMapVoice(cauThu.trim()) }
        if (loi == "dich-vu-tat") error(t("Dịch vụ Map Voice trên tivi đang tắt: bấm Bật lại.", "The Map Voice service on the TV is off: tap Turn on again."))
        ketQuaThu = v ?: error(t("Map Voice trên tivi không trả lời ($loi).", "Map Voice on the TV didn't answer ($loi)."))
        docTrangThai()
    }

    /** Nối lại ADB (người dùng đã từ chối hộp hỏi, hay vừa bật lại ADB qua mạng). */
    fun noiLai() = vm.chay(t("Đang nối lại…", "Reconnecting…")) {
        vm.damBaoKetNoi()
        if (!vm.io { vm.tv.noiLaiMapVoice() }) error(t("Dịch vụ Map Voice trên tivi đang tắt: bấm Bật lại.", "The Map Voice service on the TV is off: tap Turn on again."))
        for (i in 1..6) {
            delay(1_000)
            docTrangThai()
            if (trangThai?.adb in listOf("da-noi", "dang-hoi")) break
        }
        bao = baoTrangThai()
    }

    /** Bật lại Trợ lý Google (tắt lúc dọn tivi hay làm nhẹ máy): Map Voice đọc câu nói từ nó. */
    fun batTroLy() = vm.chay(t("Đang bật lại Trợ lý Google…", "Turning the Google Assistant back on…")) {
        vm.damBaoKetNoi()
        if (!vm.io { vm.tv.batLaiGoi(MapVoice.TRO_LY, vm.saoLuu) }) error(t("Tivi không bật lại được Trợ lý Google.", "The TV couldn't turn the Google Assistant back on."))
        docLai()
        bao = t("Đã bật lại Trợ lý Google.", "The Google Assistant is back on.")
    }

    /** Tắt app cùng việc (Super Voice…) như Quản lý app: có ghi vào bản sao lưu, Khôi phục bật lại được. */
    fun tatAppTrung(goi: String) = vm.chay(t("Đang tắt $goi…", "Disabling $goi…")) {
        require(goi in MapVoice.APP_TRUNG)
        vm.damBaoKetNoi()
        val info = vm.thongTin ?: return@chay
        val sl = vm.saoLuu ?: vm.io { vm.tv.damBaoSaoLuu(info, vm.getprop, vm.ip.trim()) }.also { vm.saoLuu = it }
        val kq = vm.io { vm.tv.tatGoi(listOf(goi), sl, epSetupwraith = false) {} }
        (kq.boQua + kq.loi).firstOrNull()?.let { error(it.second) }
        docLai()
        bao = t("Đã tắt $goi. Bật lại ở Quản lý app hay bằng Khôi phục tivi.", "$goi is disabled. Re-enable it under Manage apps or by restoring the TV.")
    }

    fun go() = vm.chay(t("Đang gỡ Map Voice…", "Removing Map Voice…")) {
        vm.damBaoKetNoi()
        vm.io { vm.tv.goMapVoice() }
        daCai = false; trangThai = null; ketQuaThu = null
        bao = t("Đã gỡ Map Voice khỏi tivi. Câu nói về lại Trợ lý Google như trước.", "Map Voice is removed from the TV. Voice requests go back to the Google Assistant as before.")
    }

    fun quen() {
        trangThai = null; daCai = false; dsYouTube = emptyList(); coVtvGo = false; coTv360 = false; coTroLy = true; troLyBat = true
        appTrung = emptyList(); cauHinh = MapVoice.CauHinh(); ketQuaThu = null; bao = ""
    }

    companion object {
        /** Trạng thái vòng nghe của app phụ, nói cho người dùng. */
        fun moTaAdb(adb: String): String = when (adb) {
            "da-noi" -> t("Đang nghe câu nói", "Listening")
            "dang-hoi" -> t("Tivi đang hỏi \"Cho phép gỡ lỗi?\": chọn Luôn cho phép rồi OK", "The TV is asking \"Allow debugging?\": choose Always allow, then OK")
            "tu-choi" -> t("Tivi đã không cho phép gỡ lỗi: bấm Nối lại rồi chọn Luôn cho phép trên tivi", "The TV didn't allow debugging: tap Reconnect and choose Always allow on the TV")
            "khong-co" -> t("Tivi không mở ADB qua mạng (cổng 5555): bật lại Gỡ lỗi USB / ADB qua mạng trong Tuỳ chọn nhà phát triển rồi bấm Nối lại. Tivi chỉ có Gỡ lỗi không dây (mã ghép 6 số) thì Map Voice chưa dùng được.",
                "The TV's network ADB (port 5555) is off: turn USB debugging / ADB over network back on in Developer options, then tap Reconnect. TVs with only Wireless debugging (6-digit pairing) can't use Map Voice yet.")
            "dang-noi" -> t("Đang nối ADB của tivi…", "Connecting to the TV's ADB…")
            "tat" -> t("Dịch vụ đang tắt", "The service is off")
            else -> t("Chưa nối được ADB của tivi, đang thử lại", "Couldn't reach the TV's ADB yet; retrying")
        }

        /** Việc Map Voice làm cho một câu, nói cho người dùng. */
        fun moTa(v: MapVoice.Viec): String = when (v.loai) {
            "yt-phat" -> t("Mở SmartTube, phát video đầu tiên khi tìm \"${v.chiTiet}\"", "Open SmartTube and play the first result for \"${v.chiTiet}\"")
            "yt-tim" -> t("Mở SmartTube, tìm \"${v.chiTiet}\"", "Open SmartTube and search for \"${v.chiTiet}\"")
            "yt-app" -> t("Mở SmartTube", "Open SmartTube")
            "kenh" -> t("Mở kênh ${v.chiTiet} bằng ", "Open channel ${v.chiTiet} in ") + if (v.app == MapVoice.APP_TV360) "TV360" else "VTVgo"
            "bo-qua" -> t("Để Trợ lý Google làm như thường (không phải câu tìm nhạc, video hay mở kênh)", "Left to the Google Assistant (not a music, video or channel request)")
            "loi" -> when (v.chiTiet) {
                "khong-co-smarttube" -> t("Không làm: tivi chưa có SmartTube", "Nothing done: SmartTube isn't on the TV")
                "khong-mo-duoc" -> t("Không mở được app", "Couldn't open the app")
                else -> t("Lỗi trên tivi: ", "Error on the TV: ") + v.chiTiet
            }
            else -> v.loai
        }
    }
}
