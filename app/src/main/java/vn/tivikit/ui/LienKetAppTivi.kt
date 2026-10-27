package vn.tivikit.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import vn.tivikit.dulieu.LienKetApp
import vn.tivikit.dulieu.LienKetApp.MoBang
import vn.tivikit.dulieu.tenDep
import vn.tivikit.t

/**
 * Set App Link (27/10/2026, anh chủ nhà đặt link YouTube mở bằng SmartTube trên tivi Skyworth): xem từng trang hay gặp có
 * app nào mở được link, tivi đang mở bằng gì; chọn app mở hoặc trả về Hỏi mỗi lần; đặt xong hỏi lại tivi xem đã nhận chưa.
 * Thêm được link / tên miền bất kỳ (nhớ trong phiên).
 */
class LienKetAppTivi(private val vm: PhienLamViec) {
    /** Một trang trên màn hình: app (không phải trình duyệt) mở được, tivi đang mở bằng gì. */
    class Dong(val trang: LienKetApp.Trang, ungVien: List<String>, moBang: MoBang, val tuThem: Boolean) {
        var ungVien by mutableStateOf(ungVien)
        var moBang by mutableStateOf(moBang)
    }

    val ds = mutableStateListOf<Dong>()
    var daQuet by mutableStateOf(false)
        private set
    /** Trang đang mở hộp chọn app. */
    var chon by mutableStateOf<Dong?>(null)
    var bao by mutableStateOf("")
    var nhapLink by mutableStateOf("")
    private val them = mutableListOf<LienKetApp.Trang>()
    private var trinhDuyet: Set<String> = emptySet()

    fun mo() = vm.chay(t("Đang xem app nào mở được link…", "Checking which apps open links…")) {
        vm.damBaoKetNoi()
        bao = ""
        quet()
        vm.man = Man.LIEN_KET_APP
    }

    fun docLai() = vm.chay(t("Đang xem app nào mở được link…", "Checking which apps open links…")) {
        vm.damBaoKetNoi()
        quet()
    }

    /** Một lệnh shell cho cả lượt (vài chục lệnh cmd package trên tivi, vài giây). */
    private suspend fun quet() {
        val trang = LienKetApp.TRANG + them
        val kq = LienKetApp.tuQuet(vm.io { vm.adb.shell(LienKetApp.lenhQuet(trang.map { it.link }), 180) }, trang.size)
        trinhDuyet = kq.trinhDuyet
        ds.clear()
        trang.zip(kq.ketQua).forEach { (tr, k) -> if (k.ungVien.isNotEmpty() || tr in them) ds += Dong(tr, k.ungVien, k.moBang, tr in them) }
        daQuet = true
    }

    /** Ô "trang khác": thêm link / tên miền người dùng gõ rồi quét lại. */
    fun themTrang() {
        val link = LienKetApp.chuanHoa(nhapLink)
        if (link == null) { bao = t("Không phải link trang web. Gõ tên miền (VD vimeo.com) hoặc dán cả link.", "That isn't a web link. Type a domain (e.g. vimeo.com) or paste a whole link."); return }
        val mien = LienKetApp.tenMien(link)
        if (ds.any { it.trang.link == link } || them.any { it.link == link }) { bao = t("$mien đã có trong danh sách.", "$mien is already in the list."); return }
        them += LienKetApp.Trang(mien, link, mien)
        nhapLink = ""; bao = ""
        docLai()
    }

    /**
     * Đặt [goi] mở link của trang [d] ([goi] null = Hỏi mỗi lần: trả mọi app của trang về mặc định), rồi hỏi lại tivi.
     * Android 12+: app được hãng xác minh trang vẫn thắng lựa chọn của người dùng thì tắt quyền mở link tự động của nó.
     * Android 11 trở xuống đặt theo app nên các trang khác của cùng app cũng đổi: quét lại cả danh sách.
     */
    fun dat(d: Dong, goi: String?) = vm.chay(t("Đang đặt…", "Applying…")) {
        chon = null
        vm.damBaoKetNoi()
        val sdk = vm.thongTin?.sdk ?: 0
        if (goi == null) vm.io { vm.adb.shell(LienKetApp.lenhBo(sdk, d.ungVien), 60) } else vm.io { vm.adb.shell(LienKetApp.lenhDat(sdk, goi), 60) }
        var mo = moBang(d.trang.link)
        var daChan: String? = null
        val thang = (mo as? MoBang.App)?.goi
        if (goi != null && sdk >= 31 && thang != null && thang != goi && thang in d.ungVien) {
            vm.io { vm.adb.shell(LienKetApp.lenhChan(thang), 60) }
            daChan = thang
            mo = moBang(d.trang.link)
        }
        val ten = d.trang.ten
        bao = when {
            goi == null && mo == MoBang.Hoi -> t("Đã trả về: tivi hỏi chọn app mỗi lần mở link $ten.", "Reset: the TV asks which app to use for $ten links.")
            goi == null && mo is MoBang.App -> t("Đã trả về mặc định: link $ten mở bằng ${tenDep(mo.goi)} (app được hãng chọn sẵn cho trang này).",
                "Back to default: $ten links open in ${tenDep(mo.goi)} (the app preset for this site).")
            goi == null -> t("Đã trả về mặc định cho link $ten.", "$ten links are back to the default.")
            mo == MoBang.App(goi) -> t("Đã đặt: link $ten giờ mở thẳng bằng ${tenDep(goi)}.", "Done: $ten links now open straight in ${tenDep(goi)}.") +
                (daChan?.let { t(" Đã tắt quyền tự mở link của ${tenDep(it)} vì nó được hãng chọn sẵn cho trang này.", " Turned off ${tenDep(it)}'s automatic link handling, since it was preset for this site.") } ?: "")
            else -> t("Tivi chưa nhận: ${moTa(mo)}. Có thể bản ${tenDep(goi)} trên tivi không khai trang $ten.",
                "The TV didn't take it: ${moTa(mo)}. The installed ${tenDep(goi)} may not declare $ten.")
        }
        quet()
    }

    private suspend fun moBang(link: String): MoBang = LienKetApp.moBangTu(vm.io { vm.adb.shell(LienKetApp.lenhChon(link), 30) }, trinhDuyet)

    /** Tivi vẫn mở link thế nào sau khi đặt không ăn (câu báo lỗi). */
    private fun moTa(mo: MoBang): String = when (mo) {
        MoBang.Hoi -> t("tivi vẫn hỏi chọn app mỗi lần", "the TV still asks which app to use")
        is MoBang.App -> t("link vẫn mở bằng ${tenDep(mo.goi)}", "links still open in ${tenDep(mo.goi)}")
        is MoBang.TrinhDuyet -> t("link vẫn mở bằng trình duyệt ${tenDep(mo.goi)}", "links still open in the ${tenDep(mo.goi)} browser")
        MoBang.KhongCo -> t("tivi không mở được link này", "the TV can't open these links")
    }

    /** Kết nối tivi khác. */
    fun quen() { ds.clear(); daQuet = false; chon = null; bao = ""; them.clear(); trinhDuyet = emptySet() }
}
