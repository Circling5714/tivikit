package vn.tivikit

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Số liệu ẩn danh (21/10/2026): mở app, cập nhật, dọn tivi, cài Home sạch, khôi phục, ghim nút máy chiếu.
 * Chỉ gửi một mã máy ngẫu nhiên tự sinh, số bản app và tên mẫu tivi — không email, không tên, không danh sách app.
 * Máy chủ: Cloudflare Worker cleantv-dem (tv-sach/dem). Gửi lỗi thì thôi, không ảnh hưởng gì.
 */
object Dem {
    private const val DIA_CHI = "https://cleantv-dem.vgszone.workers.dev/su-kien"

    private fun maMay(ctx: Context): String {
        val p = ctx.getSharedPreferences("dem", Context.MODE_PRIVATE)
        return p.getString("may", null) ?: UUID.randomUUID().toString().also { p.edit().putString("may", it).apply() }
    }

    fun gui(ctx: Context, loai: String, tivi: String = "", so: Int = 0, them: String = "") {
        if (!BuildConfig.GUI_SO_LIEU) return
        val app = ctx.applicationContext
        Thread {
            runCatching {
                val body = JSONObject().put("loai", loai).put("may", maMay(app)).put("ban", BuildConfig.VERSION_NAME)
                    .put("tivi", tivi).put("so", so).put("them", them).toString().toByteArray()
                val c = URL(DIA_CHI).openConnection() as HttpURLConnection
                c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 8000; c.readTimeout = 8000
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body) }
                c.responseCode; c.disconnect()
            }
        }.start()
    }

    /**
     * Danh sách gói người dùng TỰ BẤM gửi ở màn Xong (23/10/2026): model tivi, gói đang tắt, gói app giữ lại, đánh giá
     * và ghi chú — để làm mẫu cho tivi chưa có trong danh sách. Gọi trên luồng nền; trả true khi máy chủ nhận.
     */
    fun guiDanhSach(ctx: Context, du: JSONObject): Boolean = runCatching {
        if (!BuildConfig.GUI_SO_LIEU) return false
        val body = du.put("may", maMay(ctx.applicationContext)).put("ban", BuildConfig.VERSION_NAME).toString().toByteArray()
        val c = URL(DIA_CHI.replace("/su-kien", "/danh-sach")).openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 10000; c.readTimeout = 10000
        c.setRequestProperty("Content-Type", "application/json")
        c.outputStream.use { it.write(body) }
        (c.responseCode == 200).also { c.disconnect() }
    }.getOrDefault(false)

    /** Gọi lúc mở app: "mo" mỗi ngày một lần; "cap-nhat" khi số bản khác lần trước. */
    fun moApp(ctx: Context) {
        if (!BuildConfig.GUI_SO_LIEU) return
        val p = ctx.getSharedPreferences("dem", Context.MODE_PRIVATE)
        val banCu = p.getString("ban", null)
        if (banCu != null && banCu != BuildConfig.VERSION_NAME) gui(ctx, "cap-nhat", them = "$banCu→${BuildConfig.VERSION_NAME}")
        val homNay = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        if (p.getString("ngay_mo", "") != homNay) gui(ctx, "mo")
        p.edit().putString("ban", BuildConfig.VERSION_NAME).putString("ngay_mo", homNay).apply()
    }
}
