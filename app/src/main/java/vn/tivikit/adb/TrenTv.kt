package vn.tivikit.adb

/**
 * Các lớp Java chạy trên tivi bằng app_process dưới quyền shell (27/10/2026, module trentv/, assets/tren-tv.jar — ý từ
 * tv-remote của HoangTran0410, MIT): như máy chủ scrcpy, không cài gì lên tivi, chỉ đặt một file vài KB vào /data/local/tmp
 * (đúng bản thì khỏi đẩy lại). TenApp: tên và ảnh của app, hỏi PackageManager của chính tivi — một lần chạy cho cả danh
 * sách, thay vì đọc từng APK (TenApk). Thử trên TX3 Mini: tên 99 gói trong ~2 giây; ảnh 12 app ~1,9 giây, ~3,8 KB mỗi app.
 */
class TrenTv(private val adb: KetNoiAdb) {
    /** Tên một app trên tivi, kèm versionCode để biết lúc app cập nhật. */
    data class TenTv(val versionCode: Long, val ten: String)

    /** Ảnh của app: [banner] = banner ngang của app tivi, không thì biểu tượng; WebP. */
    class AnhTv(val versionCode: Long, val banner: Boolean, val webp: ByteArray)

    /** Tên mọi app trên tivi (gói → tên); [lay] đọc file trong assets, chỉ gọi khi phải đẩy lên tivi. */
    fun tenApp(lay: () -> ByteArray): Map<String, TenTv> {
        datLen(lay)
        return docTen(adb.shell(lenh("TenApp ten"), 60))
    }

    /** Ảnh của [goi] (banner, không có thì biểu tượng), cạnh dài nhất [canh] px; gói nào không vẽ được thì không có trong kết quả. */
    fun anhApp(lay: () -> ByteArray, goi: List<String>, canh: Int = 320): Map<String, AnhTv> {
        val ds = goi.filter { ThaoTacTv.TEN_GOI.matches(it) }
        if (ds.isEmpty()) return emptyMap()
        datLen(lay)
        return docAnh(adb.shell(lenh("TenApp anh $canh ${ds.joinToString(" ")}"), 60))
    }

    /** Đặt file lên tivi khi tivi chưa có đúng bản (so SHA-256; tivi không có sha256sum thì đẩy lại, file chỉ vài KB). */
    private fun datLen(lay: () -> ByteArray) {
        val coSan = adb.shell("sha256sum $DUONG_DAN 2>/dev/null").trim().substringBefore(' ')
        val jar = lay()
        if (coSan != vn.tivikit.dulieu.sha256(jar)) adb.ghiFile(DUONG_DAN, jar)
    }

    companion object {
        const val ASSET = "tren-tv.jar"
        const val DUONG_DAN = "/data/local/tmp/tivikit-tren-tv.jar"

        /** Lệnh chạy một lớp trong file (lớp + tham số, ASCII). */
        fun lenh(lopVaThamSo: String): String = "CLASSPATH=$DUONG_DAN app_process / vn.tivikit.trentv.$lopVaThamSo"

        /** Dòng "gói TAB versionCode TAB tên" của TenApp ten; dòng lỗi / sai dạng thì bỏ. */
        fun docTen(out: String): Map<String, TenTv> = out.lineSequence().mapNotNull { l ->
            val c = l.trimEnd('\r').split('\t')
            if (c.size != 3 || !ThaoTacTv.TEN_GOI.matches(c[0])) return@mapNotNull null
            val ma = c[1].toLongOrNull() ?: return@mapNotNull null
            c[0] to TenTv(ma, boThoat(c[2]).trim())
        }.toMap()

        /** Dòng "gói TAB versionCode TAB B|I TAB base64" của TenApp anh. */
        fun docAnh(out: String): Map<String, AnhTv> = out.lineSequence().mapNotNull { l ->
            val c = l.trimEnd('\r').split('\t')
            if (c.size != 4 || !ThaoTacTv.TEN_GOI.matches(c[0]) || c[2] !in setOf("B", "I")) return@mapNotNull null
            val ma = c[1].toLongOrNull() ?: return@mapNotNull null
            val anh = runCatching { java.util.Base64.getDecoder().decode(c[3]) }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            c[0] to AnhTv(ma, c[2] == "B", anh)
        }.toMap()

        /** Ngược của Chu.thoat (module trentv): \\uXXXX, \\t, \\n, \\\\. */
        fun boThoat(s: String): String {
            if ('\\' !in s) return s
            val b = StringBuilder(s.length)
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c != '\\' || i + 1 >= s.length) { b.append(c); i++; continue }
                when (s[i + 1]) {
                    't' -> { b.append('\t'); i += 2 }
                    'n' -> { b.append('\n'); i += 2 }
                    '\\' -> { b.append('\\'); i += 2 }
                    'u' -> {
                        val ma = s.substring(i + 2, minOf(i + 6, s.length)).toIntOrNull(16)
                        if (ma != null && i + 6 <= s.length) { b.append(ma.toChar()); i += 6 } else { b.append(c); i++ }
                    }
                    else -> { b.append(c); i++ }
                }
            }
            return b.toString()
        }
    }
}
