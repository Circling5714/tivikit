package vn.tivikit.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import vn.tivikit.adb.DoMang
import vn.tivikit.dulieu.AnhNen
import vn.tivikit.lg.KetNoiLg
import vn.tivikit.lg.LgLoiException
import vn.tivikit.lg.LgTuChoiException
import vn.tivikit.lg.SshLg
import vn.tivikit.lg.SshSaiMatKhauException
import vn.tivikit.lg.Vibehack
import vn.tivikit.lg.WebOs
import vn.tivikit.t
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Tivi LG webOS (25/10/2026, anh Alex): tivi LG đã ghép đôi (IP, tên, client-key, MAC), quét mạng tìm tivi LG (cổng
 * 3000 / 3001), kết nối, và các việc làm được không cần root: remote, gõ chữ có dấu, mở app / YouTube / trang web,
 * đổi nguồn vào, tắt / bật màn hình, tắt tivi, bật tivi qua mạng (Wake-on-LAN), gửi lời nhắn lên màn hình. Độc lập với
 * kết nối ADB của tivi Android.
 */
class LgTivi(private val vm: PhienLamViec) {
    data class TiviLg(val ip: String, val ten: String, val khoa: String, val mac: String, val luc: Long)

    private val prefs = vm.ctx.getSharedPreferences("tvsach", Context.MODE_PRIVATE)
    var dsTivi by mutableStateOf(nap())
        private set
    /** IP các máy quét thấy mở cổng 3000 / 3001. */
    val timThay = mutableStateListOf<String>()
    var dangQuet by mutableStateOf(false)
        private set
    /** Tivi đang nối; null = chưa nối. */
    var dangNoi by mutableStateOf<TiviLg?>(null)
        private set
    /** "webOSTV 6.0 · 03.51.15". */
    var phienBan by mutableStateOf("")
        private set
    var apps by mutableStateOf<List<WebOs.App>>(emptyList())
        private set
    var nguon by mutableStateOf<List<WebOs.Nguon>>(emptyList())
        private set
    /** Biểu tượng app theo id app, tải dần từ tivi. */
    val icon = mutableStateMapOf<String, ImageBitmap>()
    /** Chữ gõ lên tivi, link YouTube / trang web, lời nhắn hiện trên tivi. */
    var chu by mutableStateOf("")
    var link by mutableStateOf("")
    var loiNhan by mutableStateOf("")
    /** Dòng trạng thái nhỏ (đã gửi, lỗi…); [baoLoi] tô đỏ. */
    var bao by mutableStateOf("")
        private set
    var baoLoi by mutableStateOf(false)
        private set

    private var ket: KetNoiLg? = null
    private val khoaNoi = Mutex()

    fun mo() { bao = ""; vm.man = Man.LG }

    // ---------- Tivi đã ghép đôi ----------

    private fun nap(): List<TiviLg> = runCatching {
        val a = JSONArray(prefs.getString("lg_tivi", null) ?: "[]")
        (0 until a.length()).map { i -> a.getJSONObject(i).let { o -> TiviLg(o.getString("ip"), o.optString("ten"), o.optString("khoa"), o.optString("mac"), o.optLong("luc")) } }
    }.getOrDefault(emptyList())

    private fun luu(ds: List<TiviLg>) {
        dsTivi = ds.take(6)
        prefs.edit().putString("lg_tivi", JSONArray(dsTivi.map {
            JSONObject().put("ip", it.ip).put("ten", it.ten).put("khoa", it.khoa).put("mac", it.mac).put("luc", it.luc)
        }).toString()).apply()
    }

    fun quen(tv: TiviLg) = luu(dsTivi.filter { it.ip != tv.ip })

    // ---------- Tìm, nối ----------

