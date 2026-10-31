package vn.tivikit.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import vn.tivikit.dulieu.KenhFpt
import vn.tivikit.dulieu.KenhNhanh
import vn.tivikit.dulieu.KenhVtvGo
import vn.tivikit.dulieu.PhimRieng
import vn.tivikit.dulieu.TiviKitLauncher
import vn.tivikit.t

/**
 * Kênh nhanh (25/10/2026, muốn một nút mở thẳng VTV1; cùng ngày thành nhiều ô; 26/10/2026 thêm ô VTVgo; 31/10/2026 ô
 * FPT Play): tối đa KenhNhanh.SO_O ô kênh, mỗi ô một app phụ trên tivi (module kenh/) mở thẳng một kênh trên TV360, VTVgo
 * hoặc FPT Play. Thêm / sửa
 * ô (cài app của ô, đặt kênh, đặt tên ô theo tên kênh và logo kênh trên Home sạch và TiviKit Launcher, thêm vào dock Home
 * sạch nếu muốn), bấm thử, gán nút remote (màn Nút remote chọn sẵn ô đó), bỏ ô. Ô kênh VTVgo kiểu cũ (ô link của Home
 * sạch, KenhVtvGo; không gán được nút remote) chuyển được thành ô ở đây.
 */
class KenhNhanhTivi(private val vm: PhienLamViec) {
    /** Một ô kênh đã cài trên tivi: số ô, kênh đang đặt (null = không đọc được), có trong dock Home sạch không. */
    data class O(val so: Int, val kenh: KenhNhanh.Kenh?, val trongDock: Boolean) {
        val goi: String get() = KenhNhanh.goiCua(so)
    }

    /** Các ô đang có trên tivi, theo số ô. */
    var ds by mutableStateOf<List<O>>(emptyList())
        private set

    // Ô soạn: thêm ô mới ([dangSua] null) hay sửa ô [dangSua]. [app]: 0 = TV360 (số + tên kênh), 1 = VTVgo ([kenhGo]),
    // 2 = FPT Play ([kenhFpt]).
    var app by mutableStateOf(0)
    var so by mutableStateOf("1")
    var ten by mutableStateOf("VTV1")
    /** Kênh VTVgo của ô soạn: (tên, mã kênh). */
    var kenhGo by mutableStateOf<Pair<String, String>?>(null)
        private set
    /** Kênh FPT Play của ô soạn: (tên, mã kênh). */
    var kenhFpt by mutableStateOf<Pair<String, String>?>(null)
        private set
    /** Danh sách kênh FPT Play (đóng sẵn trong app, KenhFpt). */
    var dsFpt by mutableStateOf<List<KenhFpt.Kenh>>(emptyList())
        private set
    var moChonFpt by mutableStateOf(false)
    var trongDock by mutableStateOf(true)
    var dangSua by mutableStateOf<Int?>(null)
        private set

    /** TV360 bản tivi đang cài và bật. */
    var coTv360 by mutableStateOf(true)
        private set
    /** FPT Play bản tivi đang cài và bật. */
    var coFpt by mutableStateOf(false)
        private set
    /** Ô kênh VTVgo kiểu cũ đang có trên Home sạch (link_tiles): (tên, mã kênh). */
    var kenhGoCu by mutableStateOf<List<Pair<String, String>>>(emptyList())
        private set
    /** Home sạch trên tivi không phải bản đi kèm app: bản cũ không hiện logo kênh của ô, cần Áp dụng ở màn Home sạch. */
    var homeCu by mutableStateOf(false)
        private set
    var bao by mutableStateOf("")
        private set
    /** Lời báo là lỗi / không như mong đợi (tô màu cam). */
    var baoLech by mutableStateOf(false)
        private set

    fun mo() = vm.chay(t("Đang đọc Kênh nhanh trên tivi…", "Reading Quick channel on the TV…")) {
        vm.damBaoKetNoi()
        bao = ""; baoLech = false; dangSua = null
        docLai()
        // Ô soạn mở sẵn app tivi đang có (không có TV360 thì VTVgo, rồi FPT Play), gợi ý kênh hay xem đầu tiên chưa có ô.
        app = when {
            coTv360 -> 0
            vm.coVtvGo -> 1
            coFpt -> 2
            else -> 0
        }
        goiYKeTiep()
        trongDock = true
        vm.man = Man.KENH_NHANH
    }

