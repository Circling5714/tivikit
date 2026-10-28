package vn.tivikit.cast

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.util.Log
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

    val cong: Int get() = dial.cong
    val daNoi: Boolean get() = lounge.daNoi
    val soDienThoai: Int get() = dienThoai.size
    val dangPhat: String? get() = tp.videoId

    /** App vừa lên màn hình (dịch vụ trợ năng báo). */
    fun doiTren(goi: String) = tp.doiTren(goi)

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
        khoaWifi = runCatching {
            ctx.applicationContext.getSystemService(WifiManager::class.java)
                .createMulticastLock("tivikit-cast").apply { setReferenceCounted(false); acquire() }
        }.getOrNull()
        tp.baoDoi = { lounge.gui(listOf("onStateChange" to tp.onStateChange())) }
        tp.veHome = ::veLai
        tp.batTheoDoi()
        // Mở cổng, nối mạng ngoài luồng chính.
        thread(isDaemon = true) {
            dial.batDau()
            lounge.batDau()
            kho.ghi("bật máy nhận, cổng ${dial.cong}")
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
        dial.dung()
        lounge.dung()
        runCatching { khoaWifi?.release() }
    }

    private fun nhan(ds: List<TinNhan>) = ds.forEach { t -> Log.i(Dial.NHAN, "Lounge <- [${t.aid}] ${t.ten}"); runCatching { xuLy(t) }.onFailure { kho.ghi("lỗi ${t.ten}: ${it.message}") } }

    private fun gui(t: TinNhan, ds: List<Pair<String, Map<String, String>>>) = lounge.gui(ds, t.aid)

    private fun dayDu() = listOf(
        "onHasPreviousNextChanged" to tp.truocSau(), "nowPlaying" to tp.nowPlaying(), "onStateChange" to tp.onStateChange())

    private fun xuLy(t: TinNhan) {
        val du = t.du as? JSONObject
        when (t.ten) {
            "loungeStatus" -> phong(t)
            "remoteConnected" -> {
                du?.let { dienThoai[it.optString("id")] = it.optString("name") }
                kho.ghi("điện thoại nối: ${du?.optString("name").orEmpty()}")
                gui(t, dayDu())
            }
            "remoteDisconnected" -> du?.optString("id")?.let { dienThoai.remove(it) }
            "getNowPlaying" -> gui(t, listOf("nowPlaying" to tp.nowPlaying()))
            "setPlaylist" -> {
                val d = du ?: return
                val id = d.optString("videoId").takeIf { it.isNotEmpty() } ?: return
                if (tp.datDanhSach(d)) {
                    val ok = tp.phat(id, d.optString("currentTime").toDoubleOrNull() ?: 0.0)
                    kho.ghi(if (ok) "mở video $id" else "không mở được video $id")
                }
                gui(t, dayDu())
            }
            "updatePlaylist" -> { du?.let(tp::capNhatDanhSach); gui(t, listOf("onHasPreviousNextChanged" to tp.truocSau())) }
            "play" -> { tp.tiepTuc(); gui(t, listOf("onStateChange" to tp.onStateChange())) }
            "pause" -> { tp.tamDung(); gui(t, listOf("onStateChange" to tp.onStateChange())) }
            "seekTo" -> { tp.tua(t.chu("newTime")?.toDoubleOrNull() ?: 0.0); gui(t, listOf("onStateChange" to tp.onStateChange())) }
            "stopVideo" -> { tp.dung(); gui(t, listOf("onStateChange" to tp.onStateChange(), "nowPlaying" to tp.nowPlaying())) }
            "next" -> if (tp.tiep()) gui(t, dayDu())
            "previous" -> if (tp.truoc()) gui(t, dayDu())
            "setVolume" -> {
                val v = t.chu("volume")?.toIntOrNull() ?: return
                tp.datAmLuong(v, t.chu("muted") == "true")
                gui(t, listOf("onVolumeChanged" to tp.onVolumeChanged()))
            }
            "getVolume" -> gui(t, listOf("onVolumeChanged" to tp.onVolumeChanged()))
            "setAutoplayMode" -> gui(t, listOf("onAutoplayModeChanged" to mapOf("autoplayMode" to "UNSUPPORTED")))
        }
    }

    /** loungeStatus: danh sách thiết bị trong phòng; điện thoại mới vào thì báo trạng thái đầy đủ. */
    private fun phong(t: TinNhan) {
        val ds = runCatching { JSONArray(t.chu("devices") ?: "[]") }.getOrDefault(JSONArray())
        val co = (0 until ds.length()).mapNotNull { ds.optJSONObject(it) }
            .filter { it.optString("type") == "REMOTE_CONTROL" }
            .associate { it.optString("id") to it.optString("name") }
        val moi = co.keys - dienThoai.keys
        dienThoai.keys.retainAll(co.keys)
        dienThoai.putAll(co)
        moi.forEach { kho.ghi("điện thoại nối: ${co[it].orEmpty()}") }
        gui(t, if (moi.isNotEmpty()) dayDu() else listOf(
            "onHasPreviousNextChanged" to tp.truocSau(), "onAutoplayModeChanged" to mapOf("autoplayMode" to "UNSUPPORTED")))
    }
}
