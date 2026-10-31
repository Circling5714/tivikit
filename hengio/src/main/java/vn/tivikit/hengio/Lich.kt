package vn.tivikit.hengio

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Một việc hẹn giờ (25/10/2026): lúc [gio]:[phut] các ngày [ngay] (1 = Thứ Hai … 7 = Chủ Nhật), [loai] = bật tivi (rồi
 * mở [goi] nếu có), tắt tivi, hoặc mở app [goi] khi tivi đang bật.
 */
data class Viec(val gio: Int, val phut: Int, val ngay: Set<Int>, val loai: String, val goi: String? = null, val bat: Boolean = true) {
    companion object {
        const val BAT = "bat"
        const val TAT = "tat"
        const val MO = "mo"
    }
}

/** Lần báo thức kế tiếp: thời điểm [luc] (ms) và các việc đến hạn đúng lúc đó. */
data class KeTiep(val luc: Long, val viec: List<Viec>)

/**
 * Việc một lần (31/10/2026): [loai] lúc [luc] (ms; tắt tivi thì là giờ tắt, báo trước BAO_TRUOC_MS), chạy xong thì bỏ. Từ
 * nút hẹn tắt sau X phút hay việc một lần lúc HH:MM (TiviKit, màn Hẹn giờ trên tivi).
 */
data class MotLan(val luc: Long, val loai: String, val goi: String? = null) {
    val id: String get() = "$loai-$luc"
    /** Giờ báo thức: tắt tivi thì sớm hơn BAO_TRUOC_MS để báo trước. */
    val bao: Long get() = luc - if (loai == Viec.TAT) Lich.BAO_TRUOC_MS else 0
    fun viec(): Viec = Viec(0, 0, (1..7).toSet(), loai, goi)
}

object Lich {
    /** Tắt tivi: báo trước 60 giây (có nút Hoãn) để tivi tắt đúng giờ đã đặt. */
    const val BAO_TRUOC_MS = 60_000L
    const val TOI_DA = 30
    const val MOT_LAN_TOI_DA = 10
    /** Hẹn tắt sau từ 1 phút tới 12 giờ. */
    val SAU_PHUT = 1..720
    /** Báo thức tới trễ hơn chừng này thì việc coi như đã lỡ: bỏ, không chạy bù. */
    const val TRE_TOI_DA_MS = 10 * 60_000L

    /**
     * Báo thức hẹn lúc [luc] mà tới [bay] mới chạy là đã lỡ: tivi ngủ sâu tới lúc bấm nguồn mới thức, hay hộp không pin giữ
     * giờ bật lên với giờ cũ, hẹn theo giờ đó, rồi lấy được giờ mạng thì báo thức thành quá hạn và chạy ngay (TX3 28/10/2026:
     * mở Kênh nhanh ngay sau khi khởi động; lịch tắt thì tivi vừa bật đã báo tắt).
     */
    fun daLo(luc: Long, bay: Long): Boolean = bay - luc > TRE_TOI_DA_MS

    /** Tên việc trong nhật ký, TiviKit đọc để nói cho người dùng: bat, thu (thử đánh thức), tat, mo:<gói>. */
    fun tenViec(v: Viec, laThu: Boolean): String = when (v.loai) {
        Viec.MO -> "mo:${v.goi}"
        Viec.BAT -> if (laThu) "thu" else Viec.BAT
        else -> v.loai
    }

    private val TEN_GOI = Regex("""^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$""")

    fun laTenGoi(s: String): Boolean = TEN_GOI.matches(s)

