package vn.tivikit.cast

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Kênh Lounge (28/10/2026): tách tin từ thân trả về / luồng long-poll, mã hoá tin gửi đi. */
class TinNhanTest {
    // Dạng thân mở phiên: tiền tố chống XSSI, các khúc "độ dài\n[...]" có xuống dòng bên trong.
    private val moPhien = ")]}'\n174\n[[0,[\"c\",\"SID-1\",\"\",8]\n]\n,[1,[\"S\",\"GS-1\"]]\n" +
        ",[2,[\"loungeStatus\",{\"devices\":\"[{\\\"type\\\":\\\"LOUNGE_SCREEN\\\",\\\"id\\\":\\\"x\\\"}]\"}]]\n]\n"

    @Test fun tach_thanMoPhien() {
        val ds = TinNhan.tach(moPhien)
        assertEquals(listOf("c", "S", "loungeStatus"), ds.map { it.ten })
        assertEquals(listOf(0, 1, 2), ds.map { it.aid })
        assertEquals("SID-1", (ds[0].du as JSONArray).getString(0))
        assertEquals("GS-1", ds[1].du)
        assertEquals("LOUNGE_SCREEN", JSONArray(ds[2].chu("devices")).getJSONObject(0).getString("type"))
    }

    @Test fun luongDenTungManh_vaNgoacTrongChuoi() {
        val khuc = "[[7,[\"setPlaylist\",{\"videoId\":\"dQw4w9WgXcQ\",\"currentTime\":\"12\",\"ctt\":\"a]b[\\\"c\"}]],[8,[\"noop\"]]]"
        val bo = KhucJson()
        val nua = khuc.length / 2
        assertTrue(bo.an("123\n" + khuc.substring(0, nua)).isEmpty())
        val xong = bo.an(khuc.substring(nua) + "\n")
        assertEquals(1, xong.size)
        val ds = TinNhan.tuKhuc(xong[0])
        assertEquals("setPlaylist", ds[0].ten)
        assertEquals("dQw4w9WgXcQ", ds[0].chu("videoId"))
        assertEquals("a]b[\"c", ds[0].chu("ctt"))
        assertEquals("noop", ds[1].ten)
        assertNull(ds[1].du)
    }

    @Test fun khucHong_boQua() {
        assertTrue(TinNhan.tuKhuc("[[1,[\"pause\"]],").isEmpty())
        assertEquals(listOf("pause"), TinNhan.tach("2\n[[1,[\"pause\"]]]\n10\n[[2,\"x\"]]\n").map { it.ten })
    }

    @Test fun thanGui() {
        val s = TinNhan.thanGui(listOf("onStateChange" to mapOf("state" to "1", "currentTime" to "1.5"), "nowPlaying" to emptyMap()), 4)
        assertEquals("count=2&ofs=4&req0__sc=onStateChange&req0_state=1&req0_currentTime=1.5&req1__sc=nowPlaying", s)
        assertEquals("a+b%26c", TinNhan.maHoa("a b&c"))
    }

    @Test fun chu_rongLaKhong() {
        val t = TinNhan(1, "seekTo", JSONObject().put("newTime", "").put("x", "5"))
        assertNull(t.chu("newTime"))
        assertEquals("5", t.chu("x"))
    }
}
