package vn.tivikit.dulieu

/**
 * App đang chạy ngầm và RAM của chúng (26/10/2026, rút từ box Tanix TX3 Mini 2 GB: microG của ReVanced không còn app nào
 * dùng vẫn chiếm khoảng 210 MB, trình cập nhật ROM 80 MB, Media Center 66 MB, trợ lý giọng nói 100 MB với remote không
 * có mic). Đọc `ps -A -o RSS,NAME`, gộp tiến trình về gói, chỉ ra app nên / có thể tắt.
 */
object ChayNgam {
    /** Một gói đang chạy: tổng RSS (KB) các tiến trình của nó. */
    data class App(val pkg: String, val ramKb: Long)

    /** Vì sao nên tắt; [chac] = chắc chắn thừa, false = chỉ khi không dùng tính năng đó. */
    enum class LyDo(val chac: Boolean) { MICROG_THUA(true), CAP_NHAT_ROM(false), DLNA(false), TRO_LY_GIONG_NOI(false) }

    /** (tên tiến trình, RSS KB) từ `ps -A -o RSS,NAME` (toybox, Android 8+); bỏ dòng tiêu đề và dòng lạ. */
    fun tuPs(s: String): List<Pair<String, Long>> = s.lineSequence().mapNotNull { l ->
        val c = l.trim().split(Regex("\\s+"))
        if (c.size < 2) return@mapNotNull null
        c.last() to (c[0].toLongOrNull() ?: return@mapNotNull null)
    }.toList()

    /** Gộp RSS theo gói đã cài ([goiCua]); tiến trình không thuộc gói nào (dịch vụ hệ thống, luồng nhân) bỏ qua. Nhiều RAM trước. */
    fun gopTheoGoi(ps: List<Pair<String, Long>>, goi: Set<String>): List<App> {
        val tong = HashMap<String, Long>()
        for ((ten, rss) in ps) goiCua(ten, goi)?.let { g -> tong[g] = (tong[g] ?: 0L) + rss }
        return tong.map { App(it.key, it.value) }.sortedByDescending { it.ramKb }
    }

    /**
     * Gói của một tiến trình: trùng tên gói, hoặc gói + ":…" / ".…" (com.google.android.gms.persistent thuộc
     * com.google.android.gms); lấy gói dài nhất khớp. Gói không có dấu chấm ("android") không tính.
     */
    fun goiCua(ten: String, goi: Set<String>): String? {
        if ('.' in ten && ten in goi) return ten
        var i = ten.length
        while (i > 0) {
            i = maxOf(ten.lastIndexOf(':', i - 1), ten.lastIndexOf('.', i - 1))
            if (i <= 0) return null
            val dau = ten.substring(0, i)
            if ('.' in dau && dau in goi) return dau
        }
        return null
    }

    /** Lý do nên / có thể tắt [pkg] khi nó đang chạy ngầm; [goi] = mọi gói đã cài. null = không có gì để nói. */
    fun nenTat(pkg: String, goi: Set<String>): LyDo? = when (pkg) {
        // microG chỉ để các bản YouTube sửa đổi đăng nhập Google: không còn bản nào thì nó chạy không.
        "app.revanced.android.gms" -> LyDo.MICROG_THUA.takeIf { goi.none { it.startsWith("app.revanced.android.") && it != pkg } }
        "com.mgoogle.android.gms" -> LyDo.MICROG_THUA.takeIf { goi.none { it.startsWith("com.vanced.android.") } }
        "com.droidlogic.otaupgrade", "com.tox.updater" -> LyDo.CAP_NHAT_ROM
        "com.droidlogic.mediacenter" -> LyDo.DLNA
        "com.google.android.katniss" -> LyDo.TRO_LY_GIONG_NOI
        else -> null
    }
}
