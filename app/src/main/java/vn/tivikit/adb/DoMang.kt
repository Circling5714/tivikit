package vn.tivikit.adb

import android.net.Network
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Dò tivi trong mạng bằng cổng TCP (25/10/2026, anh Alex: thỉnh thoảng app không thấy tivi, phải tắt bật Wi-Fi tivi mới
 * quét ra). Wi-Fi của tivi hay ngủ để tiết kiệm điện: lời hỏi địa chỉ (ARP) và gói kết nối phải chờ tới lượt tivi thức
 * nghe, chậm hơn nửa giây, nên lượt dò chờ 0,4 giây bỏ sót tivi. Ở đây dò chờ đủ lâu, và phân biệt "máy có đó nhưng cổng
 * đóng" (ADB trên tivi đã tắt) với "không ai trả lời" (Wi-Fi tivi ngủ, tivi đổi IP, khác mạng) để báo đúng cách sửa.
 */
object DoMang {
    /** MO = cổng mở; DONG = máy trả lời nhưng cổng đóng (bị từ chối); IM = không trả lời. */
    enum class Cong { MO, DONG, IM }

    /** Một lần dò [host]:[port], chờ tối đa [ms]. [mang] = mạng Wi-Fi để socket đi đúng đường (null = theo mạng mặc định). */
    fun doCong(host: String, port: Int, ms: Int, mang: Network? = null): Cong {
        val s = Socket()
        return try {
            mang?.let { m -> runCatching { m.bindSocket(s) } }
            s.connect(InetSocketAddress(host, port), ms)
            Cong.MO
        } catch (e: Exception) {
            if (laTuChoi(e)) Cong.DONG else Cong.IM
        } finally {
            runCatching { s.close() }
        }
    }

    /**
     * Dò chờ lâu, [lan] lần, mỗi lần [ms]: lần đầu đánh thức Wi-Fi đang ngủ của tivi, lần sau tivi đã thức. Mở thì thôi
     * ngay; bị từ chối thì cũng thôi (cổng đóng thật, dò nữa không đổi).
     */
    fun doLau(host: String, port: Int, mang: Network? = null, ms: Int = 2500, lan: Int = 2): Cong {
        repeat(lan - 1) {
            val kq = doCong(host, port, ms, mang)
            if (kq != Cong.IM) return kq
        }
        return doCong(host, port, ms, mang)
    }

    /** Lỗi kết nối là "bị từ chối": máy có trên mạng nhưng không nghe ở cổng đó. */
    fun laTuChoi(e: Throwable): Boolean =
        generateSequence(e) { it.cause }.take(5).any { x -> x.message?.let { it.contains("ECONNREFUSED") || it.contains("refused", ignoreCase = true) } == true }
}
