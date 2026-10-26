package vn.tivikit.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket

/** Dò cổng: phân biệt mở / bị từ chối (ADB tắt) / không trả lời (Wi-Fi tivi ngủ, sai IP). */
class DoMangTest {
    @Test fun moDongIm() {
        ServerSocket(0, 50, InetAddress.getLoopbackAddress()).use { ss ->
            assertEquals(DoMang.Cong.MO, DoMang.doCong("127.0.0.1", ss.localPort, 2000))
        }
        // Cổng vừa đóng: máy có đó và từ chối (Windows thử lại vài lần trước khi báo, nên chờ lâu).
        val dong = ServerSocket(0, 50, InetAddress.getLoopbackAddress()).use { it.localPort }
        assertEquals(DoMang.Cong.DONG, DoMang.doCong("127.0.0.1", dong, 5000))
        // Địa chỉ dành cho tài liệu (TEST-NET-1): không ai trả lời.
        assertEquals(DoMang.Cong.IM, DoMang.doCong("192.0.2.1", 5555, 300))
    }

    @Test fun laTuChoi() {
        assertTrue(DoMang.laTuChoi(java.net.ConnectException("failed to connect to /192.168.1.20 (port 5555) after 700ms: isConnected failed: ECONNREFUSED (Connection refused)")))
        assertTrue(DoMang.laTuChoi(java.io.IOException("adb", java.net.ConnectException("Connection refused: connect"))))
        assertFalse(DoMang.laTuChoi(java.net.SocketTimeoutException("failed to connect to /192.168.1.20 (port 5555) after 700ms")))
        assertFalse(DoMang.laTuChoi(java.net.ConnectException("isConnected failed: EHOSTUNREACH (No route to host)")))
    }
}
