package vn.tivikit.cast

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Một tin của kênh Lounge: [aid] số thứ tự tin đến, [ten] tên lệnh, [du] dữ liệu đi kèm: JSONObject (setPlaylist…),
 * chuỗi ("S" = gsessionid), JSONArray ("c" = [SID, "", 8]) hay null ("pause", "noop"…).
 */
class TinNhan(val aid: Int, val ten: String, val du: Any? = null) {
    /** Trường chuỗi của dữ liệu dạng đối tượng; rỗng coi như không có. */
    fun chu(khoa: String): String? = (du as? JSONObject)?.optString(khoa)?.takeIf { it.isNotEmpty() }

    override fun toString() = "[$aid,$ten${du?.let { ",$it" } ?: ""}]"

    companion object {
        /** Các tin trong cả thân trả về [s] (một hay nhiều khúc "độ dài\n[[…]]", có thể có tiền tố ")]}'"). */
        fun tach(s: String): List<TinNhan> = KhucJson().an(s).flatMap { tuKhuc(it) }

        /** Các tin trong một khúc [[aid,["tên", dữ liệu…]], …]; khúc hỏng thì bỏ. */
        fun tuKhuc(json: String): List<TinNhan> = runCatching {
            val a = JSONArray(json)
            (0 until a.length()).mapNotNull { i ->
                val m = a.optJSONArray(i) ?: return@mapNotNull null
                val nd = m.optJSONArray(1) ?: return@mapNotNull null
                val ten = nd.optString(0).takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val du: Any? = when (nd.length()) {
                    1 -> null
                    2 -> nd.get(1)
                    else -> JSONArray().also { r -> for (j in 1 until nd.length()) r.put(nd.get(j)) }
                }
                TinNhan(m.optInt(0), ten, du)
            }
        }.getOrDefault(emptyList())

        /** Thân POST gửi [ds] lên kênh: count, ofs (số tin đã gửi trong phiên), req{i}__sc = tên, req{i}_{khoá} = giá trị. */
        fun thanGui(ds: List<Pair<String, Map<String, String>>>, ofs: Int): String = buildList {
            add("count" to ds.size.toString())
            add("ofs" to ofs.toString())
            ds.forEachIndexed { i, (ten, du) ->
                add("req${i}__sc" to ten)
                du.forEach { (k, v) -> add("req${i}_$k" to v) }
            }
        }.joinToString("&") { (k, v) -> "${maHoa(k)}=${maHoa(v)}" }

        fun maHoa(s: String): String = URLEncoder.encode(s, "UTF-8")
    }
}

/**
 * Bóc các mảng JSON ngoài cùng khỏi chữ đến dần (luồng long-poll): bỏ qua số độ dài, xuống dòng, tiền tố lạ; tính
 * ngoặc vuông trừ phần trong chuỗi. Không dựa vào số độ dài (đếm theo ký tự hay byte tuỳ phía YouTube).
 */
class KhucJson {
    private val dang = StringBuilder()
    private var sau = 0
    private var trongChuoi = false
    private var thoat = false

    /** Nhận thêm [s]; trả các mảng vừa đủ ngoặc. */
    fun an(s: CharSequence): List<String> {
        val xong = mutableListOf<String>()
        for (c in s) {
            if (sau == 0) {
                if (c == '[') { dang.setLength(0); dang.append(c); sau = 1 }
                continue
            }
            dang.append(c)
            if (trongChuoi) {
                when {
                    thoat -> thoat = false
                    c == '\\' -> thoat = true
                    c == '"' -> trongChuoi = false
                }
                continue
            }
            when (c) {
                '"' -> trongChuoi = true
                '[' -> sau++
                ']' -> if (--sau == 0) xong += dang.toString()
            }
        }
        return xong
    }
}
