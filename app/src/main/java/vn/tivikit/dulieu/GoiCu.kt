package vn.tivikit.dulieu

/**
 * Tên app phụ trong các bản thử trước khi TiviKit đổi tên gói sang vn.tivikit (25/10/2026). Không bản phát hành nào có
 * chúng (1.1.0 đã là vn.tivikit), nên từ 31/10/2026 TiviKit thôi tự chuyển dữ liệu từ chúng sang gói mới; chỉ còn dọn
 * sót: Khôi phục và kịch bản khôi phục gỡ các gói này, cài / tắt app phụ bỏ tên dịch vụ trợ năng cũ.
 */
object GoiCu {
    /** Tên gói bản cũ của app chính; app phụ là <APP>.kenh…, .hengio, .khoa. */
    const val APP = "com.icm.cleantv"
    const val KHOA = "$APP.khoa"
    const val HEN_GIO = "$APP.hengio"

    /**
     * Tên lớp dịch vụ trợ năng trong app phụ dựng trước khi mã nguồn chuyển từ zone.vgs.* sang vn.tivikit.* (25/10/2026):
     * mọi bản com.icm.cleantv.* và bản vn.tivikit.* đầu tiên. Bật / tắt dịch vụ trên tivi phải gọi đúng tên lớp đang cài.
     */
    const val LOP_KHOA_CU = "zone.vgs.khoa.KhoaDichVu"
    const val LOP_HEN_GIO_CU = "zone.vgs.hengio.HenGioDichVu"
    const val DICH_VU_KHOA = "$KHOA/$LOP_KHOA_CU"
    const val DICH_VU_HEN_GIO = "$HEN_GIO/$LOP_HEN_GIO_CU"

    /** Gói cũ của ô Kênh nhanh [o]. */
    fun kenh(o: Int): String = if (o == 1) "$APP.kenh" else "$APP.kenh$o"

    /** Mọi app phụ bản cũ. */
    val APP_PHU: List<String> = (1..KenhNhanh.SO_O).map { kenh(it) } + HEN_GIO + KHOA
}
