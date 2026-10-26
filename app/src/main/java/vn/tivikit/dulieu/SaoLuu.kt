package vn.tivikit.dulieu

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Trạng thái tivi TRƯỚC khi app đụng vào, cộng những gì app đã làm. Ghi xuống máy trước lệnh
 * đầu tiên; lần chạy sau trên cùng tivi giữ nguyên ảnh "trước" của lần đầu (như recovery.py).
 */
data class BanSaoLuu(
    val khoa: String,
    val ten: String,
    var ip: String,
    val luc: Long,
    val daTatTruoc: Set<String>,
    val homeTruoc: String,
    val dnsModeTruoc: String,
    val dnsHostTruoc: String,
    val truyCapTruoc: String,
    val getprop: String,
    val goiTruoc: List<String>,
    val daTatBoiApp: MutableSet<String> = linkedSetOf(),
    var launcherDaCai: Boolean = false,
    var homeGocDaTat: String = "",
    var dnsDaDoi: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("khoa", khoa); put("ten", ten); put("ip", ip); put("luc", luc)
        put("daTatTruoc", JSONArray(daTatTruoc.toList()))
        put("homeTruoc", homeTruoc); put("dnsModeTruoc", dnsModeTruoc); put("dnsHostTruoc", dnsHostTruoc)
        put("truyCapTruoc", truyCapTruoc); put("getprop", getprop)
        put("goiTruoc", JSONArray(goiTruoc))
        put("daTatBoiApp", JSONArray(daTatBoiApp.toList()))
        put("launcherDaCai", launcherDaCai); put("homeGocDaTat", homeGocDaTat); put("dnsDaDoi", dnsDaDoi)
    }

    companion object {
        fun tuJson(o: JSONObject): BanSaoLuu {
            fun list(k: String) = o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
            return BanSaoLuu(
                khoa = o.getString("khoa"), ten = o.optString("ten"), ip = o.optString("ip"), luc = o.optLong("luc"),
                daTatTruoc = list("daTatTruoc").toSet(), homeTruoc = o.optString("homeTruoc"),
                dnsModeTruoc = o.optString("dnsModeTruoc"), dnsHostTruoc = o.optString("dnsHostTruoc"),
                truyCapTruoc = o.optString("truyCapTruoc"), getprop = o.optString("getprop"),
                goiTruoc = list("goiTruoc"), daTatBoiApp = list("daTatBoiApp").toMutableSet(),
                launcherDaCai = o.optBoolean("launcherDaCai"), homeGocDaTat = o.optString("homeGocDaTat"),
                dnsDaDoi = o.optBoolean("dnsDaDoi"),
            )
        }
    }
}

class KhoSaoLuu(context: Context) {
    private val thuMuc = File(context.filesDir, "sao-luu").apply { mkdirs() }

    private fun file(khoa: String) = File(thuMuc, "$khoa.json")

    fun doc(khoa: String): BanSaoLuu? = runCatching { BanSaoLuu.tuJson(JSONObject(file(khoa).readText())) }.getOrNull()

    /** Ghi qua file tạm rồi đổi tên, để mất điện giữa chừng không làm hỏng bản cũ. */
    fun ghi(b: BanSaoLuu) {
        val tam = File(thuMuc, "${b.khoa}.json.tam")
        tam.writeText(b.toJson().toString(1))
        if (!tam.renameTo(file(b.khoa))) error("Không ghi được bản sao lưu")
    }

    fun tatCa(): List<BanSaoLuu> = thuMuc.listFiles { f -> f.name.endsWith(".json") }
        ?.mapNotNull { runCatching { BanSaoLuu.tuJson(JSONObject(it.readText())) }.getOrNull() }
        ?.sortedByDescending { it.luc } ?: emptyList()

    fun xoa(khoa: String) { file(khoa).delete() }

    fun duongDan(khoa: String): File = file(khoa)
}
