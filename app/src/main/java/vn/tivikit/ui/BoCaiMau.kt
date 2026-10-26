package vn.tivikit.ui

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import io.github.muntashirakon.adb.AdbPairingRequiredException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import vn.tivikit.adb.FileTrenTv
import vn.tivikit.adb.ThaoTacTv
import vn.tivikit.dulieu.BoCai
import vn.tivikit.dulieu.DnsRieng
import kotlinx.coroutines.CancellationException
import vn.tivikit.dulieu.GoiApk
import vn.tivikit.dulieu.GoiY
import vn.tivikit.dulieu.KhoBoCai
import vn.tivikit.dulieu.TaiLink
import vn.tivikit.dulieu.MucBoCai
import vn.tivikit.t
import java.io.File

/**
 * Bộ cài đặt mẫu (25/10/2026): soạn bộ (app + DNS chặn quảng cáo), áp dụng cho tivi đang nối (qua màn Cài app) hoặc
 * lần lượt cho nhiều tivi trong mạng (nối từng tivi ở cổng 5555, cài, bật DNS, ghi nhật ký từng tivi).
 */
class BoCaiMau(private val vm: PhienLamViec) {
    private val kho by lazy { KhoBoCai(vm.ctx.filesDir) }
    var ds by mutableStateOf<List<BoCai>>(emptyList())
        private set

    // ---- Soạn bộ ----
    var dangSua by mutableStateOf<BoCai?>(null)
        private set
    var suaTen by mutableStateOf("")
    var suaDnsLoai by mutableStateOf(DnsRieng.Loai.TAT)
    /** ID NextDNS hoặc tên máy chủ tự nhập. */
    var suaDnsGiaTri by mutableStateOf("")
    val suaApps = mutableStateListOf<MucBoCai>()

    // ---- Nhiều tivi ----
    class KetQuaTv(val ip: String) {
        var ten by mutableStateOf(ip)
        /** null = chưa xong; true = xong (có thể có app lỗi, xem nhật ký); false = không làm được tivi này. */
        var xong by mutableStateOf<Boolean?>(null)
        val nhatKy = mutableStateListOf<String>()
    }
    var chayBo by mutableStateOf<BoCai?>(null)
        private set
    val chonIp = mutableStateListOf<String>()
    var ipTay by mutableStateOf("")
    val ketQua = mutableStateListOf<KetQuaTv>()
    @Volatile private var phanTramCu = -1

    fun mo() { vm.man = Man.BO_CAI; ds = kho.tatCa() }

    /** Nút Quay lại: đang soạn / đang chọn tivi thì về danh sách bộ; true nếu đã xử lý. */
    fun quayLai(): Boolean = when {
        dangSua != null -> { huySua(); true }
        chayBo != null -> { chayBo = null; ketQua.clear(); true }
        else -> false
    }

    fun taoMoi() { batDauSua(BoCai("b" + System.currentTimeMillis(), "", emptyList())) }
    fun sua(b: BoCai) = batDauSua(b)

    private fun batDauSua(b: BoCai) {
        dangSua = b; suaTen = b.ten; suaDnsLoai = b.dnsLoai; suaDnsGiaTri = b.dnsGiaTri; suaApps.clear(); suaApps += b.apps
    }

    fun huySua() { dangSua?.let { kho.donFileThua(it.id) }; dangSua = null }

    /** Chép file đã chọn vào thư mục của bộ (đọc tên / phiên bản để hiện). */
    fun themFile(uris: List<Uri>) = vm.chay(t("Đang chép vào bộ…", "Copying into the set…")) {
        val b = dangSua ?: return@chay
        val loiDoc = mutableListOf<String>()
        for (u in uris) {
            runCatching {
                vm.io {
                    val fa = GoiApk.doc(vm.ctx, u, emptyList())
                    val thuMuc = kho.thuMucBo(b.id)
                    val ten = FileTrenTv.tenKhongTrung(FileTrenTv.tenAnToan(fa.ten), thuMuc.list()?.toSet() ?: emptySet())
                    val dich = File(thuMuc, ten)
                    if (!fa.file.renameTo(dich)) { fa.file.copyTo(dich, overwrite = true); fa.file.delete() }
                    val tt = fa.thongTin
                    MucBoCai(ten, tt?.ten ?: fa.ten, tt?.pkg ?: "", tt?.versionName ?: "", tt?.versionCode ?: 0, dich.length())
                }
            }.onSuccess { suaApps += it }.onFailure { loiDoc += it.message ?: it.javaClass.simpleName }
        }
        if (loiDoc.isNotEmpty()) vm.loi = loiDoc.joinToString("\n")
    }

