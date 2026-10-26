package vn.tivikit.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import vn.tivikit.dulieu.KenhNhanh
import vn.tivikit.dulieu.KenhVtvGo
import vn.tivikit.dulieu.PhimRieng
import vn.tivikit.t

/**
 * Kênh nhanh (25/10/2026, muốn một nút mở thẳng VTV1; cùng ngày thành nhiều ô; 26/10/2026 gom cả ô kênh VTVgo về đây),
 * mỗi app xem truyền hình một thẻ:
 * - TV360: tối đa KenhNhanh.SO_O ô kênh, mỗi ô một app phụ trên tivi (module kenh/) mang một kênh. Thêm / sửa ô (cài app
 *   của ô, đặt kênh, đặt tên ô trên Home sạch theo tên kênh, thêm vào dock nếu muốn), bấm thử, gán nút remote (màn Nút
 *   remote chọn sẵn ô đó), bỏ ô.
 * - VTVgo: ô link_tiles của Home sạch 3.0 (KenhVtvGo) có ảnh logo kênh, tối đa KenhVtvGo.TOI_DA ô ở đầu dock; trước đây
 *   chọn ở màn Home sạch. Lưu thẳng vào config.json đang có trên tivi, không đụng giao diện. Lựa chọn nằm ở vm.kenhChon,
 *   chung với lúc Áp dụng Home sạch.
 */
class KenhNhanhTivi(private val vm: PhienLamViec) {
    /** Một ô kênh đã cài trên tivi: số ô, kênh đang đặt (null = không đọc được), có trong dock Home sạch không. */
    data class O(val so: Int, val kenh: KenhNhanh.Kenh?, val trongDock: Boolean) {
        val goi: String get() = KenhNhanh.goiCua(so)
    }

    /** Các ô đang có trên tivi, theo số ô. */
    var ds by mutableStateOf<List<O>>(emptyList())
        private set

    // Ô soạn: thêm ô mới ([dangSua] null) hay sửa ô [dangSua].
    var so by mutableStateOf("1")
    var ten by mutableStateOf("VTV1")
    var trongDock by mutableStateOf(true)
    var dangSua by mutableStateOf<Int?>(null)
        private set

    /** TV360 bản tivi đang cài và bật. */
    var coTv360 by mutableStateOf(true)
        private set
    var bao by mutableStateOf("")
        private set
    /** Lời báo là lỗi / không như mong đợi (tô màu cam). */
    var baoLech by mutableStateOf(false)
        private set

    /** Thẻ đang xem: 0 TV360, 1 VTVgo. */
    var nguon by mutableStateOf(0)

    /** Ô kênh VTVgo đang có trên tivi (đọc lúc mở màn, đổi sau khi lưu): so với vm.kenhChon để biết đã lưu chưa. */
    var kenhGoTrenTv by mutableStateOf<List<Pair<String, String>>>(emptyList())
        private set
    val goChuaLuu: Boolean get() = vm.kenhChon.toList() != kenhGoTrenTv
    /** Home sạch trên tivi không phải bản đi kèm app (bản trước 1.2.0 chưa có ô kênh): Áp dụng ở màn Home sạch trước. */
    var homeCu by mutableStateOf(false)
        private set
    var baoGo by mutableStateOf("")
        private set

    fun mo() = vm.chay(t("Đang đọc Kênh nhanh trên tivi…", "Reading Quick channel on the TV…")) {
        vm.damBaoKetNoi()
        bao = ""; baoLech = false; dangSua = null; baoGo = ""
        docLai(napGo = true)
        // Ô soạn gợi ý kênh hay xem đầu tiên chưa có ô nào.
        KenhNhanh.GOI_Y.firstOrNull { g -> ds.none { it.kenh?.so == g.so } }?.let { chon(it) }
        trongDock = true
        // Mở sẵn thẻ của app tivi đang dùng: chưa có ô TV360 mà đã có ô VTVgo, hoặc tivi chỉ có VTVgo, thì thẻ VTVgo.
        nguon = if (ds.isEmpty() && (kenhGoTrenTv.isNotEmpty() || (!coTv360 && vm.coVtvGo))) 1 else 0
        vm.man = Man.KENH_NHANH
    }