    /** Quét mạng /24 của điện thoại tìm máy mở cổng 3000 hoặc 3001 (tivi LG đang bật). */
    fun quet() {
        if (dangQuet) return
        dangQuet = true; timThay.clear(); bao(""); vm.loi = ""
        vm.viewModelScope.launch {
            try {
                val goc = vm.ipWifi() ?: run { bao(t("Điện thoại chưa vào Wi-Fi.", "The phone is not on Wi-Fi."), true); return@launch }
                val mang = vm.mangDo(goc)
                val dau = goc.substringBeforeLast('.')
                val luong = Dispatchers.IO.limitedParallelism(128)
                (1..254).map { "$dau.$it" }.filter { it != goc }.map { dc ->
                    async(luong) {
                        val co = DoMang.doCong(dc, WebOs.CONG_WS, 1000, mang) == DoMang.Cong.MO ||
                            DoMang.doCong(dc, WebOs.CONG_WSS, 1000, mang) == DoMang.Cong.MO
                        if (co) withContext(Dispatchers.Main) { if (dc !in timThay) timThay += dc }
                    }
                }.awaitAll()
                if (timThay.isEmpty()) bao(t("Không thấy tivi LG nào. Tivi phải đang bật (không phải chờ), cùng Wi-Fi với điện thoại.",
                    "No LG TV found. The TV must be on (not in standby) and on the same Wi-Fi as the phone."), true)
            } finally { dangQuet = false }
        }
    }

    /** Tên tivi LG đã ghép đôi ở [ip]. */
    fun tenCua(ip: String): String? = dsTivi.firstOrNull { it.ip == ip }?.ten?.takeIf { it.isNotBlank() }

    /** Nối tivi LG ở [ip]. Lần đầu tivi hiện hộp hỏi: bấm Cho phép. */
    fun noi(ip: String) = vm.chay(t("Đang kết nối tivi LG…", "Connecting to the LG TV…")) {
        bao("")
        khoaNoi.withLock { noiNgay(ip.trim()) }
        taiApp(); taiNguon()
        kiemTraRoot()
    }

    private suspend fun noiNgay(ip: String) {
        ket?.dong(); ket = null; dangNoi = null
        if (ip.isBlank()) error(t("Nhập địa chỉ IP của tivi.", "Enter the TV's IP address."))
        val cu = dsTivi.firstOrNull { it.ip == ip }
        val k = KetNoiLg(ip, vm.mangDo(ip)?.socketFactory)
        val khoa = try {
            k.noi(cu?.khoa?.takeIf { it.isNotBlank() }) {
                vm.thongBao = t("Nhìn lên tivi: bấm Cho phép (Allow / Đồng ý) để TiviKit điều khiển tivi.", "Look at the TV: press Allow so TiviKit can control it.")
            }
        } catch (e: LgTuChoiException) {
            k.dong()
            error(t("Tivi không cho phép kết nối (bấm Từ chối hoặc hết giờ hộp hỏi). Bấm Kết nối lại rồi chọn Cho phép trên tivi.",
                "The TV refused (Deny pressed or the prompt timed out). Connect again and choose Allow on the TV."))
        } catch (e: Exception) {
            k.dong()
            error(t("Không kết nối được tivi LG ở $ip: ", "Couldn't connect to the LG TV at $ip: ") + (e.message ?: e.javaClass.simpleName))
        }
        ket = k
        val he = runCatching { k.yeuCau("system/getSystemInfo") }.getOrNull()
        val pm = runCatching { k.yeuCau("com.webos.service.update/getCurrentSWInformation") }.getOrNull()
        val mac = runCatching { WebOs.mac(k.yeuCau("com.webos.service.connectionmanager/getinfo")) }.getOrNull()
        phienBan = listOfNotNull(pm?.optString("product_name")?.takeIf { it.isNotBlank() },
            pm?.let { "${it.optString("major_ver")}.${it.optString("minor_ver")}" }?.takeIf { it.length > 1 }).joinToString(" · ")
        val ten = he?.optString("modelName")?.takeIf { it.isNotBlank() }?.let { "LG $it" } ?: cu?.ten?.takeIf { it.isNotBlank() } ?: "LG webOS"
        val tv = TiviLg(ip, ten, khoa, mac ?: cu?.mac.orEmpty(), System.currentTimeMillis())
        luu(listOf(tv) + dsTivi.filter { it.ip != ip })
        dangNoi = tv
    }

    /** Kết nối còn sống, rớt thì nối lại im lặng bằng client-key đã lưu (tivi không hỏi lại). */
    private suspend fun can(): KetNoiLg {
        ket?.takeIf { it.song }?.let { return it }
        val tv = dangNoi ?: error(t("Chưa nối tivi LG.", "No LG TV connected."))
        khoaNoi.withLock { if (ket?.song != true) noiNgay(tv.ip) }
        return ket ?: error(t("Mất kết nối với tivi LG.", "Lost the LG TV."))
    }

