package vn.tivikit.dulieu

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf

/**
 * Tên app đọc từ chính tivi (27/10/2026, TrenTv.tenApp): tenDep dùng khi danh sách viết tay (TEN_APP) không có gói đó, thay
 * vì đuôi tên gói. Nhớ trên điện thoại để lần sau hiện ngay; là state của Compose nên màn đang mở tự đổi khi đọc xong.
 */
object TenTrenTv {
    /** Gói → tên tivi báo, kể cả tên vô ích (trống, trùng tên gói): để biết gói đó đã hỏi rồi. */
    private val ten = mutableStateMapOf<String, String>()
    @Volatile private var daNap = false

    /** Tên tivi báo cho [goi]; null nếu chưa biết hoặc tên chẳng hơn đuôi tên gói. */
    operator fun get(goi: String): String? = ten[goi]?.takeIf { it.isNotBlank() && it != goi }

    /** Có gói nào trong [goi] chưa hỏi tivi (app mới cài): có thì phải hỏi lại. */
    fun thieu(goi: Collection<String>): Boolean = goi.any { it !in ten }

    fun nap(ctx: Context) {
        if (daNap) return
        daNap = true
        kho(ctx).all.forEach { (g, t) -> if (t is String) ten[g] = t }
    }

    /** Thêm tên vừa đọc (gọi trên luồng giao diện). */
    fun them(ctx: Context, moi: Map<String, String>) {
        if (moi.isEmpty()) return
        ten.putAll(moi)
        kho(ctx).edit().apply { moi.forEach { (g, t) -> putString(g, t) } }.apply()
    }

    private fun kho(ctx: Context) = ctx.getSharedPreferences("ten-tren-tv", Context.MODE_PRIVATE)
}