    /**
     * Đọc lại các ô trên tivi (app nào đã cài, kênh của nó, có trong dock không) và tên ô trên Home sạch. [napGo]: đọc cả
     * ô VTVgo vào vm.kenhChon (lúc mở màn; lưu ô TV360 xong thì giữ nguyên lựa chọn VTVgo đang soạn).
     */
    private suspend fun docLai(napGo: Boolean = false) {
        val tt = vm.io { vm.tv.docTrangThai() }
        vm.trangThai = tt
        coTv360 = KenhNhanh.TV360 in tt.caiDat && KenhNhanh.TV360 !in tt.daTat
        val j = if (vm.daCoHomeSach) vm.io { vm.tv.docCauHinhTv() } else null
        val dock = j?.let { KenhNhanh.trongDock(it) }.orEmpty()
        j?.let { vm.tenKenh.clear(); vm.tenKenh.putAll(KenhNhanh.tenTrong(it)) }
        ds = (1..KenhNhanh.SO_O).filter { KenhNhanh.goiCua(it) in tt.caiDat }.map { o ->
            O(o, KenhNhanh.tuJson(vm.io { vm.tv.docKenh(o) }), KenhNhanh.goiCua(o) in dock)
        }
        if (napGo) {
            kenhGoTrenTv = j?.let { KenhVtvGo.docTu(it) }.orEmpty()
            vm.kenhChon.clear(); vm.kenhChon.addAll(kenhGoTrenTv)
            homeCu = vm.daCoHomeSach && !vm.io { vm.tv.homeLaBanDiKem(apkHome()) }
        }
    }

    private fun apkHome(): ByteArray = vn.tivikit.CapNhat.moAsset(vm.ctx, vm.tv.apkHome).use { it.readBytes() }

    /**
     * Lưu các ô kênh VTVgo đang chọn lên Home sạch: ảnh ô, link_tiles và đầu dock (ThaoTacTv.ghiKenhVtvGo). Home sạch cũ
     * thì không tự cập nhật ở đây: lần đổi khoá gỡ Home sạch là mất ảnh nền, màn Home sạch mới gửi lại được.
     */
    fun luuVtvGo() = vm.chay(t("Đang lưu ô kênh VTVgo…", "Saving the VTVgo tiles…")) {
        vm.damBaoKetNoi()
        baoGo = ""
        homeCu = !vm.io { vm.tv.homeLaBanDiKem(apkHome()) }
        if (homeCu) error(t("Home sạch trên tivi chưa phải bản mới nhất nên chưa có ô kênh. Bấm Mở Home sạch, Áp dụng lên tivi một lần (app tự cập nhật, giữ giao diện), rồi quay lại lưu kênh.",
            "The clean Home on the TV isn't the latest yet, so it has no channel tiles. Tap Open Clean Home, press Apply to the TV once (the app updates it and keeps your look), then come back and save."))
        val kenh = vm.kenhChon.toList()
        if (kenh.isNotEmpty()) vm.thongBao = t("Đang lấy ảnh ${kenh.size} ô kênh…", "Fetching ${kenh.size} channel tile pictures…")
        val anh = vm.anhKenhChon()
        vm.thongBao = t("Đang ghi lên Home sạch…", "Writing to the clean Home…")
        if (!vm.io { vm.tv.ghiKenhVtvGo(kenh, anh) }) error(t("Home sạch đang dùng dock mặc định: bấm Mở Home sạch, Áp dụng lên tivi một lần, rồi lưu lại kênh.",
            "The clean Home uses its default dock: tap Open Clean Home, press Apply to the TV once, then save the channels again."))
        kenhGoTrenTv = kenh
        baoGo = if (kenh.isEmpty()) t("Đã bỏ hết ô kênh VTVgo khỏi Home sạch.", "All VTVgo tiles are removed from the clean Home.")
            else t("Đã lưu ${kenh.size} ô kênh ở đầu dock Home sạch: ${kenh.joinToString { it.first }}. Bấm Home trên remote để xem.",
                "Saved ${kenh.size} channel tiles at the start of the clean Home's dock: ${kenh.joinToString { it.first }}. Press Home on the remote to see them.")
    }

    /** Sang màn Home sạch (cài, hoặc Áp dụng để cập nhật Home sạch cũ); bấm quay lại thì về màn chính. */
    fun moHomeSach() {
        if (vm.daCoHomeSach) { vm.man = Man.CHINH; vm.moDoiTheme() } else vm.boQuaDon(Man.CHINH)
    }

    fun chon(k: KenhNhanh.Kenh) { so = k.so.trimStart('0'); ten = k.ten }

    /** Đưa ô [o] lên ô soạn để sửa kênh / dock. */
    fun sua(o: O) { dangSua = o.so; o.kenh?.let { chon(it) }; trongDock = o.trongDock; bao = "" }

    fun thoiSua() { dangSua = null; bao = "" }

