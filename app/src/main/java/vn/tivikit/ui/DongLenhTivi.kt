package vn.tivikit.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import vn.tivikit.adb.KetNoiAdb
import vn.tivikit.dulieu.DongLenh
import vn.tivikit.dulieu.KeHoach
import vn.tivikit.t

/**
 * Dòng lệnh ADB (27/10/2026): gõ lệnh shell chạy thẳng trên tivi, kết quả hiện dần, bấm Dừng được; lệnh mẫu có giải thích.
 * Lệnh nguy hiểm hỏi lại trước (DongLenh.kiemTra); lệnh tắt / bật gói ghi vào bản sao lưu như Quản lý app để Khôi phục
 * tivi bật lại; settings put / delete in giá trị cũ ra trước. Lệnh chỉ đến từ ô gõ của màn này: không intent, link hay QR
 * nào đưa lệnh vào được, nên trang web lạ không chạy được gì lên tivi.
 */
class DongLenhTivi(private val vm: PhienLamViec) {
    enum class Loai { LENH, RA, GHI_CHU, LOI }
    data class Dong(val chu: String, val loai: Loai)

    /** Lệnh chờ người dùng đồng ý vì có cảnh báo cần hỏi. */
    class Hoi(val lenh: String, val canhBao: List<DongLenh.CanhBao>, val boAdb: Boolean) {
        val nguyHiem: Boolean get() = canhBao.any { it.mucDo == DongLenh.MucDo.NGUY_HIEM }
    }

    private val prefs by lazy { vm.ctx.getSharedPreferences("tvsach", Context.MODE_PRIVATE) }

    /** Các dòng đang hiện; khi [doDang] thì dòng cuối là dòng lệnh còn in dở. */
    val dong = mutableStateListOf<Dong>()
    /** Số dòng đầu đã bỏ cho nhẹ (giữ tối đa SO_DONG_TOI_DA). */
    var daBo by mutableStateOf(0)
        private set
    var nhap by mutableStateOf(TextFieldValue(""))
    /** Thư mục hiện tại trên tivi: cd ở lệnh trước được giữ (DongLenh.boc). */
    var thuMuc by mutableStateOf("/")
        private set
    var laRoot by mutableStateOf(false)
        private set
    var dangChay by mutableStateOf(false)
        private set
    /** 0 Dòng lệnh, 1 Lệnh mẫu. */
    var tab by mutableStateOf(0)
    /** Đã đọc trang cảnh báo lần đầu (nhớ trên điện thoại). */
    var daHieu by mutableStateOf(false)
        private set
    var hoi by mutableStateOf<Hoi?>(null)
    /** Lệnh mẫu đang điền chỗ trống (hộp Điền lệnh). */
    var dien by mutableStateOf<DongLenh.Mau?>(null)
    /** Lệnh đã chạy, mới nhất trước; chỉ lưu trên điện thoại. */
    val lichSu = mutableStateListOf<String>()
    var moLichSu by mutableStateOf(false)
    /** Tăng mỗi lần thêm chữ: khung kết quả cuộn theo. */
    var lanThem by mutableStateOf(0)
        private set

    private var doDang = false
    private var daDoRoot = false
    private var dangCho: KetNoiAdb.LenhDangChay? = null
    private var tachDangChay: DongLenh.BoTach? = null

    fun mo() {
        daHieu = prefs.getBoolean(DA_HIEU, false)
        if (lichSu.isEmpty()) napLichSu()
        vm.man = Man.DONG_LENH
        // adbd chạy bằng root (nhiều box Amlogic): dấu nhắc thành # và hiện nhãn root.
        if (!daDoRoot) vm.viewModelScope.launch {
            runCatching { laRoot = vm.io { vm.adb.shell("id -u", 10) }.trim() == "0"; daDoRoot = true }
        }
    }

    fun dongYCanhBao() { daHieu = true; prefs.edit().putBoolean(DA_HIEU, true).apply() }

