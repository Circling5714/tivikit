package vn.tivikit.mapvoice

import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.net.Uri
import android.os.SystemClock
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import vn.tivikit.mapvoice.TuChonNguon.Nguon

/**
 * Mở kênh tự chọn nguồn (Kênh nhanh Auto, 1/11/2026), chạy trong dịch vụ trợ năng của Map Voice: mở lần lượt các nguồn
 * (TuChonNguon.xep) tới khi app đó phát tiếng — hỏi `dumpsys audio` qua ADB của chính tivi theo uid của app (chưa nối ADB
 * thì hỏi Android có tiếng phim nào đang phát trong lúc app đó ở trước mặt). Nguồn không lên tiếng trong 15 giây (10 giây
 * nếu app đang chạy sẵn) thì đóng app đó, sang nguồn kế; link M3U không trả lời thì bỏ qua M3U. Nhớ thời gian lên tiếng và
 * lần lỗi của từng nguồn để lần sau thử nguồn tốt trước.
 *
 * Đang xem kênh mở theo cách này mà mất tiếng 9 giây (anh chọn 1/11/2026) thì đổi sang nguồn kế (CauHinh.doiNguon). Không
 * đổi khi: người xem vừa bấm phím (trừ âm lượng) ngay trước / trong lúc mất tiếng — tự tạm dừng, tua, mở menu; app khác giữ
 * tiếng (màn Khoá trẻ em, Trợ lý). Thôi theo dõi khi người xem bấm phím chuyển kênh, Back, Home, hay app khác lên trước mặt.
 */
class TuChon(private val dv: MapVoiceDichVu) {
    private val luong = Executors.newSingleThreadExecutor { r -> Thread(r, "MapVoice-tu-chon").apply { isDaemon = true } }
    /** Lần mở hiện tại: lời gọi mới / huỷ tăng số này, việc cũ thấy khác số thì thôi. */
    private val phien = AtomicInteger()

    private enum class KetQua { PHAT, KHONG, THOI }

    /** Mở kênh [ten] bằng một trong [nguon] (ô Auto: nguồn TiviKit đã tìm khi lưu ô); lời gọi mới huỷ việc cũ. */
    fun mo(ten: String, nguon: List<Nguon>) {
        val ma = phien.incrementAndGet()
        luong.execute { runCatching { chay(ma, ten, XuLyCau.khoaKenh(ten), dienLink(nguon)) } }
    }

    /** Câu nói "mở VTV1" khi app kênh là "auto": nguồn Map Voice biết, kèm phím tắt M3U cùng tên trên tivi. */
    fun mo(k: KenhTv) {
        val ma = phien.incrementAndGet()
        luong.execute { runCatching { chay(ma, k.ten, XuLyCau.khoaKenh(k.ten), TuChonNguon.nguonCua(k, phimTatM3u())) } }
    }

    /** Thôi việc đang làm và thôi theo dõi (câu nói mới, dịch vụ tắt). */
    fun huy() { phien.incrementAndGet() }

    fun dong() { huy(); luong.shutdownNow() }

    private fun phimTatM3u(): List<Nguon> =
        if (!dv.coApp(TuChonNguon.GOI_M3U)) emptyList() else dv.adb(TuChonNguon.LENH_PHIM_TAT_M3U, 5000)?.let(TuChonNguon::kenhM3u).orEmpty()

    /** Nguồn M3U của ô chưa có link (TiviKit chỉ gửi mã kênh): lấy link trong phím tắt cùng mã để thử trước. */
    private fun dienLink(ds: List<Nguon>): List<Nguon> {
        if (ds.none { it.app == TuChonNguon.M3U && it.url.isEmpty() }) return ds
        val link = phimTatM3u().associate { it.ma to it.url }
        return ds.map { n -> if (n.app == TuChonNguon.M3U && n.url.isEmpty()) n.copy(url = link[n.ma].orEmpty()) else n }
    }

