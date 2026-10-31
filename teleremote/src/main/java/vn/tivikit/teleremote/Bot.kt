package vn.tivikit.teleremote

import android.os.SystemClock
import java.io.File
import java.io.IOException
import kotlin.concurrent.thread
import org.json.JSONObject

/** Nơi bot trả lời: Telegram thật, hay bản ghi lại (lệnh thu của CauHinhProvider). */
interface Ra {
    fun gui(chat: Long, chu: String, banPhim: JSONObject? = null)
    fun sua(chat: Long, idTin: Long, chu: String, banPhim: JSONObject? = null)
    fun traLoiNut(id: String, chu: String = "", canhBao: Boolean = false)
    fun guiAnh(chat: Long, anh: ByteArray, chuThich: String)
    fun xoaTin(chat: Long, idTin: Long)
    /** "typing" / "upload_photo": hiện trong chat lúc tivi làm việc lâu. */
    fun dangLam(chat: Long, viec: String)
    /** Tải ảnh / video [id] người dùng vừa gửi về [dich]; false nếu không tải được (mạng, lớn quá [toiDa]). */
    fun taiTep(id: String, dich: File, toiDa: Long): Boolean
}

/** Trả lời qua Telegram; lỗi gửi (người dùng chặn bot, mạng chập chờn) thì bỏ, không làm hỏng vòng nghe. */
class TelegramRa(private val t: Telegram) : Ra {
    override fun gui(chat: Long, chu: String, banPhim: JSONObject?) { runCatching { t.gui(chat, chu, banPhim) } }
    override fun sua(chat: Long, idTin: Long, chu: String, banPhim: JSONObject?) { runCatching { t.sua(chat, idTin, chu, banPhim) } }
    override fun traLoiNut(id: String, chu: String, canhBao: Boolean) { if (id.isNotEmpty()) runCatching { t.traLoiNut(id, chu, canhBao) } }
    override fun guiAnh(chat: Long, anh: ByteArray, chuThich: String) { runCatching { t.guiAnh(chat, anh, chuThich) } }
    override fun xoaTin(chat: Long, idTin: Long) { if (idTin != 0L) runCatching { t.xoaTin(chat, idTin) } }
    override fun dangLam(chat: Long, viec: String) { runCatching { t.dangLam(chat, viec) } }
    override fun taiTep(id: String, dich: File, toiDa: Long): Boolean = runCatching { t.taiTep(id, dich, toiDa) }.isSuccess
}

/** Ghi lại những gì bot định trả lời (lệnh thu: TiviKit / người thử xem bot làm gì mà không cần Telegram). */
class GhiRa : Ra {
    val ds = mutableListOf<String>()
    override fun gui(chat: Long, chu: String, banPhim: JSONObject?) { ds += chu + nut(banPhim) }
    override fun sua(chat: Long, idTin: Long, chu: String, banPhim: JSONObject?) { ds += chu + nut(banPhim) }
    override fun traLoiNut(id: String, chu: String, canhBao: Boolean) { if (chu.isNotEmpty()) ds += chu }
    override fun guiAnh(chat: Long, anh: ByteArray, chuThich: String) { ds += "[ảnh ${anh.size / 1024} KB] $chuThich" }
    override fun xoaTin(chat: Long, idTin: Long) {}
    override fun dangLam(chat: Long, viec: String) {}
    override fun taiTep(id: String, dich: File, toiDa: Long) = false

    /** Chữ trên các nút đi kèm (bàn phím chính hay nút trong tin) để người thử thấy: " [a · b]"; không có nút thì "". */
    private fun nut(b: JSONObject?): String {
        val hang = b?.optJSONArray("inline_keyboard") ?: b?.optJSONArray("keyboard") ?: return ""
        val chu = (0 until hang.length()).flatMap { i -> hang.getJSONArray(i).let { h -> (0 until h.length()).map { h.getJSONObject(it).optString("text") } } }
        return if (chu.isEmpty()) "" else chu.joinToString(" · ", " [", "]")
    }
}

/**
 * Vòng hỏi Telegram của bot gắn với tivi (31/10/2026): getUpdates chờ tới 50 giây mỗi lần (long polling, không cần mở cổng
 * modem hay máy chủ). Mỗi tivi một bot: Telegram chỉ cho một nơi hỏi một bot, tivi khác dùng cùng token thì Telegram báo
 * 409 (TRUNG_BOT). Mỗi lần nối lại chỉ làm tin chữ vừa gửi trong tin dồn (bỏ tin cũ và nút bấm cũ từ lúc tivi tắt / mất
 * mạng). Người lạ nhắn thì ghi vào danh sách chờ
 * duyệt và báo chủ tivi một lần.
 */