    /** Nút Chạy / phím gửi: chuẩn hoá, kiểm tra; có cảnh báo cần hỏi thì mở hộp hỏi, không thì chạy luôn. */
    fun gui() {
        if (dangChay) return
        val goc = nhap.text.trim()
        if (goc.isEmpty()) return
        if (DongLenh.conCho(goc)) {
            ghi(t("Lệnh còn chỗ trống (như <gói>): điền giá trị thật vào trước khi chạy.", "The command still has a blank (like <package>): fill in a real value first."), Loai.LOI)
            return
        }
        val (lenh, boAdb) = DongLenh.chuanHoa(goc)
        if (lenh.isEmpty()) {
            ghi(t("Chỉ có \"adb shell\": gõ lệnh cần chạy phía sau, VD getprop ro.product.model.", "Just \"adb shell\": type the command that follows it, e.g. getprop ro.product.model."), Loai.GHI_CHU)
            return
        }
        val cb = DongLenh.kiemTra(lenh) { canGiu(it) }
        cb.firstOrNull { it.mucDo == DongLenh.MucDo.KHONG_CHAY }?.let { k ->
            ghiLenh(lenh)
            ghi(k.chu, Loai.LOI)
            return
        }
        if (cb.any { it.mucDo >= DongLenh.MucDo.CAN_THAN }) hoi = Hoi(lenh, cb, boAdb) else chay(lenh, cb, boAdb)
    }

    /** Hộp hỏi: Vẫn chạy. */
    fun dongY() {
        val h = hoi ?: return
        hoi = null
        chay(h.lenh, h.canhBao, h.boAdb)
    }

    private fun chay(lenh: String, canhBao: List<DongLenh.CanhBao>, boAdb: Boolean) {
        if (dangChay) return
        themLichSu(lenh)
        nhap = TextFieldValue("")
        ghiLenh(lenh)
        if (boAdb) ghi(t("Đã bỏ \"adb shell\" ở đầu: lệnh ở đây chạy thẳng trong shell của tivi.", "Dropped the leading \"adb shell\": commands here already run in the TV's shell."), Loai.GHI_CHU)
        canhBao.filter { it.mucDo == DongLenh.MucDo.BAO }.forEach { ghi(it.chu, Loai.GHI_CHU) }
        val dk = KetNoiAdb.LenhDangChay()
        val dau = "__TK" + java.lang.Long.toHexString(System.nanoTime()) + "_"
        val tach = DongLenh.BoTach(dau)
        dangCho = dk; tachDangChay = tach; dangChay = true
        vm.viewModelScope.launch {
            val cho = StringBuilder()
            try {
                vm.damBaoKetNoi()
                truocKhiChay(lenh)
                coroutineScope {
                    // Chữ tới trên luồng IO; cứ 80 ms đưa một lượt lên màn hình (logcat in hàng nghìn dòng mỗi giây).
                    val hien = launch { while (true) { delay(80); xa(cho) } }
                    try {
                        vm.io {
                            vm.adb.chayLenh(DongLenh.boc(lenh, thuMuc, dau), dk) { manh ->
                                val ra = tach.nap(manh)
                                if (ra.isNotEmpty()) synchronized(cho) { cho.append(ra) }
                                tach.xong
                            }
                        }
                    } finally { hien.cancel() }
                }
                synchronized(cho) { cho.append(tach.ket()) }
                xa(cho)
                tach.thuMuc?.let { thuMuc = it }
                val ma = tach.maThoat
                when {
                    dk.daDung -> ghi(t("(đã dừng)", "(stopped)"), Loai.GHI_CHU)
                    ma == null -> ghi(t("(tivi đóng kết nối trước khi lệnh xong — bình thường với lệnh khởi động lại, tắt Wi-Fi…)",
                        "(the TV closed the connection before the command finished — expected after a restart, Wi-Fi off…)"), Loai.GHI_CHU)
                    ma != 0 -> ghi(t("(mã thoát $ma)", "(exit code $ma)"), Loai.GHI_CHU)
                }
                if (!dk.daDung && ma != null) runCatching { sauKhiChay(lenh) }
            } catch (e: Throwable) {
                synchronized(cho) { cho.append(tach.ket()) }
                xa(cho)
                if (dk.daDung) ghi(t("(đã dừng)", "(stopped)"), Loai.GHI_CHU)
                else ghi(t("Lỗi: ", "Error: ") + (e.message ?: e.javaClass.simpleName), Loai.LOI)
            } finally {
                ketDong()
                dangChay = false; dangCho = null; tachDangChay = null
            }
        }
    }

