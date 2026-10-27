package vn.tivikit.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import vn.tivikit.dulieu.MaOtp
import vn.tivikit.dulieu.ThoiGianXem
import vn.tivikit.t
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Khoá trẻ em (23/10/2026; tách khỏi PhienLamViec 28/10/2026): soạn giờ khoá, giới hạn mỗi ngày, mã mở khoá rồi lưu lên
 * app khoá trên tivi (module khoa/).
 *
 * Mã OTP (28/10/2026) thay PIN 4 số: lúc cài, TiviKit tạo mã gốc (MaOtp), phụ huynh quét QR vào app xác thực, cất mã gốc
 * và 10 mã dự phòng. TiviKit không giữ lại mã gốc. Tivi đã có mã thì vào màn này, Dòng lệnh ADB hay Khôi phục đều phải
 * nhập mã: tivi kiểm tra rồi trả một phiên ngắn để ghi cấu hình. Trẻ cầm điện thoại của bố mẹ cũng không tắt được khoá.
 */
class KhoaTreTivi(private val vm: PhienLamViec) {
    /** Một khung giờ khoá: "HH:MM", ngay 1 = Thứ Hai … 7 = Chủ Nhật. */
    data class Khung(val tu: String, val den: String, val ngay: Set<Int>)

    /** Trạng thái app khoá trên tivi (không có mã); null = chưa cài. */
    var trenTv by mutableStateOf<JSONObject?>(null)
        private set
    var bat by mutableStateOf(true)
    val khung = mutableStateListOf<Khung>()
    var bao by mutableStateOf("")
    /** Giới hạn xem mỗi ngày, phút (0 = không giới hạn): thứ Hai–Sáu và thứ Bảy, Chủ Nhật (25/10/2026). */
    var phutThuong by mutableStateOf(0)
    var phutCuoiTuan by mutableStateOf(0)
    /** Báo cáo 7 ngày đọc từ app khoá; null = tivi chưa có app khoá bản đếm giờ. */
    var thoiGian by mutableStateOf<ThoiGianXem?>(null)
        private set

    /** Tivi đã có mã OTP (app khoá 1.2 trở lên). */
    val coOtp: Boolean get() = trenTv?.optBoolean("coOtp") == true
    /** Đã quá 24 giờ từ lúc báo quên mã: vào được không cần mã, nhưng phải cài mã mới mới lưu được. */
    val goDuoc: Boolean get() = trenTv?.optBoolean("goDuoc") == true

    // ---------- Cài / đổi mã ----------

    enum class Buoc { KHONG, QUET, CAT }
    /** Bước cài mã mới: KHONG = không cài; QUET = quét QR, nhập thử mã; CAT = cất mã dự phòng rồi lưu. */
    var buoc by mutableStateOf(Buoc.KHONG)
        private set
    /** Mã gốc đang cài, chưa gửi tivi (null khi chỉ tạo lại mã dự phòng). Lưu xong thì quên. */
    var khoaMoi by mutableStateOf<String?>(null)
        private set
    var maThu by mutableStateOf("")
    var duPhongMoi by mutableStateOf<List<String>>(emptyList())
        private set
    /** Phụ huynh xác nhận đã cất mã gốc và mã dự phòng. */
    var daCat by mutableStateOf(false)
    /** Ô dán mã gốc đã có (tivi khác trong nhà dùng chung một mã). */
    var khoaDan by mutableStateOf("")

    // ---------- Nhập mã ----------

    /** Đang hỏi mã: [viec] làm tiếp khi tivi nhận mã đúng. */
    class HoiMa(val lyDo: String, val viec: () -> Unit)
    var hoiMa by mutableStateOf<HoiMa?>(null)
        private set
    var maNhap by mutableStateOf("")
    var baoMa by mutableStateOf("")
        private set
    /** Đang hỏi lại "Quên mã?" trong hộp nhập mã. */
    var hoiQuen by mutableStateOf(false)

    /** Phiên tivi trả sau khi nhập đúng mã; giữ ngắn hơn hạn trên tivi (15 phút) và bỏ khi rời màn. */
    private var phien: String? = null
    private var phienHet = 0L

    /** Đúng mã trên tivi thì mở bao lâu (anh Alex chốt 60 phút). */
    val MO_TAM = 60

    private fun canMa(j: JSONObject?) = j?.optBoolean("coOtp") == true && !j.optBoolean("goDuoc")
    private fun phienCon(): String? = phien?.takeIf { System.currentTimeMillis() < phienHet }

    fun quen() { trenTv = null; thoiGian = null; khung.clear(); bao = ""; boPhien(); huyMaMoi(); dongHoiMa() }

    /** Rời màn / đổi tivi: lần sau phải nhập lại mã. */
    fun boPhien() { phien = null; phienHet = 0 }

