package vn.tivikit.dulieu

/**
 * Bài đang phát trên tivi, đọc từ `dumpsys media_session` (27/10/2026, thẻ Đang phát của màn Điều khiển). Hỏi thẳng các phiên
 * phát (MediaSessionManager, cách của tv-remote) thì có cả độ dài bài và tua được, nhưng cần quyền MEDIA_CONTENT_CONTROL mà
 * shell không có trên cả TX3 Mini (Android 9) lẫn Skyworth (Android 11); quyền DUMP thì shell có. Bản dump có app, trạng
 * thái, vị trí, tốc độ, mốc cập nhật và mô tả (tên bài, dòng phụ), không có độ dài.
 */
object DangPhat {
    /** PlaybackState: đang dừng, đang phát, đang tải. */
    const val DUNG = 2
    const val PHAT = 3
    const val CHO = 6

    /**
     * Một phiên trong bản dump. [tt] theo PlaybackState; [capNhat] = elapsedRealtime (ms) của tivi lúc phiên báo [viTri];
     * [hoatDong] = active=true (phiên đóng / app đã thoát thì false).
     */
    data class Phien(
        val goi: String, val hoatDong: Boolean, val tt: Int, val viTri: Long, val tocDo: Float, val capNhat: Long,
        val tieuDe: String?, val phu: String?,
    )

    private val TRANG_THAI = Regex("""state=PlaybackState \{state=(\d+), position=(-?\d+), buffered position=-?\d+, speed=(-?[\d.]+), updated=(\d+)""")

    /** Các phiên trong `dumpsys media_session`: mỗi khối bắt đầu ở dòng "package=", lấy active / state / metadata của nó. */
    fun tuDump(s: String): List<Phien> {
        val ds = mutableListOf<Phien>()
        var goi: String? = null
        var hoatDong = false
        var tt: MatchResult? = null
        var moTa: String? = null
        fun xong() {
            val g = goi ?: return
            val m = tt
            val (tieuDe, phu) = moTa?.let { tachMoTa(it) } ?: (null to null)
            if (m != null) ds += Phien(g, hoatDong, m.groupValues[1].toInt(), m.groupValues[2].toLong(),
                m.groupValues[3].toFloatOrNull() ?: 1f, m.groupValues[4].toLong(), tieuDe, phu)
        }
        for (dong in s.lineSequence()) {
            val l = dong.trim()
            when {
                l.startsWith("package=") -> { xong(); goi = l.removePrefix("package=").trim(); hoatDong = false; tt = null; moTa = null }
                goi == null -> {}
                l.startsWith("active=") -> hoatDong = l.removePrefix("active=").trim() == "true"
                l.startsWith("state=PlaybackState") -> tt = TRANG_THAI.find(l)
                l.startsWith("metadata") && l.contains("description=") -> moTa = l.substringAfter("description=")
            }
        }
        xong()
        return ds
    }

    /**
     * MediaDescription in ra "tên, dòng phụ, mô tả" (null in chữ "null"): bỏ các ", null" ở cuối rồi tách ở dấu ", " đầu tiên.
     * Tên có dấu phẩy thì phần sau rơi xuống dòng phụ — vẫn đọc được, bản dump không cho biết chỗ cắt đúng.
     */
    fun tachMoTa(moTa: String): Pair<String?, String?> {
        var s = moTa.trim()
        while (s.endsWith(", null")) s = s.removeSuffix(", null")
        if (s.isEmpty() || s == "null") return null to null
        val i = s.indexOf(", ")
        if (i < 0) return s to null
        val ten = s.substring(0, i).takeIf { it != "null" }
        val phu = s.substring(i + 2).removePrefix("null, ").takeIf { it.isNotBlank() && it != "null" }
        return (ten ?: phu) to (if (ten == null) null else phu)
    }

    /** Phiên nên hiện: đang phát / đang tải trước; không có thì phiên đang dừng của [goiTruoc] (app đang ở trước màn hình). */
    fun chon(ds: List<Phien>, goiTruoc: String?): Phien? {
        val con = ds.filter { it.hoatDong }
        return con.firstOrNull { it.tt == PHAT || it.tt == CHO } ?: con.firstOrNull { it.tt == DUNG && it.goi == goiTruoc }
    }

    /** Vị trí lúc [bayGio] (elapsedRealtime của tivi, ms): đang phát thì cộng thời gian đã trôi từ [moc] × tốc độ. */
    fun viTriLuc(p: Phien, bayGio: Long, moc: Long = p.capNhat): Long =
        if (p.tt == PHAT) (p.viTri + ((bayGio - moc).coerceAtLeast(0) * p.tocDo).toLong()).coerceAtLeast(0) else p.viTri.coerceAtLeast(0)

    /** "4:05", "1:02:03". */
    fun dongHo(ms: Long): String {
        val g = (ms.coerceAtLeast(0) / 1000)
        val h = g / 3600; val p = g % 3600 / 60; val s = g % 60
        return if (h > 0) "%d:%02d:%02d".format(h, p, s) else "%d:%02d".format(p, s)
    }
}
