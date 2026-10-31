package vn.tivikit.teleremote

import java.time.LocalDateTime
import java.time.ZoneId
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChuBotTest {
    private val mui = ZoneId.of("Asia/Ho_Chi_Minh")
    private fun luc(ngay: Int, gio: Int, phut: Int) = LocalDateTime.of(2026, 10, ngay, gio, phut).atZone(mui).toInstant().toEpochMilli()
    private val ten = mapOf("vn.tivikit.kenh" to "Kênh nhanh: VTV1", "org.smarttube.stable" to "SmartTube", "com.google.android.youtube.tv" to "YouTube")
    private fun tenApp(g: String) = ten[g] ?: g

    @Test
    fun ngayTrongTuan() {
        assertEquals("hằng ngày", ChuBot.ngay(listOf(1, 2, 3, 4, 5, 6, 7), false))
        assertEquals("T2–T6", ChuBot.ngay(listOf(5, 4, 3, 2, 1), false))
        assertEquals("T7, CN", ChuBot.ngay(listOf(6, 7), false))
        assertEquals("Mon, Wed, Fri", ChuBot.ngay(listOf(1, 3, 5), true))
    }

    @Test
    fun bangHenGio() {
        // Như lịch của nhà trên Skyworth: bật mở VTV1 lúc 6:30, tắt lúc 7:30; thêm một hẹn tắt tối nay, một mục đang tắt.
        val bay = luc(31, 21, 0)
        val j = JSONObject("""{"bayGio":$bay,"dichVuChay":true,"lich":[
            {"gio":7,"phut":30,"ngay":[1,2,3,4,5,6,7],"loai":"tat","bat":true},
            {"gio":6,"phut":30,"ngay":[1,2,3,4,5],"loai":"bat","bat":true,"goi":"vn.tivikit.kenh"},
            {"gio":9,"phut":0,"ngay":[6],"loai":"mo","bat":false,"goi":"org.smarttube.stable"}],
            "motLan":[{"id":"tat-${luc(31, 22, 30)}","luc":${luc(31, 22, 30)},"loai":"tat"},{"id":"mo-${luc(32 - 1, 23, 0) + 86_400_000}","luc":${luc(31, 23, 0) + 86_400_000},"loai":"mo","goi":"org.smarttube.stable"}]}""")
        val hg = ChuBot.henGio(j, "TX3 Mini", false, mui, ::tenApp)
        val dong = hg.chu.lines()
        assertEquals("⏰ Hẹn giờ · TX3 Mini", dong[0])
        assertEquals("• 06:30 · T2–T6 · bật tivi, mở Kênh nhanh: VTV1", dong[2])
        assertEquals("• 07:30 · hằng ngày · tắt tivi", dong[3])
        assertFalse(hg.chu.contains("09:00"))
        assertTrue(hg.chu, hg.chu.contains("• 22:30 · tắt tivi"))
        // Ngày khác thì ghi ngày.
        assertTrue(hg.chu, hg.chu.contains("• 1/11 23:00 · mở SmartTube"))
        assertEquals(listOf("tat-${luc(31, 22, 30)}" to "22:30 tắt tivi"), hg.motLan.take(1))
        assertTrue(ChuBot.henGio(JSONObject("""{"lich":[],"motLan":[],"dichVuChay":false}"""), "TV", true, mui, ::tenApp).chu.contains("Nothing scheduled."))
    }

    @Test
    fun khoaTreEm() {
        val bay = luc(31, 20, 0)
        val dangMo = JSONObject("""{"bat":true,"coOtp":true,"dichVuChay":true,"lyDo":"","moTamDen":${luc(31, 21, 30)},"giayHomNay":4800,"gioiHanHomNay":120,
            "appKhoa":["com.google.android.youtube.tv"]}""")
        val chu = ChuBot.khoa(dangMo, "TX3 Mini", bay, false, mui, ::tenApp)
        assertTrue(chu, chu.contains("Đang mở tạm tới 21:30"))
        assertTrue(chu, chu.contains("Hôm nay đã xem 1 giờ 20 phút / được 2 giờ"))
        assertTrue(chu, chu.contains("App đang khoá: YouTube"))
        assertTrue(chu, chu.contains("mã 6 số"))
        val hetGio = JSONObject("""{"bat":true,"lyDo":"het-gio","giayHomNay":7200,"gioiHanHomNay":120}""")
        assertTrue(ChuBot.khoa(hetGio, "TV", bay, true, mui, ::tenApp).contains("Locked: today's viewing time is used up"))
        assertTrue(ChuBot.khoa(JSONObject("""{"bat":false}"""), "TV", bay, false, mui, ::tenApp).contains("Khoá trẻ em đang tắt"))
    }

    @Test
    fun ketQuaMapVoice() {
        assertEquals("▶️ Đang phát video đầu tiên cho “nhạc piano” trên SmartTube.", ChuBot.mapVoice("yt-phat|nhạc piano", "org.smarttube.stable", false, ::tenApp))
        assertEquals("🔎 Searching “lofi” in SmartTube.", ChuBot.mapVoice("yt-tim|lofi", "org.smarttube.stable", true, ::tenApp))
        assertEquals("📺 Đang mở VTV3 trên net.fptplay.ottbox.", ChuBot.mapVoice("kenh|fptplay|VTV3", "net.fptplay.ottbox", false, ::tenApp))
        assertEquals("📱 Đã mở SmartTube.", ChuBot.mapVoice("mo-app|org.smarttube.stable", "org.smarttube.stable", false, ::tenApp))
        assertEquals("Tivi chưa có SmartTube hay TizenTube để mở YouTube.", ChuBot.mapVoice("loi|khong-co-smarttube", "", false, ::tenApp))
        // Câu để Trợ lý / câu Map Voice không làm: bot tự lo (app trùng tên, Trợ lý Google).
        assertNull(ChuBot.mapVoice("tro-ly|giá vàng", "", false, ::tenApp))
        assertNull(ChuBot.mapVoice("bo-qua|", "", false, ::tenApp))
        assertNull(ChuBot.mapVoice("", "", false, ::tenApp))
    }

    @Test
    fun soDoLamMoi() {
        // Bận thêm 100 user + 50 system trên tổng 800 (rảnh + chờ đĩa thêm 650).
        assertEquals(18, ChuBot.tuProcStat("cpu  100 0 100 700 100 0 0 0 0 0\ncpu  200 0 150 1300 150 0 0 0 0 0"))
        assertNull(ChuBot.tuProcStat("cpu  1 2 3 4 5"))
        assertEquals(58, ChuBot.tuNhietDo("58000\n"))
        assertEquals(45, ChuBot.tuNhietDo("45"))
        assertNull(ChuBot.tuNhietDo("0"))
        val gb = 1L shl 30
        val chu = ChuBot.lamMoi(ChuBot.SoDo(2 * gb, gb / 10, 90, 81, 9 * 86_400_000L, 16 * gb, gb / 2), "TX3 Mini", false)
        assertTrue(chu, chu.contains("RAM trống: 102 MB / 2,0 GB"))
        assertTrue(chu, chu.contains("CPU bận: 90% · Nhiệt độ chip: 81 °C"))
        listOf("RAM còn ít", "CPU đang quá tải", "Chip đang nóng", "Bộ nhớ gần đầy", "chạy liên tục 9 ngày").forEach { assertTrue(it, chu.contains(it)) }
        assertTrue(ChuBot.lamMoi(ChuBot.SoDo(2 * gb, gb, 20, null, 3_600_000, 16 * gb, 8 * gb), "TV", true).endsWith("• The TV looks fine."))
    }
}
