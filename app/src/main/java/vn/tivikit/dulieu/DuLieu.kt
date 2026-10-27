package vn.tivikit.dulieu

import android.content.Context
import org.json.JSONObject

/** Một gói trong danh sách mẫu của cleanyourtv. */
data class GoiMau(val pkg: String, val nhom: String, val ghiChu: String)

/**
 * Một mẫu tivi đã có người thử (examples/<id>/debloat.txt). [phim]: phím remote của mẫu lấy từ config.json cạnh đó
 * (app_keys, settings_key_keycodes…), null nếu mẫu không có.
 */
data class MauTv(val id: String, val ten: String, val header: String, val goi: List<GoiMau>, val phim: org.json.JSONObject? = null)

/** Thống kê toàn cục: gói này bị tắt ở bao nhiêu mẫu, bao nhiêu mẫu ghi là phải giữ. */
data class ThongKeGoi(val soMauTat: Int, val soMauGiu: Int)

class DuLieu(val mau: List<MauTv>, val thongKe: Map<String, ThongKeGoi>) {

    /** Ghi chú đầu tiên có người viết cho mỗi gói (không có thì lấy tên nhóm). */
    val ghiChu: Map<String, String> by lazy {
        val m = HashMap<String, String>()
        for (t in mau) for (g in t.goi) if (g.ghiChu.isNotBlank()) m.putIfAbsent(g.pkg, g.ghiChu)
        for (t in mau) for (g in t.goi) if (g.nhom.isNotBlank()) m.putIfAbsent(g.pkg, g.nhom)
        m
    }

    companion object {
        /** Số mẫu tivi trong assets/du-lieu.json, để ghi lên giao diện mà không phải nạp dữ liệu (KeHoachTest kiểm tra). */
        const val SO_MAU = 316

        @Volatile private var cache: DuLieu? = null

        fun nap(context: Context): DuLieu = cache ?: synchronized(this) {
            cache ?: docJson(vn.tivikit.CapNhat.moAsset(context, "du-lieu.json").bufferedReader().readText()).also { cache = it }
        }

        fun docJson(text: String): DuLieu {
            val root = JSONObject(text)
            val arr = root.getJSONArray("mau")
            val mau = (0 until arr.length()).map { i ->
                val m = arr.getJSONObject(i)
                val g = m.getJSONArray("goi")
                MauTv(
                    id = m.getString("id"),
                    ten = m.getString("ten"),
                    header = m.getString("header"),
                    goi = (0 until g.length()).map { j ->
                        val o = g.getJSONObject(j)
                        GoiMau(o.getString("p"), o.optString("g"), o.optString("n"))
                    },
                    phim = m.optJSONObject("phim"),
                )
            }
            val goi = root.getJSONObject("goi")
            val tk = HashMap<String, ThongKeGoi>()
            goi.keys().forEach { k ->
                val o = goi.getJSONObject(k)
                tk[k] = ThongKeGoi(o.optInt("c"), o.optInt("giu"))
            }
            return DuLieu(mau, tk)
        }
    }

    /**
     * Tìm mẫu giống tivi này nhất bằng cách dò tên máy (ro.product.*) trong phần đầu danh sách.
     * Trả về tối đa 6 mẫu có điểm, cao trước.
     */
    fun khopMau(tv: ThongTinTv): List<Pair<MauTv, Int>> {
        val tieuChi = listOf(
            tv.device to 4, tv.productName to 4, tv.buildProduct to 2, tv.model to 3,
        ).filter { (v, _) -> v.length >= 3 && v.lowercase() !in TU_CHUNG }
        val hang = tv.brand.lowercase()
        return mau.map { m ->
            val dau = m.header.lines().take(8).joinToString("\n").lowercase()
            var diem = 0
            val daDung = HashSet<String>()
            for ((v, w) in tieuChi) {
                val key = v.lowercase()
                if (key in daDung) continue
                if (Regex("(?<![a-z0-9_])" + Regex.escape(key) + "(?![a-z0-9_])").containsMatchIn(dau)) {
                    diem += w
                    daDung += key
                }
            }
            if (diem > 0 && hang.isNotEmpty() && m.id.startsWith(hang.take(4))) diem += 1
            m to diem
        }.filter { it.second >= 3 }.sortedByDescending { it.second }.take(6)
    }

