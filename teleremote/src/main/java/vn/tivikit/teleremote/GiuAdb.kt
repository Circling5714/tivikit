package vn.tivikit.teleremote

import java.io.File
import kotlin.concurrent.thread

/**
 * Giữ kết nối ADB tới chính tivi (127.0.0.1:5555) cho các lệnh ngắn: như vòng nghe của Map Voice (mapvoice/Nghe.kt) nhưng
 * không chạy logcat. Mất kết nối thì nối lại, chờ lâu dần tới [CHO_TOI_DA]. Tivi hỏi "Cho phép gỡ lỗi?" thì gọi [dangHoi];
 * người dùng không cho phép thì thôi nối tới khi TiviKit gọi [noiLai] (khỏi hỏi mãi).
 */
class GiuAdb(private val thuMuc: File, private val dangHoi: () -> Unit) {
    enum class TrangThai(val ma: String) {
        DANG_NOI("dang-noi"), DANG_HOI("dang-hoi"), DA_NOI("da-noi"), KHONG_CO("khong-co"), TU_CHOI("tu-choi"), LOI("loi"), TAT("tat")
    }

    @Volatile var trangThai = TrangThai.DANG_NOI
        private set
    @Volatile private var phien: PhienAdb? = null
    @Volatile private var chay = false
    @Volatile private var duocNoiLai = false
    private val danhThuc = Object()

    fun batDau() {
        if (chay) return
        chay = true
        thread(name = "TeleRemote-adb", isDaemon = true) { vong() }
    }

    fun dung() {
        chay = false
        phien?.close()
        synchronized(danhThuc) { danhThuc.notifyAll() }
    }

    /** Nối lại ngay (sau khi người dùng đã từ chối hộp hỏi, hay đang chờ giữa hai lần thử). */
    fun noiLai() {
        synchronized(danhThuc) { duocNoiLai = true; danhThuc.notifyAll() }
    }

    /** Chạy một lệnh shell ngắn trên kết nối đang mở; null nếu chưa nối (hay quá [choToiDa] ms). */
    fun lenh(lenh: String, choToiDa: Long = 5000): String? = phien?.chay(lenh, choToiDa)

    private fun vong() {
        var cho = CHO_DAU
        while (chay) {
            trangThai = TrangThai.DANG_NOI
            val p = PhienAdb(thuMuc)
            when (p.noi { trangThai = TrangThai.DANG_HOI; dangHoi() }) {
                PhienAdb.KetQua.DA_NOI -> {
                    trangThai = TrangThai.DA_NOI
                    val luc = System.currentTimeMillis()
                    phien = p
                    p.docMai()
                    phien = null
                    // Giữ được lâu thì nối lại nhanh; rớt ngay thì chờ lâu dần.
                    cho = if (System.currentTimeMillis() - luc > 60_000) CHO_DAU else (cho * 2).coerceAtMost(CHO_TOI_DA)
                    trangThai = TrangThai.DANG_NOI
                    nghi(cho)
                }
                PhienAdb.KetQua.CHUA_CHO_PHEP -> {
                    trangThai = TrangThai.TU_CHOI
                    p.close()
                    choNoiLai()
                    cho = CHO_DAU
                }
                PhienAdb.KetQua.KHONG_CO -> {
                    trangThai = TrangThai.KHONG_CO
                    nghi(cho)
                    cho = (cho * 2).coerceAtMost(CHO_TOI_DA)
                }
                PhienAdb.KetQua.LOI -> {
                    trangThai = TrangThai.LOI
                    nghi(cho)
                    cho = (cho * 2).coerceAtMost(CHO_TOI_DA)
                }
            }
            p.close()
        }
        trangThai = TrangThai.TAT
    }

    private fun nghi(ms: Long) = synchronized(danhThuc) {
        if (!duocNoiLai && chay) danhThuc.wait(ms)
        duocNoiLai = false
    }

    private fun choNoiLai() = synchronized(danhThuc) {
        while (chay && !duocNoiLai) danhThuc.wait()
        duocNoiLai = false
    }

    companion object {
        private const val CHO_DAU = 3_000L
        private const val CHO_TOI_DA = 120_000L
    }
}