    /**
     * Đọc lại các ô trên tivi (app nào đã cài, kênh của nó, có trong dock không), tên ô và ô VTVgo kiểu cũ trên Home sạch.
     * Ô kiểu cũ cũng vào vm.kenhChon để Áp dụng Home sạch giữ đúng những gì tivi đang có.
     */
    private suspend fun docLai() {
        val tt = vm.io { vm.tv.docTrangThai() }
        vm.trangThai = tt
        coTv360 = KenhNhanh.TV360 in tt.caiDat && KenhNhanh.TV360 !in tt.daTat
        coFpt = KenhNhanh.FPTPLAY in tt.caiDat && KenhNhanh.FPTPLAY !in tt.daTat
        if (dsFpt.isEmpty()) dsFpt = vm.io { KenhFpt.ds(vm.ctx) }
        val j = if (vm.daCoHomeSach) vm.io { vm.tv.docCauHinhTv() } else null
        val dock = j?.let { KenhNhanh.trongDock(it) }.orEmpty()
        j?.let { vm.tenKenh.clear(); vm.tenKenh.putAll(KenhNhanh.tenTrong(it)); vm.anhOKenh.clear(); vm.anhOKenh.putAll(KenhNhanh.anhTrong(it)) }
        ds = (1..KenhNhanh.SO_O).filter { KenhNhanh.goiCua(it) in tt.caiDat }.map { o ->
            O(o, KenhNhanh.tuJson(vm.io { vm.tv.docKenh(o) }), KenhNhanh.goiCua(o) in dock)
        }
        kenhGoCu = j?.let { KenhVtvGo.docTu(it) }.orEmpty()
        vm.kenhChon.clear(); vm.kenhChon.addAll(kenhGoCu)
        homeCu = vm.daCoHomeSach && !vm.io { vm.tv.homeLaBanDiKem(vn.tivikit.CapNhat.moAsset(vm.ctx, vm.tv.apkHome).use { it.readBytes() }) }
    }

    /**
     * Logo kênh [k] cho ô: ảnh kênh của VTVgo (ô VTVgo theo mã kênh, ô TV360 theo tên trùng một kênh VTVgo: VTV1–9, HTV9,
     * Hà Nội 1, ANTV, QPVN…), ô FPT Play lấy ảnh kênh của FPT Play; tải một lần rồi cất (KenhVtvGo.anhO). null nếu không có /
     * không tải được: ô hiện biểu tượng và tên như trước. App không đóng sẵn logo của đài. [dsGo] = danh sách kênh VTVgo đã
     * lấy (nhiều ô một lượt).
     */
    private suspend fun logo(k: KenhNhanh.Kenh, dsGo: List<KenhVtvGo.Kenh>? = null): ByteArray? = vm.io {
        if (k.laFpt) return@io KenhVtvGo.anhO(vm.ctx, k.ma, KenhFpt.ds(vm.ctx).firstOrNull { it.ma == k.ma }?.anh, KenhVtvGo.THU_MUC_FPT)
        val ds = dsGo ?: layDsGo()
        if (k.laVtvGo) KenhVtvGo.anhO(vm.ctx, k.ma, ds.firstOrNull { it.ma == k.ma }?.anh)
        else KenhVtvGo.timTheoTen(ds, k.ten)?.let { KenhVtvGo.anhO(vm.ctx, it.ma, it.anh) }
    }

    /** Danh sách kênh VTVgo: đang có, đã lưu, không thì tải (gọi trên luồng IO). */
    private fun layDsGo() = vm.dsKenhGo.ifEmpty { KenhVtvGo.docNho(vm.ctx).ifEmpty { KenhVtvGo.tai(vm.ctx) } }

    /** Sang màn Home sạch (Áp dụng một lần để cập nhật Home sạch cũ); bấm quay lại thì về màn chính. */
    fun moHomeSach() { vm.man = Man.CHINH; vm.moDoiTheme() }

    private fun coO(k: KenhNhanh.Kenh, truO: Int? = null) = ds.firstOrNull { it.so != truO && it.kenh?.trung(k) == true }