    /**
     * Mẫu cùng hãng có tên máy gần giống (Sony BRAVIA_VU3_4K ↔ VU31, 23/10/2026 góp ý Luongnam1020): khopMau đòi
     * trùng nguyên chữ nên bỏ sót. Chỉ để GỢI Ý cho người dùng chọn, không tự chọn. Tối đa 3, trừ mẫu trong [bo].
     */
    fun ganGiong(tv: ThongTinTv, bo: Set<String> = emptySet()): List<MauTv> {
        val hang = tv.brand.lowercase().take(4)
        if (hang.length < 3) return emptyList()
        val cuaTv = listOf(tv.device, tv.model, tv.productName).flatMap(::maMay).toSet()
        if (cuaTv.isEmpty()) return emptyList()
        return mau.filter { it.id.startsWith(hang) && it.id !in bo }.map { m ->
            val cuaMau = maMay(m.header.lines().take(8).joinToString(" "))
            m to cuaTv.count { a -> cuaMau.any { b -> a != b && kotlin.math.abs(a.length - b.length) <= 2 && (a.startsWith(b) || b.startsWith(a)) } }
        }.filter { it.second > 0 }.sortedByDescending { it.second }.take(3).map { it.first }
    }
}

/** Chữ giống mã máy: chữ + số, ≥ 3 ký tự, có ít nhất một số (vu3, 55x85j, mosr4). */
private fun maMay(s: String): List<String> =
    s.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length >= 3 && it.any(Char::isDigit) && it !in TU_CHUNG }

private val TU_CHUNG = setOf("smart tv", "android tv", "google tv", "tv", "atv", "mt5867", "mt9221", "full_", "generic")

/** Những gì đọc được từ getprop. */
data class ThongTinTv(
    val brand: String,
    val manufacturer: String,
    val model: String,
    val device: String,
    val productName: String,
    val buildProduct: String,
    val android: String,
    val sdk: Int,
    val serial: String,
    val adbSecure: String,
) {
    val tenHienThi: String get() = listOf(manufacturer.ifBlank { brand }, model).filter { it.isNotBlank() }.joinToString(" ")

    /** Khoá lưu bản sao lưu: số serial, không có thì ghép model + device. */
    val khoaLuu: String get() = (serial.ifBlank { "$model-$device" }).replace(Regex("[^A-Za-z0-9._-]"), "_")

    companion object {
        /** Bảng khoá → giá trị từ đầu ra getprop ("[ro.product.model]: [MiTV-MOOQ1]"). */
        fun bangGetprop(text: String): Map<String, String> =
            Regex("""\[([^\]]+)\]: \[([^\]]*)\]""").findAll(text).associate { it.groupValues[1] to it.groupValues[2] }

        fun tuGetprop(text: String): ThongTinTv {
            val p = bangGetprop(text)
            fun g(vararg k: String) = k.firstNotNullOfOrNull { p[it]?.takeIf { v -> v.isNotBlank() } } ?: ""
            return ThongTinTv(
                brand = g("ro.product.brand", "ro.product.vendor.brand", "ro.product.system.brand"),
                manufacturer = g("ro.product.manufacturer", "ro.product.vendor.manufacturer"),
                model = g("ro.product.model", "ro.product.vendor.model"),
                device = g("ro.product.device", "ro.product.vendor.device"),
                productName = g("ro.product.name", "ro.product.vendor.name"),
                buildProduct = g("ro.build.product"),
                android = g("ro.build.version.release"),
                sdk = g("ro.build.version.sdk").toIntOrNull() ?: 0,
                serial = g("ro.serialno", "ro.boot.serialno"),
                adbSecure = g("ro.adb.secure"),
            )
        }
    }
}

