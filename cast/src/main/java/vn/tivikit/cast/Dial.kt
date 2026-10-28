package vn.tivikit.cast

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import android.util.Log
import kotlin.concurrent.thread

/**
 * Máy chủ DIAL (đặc tả DIAL 1.7, dial-multiscreen.org) cho app YouTube trên điện thoại cùng Wi-Fi: trả lời SSDP M-SEARCH
 * để tivi hiện trong nút Cast, mô tả thiết bị, mô tả app "YouTube" (luôn đang chạy). Chọn tivi thì điện thoại POST mã
 * ghép đôi (pairingCode) vào /apps/YouTube; [ghep] đăng ký mã đó với YouTube, điện thoại tự nối vào kênh Lounge của tivi.
 */
class Dial(
    private val uuid: String,
    private val ten: () -> String,
    private val nhaSanXuat: String,
    private val mau: String,
    private val mayChu: String,
    private val ghep: (maGhep: String, theme: String) -> Boolean,
    private val ghi: (String) -> Unit,
) {
    companion object {
        /** Cổng HTTP của DIAL; bận thì lấy cổng bất kỳ (LOCATION báo đúng cổng). */
        const val CONG = 8095
        const val NHOM = "239.255.255.250"
        const val DICH_VU = "urn:dial-multiscreen-org:service:dial:1"
        const val THIET_BI = "urn:dial-multiscreen-org:device:dial:1"
        const val APP = "YouTube"
        const val NHAN = "TiviKitCast"
        private const val NHAC_LAI_MS = 5 * 60_000L

        val MO_TA_APP = """<?xml version="1.0" encoding="UTF-8"?>
<service xmlns="urn:dial-multiscreen-org:schemas:dial" dialVer="1.7">
  <name>$APP</name>
  <options allowStop="false"/>
  <state>running</state>
  <link rel="run" href="run"/>
</service>
"""

        fun xml(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

        /** "a=1&b=2" (thân POST của điện thoại) → bảng. */
        fun thamSo(than: String): Map<String, String> = than.trim().split('&').filter { it.contains('=') }.associate {
            URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8")
        }

        private val DINH_DANG_NGAY = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).apply { timeZone = TimeZone.getTimeZone("GMT") }

        private fun ngay(): String = synchronized(DINH_DANG_NGAY) { DINH_DANG_NGAY.format(Date()) }

        /**
         * Tivi có máy DIAL khác cùng IP (của hãng: MediaTek quick_ssdp, Chromecast built-in) trả lời trước: app YouTube chỉ
         * giữ một máy mỗi IP nên máy nhận này hiện mờ / không hiện, phải liên kết bằng mã TV (28/10/2026, Skyworth). [ds] =
         * các lời đáp của một lượt thuTim, [cong] = cổng HTTP của mình. null = chưa dò được (không thấy cả lời đáp của chính
         * mình: mạng chưa lên).
         */
        fun biChe(ds: List<org.json.JSONObject>, cong: Int): Boolean? {
            fun cuaMinh(o: org.json.JSONObject) = o.optString("location").contains(":$cong/")
            val ip = ds.filter(::cuaMinh).map { it.optString("tu").substringBeforeLast(':') }.toSet()
            if (ip.isEmpty()) return null
            return ds.any { !cuaMinh(it) && it.optString("tu").substringBeforeLast(':') in ip }
        }
    }

    @Volatile private var chay = false
    /**
     * Im lặng trên SSDP (biChe: máy DIAL của hãng cùng IP trả lời trước, app YouTube chỉ hiện tivi mờ, không chọn được —
     * 28/10/2026, Skyworth): không trả lời điện thoại, không báo còn sống, báo byebye một lần cho điện thoại bỏ mục mờ.
     * Vẫn trả lời M-SEARCH của chính tivi để biChe dò lại được. Điện thoại dùng mã TV.
     */
    @Volatile var im = false
        set(v) {
            if (v && !field) runCatching { thongBao(false) }
            field = v
        }
    private var http: ServerSocket? = null
    private var ssdp: MulticastSocket? = null
    private val bootId = (System.currentTimeMillis() / 1000 % 100_000).toInt()
    var cong = 0; private set

    fun batDau() {
        chay = true
        val s = runCatching { ServerSocket(CONG) }.getOrElse { ServerSocket(0) }
        http = s; cong = s.localPort
        thread(name = "dial-http", isDaemon = true) {
            while (chay) {
                val k = runCatching { s.accept() }.getOrNull() ?: break
                thread(isDaemon = true) { runCatching { k.use { phucVu(it) } } }
            }
        }
        thread(name = "dial-ssdp", isDaemon = true) { runCatching { ngheSsdp() }.onFailure { ghi("SSDP không chạy được: ${it.message}") } }
    }

    fun dung() {
        chay = false
        runCatching { thongBao(false) }
        runCatching { http?.close() }
        runCatching { ssdp?.close() }
    }

    // ---------- HTTP ----------

    private fun phucVu(k: Socket) {
        k.soTimeout = 10_000
        val vao = BufferedInputStream(k.getInputStream())
        val dau = docDong(vao) ?: return
        val phan = dau.split(' ')
        if (phan.size < 2) return
        val tieuDe = HashMap<String, String>()
        while (true) {
            val d = docDong(vao) ?: break
            if (d.isEmpty()) break
            val i = d.indexOf(':')
            if (i > 0) tieuDe[d.substring(0, i).trim().lowercase()] = d.substring(i + 1).trim()
        }
        val dai = tieuDe["content-length"]?.toIntOrNull()?.coerceIn(0, 16_384) ?: 0
        val than = ByteArray(dai)
        var n = 0
        while (n < dai) { val r = vao.read(than, n, dai - n); if (r < 0) break; n += r }
        val ip = ipCua(k.localAddress) ?: ipMang() ?: return
        val thanChu = String(than, 0, n, Charsets.UTF_8)
        Log.i(NHAN, "HTTP ${phan[0]} ${phan[1]} từ ${k.inetAddress?.hostAddress} (${tieuDe["user-agent"].orEmpty().substringBefore(' ')})")
        val tl = traLoi(phan[0], phan[1].substringBefore('?'), thanChu, "http://$ip:$cong")
        k.getOutputStream().apply { write(tl); flush() }
    }

    /** Trả lời một yêu cầu HTTP (thuần, để thử): [goc] = "http://ip:cổng" mà điện thoại đã gọi tới. */
    fun traLoi(kieu: String, duong: String, than: String, goc: String): ByteArray = when {
        kieu == "GET" && duong == "/dd.xml" ->
            http(200, "OK", moTaThietBi(goc), "Application-URL" to "$goc/apps")
        kieu == "GET" && duong == "/apps" -> http(204, "No Content")
        kieu == "GET" && duong == "/apps/$APP" -> http(200, "OK", MO_TA_APP)
        kieu == "POST" && duong == "/apps/$APP" -> {
            val q = thamSo(than)
            val ma = q["pairingCode"]
            if (ma != null && runCatching { ghep(ma, q["theme"] ?: "cl") }.getOrDefault(false))
                http(200, "OK", null, "LOCATION" to "$goc/apps/$APP/run")
            else http(503, "Service Unavailable")
        }
        kieu == "DELETE" && duong.startsWith("/apps/$APP") -> http(405, "Method Not Allowed")
        else -> http(404, "Not Found")
    }

    fun moTaThietBi(goc: String) = """<?xml version="1.0"?>
<root xmlns="urn:schemas-upnp-org:device-1-0">
  <specVersion><major>1</major><minor>0</minor></specVersion>
  <URLBase>$goc</URLBase>
  <device>
    <deviceType>$THIET_BI</deviceType>
    <friendlyName>${xml(ten())}</friendlyName>
    <manufacturer>${xml(nhaSanXuat)}</manufacturer>
    <modelName>${xml(mau)}</modelName>
    <UDN>uuid:$uuid</UDN>
    <serviceList>
      <service>
        <serviceType>$DICH_VU</serviceType>
        <serviceId>urn:dial-multiscreen-org:serviceId:dial</serviceId>
        <controlURL>/ssdp/notfound</controlURL>
        <eventSubURL>/ssdp/notfound</eventSubURL>
        <SCPDURL>/ssdp/notfound</SCPDURL>
      </service>
    </serviceList>
  </device>
</root>
"""

    private fun http(ma: Int, ly: String, than: String? = null, vararg them: Pair<String, String>): ByteArray {
        val b = than?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
        val dau = StringBuilder("HTTP/1.1 $ma $ly\r\n")
        if (than != null) dau.append("Content-Type: application/xml; charset=utf-8\r\n")
        them.forEach { (k, v) -> dau.append("$k: $v\r\n") }
        dau.append("Content-Length: ${b.size}\r\nConnection: close\r\n\r\n")
        return dau.toString().toByteArray(Charsets.UTF_8) + b
    }

    private fun docDong(vao: InputStream): String? {
        val b = ByteArrayOutputStream()
        while (true) {
            val c = vao.read()
            if (c < 0) return if (b.size() > 0) b.toString("UTF-8") else null
            if (c == '\n'.code) return b.toString("UTF-8").trimEnd('\r')
            if (b.size() > 8192) return null
            b.write(c)
        }
    }

    // ---------- SSDP ----------

    private fun ngheSsdp() {
        val s = MulticastSocket(null).apply { reuseAddress = true; bind(InetSocketAddress(1900)); soTimeout = 60_000 }
        ssdp = s
        val nhom = InetAddress.getByName(NHOM)
        vaoNhom(s, nhom)
        if (!im) thongBao(true)
        var lanNhac = System.currentTimeMillis()
        val buf = ByteArray(2048)
        while (chay) {
            val g = DatagramPacket(buf, buf.size)
            try {
                s.receive(g)
                // Trả lời ngay: tivi có máy DIAL của hãng cùng IP thì app YouTube chỉ lấy máy trả lời trước. Không ghi log từng
                // lượt dò (điện thoại, máy trong nhà dò vài giây một lần: đẩy trôi mất nhật ký cần xem).
                val ip = ipNhanh(g.address)
                val goiTin = String(g.data, 0, g.length, Charsets.UTF_8)
                if (ip != null && (!im || ip == g.address.hostAddress)) {
                    for (tl in traLoiTim(goiTin, "http://$ip:$cong/dd.xml")) {
                        val b = tl.toByteArray(Charsets.UTF_8)
                        s.send(DatagramPacket(b, b.size, g.address, g.port))
                    }
                }
            } catch (_: SocketTimeoutException) {
            } catch (e: Exception) {
                if (!chay) break
                runCatching { Thread.sleep(1000) }
            }
            // Định kỳ: vào lại nhóm (Wi-Fi vừa nối lại thì mất) và báo còn sống.
            if (System.currentTimeMillis() - lanNhac > NHAC_LAI_MS) {
                vaoNhom(s, nhom); if (!im) thongBao(true); lanNhac = System.currentTimeMillis()
            }
        }
    }

    private fun vaoNhom(s: MulticastSocket, nhom: InetAddress) {
        var co = false
        runCatching { NetworkInterface.getNetworkInterfaces()?.toList() }.getOrNull().orEmpty()
            .filter { ni -> runCatching { ni.isUp && !ni.isLoopback && ni.supportsMulticast() && ni.inetAddresses.toList().any { it is Inet4Address } }.getOrDefault(false) }
            .forEach { ni -> if (runCatching { s.joinGroup(InetSocketAddress(nhom, 1900), ni) }.isSuccess) co = true }
        if (!co) runCatching { s.joinGroup(nhom) }
    }

    /** Các gói trả lời cho một M-SEARCH [tin] (thuần, để thử); không phải tìm DIAL thì rỗng. */
    fun traLoiTim(tin: String, viTri: String): List<String> {
        val dong = tin.split("\r\n", "\n")
        if (!dong.first().startsWith("M-SEARCH", ignoreCase = true)) return emptyList()
        val td = dong.drop(1).mapNotNull { d ->
            d.indexOf(':').takeIf { it > 0 }?.let { d.substring(0, it).trim().uppercase() to d.substring(it + 1).trim() }
        }.toMap()
        if (td["MAN"]?.contains("ssdp:discover") != true) return emptyList()
        val loai = when (val st = td["ST"]) {
            "ssdp:all" -> listOf(DICH_VU, THIET_BI, "upnp:rootdevice", "uuid:$uuid")
            DICH_VU, THIET_BI, "upnp:rootdevice", "uuid:$uuid" -> listOf(st)
            else -> emptyList()
        }
        return loai.map { st ->
            "HTTP/1.1 200 OK\r\nCACHE-CONTROL: max-age=1800\r\nDATE: ${ngay()}\r\nEXT:\r\nLOCATION: $viTri\r\n" +
                "SERVER: $mayChu\r\nST: $st\r\nUSN: ${usn(st)}\r\nBOOTID.UPNP.ORG: $bootId\r\nCONFIGID.UPNP.ORG: 1\r\n\r\n"
        }
    }

    private fun usn(loai: String) = if (loai == "uuid:$uuid") loai else "uuid:$uuid::$loai"

    /**
     * Chẩn đoán (TiviKit / ADB gọi qua provider): tự phát một M-SEARCH tìm DIAL như điện thoại, ghi các máy trả lời (kể cả
     * máy DIAL của hãng cùng IP) và sau bao nhiêu ms. Cùng IP mà máy hãng trả lời trước thì app YouTube chỉ thấy máy hãng.
     */
    fun thuTim(): org.json.JSONArray {
        val kq = org.json.JSONArray()
        MulticastSocket(0).use { s ->
            s.soTimeout = 300
            // false = cho gói vòng lại máy này (API ngược tên): máy nhận của mình và máy DIAL của hãng đều nhận được.
            runCatching { s.loopbackMode = false }
            val tin = "M-SEARCH * HTTP/1.1\r\nHOST: $NHOM:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 1\r\nST: $DICH_VU\r\n\r\n".toByteArray()
            val t0 = System.nanoTime()
            s.send(DatagramPacket(tin, tin.size, InetAddress.getByName(NHOM), 1900))
            val buf = ByteArray(2048)
            while (System.nanoTime() - t0 < 2_500_000_000L) {
                val g = DatagramPacket(buf, buf.size)
                try { s.receive(g) } catch (_: SocketTimeoutException) { continue }
                val chu = String(g.data, 0, g.length, Charsets.UTF_8)
                fun td(k: String) = Regex("(?im)^$k:\\s*(.+)$").find(chu)?.groupValues?.get(1)?.trim().orEmpty()
                kq.put(org.json.JSONObject().put("ms", (System.nanoTime() - t0) / 1_000_000).put("tu", "${g.address.hostAddress}:${g.port}")
                    .put("location", td("LOCATION")).put("usn", td("USN")).put("server", td("SERVER")))
            }
        }
        return kq
    }

    /** biChe theo lượt dò thật (thuTim). */
    fun biChe(): Boolean? = thuTim().let { a -> biChe((0 until a.length()).map { a.getJSONObject(it) }, cong) }

    /** NOTIFY ssdp:alive (lúc bật, định kỳ) / ssdp:byebye (lúc tắt) tới nhóm multicast. */
    private fun thongBao(song: Boolean) {
        val s = ssdp ?: return
        val ip = ipMang() ?: return
        val nhom = InetAddress.getByName(NHOM)
        for (nt in listOf("upnp:rootdevice", "uuid:$uuid", THIET_BI, DICH_VU)) {
            val tin = "NOTIFY * HTTP/1.1\r\nHOST: $NHOM:1900\r\n" +
                (if (song) "CACHE-CONTROL: max-age=1800\r\nLOCATION: http://$ip:$cong/dd.xml\r\nSERVER: $mayChu\r\n" else "") +
                "NT: $nt\r\nNTS: ${if (song) "ssdp:alive" else "ssdp:byebye"}\r\nUSN: ${usn(nt)}\r\n" +
                "BOOTID.UPNP.ORG: $bootId\r\nCONFIGID.UPNP.ORG: 1\r\n\r\n"
            val b = tin.toByteArray(Charsets.UTF_8)
            runCatching { s.send(DatagramPacket(b, b.size, nhom, 1900)) }
        }
    }

    // ---------- Địa chỉ ----------

    private fun ipCua(a: InetAddress?): String? = when (a) {
        is Inet4Address -> a.hostAddress
        is Inet6Address -> a.hostAddress?.removePrefix("::ffff:")?.takeIf { it.count { c -> c == '.' } == 3 }
        else -> null
    }

    private val ipDaBiet = HashMap<InetAddress, Pair<String, Long>>()

    /** ipToi nhớ 60 giây theo máy hỏi (điện thoại dò mỗi 10 giây): đỡ mở socket mỗi lần trả lời. */
    private fun ipNhanh(dich: InetAddress): String? {
        val bay = System.currentTimeMillis()
        ipDaBiet[dich]?.let { (ip, luc) -> if (bay - luc < 60_000) return ip }
        return ipToi(dich)?.also { ipDaBiet[dich] = it to bay }
    }

    /** IP của tivi trên đường tới [dich] (nối UDP không gửi gói nào). */
    private fun ipToi(dich: InetAddress): String? =
        runCatching { DatagramSocket().use { it.connect(dich, 9); ipCua(it.localAddress) } }.getOrNull()

    /** IP mạng nhà của tivi (đường ra Internet). */
    private fun ipMang(): String? = ipToi(InetAddress.getByName("8.8.8.8"))
}
