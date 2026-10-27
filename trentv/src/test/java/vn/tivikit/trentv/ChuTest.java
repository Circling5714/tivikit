package vn.tivikit.trentv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Đầu ra của các lớp chạy trên tivi phải là ASCII, mỗi mục một dòng (TrenTv.boThoat đọc lại). */
public class ChuTest {
    @Test public void chuCoDauVaEmoji_thanhUXXXX() {
        assertEquals("S\\u01a1n T\\u00f9ng \\ud83d\\ude00", Chu.thoat("Sơn Tùng 😀"));
    }

    @Test public void tabXuongDongGachCheo_duocThoat() {
        assertEquals("a\\tb\\nc\\\\d\\u000d", Chu.thoat("a\tb\nc\\d\r"));
    }

    @Test public void dauRaToanAscii() {
        String s = Chu.thoat("Nhạc trữ tình \u0007 – “Bolero”");
        for (int i = 0; i < s.length(); i++) assertTrue(s, s.charAt(i) >= 0x20 && s.charAt(i) < 0x7f);
    }
}
