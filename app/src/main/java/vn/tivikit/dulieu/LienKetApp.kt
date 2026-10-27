package vn.tivikit.dulieu

/**
 * Set App Link (27/10/2026): chọn app mở link của từng trang trên tivi, VD link YouTube mở thẳng bằng SmartTube thay vì
 * hỏi chọn app. Android 11 trở xuống đặt theo app (`pm set-app-link … always`: app đó mở mọi link nó khai, app đặt sau
 * thắng); Android 12 trở lên theo lựa chọn của người dùng (`pm set-app-links-user-selection`), mà app được hãng xác minh
 * trang đó vẫn thắng lựa chọn — phải tắt quyền mở link tự động của nó (`set-app-links-allowed false`).
 * Chạy lệnh: ui/LienKetAppTivi.kt.
 */
object LienKetApp {
    /** Một trang: tên, link mẫu để hỏi tivi app nào mở được, tên miền hiện cho người dùng. */
    data class Trang(val ten: String, val link: String, val mien: String)

    /** Trang hay gặp trên tivi; màn hình chỉ hiện trang có app (không phải trình duyệt) mở được. */
    val TRANG = listOf(
        Trang("YouTube", "https://www.youtube.com/watch?v=dQw4w9WgXcQ", "youtube.com · youtu.be"),
        Trang("YouTube Music", "https://music.youtube.com/watch?v=dQw4w9WgXcQ", "music.youtube.com"),
        Trang("Netflix", "https://www.netflix.com/title/80057281", "netflix.com"),
        Trang("Spotify", "https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC", "open.spotify.com"),
        Trang("Prime Video", "https://app.primevideo.com/detail?gti=amzn1.dv.gti.1", "primevideo.com"),
        Trang("Disney+", "https://www.disneyplus.com/home", "disneyplus.com"),
        Trang("Apple TV", "https://tv.apple.com/", "tv.apple.com"),
        Trang("Twitch", "https://www.twitch.tv/directory", "twitch.tv"),
        Trang("TikTok", "https://www.tiktok.com/@tiktok", "tiktok.com"),
        Trang("Facebook", "https://www.facebook.com/watch/", "facebook.com"),
        Trang("Vimeo", "https://vimeo.com/76979871", "vimeo.com"),
        Trang("Dailymotion", "https://www.dailymotion.com/video/x8j4o8c", "dailymotion.com"),
        Trang("iQIYI", "https://www.iq.com/play/1", "iq.com"),
        Trang("Bilibili", "https://www.bilibili.tv/video/2041", "bilibili.tv"),
        Trang("SoundCloud", "https://soundcloud.com/discover", "soundcloud.com"),
        Trang("VieON", "https://vieon.vn/", "vieon.vn"),
        Trang("FPT Play", "https://fptplay.vn/", "fptplay.vn"),
        Trang("TV360", "https://tv360.vn/", "tv360.vn"),
        Trang("VTVgo", "https://vtvgo.vn/", "vtvgo.vn"),
        Trang("Zing MP3", "https://zingmp3.vn/", "zingmp3.vn"),
        Trang("Nhaccuatui", "https://www.nhaccuatui.com/", "nhaccuatui.com"),
        Trang("Steam", "https://store.steampowered.com/app/570", "store.steampowered.com"),
    )

    /** Link không app nào khai: chỉ trình duyệt nhận, nhờ đó biết app nào là trình duyệt (không đưa vào lựa chọn). */
    const val LINK_TRINH_DUYET = "https://tivikit-kiem-tra.invalid/"

    private const val VIEW = "-a android.intent.action.VIEW -c android.intent.category.BROWSABLE -d"

    /** Hỏi tivi app nào mở được [link]. */
    fun lenhHoi(link: String) = "cmd package query-activities --brief $VIEW ${DongLenh.nhay(link)}"

    /** Hỏi tivi đang mở [link] bằng gì (dòng cuối: gói/màn, hoặc hộp chọn app của hệ thống). */
    fun lenhChon(link: String) = "cmd package resolve-activity --brief $VIEW ${DongLenh.nhay(link)} | tail -n 1"

    /** Một lệnh cho cả lượt: trình duyệt, rồi với mỗi link các app mở được và app đang mở; mỗi phần sau một dòng dấu. */
    fun lenhQuet(links: List<String>): String = buildString {
        append("echo @@B; ").append(lenhHoi(LINK_TRINH_DUYET))
        links.forEachIndexed { i, l -> append("; echo @@Q$i; ").append(lenhHoi(l)).append("; echo @@R$i; ").append(lenhChon(l)) }
    }