    fun ngat() {
        ket?.dong(); ket = null; dangNoi = null; apps = emptyList(); nguon = emptyList(); icon.clear(); bao("")
        root = Root.CHUA_BIET; telnetMo = false; coVibehack = false; coInputHook = false; nhatKy.clear()
    }

    // ---------- Việc trên tivi ----------

    /** Chạy [viec] trên tivi, báo [xong] nếu được, lỗi thì báo lỗi (không chặn màn như vm.chay: remote bấm liên tục được). */
    private fun lam(xong: String? = null, viec: suspend (KetNoiLg) -> Unit) {
        vm.viewModelScope.launch {
            runCatching { viec(can()) }
                .onSuccess { if (xong != null) bao(xong) }
                .onFailure { bao(it.message ?: t("Tivi không làm được việc này.", "The TV couldn't do that."), true) }
        }
    }

    /** Một phím remote LG (WebOs.nut). */
    fun nut(ten: String) = lam { it.nut(ten) }

    /** Gõ chữ (có dấu được) vào ô đang chọn trên tivi; [enter] = bấm Enter sau khi gõ. */
    fun guiChu(enter: Boolean) {
        val s = java.text.Normalizer.normalize(chu, java.text.Normalizer.Form.NFC)
        if (s.isEmpty() && !enter) return
        lam(t("Đã gửi.", "Sent.")) { k ->
            if (s.isNotEmpty()) runCatching { k.yeuCau("com.webos.service.ime/insertText", JSONObject().put("text", s).put("replace", 0)) }
                .onFailure { error(t("Tivi chưa có ô nhập chữ đang chọn: chọn ô tìm kiếm trên tivi trước rồi gửi lại.", "No text box is selected on the TV: select the search box first, then send again.")) }
            if (enter) k.yeuCau("com.webos.service.ime/sendEnterKey")
            chu = ""
        }
    }

    fun xoaChu() = lam { it.yeuCau("com.webos.service.ime/deleteCharacters", JSONObject().put("count", 1)) }

    fun moApp(a: WebOs.App) = lam(t("Đã mở ${a.ten}.", "Opened ${a.ten}.")) { it.yeuCau("system.launcher/launch", JSONObject().put("id", a.id)) }

    /** Link YouTube thì mở bằng app YouTube của tivi, link khác thì mở trình duyệt của tivi. */
    fun moLink() {
        val s = link.trim()
        val ma = WebOs.maYoutube(s)
        when {
            ma != null -> lam(t("Đã mở trên YouTube.", "Opened in YouTube.")) { it.yeuCau("system.launcher/launch", WebOs.moYoutube(ma)) }
            s.startsWith("http://") || s.startsWith("https://") -> lam(t("Đã mở trong trình duyệt của tivi.", "Opened in the TV's browser.")) {
                it.yeuCau("system.launcher/open", JSONObject().put("target", s))
            }
            else -> bao(t("Dán link YouTube hoặc link trang web (bắt đầu bằng https://).", "Paste a YouTube link or a web link (starting with https://)."), true)
        }
    }

    fun doiNguon(n: WebOs.Nguon) = lam(t("Đã chuyển sang ${n.ten}.", "Switched to ${n.ten}.")) { it.yeuCau("tv/switchInput", JSONObject().put("inputId", n.id)) }

    /** Tắt / bật màn hình (tivi vẫn chạy, vẫn nghe tiếng). webOS 4 dùng tên dịch vụ khác. */
    fun manHinh(bat: Boolean) = lam(if (bat) t("Đã bật màn hình.", "Screen on.") else t("Đã tắt màn hình, tiếng vẫn phát. Bấm phím bất kỳ trên remote để bật lại.", "Screen off, sound keeps playing. Press any remote key to turn it back on.")) { k ->
        val viec = if (bat) "turnOnScreen" else "turnOffScreen"
        val p = JSONObject().put("standbyMode", "active")
        try { k.yeuCau("com.webos.service.tvpower/power/$viec", p) } catch (e: LgLoiException) {
            if (e.khongCo) k.yeuCau("com.webos.service.tv.power/$viec", p) else throw e
        }
    }

