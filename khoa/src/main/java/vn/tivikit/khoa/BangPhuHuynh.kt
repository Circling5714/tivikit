package vn.tivikit.khoa

import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Calendar

/**
 * Bảng phụ huynh trên tivi (28/10/2026): lúc không cầm điện thoại, bố mẹ nhập mã ngay trên tivi rồi bấm remote để mở tivi
 * thêm, tạm tắt khoá tới sáng mai, khoá / mở một app, hẹn giờ khoá app. Lịch tuần và cài mã vẫn làm trong TiviKit (nhập
 * giờ, quét QR bằng remote thì khổ). Dịch vụ khoá hỏi mã trước rồi mới mở bảng; phím do dịch vụ chuyển vào ([phim]): lên /
 * xuống chọn dòng, OK làm, Back lùi một bước. Lớp trợ năng che kín như màn khoá; để yên 2 phút thì tự đóng.
 */
internal class BangPhuHuynh(private val dv: KhoaDichVu) {
    private class Muc(val tieuDe: String, val dong: List<Pair<String, () -> Unit>>)

    private val tang = ArrayList<Muc>()
    /** Dòng đang chọn ở từng mức trên [tang]: Back về mức trước thì đứng lại đúng dòng đã bấm. */
    private val chonTruoc = ArrayList<Int>()
    private var chon = 0
    private var lop: View? = null
    private lateinit var tvTieuDe: TextView
    private lateinit var tvPhu: TextView
    private lateinit var ds: LinearLayout
    private lateinit var cuon: ScrollView
    private val tuDong = Runnable { dongLai() }

    val dangMo: Boolean get() = lop != null

    private fun t(v: String, e: String) = dv.t(v, e)

    fun mo() {
        tang.clear(); chonTruoc.clear(); tang += menuChinh(); chon = 0
        if (lop == null) taoLop()
        ve()
        henDong()
    }

    /** Đóng bảng; [bao] = lời báo ở góc màn hình (việc vừa làm). Dịch vụ xét lại khoá ngay sau đó. */
    fun dongLai(bao: String? = null) {
        dv.tay.removeCallbacks(tuDong)
        lop?.let { v -> runCatching { dv.getSystemService(WindowManager::class.java).removeView(v) } }
        lop = null
        dv.sauBang(bao)
    }

    private fun henDong() { dv.tay.removeCallbacks(tuDong); dv.tay.postDelayed(tuDong, 2 * 60_000L) }

    fun phim(k: Int) {
        if (lop == null) return
        henDong()
        val m = tang.last()
        when (k) {
            KeyEvent.KEYCODE_DPAD_UP -> if (chon > 0) { chon--; ve() }
            KeyEvent.KEYCODE_DPAD_DOWN -> if (chon < m.dong.size - 1) { chon++; ve() }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> m.dong.getOrNull(chon)?.second?.invoke()
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DEL -> if (tang.size > 1) { tang.removeAt(tang.size - 1); chon = chonTruoc.removeAt(chonTruoc.size - 1); ve() } else dongLai()
        }
    }

    private fun vao(m: Muc) { chonTruoc += chon; tang += m; chon = 0; ve() }

    // ---------- Các mục ----------

