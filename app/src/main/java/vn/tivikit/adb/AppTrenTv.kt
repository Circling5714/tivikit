package vn.tivikit.adb

import vn.tivikit.dulieu.ChayNgam
import vn.tivikit.dulieu.LamMoi
import vn.tivikit.dulieu.TenApk
import java.io.ByteArrayOutputStream
import java.io.OutputStream

/** Một app trên tivi (màn Quản lý app): phiên bản và tổng cỡ các file APK. */
data class AppTv(val pkg: String, val versionName: String, val versionCode: Long, val coApk: Long)

/** Bộ nhớ /data của tivi, KB. */
data class BoNho(val tongKb: Long, val dungKb: Long, val conKb: Long)

/**
 * Tình trạng tivi cho màn Làm mới: RAM, bộ nhớ /data, số giây từ lần khởi động gần nhất, tải CPU 1 phút, số nhân, nhiệt độ
 * chip (°C), tiến trình ăn CPU nhiều nhất (26/10/2026), phần trăm CPU bận đo trong 1 giây (/proc/stat, 27/10/2026);
 * null / rỗng = không đọc được.
 */
data class TinhTrangTv(
    val ram: LamMoi.Ram?, val boNho: BoNho?, val chayGiay: Long?,
    val tai: Double? = null, val soNhan: Int? = null, val nhietDo: Int? = null, val anCpu: List<LamMoi.TienTrinh> = emptyList(),
    val cpuBan: Int? = null,
)

/** Cỡ dữ liệu theo `dumpsys diskstats` (byte; Android tự đo mỗi ngày một lần nên có thể cũ). */
data class CoDuLieu(val app: Long, val duLieu: Long, val cache: Long)

/**
 * Quản lý app trên tivi (25/10/2026): danh sách, dung lượng, buộc dừng, xoá cache / dữ liệu, dọn cache cả máy, tải APK
 * về điện thoại, đọc từng đoạn file (cho TenApk). Gỡ app / mở app dùng ThaoTacTv.goApp / moApp.
 */
class AppTrenTv(private val adb: KetNoiAdb) {
    private fun sh(lenh: String, giay: Int = 120) = adb.shell(lenh, giay).replace("\r", "")

    fun boNho(): BoNho? = tuDf(sh("df -k /data"))

    /**
     * RAM, bộ nhớ, thời gian chạy, tải CPU, nhiệt độ, ai đang ăn CPU và CPU bận trong 1 giây (hai dòng /proc/stat cách nhau
     * 1 giây, kèm utime / stime của chính TiviKit nếu nó chạy trên tivi để trừ ra), một lệnh shell (màn Làm mới tivi).
     */
    fun tinhTrang(): TinhTrangTv = tuTinhTrang(sh("cat /proc/meminfo; echo @@; cat /proc/uptime; echo @@; df -k /data; echo @@; " +
        "cat /proc/loadavg; echo @@; cat /sys/devices/system/cpu/possible; echo @@; cat /sys/class/thermal/thermal_zone0/temp 2>/dev/null; " +
        "echo @@; dumpsys cpuinfo 2>/dev/null | head -n 30; echo @@; p=\$(pidof ${vn.tivikit.BuildConfig.APPLICATION_ID} | cut -d' ' -f1); " +
        "for i in 1 2; do head -n 1 /proc/stat; [ -n \"\$p\" ] && cut -d' ' -f14,15 /proc/\$p/stat 2>/dev/null; [ \$i = 1 ] && sleep 1; done; true"))

    /**
     * Đóng các app đang nằm chờ trong bộ nhớ: `am kill-all` chỉ đóng tiến trình nền an toàn để đóng — không đụng app đang
     * mở, Home, dịch vụ trợ năng (phím Home sạch, Hẹn giờ, Khoá trẻ em).
     */
    fun dongAppNen() { sh("am kill-all") }

    fun goiNguoiDung(): Set<String> = sh("pm list packages -3").lines().mapNotNull { l ->
        l.trim().removePrefix("package:").takeIf { ThaoTacTv.TEN_GOI.matches(it) }
    }.toSet()