    /** Kênh theo ô nhập; lỗi nếu số kênh sai. */
    private fun kenhNhap(): KenhNhanh.Kenh {
        val s = KenhNhanh.chuanSo(so) ?: error(t("Số kênh từ 1 đến 999, như số TV360 hiện trong danh sách kênh (VTV1 là 1).",
            "The channel number is 1 to 999, as TV360 shows it in its channel list (VTV1 is 1)."))
        return KenhNhanh.Kenh(s, KenhNhanh.tenSach(ten).ifBlank { t("Kênh ${s.trimStart('0')}", "Channel ${s.trimStart('0')}") })
    }

    /** Thêm ô mới (ô trống đầu tiên) hoặc lưu ô đang sửa: cài / cập nhật app của ô, đặt kênh, tên ô và dock trên Home sạch. */
    fun luu() = vm.chay(t("Đang lưu ô kênh…", "Saving the channel tile…")) {
        val k = kenhNhap()
        val o = dangSua ?: (1..KenhNhanh.SO_O).firstOrNull { s -> ds.none { it.so == s } }
            ?: error(t("Đã đủ ${KenhNhanh.SO_O} ô kênh: bỏ bớt một ô hoặc sửa một ô có sẵn.", "All ${KenhNhanh.SO_O} channel tiles are used: remove one or edit an existing one."))
        ds.firstOrNull { it.so != o && it.kenh?.so == k.so }?.let {
            error(t("${k.ten} đã có ở ô ${it.so}.", "${k.ten} is already on tile ${it.so}."))
        }
        vm.damBaoKetNoi()
        bao = ""; baoLech = false
        val goi = KenhNhanh.goiCua(o)
        val apk = vm.io { vn.tivikit.CapNhat.moAsset(vm.ctx, KenhNhanh.apkCua(o)).readBytes() }
        vm.io { vm.tv.caiKenh(o, apk, KenhNhanh.json(k)) { s -> vm.thongBao = s } }
        var them = ""
        if (vm.daCoHomeSach) {
            vm.thongBao = t("Đặt ô ${k.ten} trên Home sạch…", "Setting the ${k.ten} tile on the clean Home…")
            val dock = vm.io { vm.tv.ghiOKenh(goi, k.ten, trongDock, DOCK_TOI_DA) }
            vm.tenKenh[goi] = k.ten
            if (dock == true) vm.dongBoDock(goi, trongDock)
            vm.veApp()
            them = when {
                dock == false -> t(" Dock đã đủ $DOCK_TOI_DA ô nên ô ${k.ten} nằm trong danh sách app; bỏ bớt một app khỏi dock ở mục Home sạch rồi lưu lại.",
                    " The dock already has $DOCK_TOI_DA tiles, so the ${k.ten} tile is in the app list; remove an app from the dock under Clean Home and save again.")
                dock == null && trongDock -> t(" Home sạch đang dùng dock mặc định: thêm ô ${k.ten} vào dock ở mục Home sạch.",
                    " The clean Home uses its default dock: add the ${k.ten} tile to the dock under Clean Home.")
                trongDock -> t(" Ô ${k.ten} nằm trong dock Home sạch.", " The ${k.ten} tile is in the clean Home's dock.")
                else -> t(" Ô ${k.ten} nằm trong danh sách app của Home sạch.", " The ${k.ten} tile is in the clean Home's app list.")
            }
        }
        dangSua = null
        docLai()
        bao = t("Đã lưu ô $o: ${k.ten} (số ${k.so.trimStart('0')} trên TV360).", "Saved tile $o: ${k.ten} (number ${k.so.trimStart('0')} in TV360).") + them
        // Ô soạn chuyển sang kênh hay xem kế tiếp chưa có ô.
        KenhNhanh.GOI_Y.firstOrNull { g -> ds.none { it.kenh?.so == g.so } }?.let { chon(it) }
    }

