package vn.tivikit.cast

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/** Máy nhận: DIAL cho điện thoại cùng Wi-Fi tìm thấy tivi, kênh Lounge nhận lệnh, SmartTube phát. */
class MayThu(private val ctx: Context) {
    val kho = Kho(ctx)
    private val tp = TrinhPhat(ctx)
    private val lounge = Lounge(kho, ::tenHien, thietBi(), ::nhan, kho::ghi)
    private val dial = Dial(
        kho.uuid, ::tenHien, Build.MANUFACTURER, Build.MODEL, "Android/${Build.VERSION.RELEASE} UPnP/1.1 TiviKit-Cast/1.0",
        { ma, theme -> theme == Lounge.THEME && lounge.dangKyMa(ma) }, kho::ghi)
    private var khoaWifi: WifiManager.MulticastLock? = null
    /** Điện thoại đang nối (id → tên). */
    private val dienThoai = ConcurrentHashMap<String, String>()
    /** Máy vừa nối gần nhất: người gửi video khi lệnh không ghi tài khoản (DanhBa.nguoiGui). */
    @Volatile private var ganNhat: String? = null
    /** Video máy lạ gửi, giữ lại chờ tivi hỏi xong: mã máy, dữ liệu setPlaylist, app đang ở trước lúc video tới. */
    private class ChoDuyet(val id: String, val du: JSONObject, val tren: String?)
    @Volatile private var choDuyet: ChoDuyet? = null
    /** Hỏi trên tivi có cho máy lạ cast không (màn XinPhep: máy, mã video); null = chưa có màn, cho phát như trước. */
    var hoiDuyet: ((DienThoai, String) -> Unit)? = null
    private val tay = Handler(Looper.getMainLooper())

    val cong: Int get() = dial.cong
    val daNoi: Boolean get() = lounge.daNoi
    val soDienThoai: Int get() = dienThoai.size
    /** Mã các máy đang nối. */
    val dangNoi: Set<String> get() = dienThoai.keys.toSet()
    val dangPhat: String? get() = tp.videoId
    /** Máy DIAL của hãng trả lời trước (Dial.biChe): điện thoại không thấy tivi qua Wi-Fi, phải dùng mã TV. null = chưa dò. */
    @Volatile var biChe: Boolean? = null
        private set
    @Volatile private var chay = false

    /** Mã TV để điện thoại liên kết thủ công (provider ma-tv). */
    fun xinMaTv(): String? = lounge.xinMaTv()

    /** Chẩn đoán dò tìm DIAL (provider thu-tim). */
    fun thuTim() = dial.thuTim()

    /** App vừa lên màn hình (dịch vụ trợ năng báo). */
    fun doiTren(goi: String) = tp.doiTren(goi)

    /** Bài SmartTube đang phát, có tên cả khi SmartTube không gửi (provider dang-phat). */
    fun baiDangPhat() = tp.baiDangPhat()

    /** Tên hiện trong nút Cast của app YouTube. */
    fun tenHien(): String = kho.ten ?: "SmartTube · ${tenTivi()}"

    private fun tenTivi(): String =
        runCatching { Settings.Global.getString(ctx.contentResolver, "device_name") }.getOrNull()?.takeIf { it.isNotBlank() } ?: Build.MODEL

    private fun thietBi(): String = JSONObject()
        .put("brand", Build.MANUFACTURER).put("model", Build.MODEL).put("year", 0).put("os", "Android")
        .put("osVersion", Build.VERSION.RELEASE).put("chipset", "").put("clientName", "TVHTML5")
        .put("dialAdditionalDataSupportLevel", "unsupported").put("mdxDialServerType", "MDX_DIAL_SERVER_TYPE_UNKNOWN")
        .toString()

