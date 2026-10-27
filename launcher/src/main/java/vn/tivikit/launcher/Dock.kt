package vn.tivikit.launcher

/** Danh sách gói trong dock: thêm, bỏ, dời chỗ, dock mặc định và app mở gần đây (không phụ thuộc Android). */
object Dock {
    /** Dock mặc định lấy tối đa chừng này app. */
    const val MAC_DINH = 6

    /** App hay dùng trên tivi ở Việt Nam, theo thứ tự đưa vào dock mặc định (gói nào có trên tivi mới lấy). */
    val GOI_Y = listOf(
        "org.smarttube.stable", "org.smarttube.beta", "com.teamsmart.videomanager.tv", "com.liskovsoft.smarttubetv.beta",
        "com.google.android.youtube.tv", "com.viettel.tv360.tv", "net.fptplay.ottbox", "com.fptplay.tv", "vn.vtv.vtvgotv",
        "com.vieon.tv", "vn.vieon.tv", "vn.vnpt.mytv.atv", "com.vnpt.mytv", "com.netflix.ninja",
        "com.google.android.youtube.tvmusic", "com.spotify.tv.android", "com.galaxyplay.tv", "com.kplus.tv", "com.clip.tv",
        "org.xbmc.kodi", "com.amazon.amazonvideo.livingroom", "com.disney.disneyplus",
    )

    private val TEN_GOI = Regex("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+")

    fun hopLe(goi: String) = goi.length <= 200 && TEN_GOI.matches(goi)

    /** Dock lần đầu: app gợi ý có trên tivi; không có app nào trong đó thì lấy các app đầu danh sách. */
    fun macDinh(coTren: List<String>): List<String> {
        val goiY = GOI_Y.filter { it in coTren }.take(MAC_DINH)
        return goiY.ifEmpty { coTren.take(MAC_DINH) }
    }

    fun them(ds: List<String>, goi: String): List<String> = if (goi in ds) ds else ds + goi

    fun bo(ds: List<String>, goi: String): List<String> = ds - goi

    /** Dời [goi] sang trái (buoc < 0) hay phải (buoc > 0), dừng ở hai đầu. */
    fun doi(ds: List<String>, goi: String, buoc: Int): List<String> {
        val i = ds.indexOf(goi)
        if (i < 0) return ds
        val j = (i + buoc).coerceIn(0, ds.lastIndex)
        if (i == j) return ds
        return ds.toMutableList().apply { removeAt(i); add(j, goi) }
    }

    /** Bỏ các gói không còn trên tivi (đã gỡ / tắt), giữ thứ tự. */
    fun conLai(ds: List<String>, coTren: Set<String>): List<String> = ds.filter { it in coTren }

    /** Ghi app vừa mở lên đầu lịch sử (không trùng, giữ [toiDa] mục). */
    fun ghiMo(lichSu: List<String>, goi: String, toiDa: Int = 12): List<String> = (listOf(goi) + (lichSu - goi)).take(toiDa)

    /** App mở gần đây cho phần sau vạch ngăn: chưa có trong dock, còn trên tivi, tối đa [toiDa] app. */
    fun ganDay(lichSu: List<String>, dock: List<String>, coTren: Set<String>, toiDa: Int = 3): List<String> =
        lichSu.filter { it !in dock && it in coTren }.distinct().take(toiDa)

    /** Dock vừa khít 7 ô banner (cùng 2 vạch và nút Tất cả): ghim nhiều thì bớt app gần đây (Skyworth 6 ghim + 3 gần đây tràn). */
    const val O_VUA = 7

    /**
     * App gần đây theo thống kê sử dụng [suDung] nếu có (TiviKit đã cấp quyền), không thì theo lịch sử mở từ màn Home
     * [lichSu]; bỏ các gói [boQua] (launcher, chính màn Home). Mặc định lấy đủ chỗ trống còn lại cho dock vừa [O_VUA] ô.
     */
    fun ganDayTu(suDung: List<String>, lichSu: List<String>, dock: List<String>, coTren: Set<String>, boQua: Set<String>,
                 toiDa: Int = (O_VUA - dock.count { it in coTren }).coerceIn(0, 3)): List<String> =
        ganDay(suDung.ifEmpty { lichSu }.filter { it !in boQua }, dock, coTren, toiDa)
}