    fun tatTivi() {
        val k = ket ?: return
        k.gui("system/turnOff")
        ngat()
        bao(t("Đã tắt tivi. Bật lại bằng remote, hoặc nút Bật qua mạng nếu tivi hỗ trợ.", "The TV is off. Turn it on with the remote, or Wake over the network if the TV supports it."))
    }

    /** Gửi lời nhắn hiện ở góc màn hình tivi. */
    fun guiLoiNhan() {
        val s = loiNhan.trim()
        if (s.isEmpty()) return
        lam(t("Đã hiện lời nhắn trên tivi.", "Message shown on the TV.")) { it.yeuCau("system.notifications/createToast", JSONObject().put("message", s)); loiNhan = "" }
    }

    /**
     * Bật tivi qua mạng (Wake-on-LAN, cần MAC đã lưu khi nối). Tivi phải bật "Bật TV qua Wi-Fi / mạng" trong Cài đặt →
     * Chung → Thiết bị (tên tuỳ đời tivi), không thì tivi đang chờ không nghe gói này.
     */
    fun batTivi(tv: TiviLg) {
        val goi = WebOs.goiWol(tv.mac) ?: run { bao(t("Chưa có địa chỉ MAC của tivi này: nối tivi một lần lúc đang bật để app ghi lại.", "No MAC address yet: connect once while the TV is on so the app can save it."), true); return }
        vm.viewModelScope.launch {
            val ok = vm.io {
                runCatching {
                    DatagramSocket().use { s ->
                        vm.mangDo(tv.ip)?.let { m -> runCatching { m.bindSocket(s) } }
                        s.broadcast = true
                        val dich = listOf("255.255.255.255", tv.ip.substringBeforeLast('.') + ".255")
                        repeat(3) { dich.forEach { d -> s.send(DatagramPacket(goi, goi.size, InetAddress.getByName(d), 9)) } }
                    }
                }.isSuccess
            }
            if (ok) bao(t("Đã gửi lệnh bật tới ${tv.ten}. Chờ tivi lên (vài giây) rồi bấm Nối lại. Không lên: bật \"Bật TV qua Wi-Fi\" trong Cài đặt → Chung → Thiết bị của tivi.",
                "Wake-up sent to ${tv.ten}. Wait a few seconds for the TV, then tap Reconnect. Nothing? Turn on \"Turn on via Wi-Fi\" in the TV's Settings → General → Devices."))
            else bao(t("Không gửi được lệnh bật (điện thoại chưa vào Wi-Fi?).", "Couldn't send the wake-up (is the phone on Wi-Fi?)."), true)
        }
    }

    // ---------- Tivi đã root (Homebrew Channel + SSH, 25/10/2026) ----------

    /**
     * CHUA_BIET: chưa / đang kiểm tra; CHUA_ROOT; HOMEBREW_CHUA_SSH: có Homebrew Channel nhưng chưa bật SSH Server (hoặc
     * chưa root thật); SAI_MAT_KHAU: có SSH root nhưng mật khẩu không phải mặc định; DA_ROOT: đăng nhập root được.
     */
    enum class Root { CHUA_BIET, CHUA_ROOT, HOMEBREW_CHUA_SSH, SAI_MAT_KHAU, DA_ROOT }
    var root by mutableStateOf(Root.CHUA_BIET)
        private set
    var dangKiemTra by mutableStateOf(false)
        private set
    /** Telnet của Homebrew Channel đang mở: ai cùng Wi-Fi cũng vào quyền root không cần mật khẩu. */
    var telnetMo by mutableStateOf(false)
        private set
    var coVibehack by mutableStateOf(false)
        private set
    var coInputHook by mutableStateOf(false)
        private set
    /** Tuỳ chọn màn hình chính kiểu Apple TV (LG TV Vibehack) và ảnh nền chọn từ điện thoại (1920×1080 JPEG). */
    var tuyChon by mutableStateOf(Vibehack.TuyChon())
    var anhNen by mutableStateOf<ByteArray?>(null)
        private set
    /** Chữ script cài in ra (đã bỏ mã màu), mới nhất ở cuối. */
    val nhatKy = mutableStateListOf<String>()

