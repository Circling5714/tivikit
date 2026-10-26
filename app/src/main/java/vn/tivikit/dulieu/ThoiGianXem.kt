package vn.tivikit.dulieu

import org.json.JSONObject
import vn.tivikit.t

/**
 * Báo cáo thời gian xem của Khoá trẻ em (25/10/2026), đọc từ app khoá trên tivi (content call … --method thoi-gian):
 * mỗi ngày tivi được xem bao lâu (app khoá đếm; giới hạn mỗi ngày tính theo số này) và app nào mở lâu (thống kê của
 * tivi, không tính màn hình chính). [ngay] xếp cũ trước, ngày cuối là hôm nay.
 */
data class ThoiGianXem(val homNay: String, val coQuyen: Boolean, val ngay: List<Ngay>) {
    data class App(val goi: String, val ten: String, val giay: Long)

    /** [thu] 1 = Thứ Hai … 7 = Chủ Nhật; [daDem] = app khoá đã đếm ngày này (bản có giới hạn giờ xem đã cài từ trước). */
    data class Ngay(val ngay: String, val thu: Int, val giay: Long, val gioiHanPhut: Int, val apps: List<App>, val daDem: Boolean)

    val homNayXem: Ngay? get() = ngay.lastOrNull()?.takeIf { it.ngay == homNay }

    companion object {
        fun tuJson(o: JSONObject): ThoiGianXem {
            val demTu = o.optString("demTu")
            val ds = o.optJSONArray("ngay")
            val ngay = (0 until (ds?.length() ?: 0)).mapNotNull { i ->
                val n = ds!!.optJSONObject(i) ?: return@mapNotNull null
                val ap = n.optJSONArray("apps")
                val ten = n.optString("ngay")
                Ngay(ten, n.optInt("thu"), n.optLong("giay"), n.optInt("gioiHan"),
                    (0 until (ap?.length() ?: 0)).mapNotNull { j -> ap!!.optJSONObject(j)?.let { App(it.optString("goi"), it.optString("ten").ifBlank { it.optString("goi") }, it.optLong("giay")) } },
                    // Ngày dạng yyyy-MM-dd: so chuỗi là so ngày.
                    daDem = demTu.isNotBlank() && ten >= demTu)
            }
            return ThoiGianXem(o.optString("homNay"), o.optBoolean("coQuyen"), ngay)
        }

        /** "1 giờ 20 phút", "45 phút", "dưới 1 phút". */
        fun chu(giay: Long): String {
            val p = giay / 60
            return when {
                p < 1 -> t("dưới 1 phút", "under 1 min")
                p < 60 -> t("$p phút", "$p min")
                p % 60 == 0L -> t("${p / 60} giờ", "${p / 60} h")
                else -> t("${p / 60} giờ ${p % 60} phút", "${p / 60} h ${p % 60} min")
            }
        }

        /** Gọn cho nhãn trục: "30p", "2h", "1h30" / "30m", "2h", "1h30". */
        fun ngan(giay: Long): String {
            val p = giay / 60
            return if (p < 60) t("${p}p", "${p}m") else "${p / 60}h" + (if (p % 60 > 0) "%02d".format(p % 60) else "")
        }

        /** Nhãn mức giới hạn [phut] mỗi ngày: "Không giới hạn", "30 phút", "1 giờ", "1,5 giờ". */
        fun chuGioiHan(phut: Int): String = when {
            phut <= 0 -> t("Không giới hạn", "No limit")
            phut < 60 -> t("$phut phút", "$phut min")
            phut % 60 == 0 -> t("${phut / 60} giờ", "${phut / 60} h")
            phut % 60 == 30 -> t("${phut / 60},5 giờ", "${phut / 60}.5 h")
            else -> chu(phut * 60L)
        }
    }
}
