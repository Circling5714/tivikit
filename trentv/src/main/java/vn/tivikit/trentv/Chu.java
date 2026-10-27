package vn.tivikit.trentv;

/** Chữ in ra cho TiviKit đọc qua ADB. */
final class Chu {
    private Chu() { }

    /**
     * \, tab và xuống dòng thoát bằng \; ký tự điều khiển và ngoài ASCII (chữ có dấu, emoji) viết \\uXXXX. Mỗi dòng in ra là
     * một mục, cột cách nhau bằng tab; cả đầu ra là ASCII, TrenTv.boThoat đọc lại.
     */
    static String thoat(CharSequence s) {
        StringBuilder b = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\') b.append("\\\\");
            else if (c == '\t') b.append("\\t");
            else if (c == '\n') b.append("\\n");
            else if (c < 0x20 || c >= 0x7f) {
                String h = Integer.toHexString(c);
                b.append("\\u");
                for (int k = h.length(); k < 4; k++) b.append('0');
                b.append(h);
            } else b.append(c);
        }
        return b.toString();
    }
}
