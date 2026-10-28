package vn.tivikit.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Tên video tra bằng oEmbed cho thẻ Đang phát (28/10/2026): SmartTube không gửi tên bài khi mở video từ đường link. */
class TenVideoTest {
    @Test fun tuJson() {
        val j = """{"title":"Gửi Âu Lo Theo Mây Gió","author_name":"DAN.S","type":"video","provider_name":"YouTube"}"""
        assertEquals("Gửi Âu Lo Theo Mây Gió" to "DAN.S", TenVideo.tuJson(j))
        assertNull(TenVideo.tuJson("""{"title":"  "}"""))
        assertNull(TenVideo.tuJson("Not Found"))
    }

    @Test fun maSai_khongTra() {
        assertNull(TenVideo.lay("abc"))
    }
}
