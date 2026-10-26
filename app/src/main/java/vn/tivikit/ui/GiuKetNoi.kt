package vn.tivikit.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import vn.tivikit.adb.DoMang
import vn.tivikit.t

/**
 * Giữ kết nối tivi (25/10/2026, anh Alex: "tivi rớt kết nối thì tự kết nối lại"). Chỉ chạy lúc app đang hiện trên màn
 * hình — app nằm nền thì Android đóng băng app, cắt mạng của nó, giữ cũng vô ích:
 *  - đang nối: 15 giây hỏi tivi một lần (echo); không trả lời thì tự nối lại đúng địa chỉ cũ, thử thưa dần (5, 10, 20
 *    rồi 30 giây một lần) tới khi được, đang ở màn nào giữ nguyên màn đó. Hỏi đều cũng giữ Wi-Fi tivi khỏi ngủ sâu.
 *  - chưa nối, đang ở màn chính: tự nối tivi kết nối thành công gần nhất khi nó có mặt trên mạng (mở app, bật tivi).
 * Bấm Ngắt kết nối / Tắt ADB thì thôi tự nối tới lần kết nối sau. Tắt cả cơ chế ở thẻ tivi màn chính.
 */
class GiuKetNoi(private val vm: PhienLamViec) {
    /** ON: ổn (hoặc không có gì để làm); DANG_NOI: đang dò / nối lại; CHO: chưa nối lại được, chờ lượt sau ([lyDo]). */
    enum class TinhTrang { ON, DANG_NOI, CHO }

    var tinhTrang by mutableStateOf(TinhTrang.ON)
        private set
    var lyDo by mutableStateOf("")
        private set

    private val prefs = vm.ctx.getSharedPreferences("tvsach", Context.MODE_PRIVATE)
    var bat by mutableStateOf(prefs.getBoolean("tu_noi_lai", true))
        private set
    /** Tivi tự nối: "ip:cổng" của lần kết nối thành công gần nhất; null khi người dùng tự ngắt / tắt ADB. */
    var dich by mutableStateOf(prefs.getString("tu_noi", null)?.takeIf { it.isNotBlank() })
        private set

    private var vong: Job? = null
    private val nhac = Channel<Unit>(Channel.CONFLATED)
    /** Tự nối lỗi dù tivi có trả lời (tivi hỏi "Cho phép gỡ lỗi?" chưa ai bấm…): thôi tới lần app hiện lên sau, khỏi hỏi mãi. */
    private var thoiTuNoi = false

    fun doiBat(b: Boolean) {
        bat = b
        prefs.edit().putBoolean("tu_noi_lai", b).apply()
        if (b) nhac.trySend(Unit) else lamOn()
    }

    /** Vừa kết nối thành công [host]:[port] (sauKhiKetNoi): tivi này là tivi tự nối. */
    fun daNoi(host: String, port: Int) {
        dich = "$host:$port"
        prefs.edit().putString("tu_noi", dich).apply()
        thoiTuNoi = false
        lamOn()
    }

    /** Người dùng tự ngắt kết nối / tắt ADB: thôi tự nối. */
    fun boDich() {
        dich = null
        prefs.edit().remove("tu_noi").apply()
        lamOn()
    }

    /** App hiện lên (ON_START): bắt đầu canh, hỏi ngay. */
    fun hien() {
        thoiTuNoi = false
        if (vong?.isActive != true) vong = vm.viewModelScope.launch { canh() }
        nhac.trySend(Unit)
    }

    /** App xuống nền (ON_STOP): thôi canh. */
    fun an() {
        vong?.cancel(); vong = null
        if (tinhTrang == TinhTrang.DANG_NOI) lamOn()
    }