    /** Phiên bản + cỡ APK của các gói, một lệnh shell cho cả danh sách (mỗi gói một dumpsys, vài chục ms). */
    fun danhSach(goi: Collection<String>): List<AppTv> {
        val ds = goi.filter { ThaoTacTv.TEN_GOI.matches(it) }
        if (ds.isEmpty()) return emptyList()
        return tuDanhSach(sh(
            "for p in ${ds.joinToString(" ")}; do s=0; for f in \$(pm path \$p | cut -d: -f2); do s=\$((s + \$(stat -c %s \$f))); done; " +
                "echo \"@@ \$p|\$s\"; dumpsys package \$p | grep -m 2 -E 'versionCode=|versionName='; done", 300))
    }

    fun coDuLieu(): Map<String, CoDuLieu> = tuDiskstats(sh("dumpsys diskstats"))

    /** App đang chạy (RAM gộp theo gói, nhiều trước) và mọi gói đã cài, một lệnh shell (Quản lý app, 26/10/2026). */
    fun chayNgam(): Pair<List<ChayNgam.App>, Set<String>> = tuChayNgam(sh("ps -A -o RSS,NAME 2>/dev/null; echo @@; pm list packages"))

    /** Mọi gói đang tắt (do hãng, luồng dọn hay người dùng). */
    fun goiDangTat(): Set<String> = sh("pm list packages -d").lines().mapNotNull { l ->
        l.trim().removePrefix("package:").takeIf { ThaoTacTv.TEN_GOI.matches(it) }
    }.toSet()

    /** Các file APK của gói (base + split) kèm cỡ. */
    fun fileApk(pkg: String): List<Pair<String, Long>> {
        require(ThaoTacTv.TEN_GOI.matches(pkg)) { "pkg" }
        return sh("for f in \$(pm path $pkg | cut -d: -f2); do echo \"\$(stat -c %s \$f) \$f\"; done").lines().mapNotNull { l ->
            val co = l.trim().substringBefore(' ').toLongOrNull() ?: return@mapNotNull null
            val p = l.trim().substringAfter(' ')
            if (p.startsWith("/") && !p.contains('\'')) p to co else null
        }
    }

    /**
     * Đọc đoạn [tu, tu+co) của file trên tivi bằng dd khối 4 KB (toybox dd không có skip theo byte), rồi cắt phần thừa.
     * [coFile] để biết dd trả về bao nhiêu byte ở cuối file.
     */
    fun docDoan(duongDan: String, coFile: Long): TenApk.DocDoan {
        require(!duongDan.contains('\'')) { "duongDan" }
        return TenApk.DocDoan { tu, co ->
            val n = minOf(co.toLong(), coFile - tu).toInt()
            if (n <= 0) return@DocDoan ByteArray(0)
            val bs = 4096
            val boQua = tu / bs
            val lech = (tu % bs).toInt()
            val khoi = (lech + n + bs - 1) / bs
            val can = minOf(khoi.toLong() * bs, coFile - boQua * bs)
            val ra = ByteArrayOutputStream(can.toInt())
            adb.docRa("dd if='$duongDan' bs=$bs skip=$boQua count=$khoi 2>/dev/null", can, ra)
            ra.toByteArray().copyOfRange(lech, lech + n)
        }
    }

    fun taiVe(duongDan: String, co: Long, ra: OutputStream, tienDo: (Long) -> Unit) {
        require(!duongDan.contains('\'')) { "duongDan" }
        adb.docRa("cat '$duongDan'", co, ra, tienDo = tienDo)
    }

    fun dung(pkg: String) { require(ThaoTacTv.TEN_GOI.matches(pkg)); sh("am force-stop $pkg") }

    /** Xoá cache một app: `pm clear --cache-only` chỉ có từ Android 12, máy cũ hơn trả false. */
    fun xoaCache(pkg: String): Boolean { require(ThaoTacTv.TEN_GOI.matches(pkg)); return sh("pm clear --cache-only $pkg 2>&1").contains("Success") }