    private fun gio(ms: Long) = Calendar.getInstance().apply { timeInMillis = ms }.let { Luat.chuGio(it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE)) }

    private fun moTivi(phut: Int): Pair<String, () -> Unit> = t("Mở tivi ${Luat.chuThoiGian(phut, true)}", "Unlock the TV for ${Luat.chuThoiGian(phut, false)}") to {
        val den = System.currentTimeMillis() + phut * 60_000L
        dv.kho.moTamDen = den
        dongLai(t("Tivi mở tới ${gio(den)}.", "The TV is unlocked until ${gio(den)}."))
    }

    private fun menuChinh(): Muc {
        val kho = dv.kho
        val bay = System.currentTimeMillis()
        val muc = mutableListOf(moTivi(30), moTivi(60), moTivi(120))
        muc += t("Tạm tắt khoá tới 6 giờ sáng mai", "Pause the lock until 6 am tomorrow") to {
            val den = KhoaApp.sangMai(System.currentTimeMillis())
            kho.moTamDen = den
            dongLai(t("Khoá tạm tắt tới 06:00 sáng mai.", "The lock is paused until 6:00 tomorrow."))
        }
        if (kho.moTamDen > bay) muc += t("Thôi mở tạm, khoá lại theo lịch", "End the unlock, back to the schedule") to {
            kho.moTamDen = 0
            dongLai(t("Đã thôi mở tạm: tivi khoá lại nếu đang trong giờ khoá.", "Unlock ended: the TV locks again if it's within a lock period."))
        }
        muc += t("Khoá một app…", "Lock an app…") to { vao(chonAppKhoa()) }
        if (dv.appDangKhoa().isNotEmpty()) muc += t("Mở app đang khoá…", "Open a locked app…") to { vao(chonAppMo()) }
        muc += t("Đóng", "Close") to { dongLai() }
        return Muc(t("Bảng phụ huynh", "Parent panel"), muc)
    }

    private fun chonAppKhoa(): Muc = Muc(t("Khoá app nào?", "Lock which app?"),
        dv.dsAppMo().map { (goi, ten) -> ten to { vao(chonLucKhoa(goi, ten)) } })

    private fun chonLucKhoa(goi: String, ten: String): Muc {
        fun hen(sau: Int): Pair<String, () -> Unit> =
            (if (sau == 0) t("Khoá ngay, tới hết hôm nay", "Lock now, until the end of today") else t("Khoá sau ${Luat.chuThoiGian(sau, true)}, tới hết hôm nay", "Lock in ${Luat.chuThoiGian(sau, false)}, until the end of today")) to {
                val tu = System.currentTimeMillis() + sau * 60_000L
                dv.kho.hen = dv.kho.hen + HenKhoa(setOf(goi), tu, KhoaApp.nuaDem(tu))
                dongLai(if (sau == 0) t("Đã khoá $ten tới hết hôm nay.", "$ten is locked until the end of today.") else t("$ten khoá lúc ${gio(tu)}, tới hết hôm nay.", "$ten locks at ${gio(tu)}, until the end of today."))
            }
        return Muc(ten, listOf(hen(0), hen(15), hen(30), hen(60), hen(120)))
    }

    private fun chonAppMo(): Muc = Muc(t("Mở app nào?", "Open which app?"), dv.appDangKhoa().map { goi ->
        val ten = dv.tenApp(goi)
        ten to { vao(chonLucMo(goi, ten)) }
    })

    private fun chonLucMo(goi: String, ten: String): Muc {
        fun mo(phut: Int): Pair<String, () -> Unit> = t("Mở $ten ${Luat.chuThoiGian(phut, true)}", "Open $ten for ${Luat.chuThoiGian(phut, false)}") to {
            dv.kho.moRiengApp(goi, System.currentTimeMillis() + phut * 60_000L)
            dongLai(t("$ten mở được ${Luat.chuThoiGian(phut, true)}.", "$ten is open for ${Luat.chuThoiGian(phut, false)}."))
        }
        val muc = mutableListOf(mo(30), mo(60))
        if (dv.kho.hen.any { goi in it.goi }) muc += t("Bỏ hẹn khoá $ten hôm nay", "Cancel today's lock timer for $ten") to {
            dv.kho.hen = KhoaApp.boHenApp(dv.kho.hen, goi)
            dongLai(t("Đã bỏ hẹn khoá $ten.", "$ten's lock timer is cancelled."))
        }
        return Muc(ten, muc)
    }

    // ---------- Vẽ ----------

    private fun taoLop() {
        val dp = dv::dp
        val cot = LinearLayout(dv).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        tvTieuDe = dv.chu("", 30f, dam = true).apply { setPadding(0, 0, 0, dp(4)) }; cot.addView(tvTieuDe)
        tvPhu = dv.chu("", 16f, Color.parseColor("#C9C3FF")).apply { setPadding(0, 0, 0, dp(14)) }
        cot.addView(tvPhu, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        ds = LinearLayout(dv).apply { orientation = LinearLayout.VERTICAL }
        cuon = ScrollView(dv).apply { isVerticalScrollBarEnabled = false; addView(ds) }
        cot.addView(cuon, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(360)))
        cot.addView(dv.chu(t("Lên / xuống để chọn · OK để làm · Back để quay lại", "Up / down to pick · OK to do it · Back to go back"), 13f, Color.parseColor("#8A84B8"))
            .apply { setPadding(0, dp(12), 0, 0) })
        val nen = FrameLayout(dv).apply {
            setBackgroundColor(Color.parseColor("#FF0A0718"))
            addView(cot, FrameLayout.LayoutParams(dp(640), FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.TRANSLUCENT)
        runCatching { dv.getSystemService(WindowManager::class.java).addView(nen, lp) }.onSuccess { lop = nen }
    }

    private fun ve() {
        if (lop == null) return
        val m = tang.last()
        tvTieuDe.text = m.tieuDe
        tvPhu.text = if (tang.size == 1) dv.tomTat() else t("Back để quay lại", "Back to go back")
        ds.removeAllViews()
        val dp = dv::dp
        m.dong.forEachIndexed { i, (chu, _) ->
            val v = dv.chu(chu, 20f, dam = i == chon).apply {
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                setPadding(dp(22), dp(10), dp(22), dp(10))
                background = GradientDrawable().apply {
                    cornerRadius = dp(14).toFloat()
                    setColor(if (i == chon) Color.parseColor("#8B5CF6") else Color.parseColor("#221B45"))
                }
            }
            ds.addView(v, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(3), 0, dp(3)) })
        }
        // Giữ dòng đang chọn trong tầm nhìn khi danh sách dài (danh sách app).
        ds.post { ds.getChildAt(chon)?.let { cuon.smoothScrollTo(0, (it.top - dp(120)).coerceAtLeast(0)) } }
    }
}
