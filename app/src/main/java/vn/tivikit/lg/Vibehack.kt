package vn.tivikit.lg

import vn.tivikit.adb.PhimTv

/**
 * Tivi LG đã root (25/10/2026): nhận ra root và cài màn hình chính kiểu Apple TV của LG TV Vibehack
 * (github.com/hoangvya19/lg-tv-webos-vibehack, MIT) qua SSH root. Script cài của dự án luôn lấy nhánh main và bản phát
 * hành mới nhất; repo còn rất mới, đổi liên tục, nên ở đây ghim đúng một bản (BAN, commit COMMIT): script tải về file
 * tạm, sửa nhánh / link gói về bản ghim rồi mới chạy. Phần này chỉ dựng lệnh / đọc kết quả, test được trên máy tính.
 */
object Vibehack {
    const val APP = "com.gathin.tvhome"
    const val HOMEBREW = "org.webosbrew.hbchannel"
    const val INPUT_HOOK = "org.webosbrew.inputhook"
    const val BAN = "v1.2.0"
    private const val COMMIT = "39f0174941516215e76c1070d49d51f4b598f692"
    const val NEN_TAM = "/tmp/tivikit-nen.jpg"
    private const val SCRIPT_TAM = "/tmp/tivikit-vibehack.sh"

    /** Kiểm tra sau khi đăng nhập SSH: root thật, có Homebrew Channel (root), đã cài Vibehack, có LG Input Hook. */
    val LENH_KIEM_TRA = "echo \"UID=\$(id -u)\"; [ -d /var/lib/webosbrew ] && echo HB=1; [ -d /home/root/lg-tv-vibehack ] && echo VH=1; " +
        "[ -d /media/developer/apps/usr/palm/applications/$INPUT_HOOK ] && echo IH=1; true"

    data class KiemTra(val laRoot: Boolean, val coHomebrew: Boolean, val coVibehack: Boolean, val coInputHook: Boolean)

    fun docKiemTra(out: String): KiemTra {
        val dong = out.lines().map { it.trim() }
        return KiemTra("UID=0" in dong, "HB=1" in dong, "VH=1" in dong, "IH=1" in dong)
    }

    /** Dòng đầu cổng 22 là lời chào của máy chủ SSH ("SSH-2.0-dropbear_2022.83"). */
    fun laSsh(dongDau: String?): Boolean = dongDau?.startsWith("SSH-") == true

    data class TuyChon(
        val tiengViet: Boolean = true, val dongHo24: Boolean = true, val cot: Int = 6, val thanhPho: String = "Ho Chi Minh City",
        val nutHome: Boolean = true, val coAnhNen: Boolean = false, val boAnhNen: Boolean = false,
    )

    /** Lệnh cài / cập nhật (chạy bằng root trên tivi), kèm tuỳ chọn. Thành phố bỏ dấu: script tra Open-Meteo theo tên không dấu. */
    fun lenhCai(tc: TuyChon): String {
        val ts = mutableListOf("--lang", if (tc.tiengViet) "vi" else "en", "--clock24", if (tc.dongHo24) "on" else "off", "--columns", tc.cot.coerceIn(3, 10).toString())
        val tp = PhimTv.boDau(tc.thanhPho).trim()
        if (tp.isEmpty()) ts += "--no-weather" else ts += listOf("--city", tp)
        if (tc.coAnhNen) ts += listOf("--wallpaper", NEN_TAM) else if (tc.boAnhNen) ts += "--no-wallpaper"
        if (!tc.nutHome) ts += "--no-home-button"
        return chayScript(ts)
    }

    fun lenhGo(): String = chayScript(listOf("--uninstall"))

    /** Tải script của bản ghim về file tạm, sửa nhánh và link gói về bản ghim, chạy với [ts]; lỗi ở bước nào thì mã thoát khác 0. */
    private fun chayScript(ts: List<String>): String {
        val url = "https://raw.githubusercontent.com/hoangvya19/lg-tv-webos-vibehack/$COMMIT/install.sh"
        return "{ curl -fsSL $url -o $SCRIPT_TAM && " +
            "sed -i -e 's#^BRANCH=\"main\"#BRANCH=\"$COMMIT\"#' -e 's#releases/latest/download#releases/download/$BAN#' $SCRIPT_TAM && " +
            "sh $SCRIPT_TAM ${ts.joinToString(" ") { trich(it) }}; kq=\$?; rm -f $SCRIPT_TAM; exit \$kq; } 2>&1"
    }

    /** Bọc nháy đơn cho shell. */
    fun trich(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    /** Bỏ mã màu ANSI trong chữ script in ra ("\u001B[1;36m==>\u001B[0m"). */
    fun boMau(s: String): String = s.replace(Regex("\u001B\\[[0-9;]*m"), "")
}
