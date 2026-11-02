package vn.tivikit.hengio

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.PowerManager
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.concurrent.thread

/**
 * Auto Reboot (2/11/2026): mỗi ngày khởi động lại tivi lúc nó đang chờ cho tivi luôn mượt, lên lại thì tắt màn hình như
 * trước, bấm nguồn trên remote là lên bình thường. Tivi kiểu Skyworth tắt bằng remote chỉ tắt màn hình, hệ thống chạy liền
 * nhiều ngày. Lúc [gio]:[phut] các ngày [ngay] (1 = Thứ Hai … 7 = Chủ Nhật); tivi đang bật thì chờ (ChayAutoReboot).
 */
data class AutoReboot(val bat: Boolean = false, val gio: Int = 4, val phut: Int = 0, val ngay: Set<Int> = (1..7).toSet()) {
    fun json(): JSONObject = JSONObject().put("bat", bat).put("gio", gio).put("phut", phut).put("ngay", JSONArray(ngay.sorted()))

    /** Việc phải làm ở một lần kiểm (quyet). */
    enum class Quyet { KHOI_DONG_LAI, CHO, BO_QUA, LO }

    companion object {
        /** Tới giờ mà tivi đang bật: kiểm lại sau chừng này. */
        const val KIEM_LAI_MS = 15 * 60_000L
        /** Chờ tivi tắt tối đa chừng này sau giờ hẹn; quá thì bỏ hôm đó. */
        const val CHO_TOI_DA_MS = 3 * 3_600_000L
        /** Có việc bật tivi / mở app trong chừng này tới thì chưa khởi động lại (khởi động xong là tắt màn hình). */
        const val TRANH_VIEC_MS = 30 * 60_000L
        /** Tivi lên lại trong chừng này sau lệnh khởi động lại thì là do Auto Reboot: tắt màn hình. */
        const val SAU_KHOI_DONG_MS = 15 * 60_000L

        /** Cấu hình TiviKit gửi ({"bat","gio","phut","ngay"}); null nếu sai dạng. */
        fun doc(s: String?): AutoReboot? = runCatching {
            val o = JSONObject(s ?: return null)
            val n = o.getJSONArray("ngay")
            val ngay = (0 until n.length()).map { n.getInt(it) }.toSet()
            val gio = o.getInt("gio"); val phut = o.getInt("phut")
            require(gio in 0..23 && phut in 0..59 && ngay.isNotEmpty() && ngay.all { it in 1..7 })
            AutoReboot(o.getBoolean("bat"), gio, phut, ngay)
        }.getOrNull()

        /** Giờ hẹn kế tiếp sau [tu] (ms, không tính đúng [tu]) theo múi giờ [zone]; null nếu đang tắt. */
        fun lanToi(c: AutoReboot, tu: Long, zone: ZoneId): Long? {
            if (!c.bat) return null
            val homNay = Instant.ofEpochMilli(tu).atZone(zone).toLocalDate()
            // 8 ngày: hôm nay đã qua giờ thì lần kế có thể là đúng thứ này tuần sau.
            for (d in 0L..7L) {
                val ngay = homNay.plusDays(d)
                if (ngay.dayOfWeek.value !in c.ngay) continue
                val luc = ZonedDateTime.of(ngay, LocalTime.of(c.gio, c.phut), zone).toInstant().toEpochMilli()
                if (luc > tu) return luc
            }
            return null
        }

        /**
         * Lần kiểm lúc [bay] cho giờ hẹn [dot]: tivi đang chờ thì khởi động lại; đang bật thì chờ lần kiểm sau nếu nó còn trong
         * [CHO_TOI_DA_MS], không thì bỏ hôm đó. Báo thức tới trễ quá hạn chờ (tivi ngủ sâu, tắt điện, đổi giờ) là lỡ.
         */
        fun quyet(dot: Long, bay: Long, dangCho: Boolean): Quyet = when {
            bay - dot > CHO_TOI_DA_MS -> Quyet.LO
            dangCho -> Quyet.KHOI_DONG_LAI
            bay + KIEM_LAI_MS - dot > CHO_TOI_DA_MS -> Quyet.BO_QUA
            else -> Quyet.CHO
        }
    }
}

