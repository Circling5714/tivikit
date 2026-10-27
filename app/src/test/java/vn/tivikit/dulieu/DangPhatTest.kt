package vn.tivikit.dulieu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Thẻ Đang phát: đọc `dumpsys media_session` (dạng của TX3 Mini, Android 9; tên bài trong mẫu là chữ tự đặt). */
class DangPhatTest {
    private val DUMP = """
MEDIA SESSION SERVICE (dumpsys media_session)
2 sessions listeners.
Global priority session is null
User Records:
Record for full_user=0
  Media button session is org.smarttube.stable/org.smarttube.stable (userId=0)
  Sessions Stack - have 2 sessions:
    org.smarttube.stable org.smarttube.stable/org.smarttube.stable (userId=0)
      ownerPid=31620, ownerUid=10061, userId=0
      package=org.smarttube.stable
      launchIntent=null
      active=true
      flags=3
      controllers: 2
      state=PlaybackState {state=3, position=93322, buffered position=96040, speed=1.0, updated=21980869, actions=2360191, custom actions=[], active item id=0, error=null}
      audioAttrs=AudioAttributes: usage=USAGE_MEDIA content=CONTENT_TYPE_UNKNOWN flags=0x0 tags= bundle=null
      metadata:size=7, description=Phim hoạt hình tập 26 - Kênh Thiếu Nhi, Kênh Thiếu Nhi • 9,3 Tr lượt xem • Ngày xuất bản: 8 thg 5, 2023, null
      queueTitle=null, size=0
    com.example.radio com.example.radio/Radio (userId=0)
      package=com.example.radio
      active=false
      state=PlaybackState {state=2, position=5000, buffered position=0, speed=0.0, updated=100, actions=0, custom actions=[], active item id=0, error=null}
      metadata: size=0, description=null, null, null
Audio playback (lastly played comes first)
  uid=10061 packages=org.smarttube.stable
""".trimIndent()

    @Test fun tuDump_haiPhien() {
        val ds = DangPhat.tuDump(DUMP)
        assertEquals(2, ds.size)
        val st = ds[0]
        assertEquals("org.smarttube.stable", st.goi)
        assertEquals(true, st.hoatDong)
        assertEquals(DangPhat.PHAT, st.tt)
        assertEquals(93322L, st.viTri)
        assertEquals(1f, st.tocDo)
        assertEquals(21980869L, st.capNhat)
        assertEquals("Phim hoạt hình tập 26 - Kênh Thiếu Nhi", st.tieuDe)
        assertEquals("Kênh Thiếu Nhi • 9,3 Tr lượt xem • Ngày xuất bản: 8 thg 5, 2023", st.phu)
        val radio = ds[1]
        assertEquals(false, radio.hoatDong)
        assertEquals(DangPhat.DUNG, radio.tt)
        assertNull(radio.tieuDe)
    }

    @Test fun tachMoTa() {
        assertEquals("Bài A" to "Ca sĩ B", DangPhat.tachMoTa("Bài A, Ca sĩ B, null"))
        assertEquals("Bài A" to null, DangPhat.tachMoTa("Bài A, null, null"))
        assertEquals(null to null, DangPhat.tachMoTa("null, null, null"))
        assertEquals("Bài A" to "mô tả", DangPhat.tachMoTa("Bài A, null, mô tả"))
        // Không có tên, chỉ có dòng phụ: dòng phụ lên làm tên.
        assertEquals("Ca sĩ B" to null, DangPhat.tachMoTa("null, Ca sĩ B, null"))
        // Tên có dấu phẩy: phần sau rơi xuống dòng phụ, vẫn đủ chữ.
        assertEquals("Doraemon" to "Nobita và bạn bè, Kênh X", DangPhat.tachMoTa("Doraemon, Nobita và bạn bè, Kênh X, null"))
    }

    @Test fun chon_dangPhatTruocRoiMoiToiDangDungOTruoc() {
        fun p(goi: String, tt: Int, hd: Boolean = true) = DangPhat.Phien(goi, hd, tt, 0, 1f, 0, null, null)
        val dung = p("a.dung", DangPhat.DUNG)
        val phat = p("b.phat", DangPhat.PHAT)
        assertEquals(phat, DangPhat.chon(listOf(dung, phat), null))
        // Chỉ còn phiên đang dừng: hiện khi app của nó ở trước màn hình (rời app đang dừng thì thẻ thôi hiện).
        assertNull(DangPhat.chon(listOf(dung), "x.khac"))
        assertEquals(dung, DangPhat.chon(listOf(dung), "a.dung"))
        assertNull(DangPhat.chon(listOf(p("c.phat", DangPhat.PHAT, hd = false)), null))
        assertEquals(DangPhat.CHO, DangPhat.chon(listOf(p("d.tai", DangPhat.CHO)), null)!!.tt)
    }

    @Test fun viTriLuc_dangPhatThiCongThoiGian() {
        val phat = DangPhat.Phien("a", true, DangPhat.PHAT, 60_000, 1.5f, 1_000_000, null, null)
        assertEquals(60_000L + 15_000L, DangPhat.viTriLuc(phat, 1_010_000))
        assertEquals(60_000L, DangPhat.viTriLuc(phat.copy(tt = DangPhat.DUNG), 1_010_000))
        // Mốc sửa lại (YouTube trên Google TV báo mốc lùi): tính từ mốc đó.
        assertEquals(60_000L + 3_000L, DangPhat.viTriLuc(phat, 1_010_000, moc = 1_008_000))
    }

    @Test fun dongHo() {
        assertEquals("0:00", DangPhat.dongHo(0))
        assertEquals("4:05", DangPhat.dongHo(245_000))
        assertEquals("1:02:03", DangPhat.dongHo(3_723_000))
    }
}
