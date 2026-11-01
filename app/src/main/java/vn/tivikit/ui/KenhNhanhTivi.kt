package vn.tivikit.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import vn.tivikit.dulieu.AnhOKenh
import vn.tivikit.dulieu.KenhFpt
import vn.tivikit.dulieu.KenhNhanh
import vn.tivikit.dulieu.KenhVtvGo
import vn.tivikit.dulieu.MapVoice
import vn.tivikit.dulieu.PhimRieng
import vn.tivikit.dulieu.TiviKitLauncher
import vn.tivikit.t

/**
 * Kênh nhanh (25/10/2026, muốn một nút mở thẳng VTV1; cùng ngày thành nhiều ô; 26/10/2026 thêm ô VTVgo; 31/10/2026 ô
 * FPT Play; 1/11/2026 ô M3U, ô Auto): tối đa KenhNhanh.SO_O ô kênh, mỗi ô một app phụ trên tivi (module kenh/) mở thẳng một
 * kênh trên TV360, VTVgo, FPT Play hoặc M3U (kênh có phím tắt trong M3UAndroid); ô Auto để Map Voice chọn nguồn nào trong
 * số đó đang phát được (TuChon của Map Voice). Thêm / sửa
 * ô (cài app của ô, đặt kênh, đặt tên ô theo tên kênh và logo kênh trên Home sạch và TiviKit Launcher, thêm vào dock Home
 * sạch nếu muốn), bấm thử, gán nút remote (màn Nút remote chọn sẵn ô đó), bỏ ô. Ô kênh VTVgo kiểu cũ (ô link của Home
 * sạch, KenhVtvGo; không gán được nút remote) chuyển được thành ô ở đây. Ảnh ô mang huy hiệu app mở kênh ở góc phải dưới
 * (1/11/2026, AnhOKenh); ô làm trước đó vẽ lại một lượt (veLaiAnh).
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
    // 2 = FPT Play ([kenhFpt]), 3 = M3U ([kenhM3u]), 4 = Auto ([tenAuto], nguồn tìm theo tên: nguonAuto).
    var app by mutableStateOf(0)
    /** Tên kênh của ô soạn Auto. */
    var tenAuto by mutableStateOf("VTV1")
    /** Danh sách kênh VTVgo, để ô Auto tìm nguồn VTVgo theo tên. */
    var dsGo by mutableStateOf<List<KenhVtvGo.Kenh>>(emptyList())
        private set
    /** Map Voice đang cài và bật (ô Auto cần nó để tự chọn, đổi nguồn); [mapVoiceCu] = bản trước 1.2, chưa biết tự chọn. */
    var coMapVoice by mutableStateOf(false)
        private set
    var mapVoiceCu by mutableStateOf(false)
        private set
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
    /** Kênh M3U của ô soạn: (tên, mã kênh). */
    var kenhM3u by mutableStateOf<Pair<String, String>?>(null)
        private set
    /** Kênh M3U có phím tắt trên tivi (ThaoTacTv.docKenhM3u): (tên, mã). */
    var dsM3u by mutableStateOf<List<Pair<String, String>>>(emptyList())
        private set
    var trongDock by mutableStateOf(true)
    var dangSua by mutableStateOf<Int?>(null)
        private set

    /** TV360 bản tivi đang cài và bật. */
    var coTv360 by mutableStateOf(true)
        private set
    /** FPT Play bản tivi đang cài và bật. */
    var coFpt by mutableStateOf(false)
        private set
    /** M3U (M3UAndroid) đang cài và bật. */
    var coM3u by mutableStateOf(false)
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
    /** Tivi có ô làm trước khi ảnh ô có huy hiệu app nguồn (1/11/2026), chưa vẽ lại: màn Kênh nhanh mời vẽ lại (veLaiAnh). */
    var canVeLai by mutableStateOf(false)
        private set

    /** Biểu tượng các app xem kênh trên tivi (gói → PNG) cho huy hiệu ở góc ô; hỏi tivi một lần mỗi lần mở màn. */
    private val bieuTuong = mutableMapOf<String, ByteArray>()
    private var daHoiBieuTuong = false
    private val prefs by lazy { vm.ctx.getSharedPreferences("tvsach", Context.MODE_PRIVATE) }

    fun mo() = vm.chay(t("Đang đọc Kênh nhanh trên tivi…", "Reading Quick channel on the TV…")) {
        vm.damBaoKetNoi()
        bao = ""; baoLech = false; dangSua = null
        bieuTuong.clear(); daHoiBieuTuong = false
        docLai()
        // Ô soạn mở sẵn app tivi đang có (không có TV360 thì VTVgo, rồi FPT Play, M3U), gợi ý kênh hay xem đầu tiên chưa có ô.
        app = when {
            coTv360 -> 0
            vm.coVtvGo -> 1
            coFpt -> 2
            coM3u -> 3
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
        coM3u = KenhNhanh.M3U in tt.caiDat && KenhNhanh.M3U !in tt.daTat
        dsM3u = if (coM3u) vm.io { vm.tv.docKenhM3u() } else emptyList()
        if (dsFpt.isEmpty()) dsFpt = vm.io { KenhFpt.ds(vm.ctx) }
        if (dsGo.isEmpty()) dsGo = vm.io { layDsGo() }
        coMapVoice = MapVoice.GOI in tt.caiDat && MapVoice.GOI !in tt.daTat
        mapVoiceCu = coMapVoice && (vm.io { vm.tv.banTrenTv(MapVoice.GOI) }?.let { Regex("\\((\\d+)\\)").find(it)?.groupValues?.get(1)?.toIntOrNull() } ?: 0) < MapVoice.BAN_TU_CHON
        val j = if (vm.daCoHomeSach) vm.io { vm.tv.docCauHinhTv() } else null
        val dock = j?.let { KenhNhanh.trongDock(it) }.orEmpty()
        j?.let { vm.tenKenh.clear(); vm.tenKenh.putAll(KenhNhanh.tenTrong(it)); vm.anhOKenh.clear(); vm.anhOKenh.putAll(KenhNhanh.anhTrong(it)) }
        ds = (1..KenhNhanh.SO_O).filter { KenhNhanh.goiCua(it) in tt.caiDat }.map { o ->
            O(o, KenhNhanh.tuJson(vm.io { vm.tv.docKenh(o) }), KenhNhanh.goiCua(o) in dock)
        }
        kenhGoCu = j?.let { KenhVtvGo.docTu(it) }.orEmpty()
        vm.kenhChon.clear(); vm.kenhChon.addAll(kenhGoCu)
        homeCu = vm.daCoHomeSach && !vm.io { vm.tv.homeLaBanDiKem(vn.tivikit.CapNhat.moAsset(vm.ctx, vm.tv.apkHome).use { it.readBytes() }) }
        // Lúc chưa có ô nào thì mọi ô làm sau đều có huy hiệu: khỏi mời vẽ lại.
        val khoa = vm.thongTin?.khoaLuu
        if (khoa != null && ds.isEmpty()) ghiDaVeLai(khoa)
        val da = prefs.getStringSet(DA_VE_LAI, null).orEmpty()
        canVeLai = khoa != null && khoa !in da && ds.any { "$khoa#${it.so}" !in da } && (vm.daCoHomeSach || TiviKitLauncher.GOI in tt.caiDat)
    }

    /**
     * Ghi nhớ đã có ảnh kèm huy hiệu: cả tivi [khoa] (khoaLuu; vẽ lại mọi ô, hay chưa có ô nào), hay từng ô vừa lưu
     * ("khoaLuu#số ô"): lưu lại hết các ô cũ thì khỏi mời vẽ lại.
     */
    private fun ghiDaVeLai(muc: String) {
        val da = prefs.getStringSet(DA_VE_LAI, null).orEmpty()
        if (muc !in da) prefs.edit().putStringSet(DA_VE_LAI, da + muc).apply()
    }

    /**
     * Ảnh ô của kênh [k]: logo kênh (logoKenh) kèm huy hiệu app mở kênh ở góc phải dưới; kênh không có logo thì ô chữ tên
     * kênh kèm huy hiệu (AnhOKenh). null nếu không có logo lẫn biểu tượng app: ô hiện biểu tượng và tên như trước.
     */
    private suspend fun logo(k: KenhNhanh.Kenh, dsGo: List<KenhVtvGo.Kenh>? = null): ByteArray? {
        if (k.laAuto) return vm.io { AnhOKenh.veAuto(logoKenh(k, dsGo), k.ten) }
        val bt = bieuTuongCua(k.app)
        return vm.io { AnhOKenh.ve(logoKenh(k, dsGo), k.ten, bt) }
    }

    /**
     * Logo kênh [k] cho ô: ảnh kênh của VTVgo (ô VTVgo theo mã kênh, ô TV360 / M3U theo tên trùng một kênh VTVgo: VTV1–9,
     * HTV9, Hà Nội 1, ANTV, QPVN…), ô FPT Play lấy ảnh kênh của FPT Play; ô Auto lấy của nguồn VTVgo, không có thì nguồn FPT
     * Play, rồi theo tên; tải một lần rồi cất (KenhVtvGo.anhO). null nếu không có / không tải được. App không đóng sẵn logo
     * của đài. [dsGo] = danh sách kênh VTVgo đã lấy (nhiều ô một lượt). Gọi trên luồng IO.
     */
    private fun logoKenh(k: KenhNhanh.Kenh, dsGo: List<KenhVtvGo.Kenh>?): ByteArray? {
        if (k.laAuto) return k.nguon.filter { it.laVtvGo || it.laFpt }.firstNotNullOfOrNull { logoKenh(it, dsGo) }
            ?: logoKenh(KenhNhanh.Kenh("", k.ten), dsGo)
        if (k.laFpt) return KenhVtvGo.anhO(vm.ctx, k.ma, KenhFpt.ds(vm.ctx).firstOrNull { it.ma == k.ma }?.anh, KenhVtvGo.THU_MUC_FPT)
        val ds = dsGo ?: layDsGo()
        return if (k.laVtvGo) KenhVtvGo.anhO(vm.ctx, k.ma, ds.firstOrNull { it.ma == k.ma }?.anh)
        else KenhVtvGo.timTheoTen(ds, k.ten)?.let { KenhVtvGo.anhO(vm.ctx, it.ma, it.anh) }
    }

    /** Biểu tượng (PNG) của app xem kênh [goi] trên tivi: lần đầu hỏi tivi một lượt cho mọi app xem kênh (TrenTv.bieuTuong). */
    private suspend fun bieuTuongCua(goi: String): ByteArray? {
        if (!daHoiBieuTuong) {
            daHoiBieuTuong = true
            bieuTuong.putAll(runCatching {
                vm.io { vn.tivikit.adb.TrenTv(vm.adb).bieuTuong({ vn.tivikit.CapNhat.moAsset(vm.ctx, vn.tivikit.adb.TrenTv.ASSET).use { it.readBytes() } }, KenhNhanh.APP_XEM) }
            }.getOrDefault(emptyMap()))
        }
        return bieuTuong[goi]
    }

    /** Danh sách kênh VTVgo: đang có, đã lưu, không thì tải (gọi trên luồng IO). */
    private fun layDsGo() = vm.dsKenhGo.ifEmpty { KenhVtvGo.docNho(vm.ctx).ifEmpty { KenhVtvGo.tai(vm.ctx) } }

    /** Sang màn Home sạch (Áp dụng một lần để cập nhật Home sạch cũ); bấm quay lại thì về màn chính. */
    fun moHomeSach() { vm.man = Man.CHINH; vm.moDoiTheme() }

    private fun coO(k: KenhNhanh.Kenh, truO: Int? = null) = ds.firstOrNull { it.so != truO && it.kenh?.trung(k) == true }

    /** Ô soạn chuyển sang kênh hay xem kế tiếp chưa có ô, cho cả TV360, VTVgo, FPT Play, M3U lẫn Auto. */
    private fun goiYKeTiep() {
        KenhNhanh.GOI_Y.firstOrNull { coO(it) == null }?.let { chon(it) }
        KenhVtvGo.VTV.firstOrNull { (ten, ma, _) -> coO(KenhNhanh.Kenh("", ten, ma)) == null }?.let { (ten, ma, _) -> kenhGo = ten to ma }
        KenhFpt.hayXem(dsFpt).firstOrNull { coO(oFpt(it.tenO, it.ma)) == null }?.let { kenhFpt = it.tenO to it.ma }
        kenhM3u = dsM3u.firstOrNull { (tenK, ma) -> coO(oM3u(tenK, ma)) == null }
        KenhNhanh.GOI_Y.firstOrNull { coO(oAuto(it.ten)) == null }?.let { tenAuto = it.ten }
    }

    private fun oFpt(tenK: String, ma: String) = KenhNhanh.Kenh("", tenK, ma, KenhNhanh.LOAI_FPT)
    private fun oM3u(tenK: String, ma: String) = KenhNhanh.Kenh("", tenK, ma, KenhNhanh.LOAI_M3U)
    private fun oAuto(tenK: String) = KenhNhanh.Kenh("", tenK, "", KenhNhanh.LOAI_AUTO)

    /**
     * Nguồn của ô Auto kênh [tenK] (KenhNhanh.nguonAuto): kênh cùng tên trên VTVgo, TV360 (số kênh đã biết: gợi ý và ô TV360
     * đang có), FPT Play và phím tắt M3U.
     */
    fun nguonAuto(tenK: String = tenAuto): List<KenhNhanh.Kenh> =
        KenhNhanh.nguonAuto(tenK, dsGo, dsFpt, dsM3u, KenhNhanh.GOI_Y + ds.mapNotNull { it.kenh })

    /** App của nguồn [loai] có trên tivi (đang bật). */
    fun coNguon(loai: String): Boolean = when (loai) {
        KenhNhanh.LOAI_VTVGO -> vm.coVtvGo
        KenhNhanh.LOAI_FPT -> coFpt
        KenhNhanh.LOAI_M3U -> coM3u
        else -> coTv360
    }

    /** Đọc lại kênh M3U có phím tắt (người dùng vừa tạo thêm trong M3U trên tivi). */
    fun taiLaiM3u() = vm.chay(t("Đang đọc kênh M3U trên tivi…", "Reading the M3U channels on the TV…")) {
        vm.damBaoKetNoi()
        dsM3u = vm.io { vm.tv.docKenhM3u() }
        if (kenhM3u?.let { k -> dsM3u.none { it.second == k.second } } != false) kenhM3u = dsM3u.firstOrNull { (tenK, ma) -> coO(oM3u(tenK, ma)) == null }
        baoLech = dsM3u.isEmpty()
        bao = if (dsM3u.isEmpty()) t("Chưa thấy phím tắt kênh nào của M3U: tạo trong M3U (giữ OK trên kênh → Create Shortcut) rồi tải lại.",
            "No M3U channel shortcuts yet: create them in M3U (hold OK on a channel → Create Shortcut), then reload.")
            else t("Có ${dsM3u.size} kênh M3U.", "${dsM3u.size} M3U channels.")
    }

    fun chon(k: KenhNhanh.Kenh) { so = k.so.trimStart('0'); ten = k.ten }

    fun chonGo(tenK: String, ma: String) { kenhGo = tenK to ma }

    fun chonFpt(tenK: String, ma: String) { kenhFpt = tenK to ma }

    fun chonM3u(tenK: String, ma: String) { kenhM3u = tenK to ma }

    /** Kênh [ma] trên VTVgo đã có ô (trừ ô đang sửa): để đánh dấu ✓ ở chỗ chọn kênh. */
    fun daCoGo(ma: String): Boolean = coO(KenhNhanh.Kenh("", "", ma), dangSua) != null
    fun daCoFpt(ma: String): Boolean = coO(oFpt("", ma), dangSua) != null
    fun daCoM3u(ma: String): Boolean = coO(oM3u("", ma), dangSua) != null
    fun daCoTv360(g: KenhNhanh.Kenh): Boolean = coO(g, dangSua) != null
    fun daCoAuto(tenK: String): Boolean = coO(oAuto(tenK), dangSua) != null

    /** Đưa ô [o] lên ô soạn để sửa kênh / dock. */
    fun sua(o: O) {
        dangSua = o.so
        o.kenh?.let { k ->
            when {
                k.laAuto -> { app = 4; tenAuto = k.ten }
                k.laM3u -> { app = 3; kenhM3u = k.ten to k.ma }
                k.laFpt -> { app = 2; kenhFpt = k.ten to k.ma }
                k.laVtvGo -> { app = 1; kenhGo = k.ten to k.ma }
                else -> { app = 0; chon(k) }
            }
        }
        trongDock = o.trongDock; bao = ""
    }

    fun thoiSua() { dangSua = null; bao = "" }

    /** Kênh theo ô soạn; lỗi nếu số kênh sai / chưa chọn kênh VTVgo, FPT Play, M3U / kênh Auto không có nguồn nào. */
    private fun kenhNhap(): KenhNhanh.Kenh {
        if (app == 4) {
            val tenK = KenhNhanh.tenSach(tenAuto)
            if (tenK.isEmpty()) error(t("Chọn hay gõ tên kênh cho ô Auto.", "Pick or type a channel name for the Auto tile."))
            val nguon = nguonAuto(tenK)
            if (nguon.isEmpty()) error(t("Chưa thấy $tenK trên VTVgo, TV360, FPT Play hay phím tắt M3U: thử tên khác (VD VTV3, HTV7, Hà Nội 1).",
                "$tenK isn't on VTVgo, TV360, FPT Play or an M3U shortcut: try another name (e.g. VTV3, HTV7, Hà Nội 1)."))
            return KenhNhanh.Kenh("", tenK, "", KenhNhanh.LOAI_AUTO, nguon)
        }
        if (app == 3) {
            val (tenK, ma) = kenhM3u ?: error(t("Chọn một kênh M3U (kênh đã tạo phím tắt trong M3U).", "Pick an M3U channel (one with a shortcut in M3U)."))
            return oM3u(KenhNhanh.tenSach(tenK).ifBlank { ma }, ma)
        }
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
        bao = when {
            k.laAuto -> t("Đã lưu ô $o: ${k.ten}, tự chọn nguồn (${tenNguon(k)}).", "Saved tile $o: ${k.ten}, auto source (${tenNguon(k)}).") + when {
                !coMapVoice -> t(" Tivi chưa có Map Voice nên ô chỉ mở ${KenhNhanh.tenAppCua(k.nguon.first().app)}: bật Map Voice ở mục Map Voice để ô tự chọn và đổi nguồn.",
                    " The TV has no Map Voice, so the tile only opens ${KenhNhanh.tenAppCua(k.nguon.first().app)}: turn Map Voice on under Map Voice for automatic sources.")
                mapVoiceCu -> t(" Map Voice trên tivi là bản cũ, chưa tự chọn nguồn: mở mục Map Voice, bấm Cài lại / cập nhật.",
                    " Map Voice on the TV is an old version that can't pick sources yet: open Map Voice and tap Reinstall / update.")
                else -> ""
            }
            !k.laTv360 -> t("Đã lưu ô $o: ${k.ten} (mở trên ${k.tenApp}).", "Saved tile $o: ${k.ten} (opens in ${k.tenApp}).")
            else -> t("Đã lưu ô $o: ${k.ten} (số ${k.so.trimStart('0')} trên TV360).", "Saved tile $o: ${k.ten} (number ${k.so.trimStart('0')} in TV360).")
        } + them
        goiYKeTiep()
    }

    /** "VTVgo → TV360 → FPT Play": nguồn của ô Auto [k] theo thứ tự thử mặc định. */
    fun tenNguon(k: KenhNhanh.Kenh): String = k.nguon.map { KenhNhanh.tenAppCua(it.app) }.distinct().joinToString(" → ")

    /**
     * Cài / cập nhật app của ô [o] (app phụ cũ chưa biết VTVgo thì bản đi kèm thay vào), đặt kênh [k], tên ô, logo kênh
     * (kèm huy hiệu app) và dock trên Home sạch, tên ô và logo trên TiviKit Launcher. Trả câu nói thêm ô nằm ở đâu trên Home sạch.
     */
    private suspend fun themO(o: Int, k: KenhNhanh.Kenh, trongDock: Boolean): String {
        val goi = KenhNhanh.goiCua(o)
        val apk = vm.io { vn.tivikit.CapNhat.moAsset(vm.ctx, KenhNhanh.apkCua(o)).use { it.readBytes() } }
        vm.io { vm.tv.caiKenh(o, apk, KenhNhanh.json(k)) { s -> vm.thongBao = s } }
        val coTkl = TiviKitLauncher.GOI in vm.trangThai?.caiDat.orEmpty()
        if (!vm.daCoHomeSach && !coTkl) return ""
        val logo = logo(k)
        vm.thongTin?.khoaLuu?.let { ghiDaVeLai("$it#$o") }
        val tkl = if (!coTkl) "" else {
            vm.thongBao = t("Đặt ô ${k.ten} trên TiviKit Launcher…", "Setting the ${k.ten} tile on TiviKit Launcher…")
            tklCu(runCatching { vm.io { vm.tv.datOKenhTkl(mapOf(goi to (k.ten to logo))) } }.getOrNull())
        }
        if (!vm.daCoHomeSach) return tkl
        vm.thongBao = t("Đặt ô ${k.ten} trên Home sạch…", "Setting the ${k.ten} tile on the clean Home…")
        // Gửi logo trước rồi mới trỏ app_banners tới, để Home sạch khởi động lại là thấy ngay; gửi không được thì ô không logo.
        val anh = logo?.takeIf { b -> runCatching { vm.io { vm.tv.ghiAnhOKenh(o, b) } }.isSuccess }?.let { KenhNhanh.tenAnh(o) }
        val dock = vm.io { vm.tv.ghiOKenh(goi, k.ten, anh, trongDock, DOCK_TOI_DA, anhMoi = anh != null) }
        vm.tenKenh[goi] = k.ten
        if (anh != null) vm.anhOKenh[goi] = anh else vm.anhOKenh.remove(goi)
        if (dock == true) vm.dongBoDock(goi, trongDock)
        vm.veApp()
        val logoChu = when {
            anh == null -> ""
            homeCu -> t(" Logo kênh hiện khi Home sạch được cập nhật: mở Home sạch, bấm Áp dụng lên tivi một lần.",
                " The channel logo shows once the clean Home is updated: open Clean Home and press Apply to the TV once.")
            k.laAuto -> t(" Ô có ảnh kênh, góc phải dưới là huy hiệu Auto.", " The tile has the channel's picture with the Auto badge in the bottom-right corner.")
            k.app in bieuTuong -> t(" Ô có ảnh kênh, góc phải dưới là biểu tượng ${k.tenApp}.", " The tile has the channel's picture with the ${k.tenApp} icon in the bottom-right corner.")
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
     * Vẽ lại ảnh mọi ô đang có (logo kênh kèm huy hiệu app mở kênh) trên TiviKit Launcher và Home sạch, cho các ô làm trước
     * khi có huy hiệu (1/11/2026). Home sạch khởi động lại một lần mới thấy ảnh mới. Ô không vẽ được ảnh thì giữ ảnh cũ.
     */
    fun veLaiAnh() = vm.chay(t("Đang vẽ lại ảnh các ô…", "Redrawing the tiles…")) {
        vm.damBaoKetNoi()
        bao = ""; baoLech = false
        val dsGo = vm.io { layDsGo() }
        val anh = ds.mapNotNull { o -> o.kenh?.let { k -> logo(k, dsGo)?.let { Triple(o, k, it) } } }
        if (anh.none { (_, k, _) -> k.laAuto || k.app in bieuTuong }) error(t("Không đọc được biểu tượng app xem kênh trên tivi nên chưa vẽ lại. Thử lại.",
            "Couldn't read the channel apps' icons on the TV, so nothing was redrawn. Try again."))
        val tkl = if (TiviKitLauncher.GOI !in vm.trangThai?.caiDat.orEmpty()) "" else {
            vm.thongBao = t("Gửi ảnh các ô lên TiviKit Launcher…", "Sending the tiles to TiviKit Launcher…")
            tklCu(runCatching { vm.io { vm.tv.datOKenhTkl(anh.associate { (o, k, b) -> o.goi to (k.ten to b) }) } }.getOrNull())
        }
        if (vm.daCoHomeSach) {
            vm.thongBao = t("Gửi ảnh các ô lên Home sạch…", "Sending the tiles to the clean Home…")
            val gui = anh.filter { (o, _, b) -> runCatching { vm.io { vm.tv.ghiAnhOKenh(o.so, b) } }.isSuccess }
                .associate { (o, _, _) -> o.goi to KenhNhanh.tenAnh(o.so) }
            vm.io { vm.tv.datAnhOKenh(gui) }
            vm.anhOKenh.putAll(gui)
            vm.veApp()
        }
        vm.thongTin?.khoaLuu?.let { ghiDaVeLai(it) }
        canVeLai = false
        bao = t("Đã vẽ lại ảnh ${anh.size} ô: góc phải dưới mỗi ô có biểu tượng app mở kênh.",
            "Redrew ${anh.size} tiles: each shows the icon of the app that opens it in the bottom-right corner.") + tkl
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

    /** Mở ô [o] trên tivi như bấm ô, xem app của ô (TV360 / VTVgo / FPT Play / M3U; ô Auto: nguồn nào lên) có lên không. */
    fun thu(o: O) = vm.chay(t("Đang mở ô ${o.kenh?.ten ?: o.so} trên tivi…", "Opening tile ${o.kenh?.ten ?: o.so} on the TV…")) {
        vm.damBaoKetNoi()
        bao = ""; baoLech = false
        val out = vm.io { vm.tv.moKenhNhanh(o.so) }
        if (out.contains("Error")) error(t("Tivi không mở được ô này: ${out.trim().lines().lastOrNull()}. Bấm Sửa rồi lưu lại ô.",
            "The TV couldn't open this tile: ${out.trim().lines().lastOrNull()}. Tap Edit and save the tile again."))
        if (o.kenh?.laAuto == true) { thuAuto(o.kenh); return@chay }
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
        bao = if (!baoLech && goiApp == KenhNhanh.M3U) t("M3U đã mở. Nhìn tivi: đang phát $tenK là được; M3U báo lỗi kênh thì mở danh sách kênh trong M3U xem kênh đó còn xem được không.",
                "M3U is open. Look at the TV: if $tenK is playing, it works; if M3U reports a channel error, check in M3U's channel list that the channel still plays.")
            else if (!baoLech) t("$tenApp đã mở. Nhìn tivi: đang phát $tenK là được. $tenApp chưa đăng nhập hoặc hiện quảng cáo thì bấm qua rồi thử lại.",
                "$tenApp is open. Look at the TV: if $tenK is playing, it works. If $tenApp asks you to sign in or shows an ad, get past it and test again.") +
                (if (goiApp == KenhNhanh.FPTPLAY) t(" FPT Play hỏi mua gói thì kênh này cần gói của FPT Play.", " If FPT Play offers a package, this channel needs one.") else "")
            else if (!coApp(goiApp) && goiApp == KenhNhanh.M3U) t("Tivi chưa có M3U (hoặc đang bị tắt): cài ở Cài app → Gợi ý, hoặc bật lại ở Quản lý app.",
                "M3U isn't on the TV (or is disabled): install it under Install apps → Suggestions, or re-enable it in Manage apps.")
            else if (!coApp(goiApp)) t("Tivi chưa có $tenApp (hoặc đang bị tắt): cài $tenApp từ CH Play, hoặc bật lại ở Quản lý app.",
                "$tenApp isn't on the TV (or is disabled): install it from the Play Store, or re-enable it in Manage apps.")
            else t("$tenApp chưa lên trước mặt (đang thấy ${g.ifBlank { "không rõ" }}). Thử lại; vẫn vậy thì mở $tenApp một lần bằng tay rồi thử.",
                "$tenApp didn't come to the front (seeing ${g.ifBlank { "unknown" }}). Try again; if it persists, open $tenApp by hand once and test.")
    }

    /**
     * Ô Auto vừa bấm: chờ một nguồn của ô lên trước mặt (Map Voice thử lần lượt, mỗi nguồn tới 20–30 giây), báo nguồn nào; nguồn
     * sau cùng mới lên là các nguồn trước không phát được.
     */
    private suspend fun thuAuto(k: KenhNhanh.Kenh) {
        val goi = k.nguon.map { it.app }.distinct()
        var g = ""
        for (i in 1..20) {
            kotlinx.coroutines.delay(1500)
            g = vm.io { vm.tv.goiDangHien() }
            if (g in goi) break
        }
        baoLech = g !in goi
        bao = when {
            !baoLech -> t("Ô Auto đang mở ${k.ten} trên ${KenhNhanh.tenAppCua(g)}. Nhìn tivi: có hình có tiếng là được; nguồn không phát được thì Map Voice báo trên tivi và chuyển sang nguồn kế.",
                "The Auto tile is opening ${k.ten} in ${KenhNhanh.tenAppCua(g)}. Look at the TV: picture and sound means it works; if a source fails, Map Voice says so on the TV and moves to the next.")
            !coMapVoice -> t("Tivi chưa có Map Voice nên ô Auto chỉ mở nguồn đầu (${KenhNhanh.tenAppCua(goi.first())}), mà nguồn đó chưa lên. Bật Map Voice ở mục Map Voice.",
                "The TV has no Map Voice, so the Auto tile only opens its first source (${KenhNhanh.tenAppCua(goi.first())}), which didn't come up. Turn Map Voice on under Map Voice.")
            k.nguon.none { coNguon(it.loai) } -> t("Tivi chưa có app nào trong các nguồn của ô (${tenNguon(k)}).", "The TV has none of the tile's source apps (${tenNguon(k)}).")
            else -> t("Chưa thấy nguồn nào của ô lên trước mặt (đang thấy ${g.ifBlank { "không rõ" }}). Thử lại; Map Voice phải đang bật (mục Map Voice).",
                "No source of the tile came to the front (seeing ${g.ifBlank { "unknown" }}). Try again; Map Voice must be on (Map Voice screen).")
        }
    }

    private fun coApp(goi: String) = when (goi) {
        KenhNhanh.VTVGO -> vm.coVtvGo
        KenhNhanh.FPTPLAY -> coFpt
        KenhNhanh.M3U -> coM3u
        MapVoice.GOI -> coMapVoice
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
        ds = emptyList(); dangSua = null; bao = ""; baoLech = false; coTv360 = true; coFpt = false; coM3u = false
        app = 0; kenhGo = null; kenhFpt = null; kenhM3u = null; dsM3u = emptyList(); moChonFpt = false; kenhGoCu = emptyList(); homeCu = false
        canVeLai = false; bieuTuong.clear(); daHoiBieuTuong = false
        tenAuto = "VTV1"; coMapVoice = false; mapVoiceCu = false
    }

    private companion object {
        /** Tivi (khoaLuu) / ô ("khoaLuu#số ô") đã có ảnh ô kèm huy hiệu (canVeLai). */
        const val DA_VE_LAI = "kenh-huy-hieu"
    }
}