    /** Nút Dừng: đóng luồng, giết sh đang chạy lệnh cùng tiến trình con (logcat, ping…) để không chạy ngầm trên tivi. */
    fun dung() {
        val dk = dangCho ?: return
        dk.dung()
        val pid = tachDangChay?.pid ?: return
        vm.viewModelScope.launch {
            runCatching { vm.io { vm.adb.shell("pkill -9 -P $pid 2>/dev/null; kill -9 $pid 2>/dev/null; true", 15) } }
        }
    }

    /** Trước lệnh: tắt gói thì chụp bản sao lưu trước (ảnh "trước" phải có trước khi tắt); đổi cài đặt thì in giá trị cũ. */
    private suspend fun truocKhiChay(lenh: String) {
        val info = vm.thongTin
        if (DongLenh.goiTat(lenh).isNotEmpty() && vm.saoLuu == null && info != null)
            vm.saoLuu = vm.io { vm.tv.damBaoSaoLuu(info, vm.getprop, vm.ip.trim()) }
        for ((bang, khoa) in DongLenh.caiDatDoi(lenh)) {
            val cu = runCatching { vm.io { vm.adb.shell("settings get $bang $khoa", 15) }.trim() }.getOrNull() ?: continue
            ghi(if (cu.isEmpty() || cu == "null") t("Trước khi đổi: $bang $khoa chưa đặt. Trả lại: settings delete $bang $khoa",
                    "Before the change: $bang $khoa is not set. To undo: settings delete $bang $khoa")
                else t("Trước khi đổi: $bang $khoa = $cu. Trả lại: settings put $bang $khoa ${DongLenh.nhayNeuCan(cu)}",
                    "Before the change: $bang $khoa = $cu. To undo: settings put $bang $khoa ${DongLenh.nhayNeuCan(cu)}"), Loai.GHI_CHU)
        }
    }

    /** Sau lệnh: gói vừa tắt / bật ghi vào (bỏ khỏi) bản sao lưu như Quản lý app; gói gỡ khỏi người dùng thì báo cách cài lại. */
    private suspend fun sauKhiChay(lenh: String) {
        val tat = DongLenh.goiTat(lenh)
        val bat = DongLenh.goiBat(lenh)
        val go = DongLenh.goiGo(lenh)
        if (tat.isEmpty() && bat.isEmpty() && go.isEmpty()) return
        val sl = vm.saoLuu
        if (sl != null) {
            var doi = false
            for (p in tat) if (p !in sl.daTatTruoc && p !in sl.daTatBoiApp && vm.io { vm.tv.dangTat(p) }) {
                sl.daTatBoiApp += p; doi = true
                ghi(t("Đã ghi $p vào bản sao lưu: Khôi phục tivi sẽ bật lại.", "Recorded $p in the backup: Restore will turn it back on."), Loai.GHI_CHU)
            }
            for (p in bat) if (p in sl.daTatBoiApp && !vm.io { vm.tv.dangTat(p) }) {
                sl.daTatBoiApp -= p; doi = true
                ghi(t("Đã bỏ $p khỏi danh sách Khôi phục cần bật lại.", "Removed $p from the list Restore turns back on."), Loai.GHI_CHU)
            }
            if (doi) vm.io { vm.tv.luuSaoLuu(sl) }
        }
        for (p in go) if (vm.io { vm.adb.shell("pm list packages $p", 20) }.lines().none { it.trim() == "package:$p" })
            ghi(t("$p đã gỡ khỏi người dùng; Khôi phục tivi không cài lại được. Cài lại bằng: pm install-existing $p",
                "$p was removed for the user; Restore can't bring it back. Reinstall with: pm install-existing $p"), Loai.GHI_CHU)
        runCatching { vm.trangThai = vm.io { vm.tv.docTrangThai() } }
    }

    /** Gói tivi cần để chạy: lõi Android (khối của CleanYourTV), Home đang dùng, bàn phím… (KeHoach). */
    private fun canGiu(p: String) = KeHoach.laGoiLoiAndroid(p) || vm.trangThai?.let { KeHoach.laGoiCanGiu(p, it) } == true

