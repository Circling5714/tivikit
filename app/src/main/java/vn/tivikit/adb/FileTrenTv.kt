package vn.tivikit.adb

/**
 * Gửi ảnh / video / file lên tivi (25/10/2026): thư mục Download/TiviKit, tên không trùng, báo MediaStore có file
 * mới (app xem ảnh / video mới thấy), mở file bằng app trên tivi.
 */
class FileTrenTv(private val adb: KetNoiAdb) {
    private fun sh(lenh: String) = adb.shell(lenh).replace("\r", "")

    /** Tạo thư mục (nếu chưa có), trả về tên các file đang có trong đó. */
    fun chuanBi(): Set<String> =
        sh("mkdir -p '$THU_MUC' && ls -1 '$THU_MUC' 2>/dev/null").lines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    fun gui(ten: String, nguon: java.io.InputStream, co: Long, tienDo: (Long) -> Unit): String {
        val duongDan = "$THU_MUC/$ten"
        adb.dayLen(duongDan, nguon, co, tienDo)
        // Android 11+ tự ghi vào MediaStore khi file tạo qua /storage/emulated; máy cũ cần báo quét.
        runCatching { sh("am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d 'file://$duongDan' >/dev/null 2>&1") }
        return duongDan
    }

    /**
     * Mở file bằng app trên tivi. Ưu tiên địa chỉ content:// của MediaStore (app đời mới không đọc thẳng /sdcard được),
     * không có thì file://. False nếu tivi không có app nào mở được loại file này.
     */
    fun mo(duongDan: String, kieu: String): Boolean {
        require(duongDan.none { it in "\"'\$`\\" } && kieu.none { it in "\"'\$`\\ " }) { "duongDan" }
        val id = Regex("""_id=(\d+)""").find(sh("content query --uri content://media/external/file --projection _id --where \"_data='$duongDan'\" 2>/dev/null"))
            ?.groupValues?.get(1)
        val bang = when { kieu.startsWith("image/") -> "images/media"; kieu.startsWith("video/") -> "video/media"; kieu.startsWith("audio/") -> "audio/media"; else -> "file" }
        val diaChi = listOfNotNull(id?.let { "content://media/external/$bang/$it" }, "file://$duongDan")
        return diaChi.any { d ->
            !sh("am start -a android.intent.action.VIEW -d '$d' -t '$kieu' --grant-read-uri-permission 2>&1").contains("Error")
        }
    }

    companion object {
        const val THU_MUC = "/storage/emulated/0/Download/TiviKit"

        /** Bỏ ký tự làm hỏng lệnh shell / đường dẫn; giữ chữ có dấu. */
        fun tenAnToan(ten: String): String =
            ten.replace(Regex("[\\u0000-\\u001f/\\\\\"'`$*?<>|:]"), "_").trim().trimStart('.').ifEmpty { "file" }.take(120)

        /** "a.jpg" đã có thì "a (2).jpg", "a (3).jpg"… */
        fun tenKhongTrung(ten: String, coSan: Set<String>): String {
            if (ten !in coSan) return ten
            val cham = ten.lastIndexOf('.').takeIf { it > 0 } ?: ten.length
            val goc = ten.substring(0, cham); val duoi = ten.substring(cham)
            return (2..9999).asSequence().map { "$goc ($it)$duoi" }.first { it !in coSan }
        }
    }
}