    /** Danh sách việc từ JSON TiviKit gửi; null nếu sai dạng (cả danh sách bị từ chối, không lưu nửa vời). */
    fun docJson(s: String?): List<Viec>? = runCatching {
        val a = JSONArray(s ?: return null)
        if (a.length() > TOI_DA) return null
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            val gio = o.getInt("gio"); val phut = o.getInt("phut")
            val ngayA = o.getJSONArray("ngay")
            val ngay = (0 until ngayA.length()).map { ngayA.getInt(it) }.toSet()
            val loai = o.getString("loai")
            val goi = o.optString("goi").trim().ifEmpty { null }
            require(gio in 0..23 && phut in 0..59 && ngay.isNotEmpty() && ngay.all { it in 1..7 })
            require(loai == Viec.BAT || loai == Viec.TAT || loai == Viec.MO)
            require(goi == null || TEN_GOI.matches(goi))
            require(loai != Viec.MO || goi != null)
            Viec(gio, phut, ngay, loai, if (loai == Viec.TAT) null else goi, o.optBoolean("bat", true))
        }
    }.getOrNull()

    fun json(ds: List<Viec>): JSONArray = JSONArray(ds.map { v ->
        JSONObject().put("gio", v.gio).put("phut", v.phut).put("ngay", JSONArray(v.ngay.sorted())).put("loai", v.loai)
            .put("bat", v.bat).apply { v.goi?.let { put("goi", it) } }
    })

    /** Sắp theo giờ trong ngày, rồi theo loại (bật trước mở, tắt sau), như TiviKit. */
    fun sapXep(ds: List<Viec>): List<Viec> = ds.sortedWith(compareBy({ it.gio * 60 + it.phut }, { listOf(Viec.BAT, Viec.MO, Viec.TAT).indexOf(it.loai) }))

    /**
     * Việc một lần TiviKit / màn tivi gửi (31/10/2026): {"loai","goi"?} kèm "sauPhut" (tính từ [bay]), "gio" + "phut" (lần tới
     * của giờ đó, lanToi) hay "luc" (ms). Null nếu sai dạng.
     */
    fun docMotLan(s: String?, bay: Long, zone: ZoneId): MotLan? = runCatching {
        val o = JSONObject(s ?: return null)
        val loai = o.getString("loai")
        val goi = o.optString("goi").trim().ifEmpty { null }
        require(loai == Viec.BAT || loai == Viec.TAT || loai == Viec.MO)
        require(goi == null || TEN_GOI.matches(goi))
        require(loai != Viec.MO || goi != null)
        val luc = when {
            o.has("sauPhut") -> bay + o.getInt("sauPhut").also { require(it in SAU_PHUT) } * 60_000L
            o.has("gio") -> lanToi(o.getInt("gio"), o.getInt("phut"), bay, zone)
            else -> o.getLong("luc").also { require(it > bay) }
        }
        MotLan(luc, loai, if (loai == Viec.TAT) null else goi)
    }.getOrNull()

    /** Lần tới của [gio]:[phut] sau [bay] theo múi giờ [zone]: hôm nay nếu chưa qua giờ đó, không thì ngày mai. */
    fun lanToi(gio: Int, phut: Int, bay: Long, zone: ZoneId): Long {
        require(gio in 0..23 && phut in 0..59)
        val homNay = Instant.ofEpochMilli(bay).atZone(zone).toLocalDate()
        val luc = ZonedDateTime.of(homNay, LocalTime.of(gio, phut), zone).toInstant().toEpochMilli()
        return if (luc > bay) luc else ZonedDateTime.of(homNay.plusDays(1), LocalTime.of(gio, phut), zone).toInstant().toEpochMilli()
    }

    fun docMotLanDs(s: String?): List<MotLan> = runCatching {
        val a = JSONArray(s ?: return emptyList())
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val loai = o.optString("loai"); val luc = o.optLong("luc")
            if (luc <= 0 || (loai != Viec.BAT && loai != Viec.TAT && loai != Viec.MO)) null
            else MotLan(luc, loai, o.optString("goi").trim().ifEmpty { null })
        }
    }.getOrDefault(emptyList())

    fun jsonMotLan(ds: List<MotLan>): JSONArray = JSONArray(ds.map { m ->
        JSONObject().put("id", m.id).put("luc", m.luc).put("loai", m.loai).apply { m.goi?.let { put("goi", it) } }
    })

    /** Thời điểm báo thức của [v] cho lần chạy vào ngày-giờ [lan]: tắt tivi thì sớm hơn BAO_TRUOC_MS để báo trước. */
    private fun bao(v: Viec, lan: ZonedDateTime): Long = lan.toInstant().toEpochMilli() - if (v.loai == Viec.TAT) BAO_TRUOC_MS else 0

    /**
     * Việc tắt tivi đang ở giữa phút báo trước: giờ báo (sớm hơn BAO_TRUOC_MS) đã qua mà chưa tới giờ tắt, vì báo thức vừa
     * được đặt lại đúng lúc đó (tivi khởi động lại, đổi giờ). keTiep bỏ qua lần này (giờ báo đã qua) nên phải báo ngay, không
     * thì lỡ cả ngày (28/10/2026). Trả giờ tắt (ms) của lần đó, null nếu không có.
     */
    fun tatLoBao(ds: List<Viec>, tu: Long, zone: ZoneId): Long? {
        val homNay = Instant.ofEpochMilli(tu).atZone(zone).toLocalDate()
        for (v in ds) if (v.bat && v.loai == Viec.TAT) {
            // Hôm nay hay ngày mai: tắt lúc 00:00 thì phút báo trước còn ở ngày hôm trước.
            for (d in 0L..1L) {
                val ngay = homNay.plusDays(d)
                if (ngay.dayOfWeek.value !in v.ngay) continue
                val tat = ZonedDateTime.of(ngay, LocalTime.of(v.gio, v.phut), zone).toInstant().toEpochMilli()
                if (tat - BAO_TRUOC_MS <= tu && tu < tat) return tat
            }
        }
        return null
    }

    /**
     * Lần báo thức kế tiếp sau [tu] (ms, không tính đúng [tu]) theo múi giờ [zone], cho các việc đang bật trong [ds] và
     * các việc một lần [motLan] (luc, việc: hoãn tắt, thử đánh thức). Các việc trùng thời điểm đi chung một lần báo.
     */
    fun keTiep(ds: List<Viec>, motLan: List<Pair<Long, Viec>>, tu: Long, zone: ZoneId): KeTiep? {
        val homNay = Instant.ofEpochMilli(tu).atZone(zone).toLocalDate()
        val ung = mutableListOf<Pair<Long, Viec>>()
        for (v in ds) if (v.bat) {
            // 8 ngày: hôm nay đã qua giờ thì lần kế có thể là đúng thứ này tuần sau.
            for (d in 0L..7L) {
                val ngay = homNay.plusDays(d)
                if (ngay.dayOfWeek.value !in v.ngay) continue
                val luc = bao(v, ZonedDateTime.of(ngay, LocalTime.of(v.gio, v.phut), zone))
                if (luc > tu) { ung += luc to v; break }
            }
        }
        motLan.filter { it.first > tu }.forEach { ung += it }
        val luc = ung.minOfOrNull { it.first } ?: return null
        return KeTiep(luc, ung.filter { it.first == luc }.map { it.second })
    }
}
