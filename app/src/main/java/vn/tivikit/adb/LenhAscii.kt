package vn.tivikit.adb

/**
 * Lệnh gửi được qua libadb. libadb 3.1.1 cấp bộ đệm theo số ký tự của lệnh rồi ghi byte UTF-8 (AdbProtocol.generateOpen),
 * nên lệnh có chữ ngoài ASCII hỏng với BufferOverflowException: tên kênh "Hà Nội 1", gửi file "Ảnh cưới.jpg", thư mục có
 * dấu trên USB hay ở Dòng lệnh, liên kết app có dấu (28/10/2026). Lệnh như vậy gửi dạng base64, shell trên tivi giải ra rồi
 * chạy bằng eval: vẫn cùng shell, stdin vẫn là luồng adb (cài app, ghi file đẩy byte vào stdin). adbd chạy cả `shell:` lẫn
 * `exec:` bằng `sh -c`; toybox có base64 từ Android 6.
 */
object LenhAscii {
    fun doi(dich: String): String {
        if (dich.all { it.code < 0x80 }) return dich
        val loai = listOf("shell:", "exec:").firstOrNull { dich.startsWith(it) } ?: return dich
        val b64 = java.util.Base64.getEncoder().encodeToString(dich.removePrefix(loai).toByteArray(Charsets.UTF_8))
        return "${loai}eval \"\$(echo $b64 | base64 -d)\""
    }
}