    fun luuSua() {
        val b = dangSua ?: return
        if (suaTen.isBlank()) { vm.loi = t("Đặt tên cho bộ trước khi lưu.", "Name the set before saving."); return }
        // Sai định dạng ID NextDNS / tên máy chủ thì báo ngay, khỏi đợi tới lúc cài cho tivi.
        runCatching { DnsRieng.hostCho(suaDnsLoai, suaDnsGiaTri, "", ::t) }.onFailure { vm.loi = it.message ?: ""; return }
        kho.luu(b.copy(ten = suaTen.trim(), apps = suaApps.toList(), dnsLoai = suaDnsLoai, dnsGiaTri = suaDnsGiaTri.trim().lowercase()))
        dangSua = null
        ds = kho.tatCa()
    }

    fun xoa(b: BoCai) { kho.xoa(b.id); ds = kho.tatCa() }

    /** Số versionCode trong chuỗi "1.2.3 (45)" của ThaoTacTv.banTrenTv. */
    private fun soBan(ban: String?) = ban?.let { Regex("""\((\d+)\)""").find(it)?.groupValues?.get(1)?.toLongOrNull() }

    /** Áp dụng cho tivi đang nối: bật DNS (có sao lưu để Khôi phục trả lại), đưa app vào màn Cài app rồi cài luôn. */
    fun apDungTvNay(b: BoCai) = vm.chay(t("Đang áp dụng ${b.ten}…", "Applying ${b.ten}…")) {
        vm.damBaoKetNoi()
        val info = vm.thongTin ?: return@chay
        val abi = vm.layAbi()
        val thuMuc = kho.thuMucBo(b.id)
        val them = mutableListOf<PhienLamViec.AppCai>()
        val loiTai = mutableListOf<String>()
        for (m in b.apps) {
            val fa = try {
                if (m.goiY.isNotEmpty()) {
                    val g = GoiY.theoId(m.goiY) ?: continue
                    val url = vm.io { GoiY.timLink(g, abi) }
                    vm.taiVe(url, t("Đang tải ${g.ten}…", "Downloading ${g.ten}…"), g.ten)
                } else {
                    val f = File(thuMuc, m.file)
                    if (!f.isFile || vm.dsCai.any { it.file?.file == f }) continue
                    vm.io { GoiApk.tuFile(vm.ctx, m.ten, f, abi, tam = false) }
                }
            } catch (e: CancellationException) { throw e } catch (e: Throwable) {
                if (ThaoTacTv.laMatKetNoi(e)) throw e
                loiTai += "${m.ten}: ${e.message ?: e.javaClass.simpleName}"
                continue
            }
            val a = vm.themVaoDs(fa)
            val coSan = soBan(a.banTv)
            if (coSan != null && a.thongTin != null && coSan >= a.thongTin!!.versionCode) {
                a.trangThai = PhienLamViec.TrangThaiCai.XONG; a.tienDo = 1f; a.pkgDaCai = a.thongTin!!.pkg
                a.bao = t("Tivi đã có bản này, bỏ qua.", "Already on the TV, skipped.")
            }
            if (a.trangThai == PhienLamViec.TrangThaiCai.CHO) them += a
        }
        if (b.dnsLoai != DnsRieng.Loai.TAT) {
            if (info.sdk in 1..27) loiTai += t("DNS riêng cần Android 9 trở lên: bỏ qua.", "Private DNS needs Android 9 or later: skipped.")
            else {
                val dnsHost = DnsRieng.hostCho(b.dnsLoai, b.dnsGiaTri, info.tenHienThi, ::t)!!
                vm.thongBao = t("Đặt DNS $dnsHost, chờ tivi kiểm tra…", "Setting DNS $dnsHost, waiting for the TV to check it…")
                val sl = vm.saoLuu ?: vm.io { vm.tv.damBaoSaoLuu(info, vm.getprop, vm.ip.trim()) }
                vm.saoLuu = sl
                if (vm.io { vm.tv.datDnsAnToan(sl, dnsHost) } == false)
                    loiTai += t("Tivi không dùng được DNS $dnsHost: đã trả lại DNS cũ.", "The TV can't use DNS $dnsHost: the old DNS is back.")
                vm.dnsChanQc = vm.io { vm.tv.dnsDangChan() }
            }
        }
        if (loiTai.isNotEmpty()) vm.loi = loiTai.joinToString("\n")
        vm.man = Man.CAI_APK
        for (a in them) if (!vm.caiMot(a, goTruoc = false)) break
        runCatching { vm.trangThai = vm.io { vm.tv.docTrangThai() } }
        // vm.chay xoá loi lúc bắt đầu, không xoá lúc xong: lỗi tải vẫn hiện ở thanh dưới.
    }