    private suspend fun docThoiGian(j: JSONObject?): ThoiGianXem? =
        if (j == null || j.optBoolean("banCu")) null
        else vm.io { runCatching { vm.tv.docThoiGianKhoa()?.let { ThoiGianXem.tuJson(it) } }.getOrNull() }

    fun mo(): Unit = vm.chayRoi(t("Đọc cài đặt khoá trên tivi…", "Reading the lock settings…")) {
        vm.damBaoKetNoi()
        val j = vm.io { vm.tv.docKhoa() }
        if (canMa(j) && phienCon() == null) {
            hoi(t("Tivi này đã cài mã cho Khoá trẻ em: nhập mã để xem và đổi cài đặt khoá.",
                "This TV's Kids lock has a code: enter it to see and change the lock settings.")) { mo() }
            return@chayRoi null
        }
        trenTv = j; bao = ""; huyMaMoi()
        khung.clear()
        phutThuong = j?.optInt("phutThuong") ?: 0; phutCuoiTuan = j?.optInt("phutCuoiTuan") ?: 0
        thoiGian = docThoiGian(j)
        if (j != null) {
            bat = j.optBoolean("bat")
            val ks = j.optJSONArray("khung") ?: JSONArray()
            for (i in 0 until ks.length()) {
                val k = ks.getJSONObject(i); val ng = k.optJSONArray("ngay") ?: JSONArray()
                khung += Khung(k.optString("tu"), k.optString("den"), (0 until ng.length()).map { ng.getInt(it) }.toSet())
            }
        }
        if (khung.isEmpty() && phutThuong == 0 && phutCuoiTuan == 0) { bat = true; khung += Khung("21:00", "06:00", (1..7).toSet()) }
        vm.man = Man.KHOA_TRE
        null
    }

    /**
     * Việc gỡ / tắt được khoá ngoài màn này (Dòng lệnh ADB, Khôi phục): tivi có Khoá trẻ em đang bật với mã OTP thì hỏi mã
     * trước, không thì làm luôn.
     */
    fun quaCua(lyDo: String, viec: () -> Unit): Unit = vm.chayRoi(t("Đọc Khoá trẻ em trên tivi…", "Reading the Kids lock…")) {
        val j = vm.io { vm.tv.docKhoa() }
        if (canMa(j) && j!!.optBoolean("bat") && phienCon() == null) { hoi(lyDo, viec); null } else viec
    }

    private fun hoi(lyDo: String, viec: () -> Unit) { maNhap = ""; baoMa = ""; hoiQuen = false; hoiMa = HoiMa(lyDo, viec) }

    fun dongHoiMa() { hoiMa = null; maNhap = ""; baoMa = ""; hoiQuen = false }

    /** Gửi mã trong hộp nhập mã cho tivi kiểm tra; đúng thì giữ phiên và làm tiếp việc đang chờ. */
    fun nhapMa() {
        val h = hoiMa ?: return
        val ma = maNhap.filter(Char::isDigit)
        if (ma.length != MaOtp.SO_CHU_SO && ma.length != MaOtp.SO_CHU_SO_DU_PHONG) {
            baoMa = t("Nhập mã 6 số app xác thực đang hiện, hoặc một mã dự phòng 8 số.", "Enter the 6-digit code from the authenticator app, or an 8-digit backup code.")
            return
        }
        vm.chayRoi(t("Tivi đang kiểm tra mã…", "The TV is checking the code…")) {
            val kq = vm.io { vm.tv.xacThucKhoa(ma) }
            maNhap = ""
            when {
                kq?.optBoolean("ok") == true -> {
                    phien = kq.getString("phien"); phienHet = System.currentTimeMillis() + 14 * 60_000L
                    dongHoiMa()
                    return@chayRoi h.viec
                }
                kq?.optString("loi") == "sai" -> baoMa = t("Sai mã, còn ${kq.optInt("con")} lần thử.", "Wrong code, ${kq.optInt("con")} tries left.")
                kq?.optString("loi") == "cho" -> baoMa = t("Sai nhiều lần. Thử lại sau ${chuCho(kq.optLong("giay"))}.", "Too many wrong codes. Try again in ${chuCho(kq.optLong("giay"))}.")
                else -> baoMa = t("Tivi không trả lời. Kiểm tra kết nối rồi thử lại.", "The TV didn't answer. Check the connection and try again.")
            }
            null
        }
    }

    private fun chuCho(giay: Long): String = if (giay > 60) t("${(giay + 59) / 60} phút", "${(giay + 59) / 60} min") else t("$giay giây", "$giay s")

