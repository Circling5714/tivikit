package vn.tivikit.lg

import org.json.JSONArray
import org.json.JSONObject

/**
 * Tivi LG webOS (25/10/2026, anh Alex: "TiviKit mới chạy được cho Android TV"). Điều khiển qua giao thức SSAP mà app LG
 * ThinQ dùng: WebSocket cổng 3000 (ws) hoặc 3001 (wss, firmware mới bắt buộc), ghép đôi bằng hộp "Cho phép" trên
 * tivi — không cần root, không cần chế độ nhà phát triển. Dạng tin nhắn theo aiowebostv của Home Assistant
 * (Apache 2.0) và LGWebOSRemote. Ở đây chỉ dựng / đọc tin nhắn, không đụng mạng, để test được trên máy tính.
 */
object WebOs {
    const val CONG_WS = 3000
    const val CONG_WSS = 3001
    const val ID_DANG_KY = "register_0"
    const val ID_CHAO = "hello"
    const val APP_YOUTUBE = "youtube.leanback.v4"

    /** Quyền xin lúc ghép đôi (như aiowebostv): điều khiển, gõ chữ, mở app, nguồn vào, nguồn điện, thông báo. */
    private val QUYEN = listOf(
        "APP_TO_APP", "CLOSE", "CONTROL_AUDIO", "CONTROL_DISPLAY", "CONTROL_INPUT_JOYSTICK", "CONTROL_INPUT_MEDIA_PLAYBACK",
        "CONTROL_INPUT_MEDIA_RECORDING", "CONTROL_INPUT_TEXT", "CONTROL_INPUT_TV", "CONTROL_MOUSE_AND_KEYBOARD", "CONTROL_POWER",
        "CONTROL_TV_SCREEN", "LAUNCH", "LAUNCH_WEBAPP", "READ_APP_STATUS", "READ_COUNTRY_INFO", "READ_CURRENT_CHANNEL",
        "READ_INPUT_DEVICE_LIST", "READ_INSTALLED_APPS", "READ_LGE_SDX", "READ_LGE_TV_INPUT_EVENTS", "READ_NETWORK_STATE",
        "READ_NOTIFICATIONS", "READ_POWER_STATE", "READ_RUNNING_APPS", "READ_SETTINGS", "READ_TV_CHANNEL_LIST",
        "READ_TV_CURRENT_TIME", "READ_UPDATE_INFO", "SEARCH", "TEST_OPEN", "TEST_PROTECTED", "TEST_SECURE",
        "UPDATE_FROM_REMOTE_APP", "WRITE_NOTIFICATION_ALERT", "WRITE_NOTIFICATION_TOAST", "WRITE_SETTINGS",
    )

    fun chao(): String = JSONObject().put("id", ID_CHAO).put("type", "hello").put("payload", JSONObject()).toString()

    /** Tin ghép đôi. [khoa] = client-key tivi cấp lần trước: còn đúng thì tivi nhận luôn, không hỏi lại. */
    fun dangKy(khoa: String?): String {
        val p = JSONObject().put("forcePairing", false).put("pairingType", "PROMPT")
            .put("manifest", JSONObject().put("appVersion", "1.1").put("manifestVersion", 1).put("permissions", JSONArray(QUYEN)))
        if (!khoa.isNullOrBlank()) p.put("client-key", khoa)
        return JSONObject().put("id", ID_DANG_KY).put("type", "register").put("payload", p).toString()
    }

    fun yeuCau(id: String, uri: String, payload: JSONObject = JSONObject()): String =
        JSONObject().put("id", id).put("type", "request").put("uri", "ssap://$uri").put("payload", payload).toString()

    /** Một phím remote, gửi trên kênh phím (getPointerInputSocket). Tên phím như remote LG: HOME, BACK, ENTER, UP… */
    fun nut(ten: String): String = "type:button\nname:$ten\n\n"