    /** Ô soạn chuyển sang kênh hay xem kế tiếp chưa có ô, cho cả TV360, VTVgo lẫn FPT Play. */
    private fun goiYKeTiep() {
        KenhNhanh.GOI_Y.firstOrNull { coO(it) == null }?.let { chon(it) }
        KenhVtvGo.VTV.firstOrNull { (ten, ma, _) -> coO(KenhNhanh.Kenh("", ten, ma)) == null }?.let { (ten, ma, _) -> kenhGo = ten to ma }
        KenhFpt.hayXem(dsFpt).firstOrNull { coO(oFpt(it.tenO, it.ma)) == null }?.let { kenhFpt = it.tenO to it.ma }
    }

    private fun oFpt(tenK: String, ma: String) = KenhNhanh.Kenh("", tenK, ma, KenhNhanh.LOAI_FPT)

    fun chon(k: KenhNhanh.Kenh) { so = k.so.trimStart('0'); ten = k.ten }

    fun chonGo(tenK: String, ma: String) { kenhGo = tenK to ma }

    fun chonFpt(tenK: String, ma: String) { kenhFpt = tenK to ma }

    /** Kênh [ma] trên VTVgo đã có ô (trừ ô đang sửa): để đánh dấu ✓ ở chỗ chọn kênh. */
    fun daCoGo(ma: String): Boolean = coO(KenhNhanh.Kenh("", "", ma), dangSua) != null
    fun daCoFpt(ma: String): Boolean = coO(oFpt("", ma), dangSua) != null
    fun daCoTv360(g: KenhNhanh.Kenh): Boolean = coO(g, dangSua) != null

    /** Đưa ô [o] lên ô soạn để sửa kênh / dock. */
    fun sua(o: O) {
        dangSua = o.so
        o.kenh?.let { k ->
            when {
                k.laFpt -> { app = 2; kenhFpt = k.ten to k.ma }
                k.laVtvGo -> { app = 1; kenhGo = k.ten to k.ma }
                else -> { app = 0; chon(k) }
            }
        }
        trongDock = o.trongDock; bao = ""
    }

    fun thoiSua() { dangSua = null; bao = "" }

    /** Kênh theo ô soạn; lỗi nếu số kênh sai / chưa chọn kênh VTVgo, FPT Play. */
    private fun kenhNhap(): KenhNhanh.Kenh {
        if (app == 2) {
            val (tenK, ma) = kenhFpt ?: error(t("Chọn một kênh FPT Play.", "Pick an FPT Play channel."))
            return oFpt(KenhNhanh.tenSach(tenK).ifBlank { ma }, ma)
        }
        if (app == 1) {
            val (tenK, ma) = kenhGo ?: error(t("Chọn một kênh VTVgo.", "Pick a VTVgo channel."))
            return KenhNhanh.Kenh("", KenhNhanh.tenSach(tenK).ifBlank { ma }, ma)
        }
        val s = KenhNhanh.chuanSo(so) ?: error(t("Số kênh từ 1 đến 999, như số TV360 hiện trong danh sách kênh (VTV1 là 1).",
            "The channel number is 1 to 999, as TV360 shows it in its channel list (VTV1 is 1)."))
        return KenhNhanh.Kenh(s, KenhNhanh.tenSach(ten).ifBlank { t("Kênh ${s.trimStart('0')}", "Channel ${s.trimStart('0')}") })
    }

    /** Thêm ô mới (ô trống đầu tiên) hoặc lưu ô đang sửa: cài / cập nhật app của ô, đặt kênh, tên ô và dock trên Home sạch. */
    fun luu() = vm.chay(t("Đang lưu ô kênh…", "Saving the channel tile…")) {
        val k = kenhNhap()
        val o = dangSua ?: (1..KenhNhanh.SO_O).firstOrNull { s -> ds.none { it.so == s } }
            ?: error(t("Đã đủ ${KenhNhanh.SO_O} ô kênh: bỏ bớt một ô hoặc sửa một ô có sẵn.", "All ${KenhNhanh.SO_O} channel tiles are used: remove one or edit an existing one."))
        coO(k, o)?.let { error(t("${k.ten} đã có ở ô ${it.so}.", "${k.ten} is already on tile ${it.so}.")) }
        vm.damBaoKetNoi()
        bao = ""; baoLech = false
        val them = themO(o, k, trongDock)
        dangSua = null
        docLai()
        bao = (if (!k.laTv360) t("Đã lưu ô $o: ${k.ten} (mở trên ${k.tenApp}).", "Saved tile $o: ${k.ten} (opens in ${k.tenApp}).")
            else t("Đã lưu ô $o: ${k.ten} (số ${k.so.trimStart('0')} trên TV360).", "Saved tile $o: ${k.ten} (number ${k.so.trimStart('0')} in TV360).")) + them
        goiYKeTiep()
    }

