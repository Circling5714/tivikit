package vn.tivikit

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Tự cập nhật TiviKit (26/10/2026) từ GitHub Releases của [KHO]: mỗi bản phát hành đính kèm cap-nhat.json (số bản, link
 * APK, cỡ, SHA-256, nhật ký); link releases/latest/download luôn trỏ tới bản mới nhất (không tính lượt gọi API). Tải APK,
 * kiểm tra cỡ + SHA-256 rồi cài bằng PackageInstaller: Android 12+ cho app tự cập nhật chính nó không cần bấm, máy cũ
 * hơn mở hộp cài của Android. Android chỉ cài đè khi APK cùng chữ ký, nên bản giả không cài được.
 * Phần dữ liệu OTA của CleanTV ("duLieu" trong cap-nhat.json: danh sách mẫu tivi, APK app phụ tải lặng lẽ) đã bỏ
 * (28/10/2026): bản phát hành không dùng tới, mà nó thay được app phụ cài lên tivi chỉ với kiểm tra cỡ file, tên file lấy
 * từ JSON làm đường dẫn. App phụ, dữ liệu đi theo bản phát hành (ký, kiểm SHA-256).
 */
object CapNhat {
    const val KHO = "Circling5714/tivikit"
    const val GOC = "https://github.com/$KHO/releases/latest/download"
    /** Trang các bản phát hành, mỗi bản kèm nhật ký. */
    const val TRANG = "https://github.com/$KHO/releases"

    /** [co]: cỡ APK (byte, 0 = không ghi), [sha256]: mã băm APK, để chắc file tải về đúng là bản đã phát hành. */
    data class BanMoi(val versionCode: Int, val versionName: String, val apk: String, val ghiChu: String, val co: Long, val sha256: String)

    /** App đang hiện trên màn hình (MainActivity onStart / onStop): đang mở thì không tự cài, tránh app bị đóng ngang. */
    @Volatile var dangMo = false

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("ota", Context.MODE_PRIVATE)

    /** Tự tải và cài bản mới lúc không mở app (bật sẵn, tắt ở mục Cập nhật TiviKit). */
    fun tuCai(ctx: Context) = prefs(ctx).getBoolean("tu_cai", true)

    fun datTuCai(ctx: Context, bat: Boolean) = prefs(ctx).edit().putBoolean("tu_cai", bat).apply()

    /** Chạy nền có nên tự cài [b] không: Android 12+, người dùng không tắt, app không đang mở, Android chưa đòi xác nhận bản này. */
    fun nenTuCai(ctx: Context, b: BanMoi) = BuildConfig.TU_CAP_NHAT && Build.VERSION.SDK_INT >= 31 && tuCai(ctx) && !dangMo &&
        prefs(ctx).getInt("can_bam", 0) < b.versionCode

    /** Dữ liệu OTA tải về từ bản cũ (đã bỏ, xem trên): app lên bản mới thì xoá. */
    fun donOtaCuNeuDoiBan(ctx: Context) {
        val p = prefs(ctx)
        if (p.getInt("cho_ban", -1) == BuildConfig.VERSION_CODE) return
        File(ctx.filesDir, "ota").deleteRecursively()
        p.edit().putInt("cho_ban", BuildConfig.VERSION_CODE).apply()
    }

    fun moAsset(ctx: Context, ten: String): java.io.InputStream = ctx.assets.open(ten)