class Bot(private val dv: TeleRemoteDichVu) {
    enum class TrangThai(val ma: String) {
        CHUA_CO_TOKEN("chua-co-token"), TAM_TAT("tam-tat"), DANG_NOI("dang-noi"), DANG_NGHE("dang-nghe"), SAI_TOKEN("sai-token"),
        TRUNG_BOT("trung-bot"), KHONG_MANG("khong-mang"), LOI("loi"), TAT("tat")
    }

    @Volatile var trangThai = TrangThai.DANG_NOI
        private set
    /** Lần cuối hỏi Telegram được (ms), 0 nếu chưa. */
    @Volatile var lanCuoi = 0L
        private set
    @Volatile private var chay = false
    @Volatile private var tele: Telegram? = null
    /** Vừa được gọi dậy (token / cấu hình đổi): lần chờ kế tiếp khỏi chờ, khỏi lỡ lời gọi tới trước lúc bắt đầu chờ. */
    @Volatile private var doi = false
    private val danhThuc = Object()
    /** Đã báo "tivi vừa bật" cho lần khởi động này (báo tin). */
    @Volatile private var daBaoBat = false
    private val kho get() = dv.kho

    fun batDau() {
        if (chay) return
        chay = true
        thread(name = "TeleRemote-bot", isDaemon = true) { vong() }
    }

    fun dung() {
        chay = false
        tele?.ngat()
        synchronized(danhThuc) { danhThuc.notifyAll() }
    }

    /** Token hay cấu hình vừa đổi: bỏ lời hỏi đang chờ, làm lại từ đầu với cái mới. */
    fun lamLai() {
        synchronized(danhThuc) { doi = true; danhThuc.notifyAll() }
        tele?.ngat()
    }

    private fun vong() {
        var cho = CHO_DAU
        while (chay) {
            // Đọc token / cấu hình sau khi xoá cờ: lời gọi dậy tới sau lúc này thì lần chờ kế tiếp không chờ.
            doi = false
            val token = kho.token
            if (token.isEmpty()) { trangThai = TrangThai.CHUA_CO_TOKEN; nghi(0); continue }
            if (!kho.cauHinh.bat) { trangThai = TrangThai.TAM_TAT; nghi(0); continue }
            val t = Telegram(token).also { tele = it }
            try {
                trangThai = TrangThai.DANG_NOI
                t.toi().optString("username").takeIf { it.isNotEmpty() }?.let { if (it != kho.bot) kho.bot = it }
                t.boWebhook()
                runCatching { t.datLenh(LamViec.lenhMenu(kho.cauHinh.tiengAnh)) }
                val (don, dau) = t.tinDon()
                var offset = dau
                // Tin dồn: chỉ làm tin chữ vừa gửi (xuLy bỏ tin cũ hơn 2 phút: tivi vừa cài đè, mạng chập chờn vài giây). Nút bấm
                // không biết bấm lúc nào nên bỏ cả: "Tắt tivi" bấm từ tối qua không tắt tivi vừa bật sáng nay.
                for (u in don) if (u.has("message")) runCatching { xuLy(u, t) }
                trangThai = TrangThai.DANG_NGHE
                lanCuoi = System.currentTimeMillis()
                cho = CHO_DAU
                // Báo tin: tivi vừa khởi động (dịch vụ chạy từ lúc bật tivi; cài đè / bật lại dịch vụ giữa chừng thì không báo).
                if (!daBaoBat) {
                    daBaoBat = true
                    if (kho.cauHinh.baoTin && SystemClock.elapsedRealtime() < MOI_KHOI_DONG) dv.baoTin(LamViec.chuBat(dv))
                }
                while (chay && kho.token == token && kho.cauHinh.bat) {
                    val ds = t.capNhat(offset)
                    lanCuoi = System.currentTimeMillis()
                    for (u in ds) {
                        offset = u.optLong("update_id") + 1
                        runCatching { xuLy(u, t) }
                    }
                }
            } catch (e: Telegram.Loi) {
                when (e.ma) {
                    401, 404 -> { trangThai = TrangThai.SAI_TOKEN; nghi(0) }
                    409 -> { trangThai = TrangThai.TRUNG_BOT; nghi(30_000) }
                    429 -> { trangThai = TrangThai.LOI; nghi(e.choGiay.coerceIn(1, 300) * 1000L) }
                    else -> { trangThai = TrangThai.LOI; nghi(cho); cho = (cho * 2).coerceAtMost(CHO_TOI_DA) }
                }
            } catch (e: IOException) {
                // Bị ngắt vì đổi token / tạm tắt / dừng dịch vụ: làm lại ngay; còn lại là mất mạng hay Telegram bị chặn.
                if (chay && kho.token == token && kho.cauHinh.bat) {
                    trangThai = TrangThai.KHONG_MANG
                    nghi(cho)
                    cho = (cho * 2).coerceAtMost(CHO_TOI_DA)
                }
            } finally {
                tele = null
            }
        }
        trangThai = TrangThai.TAT
    }