    /**
     * Cài / cập nhật app của ô [o] (app phụ cũ chưa biết VTVgo thì bản đi kèm thay vào), đặt kênh [k], tên ô, logo kênh
     * và dock trên Home sạch, tên ô và logo trên TiviKit Launcher. Trả câu nói thêm ô nằm ở đâu trên Home sạch.
     */
    private suspend fun themO(o: Int, k: KenhNhanh.Kenh, trongDock: Boolean): String {
        val goi = KenhNhanh.goiCua(o)
        val apk = vm.io { vn.tivikit.CapNhat.moAsset(vm.ctx, KenhNhanh.apkCua(o)).use { it.readBytes() } }
        vm.io { vm.tv.caiKenh(o, apk, KenhNhanh.json(k)) { s -> vm.thongBao = s } }
        val coTkl = TiviKitLauncher.GOI in vm.trangThai?.caiDat.orEmpty()
        if (!vm.daCoHomeSach && !coTkl) return ""
        val logo = logo(k)
        val tkl = if (!coTkl) "" else {
            vm.thongBao = t("Đặt ô ${k.ten} trên TiviKit Launcher…", "Setting the ${k.ten} tile on TiviKit Launcher…")
            tklCu(runCatching { vm.io { vm.tv.datOKenhTkl(mapOf(goi to (k.ten to logo))) } }.getOrNull())
        }
        if (!vm.daCoHomeSach) return tkl
        vm.thongBao = t("Đặt ô ${k.ten} trên Home sạch…", "Setting the ${k.ten} tile on the clean Home…")
        // Gửi logo trước rồi mới trỏ app_banners tới, để Home sạch khởi động lại là thấy ngay; gửi không được thì ô không logo.
        val anh = logo?.takeIf { b -> runCatching { vm.io { vm.tv.ghiAnhOKenh(o, b) } }.isSuccess }?.let { KenhNhanh.tenAnh(o) }
        val dock = vm.io { vm.tv.ghiOKenh(goi, k.ten, anh, trongDock, DOCK_TOI_DA) }
        vm.tenKenh[goi] = k.ten
        if (anh != null) vm.anhOKenh[goi] = anh else vm.anhOKenh.remove(goi)
        if (dock == true) vm.dongBoDock(goi, trongDock)
        vm.veApp()
        val logoChu = when {
            anh == null -> ""
            homeCu -> t(" Logo kênh hiện khi Home sạch được cập nhật: mở Home sạch, bấm Áp dụng lên tivi một lần.",
                " The channel logo shows once the clean Home is updated: open Clean Home and press Apply to the TV once.")
            else -> t(" Ô có logo kênh.", " The tile has the channel logo.")
        }
        return logoChu + when {
            dock == false -> t(" Dock đã đủ $DOCK_TOI_DA ô nên ô ${k.ten} nằm trong danh sách app; bỏ bớt một app khỏi dock ở mục Home sạch rồi lưu lại.",
                " The dock already has $DOCK_TOI_DA tiles, so the ${k.ten} tile is in the app list; remove an app from the dock under Clean Home and save again.")
            dock == null && trongDock -> t(" Home sạch đang dùng dock mặc định: thêm ô ${k.ten} vào dock ở mục Home sạch.",
                " The clean Home uses its default dock: add the ${k.ten} tile to the dock under Clean Home.")
            trongDock -> t(" Ô ${k.ten} nằm trong dock Home sạch.", " The ${k.ten} tile is in the clean Home's dock.")
            else -> t(" Ô ${k.ten} nằm trong danh sách app của Home sạch.", " The ${k.ten} tile is in the clean Home's app list.")
        } + tkl
    }

    /** Lời nhắc khi TiviKit Launcher trên tivi còn bản cũ, chưa hiện tên kênh và logo của ô ([kq] của datOKenhTkl). */
    private fun tklCu(kq: Boolean?) = if (kq != false) "" else t(
        " TiviKit Launcher hiện tên kênh và logo của ô sau khi cập nhật nó: mở Quản lý launcher, bấm Cập nhật.",
        " TiviKit Launcher shows the tile's channel name and logo once it's updated: open Manage launchers and tap Update.")