    /** Mở ô [o] trên tivi như bấm ô, xem TV360 có lên không. */
    fun thu(o: O) = vm.chay(t("Đang mở ô ${o.kenh?.ten ?: o.so} trên tivi…", "Opening tile ${o.kenh?.ten ?: o.so} on the TV…")) {
        vm.damBaoKetNoi()
        bao = ""; baoLech = false
        val out = vm.io { vm.tv.moKenhNhanh(o.so) }
        if (out.contains("Error")) error(t("Tivi không mở được ô này: ${out.trim().lines().lastOrNull()}. Bấm Sửa rồi lưu lại ô.",
            "The TV couldn't open this tile: ${out.trim().lines().lastOrNull()}. Tap Edit and save the tile again."))
        // TV360 mở nguội mất vài giây (màn chào, tải danh sách kênh).
        var g = ""
        for (i in 1..10) {
            kotlinx.coroutines.delay(1000)
            g = vm.io { vm.tv.goiDangHien() }
            if (g == KenhNhanh.TV360) break
        }
        val tenK = o.kenh?.ten ?: t("kênh của ô ${o.so}", "tile ${o.so}'s channel")
        baoLech = g != KenhNhanh.TV360
        bao = if (!baoLech) t("TV360 đã mở. Nhìn tivi: đang phát $tenK là được. TV360 chưa đăng nhập hoặc hiện quảng cáo thì bấm qua rồi thử lại.",
                "TV360 is open. Look at the TV: if $tenK is playing, it works. If TV360 asks you to sign in or shows an ad, get past it and test again.")
            else if (!coTv360) t("Tivi chưa có TV360 (hoặc đang bị tắt): cài TV360 từ CH Play, hoặc bật lại ở Quản lý app.",
                "TV360 isn't on the TV (or is disabled): install it from the Play Store, or re-enable it in Manage apps.")
            else t("TV360 chưa lên trước mặt (đang thấy ${g.ifBlank { "không rõ" }}). Thử lại; vẫn vậy thì mở TV360 một lần bằng tay rồi thử.",
                "TV360 didn't come to the front (seeing ${g.ifBlank { "unknown" }}). Try again; if it persists, open TV360 by hand once and test.")
    }

    /** Sang màn Nút remote với ô [o] chọn sẵn. */
    fun ganNut(o: O) { vm.ganPhim.mo(goiSan = o.goi) }

    fun moCHPlay() = vm.chay(t("Đang mở CH Play trên tivi…", "Opening the Play Store on the TV…")) {
        vm.damBaoKetNoi()
        val out = vm.io { vm.tv.moCHPlay(KenhNhanh.TV360) }
        if (out.contains("Error")) error(t("Tivi không mở được CH Play: ${out.trim().lines().lastOrNull()}", "The TV couldn't open the Play Store: ${out.trim().lines().lastOrNull()}"))
        bao = t("Đã mở trang TV360 trên CH Play của tivi: cài xong thì mở lại màn này.", "TV360's Play Store page is open on the TV: install it, then reopen this screen.")
        baoLech = false
    }

    /** Bỏ ô [o]: gỡ app của ô, bỏ tên ô, ô trong dock và các nút remote đang gán cho nó. */
    fun bo(o: O) = vm.chay(t("Đang bỏ ô ${o.kenh?.ten ?: o.so}…", "Removing tile ${o.kenh?.ten ?: o.so}…")) {
        vm.damBaoKetNoi()
        bao = ""; baoLech = false
        vm.io { vm.tv.goKenh(o.so) }
        if (vm.daCoHomeSach) {
            val j = vm.io { vm.tv.docCauHinhTv() }
            val phim = j?.let { PhimRieng.docTu(it) }.orEmpty()
            val con = phim.filter { PhimRieng.dichCua(it) != o.goi }
            if (con.size != phim.size) { vm.io { vm.tv.ghiPhimRieng(con) }; vm.phimRieng.clear(); vm.phimRieng.addAll(con) }
            vm.io { vm.tv.ghiOKenh(o.goi, null, false, DOCK_TOI_DA) }
            vm.tenKenh.remove(o.goi)
            vm.dongBoDock(o.goi, false)
            vm.veApp()
        }
        if (dangSua == o.so) dangSua = null
        docLai()
        bao = t("Đã bỏ ô ${o.kenh?.ten ?: o.so} khỏi tivi.", "Tile ${o.kenh?.ten ?: o.so} is removed from the TV.")
    }

    /** Tên hiện của ô kênh [goi] ở màn Nút remote / Hẹn giờ: "Kênh nhanh: VTV3"; null nếu [goi] không phải ô kênh. */
    fun tenO(goi: String): String? {
        val o = KenhNhanh.oCua(goi) ?: return null
        val tenK = vm.tenKenh[goi] ?: ds.firstOrNull { it.so == o }?.kenh?.ten
        return if (tenK != null) t("Kênh nhanh: $tenK", "Quick channel: $tenK") else t("Kênh nhanh $o", "Quick channel $o")
    }

    /** Kết nối tivi khác. */
    fun quen() {
        ds = emptyList(); dangSua = null; bao = ""; baoLech = false; coTv360 = true
        nguon = 0; kenhGoTrenTv = emptyList(); homeCu = false; baoGo = ""
    }
}