    /**
     * Thử lần lượt các nguồn của kênh [ten]. [hongTruoc] = gói app vừa hỏng (theo dõi thấy mất tiếng): như app không lên
     * tiếng, đóng nó sau khi đã mở nguồn kế, khỏi nháy màn Home giữa hai app (TX3, 1/11/2026).
     */
    private fun chay(ma: Int, ten: String, khoa: String, nguon: List<Nguon>, hongTruoc: String? = null) {
        val ds = TuChonNguon.xep(nguon.filter { dv.coApp(it.goi) }, dv.kho.thongKe(khoa), System.currentTimeMillis())
        if (ds.isEmpty()) { dv.hien(dv.getString(R.string.auto_khong_co_app, ten), dai = true); return }
        var truoc: Nguon? = null
        var dong = hongTruoc
        for (n in ds) {
            if (ma != phien.get()) return
            if (n.app == TuChonNguon.M3U && n.url.isNotEmpty() && !linkSong(n.url)) { hong(khoa, n); continue }
            val uid = dv.uidCua(n.goi) ?: continue
            // Thử lại chính app vừa hỏng (nguồn khác của nó, hay hết nguồn khác): đóng hẳn trước để nó mở lại từ đầu.
            if (dong == n.goi) { dv.adb("am force-stop $dong", 3000); dong = null }
            val chaySan = dv.dangChay(n.goi)
            dv.hien(truoc?.let { dv.getString(R.string.auto_chuyen, dv.tenApp(it.goi), ten, dv.tenApp(n.goi)) }
                ?: dv.getString(R.string.mo_kenh, ten, dv.tenApp(n.goi)))
            val tu = SystemClock.elapsedRealtime()
            val moDuoc = dv.mo(lenhMo(n))
            dong?.let { if (ngu(ma, 1500)) dv.adb("am force-stop $it", 3000) }
            dong = null
            if (!moDuoc) { hong(khoa, n); truoc = n; continue }
            when (choPhat(ma, n.goi, uid, if (chaySan) HAN_CHAY_SAN else HAN, tu)) {
                KetQua.PHAT -> {
                    dv.kho.ghiThongKe(khoa, n.khoa) { it.duoc(SystemClock.elapsedRealtime() - tu) }
                    if (dv.kho.cauHinh.doiNguon) theoDoi(ma, ten, khoa, nguon, n, uid)
                    return
                }
                KetQua.THOI -> return
                KetQua.KHONG -> {
                    hong(khoa, n)
                    dong = n.goi
                    truoc = n
                }
            }
        }
        // Hết nguồn: để app cuối trước mặt (màn đăng nhập, báo lỗi…) cho người xem tự xử lý.
        if (ma == phien.get()) dv.hien(dv.getString(R.string.auto_khong_nguon, ten), dai = true)
    }

    private fun hong(khoa: String, n: Nguon) = dv.kho.ghiThongKe(khoa, n.khoa) { it.hong(System.currentTimeMillis()) }

    /**
     * Chờ app [goi] lên tiếng trong [han] ms tính từ [tu]: PHAT; hết giờ: KHONG; người xem bấm phím (đăng nhập, chọn gói…),
     * màn Khoá trẻ em giữ tiếng hay lời gọi mới: THOI (để yên, không đóng app dưới tay người xem).
     */
    private fun choPhat(ma: Int, goi: String, uid: Int, han: Long, tu: Long): KetQua {
        // Kênh cũ của chính app này còn kêu trong một hai giây đầu: chưa hỏi vội.
        if (!ngu(ma, 2000)) return KetQua.THOI
        while (true) {
            if (dv.lucBamPhim > tu + 1500) return KetQua.THOI
            val dump = dv.adb("dumpsys audio", 4000)
            if (dump != null) {
                if (TuChonNguon.dangPhat(dump, uid)) return KetQua.PHAT
                if (TuChonNguon.giuTieng(dump) == KHOA_TRE) return KetQua.THOI
            } else if (dv.appTruoc == goi && coTiengPhim()) return KetQua.PHAT
            if (SystemClock.elapsedRealtime() - tu > han) return KetQua.KHONG
            if (!ngu(ma, 1000)) return KetQua.THOI
        }
    }

    /** Đang xem kênh vừa mở bằng nguồn [n]: mất tiếng [IM_TOI_DA] mà không phải do người xem thì đổi nguồn. */
    private fun theoDoi(ma: Int, ten: String, khoa: String, nguon: List<Nguon>, n: Nguon, uid: Int) {
        val batDau = SystemClock.elapsedRealtime()
        // App trước mặt lúc bắt đầu (thường là chính nguồn; tivi báo đổi cửa sổ chậm thì có khi còn là launcher): đổi sang app khác thì thôi.
        val goc = dv.appTruoc
        var imTu = 0L
        // Mất tiếng do người xem (bấm phím) hay app khác giữ tiếng: chờ có tiếng lại mới tính tiếp.
        var nhuong = false
        while (ngu(ma, BUOC_THEO_DOI)) {
            val bay = SystemClock.elapsedRealtime()
            if (bay - batDau > THEO_DOI_TOI_DA || dv.lucPhimDoiKenh > batDau) return
            val truoc = dv.appTruoc
            if (truoc != goc && truoc != n.goi) return
            if (coTiengPhim()) { imTu = 0; nhuong = false; continue }
            if (nhuong) continue
            if (imTu == 0L) imTu = bay
            if (dv.lucBamPhim > imTu - PHIM_TRUOC_IM) { nhuong = true; continue }
            if (bay - imTu < IM_TOI_DA) continue
            // Hỏi lại theo uid cho chắc (Android không báo chi tiết tiếng của app khác cho Map Voice); chưa nối ADB thì không
            // chắc được, không đổi.
            val dump = dv.adb("dumpsys audio", 4000)
            if (dump == null || TuChonNguon.dangPhat(dump, uid)) { imTu = 0; continue }
            val giu = TuChonNguon.giuTieng(dump)
            if (giu == KHOA_TRE) return
            if (giu != null && giu != n.goi) { nhuong = true; continue }
            dv.hien(dv.getString(R.string.auto_mat_tieng, ten, dv.tenApp(n.goi)), dai = true)
            hong(khoa, n)
            chay(ma, ten, khoa, nguon, hongTruoc = n.goi)
            return
        }
    }