/** Tên dễ đọc cho các app hay gặp (shell không lấy được nhãn app). */
val TEN_APP = mapOf(
    "com.netflix.ninja" to "Netflix",
    "com.netflix.mediaclient" to "Netflix",
    "com.google.android.youtube.tv" to "YouTube",
    "com.google.android.youtube.tvkids" to "YouTube Kids",
    "com.google.android.youtube.tvmusic" to "YouTube Music",
    "org.smarttube.stable" to "SmartTube",
    "org.smarttube.beta" to "SmartTube Beta",
    "app.smarttube.fdroid" to "SmartTube (F-Droid)",
    "app.smarttube" to "SmartTube",
    "com.teamsmart.videomanager.tv" to "SmartTube",
    "com.liskovsoft.smarttubetv.beta" to "SmartTube Beta",
    "com.liskovsoft.smarttubetv" to "SmartTube",
    "org.smartteam.smarttube.tv.orig" to "SmartTube",
    "com.viettel.tv360.tv" to "TV360",
    "vn.tivikit.kenh" to "Kênh nhanh (TiviKit)",
    "vn.tivikit.hengio" to "Hẹn giờ (TiviKit)",
    "vn.tivikit.khoa" to "Khoá trẻ em (TiviKit)",
    "vn.tivikit.go" to "Gõ tiếng Việt (TiviKit)",
    // App phụ trước khi đổi tên gói (25/10/2026), còn trên tivi tới khi được chuyển.
    "com.icm.cleantv.kenh" to "Kênh nhanh (TiviKit cũ)",
    "com.icm.cleantv.hengio" to "Hẹn giờ (TiviKit cũ)",
    "com.icm.cleantv.khoa" to "Khoá trẻ em (TiviKit cũ)",
    "com.google.android.videos" to "Google TV / Play Phim",
    "com.google.android.play.games" to "Google Play Games",
    "com.amazon.amazonvideo.livingroom" to "Prime Video",
    "com.disney.disneyplus" to "Disney+",
    "com.spotify.tv.android" to "Spotify",
    "com.apple.atve.androidtv.appletv" to "Apple TV",
    "com.fptplay.tv" to "FPT Play",
    "net.fptplay.ottbox" to "FPT Play",
    "com.vieon.tv" to "VieON",
    "vn.vieon.tv" to "VieON",
    "com.viettel.tv360" to "TV360",
    "com.viettel.tv360.tv" to "TV360",
    "com.vnpt.mytv" to "MyTV",
    "vn.vnpt.mytv.atv" to "MyTV",
    "com.galaxyplay.tv" to "Galaxy Play",
    "com.fptplay.galaxyplay" to "Galaxy Play",
    "com.vtvcab.on" to "VTVcab ON",
    "vn.vtv.vtvgo" to "VTVgo",
    "vn.vtv.vtvgotv" to "VTVgo",
    "com.kplus.tv" to "K+",
    "com.clip.tv" to "Clip TV",
    "org.xbmc.kodi" to "Kodi",
    "com.google.android.apps.mediashell" to "Chromecast (truyền từ điện thoại)",
    "com.google.android.katniss" to "Trợ lý Google (giọng nói)",
    "com.google.android.apps.tv.dreamx" to "Màn hình chờ Google",
    "com.google.android.backdrop" to "Màn hình chờ ảnh nền",
    "com.android.vending" to "CH Play",
    "com.google.android.tvlauncher" to "Màn hình Home gốc (Android TV)",
    "com.google.android.apps.tv.launcherx" to "Màn hình Home gốc (Google TV)",
    "com.gathin.tvhome" to "Home sạch (CleanYourTV)",
    "com.ts.launcher" to "Home sạch (bản cho Xiaomi)",
    // Trình duyệt và app hay nhận link (màn Set App Link, 27/10/2026).
    "com.coccoc.trinhduyet_tv" to "Cốc Cốc",
    "com.phlox.tvwebbrowser" to "TV Bro",
    "com.android.chrome" to "Chrome",
    "org.mozilla.tv.firefox" to "Firefox",
    "com.cloudmosa.puffinTV" to "Puffin TV",
    "com.google.android.tv.frameworkpackagestubs" to "Trình duyệt trống của Google TV",
    "com.spotify.music" to "Spotify",
    "com.valvesoftware.steamlink" to "Steam Link",
    "com.iqiyi.i18n.tv" to "iQIYI",
    "com.irdeto.kplus" to "K+",
    "org.videolan.vlc" to "VLC",
)

private val TEN_APP_EN = mapOf(
    "com.google.android.videos" to "Google TV / Play Movies",
    "com.google.android.apps.mediashell" to "Chromecast (cast from phone)",
    "com.google.android.katniss" to "Google Assistant (voice)",
    "com.google.android.apps.tv.dreamx" to "Google screensaver",
    "com.google.android.backdrop" to "Backdrop screensaver",
    "com.android.vending" to "Play Store",
    "com.google.android.tvlauncher" to "Stock Home (Android TV)",
    "com.google.android.apps.tv.launcherx" to "Stock Home (Google TV)",
    "com.gathin.tvhome" to "Clean Home (CleanYourTV)",
    "com.ts.launcher" to "Clean Home (Xiaomi build)",
    "vn.tivikit.kenh" to "Quick channel (TiviKit)",
    "vn.tivikit.hengio" to "Timer (TiviKit)",
    "vn.tivikit.khoa" to "Kids lock (TiviKit)",
    "vn.tivikit.go" to "Vietnamese typing (TiviKit)",
    "com.icm.cleantv.kenh" to "Quick channel (old TiviKit)",
    "com.icm.cleantv.hengio" to "Timer (old TiviKit)",
    "com.icm.cleantv.khoa" to "Kids lock (old TiviKit)",
    "com.google.android.tv.frameworkpackagestubs" to "Google TV's empty browser",
)

fun tenDep(pkg: String): String = (if (!vn.tivikit.NgonNgu.tiengViet) TEN_APP_EN[pkg] else null) ?: TEN_APP[pkg] ?: pkg.substringAfterLast('.')
    .replace('_', ' ').replaceFirstChar { it.uppercase() }
