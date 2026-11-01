package vn.tivikit.dulieu

import vn.tivikit.t

/**
 * Tiện ích của TiviKit trên tivi (1/11/2026): các app phụ đi kèm TiviKit (Khoá trẻ em, Hẹn giờ, Kênh nhanh, Cast, Map
 * Voice, Tele Remote, bộ gõ, TiviKit Launcher, Home sạch). Trước đây mỗi cái chỉ lên bản mới khi mở đúng màn của nó rồi
 * lưu; nay nối tivi là so cả lượt với bản đi kèm, màn chính hiện "N tiện ích có bản mới", cập nhật một lần (hay tự làm).
 */
object TienIch {
    enum class Loai { KHOA, HEN_GIO, KENH, CAST, MAP_VOICE, TELE_REMOTE, GO, TKL, HOME }

    /** Một tiện ích: [goi] trên tivi, [apk] trong assets của TiviKit, [ten] hiện cho người dùng. */
    data class Muc(val loai: Loai, val goi: String, val apk: String, val ten: String)

    /** Bản của một APK: versionCode, versionName, SHA-256 (chữ thường). */
    data class Ban(val so: Long, val ten: String, val sha: String)

    /** Tiện ích trên tivi khác bản đi kèm. */
    data class CanCapNhat(val muc: Muc, val trenTv: Ban, val diKem: Ban)

    /**
     * Mọi tiện ích, theo thứ tự cập nhật: launcher sau cùng (cài đè launcher có thể làm màn hình chuyển). Home sạch có gói
     * và file tuỳ tivi ([goiHome], [apkHome]: bản com.ts.launcher cho Xiaomi Trung Quốc).
     */
    fun ds(goiHome: String, apkHome: String): List<Muc> = listOf(
        Muc(Loai.KHOA, GOI_KHOA, "khoa.apk", t("Khoá trẻ em", "Kids lock")),
        Muc(Loai.HEN_GIO, HenGio.GOI, "hengio.apk", t("Hẹn giờ", "Timer")),
    ) + (1..KenhNhanh.SO_O).map { Muc(Loai.KENH, KenhNhanh.goiCua(it), KenhNhanh.apkCua(it), t("Kênh nhanh $it", "Quick channel $it")) } + listOf(
        Muc(Loai.CAST, CastSmartTube.GOI, "cast.apk", "Cast SmartTube"),
        Muc(Loai.MAP_VOICE, MapVoice.GOI, MapVoice.APK, "Map Voice"),
        Muc(Loai.TELE_REMOTE, TeleRemote.GOI, TeleRemote.APK, "Tele Remote"),
        Muc(Loai.GO, GoChu.GOI, "go.apk", t("Bộ gõ tiếng Việt", "Vietnamese typing")),
        Muc(Loai.TKL, TiviKitLauncher.GOI, TiviKitLauncher.APK, "TiviKit Launcher"),
        Muc(Loai.HOME, goiHome, apkHome, t("Home sạch", "Clean Home")),
    )

    /** Như ThaoTacTv.PKG_KHOA (gói app Khoá trẻ em). */
    const val GOI_KHOA = "vn.tivikit.khoa"

    /**
     * Cần cập nhật: tivi có tiện ích này, khác SHA-256 bản đi kèm, và bản đi kèm không cũ hơn (không hạ một bản thử mới hơn
     * đã cài tay; đọc không ra số bản đi kèm thì cũng thôi).
     */
    fun canCapNhat(trenTv: Ban?, diKem: Ban): Boolean = trenTv != null && diKem.so > 0 && trenTv.sha != diKem.sha && diKem.so >= trenTv.so

    /** Dòng hiện trên thẻ: "Tele Remote 1.0 → 1.0.1"; cùng số bản (bản dựng lại) thì "Home sạch 3.0 (bản dựng mới)". */
    fun moTa(c: CanCapNhat): String =
        if (c.trenTv.ten == c.diKem.ten) "${c.muc.ten} ${c.diKem.ten} " + t("(bản dựng mới)", "(new build)")
        else "${c.muc.ten} ${c.trenTv.ten.ifEmpty { "?" }} → ${c.diKem.ten}"

    private val TEN_GOI = Regex("""^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$""")
    private val SHA = Regex("^[0-9a-f]{64}$")

    /**
     * Lệnh đọc bản của các gói [goi] trên tivi, cả lượt trong một lệnh: mỗi gói đã cài một dòng
     * "TI|gói|sha256 của APK|dòng versionCode= … versionName=…" (gói chưa cài thì không có dòng).
     */
    fun lenhDoc(goi: List<String>): String {
        val ds = goi.filter { TEN_GOI.matches(it) }
        if (ds.isEmpty()) return "true"
        return "for p in ${ds.joinToString(" ")}; do " +
            "f=\$(pm path \$p 2>/dev/null | head -n 1 | sed 's/^package://'); [ -n \"\$f\" ] || continue; " +
            "s=\$(sha256sum \"\$f\" 2>/dev/null | cut -d ' ' -f 1); " +
            "v=\$(dumpsys package \$p | grep -m 2 -E 'versionCode=|versionName=' | tr '\\n' ' '); " +
            "echo \"TI|\$p|\$s|\$v\"; done"
    }

    /** Đọc kết quả của [lenhDoc]: gói → bản trên tivi (dòng hỏng, thiếu SHA hay versionCode thì bỏ). */
    fun docKetQua(out: String): Map<String, Ban> = out.lines().mapNotNull { l ->
        val p = l.trim().split('|')
        if (p.size < 4 || p[0] != "TI" || !TEN_GOI.matches(p[1])) return@mapNotNull null
        val sha = p[2].trim().takeIf { SHA.matches(it) } ?: return@mapNotNull null
        val so = Regex("""versionCode=(\d+)""").find(p[3])?.groupValues?.get(1)?.toLongOrNull() ?: return@mapNotNull null
        p[1] to Ban(so, Regex("""versionName=(\S+)""").find(p[3])?.groupValues?.get(1).orEmpty(), sha)
    }.toMap()
}