    /** Mật khẩu root SSH của tivi [ip]: mặc định của Homebrew Channel là alpine. */
    private fun matKhau(ip: String) = prefs.getString("lg_ssh_$ip", null) ?: "alpine"

    /**
     * Tự nhận ra tivi đã root: Homebrew Channel có trong danh sách app, cổng 22 chào bằng SSH (dropbear của Homebrew
     * Channel; chế độ nhà phát triển dùng cổng 9922, không phải root), đăng nhập root được và có /var/lib/webosbrew.
     * Kèm xem Telnet (cổng 23) có đang mở không để cảnh báo.
     */
    fun kiemTraRoot() {
        val tv = dangNoi ?: return
        if (dangKiemTra) return
        dangKiemTra = true
        vm.viewModelScope.launch {
            try {
                val ip = tv.ip
                val mang = vm.mangDo(ip)
                val ids = apps.map { it.id }.toSet()
                coVibehack = Vibehack.APP in ids; coInputHook = Vibehack.INPUT_HOOK in ids
                val coHomebrew = Vibehack.HOMEBREW in ids
                telnetMo = vm.io { DoMang.doCong(ip, 23, 1500, mang) == DoMang.Cong.MO }
                val chao = vm.io { SshLg.dongDau(ip, SshLg.CONG, 2500, mang?.socketFactory) }
                root = if (!Vibehack.laSsh(chao)) { if (coHomebrew) Root.HOMEBREW_CHUA_SSH else Root.CHUA_ROOT } else {
                    val kq = runCatching { vm.io { SshLg(ip, matKhau(ip), mang?.socketFactory).kiemTra() } }
                    val k = kq.getOrNull()
                    when {
                        kq.exceptionOrNull() is SshSaiMatKhauException -> Root.SAI_MAT_KHAU
                        k == null -> if (coHomebrew) Root.HOMEBREW_CHUA_SSH else Root.CHUA_ROOT
                        k.laRoot -> { coVibehack = coVibehack || k.coVibehack; coInputHook = coInputHook || k.coInputHook; Root.DA_ROOT }
                        else -> Root.CHUA_ROOT
                    }
                }
            } finally { dangKiemTra = false }
        }
    }

    /** Mật khẩu root khác mặc định: lưu cho tivi này rồi kiểm tra lại. */
    fun datMatKhau(mk: String) {
        val tv = dangNoi ?: return
        prefs.edit().putString("lg_ssh_${tv.ip}", mk).apply()
        kiemTraRoot()
    }

    fun chonAnhNen(uri: android.net.Uri) = vm.chay(t("Đang chuẩn bị ảnh…", "Preparing the picture…")) {
        val goc = vm.io { AnhNen.goc(vm.ctx, uri) } ?: error(t("Không đọc được ảnh này, thử ảnh khác.", "Couldn't read this picture, try another one."))
        anhNen = vm.io { AnhNen.cat(goc, AnhNen.Khung()) } ?: error(t("Không cắt được ảnh này, thử ảnh khác.", "Couldn't crop this picture, try another one."))
        tuyChon = tuyChon.copy(coAnhNen = true, boAnhNen = false)
    }

    fun boAnhNen() { anhNen = null; tuyChon = tuyChon.copy(coAnhNen = false, boAnhNen = true) }

    /** Cài / cập nhật màn hình chính kiểu Apple TV với tuỳ chọn đang chọn (tivi cần Internet để tải bản cài). */
    fun caiVibehack() = chaySsh(t("Đang cài màn hình chính mới lên tivi (1–2 phút)…", "Installing the new home screen (1–2 minutes)…"),
        t("Đã cài xong. Khởi động lại tivi một lần để nút Home và tự mở khi bật tivi có hiệu lực.", "Installed. Restart the TV once so the Home button and auto-start take effect.")) { ssh ->
        val tc = tuyChon.copy(coAnhNen = anhNen != null)
        anhNen?.let { ssh.ghi(it, Vibehack.NEN_TAM) }
        ssh.chay(Vibehack.lenhCai(tc), 600) { ghiNhatKy(it) }.also { (ma, _) -> if (ma == 0) coVibehack = true }
    }