    /**
     * Gửi tên kênh và logo của mọi ô đang có lên TiviKit Launcher (vừa cài / cập nhật launcher: nó chưa biết các ô có sẵn).
     * Kết quả như datOKenhTkl; null cả khi tivi không có ô nào.
     */
    suspend fun guiTkl(): Boolean? {
        val ds = vm.io { vm.tv.oKenhDaCai() }.mapNotNull { o -> KenhNhanh.tuJson(vm.io { vm.tv.docKenh(o) })?.let { o to it } }
        if (ds.isEmpty()) return null
        vm.thongBao = t("Gửi tên và logo các ô Kênh nhanh…", "Sending the Quick channel names and logos…")
        // Lấy danh sách kênh VTVgo một lần cho mọi ô (mạng lỗi thì không chờ hết hạn từng ô).
        val dsGo = vm.io { layDsGo() }
        val gui = ds.associate { (o, k) -> KenhNhanh.goiCua(o) to (k.ten to logo(k, dsGo)) }
        return vm.io { vm.tv.datOKenhTkl(gui) }
    }

    /**
     * Chuyển ô kênh VTVgo kiểu cũ thành ô Kênh nhanh: mỗi kênh chưa có ô vào một ô trống, rồi bỏ các ô link đó khỏi Home
     * sạch (kênh đã có ô thì chỉ bỏ ô link). Hết ô trống thì phần còn lại giữ nguyên kiểu cũ.
     */
    fun chuyenKenhCu() = vm.chay(t("Đang chuyển ô kênh VTVgo…", "Moving the VTVgo tiles…")) {
        vm.damBaoKetNoi()
        bao = ""; baoLech = false
        val moi = kenhGoCu.filter { (tenK, ma) -> coO(KenhNhanh.Kenh("", tenK, ma)) == null }
        val trong = (1..KenhNhanh.SO_O).filter { s -> ds.none { it.so == s } }
        val vao = moi.take(trong.size)
        vao.zip(trong).forEachIndexed { i, (kg, o) ->
            vm.thongBao = t("Chuyển ô ${kg.first} (${i + 1}/${vao.size})…", "Moving ${kg.first} (${i + 1}/${vao.size})…")
            themO(o, KenhNhanh.Kenh("", KenhNhanh.tenSach(kg.first).ifBlank { kg.second }, kg.second), trongDock = true)
        }
        val con = moi.drop(vao.size)
        vm.io { vm.tv.ghiKenhVtvGo(con, emptyList()) }
        docLai()
        baoLech = con.isNotEmpty()
        bao = t("Đã chuyển ${vao.size} ô kênh VTVgo sang Kênh nhanh.", "Moved ${vao.size} VTVgo tiles to Quick channel.") +
            if (con.isEmpty()) "" else t(" Còn ${con.size} ô kiểu cũ vì hết ô trống: bỏ bớt một ô rồi chuyển tiếp.",
                " ${con.size} old tiles are left because no tile is free: remove one and move again.")
    }