    /** Tivi mở link thế nào. */
    sealed interface MoBang {
        /** Hộp chọn app của hệ thống (ResolverActivity): tivi hỏi mỗi lần. */
        data object Hoi : MoBang
        data class App(val goi: String) : MoBang
        data class TrinhDuyet(val goi: String) : MoBang
        /** Không app nào mở được (tivi không có trình duyệt). */
        data object KhongCo : MoBang
    }

    /** Một link: các app (không phải trình duyệt) mở được, theo thứ tự tivi trả về, và tivi đang mở bằng gì. */
    data class KetQua(val ungVien: List<String>, val moBang: MoBang)

    data class Quet(val trinhDuyet: Set<String>, val ketQua: List<KetQua>)

    private val THANH_PHAN = Regex("""^\s*([A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+|android)/[A-Za-z0-9_.$]+\s*$""")

    /** Các gói trong chữ query-activities / resolve-activity --brief (dòng "gói/màn"), không lặp. */
    fun goiTrong(chu: String): List<String> = chu.lines().mapNotNull { THANH_PHAN.find(it)?.groupValues?.get(1) }.distinct()

    /** Dòng cuối của resolve-activity --brief → tivi mở bằng gì. */
    fun moBangTu(chu: String, trinhDuyet: Set<String>): MoBang {
        val goi = chu.lines().lastOrNull { it.isNotBlank() }?.let { THANH_PHAN.find(it)?.groupValues?.get(1) } ?: return MoBang.KhongCo
        return when (goi) {
            "android" -> MoBang.Hoi
            in trinhDuyet -> MoBang.TrinhDuyet(goi)
            else -> MoBang.App(goi)
        }
    }

    /** Đọc kết quả lenhQuet cho [soLink] link. */
    fun tuQuet(out: String, soLink: Int): Quet {
        val phan = HashMap<String, StringBuilder>()
        var dang: StringBuilder? = null
        for (dong in out.replace("\r", "").lines()) {
            val m = Regex("""^@@([BQR]\d*)$""").find(dong.trim())
            if (m != null) dang = StringBuilder().also { phan[m.groupValues[1]] = it } else dang?.appendLine(dong)
        }
        val trinhDuyet = goiTrong(phan["B"]?.toString().orEmpty()).toSet()
        return Quet(trinhDuyet, (0 until soLink).map { i ->
            KetQua(goiTrong(phan["Q$i"]?.toString().orEmpty()).filter { it !in trinhDuyet && it != "android" },
                moBangTu(phan["R$i"]?.toString().orEmpty(), trinhDuyet))
        })
    }

    /** Lệnh đặt [goi] luôn mở link của nó trên tivi Android [sdk]. */
    fun lenhDat(sdk: Int, goi: String): String {
        require(TEN_GOI_LK.matches(goi)) { goi }
        return if (sdk >= 31) "pm set-app-links-allowed --user 0 --package $goi true; pm set-app-links-user-selection --user 0 --package $goi true all"
        else "pm set-app-link --user 0 $goi always"
    }

    /**
     * Lệnh trả các app [goi] về mặc định: tivi hỏi chọn app, hoặc app được hãng xác minh trang đó mở như trước khi đặt
     * (Android 12+: bật lại cả quyền mở link tự động đã tắt bằng lenhChan).
     */
    fun lenhBo(sdk: Int, goi: List<String>): String = goi.onEach { require(TEN_GOI_LK.matches(it)) { it } }.joinToString("; ") { g ->
        if (sdk >= 31) "pm set-app-links-user-selection --user 0 --package $g false all; pm set-app-links-allowed --user 0 --package $g true"
        else "pm set-app-link --user 0 $g undefined"
    }

    /** Android 12+: tắt quyền mở link tự động của [goi] (app được hãng xác minh trang thắng lựa chọn của người dùng). */
    fun lenhChan(goi: String): String {
        require(TEN_GOI_LK.matches(goi)) { goi }
        return "pm set-app-links-allowed --user 0 --package $goi false"
    }

    private val TEN_GOI_LK = Regex("""^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$""")

    /** Link / tên miền người dùng gõ → link để thử (https://…/), null nếu không phải trang web. */
    fun chuanHoa(nhap: String): String? {
        var s = nhap.trim()
        if (s.isEmpty() || s.any { it.isWhitespace() || it in "'\"`\\<>{}|^" }) return null
        if (!s.contains("://")) s = "https://$s"
        val u = runCatching { java.net.URI(s) }.getOrNull() ?: return null
        val host = u.host ?: return null
        if (u.scheme?.lowercase() !in setOf("http", "https") || '.' !in host || host.startsWith('.') || host.endsWith('.')) return null
        return if (u.rawPath.isNullOrEmpty()) "$s/" else s
    }

    /** Tên miền của [link] (bỏ www.) để hiện. */
    fun tenMien(link: String): String = runCatching { java.net.URI(link).host?.removePrefix("www.") }.getOrNull() ?: link
}