    private suspend fun canh() {
        var hong = 0
        while (true) {
            // Ổn thì 15 giây một lượt; đang hỏng thì 5, 10, 20 rồi 30 giây; có ai nhắc thì hỏi ngay.
            withTimeoutOrNull(if (hong == 0) 15_000L else listOf(5_000L, 10_000L, 20_000L).getOrElse(hong - 1) { 30_000L }) { nhac.receive() }
            // Đang có việc (dọn, cài…): việc đó tự nối lại khi cần (damBaoKetNoi), không chen vào.
            if (!bat || vm.dangBan) continue
            hong = if (mot()) 0 else hong + 1
        }
    }

    /** Một lượt; true = ổn hoặc không có gì để làm. */
    private suspend fun mot(): Boolean {
        val d = vm.adb.diemCuoi
        if (vm.thongTin != null && d != null) return giu(d.first, d.second)
        val (host, port) = tach(dich) ?: return lamOn()
        if (thoiTuNoi || vm.man != Man.CHINH) return lamOn()
        return tuNoi(host, port)
    }

    /** Đang nối: tivi còn trả lời không; không thì nối lại đúng địa chỉ đó. */
    private suspend fun giu(host: String, port: Int): Boolean {
        if (vm.io { vm.adb.conSong(5) }) return lamOn()
        if (vm.dangBan) return true
        tinhTrang = TinhTrang.DANG_NOI
        val kq = vm.io { DoMang.doLau(host, port, vm.mangDo(host)) }
        if (kq == DoMang.Cong.MO && vm.io { vm.adb.ketNoiLai() && vm.adb.conSong() }) return lamOn()
        return cho(kq, port)
    }

    /** Chưa nối, đang ở màn chính: tivi lần trước có mặt trên mạng thì nối như bấm Nối lại. */
    private suspend fun tuNoi(host: String, port: Int): Boolean {
        tinhTrang = TinhTrang.DANG_NOI
        val kq = vm.io { DoMang.doLau(host, port, vm.mangDo(host)) }
        if (kq != DoMang.Cong.MO) return cho(kq, port)
        // Trong lúc dò người dùng đã bấm việc khác: để yên.
        if (vm.dangBan || vm.thongTin != null || vm.man != Man.CHINH) return lamOn()
        vm.ip = host; vm.cong = port.toString()
        vm.ketNoi()
        snapshotFlow { vm.dangBan }.first { !it }
        if (vm.thongTin == null) thoiTuNoi = true
        return lamOn()
    }

    private fun cho(kq: DoMang.Cong, port: Int): Boolean {
        tinhTrang = TinhTrang.CHO
        lyDo = when {
            kq == DoMang.Cong.DONG && port == 5555 -> t("tivi đã tắt ADB qua mạng (thường sau khi khởi động lại hoặc rút điện), bật lại Gỡ lỗi trên tivi.",
                "network ADB is off on the TV (usually after a reboot or power cut); turn debugging back on.")
            kq == DoMang.Cong.DONG -> t("Gỡ lỗi không dây đã đổi cổng (tivi vừa khởi động lại), kết nối lại bằng cổng mới.",
                "Wireless debugging changed its port (the TV restarted); connect again with the new port.")
            kq == DoMang.Cong.MO -> t("tivi có trả lời nhưng chưa nối lại được, app đang thử tiếp.", "the TV answers but won't reconnect yet; still trying.")
            else -> t("chưa thấy tivi trên mạng (tivi tắt, Wi-Fi tivi ngủ hoặc đổi IP). Tivi có mặt là app tự nối lại.",
                "the TV isn't on the network (off, Wi-Fi asleep or new IP). The app reconnects once it's back.")
        }
        return false
    }

    private fun lamOn(): Boolean { tinhTrang = TinhTrang.ON; lyDo = ""; return true }

    private fun tach(s: String?): Pair<String, Int>? {
        val i = s?.lastIndexOf(':') ?: return null
        if (i <= 0) return null
        val p = s.substring(i + 1).toIntOrNull() ?: return null
        return s.substring(0, i) to p
    }
}
