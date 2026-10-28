package vn.tivikit.cast

import org.json.JSONObject
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.Executors
import android.util.Log
import kotlin.concurrent.thread

/**
 * Phía "màn hình" của kênh Lounge, giao thức YouTube dùng cho "Xem trên TV" (không công khai; tham số theo
 * yt-cast-receiver của Patrick Kan, MIT): xin screen, lounge token, mở phiên (bind), nghe lệnh điện thoại gửi qua
 * long-poll, gửi trạng thái phát về. Chỉ nhận YouTube (theme "cl"), chưa nhận YouTube Music.
 */
class Lounge(
    private val kho: Kho,
    private val ten: () -> String,
    /** deviceInfo JSON gửi kèm khi mở phiên và khi gửi tin. */
    private val thietBi: String,
    private val nhan: (List<TinNhan>) -> Unit,
    private val ghi: (String) -> Unit,
) {
    companion object {
        private const val GOC = "https://www.youtube.com/api/lounge"
        private const val URL_SCREEN = "$GOC/pairing/generate_screen_id"
        private const val URL_TOKEN = "$GOC/pairing/get_lounge_token_batch"
        private const val URL_GHEP = "$GOC/pairing/register_pairing_code"
        private const val URL_BIND = "$GOC/bc/bind"
        private const val URL_MA_TV = "$GOC/pairing/get_pairing_code?ctx=pair"
        const val APP = "tivikit"
        const val THEME = "cl"
        private const val UA = "Mozilla/5.0 (Linux; Android) TiviKit-Cast/1.0"
        /** Long-poll im quá lâu (YouTube gửi noop chừng 30 giây một lần) thì coi như đứt, nối lại. */
        private const val NGHE_TOI_DA_MS = 120_000
        private const val TOKEN_MAC_DINH_MS = 12 * 3_600_000L
    }

    /** Lỗi phiên (SID hết hạn, token hỏng): mở phiên mới thay vì thử lại. */
    private class PhienHong(msg: String, val tokenHong: Boolean = false) : Exception(msg)

    /** YouTube trả lời rõ là không còn screen này (không phải lỗi mạng): phải xin screen mới. */
    private class ScreenMat(msg: String) : Exception(msg)

    @Volatile private var chay = false
    @Volatile private var token: String? = null
    @Volatile private var tokenHet = 0L
    @Volatile private var sid: String? = null
    @Volatile private var gsid: String? = null
    /** Số tin đến cuối cùng đã nhận (AID của BrowserChannel). */
    @Volatile private var aid = 0
    private var rid = 0
    private var ofs = 0
    private val guiDi = Executors.newSingleThreadExecutor()
    @Volatile private var dangNghe: HttpURLConnection? = null
    private var luong: Thread? = null

    /** Đã mở phiên với YouTube bằng screen đang giữ (vừa huỷ mọi liên kết thì đợi phiên bằng screen mới). */
    val daNoi: Boolean get() = sid != null && screenPhien.let { it != null && it == kho.screenId }

    fun batDau() {
        chay = true
        luong = thread(name = "lounge", isDaemon = true) { vongLap() }
    }

    fun dung() {
        val coPhien = sid != null
        chay = false
        luong?.interrupt()
        // Báo điện thoại tivi đã rời đi (không chờ, không chạy trên luồng chính).
        thread(isDaemon = true) {
            if (coPhien) runCatching { guiNgay(listOf("loungeScreenDisconnected" to emptyMap()), null) }
            runCatching { dangNghe?.disconnect() }
            guiDi.shutdownNow()
        }
    }

    /** Gửi [ds] lên kênh (theo thứ tự, luồng riêng); [aidDen] = số của tin đang trả lời. */
    fun gui(ds: List<Pair<String, Map<String, String>>>, aidDen: Int? = null) {
        if (ds.isEmpty() || !chay) return
        runCatching { guiDi.execute { runCatching { guiNgay(ds, aidDen) }.onFailure { ghi("gửi ${ds.first().first} lỗi: ${it.message}") } } }
    }

    /**
     * Điện thoại chọn tivi qua DIAL và gửi [ma]: đăng ký mã với screen của tivi, YouTube nối điện thoại vào kênh.
     * Gọi từ luồng HTTP của DIAL.
     */
    fun dangKyMa(ma: String): Boolean {
        val id = kho.screenId ?: return false.also { ghi("ghép qua Wi-Fi: chưa có screen (tivi chưa nối được YouTube)") }
        val (code, _) = goi(URL_GHEP, thamSo(mapOf(
            "access_type" to "permanent", "app" to APP, "pairing_code" to ma, "screen_id" to id,
            "screen_name" to ten(), "device_id" to kho.maThietBi)))
        ghi(if (code == 200) "điện thoại chọn tivi qua Wi-Fi" else "ghép qua Wi-Fi lỗi $code")
        return code == 200
    }

    /**
     * Mã TV (12 số) cho điện thoại liên kết thủ công: YouTube → Cast → Thêm thiết bị → Liên kết bằng mã TV. Dùng khi tivi
     * có máy DIAL của hãng cùng IP (app YouTube chỉ lấy máy đó, không thấy máy nhận này). Mã sống chừng 5 phút; liên kết
     * xong thì điện thoại nhớ luôn (screen giữ nguyên), máy nhận chạy suốt nên không cần mở SmartTube trước.
     */
    fun xinMaTv(): String? {
        // Chỉ xin cho screen của phiên đang mở, khi screen đó chưa bị huỷ. 28/10/2026 trên Skyworth: màn Cast xin mã
        // ngay sau khi bấm Huỷ mọi liên kết, lúc luồng phụ chưa kịp bỏ screen cũ, nên ra mã của screen cũ; iPhone nhập
        // mã đó là liên kết với tivi đã chết (cast không tới, tên mới không hiện).
        if (!daNoi) return null
        val id = screenPhien ?: return null
        val tk = token ?: return null
        val (ma, than) = goi(URL_MA_TV, thamSo(mapOf(
            "access_type" to "permanent", "app" to APP, "lounge_token" to tk, "screen_id" to id,
            "screen_name" to ten(), "device_id" to kho.maThietBi)))
        val so = than.trim()
        // Bấm huỷ đúng lúc đang chờ YouTube trả mã: mã này thuộc screen vừa bỏ.
        if (id != kho.screenId) return null
        return so.takeIf { ma == 200 && it.length in 6..16 && it.all(Char::isDigit) }?.also { ghi("xin mã TV để liên kết điện thoại") }
    }

    /**
     * Huỷ mọi liên kết (28/10/2026): báo các điện thoại đang nối là tivi rời đi, bỏ screen cũ (máy đã liên kết bằng mã TV
     * hay qua Wi-Fi đều giữ screen đó nên mất tivi), vòng chạy xin screen mới rồi mở phiên lại. Điện thoại muốn dùng lại
     * thì nhập mã TV mới (tivi tìm được qua Wi-Fi thì tự ghép lại như lần đầu).
     */
    fun doiScreen() {
        // Bỏ screen ngay trên luồng gọi, không đợi luồng phụ: từ lúc này mã TV và "đã nối" chỉ tính cho phiên mở bằng
        // screen mới.
        kho.screenId = null
        lanDoiScreen++
        val sidCu = sid
        thread(isDaemon = true) {
            if (sidCu != null) runCatching { guiNgay(listOf("loungeScreenDisconnected" to emptyMap()), null) }
            // Đóng phiên cũ cho vòng chạy mở phiên bằng screen mới (vòng chạy đã tự mở phiên khác thì thôi).
            if (sid == sidCu) { token = null; tokenHet = 0; sid = null; runCatching { dangNghe?.disconnect() } }
            ghi("huỷ mọi liên kết: xin screen mới")
        }
    }

    /** Tăng mỗi lần đổi screen: phiên đang mở dở bằng screen cũ thì bỏ (moPhien). */
    @Volatile private var lanDoiScreen = 0
    /** Screen của phiên mở gần nhất (moPhien). */
    @Volatile private var screenPhien: String? = null

    // ---------- Vòng chạy ----------

    private fun vongLap() {
        var loi = 0
        while (chay) {
            try {
                if (sid == null || token == null || System.currentTimeMillis() > tokenHet) moPhien()
                nghe()
                loi = 0
            } catch (e: PhienHong) {
                if (!chay) break
                // Hỏng liền nhiều lần (YouTube chặn tạm, đổi giao thức): giãn dần tới 60 giây, chỉ ghi nhật ký lần đầu.
                loi++
                if (loi == 1) ghi("kênh YouTube: ${e.message}, mở lại")
                sid = null
                if (e.tokenHong) token = null
                if (!ngu(if (loi == 1) 2_000L else minOf(60_000L, 3_000L * loi))) break
            } catch (e: InterruptedException) {
                break
            } catch (e: Exception) {
                if (!chay) break
                loi++
                if (loi == 1) ghi("kênh YouTube đứt: ${e.javaClass.simpleName} ${e.message.orEmpty()}")
                if (loi >= 3) { sid = null; token = null }
                if (!ngu(minOf(60_000L, 3_000L * loi))) break
            }
        }
    }

    private fun ngu(ms: Long) = runCatching { Thread.sleep(ms) }.isSuccess

    /** Screen (giữ lâu dài) → lounge token → mở phiên: lấy SID / gsessionid và tình trạng phòng. */
    private fun moPhien() {
        sid = null; gsid = null
        val lan = lanDoiScreen
        var id = kho.screenId ?: xinScreen().also { kho.screenId = it }
        val tk = try { layToken(id) } catch (e: ScreenMat) {
            // YouTube trả lời rõ là không còn screen cũ: xin screen mới, máy đã liên kết bằng mã TV phải nhập mã mới. Lỗi
            // mạng (hết giờ chờ, mất DNS, YouTube lỗi tạm) thì vòng chạy thử lại đúng screen này (28/10/2026: trước đây một
            // lần hết giờ chờ cũng đổi screen, mọi máy liên kết bằng mã TV mất tivi mà không ai biết).
            ghi("YouTube không còn screen cũ (${e.message}): xin screen mới, điện thoại phải liên kết lại bằng mã TV")
            id = xinScreen(); kho.screenId = id
            layToken(id)
        }
        token = tk.first
        tokenHet = System.currentTimeMillis() + tk.second
        val (ma, than) = synchronized(this) {
            rid = (41000..49999).random(); aid = 0; ofs = 0
            goi("$URL_BIND?" + thamSo(chung() + mapOf("deviceInfo" to thietBi, "RID" to "${rid++}", "CVER" to "1")), "count=0")
        }
        if (ma != 200) throw PhienHong("mở phiên HTTP $ma", tokenHong = ma == 401 || ma == 403)
        val ds = TinNhan.tach(than)
        capNhat(ds)
        if (sid == null || gsid == null) throw PhienHong("thiếu SID", tokenHong = true)
        if (lan != lanDoiScreen) throw PhienHong("vừa huỷ mọi liên kết", tokenHong = true)
        screenPhien = id
        ghi("đã nối kênh YouTube")
        ds.filter { it.ten !in setOf("c", "S", "noop") }.takeIf { it.isNotEmpty() }?.let(nhan)
    }

    private fun xinScreen(): String {
        val (ma, than) = goi(URL_SCREEN, null)
        val id = than.trim()
        if (ma != 200 || id.isEmpty() || id.length > 200) throw IllegalStateException("xin screen HTTP $ma")
        return id
    }

    /**
     * (loungeToken, sau bao lâu thì xin lại) của screen [id]. Không có token: ScreenMat khi YouTube trả lời rõ (200 có danh
     * sách screens, hay 400 / 404 / 410), còn lại (lỗi mạng, 5xx, 429, trang lạ) là IOException để thử lại sau.
     */
    private fun layToken(id: String): Pair<String, Long> {
        val (ma, than) = goi(URL_TOKEN, thamSo(mapOf("screen_ids" to id)))
        val ds = runCatching { JSONObject(than).getJSONArray("screens") }.getOrNull()
        val s = ds?.optJSONObject(0)
        val tk = s?.optString("loungeToken")?.takeIf { it.isNotEmpty() }
        if (s == null || tk == null) {
            val ro = (ma == 200 && ds != null) || ma == 400 || ma == 404 || ma == 410
            throw if (ro) ScreenMat("lounge token HTTP $ma") else java.io.IOException("lounge token HTTP $ma")
        }
        return tk to (s.optLong("refreshIntervalInMillis").takeIf { it > 60_000 } ?: TOKEN_MAC_DINH_MS)
    }

    /** Long-poll: giữ kết nối, tách tin đến dần; YouTube đóng thì về vòng lặp mở lại ngay. */
    private fun nghe() {
        val s = sid ?: return
        val g = gsid ?: return
        val c = moKetNoi("$URL_BIND?" + thamSo(chung() + mapOf(
            "RID" to "rpc", "SID" to s, "CI" to "0", "AID" to "$aid", "gsessionid" to g, "TYPE" to "xmlhttp")), null, NGHE_TOI_DA_MS)
        dangNghe = c
        try {
            val ma = c.responseCode
            if (ma != 200) throw PhienHong("nghe HTTP $ma", tokenHong = ma == 401 || ma == 403)
            val r = InputStreamReader(c.inputStream, Charsets.UTF_8)
            val bo = KhucJson()
            val buf = CharArray(4096)
            while (chay) {
                val n = r.read(buf)
                if (n < 0) break
                for (khuc in bo.an(String(buf, 0, n))) {
                    val ds = TinNhan.tuKhuc(khuc)
                    capNhat(ds)
                    ds.filter { it.ten !in setOf("c", "S", "noop") }.takeIf { it.isNotEmpty() }?.let(nhan)
                }
            }
        } finally {
            dangNghe = null
            runCatching { c.disconnect() }
        }
    }

    private fun capNhat(ds: List<TinNhan>) {
        for (t in ds) {
            when (t.ten) {
                "c" -> (t.du as? org.json.JSONArray)?.optString(0)?.takeIf { it.isNotEmpty() }?.let { sid = it }
                "S" -> (t.du as? String)?.takeIf { it.isNotEmpty() }?.let { gsid = it }
            }
            if (t.aid > aid) aid = t.aid
        }
    }

    @Synchronized private fun guiNgay(ds: List<Pair<String, Map<String, String>>>, aidDen: Int?) {
        val s = sid ?: return
        val g = gsid ?: return
        val q = chung() + mapOf("deviceInfo" to thietBi, "SID" to s, "RID" to "${rid++}", "AID" to "${maxOf(aid, aidDen ?: 0)}", "gsessionid" to g)
        val than = TinNhan.thanGui(ds, ofs)
        ofs += ds.size
        val (ma, _) = goi("$URL_BIND?" + thamSo(q), than)
        Log.d(Dial.NHAN, "Lounge -> ${ds.joinToString { it.first }}: HTTP $ma")
        if (ma == 400 || ma == 404 || ma == 410) {
            // SID hết hạn: bỏ phiên, vòng nghe tự mở phiên mới.
            sid = null
            runCatching { dangNghe?.disconnect() }
        }
    }

    // ---------- HTTP ----------

    private fun chung(): Map<String, String> = linkedMapOf(
        "device" to "LOUNGE_SCREEN", "id" to kho.maThietBi, "obfuscatedGaiaId" to "", "name" to ten(), "app" to APP,
        "theme" to THEME, "capabilities" to "dsp,mic,dpa,ntb", "cst" to "m", "mdxVersion" to "2",
        "loungeIdToken" to (token ?: ""), "VER" to "8", "v" to "2",
        "zx" to UUID.randomUUID().toString().replace("-", "").take(12), "t" to "1")

    private fun thamSo(m: Map<String, String>) = m.entries.joinToString("&") { (k, v) -> "${TinNhan.maHoa(k)}=${TinNhan.maHoa(v)}" }

    private fun moKetNoi(url: String, than: String?, docToi: Int = 20_000): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = docToi
        c.setRequestProperty("User-Agent", UA)
        c.setRequestProperty("Origin", "https://www.youtube.com")
        if (than != null) {
            c.requestMethod = "POST"
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            c.outputStream.use { it.write(than.toByteArray(Charsets.UTF_8)) }
        }
        return c
    }

    /** (mã HTTP, thân) của một yêu cầu ngắn. */
    private fun goi(url: String, than: String?): Pair<Int, String> {
        val c = moKetNoi(url, than)
        return try {
            val ma = c.responseCode
            val s = runCatching { (if (ma in 200..299) c.inputStream else c.errorStream)?.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()
            ma to (s ?: "")
        } finally {
            c.disconnect()
        }
    }
}