    /** Thêm app gợi ý vào bộ đang soạn: khi áp dụng mới tải bản mới nhất. */
    fun themGoiY(g: GoiY.App) { if (suaApps.none { it.goiY == g.id }) suaApps += MucBoCai.tuGoiY(g) }

    fun moNhieuTv(b: BoCai) { chayBo = b; ketQua.clear(); chonIp.clear(); ipTay = "" }

    fun doiChonIp(ip: String) { if (ip in chonIp) chonIp.remove(ip) else chonIp += ip }

    /** IP gõ tay: 192.168.1.20 hoặc 192.168.1.20:5556. */
    fun themIpTay() {
        val s = ipTay.trim()
        if (!Regex("""^\d{1,3}(\.\d{1,3}){3}(:\d{1,5})?$""").matches(s)) { vm.loi = t("IP dạng 192.168.1.20 (hoặc 192.168.1.20:5555).", "Use an IP like 192.168.1.20 (or 192.168.1.20:5555)."); return }
        if (s !in chonIp) chonIp += s
        ipTay = ""
    }

    /** Lần lượt từng tivi; xong thì ngắt kết nối và đặt lại phiên (phiên cũ, nếu có, không còn đúng tivi). */
    fun chayNhieuTv() = vm.chay(t("Đang cài cho nhiều tivi…", "Setting up several TVs…")) {
        val b = chayBo ?: return@chay
        ketQua.clear()
        ketQua += chonIp.map { KetQuaTv(it) }
        vm.io { runCatching { vm.adb.disconnect() } }
        // App gợi ý: mỗi (app, ABI) tải một lần cho cả lượt; file nằm trong bộ nhớ đệm, datLai dọn khi xong.
        val daTai = mutableMapOf<String, File>()
        try {
            for (kq in ketQua) motTv(b, kq, daTai)
        } finally {
            vm.io { runCatching { vm.adb.disconnect() } }
            vm.datLai()
        }
    }