    /** Một tin từ tivi: [loai] response / registered / error / hello; [loi] chữ lỗi nếu có. */
    data class Tin(val id: String, val loai: String, val payload: JSONObject?, val loi: String)

    fun doc(s: String): Tin? = runCatching {
        val o = JSONObject(s)
        Tin(o.optString("id"), o.optString("type"), o.optJSONObject("payload"), o.optString("error"))
    }.getOrNull()

    /** Trả lời thành công: không phải lỗi và returnValue không false. */
    fun thanhCong(t: Tin): Boolean = t.loai != "error" && t.payload?.optBoolean("returnValue", true) != false

    /** Chữ lỗi để báo: "error" của tin, không có thì errorText trong payload. */
    fun chuLoi(t: Tin): String = t.loi.ifBlank { t.payload?.optString("errorText").orEmpty() }

    data class App(val id: String, val ten: String, val icon: String)

    /** Danh sách app trên màn Home của tivi (listLaunchPoints), theo tên. */
    fun dsApp(p: JSONObject): List<App> {
        val ds = p.optJSONArray("launchPoints") ?: return emptyList()
        return (0 until ds.length()).mapNotNull { i ->
            val o = ds.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").ifBlank { return@mapNotNull null }
            App(id, o.optString("title").ifBlank { id }, o.optString("icon").ifBlank { o.optString("largeIcon") })
        }.distinctBy { it.id }.sortedBy { it.ten.lowercase() }
    }

    data class Nguon(val id: String, val ten: String, val coThietBi: Boolean)

    /** Nguồn vào HDMI / AV (getExternalInputList). */
    fun dsNguon(p: JSONObject): List<Nguon> {
        val ds = p.optJSONArray("devices") ?: return emptyList()
        return (0 until ds.length()).mapNotNull { i ->
            val o = ds.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").ifBlank { return@mapNotNull null }
            Nguon(id, o.optString("label").ifBlank { id }, o.optBoolean("connected"))
        }
    }

    /** Địa chỉ MAC để bật tivi qua mạng (connectionmanager/getinfo): mạng đang dùng trước, dây LAN rồi Wi-Fi. */
    fun mac(p: JSONObject): String? {
        val cacMang = listOfNotNull(p.optJSONObject("wiredInfo"), p.optJSONObject("wifiInfo"))
        val dangDung = cacMang.firstOrNull { it.optString("state").equals("connected", ignoreCase = true) }
        return (listOfNotNull(dangDung) + cacMang).map { it.optString("macAddress") }.firstOrNull { MAC.matches(it) }?.lowercase()
    }

    private val MAC = Regex("^[0-9A-Fa-f]{2}([:-][0-9A-Fa-f]{2}){5}$")

    /** Gói Wake-on-LAN: 6 byte 0xFF rồi 16 lần địa chỉ MAC. */
    fun goiWol(mac: String): ByteArray? {
        if (!MAC.matches(mac)) return null
        val m = mac.split(':', '-').map { it.toInt(16).toByte() }
        return ByteArray(6) { 0xFF.toByte() } + (1..16).flatMap { m }.toByteArray()
    }

    /** Mã video YouTube (11 ký tự) trong một đường link, hay chính mã đó. */
    fun maYoutube(link: String): String? {
        val s = link.trim()
        if (Regex("^[A-Za-z0-9_-]{11}$").matches(s)) return s
        val m = Regex("""(?:youtu\.be/|[?&]v=|/shorts/|/live/|/embed/)([A-Za-z0-9_-]{11})""").find(s) ?: return null
        return m.groupValues[1]
    }

    /** Mở video YouTube trên app YouTube của tivi (như LGWebOSRemote: contentTarget là link youtube.com/tv). */
    fun moYoutube(ma: String): JSONObject =
        JSONObject().put("id", APP_YOUTUBE).put("params", JSONObject().put("contentTarget", "https://www.youtube.com/tv?v=$ma"))
}