/**
 * Auto Reboot trên tivi: tới giờ thì khởi động lại bằng lệnh reboot qua ADB của chính tivi (AdbNoiBo; khởi động lại của
 * Android trên tivi MediaTek thành tắt chờ), lên lại thì tắt màn hình (HenGioDichVu.tatSauKhoiDong). Lỗi hay phải bỏ hôm đó
 * thì báo bot Telegram của tivi (Tele Remote 1.0.2 trở lên), lần nào chạy được thì thôi.
 */
object ChayAutoReboot {
    /** Tên việc trong nhật ký. */
    const val VIEC = Viec.KDL
    private const val TELE_REMOTE = "vn.tivikit.teleremote"

    /** Tivi đang chờ: màn hình tắt (màn bảo vệ vẫn tính là bật) và không phát tiếng. */
    fun dangCho(ctx: Context): Boolean =
        ctx.getSystemService(PowerManager::class.java)?.isInteractive != true && ctx.getSystemService(AudioManager::class.java)?.isMusicActive != true

    /** Tới lần kiểm [luc] (giờ hẹn, hay lần kiểm lại khi đang chờ tivi tắt); [ban]: lúc này hay 30 phút tới có việc bật / mở. */
    fun toiGio(ctx: Context, luc: Long, ban: Boolean) {
        val kho = Kho(ctx)
        if (!kho.kdl.bat) { kho.kdlCho = 0; return }
        val bay = System.currentTimeMillis()
        val dot = kho.kdlCho.takeIf { it in 1..luc } ?: luc
        when (AutoReboot.quyet(dot, bay, !ban && dangCho(ctx))) {
            AutoReboot.Quyet.LO -> { kho.kdlCho = 0; kho.ghi(VIEC, "lo-gio") }
            AutoReboot.Quyet.KHOI_DONG_LAI -> { kho.kdlCho = 0; khoiDongLai(ctx, dot, thu = false) }
            AutoReboot.Quyet.CHO -> {
                if (kho.kdlCho != dot) kho.ghi(VIEC, "cho-tivi-tat")
                kho.kdlCho = dot
                kho.kdlKiemLuc = bay + AutoReboot.KIEM_LAI_MS
            }
            AutoReboot.Quyet.BO_QUA -> { kho.kdlCho = 0; kho.ghi(VIEC, "bo-qua"); baoTeleRemote(ctx, "bo-qua", dot) }
        }
    }

    /** Đang chờ tivi tắt mà lần kiểm đã qua (báo thức mất khi tivi khởi động lại, đổi giờ): kiểm sau 1 phút; quá hạn chờ thì thôi. */
    fun kiemLo(kho: Kho, bay: Long) {
        val cho = kho.kdlCho
        if (cho <= 0) return
        if (!kho.kdl.bat || bay - cho > AutoReboot.CHO_TOI_DA_MS) { kho.kdlCho = 0; return }
        if (kho.kdlKiemLuc <= bay) kho.kdlKiemLuc = bay + 60_000
    }