    private suspend fun motTv(b: BoCai, kq: KetQuaTv, daTai: MutableMap<String, File>) {
        fun ghi(s: String) { kq.nhatKy += s }
        try {
            val host = kq.ip.substringBefore(':')
            val cong = kq.ip.substringAfter(':', "5555").toIntOrNull() ?: 5555
            vm.thongBao = t("${kq.ip}: đang kết nối (tivi hỏi thì bấm Cho phép)…", "${kq.ip}: connecting (press Allow on the TV if asked)…")
            if (!vm.io { runCatching { vm.adb.disconnect() }; vm.adb.connect(host, cong) }) error(t("tivi không nhận kết nối", "the TV didn't accept the connection"))
            vm.tv.quenTivi()
            val (info, gp) = vm.io { vm.tv.docThongTin() }
            kq.ten = "${info.tenHienThi} (${kq.ip})"
            val abi = vm.io { vm.tv.abiTv() }
            val thuMuc = kho.thuMucBo(b.id)
            for (m in b.apps) {
                val f = if (m.goiY.isNotEmpty()) {
                    val g = GoiY.theoId(m.goiY) ?: continue
                    try {
                        val khoa = "${g.id}|${abi.firstOrNull()}"
                        daTai[khoa] ?: run {
                            vm.thongBao = t("Đang tải ${g.ten}…", "Downloading ${g.ten}…")
                            vm.io { TaiLink.tai(vm.ctx, GoiY.timLink(g, abi)) }.also { daTai[khoa] = it }
                        }
                    } catch (e: CancellationException) { throw e } catch (e: Throwable) {
                        ghi("${m.ten}: " + t("không tải được (${e.message})", "couldn't download (${e.message})")); continue
                    }
                } else File(thuMuc, m.file)
                if (!f.isFile) { ghi("${m.ten}: " + t("thiếu file trong bộ", "file missing from the set")); continue }
                val fa = vm.io { GoiApk.tuFile(vm.ctx, m.ten, f, abi, tam = false) }
                val tt = fa.thongTin
                if (tt != null && info.sdk > 0 && tt.minSdk > info.sdk) { ghi("${m.ten}: " + t("cần Android API ${tt.minSdk}, tivi là ${info.sdk}", "needs API ${tt.minSdk}, the TV is ${info.sdk}")); continue }
                val coSan = tt?.let { soBan(vm.io { vm.tv.banTrenTv(it.pkg) }) }
                if (tt != null && coSan != null && coSan >= tt.versionCode) { ghi("${m.ten}: " + t("đã có, bỏ qua", "already there, skipped")); continue }
                val viec = t("${info.tenHienThi}: đang cài ${m.ten}…", "${info.tenHienThi}: installing ${m.ten}…")
                vm.thongBao = viec; phanTramCu = -1
                val batDau = System.nanoTime()
                val out = vm.io { vm.caiFileApk(fa) { n -> baoTienDo(viec, n, fa.co, batDau) } }
                ghi("${m.ten}: " + if (out.contains("Success")) t("đã cài", "installed") else GoiApk.giaiThich(out, info.sdk))
            }
            if (b.dnsLoai != DnsRieng.Loai.TAT) {
                if (info.sdk in 1..27) ghi(t("DNS riêng cần Android 9 trở lên: bỏ qua.", "Private DNS needs Android 9 or later: skipped."))
                else {
                    // Tên tivi + số cuối IP: nhật ký NextDNS phân biệt được các tivi cùng mẫu trong một lượt.
                    val dnsHost = DnsRieng.hostCho(b.dnsLoai, b.dnsGiaTri, "${info.tenHienThi} ${host.substringAfterLast('.')}", ::t)!!
                    vm.thongBao = t("${info.tenHienThi}: đặt DNS, chờ tivi kiểm tra…", "${info.tenHienThi}: setting DNS, waiting for the TV to check it…")
                    val sl = vm.io { vm.tv.damBaoSaoLuu(info, gp, host) }
                    ghi(when (vm.io { vm.tv.datDnsAnToan(sl, dnsHost) }) {
                        true -> t("Đã bật DNS ${tenDns(b.dnsLoai)} ($dnsHost).", "DNS ${tenDns(b.dnsLoai)} on ($dnsHost).")
                        false -> t("Tivi không dùng được DNS $dnsHost: đã trả lại DNS cũ.", "The TV can't use DNS $dnsHost: the old DNS is back.")
                        null -> t("Đã đặt DNS $dnsHost, tivi chưa báo kết quả kiểm tra.", "DNS $dnsHost set; the TV hasn't reported its check yet.")
                    })
                }
            }
            kq.xong = true
        } catch (e: Throwable) {
            kq.xong = false
            ghi(when {
                e is AdbPairingRequiredException -> t("Tivi cần ghép mã (Gỡ lỗi không dây): làm riêng ở màn Kết nối.", "The TV needs pairing (Wireless debugging): do it from Connect.")
                ThaoTacTv.laMatKetNoi(e) -> t("Mất kết nối giữa chừng.", "Lost the connection.")
                listOfNotNull(e.message, e.cause?.message).any { "refused" in it || "ECONNREFUSED" in it } ->
                    t("Tivi từ chối: chưa bật Gỡ lỗi USB / Gỡ lỗi qua mạng (cổng 5555).", "Refused: USB / Network debugging (port 5555) is off.")
                e is java.net.SocketTimeoutException || e.message?.contains("timed out") == true ->
                    t("Không trả lời: tivi tắt, khác mạng, hoặc chưa bấm Cho phép trên tivi.", "No answer: the TV is off, on another network, or Allow wasn't pressed.")
                else -> e.message ?: e.javaClass.simpleName
            })
        }
    }

    private fun baoTienDo(viec: String, da: Long, tong: Long, batDau: Long) {
        val pt = if (tong > 0) (da * 100 / tong).toInt() else 0
        if (pt == phanTramCu) return
        phanTramCu = pt
        val them = vm.tocDo(da, tong, batDau)
        vm.viewModelScope.launch(Dispatchers.Main) { vm.thongBao = "$viec $pt%$them" }
    }
}
