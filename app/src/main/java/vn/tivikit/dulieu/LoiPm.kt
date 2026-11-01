package vn.tivikit.dulieu

/**
 * Lỗi của lệnh `pm` trên tivi (1/11/2026, thử Khôi phục trên TX3): Android in "Exception occurred while executing:" rồi
 * nguyên stack trace Java, màn hình (chỉ hiện vài dòng nhật ký cuối) bị một lỗi che hết. Ở đây gọn lại một dòng lý do.
 */
object LoiPm {
    /**
     * pm báo tivi không có gói ("Unknown package"): gói đã gỡ hẳn sau khi dọn (bản cập nhật của hãng, chủ tivi tự gỡ) hay
     * mẫu tivi ghi gói mà tivi này không có. Gói hệ thống chỉ gỡ khỏi người dùng thì pm vẫn biết, không báo thế này.
     */
    fun khongCoGoi(out: String): Boolean = out.contains("Unknown package", ignoreCase = true)

    /**
     * Một dòng lý do: bỏ dòng "Exception occurred while executing…", các dòng "at …" của stack trace và tên lớp ngoại lệ
     * đứng đầu (VD "Unknown package: com.tcl.browser"). Không nhận ra dạng nào thì trả dòng đầu tiên có chữ.
     */
    fun gon(out: String): String {
        val dong = out.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val lyDo = dong.firstOrNull { !it.startsWith("Exception occurred while executing") && !it.startsWith("at ") } ?: dong.firstOrNull().orEmpty()
        return lyDo.replace(TEN_LOP, "").ifBlank { lyDo }.take(200)
    }

    /** "Error: " (pm Android 7) và tên lớp ngoại lệ, kể cả lồng nhau ("java.lang.RuntimeException: java.lang.…Exception: "). */
    private val TEN_LOP = Regex("""^(?:Error:\s*)?(?:[A-Za-z_][\w.]*(?:Exception|Error):\s*)+""")
}
