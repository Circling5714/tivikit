package vn.tivikit.mapvoice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimVideoTest {
    @Test
    fun linkTimMaHoaChuCoDau() {
        assertEquals("https://www.youtube.com/results?search_query=nh%E1%BA%A1c+piano", TimVideo.linkTim("nhạc piano"))
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", TimVideo.linkXem("dQw4w9WgXcQ"))
    }

    @Test
    fun videoDauBoQuaQuangCaoVaShorts() {
        val html = """<a href="/watch?v=AAAAAAAAAAA">quảng cáo</a> ... "reelItemRenderer":{"videoId":"SSSSSSSSSSS"} ...
            "videoRenderer":{"videoId":"BBBBBBBBBBB","thumbnail":{}} ... "videoRenderer":{"videoId":"CCCCCCCCCCC""""
        assertEquals("BBBBBBBBBBB", TimVideo.maDau(html))
        assertEquals("AAAAAAAAAAA", TimVideo.maDau("""<a href="/watch?v=AAAAAAAAAAA&t=1">"""))
        assertNull(TimVideo.maDau("<html>không có video</html>"))
    }
}