    private fun xuLy(u: JSONObject, t: Telegram) {
        val d = XuLyLenh.docCapNhat(u) ?: return
        if (!d.rieng) return
        if (XuLyLenh.cu(d, System.currentTimeMillis() / 1000)) return
        val ra = TelegramRa(t)
        val ch = kho.cauHinh
        val nd = XuLyLenh.tim(ch, d.nguoi)
        if (nd == null) {
            nguoiLa(d, ra, ch)
            return
        }
        // Lần đầu nhắn: ghi ID số (từ đó khớp theo ID), cập nhật username / tên hiện cho TiviKit hiện.
        val nd2 = XuLyLenh.ghiNguoi(ch, nd, d.nguoi)?.let { moi -> kho.cauHinh = moi; XuLyLenh.tim(moi, d.nguoi) } ?: nd
        LamViec(dv, ra).lam(d, nd2)
    }

    /**
     * Nhắn [chu] cho các chủ tivi đã nhắn bot (báo tin: tivi vừa bật / vừa tắt), trên luồng riêng. Tivi vừa thức dậy thì mạng
     * có thể chưa lên: thử lại vài lần trong chừng một phút.
     */
    fun baoChu(chu: String) {
        val token = kho.token.takeIf { it.isNotEmpty() } ?: return
        val chuTivi = kho.cauHinh.nguoiDung.filter { it.vaiTro == VaiTro.CHU && it.id != 0L }
        if (chuTivi.isEmpty()) return
        thread(name = "TeleRemote-bao", isDaemon = true) {
            val t = Telegram(token)
            val conLai = chuTivi.map { it.id }.toMutableList()
            for (lan in 1..4) {
                conLai.removeAll { id -> runCatching { t.gui(id, chu) }.isSuccess }
                if (conLai.isEmpty() || !chay) break
                Thread.sleep(15_000)
            }
        }
    }

    /** Người không có trong danh sách: nút thì báo không được phép; tin thì ghi chờ duyệt, nhắn họ và chủ tivi một lần. */
    private fun nguoiLa(d: XuLyLenh.Den, ra: Ra, ch: CauHinh) {
        val en = ch.tiengAnh
        val chuaDuoc = if (en) "You're not allowed to control this TV yet. Ask the TV's owner to approve you in TiviKit → Tele Remote."
            else "Bạn chưa được phép điều khiển tivi này. Nhờ chủ tivi mở TiviKit → Tele Remote và duyệt bạn."
        if (d.nut != null) {
            ra.traLoiNut(d.idNut, chuaDuoc, canhBao = true)
            return
        }
        if (!kho.themCho(XinDung(d.nguoi.id, d.nguoi.ten, d.nguoi.tenHien, System.currentTimeMillis()))) return
        ra.gui(d.chat, chuaDuoc)
        val ai = listOf(d.nguoi.ten, d.nguoi.tenHien).filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { "ID ${d.nguoi.id}" }
        val bao = if (en) "🔔 $ai wants to use this TV's bot. Approve or ignore in TiviKit → Tele Remote."
            else "🔔 $ai muốn dùng bot của tivi. Duyệt hay bỏ qua ở TiviKit → Tele Remote."
        ch.nguoiDung.filter { it.vaiTro == VaiTro.CHU && it.id != 0L }.forEach { ra.gui(it.id, bao) }
        kho.ghi(ai, "xin-dung")
    }

    /** Chờ [ms] ms (0 = tới khi được gọi dậy: token / cấu hình đổi, dừng dịch vụ). */
    private fun nghi(ms: Long) = synchronized(danhThuc) {
        if (chay && !doi) danhThuc.wait(ms)
        doi = false
    }

    companion object {
        private const val CHO_DAU = 5_000L
        private const val CHO_TOI_DA = 120_000L
        /** Dịch vụ chạy trong chừng này ms đầu sau khi bật tivi là tivi vừa khởi động (báo tin). */
        private const val MOI_KHOI_DONG = 10 * 60_000L
    }
}