    /** Quên mã: báo tivi; 24 giờ sau vào được không cần mã (tivi nhắc suốt lúc chờ, ai nhập đúng mã là huỷ). */
    fun quenMa() = vm.chay(t("Đang gửi yêu cầu…", "Sending the request…")) {
        val kq = vm.io { vm.tv.quenMaKhoa() }
        val luc = kq?.optLong("goDuocLuc") ?: 0L
        hoiQuen = false
        baoMa = if (luc > 0) t("Đã gửi. Từ ${gio(luc)} vào được Khoá trẻ em không cần mã, rồi cài mã mới. Tới lúc đó tivi nhắc việc này; ai nhập đúng mã thì yêu cầu bị huỷ.",
            "Sent. From ${gio(luc)} you can open the Kids lock without the code and set a new one. Until then the TV shows a reminder; entering the right code cancels the request.")
            else t("Tivi không nhận yêu cầu. Kiểm tra kết nối rồi thử lại.", "The TV didn't take the request. Check the connection and try again.")
    }

    private fun gio(ms: Long) = SimpleDateFormat("HH:mm dd/MM", Locale.getDefault()).format(Date(ms))

    // ---------- Cài mã ----------

    /** Tạo mã gốc mới (cài lần đầu hoặc đổi mã gốc). */
    fun taoMa() { huyMaMoi(); khoaMoi = MaOtp.taoKhoa(); buoc = Buoc.QUET }

    /** Dùng mã gốc phụ huynh đã cất (của tivi khác trong nhà): app xác thực chỉ cần một dòng cho cả nhà. */
    fun dungKhoaDan() {
        val k = MaOtp.chuanKhoa(khoaDan) ?: run {
            vm.loi = t("Mã gốc chưa đúng: 32 chữ cái A–Z và số 2–7, như lúc cài tivi kia.", "That root key isn't valid: 32 letters A–Z and digits 2–7, as shown when the other TV was set up.")
            return
        }
        huyMaMoi(); khoaMoi = k; buoc = Buoc.QUET
    }

    /** Chỉ tạo lại mã dự phòng (mã gốc giữ nguyên). */
    fun taoDuPhong() { huyMaMoi(); duPhongMoi = MaOtp.taoDuPhong(); buoc = Buoc.CAT }

    fun huyMaMoi() { buoc = Buoc.KHONG; khoaMoi = null; maThu = ""; duPhongMoi = emptyList(); daCat = false; khoaDan = "" }

    /** Phụ huynh nhập mã app xác thực đang hiện cho mã gốc mới: khớp là đã quét đúng, tạo mã dự phòng. */
    fun thuMa() {
        val k = khoaMoi ?: return
        if (!MaOtp.dung(k, maThu.filter(Char::isDigit))) {
            vm.loi = t("Mã chưa khớp. Kiểm tra app xác thực đã quét đúng QR này và giờ trên điện thoại đúng, rồi nhập mã đang hiện.",
                "The code doesn't match. Check the authenticator scanned this QR and the phone's clock is right, then enter the current code.")
            return
        }
        vm.loi = ""; maThu = ""; duPhongMoi = MaOtp.taoDuPhong(); buoc = Buoc.CAT
    }

    // ---------- Lưu ----------