    fun batDau() {
        chay = true
        khoaWifi = runCatching {
            ctx.applicationContext.getSystemService(WifiManager::class.java)
                .createMulticastLock("tivikit-cast").apply { setReferenceCounted(false); acquire() }
        }.getOrNull()
        tp.baoDoi = { lounge.gui(listOf("onStateChange" to tp.onStateChange())) }
        tp.veHome = ::veLai
        tp.batTheoDoi()
        // Lên bản 2.0 (có danh sách điện thoại): máy đã từng cast theo nhật ký cũ coi như được phép, khỏi hỏi lại cả nhà.
        if (kho.tenMayCu == null) kho.tenMayCu = if (kho.dienThoai.isEmpty()) DanhBa.tenTuNhatKy(kho.nhatKy) else emptySet()
        // Lần trước thấy máy DIAL của hãng chen trước: im lặng ngay từ đầu (lượt dò dưới đây xem lại).
        dial.im = kho.biChe
        if (kho.biChe) biChe = true
        // Mở cổng, nối mạng ngoài luồng chính.
        thread(isDaemon = true) {
            dial.batDau()
            lounge.batDau()
            kho.ghi("bật máy nhận, cổng ${dial.cong}")
            // Dò máy DIAL của hãng một lần khi mạng đã lên (chưa thấy cả lời đáp của mình thì thử lại mỗi phút, tối đa 10 lần).
            for (lan in 1..10) {
                if (!runCatching { Thread.sleep(if (lan == 1) 10_000L else 60_000L) }.isSuccess || !chay) break
                val bc = runCatching { dial.biChe() }.getOrNull() ?: continue
                if (bc != kho.biChe) kho.ghi(if (bc) "tivi có máy Cast của hãng trả lời trước: điện thoại dùng mã TV" else "hết máy Cast của hãng chen trước")
                biChe = bc; kho.biChe = bc; dial.im = bc
                break
            }
        }
    }

    /**
     * Cho SmartTube về nghe nền: mở lại [launcher] (SmartTube rời màn hình như khi bấm Home, cách nghe nền của nó chạy),
     * không biết launcher thì bấm Home. Không bấm Home khi biết launcher: trên Google TV Home bơm (không qua bộ lọc phím của
     * Home sạch) có lúc kẹt ở launcherx thay vì launcher người dùng chọn (28/10/2026, Skyworth).
     */
    private fun veLai(launcher: String?) {
        val man = launcher?.let { g ->
            runCatching {
                ctx.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(g), 0)
                    .firstOrNull()?.activityInfo?.name
            }.getOrNull()?.let { Intent().setClassName(g, it) }
        }
        if (man != null && runCatching {
                ctx.startActivity(man.addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
            }.isSuccess) return
        (ctx as? AccessibilityService)?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
    }

    fun dung() {
        chay = false
        dial.dung()
        lounge.dung()
        runCatching { khoaWifi?.release() }
    }

    // ---------- Điện thoại: duyệt, chặn, huỷ liên kết (28/10/2026) ----------

    /** Làm [lam] với máy [id] (màn trên tivi, TiviKit qua provider). */
    fun lamDienThoai(id: String, lam: DanhBa.Lam) {
        val truoc = kho.dienThoai.firstOrNull { it.id == id } ?: return
        kho.doiDienThoai { DanhBa.lam(it, id, lam, System.currentTimeMillis()) }
        kho.ghi("${truoc.tenHien ?: id}: " + when (lam) {
            DanhBa.Lam.CHO_PHEP -> "cho phép cast"
            DanhBa.Lam.HOM_NAY -> "cho phép tới hết hôm nay"
            DanhBa.Lam.MOT_NGAY -> "cho phép 24 giờ"
            DanhBa.Lam.CHAN -> "chặn"
            DanhBa.Lam.XOA -> "xoá khỏi danh sách"
        })
    }

    /**
     * Huỷ mọi liên kết: tivi đổi screen với YouTube nên mọi điện thoại đã liên kết (mã TV hay Wi-Fi) mất tivi, phải nhập mã
     * mới; danh sách điện thoại xoá hết.
     */
    fun huyLienKet() {
        lounge.doiScreen()
        kho.dienThoai = emptyList()
        // Máy nào liên kết lại cũng phải hỏi: thôi tự cho phép theo tên trong nhật ký cũ (lên bản 2.0, batDau).
        kho.tenMayCu = emptySet()
        dienThoai.clear(); ganNhat = null; choDuyet = null
        kho.ghi("huỷ mọi liên kết")
    }

    /** Tivi trả lời câu hỏi cho máy [id] cast ([lam] null = không trả lời: bỏ qua video này, lần sau hỏi lại). */
    fun traLoiDuyet(id: String, lam: DanhBa.Lam?) {
        val cho = choDuyet?.takeIf { it.id == id }
        if (cho != null) choDuyet = null
        if (lam != null) lamDienThoai(id, lam)
        val duoc = lam == DanhBa.Lam.CHO_PHEP || lam == DanhBa.Lam.HOM_NAY || lam == DanhBa.Lam.MOT_NGAY
        if (cho != null && duoc) thread(isDaemon = true) {
            moVideo(cho.du, kho.dienThoai.firstOrNull { it.id == id }?.tenHien, cho.tren)
            lounge.gui(dayDu())
        }
    }

    /** Lời báo nhỏ trên tivi (Toast). */
    private fun bao(chu: String) = tay.post { runCatching { Toast.makeText(ctx, chu, Toast.LENGTH_LONG).show() } }

