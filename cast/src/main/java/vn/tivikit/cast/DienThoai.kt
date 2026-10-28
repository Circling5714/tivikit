package vn.tivikit.cast

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * Một điện thoại đã nối vào tivi để cast (28/10/2026). YouTube không cho tivi biết danh sách mọi máy đã liên kết bằng mã
 * TV, chỉ biết máy nào nối vào (remoteConnected / loungeStatus: mã máy, tên máy, tài khoản Google): danh sách ghi dần từ
 * đó. Máy lạ cast lần đầu thì tivi hỏi; cho phép luôn hay có hạn (khách: tới hết hôm nay, 24 giờ), quá hạn coi như chặn;
 * chặn thì tivi bỏ qua video máy đó gửi. YouTube không có lệnh để tivi huỷ liên kết riêng một máy nên "huỷ một máy" là
 * chặn tại tivi; huỷ tất cả thì đổi screen (Lounge.doiScreen).
 */
data class DienThoai(
    val id: String,
    val ten: String,
    val taiKhoan: String = "",
    val trangThai: TrangThai = TrangThai.CHO,
    /** Cho phép tới lúc này (giờ máy, ms); 0 = mãi. */
    val hetHan: Long = 0L,
    val lanDau: Long = 0L,
    val lanCuoi: Long = 0L,
) {
    enum class TrangThai { CHO, CHO_PHEP, CHAN }

    /** Video máy này gửi tới: phát, hỏi trên tivi (chưa duyệt), hay bỏ qua (chặn, hết hạn). */
    enum class Xet { PHAT, HOI, BO_QUA }

    fun xet(bay: Long): Xet = when (trangThai) {
        TrangThai.CHAN -> Xet.BO_QUA
        TrangThai.CHO -> Xet.HOI
        TrangThai.CHO_PHEP -> if (hetHan in 1..bay) Xet.BO_QUA else Xet.PHAT
    }

    /** Khách đã quá hạn được cast. */
    fun quaHan(bay: Long) = trangThai == TrangThai.CHO_PHEP && hetHan in 1..bay

    /** Tài khoản Google và tên máy (bỏ trùng); trống cả hai thì null. */
    val tenHien: String? get() = listOf(taiKhoan, ten).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        .joinToString(" · ").ifEmpty { null }

    fun toJson(): JSONObject = JSONObject().put("id", id).put("ten", ten).put("taiKhoan", taiKhoan)
        .put("trangThai", trangThai.name).put("hetHan", hetHan).put("lanDau", lanDau).put("lanCuoi", lanCuoi)

    companion object {
        fun tuJson(o: JSONObject): DienThoai? {
            val id = o.optString("id").takeIf { it.isNotEmpty() } ?: return null
            val tt = runCatching { TrangThai.valueOf(o.optString("trangThai")) }.getOrDefault(TrangThai.CHO)
            return DienThoai(id, o.optString("ten"), o.optString("taiKhoan"), tt, o.optLong("hetHan"), o.optLong("lanDau"), o.optLong("lanCuoi"))
        }
    }
}

/** Danh sách điện thoại của tivi (Kho lưu dạng JSON); mọi phép đổi trả danh sách mới, để thử được. */
object DanhBa {
    /** Giữ chừng này máy gần nhất (máy bị chặn giữ trước, để chặn vẫn còn). */
    const val TOI_DA = 60
    const val NGAY_MS = 24 * 3_600_000L

    /** Việc làm với một máy: cho phép luôn / tới hết hôm nay / 24 giờ, chặn, xoá khỏi danh sách (lần sau hỏi lại). */
    enum class Lam(val ma: String) {
        CHO_PHEP("cho-phep"), HOM_NAY("hom-nay"), MOT_NGAY("24h"), CHAN("chan"), XOA("xoa");

        companion object { fun tu(ma: String): Lam? = entries.firstOrNull { it.ma == ma } }
    }

    fun tuJson(s: String?): List<DienThoai> = runCatching {
        val a = JSONArray(s ?: "[]")
        (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(DienThoai::tuJson) }
    }.getOrDefault(emptyList())

    fun json(ds: List<DienThoai>): String = JSONArray().also { a -> ds.forEach { a.put(it.toJson()) } }.toString()