    /**
     * Có tiếng phim / truyền hình đang phát (Android chỉ cho Map Voice biết có hay không, không biết của app nào): rẻ, hỏi
     * mỗi [BUOC_THEO_DOI]; trước khi đổi nguồn mới hỏi `dumpsys audio` theo uid.
     */
    private fun coTiengPhim(): Boolean = runCatching {
        dv.getSystemService(AudioManager::class.java).activePlaybackConfigurations.any {
            it.audioAttributes.usage in setOf(AudioAttributes.USAGE_MEDIA, AudioAttributes.USAGE_UNKNOWN, AudioAttributes.USAGE_GAME)
        }
    }.getOrDefault(false)

    /** Ngủ [ms] theo từng đoạn ngắn; false nếu việc này đã bị huỷ. */
    private fun ngu(ma: Int, ms: Long): Boolean {
        val het = SystemClock.elapsedRealtime() + ms
        while (true) {
            if (ma != phien.get()) return false
            val con = het - SystemClock.elapsedRealtime()
            if (con <= 0) return true
            try { Thread.sleep(minOf(con, 250)) } catch (_: InterruptedException) { return false }
        }
    }

    /**
     * Link luồng M3U còn trả lời trong 5 giây. Không tìm thấy máy chủ, hết giờ, 404 / 410, 5xx: chết. 401 / 403 có khi chỉ vì
     * thiếu header riêng của danh sách phát (M3U tự gửi): coi như còn, để M3U thử.
     */
    private fun linkSong(url: String): Boolean = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 5000; c.readTimeout = 5000; c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android TV) ExoPlayer")
            c.responseCode.let { it in 200..399 || it == 401 || it == 403 }
        } finally {
            c.disconnect()
        }
    }.getOrDefault(false)

    companion object {
        const val KHOA_TRE = "vn.tivikit.khoa"
        /** Chờ lên tiếng: app mở nguội (màn chào, tải danh sách kênh) / app đang chạy sẵn. */
        const val HAN = 15_000L
        const val HAN_CHAY_SAN = 10_000L
        /** Mất tiếng bao lâu thì đổi nguồn (anh chọn 9 giây, 1/11/2026). */
        const val IM_TOI_DA = 9_000L
        const val BUOC_THEO_DOI = 1_500L
        /**
         * Phím bấm trong khoảng này trước lúc thấy mất tiếng là người xem tự dừng: tiếng tắt tới lúc thấy còn trễ tới một
         * [BUOC_THEO_DOI] (TX3, 1/11/2026: 2 giây không đủ, bấm phím rồi tạm dừng vẫn bị đổi nguồn).
         */
        const val PHIM_TRUOC_IM = 5_000L
        /** Theo dõi tối đa 6 giờ cho một lần mở. */
        const val THEO_DOI_TOI_DA = 6 * 3_600_000L

        /** Lệnh mở kênh của nguồn [n], như ô Kênh nhanh (kenh/MoKenh). */
        fun lenhMo(n: Nguon): Intent = when (n.app) {
            TuChonNguon.VTVGO -> Intent(Intent.ACTION_VIEW, Uri.parse("vtvgo://play?mediaType=CHANNEL&contentId=${n.ma}")).setPackage(n.goi)
            // Deep link màn xem truyền hình của FPT Play bản tivi.
            TuChonNguon.FPT -> Intent(Intent.ACTION_VIEW, Uri.parse("https://fptplay.vn/xem-truyen-hinh/${n.ma}")).setPackage(n.goi)
            // Màn phát của M3U kèm mã kênh (như phím tắt Create Shortcut của M3U).
            TuChonNguon.M3U -> Intent(Intent.ACTION_VIEW).setClassName(TuChonNguon.GOI_M3U, TuChonNguon.M3U_PHAT)
                .putExtra(TuChonNguon.M3U_MA_KENH, n.ma.toIntOrNull() ?: 0)
            // Cổng nhận lệnh trợ lý giọng nói của TV360 bản tivi: tìm theo số kênh rồi tên kênh.
            else -> Intent("${n.goi}.VIEW", Uri.parse("content://${n.goi}/SWITCH_CHANNEL")).setClassName(n.goi, "${n.goi}.assistant.PostmanAssistant")
                .apply { if (n.so.isNotEmpty()) putExtra("number_channel", n.so) }.putExtra("name_channel", n.ten)
        }
    }
}
