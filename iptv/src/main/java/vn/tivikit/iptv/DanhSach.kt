package vn.tivikit.iptv

import org.json.JSONObject
import java.text.Normalizer

/**
 * Một kênh trong danh sách IPTV: [ten], [ma] (tvg-id; "" nếu danh sách không ghi), [nhom] (group-title), [logo] (tvg-logo),
 * [link] luồng phát, [ua] / [referer] riêng của kênh ("" = mặc định).
 */
data class KenhIptv(
    val ten: String, val ma: String, val nhom: String, val logo: String, val link: String, val ua: String = "", val referer: String = "",
)

/**
 * Đọc danh sách IPTV dạng .m3u (#EXTM3U): mỗi kênh một dòng #EXTINF (thuộc tính tvg-id, tvg-logo, group-title, tên sau dấu
 * phẩy ngoài ngoặc kép), các dòng tuỳ chọn (#EXTVLCOPT:http-user-agent= / http-referrer=, #EXTHTTP:{"User-Agent":…}), rồi
 * dòng link (đuôi "|User-Agent=…&Referer=…" kiểu Kodi cũng đọc). Danh sách lấy theo link của người dùng; app không đóng
 * sẵn kênh nào.
 */
object DanhSach {
    private val THUOC_TINH = Regex("""([\w-]+)="([^"]*)"""")

    fun doc(chu: String): List<KenhIptv> {
        val ds = ArrayList<KenhIptv>()
        var dau: KenhIptv? = null
        for (dong0 in chu.lineSequence()) {
            val dong = dong0.trim()
            when {
                dong.startsWith("#EXTINF", ignoreCase = true) -> dau = docExtinf(dong)
                dong.startsWith("#EXTVLCOPT", ignoreCase = true) -> dau = dau?.let { k ->
                    val (khoa, gt) = dong.substringAfter(':').split('=', limit = 2).let { it[0].trim().lowercase() to it.getOrElse(1) { "" }.trim() }
                    when (khoa) {
                        "http-user-agent" -> k.copy(ua = gt)
                        "http-referrer", "http-referer" -> k.copy(referer = gt)
                        else -> k
                    }
                }
                dong.startsWith("#EXTHTTP", ignoreCase = true) -> dau = dau?.let { k ->
                    val j = runCatching { JSONObject(dong.substringAfter(':')) }.getOrNull() ?: return@let k
                    k.copy(ua = j.optString("User-Agent", j.optString("user-agent", k.ua)), referer = j.optString("Referer", j.optString("referer", k.referer)))
                }
                dong.isEmpty() || dong.startsWith("#") -> {}
                dau != null -> {
                    ds += voiLink(dau, dong)
                    dau = null
                }
            }
        }
        return ds
    }

    /** "#EXTINF:-1 tvg-id="vtv1hd" tvg-logo="…" group-title="KÊNH VTV",VTV1 HD": tên là phần sau dấu phẩy đầu tiên ngoài ngoặc kép. */
    private fun docExtinf(dong: String): KenhIptv {
        var trongNhay = false
        var phay = -1
        for (i in dong.indices) {
            val c = dong[i]
            if (c == '"') trongNhay = !trongNhay
            else if (c == ',' && !trongNhay) { phay = i; break }
        }
        val dau = if (phay >= 0) dong.substring(0, phay) else dong
        val ten = if (phay >= 0) dong.substring(phay + 1).trim() else ""
        val tt = THUOC_TINH.findAll(dau).associate { it.groupValues[1].lowercase() to it.groupValues[2].trim() }
        return KenhIptv(ten.ifEmpty { tt["tvg-name"].orEmpty() }, tt["tvg-id"].orEmpty(), tt["group-title"].orEmpty(), tt["tvg-logo"].orEmpty(), "")
    }

    /** Dòng link, đuôi "|User-Agent=…&Referer=…" (kiểu Kodi) tách ra làm header. */
    private fun voiLink(k: KenhIptv, dong: String): KenhIptv {
        val link = dong.substringBefore('|').trim()
        var ua = k.ua
        var referer = k.referer
        if ('|' in dong) dong.substringAfter('|').split('&').forEach { cap ->
            val (khoa, gt) = cap.split('=', limit = 2).let { it[0].trim().lowercase() to it.getOrElse(1) { "" }.trim() }
            when (khoa) {
                "user-agent" -> ua = gt
                "referer", "referrer" -> referer = gt
            }
        }
        return k.copy(link = link, ua = ua, referer = referer)
    }

    /**
     * Tên kênh để so (như KenhNhanh.khoaTen của TiviKit, khoaKenh của Map Voice): không dấu, bỏ "kênh", "truyền hình", HD /
     * FHD / UHD / 4K, chỉ giữ a-z 0-9, gộp tên khác ("QPVN HD" = "Quốc phòng Việt Nam").
     */
    fun khoa(ten: String): String {
        val kd = Normalizer.normalize(ten.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").replace('đ', 'd')
        val k = kd.replace(Regex("\\b(kenh|truyen hinh|hd|fhd|uhd|4k)\\b"), " ").replace(Regex("[^a-z0-9]"), "")
        return BI_DANH[k] ?: k
    }

    private val BI_DANH = mapOf(
        "qpvn" to "quocphongvietnam", "quocphongvn" to "quocphongvietnam", "antv" to "congannhandan",
        "hanoitv1" to "hanoi1", "hanoitv2" to "hanoi2",
        "thvl1" to "vinhlong1", "thvl2" to "vinhlong2", "thvl3" to "vinhlong3", "thvl4" to "vinhlong4",
    )

    /**
     * Kênh của ô: theo [ma] (tvg-id; nhiều kênh có khi dùng chung một mã, như "no_epg" của các kênh radio) và [ten] (tên trên
     * ô, đã gọn như [gon]): cùng mã cùng tên, rồi cùng tên, rồi mã chỉ một kênh dùng (danh sách đổi tên kênh), rồi tên đã gọn
     * ([khoa]). null nếu danh sách không còn kênh này.
     */
    fun tim(ds: List<KenhIptv>, ma: String, ten: String): KenhIptv? {
        val cungMa = if (ma.isEmpty()) emptyList() else ds.filter { it.ma.equals(ma, ignoreCase = true) }
        cungMa.firstOrNull { trungTen(it.ten, ten) }?.let { return it }
        if (ten.isNotBlank()) ds.firstOrNull { trungTen(it.ten, ten) }?.let { return it }
        cungMa.singleOrNull()?.let { return it }
        val k = khoa(ten)
        return if (k.isEmpty()) null else ds.firstOrNull { khoa(it.ten) == k }
    }

    /** Tên [goc] trong danh sách là tên [ten] của ô: trùng hẳn, hay trùng sau khi gọn như tên ô ([gon]). */
    private fun trungTen(goc: String, ten: String) = goc == ten || gon(goc) == ten

    /**
     * Tên kênh như TiviKit đặt cho ô (KenhNhanh.tenSach): bỏ dấu phẩy, nháy, ký tự shell, "=", xuống dòng; gộp khoảng trắng;
     * tối đa 30 ký tự.
     */
    fun gon(s: String): String = s.replace(Regex("[,'\"`\$\\\\=\\r\\n]"), " ").trim().replace(Regex("\\s+"), " ").take(30)
}