    /** Máy [id] vừa nối vào: máy mới thêm vào ở trạng thái chờ duyệt; máy đã biết thì cập nhật tên, tài khoản, lần cuối. */
    fun noi(ds: List<DienThoai>, id: String, ten: String, taiKhoan: String, bay: Long): List<DienThoai> {
        if (id.isEmpty()) return ds
        val cu = ds.firstOrNull { it.id == id }
        val moi = cu?.copy(ten = ten.ifEmpty { cu.ten }, taiKhoan = taiKhoan.ifEmpty { cu.taiKhoan }, lanCuoi = bay)
            ?: DienThoai(id, ten, taiKhoan, lanDau = bay, lanCuoi = bay)
        return gon(listOf(moi) + ds.filter { it.id != id })
    }

    /** Làm [lam] với máy [id]; không có máy đó thì giữ nguyên. */
    fun lam(ds: List<DienThoai>, id: String, lam: Lam, bay: Long, cal: Calendar = Calendar.getInstance()): List<DienThoai> {
        if (ds.none { it.id == id }) return ds
        if (lam == Lam.XOA) return ds.filter { it.id != id }
        return ds.map { d ->
            if (d.id != id) d else when (lam) {
                Lam.CHO_PHEP -> d.copy(trangThai = DienThoai.TrangThai.CHO_PHEP, hetHan = 0L)
                Lam.HOM_NAY -> d.copy(trangThai = DienThoai.TrangThai.CHO_PHEP, hetHan = hetHomNay(bay, cal))
                Lam.MOT_NGAY -> d.copy(trangThai = DienThoai.TrangThai.CHO_PHEP, hetHan = bay + NGAY_MS)
                Lam.CHAN -> d.copy(trangThai = DienThoai.TrangThai.CHAN, hetHan = 0L)
                Lam.XOA -> d
            }
        }
    }

    /** 0 giờ hôm sau theo giờ tivi: khách được cast "tới hết hôm nay". */
    fun hetHomNay(bay: Long, cal: Calendar = Calendar.getInstance()): Long {
        cal.timeInMillis = bay
        cal.add(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /**
     * Ai gửi video: lệnh setPlaylist không ghi mã máy, chỉ có tài khoản trong eventDetails ([taiKhoan]). Có tài khoản thì
     * lấy máy đang nối có tài khoản đó (không có máy đang nối thì máy đã biết gần nhất của tài khoản đó), không thì máy vừa
     * nối gần nhất ([ganNhat]).
     */
    fun nguoiGui(ds: List<DienThoai>, dangNoi: Collection<String>, ganNhat: String?, taiKhoan: String): DienThoai? {
        if (taiKhoan.isNotEmpty()) {
            val cung = ds.filter { it.taiKhoan == taiKhoan }
            (cung.firstOrNull { it.id == ganNhat } ?: cung.firstOrNull { it.id in dangNoi } ?: cung.maxByOrNull { it.lanCuoi })?.let { return it }
        }
        return ds.firstOrNull { it.id == ganNhat }
    }

    /**
     * Lệnh điều khiển (tạm dừng, tua, âm lượng…) không biết của máy nào: làm theo nếu có ít nhất một máy đang nối được
     * phép cast, hay không biết máy nào đang nối (vừa bật lại). Chỉ toàn máy bị chặn / chờ duyệt đang nối thì bỏ qua.
     */
    fun duocDieuKhien(ds: List<DienThoai>, dangNoi: Collection<String>, bay: Long): Boolean =
        dangNoi.isEmpty() || dangNoi.any { id -> ds.firstOrNull { it.id == id }?.xet(bay) == DienThoai.Xet.PHAT }

    /** Giữ tối đa [TOI_DA] máy: máy bị chặn trước, rồi máy dùng gần nhất. */
    private fun gon(ds: List<DienThoai>): List<DienThoai> = if (ds.size <= TOI_DA) ds
        else ds.sortedWith(compareByDescending<DienThoai> { it.trangThai == DienThoai.TrangThai.CHAN }.thenByDescending { it.lanCuoi })
            .take(TOI_DA).let { giu -> ds.filter { it in giu } }
}