    // ---------- Lệnh mẫu, lịch sử ----------

    /** Bấm một lệnh mẫu: có chỗ trống thì mở hộp Điền lệnh, không thì đưa vào ô gõ (chưa chạy). */
    fun chonMau(m: DongLenh.Mau) { if (m.coCho) dien = m else datNhap(m.lenh) }

    /** Nút chạy ngay của lệnh mẫu không có chỗ trống (vẫn qua bước hỏi lại). */
    fun chayMau(m: DongLenh.Mau) { datNhap(m.lenh); gui() }

    fun dienXong(m: DongLenh.Mau, giaTri: Map<DongLenh.Cho, String>) { dien = null; datNhap(DongLenh.dien(m.lenh, giaTri)) }

    /** Lấy lại một lệnh trong lịch sử vào ô gõ. */
    fun dungLai(l: String) { moLichSu = false; datNhap(l) }

    private fun datNhap(l: String) { nhap = TextFieldValue(l, TextRange(l.length)); tab = 0 }

    fun xoaLichSu() { lichSu.clear(); prefs.edit().remove(LICH_SU).apply() }

    private fun napLichSu() {
        runCatching { val a = JSONArray(prefs.getString(LICH_SU, "[]")); for (i in 0 until a.length()) lichSu += a.getString(i) }
    }

    private fun themLichSu(l: String) {
        lichSu.remove(l); lichSu.add(0, l)
        while (lichSu.size > LICH_SU_TOI_DA) lichSu.removeAt(lichSu.lastIndex)
        prefs.edit().putString(LICH_SU, JSONArray(lichSu.toList()).toString()).apply()
    }

    // ---------- Khung kết quả ----------

    fun chepHet(): String = dong.joinToString("\n") { it.chu }

    fun xoaManHinh() { dong.clear(); daBo = 0; doDang = false }

    /** Đưa chữ đã nhận vào danh sách dòng: mẩu đầu nối vào dòng đang dở. */
    private fun xa(cho: StringBuilder) {
        val s = synchronized(cho) { cho.toString().also { cho.setLength(0) } }
        if (s.isEmpty()) return
        val phan = s.split('\n')
        val cuoi = dong.lastOrNull()
        if (doDang && cuoi != null) dong[dong.lastIndex] = cuoi.copy(chu = (cuoi.chu + phan[0]).take(DAI_TOI_DA))
        else dong += Dong(phan[0].take(DAI_TOI_DA), Loai.RA)
        for (i in 1 until phan.size) dong += Dong(phan[i].take(DAI_TOI_DA), Loai.RA)
        doDang = true
        catBot()
        lanThem++
    }

    /** Lệnh xong: bỏ dòng trống còn dở ở cuối. */
    private fun ketDong() {
        if (doDang && dong.lastOrNull()?.let { it.loai == Loai.RA && it.chu.isEmpty() } == true) dong.removeAt(dong.lastIndex)
        doDang = false
    }

    private fun ghi(chu: String, loai: Loai) {
        ketDong()
        dong += Dong(chu, loai)
        catBot()
        lanThem++
    }

    private fun ghiLenh(lenh: String) = ghi("$thuMuc ${if (laRoot) "#" else "$"} $lenh", Loai.LENH)

    private fun catBot() {
        if (dong.size <= SO_DONG_TOI_DA) return
        val bo = dong.size - SO_DONG_TOI_DA + 500
        dong.removeRange(0, bo)
        daBo += bo
    }

    /** Đổi / ngắt tivi: dừng lệnh đang chạy, xoá khung kết quả, thư mục về /. Lịch sử lệnh giữ (thuộc điện thoại). */
    fun quen() {
        dung()
        dong.clear(); daBo = 0; doDang = false
        thuMuc = "/"; laRoot = false; daDoRoot = false
        hoi = null; dien = null
    }

    private companion object {
        const val DA_HIEU = "dong_lenh_da_hieu"
        const val LICH_SU = "dong_lenh_lich_su"
        const val LICH_SU_TOI_DA = 50
        const val SO_DONG_TOI_DA = 3000
        const val DAI_TOI_DA = 4000
    }
}