    /** Có người vừa cast: báo tên người, tên bài (tra theo mã, chờ chút cho kịp tra). */
    private fun baoCast(ai: String?, video: String) {
        TenVideo.lay(video)
        tay.postDelayed({
            val bai = TenVideo.lay(video)?.first ?: t("video mới", "a new video")
            runCatching { Toast.makeText(ctx, "▶ ${ai ?: t("Điện thoại", "Phone")}: $bai", Toast.LENGTH_LONG).show() }
        }, 2_500)
    }

    private fun nhan(ds: List<TinNhan>) = ds.forEach { t -> Log.i(Dial.NHAN, "Lounge <- [${t.aid}] ${t.ten}"); runCatching { xuLy(t) }.onFailure { kho.ghi("lỗi ${t.ten}: ${it.message}") } }

    private fun gui(t: TinNhan, ds: List<Pair<String, Map<String, String>>>) = lounge.gui(ds, t.aid)

    private fun dayDu() = listOf(
        "onHasPreviousNextChanged" to tp.truocSau(), "nowPlaying" to tp.nowPlaying(), "onStateChange" to tp.onStateChange())

    private fun xuLy(t: TinNhan) {
        val du = t.du as? JSONObject
        // Lệnh điều khiển không ghi máy nào gửi: bỏ qua khi chỉ toàn máy bị chặn / chờ duyệt đang nối.
        val duoc = { DanhBa.duocDieuKhien(kho.dienThoai, dienThoai.keys, System.currentTimeMillis()) }
        when (t.ten) {
            "loungeStatus" -> phong(t)
            "remoteConnected" -> {
                du?.let { ghiNoi(it) }
                kho.ghi("điện thoại nối: ${du?.optString("name").orEmpty()}")
                gui(t, dayDu())
            }
            "remoteDisconnected" -> du?.optString("id")?.let { dienThoai.remove(it) }
            "getNowPlaying" -> gui(t, listOf("nowPlaying" to tp.nowPlaying()))
            "setPlaylist" -> { du?.let { nhanVideo(t, it) } }
            "updatePlaylist" -> if (duoc()) { du?.let(tp::capNhatDanhSach); gui(t, listOf("onHasPreviousNextChanged" to tp.truocSau())) }
            "play" -> if (duoc()) { tp.tiepTuc(); gui(t, listOf("onStateChange" to tp.onStateChange())) }
            "pause" -> if (duoc()) { tp.tamDung(); gui(t, listOf("onStateChange" to tp.onStateChange())) }
            "seekTo" -> if (duoc()) { tp.tua(t.chu("newTime")?.toDoubleOrNull() ?: 0.0); gui(t, listOf("onStateChange" to tp.onStateChange())) }
            "stopVideo" -> if (duoc()) { tp.dung(); gui(t, listOf("onStateChange" to tp.onStateChange(), "nowPlaying" to tp.nowPlaying())) }
            "next" -> if (duoc() && tp.tiep()) gui(t, dayDu())
            "previous" -> if (duoc() && tp.truoc()) gui(t, dayDu())
            "setVolume" -> if (duoc()) {
                val v = t.chu("volume")?.toIntOrNull() ?: return
                tp.datAmLuong(v, t.chu("muted") == "true")
                gui(t, listOf("onVolumeChanged" to tp.onVolumeChanged()))
            }
            "getVolume" -> gui(t, listOf("onVolumeChanged" to tp.onVolumeChanged()))
            "setAutoplayMode" -> gui(t, listOf("onAutoplayModeChanged" to mapOf("autoplayMode" to "UNSUPPORTED")))
        }
    }

    /**
     * Một điện thoại nối vào ([d]: id, name, user… của remoteConnected / loungeStatus): ghi vào danh sách. Máy mới mà đã từng
     * cast trước bản 2.0 (Kho.tenMayCu) thì được phép luôn.
     */
    private fun ghiNoi(d: JSONObject) {
        val id = d.optString("id").takeIf { it.isNotEmpty() } ?: return
        val ten = d.optString("name").ifEmpty { d.optString("clientName") }
        dienThoai[id] = ten
        ganNhat = id
        val bay = System.currentTimeMillis()
        val cu = kho.tenMayCu.orEmpty()
        kho.doiDienThoai { ds ->
            val moi = ds.none { it.id == id }
            val sau = DanhBa.noi(ds, id, ten, d.optString("user"), bay)
            if (moi && ten in cu) { kho.tenMayCu = cu - ten; DanhBa.lam(sau, id, DanhBa.Lam.CHO_PHEP, bay) } else sau
        }
    }

