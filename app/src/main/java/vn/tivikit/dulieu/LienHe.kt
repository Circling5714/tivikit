package vn.tivikit.dulieu

/**
 * Liên hệ và ủng hộ tác giả TiviKit (26/10/2026, công khai cả trong app lẫn trên GitHub): nhóm Telegram, báo lỗi bằng
 * trang tạo issue trên GitHub (điền sẵn thông tin máy; người dùng xem lại rồi tự bấm gửi, app không gửi gì), tài khoản
 * MB Bank có nút chép.
 */
object LienHe {
    const val TELEGRAM = "https://t.me/tivikit"
    const val NGAN_HANG = "MB Bank"
    const val SO_TAI_KHOAN = "070991988888"
    const val CHU_TAI_KHOAN = "CAO MINH THI"
    /** Nội dung chuyển khoản gợi ý: không dấu, ngân hàng nào cũng nhận. */
    const val NOI_DUNG_CK = "Ung ho TiviKit"

    /**
     * Nội dung báo lỗi điền sẵn: chỗ để tả lỗi và các bước, rồi bản TiviKit, điện thoại, tivi đang nối (nếu có), lỗi app
     * vừa báo (nếu có, tối đa 500 ký tự). Không có gì riêng tư: không IP, không danh sách app.
     */
    fun noiDungBaoLoi(tiengViet: Boolean, banApp: String, dienThoai: String, tivi: String?, loi: String?): String = buildString {
        fun t(vi: String, en: String) = if (tiengViet) vi else en
        appendLine(t("**Lỗi / góp ý:**", "**What happened / suggestion:**")).appendLine().appendLine()
        appendLine(t("**Các bước làm:**", "**Steps:**")).appendLine("1. ").appendLine()
        appendLine("---")
        appendLine("- TiviKit: $banApp")
        appendLine(t("- Điện thoại: ", "- Phone: ") + dienThoai)
        if (!tivi.isNullOrBlank()) appendLine(t("- Tivi: ", "- TV: ") + tivi)
        if (!loi.isNullOrBlank()) appendLine(t("- App báo lỗi: ", "- App error: ") + loi.trim().replace('\n', ' ').take(500))
    }

    /** Trang tạo issue trên kho TiviKit với tiêu đề và nội dung điền sẵn. */
    fun linkBaoLoi(tieuDe: String, noiDung: String): String =
        "https://github.com/${vn.tivikit.CapNhat.KHO}/issues/new?title=" + ma(tieuDe) + "&body=" + ma(noiDung)

    /** Mã hoá cho URL; dấu cách viết %20 thay cho "+" để trang nào đọc cũng ra dấu cách. */
    private fun ma(s: String) = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
