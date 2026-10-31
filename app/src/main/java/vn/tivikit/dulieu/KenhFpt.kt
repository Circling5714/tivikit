package vn.tivikit.dulieu

import android.content.Context
import org.json.JSONObject

/**
 * Kênh của FPT Play (31/10/2026). Mã kênh là đoạn cuối link kênh trên fptplay.vn (fptplay.vn/xem-truyen-hinh/vtv1-hd),
 * cũng là deep link FPT Play bản tivi (net.fptplay.ottbox) mở thẳng màn xem kênh: ô Kênh nhanh FPT Play (app phụ, module
 * kenh/) và câu mở kênh của Map Voice dùng link này. Danh sách đóng sẵn trong assets/kenh-fpt.json (93 kênh, lấy từ danh
 * sách kênh công khai của fptplay.vn ngày 31/10/2026): mã, tên, nhóm, số kênh của FPT Play, ảnh kênh, có cần gói không. Xem
 * kênh vẫn cần đăng nhập FPT Play trên tivi (chưa đăng nhập thì FPT Play hỏi); kênh cần gói (VTV2–VTV10, HTV7, HTV9, kênh quốc
 * tế…, nhãn premium của FPT Play) thì tài khoản chưa mua gói bị hỏi mua gói (thấy trên Skyworth: VTV3 HD hỏi, VTV1 HD phát).
 */
object KenhFpt {
    const val GOI = "net.fptplay.ottbox"
    const val ASSET = "kenh-fpt.json"

    data class Kenh(val ma: String, val ten: String, val nhom: String, val so: String, val anh: String, val canGoi: Boolean = false) {
        /** Tên trên ô: bỏ chữ "HD" cuối cho gọn ("VTV1 HD" → "VTV1"), tối đa 24 ký tự. */
        val tenO: String get() = tenNgan(ten)
    }

    /** Kênh hay xem, chọn nhanh ở màn Kênh nhanh (theo thứ tự này). */
    val HAY_XEM = listOf(
        "vtv1-hd", "vtv2-hd", "vtv3-hd", "vtv4-hd", "vtv5-hd", "vtv6-hd", "vtv7-hd", "vtv8-hd", "vtv9-hd", "vtv10",
        "htv7-hd", "htv9-hd", "hanoitv-1", "antv", "quoc-phong-vn",
    )

    private val MA = Regex("[A-Za-z0-9_-]{1,60}")

    fun tenNgan(ten: String): String = ten.trim().replace(Regex("\\s+HD$", RegexOption.IGNORE_CASE), "").take(24).trim()

    fun link(ma: String): String = "https://fptplay.vn/xem-truyen-hinh/$ma"

    /** Đọc danh sách kênh dạng assets/kenh-fpt.json; mục hỏng (mã lạ, thiếu tên) thì bỏ, mã trùng lấy mục đầu. */
    fun tach(json: String): List<Kenh> {
        val a = runCatching { JSONObject(json).optJSONArray("kenh") }.getOrNull() ?: return emptyList()
        val da = mutableSetOf<String>()
        return (0 until a.length()).mapNotNull { a.optJSONObject(it) }.mapNotNull { o ->
            val ma = o.optString("ma").trim()
            val ten = o.optString("ten").trim()
            if (!MA.matches(ma) || ten.isEmpty() || !da.add(ma)) null
            else Kenh(ma, ten, o.optString("nhom").trim(), o.optString("so").trim(),
                o.optString("anh").trim().takeIf { it.startsWith("https://") }.orEmpty(), o.optBoolean("canGoi"))
        }
    }

    @Volatile private var nho: List<Kenh>? = null

    /** Danh sách đóng sẵn (đọc một lần rồi nhớ). */
    fun ds(ctx: Context): List<Kenh> = nho ?: runCatching {
        tach(vn.tivikit.CapNhat.moAsset(ctx, ASSET).bufferedReader().use { it.readText() })
    }.getOrDefault(emptyList()).also { if (it.isNotEmpty()) nho = it }

    /** Kênh hay xem (HAY_XEM) có trong [ds]. */
    fun hayXem(ds: List<Kenh>): List<Kenh> = HAY_XEM.mapNotNull { m -> ds.firstOrNull { it.ma == m } }
}