    /**
     * setPlaylist: xét máy gửi rồi mới mở. Tivi tạm ngừng nhận, máy bị chặn / khách quá hạn thì bỏ qua (báo trên tivi);
     * máy lạ thì giữ video lại, hỏi trên tivi (hoiDuyet), cho phép thì mới mở (traLoiDuyet).
     */
    private fun nhanVideo(t: TinNhan, d: JSONObject) {
        if (d.optString("videoId").isEmpty()) return
        val bay = System.currentTimeMillis()
        val taiKhoan = runCatching { JSONObject(d.optString("eventDetails")).optString("user") }.getOrDefault("")
        var ai = DanhBa.nguoiGui(kho.dienThoai, dienThoai.keys, ganNhat, taiKhoan)
        // Không biết máy nào mà có tài khoản: ghi theo tài khoản để duyệt / chặn được.
        if (ai == null && taiKhoan.isNotEmpty()) {
            val id = "tk:$taiKhoan"
            ai = kho.doiDienThoai { DanhBa.noi(it, id, "", taiKhoan, bay) }.firstOrNull { it.id == id }
        }
        val ten = ai?.tenHien ?: t("Một điện thoại", "A phone")
        val xet = if (!kho.nhanCast) DienThoai.Xet.BO_QUA else ai?.xet(bay) ?: DienThoai.Xet.HOI
        val hoi = hoiDuyet
        when {
            !kho.nhanCast -> {
                kho.ghi("tạm ngừng nhận cast: bỏ qua video của $ten")
                bao(t("Tivi đang tạm ngừng nhận cast: bỏ qua video của $ten", "The TV isn't taking casts right now: ignored $ten"))
            }
            xet == DienThoai.Xet.BO_QUA -> {
                kho.ghi("bỏ qua video của $ten (${if (ai?.quaHan(bay) == true) "hết hạn khách" else "đã chặn"})")
                bao(if (ai?.quaHan(bay) == true) t("$ten đã hết hạn cast lên tivi này", "$ten can no longer cast to this TV (expired)")
                    else t("$ten đã bị chặn cast lên tivi này", "$ten is blocked from casting to this TV"))
            }
            xet == DienThoai.Xet.PHAT || hoi == null -> moVideo(d, ten)
            else -> {
                val may = ai ?: DienThoai("", ten)
                choDuyet = ChoDuyet(may.id, d, tp.appTren())
                kho.ghi("hỏi trên tivi: $ten muốn cast")
                hoi(may, d.optString("videoId"))
            }
        }
        gui(t, dayDu())
    }

    /** Mở video của setPlaylist [d] (người gửi [ai] để báo trên tivi; [trenTruoc]: app ở trước lúc video tới, xem TrinhPhat.phat). */
    private fun moVideo(d: JSONObject, ai: String?, trenTruoc: String? = null) {
        val id = d.optString("videoId").takeIf { it.isNotEmpty() } ?: return
        if (!tp.datDanhSach(d)) return
        val ok = tp.phat(id, d.optString("currentTime").toDoubleOrNull() ?: 0.0, trenTruoc)
        kho.ghi(if (ok) "mở video $id" + (ai?.let { " ($it)" } ?: "") else "không mở được video $id")
        if (ok) baoCast(ai, id)
    }

    /** loungeStatus: danh sách thiết bị trong phòng; điện thoại mới vào thì ghi lại và báo trạng thái đầy đủ. */
    private fun phong(t: TinNhan) {
        val ds = runCatching { JSONArray(t.chu("devices") ?: "[]") }.getOrDefault(JSONArray())
        val may = (0 until ds.length()).mapNotNull { ds.optJSONObject(it) }
            .filter { it.optString("type") == "REMOTE_CONTROL" && it.optString("id").isNotEmpty() }
        val co = may.map { it.optString("id") }.toSet()
        val moi = co - dienThoai.keys
        dienThoai.keys.retainAll(co)
        may.filter { it.optString("id") in moi }.forEach { ghiNoi(it) }
        // Máy vừa nối / rời đi theo connectionEventDetails: máy vừa nối là người gửi video sắp tới.
        runCatching { JSONObject(t.chu("connectionEventDetails") ?: "{}").optString("deviceId") }.getOrNull()
            ?.takeIf { it in co }?.let { ganNhat = it }
        moi.forEach { kho.ghi("điện thoại nối: ${dienThoai[it].orEmpty()}") }
        gui(t, if (moi.isNotEmpty()) dayDu() else listOf(
            "onHasPreviousNextChanged" to tp.truocSau(), "onAutoplayModeChanged" to mapOf("autoplayMode" to "UNSUPPORTED")))
    }
}
