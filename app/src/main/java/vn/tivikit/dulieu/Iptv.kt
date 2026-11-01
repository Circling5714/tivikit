package vn.tivikit.dulieu

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * IPTV (1/11/2026): app phụ vn.tivikit.iptv (module iptv/) phát kênh trong danh sách IPTV của người dùng (link .m3u, link rút
 * gọn cũng được) bằng Media3, đặt User-Agent / Referer riêng từng kênh như danh sách ghi. Ô Kênh nhanh IPTV, nguồn IPTV của ô
 * Auto mở màn PhatKenh với tên và mã kênh; app phụ tự tải lại danh sách theo link nên danh sách đổi nguồn kênh vẫn mở được.
 * Ở đây: đọc danh sách trên điện thoại để chọn kênh (tên, mã tvg-id, nhóm, logo), lệnh cấu hình app phụ. App không đóng sẵn
 * danh sách nào: link là của người dùng.
 */
object Iptv {
    const val GOI = "vn.tivikit.iptv"
    const val APK = "iptv.apk"
    const val PHAT = "$GOI.PhatKenh"
    const val LENH_DOC = "content call --uri content://$GOI --method doc"
    const val LENH_TAI = "content call --uri content://$GOI --method tai"

    /**
     * Một kênh để chọn: [ma] = tvg-id ("" nếu danh sách không ghi; nhiều kênh có khi chung một mã như "no_epg", nên ô nhận
     * kênh theo cả mã lẫn tên), [nhom] = group-title, [logo] = tvg-logo.
     */
    data class Kenh(val ten: String, val ma: String, val nhom: String, val logo: String) {
        /**
         * Mã của ô: tvg-id nếu dùng được trong lệnh, không thì tên đã gọn, tên không gọn được (chữ Nga…) thì "t" + băm của
         * tên; app phụ tìm lại theo tên (DanhSach.tim).
         */
        val maO: String get() = ma.takeIf { MA.matches(it) } ?: KenhNhanh.khoaTen(ten).ifEmpty { "t" + Integer.toHexString(ten.hashCode()) }
    }

    /** Mã kênh IPTV của ô (đi trong lệnh content call bọc nháy đơn và extra của Intent). */
    val MA = Regex("[A-Za-z0-9_.@:+-]{1,80}")
    /** Link danh sách: http(s), không khoảng trắng, nháy (đi trong lệnh content call bọc nháy đơn), như CauHinhProvider.LINK của app phụ. */
    val LINK = Regex("https?://[^\\s'\"`]{3,2000}")
    private val THUOC_TINH = Regex("""([\w-]+)="([^"]*)"""")

    /** Kênh trong danh sách .m3u (dòng #EXTINF có link theo sau), như DanhSach.doc của app phụ (ở đây không cần link, header). */
    fun doc(chu: String): List<Kenh> {
        val ds = ArrayList<Kenh>()
        var dau: Kenh? = null
        for (dong0 in chu.lineSequence()) {
            val dong = dong0.trim()
            when {
                dong.startsWith("#EXTINF", ignoreCase = true) -> dau = docExtinf(dong)
                dong.isEmpty() || dong.startsWith("#") -> {}
                dau != null -> { if (dau.ten.isNotBlank()) ds += dau; dau = null }
            }
        }
        return ds
    }

    private fun docExtinf(dong: String): Kenh {
        var trongNhay = false
        var phay = -1
        for (i in dong.indices) {
            val c = dong[i]
            if (c == '"') trongNhay = !trongNhay
            else if (c == ',' && !trongNhay) { phay = i; break }
        }
        val dau = if (phay >= 0) dong.substring(0, phay) else dong
        val tt = THUOC_TINH.findAll(dau).associate { it.groupValues[1].lowercase() to it.groupValues[2].trim() }
        val ten = (if (phay >= 0) dong.substring(phay + 1).trim() else "").ifEmpty { tt["tvg-name"].orEmpty() }
        return Kenh(ten, tt["tvg-id"].orEmpty(), tt["group-title"].orEmpty(), tt["tvg-logo"].orEmpty())
    }

    /** Tải danh sách ở [url] (gọi trên luồng IO), đi theo chuyển hướng (link rút gọn → file thật, cả http ↔ https), tối đa 8 MB. */
    fun tai(url: String): String {
        var u = url
        repeat(6) {
            val c = URL(u).openConnection() as HttpURLConnection
            try {
                c.instanceFollowRedirects = false
                c.connectTimeout = 10_000; c.readTimeout = 15_000
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) TiviKit")
                val ma = c.responseCode
                if (ma in 300..399) {
                    u = URL(URL(u), c.getHeaderField("Location") ?: error("chuyển hướng rỗng")).toString()
                    return@repeat
                }
                if (ma !in 200..299) error("HTTP $ma")
                val ra = java.io.ByteArrayOutputStream()
                c.inputStream.use { vao ->
                    val dem = ByteArray(16 shl 10)
                    while (ra.size() < TOI_DA) { val n = vao.read(dem); if (n < 0) break; ra.write(dem, 0, n) }
                }
                return ra.toString("UTF-8")
            } finally {
                c.disconnect()
            }
        }
        error("chuyển hướng quá nhiều")
    }

    /** Danh sách lớn nhất đọc về (8 MB; danh sách 472 kênh ~90 KB). */
    private const val TOI_DA = 8 shl 20

    /** Lệnh đặt link danh sách cho app phụ (chữ ngoài ASCII thành \uXXXX như TiviKitLauncher.chiAscii). */
    fun lenhDat(link: String): String {
        require(LINK.matches(link)) { "link" }
        return "content call --uri content://$GOI --method dat --arg '" + TiviKitLauncher.chiAscii(JSONObject().put("link", link).toString()) + "'"
    }

    /** Link danh sách app phụ đang dùng, trong kết quả lệnh [LENH_DOC] ("" nếu chưa đặt / không đọc được). */
    fun linkTrong(j: JSONObject?): String = j?.optString("link").orEmpty()
}