    fun xoaDuLieu(pkg: String): Boolean { require(ThaoTacTv.TEN_GOI.matches(pkg)); return sh("pm clear $pkg 2>&1").contains("Success") }

    /** Xoá cache mọi app (Android tự chọn app nào xoá trước cho tới khi đủ chỗ; xin 1 TB là xoá hết). */
    fun donCacheTatCa() { sh("pm trim-caches 1000G", 300) }

    companion object {
        /** Dòng cuối của `df -k /data`: Filesystem 1K-blocks Used Available Use% Mounted. */
        fun tuDf(out: String): BoNho? {
            val cot = out.lines().lastOrNull { it.contains("/data") }?.trim()?.split(Regex("\\s+")) ?: return null
            if (cot.size < 4) return null
            val (tong, dung, con) = listOf(cot[1], cot[2], cot[3]).map { it.toLongOrNull() ?: return null }
            return BoNho(tong, dung, con)
        }

        /** Kết quả lệnh của tinhTrang: meminfo, uptime, df, loadavg, số nhân, nhiệt độ, cpuinfo, /proc/stat cách nhau bằng "@@". */
        fun tuTinhTrang(out: String): TinhTrangTv {
            val p = out.split("@@")
            fun doan(i: Int) = p.getOrElse(i) { "" }
            return TinhTrangTv(LamMoi.tuMeminfo(doan(0)), tuDf(doan(2)), LamMoi.tuUptime(doan(1)),
                LamMoi.tuLoadavg(doan(3)), LamMoi.tuSoNhan(doan(4)), LamMoi.tuNhietDo(doan(5)), LamMoi.tuCpuinfo(doan(6)).take(6),
                LamMoi.tuProcStat(doan(7)))
        }

        /** Kết quả lệnh của chayNgam: `ps -A -o RSS,NAME`, rồi `pm list packages` sau dòng "@@". */
        fun tuChayNgam(out: String): Pair<List<ChayNgam.App>, Set<String>> {
            val p = out.split("@@")
            val goi = p.getOrElse(1) { "" }.lines().mapNotNull { l -> l.trim().removePrefix("package:").takeIf { ThaoTacTv.TEN_GOI.matches(it) } }.toSet()
            return ChayNgam.gopTheoGoi(ChayNgam.tuPs(p.getOrElse(0) { "" }), goi) to goi
        }

        /** Khối "@@ gói|cỡ" + dòng versionCode= / versionName= của dumpsys. */
        fun tuDanhSach(out: String): List<AppTv> = out.split("@@ ").drop(1).mapNotNull { khoi ->
            val dau = khoi.lineSequence().first().trim()
            val pkg = dau.substringBefore('|')
            if (!ThaoTacTv.TEN_GOI.matches(pkg)) return@mapNotNull null
            AppTv(pkg,
                Regex("""versionName=([^\n]*)""").find(khoi)?.groupValues?.get(1)?.trim() ?: "",
                Regex("""versionCode=(\d+)""").find(khoi)?.groupValues?.get(1)?.toLongOrNull() ?: 0,
                dau.substringAfter('|').toLongOrNull() ?: 0)
        }

        /** "Package Names: [...]", "App Sizes: [...]", "App Data Sizes: [...]", "Cache Sizes: [...]" của dumpsys diskstats. */
        fun tuDiskstats(out: String): Map<String, CoDuLieu> {
            fun mang(ten: String) = Regex("""$ten: \[([^\]]*)]""").find(out)?.groupValues?.get(1)
                ?.split(',')?.map { it.trim().trim('"') }?.filter { it.isNotEmpty() } ?: emptyList()
            val goi = mang("Package Names")
            val app = mang("App Sizes").map { it.toLongOrNull() ?: 0 }
            val du = mang("App Data Sizes").map { it.toLongOrNull() ?: 0 }
            val cache = mang("Cache Sizes").map { it.toLongOrNull() ?: 0 }
            if (goi.isEmpty() || app.size != goi.size || du.size != goi.size || cache.size != goi.size) return emptyMap()
            return goi.indices.associate { goi[it] to CoDuLieu(app[it], du[it], cache[it]) }
        }
    }
}