    fun luu(): Unit = vm.chay(t("Đang cài khoá lên tivi…", "Setting up the lock on the TV…")) {
        val coMaTv = coOtp && trenTv?.optBoolean("banCu") != true
        val maMoiXong = buoc == Buoc.CAT && daCat
        if (buoc == Buoc.QUET) error(t("Làm xong bước mã mới (quét QR, nhập thử mã) hoặc bấm Huỷ.", "Finish the new code (scan the QR, test a code) or tap Cancel."))
        if (buoc == Buoc.CAT && !daCat) error(t("Cất mã dự phòng rồi đánh dấu \"Đã cất\" trước khi lưu.", "Store the backup codes and tick \"Stored\" before saving."))
        if (!coMaTv && khoaMoi == null) error(t("Cài mã OTP trước (thẻ Mã mở khoá): khoá không dùng PIN nữa.", "Set up the OTP code first (Unlock code card): the lock no longer uses a PIN."))
        if (goDuoc && khoaMoi == null) error(t("Đã vào bằng Quên mã: cài mã OTP mới rồi mới lưu được.", "You came in through Forgot code: set up a new OTP code before saving."))
        if (khung.isEmpty() && phutThuong == 0 && phutCuoiTuan == 0) error(t("Thêm ít nhất một khung giờ khoá hoặc đặt giới hạn mỗi ngày.", "Add a lock period or a daily limit."))
        val gio = Regex("^([01][0-9]|2[0-3]):[0-5][0-9]$")
        khung.forEach { if (!gio.matches(it.tu) || !gio.matches(it.den) || it.ngay.isEmpty()) error(t("Khung giờ ${it.tu}–${it.den} chưa đúng (giờ dạng 21:00, chọn ít nhất một ngày).", "Period ${it.tu}–${it.den} is not valid.")) }
        val j = JSONObject().put("bat", bat).put("moTamPhut", MO_TAM)
            .put("khung", JSONArray(khung.map { JSONObject().put("tu", it.tu).put("den", it.den).put("ngay", JSONArray(it.ngay.sorted())) }))
            .put("phutThuong", phutThuong).put("phutCuoiTuan", phutCuoiTuan)
        if (coMaTv && !goDuoc) j.put("phien", phienHoac { luu() } ?: return@chay)
        if (maMoiXong) MaOtp.ghiVao(j, khoaMoi, duPhongMoi)
        val apk = vm.io { vn.tivikit.CapNhat.moAsset(vm.ctx, "khoa.apk").readBytes() }
        try { vm.io { vm.tv.caiKhoa(apk, j.toString()) { vm.thongBao = it } } }
        catch (e: vn.tivikit.adb.ThaoTacTv.CanMaException) { boPhien(); phienHoac { luu() }; return@chay }
        val doiMa = when { !maMoiXong -> ""
            khoaMoi != null -> t("Đã lưu mã mới: từ giờ dùng mã này và các mã dự phòng vừa cất. ", "The new code is saved: use it and the backup codes you just stored. ")
            else -> t("Đã lưu mã dự phòng mới: các mã dự phòng cũ hết dùng được. ", "New backup codes saved: the old backup codes no longer work. ") }
        huyMaMoi()
        kotlinx.coroutines.delay(2500)
        trenTv = vm.io { vm.tv.docKhoa() }
        thoiGian = docThoiGian(trenTv)
        val chay = trenTv?.optBoolean("dichVuChay") == true
        val coHan = phutThuong > 0 || phutCuoiTuan > 0
        bao = doiMa +
            if (!bat) t("Đã lưu. Khoá đang tắt.", "Saved. The lock is off.")
            else if (chay && coHan) t("Đã bật khoá. Trong khung giờ khoá hoặc khi đã xem hết giờ trong ngày, tivi hiện màn khoá (còn 5 phút thì báo trước); nhập mã trong app xác thực thì mở $MO_TAM phút.",
                "The lock is on. During the periods, or once today's time is used up, the TV shows the lock screen (with a 5-minute warning); a code from the authenticator app unlocks it for $MO_TAM minutes.")
            else if (chay) t("Đã bật khoá. Trong khung giờ, tivi hiện màn khoá; nhập mã trong app xác thực thì mở $MO_TAM phút.", "The lock is on. During the periods the TV shows the lock screen; a code from the authenticator app unlocks it for $MO_TAM minutes.")
            else t("Đã lưu nhưng dịch vụ khoá chưa chạy. Tắt tivi bật lại rồi mở lại màn này kiểm tra.", "Saved, but the lock service isn't running yet. Restart the TV and check again.")
        vn.tivikit.Dem.gui(vm.ctx, "khoa", vm.tenTiviDem(), khung.size, if (bat) "bat" else "tat")
    }

    /**
     * Phiên còn hạn để gửi kèm lệnh. Hết hạn (quá 14 phút, hay tivi khởi động lại) thì hỏi lại mã — nhập đúng thì làm lại
     * [lai], thay đổi đang soạn vẫn giữ — và trả null.
     */
    private fun phienHoac(lai: () -> Unit): String? = phienCon() ?: run {
        boPhien()
        hoi(t("Đã quá 15 phút từ lần nhập mã trước: nhập lại mã để làm tiếp.", "It's been over 15 minutes since the code was entered: enter it again to continue."), lai)
        null
    }

    fun moTam(phut: Int): Unit = vm.chay(t("Đang gửi lệnh…", "Sending…")) {
        val p = if (coOtp && !goDuoc) phienHoac { moTam(phut) } ?: return@chay else null
        try { vm.io { vm.tv.moTamKhoa(phut, p) } }
        catch (e: vn.tivikit.adb.ThaoTacTv.CanMaException) { boPhien(); phienHoac { moTam(phut) }; return@chay }
        kotlinx.coroutines.delay(800)
        trenTv = vm.io { vm.tv.docKhoa() }
        bao = if (phut > 0) t("Đã mở tạm $phut phút.", "Unlocked for $phut minutes.") else t("Đã khoá lại (nếu đang trong giờ khoá hoặc đã hết giờ xem).", "Locked again (if within a period or out of time for today).")
    }

    fun tat() = vm.chay(t("Đang tắt khoá…", "Turning the lock off…")) {
        vm.io { vm.tv.tatKhoa(go = true, phien = phienCon()) }
        trenTv = null; thoiGian = null; huyMaMoi(); boPhien()
        bao = t("Đã tắt và gỡ Khoá trẻ em khỏi tivi. Dòng TiviKit trong app xác thực không dùng nữa, xoá được.",
            "The Kids lock is off and removed from the TV. You can delete the TiviKit entry in the authenticator app.")
    }
}