    private fun tai(url: String, giay: Int = 20): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = giay * 1000; c.readTimeout = giay * 1000
        c.setRequestProperty("Cache-Control", "no-cache")
        try {
            if (c.responseCode != 200) error("HTTP ${c.responseCode} $url")
            return c.inputStream.use { it.readBytes() }
        } finally { c.disconnect() }
    }

    /** Bản app mới trong cap-nhat.json của bản phát hành mới nhất (null nếu không mới hơn bản đang chạy). */
    fun kiemTraApp(): BanMoi? =
        if (!BuildConfig.TU_CAP_NHAT) null else docBanMoi(JSONObject(String(tai("$GOC/cap-nhat.json"), Charsets.UTF_8)), BuildConfig.VERSION_CODE)

    /** Bản trong cap-nhat.json nếu cao hơn [maHienTai] và có SHA-256 hợp lệ (không có mã thì không kiểm được file: bỏ qua). */
    fun docBanMoi(j: JSONObject, maHienTai: Int): BanMoi? {
        val app = j.optJSONObject("app") ?: return null
        val code = app.optInt("versionCode")
        val sha = app.optString("sha256").lowercase()
        if (code <= maHienTai || !Regex("^[0-9a-f]{64}$").matches(sha)) return null
        val apk = app.optString("apk").takeIf { it.isNotBlank() } ?: return null
        return BanMoi(code, app.optString("versionName"), if (apk.startsWith("http")) apk else "$GOC/${apk.removePrefix("/")}",
            t(app.optString("ghiChu"), app.optString("ghiChuEn").ifBlank { app.optString("ghiChu") }), app.optLong("co"), sha)
    }

    /** [b] đúng là APK của bản [ban]: đúng cỡ (nếu cap-nhat.json ghi) và đúng SHA-256. */
    fun hopLe(b: ByteArray, ban: BanMoi): Boolean = (ban.co <= 0 || b.size.toLong() == ban.co) && vn.tivikit.dulieu.sha256(b) == ban.sha256

    /** Người dùng chạm thông báo "có bản mới" để mở app → tải và cài luôn, khỏi hỏi lại. */
    @Volatile var moTuThongBao = false

    /** Tải APK bản mới vào cache (tải sẵn rồi mà còn đúng thì dùng lại) và kiểm tra; sai cỡ / SHA-256 thì báo lỗi, không cài. */
    fun taiVe(ctx: Context, ban: BanMoi): File {
        // Tên file theo số bản (versionCode), không lấy versionName của JSON làm đường dẫn.
        val f = File(ctx.cacheDir, "cap-nhat/TiviKit-${ban.versionCode}.apk").apply { parentFile?.mkdirs() }
        if (f.isFile && hopLe(f.readBytes(), ban)) return f
        val b = tai(ban.apk, 180)
        if (!hopLe(b, ban)) error(t("file tải về không khớp bản đã phát hành (cỡ / SHA-256)", "the download doesn't match the release (size / SHA-256)"))
        f.parentFile?.listFiles()?.forEach { it.delete() } // APK bản cũ hơn tải lúc trước
        f.writeBytes(b)
        return f
    }

    /**
     * Cài [f] bằng PackageInstaller. Android 12+: không cần người dùng bấm (app tự cập nhật chính nó, có quyền
     * UPDATE_PACKAGES_WITHOUT_USER_ACTION). Android vẫn đòi xác nhận thì [KetQuaCai] mở hộp cài ([ngam] = false, app đang
     * mở) hoặc mời cài bằng thông báo ([ngam] = true: chạy nền, Android 10+ không cho mở màn hình từ nền).
     */
    fun cai(ctx: Context, ban: BanMoi, f: File, ngam: Boolean) {
        val pi = ctx.packageManager.packageInstaller
        val ts = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(ctx.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = pi.createSession(ts)
        pi.openSession(id).use { s ->
            f.inputStream().use { i -> s.openWrite("TiviKit.apk", 0, f.length()).use { o -> i.copyTo(o); s.fsync(o) } }
            val y = Intent(ctx, KetQuaCai::class.java).putExtra(KetQuaCai.NGAM, ngam)
                .putExtra(KetQuaCai.MA, ban.versionCode).putExtra(KetQuaCai.TEN, ban.versionName).putExtra(KetQuaCai.GHI_CHU, ban.ghiChu)
            // FLAG_MUTABLE: hệ thống ghi kết quả (EXTRA_STATUS, hộp cài…) vào intent này.
            s.commit(PendingIntent.getBroadcast(ctx, id, y, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE).intentSender)
        }
    }

    /** Android đòi bấm xác nhận khi cài bản [ma]: lần chạy nền sau không thử tự cài bản đó nữa, chỉ mời bằng thông báo. */
    fun ghiCanBam(ctx: Context, ma: Int) = prefs(ctx).edit().putInt("can_bam", ma).apply()
}

/** Kết quả cài bản mới (CapNhat.cai). Cài xong lúc chạy nền thì app đã là bản mới khi nhận được. */
class KetQuaCai : BroadcastReceiver() {
    override fun onReceive(ctx: Context, y: Intent) {
        NgonNgu.nap(ctx)
        val ban = CapNhat.BanMoi(y.getIntExtra(MA, 0), y.getStringExtra(TEN) ?: "", "", y.getStringExtra(GHI_CHU) ?: "", 0, "")
        val ngam = y.getBooleanExtra(NGAM, false)
        when (y.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> if (ngam) {
                CapNhat.ghiCanBam(ctx, ban.versionCode)
                ThongBaoCapNhat.bao(ctx, ban)
            } else {
                val hop = if (Build.VERSION.SDK_INT >= 33) y.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                    else @Suppress("DEPRECATION") y.getParcelableExtra(Intent.EXTRA_INTENT)
                hop?.let { runCatching { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
            }
            PackageInstaller.STATUS_SUCCESS -> if (ngam) ThongBaoCapNhat.baoDaCai(ctx, ban)
            // Cài nền hỏng (máy hết chỗ…): mời cập nhật bằng thông báo, không thì người dùng không biết có bản mới.
            else -> if (!ngam) Toast.makeText(ctx, t("Cài bản mới không được: ", "Couldn't install the update: ") +
                (y.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""), Toast.LENGTH_LONG).show()
                else ThongBaoCapNhat.bao(ctx, ban)
        }
    }

    companion object {
        const val NGAM = "ngam"
        const val MA = "ma"
        const val TEN = "ten"
        const val GHI_CHU = "ghi_chu"
    }
}