    fun goVibehack() = chaySsh(t("Đang gỡ màn hình chính mới…", "Removing the new home screen…"),
        t("Đã gỡ. Khởi động lại tivi để nút Home về như cũ.", "Removed. Restart the TV to get LG's Home button back.")) { ssh ->
        ssh.chay(Vibehack.lenhGo(), 300) { ghiNhatKy(it) }.also { (ma, _) -> if (ma == 0) coVibehack = false }
    }

    fun khoiDongLaiTv() {
        val tv = dangNoi ?: return
        vm.viewModelScope.launch {
            // Tivi tắt ngay nên phiên SSH rớt giữa chừng: lỗi ở đây là bình thường.
            vm.io { runCatching { SshLg(tv.ip, matKhau(tv.ip), vm.mangDo(tv.ip)?.socketFactory).chay("(sleep 1; reboot) >/dev/null 2>&1 &", 10) } }
            ngat()
            bao(t("Tivi đang khởi động lại. Chờ tivi lên (khoảng 1 phút) rồi bấm Nối lại.", "The TV is restarting. Wait about a minute, then tap Reconnect."))
        }
    }

    /** Chạy một việc SSH root có chữ in ra (hiện ở nhật ký), báo [xong] nếu mã thoát 0. */
    private fun chaySsh(viec: String, xong: String, khoi: (SshLg) -> Pair<Int, String>) = vm.chay(viec) {
        val tv = dangNoi ?: error(t("Chưa nối tivi LG.", "No LG TV connected."))
        nhatKy.clear(); bao("")
        val (ma, ra) = try {
            vm.io { khoi(SshLg(tv.ip, matKhau(tv.ip), vm.mangDo(tv.ip)?.socketFactory)) }
        } catch (e: SshSaiMatKhauException) {
            root = Root.SAI_MAT_KHAU
            error(t("Sai mật khẩu SSH root của tivi.", "Wrong root SSH password for the TV."))
        }
        if (ma != 0) error(t("Tivi báo lỗi (mã $ma): ", "The TV reported an error (code $ma): ") +
            (Vibehack.boMau(ra).lines().map { it.trim() }.lastOrNull { it.isNotEmpty() } ?: t("không rõ", "unknown")))
        taiApp()
        bao(xong)
    }

    private val tayChinh = android.os.Handler(android.os.Looper.getMainLooper())

    /** Một dòng script in ra (luồng SSH): thêm vào nhật ký, dòng bước ("==> …") lên thanh trạng thái. */
    private fun ghiNhatKy(dong: String) {
        val d = Vibehack.boMau(dong).trim()
        if (d.isEmpty()) return
        tayChinh.post {
            nhatKy += d
            if (nhatKy.size > 200) nhatKy.removeAt(0)
            if (d.startsWith("==>")) vm.thongBao = d.removePrefix("==>").trim()
        }
    }

    // ---------- Danh sách trên tivi ----------

    private suspend fun taiApp() {
        val k = ket ?: return
        apps = runCatching { WebOs.dsApp(k.yeuCau("com.webos.applicationManager/listLaunchPoints")) }.getOrDefault(emptyList())
        // Biểu tượng tải dần, vài cái một lúc, thu nhỏ còn khoảng 96 điểm ảnh.
        vm.viewModelScope.launch {
            val luong = Dispatchers.IO.limitedParallelism(4)
            apps.filter { it.icon.startsWith("http") && it.id !in icon }.map { a ->
                async(luong) {
                    val du = k.taiAnh(a.icon) ?: return@async
                    val bien = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(du, 0, du.size, bien)
                    val bm = BitmapFactory.decodeByteArray(du, 0, du.size, BitmapFactory.Options().apply { inSampleSize = maxOf(1, bien.outWidth / 96) }) ?: return@async
                    withContext(Dispatchers.Main) { icon[a.id] = bm.asImageBitmap() }
                }
            }.awaitAll()
        }
    }

    private suspend fun taiNguon() {
        val k = ket ?: return
        nguon = runCatching { WebOs.dsNguon(k.yeuCau("tv/getExternalInputList")) }.getOrDefault(emptyList())
    }

    private fun bao(chu: String, loi: Boolean = false) { bao = chu; baoLoi = loi }
}