    /**
     * Khởi động lại ngay, chạy nền. Ghi cờ trước (ghi chắc: tivi tắt ngay sau lệnh) để lên lại thì tắt màn hình. Lúc tự chạy
     * không ai ở trước tivi nên không chờ người bấm Cho phép; [thu] (nút Thử ngay ở TiviKit) thì chờ tối đa 60 giây. Lỗi thì
     * bỏ cờ, ghi nhật ký và (lúc tự chạy) báo Tele Remote.
     */
    fun khoiDongLai(ctx: Context, dot: Long, thu: Boolean) {
        val app = ctx.applicationContext
        val kho = Kho(app)
        kho.datCoKhoiDong(System.currentTimeMillis())
        kho.ghi(VIEC, if (thu) "thu" else "khoi-dong-lai", chac = true)
        val wl = runCatching { app.getSystemService(PowerManager::class.java)?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "hengio:kdl")?.apply { acquire(90_000) } }.getOrNull()
        thread(name = "hengio-kdl") {
            val kq = AdbNoiBo.chay(app.filesDir, "shell:reboot", {}, choToiDa = if (thu) 60_000 else 5_000)
            if (kq != AdbNoiBo.KetQua.DA_GUI) {
                kho.datCoKhoiDong(0)
                val ma = maLoi(kq)
                kho.ghi(VIEC, ma)
                if (!thu) baoTeleRemote(app, ma, dot)
            }
            runCatching { wl?.release() }
        }
    }

    private fun maLoi(kq: AdbNoiBo.KetQua) = when (kq) {
        AdbNoiBo.KetQua.KHONG_CO -> "loi-khong-co-adb"
        AdbNoiBo.KetQua.CHUA_CHO_PHEP -> "loi-chua-cho-phep"
        else -> "loi"
    }

    /**
     * Tivi vừa lên lại (BOOT_COMPLETED, hay dịch vụ trợ năng vừa nối): lần khởi động này do Auto Reboot (cờ còn mới, máy khởi
     * động sau lúc ghi cờ) thì tắt màn hình. Dịch vụ chưa nối thì chờ nó gọi lại; cờ quá hạn thì bỏ.
     */
    fun sauKhoiDong(ctx: Context) {
        val kho = Kho(ctx)
        val t0 = kho.kdlVuaLam
        if (t0 <= 0) return
        val qua = System.currentTimeMillis() - t0
        if (qua !in 0..AutoReboot.SAU_KHOI_DONG_MS) { kho.datCoKhoiDong(0); return }
        // Máy chạy từ lúc khởi động lâu hơn từ lúc ghi cờ: chưa khởi động lại (lệnh đang gửi), để nguyên.
        if (SystemClock.elapsedRealtime() >= qua) return
        val dv = HenGioDichVu.dangChay ?: return
        kho.datCoKhoiDong(0)
        dv.tatSauKhoiDong()
    }

    /**
     * Nối thử ADB của tivi (TiviKit gọi lúc lưu Auto Reboot): lần đầu tivi hỏi Cho phép gỡ lỗi. Chờ người dùng tới 2 phút:
     * Khoá trẻ em đang bật thì che hộp đó bằng màn hỏi mã bố mẹ, bố mẹ mở app xác thực gõ mã trước (Skyworth 2/11/2026).
     * Kết quả ở Kho.kdlAdb.
     */
    fun thuAdb(ctx: Context) {
        val app = ctx.applicationContext
        val kho = Kho(app)
        kho.kdlAdb = "dang-thu"
        thread(name = "hengio-adb") {
            val kq = AdbNoiBo.chay(app.filesDir, "shell:true", { kho.kdlAdb = "cho-cho-phep" }, choToiDa = 120_000)
            kho.kdlAdb = when (kq) {
                AdbNoiBo.KetQua.DA_GUI -> "da-noi"
                AdbNoiBo.KetQua.KHONG_CO -> "khong-co"
                AdbNoiBo.KetQua.CHUA_CHO_PHEP -> "chua-cho-phep"
                AdbNoiBo.KetQua.LOI -> "loi"
            }
        }
    }

    /** Báo bot Telegram của tivi: [kq] (bo-qua, loi-…, tat-loi) cho giờ hẹn [luc]. Không có Tele Remote thì thôi. */
    fun baoTeleRemote(ctx: Context, kq: String, luc: Long) {
        val app = ctx.applicationContext
        thread(name = "hengio-bao") {
            runCatching { app.contentResolver.call(Uri.parse("content://$TELE_REMOTE"), "auto-reboot", JSONObject().put("kq", kq).put("luc", luc).toString(), null) }
        }
    }
}