    /** Mở ô [o] trên tivi như bấm ô, xem app của ô (TV360 / VTVgo / FPT Play) có lên không. */
    fun thu(o: O) = vm.chay(t("Đang mở ô ${o.kenh?.ten ?: o.so} trên tivi…", "Opening tile ${o.kenh?.ten ?: o.so} on the TV…")) {
        vm.damBaoKetNoi()
        bao = ""; baoLech = false
        val out = vm.io { vm.tv.moKenhNhanh(o.so) }
        if (out.contains("Error")) error(t("Tivi không mở được ô này: ${out.trim().lines().lastOrNull()}. Bấm Sửa rồi lưu lại ô.",
            "The TV couldn't open this tile: ${out.trim().lines().lastOrNull()}. Tap Edit and save the tile again."))
        val goiApp = o.kenh?.app ?: KenhNhanh.TV360
        val tenApp = KenhNhanh.tenAppCua(goiApp)
        // App mở nguội mất vài giây (màn chào, tải danh sách kênh).
        var g = ""
        for (i in 1..10) {
            kotlinx.coroutines.delay(1000)
            g = vm.io { vm.tv.goiDangHien() }
            if (g == goiApp) break
        }
        val tenK = o.kenh?.ten ?: t("kênh của ô ${o.so}", "tile ${o.so}'s channel")
        baoLech = g != goiApp
        bao = if (!baoLech) t("$tenApp đã mở. Nhìn tivi: đang phát $tenK là được. $tenApp chưa đăng nhập hoặc hiện quảng cáo thì bấm qua rồi thử lại.",
                "$tenApp is open. Look at the TV: if $tenK is playing, it works. If $tenApp asks you to sign in or shows an ad, get past it and test again.") +
                (if (goiApp == KenhNhanh.FPTPLAY) t(" FPT Play hỏi mua gói thì kênh này cần gói của FPT Play.", " If FPT Play offers a package, this channel needs one.") else "")
            else if (!coApp(goiApp)) t("Tivi chưa có $tenApp (hoặc đang bị tắt): cài $tenApp từ CH Play, hoặc bật lại ở Quản lý app.",
                "$tenApp isn't on the TV (or is disabled): install it from the Play Store, or re-enable it in Manage apps.")
            else t("$tenApp chưa lên trước mặt (đang thấy ${g.ifBlank { "không rõ" }}). Thử lại; vẫn vậy thì mở $tenApp một lần bằng tay rồi thử.",
                "$tenApp didn't come to the front (seeing ${g.ifBlank { "unknown" }}). Try again; if it persists, open $tenApp by hand once and test.")
    }

    private fun coApp(goi: String) = when (goi) {
        KenhNhanh.VTVGO -> vm.coVtvGo
        KenhNhanh.FPTPLAY -> coFpt
        else -> coTv360
    }

    /** Sang màn Nút remote với ô [o] chọn sẵn. */
    fun ganNut(o: O) { vm.ganPhim.mo(goiSan = o.goi) }

    /** Mở trang của TV360 / VTVgo / FPT Play ([goi]) trên CH Play của tivi. */
    fun moCHPlay(goi: String) = vm.chay(t("Đang mở CH Play trên tivi…", "Opening the Play Store on the TV…")) {
        vm.damBaoKetNoi()
        val out = vm.io { vm.tv.moCHPlay(goi) }
        if (out.contains("Error")) error(t("Tivi không mở được CH Play: ${out.trim().lines().lastOrNull()}", "The TV couldn't open the Play Store: ${out.trim().lines().lastOrNull()}"))
        val tenApp = KenhNhanh.tenAppCua(goi)
        bao = t("Đã mở trang $tenApp trên CH Play của tivi: cài xong, mở $tenApp một lần, rồi mở lại màn này.",
            "$tenApp's Play Store page is open on the TV: install it, open it once, then reopen this screen.")
        baoLech = false
    }

    /** Bỏ ô [o]: gỡ app của ô, bỏ tên ô và logo (Home sạch, TiviKit Launcher), ô trong dock và nút remote đang gán cho nó. */
    fun bo(o: O) = vm.chay(t("Đang bỏ ô ${o.kenh?.ten ?: o.so}…", "Removing tile ${o.kenh?.ten ?: o.so}…")) {
        vm.damBaoKetNoi()
        bao = ""; baoLech = false
        vm.io { vm.tv.goKenh(o.so) }
        runCatching { vm.io { vm.tv.datOKenhTkl(mapOf(o.goi to (null to null))) } }
        if (vm.daCoHomeSach) {
            val j = vm.io { vm.tv.docCauHinhTv() }
            val phim = j?.let { PhimRieng.docTu(it) }.orEmpty()
            val con = phim.filter { PhimRieng.dichCua(it) != o.goi }
            if (con.size != phim.size) { vm.io { vm.tv.ghiPhimRieng(con) }; vm.phimRieng.clear(); vm.phimRieng.addAll(con) }
            vm.io { vm.tv.ghiOKenh(o.goi, null, null, false, DOCK_TOI_DA) }
            vm.tenKenh.remove(o.goi); vm.anhOKenh.remove(o.goi)
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
        ds = emptyList(); dangSua = null; bao = ""; baoLech = false; coTv360 = true; coFpt = false
        app = 0; kenhGo = null; kenhFpt = null; moChonFpt = false; kenhGoCu = emptyList(); homeCu = false
    }
}
