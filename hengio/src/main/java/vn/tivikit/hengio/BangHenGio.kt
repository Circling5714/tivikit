package vn.tivikit.hengio

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import vn.tivikit.hengio.Chu.t
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * Màn Hẹn giờ trên tivi (31/10/2026), dùng remote của tivi: việc kế tiếp, hẹn tắt tivi sau X phút, việc một lần đang chờ,
 * lịch hằng tuần (thêm / sửa / xoá / bật tắt), việc một lần lúc HH:MM, lần chạy gần đây. Khoá trẻ em đang bật thì việc
 * đổi lịch cần mã bố mẹ (màn XacNhan của Khoá trẻ em, như app Cast), trừ hẹn tắt tivi. Dựng bằng View thường (app nhỏ, mở
 * nhanh trên tivi yếu); mỗi màn con (sửa việc, chọn giờ, ngày, app…) vẽ lại cả màn, Back về màn trước.
 */
class BangHenGio : Activity() {
    /** Bản nháp việc đang thêm / sửa: [cu] = việc gốc khi sửa (null = việc mới); [the] = ô chọn sẵn khi vẽ lại màn sửa. */
    private class Nhap(var loai: String, var gio: Int, var phut: Int, var ngay: Set<Int>, var motLan: Boolean, var goi: String?, var bat: Boolean, val cu: Viec?) {
        var the = "viec"
    }

    private val kho by lazy { Kho(this) }
    private val tay = Handler(Looper.getMainLooper())
    private val zone: ZoneId get() = ZoneId.systemDefault()
    /** Các màn đang mở, màn chính ở đáy: Back vẽ lại màn trước. */
    private val ngan = ArrayList<() -> Unit>()
    /** Thẻ ô chọn sẵn khi vẽ màn chính. */
    private var theChinh: String? = null
    /** Dữ liệu và phút của màn chính đang hiện: đổi (việc một lần vừa chạy, nhật ký mới, sang phút mới…) thì vẽ lại. */
    private var dauChinh = ""
    private var phutChinh = 0L
    private val nhip = object : Runnable {
        override fun run() {
            if (ngan.size == 1 && (System.currentTimeMillis() / 60_000 != phutChinh || dau() != dauChinh)) veLaiChinh()
            tay.postDelayed(this, NHIP_MS)
        }
    }
    /** Tên app TiviKit gửi (ô Kênh nhanh mang tên kênh), đọc lại mỗi lần về màn chính. */
    private var tenKho: Map<String, String> = emptyMap()
    private val tenTam = HashMap<String, String>()
    // Mã bố mẹ (Khoá trẻ em): nhập đúng thì 5 phút không hỏi lại lúc màn còn mở.
    private var moMaDen = 0L
    private var choMa: (() -> Unit)? = null

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.setBackgroundDrawable(GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(NEN_TREN, NEN_DUOI)))
        veChinh()
    }

    override fun onNewIntent(i: Intent?) {
        super.onNewIntent(i)
        veChinh()
    }

    override fun onResume() {
        super.onResume()
        tay.removeCallbacks(nhip)
        tay.postDelayed(nhip, NHIP_MS)
    }

    override fun onPause() {
        tay.removeCallbacks(nhip)
        super.onPause()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) { veTruoc(); return true }
        return super.onKeyDown(keyCode, event)
    }

    // ---------- Đi giữa các màn ----------

    /** Mở màn con [ve] (vẽ ngay); Back vẽ lại màn trước. */
    private fun mo(ve: () -> Unit) { ngan += ve; ve() }

    private fun veTruoc() {
        if (ngan.size <= 1) { finish(); return }
        ngan.removeAt(ngan.lastIndex)
        // Không dùng ngan.last(): compileSdk 35 có List.getLast() (Java 21), Kotlin gọi nó, Android cũ không có.
        ngan[ngan.lastIndex]()
    }

    /** Về màn chính (bỏ các màn con), chọn sẵn ô [the]. */
    private fun veChinh(the: String? = null) {
        theChinh = the
        tenKho = kho.tenApp
        ngan.clear()
        mo { manChinh() }
    }

    private fun veLaiChinh() {
        theChinh = currentFocus?.tag as? String
        manChinh()
    }

    /** Dữ liệu màn chính: việc một lần, lịch, việc kế tiếp, dòng nhật ký mới nhất, dịch vụ đang chạy. */
    private fun dau(): String {
        val nk = kho.nhatKy
        return listOf(kho.motLanDs.joinToString { it.id }, kho.lich.hashCode(), kho.keTiep, HenGioDichVu.dangChay != null,
            if (nk.length() == 0) 0 else nk.optJSONObject(nk.length() - 1)?.optLong("luc")).joinToString("|")
    }

    // ---------- Màn chính ----------

    private fun manChinh() {
        val bay = System.currentTimeMillis()
        val cot = cot()
        cot.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM
            addView(chu(t("Hẹn giờ", "Timer"), 28f, dam = true), LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(chu(Chu.gio(bay, zone) + " · " + Chu.thuDai(Instant.ofEpochMilli(bay).atZone(zone).dayOfWeek.value), 17f, XAM))
        })
        cot.addView(chu(chuKeTiep(bay), 16f, TIM_NHAT), lp(4))
        if (HenGioDichVu.dangChay == null) {
            cot.addView(chu(t("Dịch vụ Hẹn giờ đang tắt nên tắt tivi và mở app đúng giờ có thể không chạy. Bật lại trong Cài đặt → Hỗ trợ tiếp cận → Hẹn giờ (TiviKit), hay bấm Lưu lên tivi ở màn Hẹn giờ của TiviKit.",
                "The Timer service is off, so timed turn-off and app opening may not run. Turn on Timer (TiviKit) in Settings → Accessibility, or tap Save to the TV on TiviKit's Timer screen."), 15f, VANG), lp(12))
            cot.addView(nut(t("Mở cài đặt Hỗ trợ tiếp cận", "Open Accessibility settings"), "tro-nang") {
                theChinh = "tro-nang"
                runCatching { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            }, lpBoc(8))
        }
        // Hẹn tắt tivi sau X phút: không cần mã bố mẹ.
        if (Build.VERSION.SDK_INT >= 28) {
            cot.addView(muc(t("Hẹn tắt tivi sau", "Turn the TV off in")), lp(22))
            val nuts = PHUT_NHANH.map { p -> nut(Chu.phut(p), "sau-$p") { theChinh = "sau-$p"; tatSau(p) } } +
                nut(t("Khác…", "Other…"), "sau-khac") { theChinh = "sau-khac"; mo { chonPhut(45) } }
            cot.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(hangNut(nuts)) }, lp(8))
        }
        val ml = kho.motLanDs
        if (ml.isNotEmpty()) {
            cot.addView(muc(t("Việc một lần đang chờ (OK để huỷ)", "Pending one-offs (OK to cancel)")), lp(20))
            ml.forEach { m ->
                val the = "ml-${m.id}"
                cot.addView(dong(moTa(m, bay), null, Chu.con(m.luc - bay), TIM_NHAT, the) { theChinh = the; hoiHuy(m) }, lp(6))
            }
        }
        cot.addView(muc(t("Lịch hằng tuần (OK để sửa)", "Weekly schedule (OK to edit)")), lp(22))
        val lich = kho.lich
        if (lich.isEmpty()) cot.addView(chu(t("Chưa có việc nào.", "No entries yet."), 15f, XAM), lp(6))
        lich.forEachIndexed { i, v ->
            val the = "lich-$i"
            cot.addView(dong(gioChu(v.gio, v.phut) + "   " + Chu.viec(v.loai, v.goi, ::ten), Chu.ngay(v.ngay),
                if (v.bat) t("Đang bật", "On") else t("Đang tắt", "Off"), if (v.bat) XANH else XAM, the) {
                theChinh = the
                // Tạo bản nháp một lần: Back từ màn chọn giờ / ngày / app vẽ lại màn sửa với đúng bản nháp này.
                val n = Nhap(v.loai, v.gio, v.phut, v.ngay, false, v.goi, v.bat, v)
                mo { manSua(n) }
            }, lp(6))
        }
        val nutThem = nut(t("+ Thêm việc", "+ Add an entry"), "them") { theChinh = "them"; val n = nhapMoi(false); mo { manSua(n) } }
        xuongToi(cot, nutThem)
        cot.addView(hangNut(listOf(
            nutThem,
            nut(t("+ Việc một lần", "+ One-off"), "them-1") { theChinh = "them-1"; val n = nhapMoi(true); mo { manSua(n) } },
        )), lpBoc(10))
        val nk = kho.nhatKy
        if (nk.length() > 0) {
            cot.addView(muc(t("Lần chạy gần đây", "Recent runs")), lp(22))
            for (i in nk.length() - 1 downTo maxOf(0, nk.length() - 6)) {
                val o = nk.optJSONObject(i) ?: continue
                val luc = o.optLong("luc")
                cot.addView(chu(Chu.gio(luc, zone) + " " + Chu.thu(Instant.ofEpochMilli(luc).atZone(zone).dayOfWeek.value) + " · " +
                    Chu.nhatKy(o.optString("viec"), o.optString("kq"), ::ten), 14f, XAM), lp(4))
            }
        }
        cot.addView(chu(t("Lịch này cũng sửa được trong TiviKit trên điện thoại. Khoá trẻ em đang bật thì thêm, sửa, xoá hay huỷ việc cần mã bố mẹ; hẹn tắt tivi thì không cần.",
            "This schedule can also be changed in TiviKit on the phone. While the Kids lock is on, adding, editing, deleting or cancelling needs the parent code; setting a turn-off doesn't."), 13f, XAM), lp(22))
        hien(cot, theChinh ?: if (Build.VERSION.SDK_INT >= 28) "sau-30" else "them")
        dauChinh = dau()
        phutChinh = bay / 60_000
    }

    /** "Việc kế tiếp: Tắt tivi lúc 22:30 hôm nay" (tắt tivi: giờ tắt, không phải giờ báo trước). */
    private fun chuKeTiep(bay: Long): String {
        val k = Lich.keTiep(kho.lich, kho.motLan(), bay, zone) ?: return t("Chưa có việc nào sắp tới.", "Nothing coming up.")
        val luc = k.luc + if (k.viec.all { it.loai == Viec.TAT }) Lich.BAO_TRUOC_MS else 0
        return t("Việc kế tiếp: ", "Next: ") + k.viec.joinToString(", ") { Chu.viec(it.loai, it.goi, ::ten) } +
            t(" lúc ", " at ") + Chu.gio(luc, zone) + " " + Chu.ngayCua(luc, bay, zone)
    }

    private fun moTa(m: MotLan, bay: Long) = Chu.viec(m.loai, m.goi, ::ten) + t(" lúc ", " at ") + Chu.gio(m.luc, zone) + " " + Chu.ngayCua(m.luc, bay, zone)

    // ---------- Hẹn tắt, việc một lần ----------

    private fun tatSau(phut: Int) {
        val m = MotLan(System.currentTimeMillis() + phut * 60_000L, Viec.TAT)
        HenGio.themMotLan(this, m)?.let { bao(loiMotLan(it)); return }
        bao(t("Tivi sẽ tắt lúc ", "The TV turns off at ") + Chu.gio(m.luc, zone) + t(" (sau ", " (in ") + Chu.phut(phut) + ").")
        veChinh("ml-${m.id}")
    }

    private fun loiMotLan(ma: String) = if (ma == "trung") t("Việc này đã hẹn rồi.", "That's already set.")
        else t("Đã có ${Lich.MOT_LAN_TOI_DA} việc một lần đang chờ: huỷ bớt rồi thử lại.", "There are already ${Lich.MOT_LAN_TOI_DA} pending one-offs: cancel one first.")

    private fun hoiHuy(m: MotLan) {
        val mt = moTa(m, System.currentTimeMillis())
        hoi(t("Huỷ việc một lần này?", "Cancel this one-off?"), mt, t("Huỷ việc", "Cancel it")) {
            quaMa(t("Huỷ việc hẹn giờ: ", "Cancel a timer entry: ") + mt) {
                HenGio.huyMotLan(this, m.id)
                bao(t("Đã huỷ.", "Cancelled."))
                veChinh()
            }
        }
    }

    /** Màn chọn số phút cho "Khác…": ▲ ▼ thêm / bớt 5 phút, phím số gõ thẳng, OK để hẹn. */
    private fun chonPhut(dau: Int) {
        var phut = dau
        var go = ""
        val cot = cot()
        cot.addView(chu(t("Tắt tivi sau", "Turn the TV off in"), 26f, dam = true))
        val o = soLon("")
        val sau = chu("", 16f, XAM)
        fun ve() {
            o.text = Chu.phut(phut)
            sau.text = t("Tivi tắt lúc ", "The TV turns off at ") + Chu.gio(System.currentTimeMillis() + phut * 60_000L, zone)
        }
        o.setOnKeyListener { _, k, e ->
            if (e.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            when (k) {
                KeyEvent.KEYCODE_DPAD_UP -> { phut = minOf(Lich.SAU_PHUT.last, if (phut < 5) 5 else phut / 5 * 5 + 5); go = ""; ve(); true }
                KeyEvent.KEYCODE_DPAD_DOWN -> { phut = if (phut <= 5) 1 else (phut - 1) / 5 * 5; go = ""; ve(); true }
                in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                    go = (go + (k - KeyEvent.KEYCODE_0)).takeLast(3)
                    phut = go.toInt().coerceIn(Lich.SAU_PHUT.first, Lich.SAU_PHUT.last); ve(); true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> { tatSau(phut); true }
                else -> false
            }
        }
        cot.addView(o, lpBoc(24))
        cot.addView(sau, lp(10))
        cot.addView(chu(t("▲ ▼ thêm / bớt 5 phút · phím số gõ thẳng · OK để hẹn · Back để thôi", "▲ ▼ add / take 5 min · number keys type it · OK to set · Back to cancel"), 14f, XAM), lp(20))
        ve()
        hien(cot, null)
        o.requestFocus()
    }

    // ---------- Thêm / sửa việc ----------

    /** Việc mới: tắt tivi 22:00 mọi ngày; việc một lần thì chừng 30 phút nữa, tròn 5 phút. */
    private fun nhapMoi(motLan: Boolean): Nhap {
        val loai = if (Build.VERSION.SDK_INT >= 28) Viec.TAT else Viec.BAT
        if (!motLan) return Nhap(loai, 22, 0, (1..7).toSet(), false, null, true, null)
        val z = Instant.ofEpochMilli(System.currentTimeMillis() + 30 * 60_000L).atZone(zone)
        val p = (z.minute + 4) / 5 * 5
        return Nhap(loai, (z.hour + p / 60) % 24, p % 60, (1..7).toSet(), true, null, true, null)
    }

    private fun manSua(n: Nhap) {
        val cot = cot()
        val cu = n.cu
        cot.addView(chu(when {
            cu != null -> t("Sửa việc ", "Edit the entry at ") + gioChu(cu.gio, cu.phut)
            n.motLan -> t("Việc một lần", "One-off")
            else -> t("Thêm việc", "Add an entry")
        }, 26f, dam = true))
        val loais = listOfNotNull(Viec.BAT, Viec.TAT.takeIf { Build.VERSION.SDK_INT >= 28 }, Viec.MO)
        cot.addView(dong(t("Việc", "Action"), null, "◀  ${Chu.loai(n.loai)}  ▶", TRANG, "viec") { doiLoai(n, loais, 1) }.apply {
            setOnKeyListener { _, k, e ->
                if (e.action == KeyEvent.ACTION_DOWN && (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT)) {
                    doiLoai(n, loais, if (k == KeyEvent.KEYCODE_DPAD_RIGHT) 1 else -1); true
                } else false
            }
        }, lp(18))
        cot.addView(dong(t("Giờ", "Time"), null, gioChu(n.gio, n.phut), TRANG, "gio") { n.the = "gio"; mo { chonGio(n) } }, lp(8))
        val lap = when {
            n.motLan -> t("Một lần, ", "Once, ") + Chu.ngayCua(Lich.lanToi(n.gio, n.phut, System.currentTimeMillis(), zone), System.currentTimeMillis(), zone)
            n.ngay.isEmpty() -> t("Chưa chọn ngày", "No days chosen")
            else -> Chu.ngay(n.ngay)
        }
        cot.addView(dong(t("Lặp lại", "Repeat"), null, lap, if (!n.motLan && n.ngay.isEmpty()) VANG else TRANG, "ngay") { n.the = "ngay"; mo { chonNgay(n, "n-1") } }, lp(8))
        if (n.loai != Viec.TAT) {
            val app = n.goi?.let(::ten) ?: if (n.loai == Viec.BAT) t("Không mở app nào", "Nothing") else t("Chưa chọn", "Not chosen")
            cot.addView(dong(if (n.loai == Viec.BAT) t("Bật xong thì mở", "Then open") else t("App cần mở", "App to open"), null, app,
                if (n.loai == Viec.MO && n.goi == null) VANG else TRANG, "app") { n.the = "app"; mo { chonApp(n) } }, lp(8))
        } else cot.addView(chu(t("Trước 1 phút tivi báo, có nút Hoãn 30 phút; không ai bấm thì tivi tắt đúng giờ.",
            "A minute before, the TV shows a notice with Snooze 30 min; if nobody presses anything it turns off on time."), 14f, XAM), lp(8))
        if (!n.motLan) cot.addView(dong(t("Trạng thái", "Status"), null, if (n.bat) t("Đang bật", "On") else t("Đang tắt", "Off"),
            if (n.bat) XANH else XAM, "bat") { n.bat = !n.bat; n.the = "bat"; manSua(n) }, lp(8))
        val nutLuu = nut(if (n.motLan) t("Hẹn", "Set") else t("Lưu", "Save"), "luu") { luu(n) }
        xuongToi(cot, nutLuu)
        cot.addView(hangNut(listOfNotNull(
            nutLuu,
            if (cu != null) nut(t("Xoá việc", "Delete"), "xoa") { hoiXoa(cu) } else null,
            nut(t("Thôi", "Cancel"), "thoi") { veTruoc() },
        )), lpBoc(20))
        cot.addView(chu(t("◀ ▶ đổi việc · OK để chọn · Back để thôi", "◀ ▶ change the action · OK to choose · Back to cancel"), 13f, XAM), lp(14))
        hien(cot, n.the)
    }

    private fun doiLoai(n: Nhap, loais: List<String>, d: Int) {
        n.loai = loais[(loais.indexOf(n.loai) + d + loais.size) % loais.size]
        if (n.loai == Viec.TAT) n.goi = null
        n.the = "viec"
        manSua(n)
    }

    private fun luu(n: Nhap) {
        if (n.loai == Viec.MO && n.goi == null) { bao(t("Chọn app cần mở.", "Pick the app to open.")); n.the = "app"; manSua(n); return }
        if (!n.motLan && n.ngay.isEmpty()) { bao(t("Chọn ít nhất một ngày.", "Pick at least one day.")); n.the = "ngay"; manSua(n); return }
        val goi = if (n.loai == Viec.TAT) null else n.goi
        if (n.motLan) {
            val m = MotLan(Lich.lanToi(n.gio, n.phut, System.currentTimeMillis(), zone), n.loai, goi)
            val hen = {
                val loi = HenGio.themMotLan(this, m)
                if (loi != null) bao(loiMotLan(loi))
                else { bao(t("Đã hẹn: ", "Set: ") + moTa(m, System.currentTimeMillis()) + "."); veChinh("ml-${m.id}") }
            }
            // Hẹn tắt tivi không cần mã bố mẹ.
            if (m.loai == Viec.TAT) hen() else quaMa(t("Hẹn việc một lần: ", "Set a one-off: ") + moTa(m, System.currentTimeMillis()), hen)
            return
        }
        val v = Viec(n.gio, n.phut, n.ngay, n.loai, goi, n.bat)
        val cu = n.cu
        val lich = kho.lich
        if (v != cu && v in lich) { bao(t("Việc này đã có trong lịch.", "This entry is already in the schedule.")); return }
        if (cu == null && lich.size >= Lich.TOI_DA) { bao(t("Tối đa ${Lich.TOI_DA} việc.", "At most ${Lich.TOI_DA} entries.")); return }
        quaMa((if (cu == null) t("Thêm việc hẹn giờ: ", "Add a timer entry: ") else t("Sửa việc hẹn giờ: ", "Edit a timer entry: ")) +
            gioChu(v.gio, v.phut) + " " + Chu.viec(v.loai, v.goi, ::ten)) {
            // Đọc lại lịch (TiviKit có thể vừa đổi); việc gốc không còn thì thêm như việc mới.
            val moi = kho.lich.toMutableList()
            val i = if (cu == null) -1 else moi.indexOf(cu)
            if (i >= 0) moi[i] = v else moi += v
            HenGio.luuLich(this, moi)
            bao(t("Đã lưu.", "Saved."))
            veChinh("lich-${Lich.sapXep(moi).indexOf(v)}")
        }
    }

    private fun hoiXoa(v: Viec) {
        val mt = gioChu(v.gio, v.phut) + " " + Chu.viec(v.loai, v.goi, ::ten) + " · " + Chu.ngay(v.ngay)
        hoi(t("Xoá việc này?", "Delete this entry?"), mt, t("Xoá", "Delete")) {
            quaMa(t("Xoá việc hẹn giờ: ", "Delete a timer entry: ") + mt) {
                HenGio.luuLich(this, kho.lich - v)
                bao(t("Đã xoá.", "Deleted."))
                veChinh("them")
            }
        }
    }

    /** Chọn giờ: ▲ ▼ đổi số, ◀ ▶ giờ / phút, phím số gõ thẳng (đủ giờ thì sang phút), OK xong. */
    private fun chonGio(n: Nhap) {
        var gio = n.gio
        var phut = n.phut
        var go = ""
        val cot = cot()
        cot.addView(chu(t("Giờ", "Time"), 26f, dam = true))
        val oGio = soLon("")
        val oPhut = soLon("")
        fun ve() { oGio.text = "%02d".format(Locale.ROOT, gio); oPhut.text = "%02d".format(Locale.ROOT, phut) }
        val nghe = View.OnKeyListener { v, k, e ->
            if (e.action != KeyEvent.ACTION_DOWN) return@OnKeyListener false
            val laGio = v === oGio
            when (k) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                    val d = if (k == KeyEvent.KEYCODE_DPAD_UP) 1 else -1
                    if (laGio) gio = (gio + d + 24) % 24 else phut = (phut + d + 60) % 60
                    go = ""; ve(); true
                }
                in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                    go += (k - KeyEvent.KEYCODE_0).toString()
                    val so = go.toInt()
                    // Chữ số đầu của giờ từ 3, của phút từ 6 thì đã đủ một số.
                    val du = go.length == 2 || so > if (laGio) 2 else 5
                    if (laGio) gio = minOf(so, 23) else phut = minOf(so, 59)
                    ve()
                    if (du) { go = ""; if (laGio) oPhut.requestFocus() }
                    true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    n.gio = gio; n.phut = phut; n.the = "gio"; veTruoc(); true
                }
                else -> false
            }
        }
        listOf(oGio, oPhut).forEach { o -> o.setOnKeyListener(nghe); o.setOnFocusChangeListener { _, _ -> go = "" } }
        cot.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(oGio)
            addView(chu(":", 44f, dam = true).apply { setPadding(px(14), 0, px(14), 0) })
            addView(oPhut)
        }, lpBoc(24))
        cot.addView(chu(t("▲ ▼ đổi số · ◀ ▶ giờ / phút · phím số gõ thẳng · OK xong · Back để thôi",
            "▲ ▼ change · ◀ ▶ hours / minutes · number keys type it · OK when done · Back to cancel"), 14f, XAM), lp(20))
        ve()
        hien(cot, null)
        oGio.requestFocus()
    }

    /** Chọn ngày lặp lại: OK bật / tắt từng ngày, nút gợi ý, "Một lần" (chỉ khi thêm việc mới). */
    private fun chonNgay(n: Nhap, the: String) {
        val cot = cot()
        cot.addView(chu(t("Lặp lại", "Repeat"), 26f, dam = true))
        cot.addView(chu(t("OK để bật / tắt từng ngày", "OK turns each day on / off"), 14f, XAM), lp(6))
        cot.addView(hangNut((1..7).map { d ->
            nut(Chu.thu(d), "n-$d", chon = !n.motLan && d in n.ngay) {
                n.ngay = if (!n.motLan && d in n.ngay) n.ngay - d else (if (n.motLan) emptySet() else n.ngay) + d
                n.motLan = false
                chonNgay(n, "n-$d")
            }
        }), lpBoc(14))
        val goiY = listOf(
            Triple("moi", t("Mọi ngày", "Every day"), (1..7).toSet()),
            Triple("t2t6", t("T2–T6", "Mon–Fri"), (1..5).toSet()),
            Triple("t7cn", t("T7, CN", "Sat, Sun"), setOf(6, 7)),
        )
        cot.addView(hangNut(goiY.map { (k, s, ds) ->
            nut(s, "g-$k", chon = !n.motLan && n.ngay == ds) { n.motLan = false; n.ngay = ds; chonNgay(n, "g-$k") }
        } + listOfNotNull(if (n.cu == null) nut(t("Một lần", "Once"), "g-1", chon = n.motLan) { n.motLan = true; n.the = "ngay"; veTruoc() } else null)), lpBoc(12))
        cot.addView(nut(t("Xong", "Done"), "xong") {
            if (!n.motLan && n.ngay.isEmpty()) bao(t("Chọn ít nhất một ngày.", "Pick at least one day.")) else { n.the = "ngay"; veTruoc() }
        }, lpBoc(18))
        hien(cot, the)
    }

    /** Chọn app: ô Kênh nhanh trước rồi theo tên; bật tivi thì có "Không mở app nào". */
    private fun chonApp(n: Nhap) {
        val cot = cot()
        cot.addView(chu(if (n.loai == Viec.BAT) t("Bật xong thì mở", "Then open") else t("App cần mở", "App to open"), 26f, dam = true))
        if (n.loai == Viec.BAT) cot.addView(dong(t("Không mở app nào", "Nothing"), t("Tivi lên màn hình như khi bấm nút nguồn.", "The TV comes up as with the power button."),
            null, XAM, "app-") { n.goi = null; n.the = "app"; veTruoc() }, lp(14))
        dsApp().forEachIndexed { i, (goi, ten) ->
            cot.addView(dong(ten, goi, null, XAM, "app-$goi") { n.goi = goi; n.the = "app"; veTruoc() }, lp(if (i == 0 && n.loai != Viec.BAT) 14 else 6))
        }
        hien(cot, "app-${n.goi.orEmpty()}")
    }

    /** App mở được trên tivi, trừ launcher và chính app này: ô Kênh nhanh trước, rồi theo tên. */
    private fun dsApp(): List<Pair<String, String>> {
        val pm = packageManager
        val goi = listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER).flatMap { c ->
            runCatching { pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(c), 0) }.getOrDefault(emptyList()).map { it.activityInfo.packageName }
        }.toSet() - HenGio.manChinh(this) - packageName
        val kenh = goi.filter { it.startsWith("vn.tivikit.kenh") }.sorted()
        return (kenh + (goi - kenh.toSet()).sortedBy { ten(it).lowercase() }).map { it to ten(it) }
    }

    /** Màn hỏi lại trước khi xoá / huỷ: [co] làm việc đó, Thôi (chọn sẵn) về màn trước. */
    private fun hoi(tieuDe: String, chiTiet: String, co: String, lam: () -> Unit) = mo {
        val cot = cot()
        cot.addView(chu(tieuDe, 26f, dam = true))
        cot.addView(chu(chiTiet, 18f), lp(12))
        cot.addView(hangNut(listOf(nut(co, "co") { lam() }, nut(t("Thôi", "Cancel"), "thoi") { veTruoc() })), lpBoc(22))
        hien(cot, "thoi")
    }

    // ---------- Mã bố mẹ ----------

    /**
     * Việc đổi lịch: Khoá trẻ em đang chạy thì nhờ màn XacNhan của nó hỏi mã bố mẹ (nó tự cho qua khi khoá đang tắt hay chưa
     * có mã), như app Cast; không có Khoá trẻ em thì làm luôn. Đúng mã thì 5 phút không hỏi lại.
     */
    private fun quaMa(viec: String, lam: () -> Unit) {
        val khoaChay = runCatching { Settings.Secure.getString(contentResolver, "enabled_accessibility_services") }.getOrNull()?.contains("$GOI_KHOA/") == true
        if (!khoaChay || SystemClock.elapsedRealtime() < moMaDen) { lam(); return }
        val i = Intent().setClassName(GOI_KHOA, "$GOI_KHOA.XacNhan").putExtra("viec", viec)
        if (runCatching { packageManager.resolveActivity(i, 0) }.getOrNull() == null) {
            bao(t("Khoá trẻ em trên tivi là bản cũ, chưa hỏi mã giúp app khác được: cập nhật nó bằng TiviKit trên điện thoại rồi thử lại.",
                "The Kids lock on this TV is too old to ask for the code here: update it with TiviKit on the phone, then try again."))
            return
        }
        choMa = lam
        runCatching { startActivityForResult(i, MA) }.onFailure { choMa = null }
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req != MA) return
        val f = choMa
        choMa = null
        if (res == RESULT_OK) { moMaDen = SystemClock.elapsedRealtime() + 5 * 60_000L; f?.invoke() }
    }

    // ---------- Vẽ ----------

    private fun ten(goi: String): String = tenKho[goi] ?: tenTam.getOrPut(goi) {
        runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(goi, 0)).toString() }.getOrDefault(goi)
    }

    private fun gioChu(gio: Int, phut: Int) = "%02d:%02d".format(Locale.ROOT, gio, phut)

    private fun bao(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    private fun px(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun hinh(mau: Int, bo: Int) = GradientDrawable().apply { setColor(mau); cornerRadius = px(bo).toFloat() }

    /** Nền ô bấm được: tím sáng khi đang chọn bằng remote, [nen] lúc thường. */
    private fun nenO(nen: Int = O, bo: Int = 14) = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_focused), hinh(TIM, bo))
        addState(intArrayOf(), hinh(nen, bo))
    }

    private fun chu(s: CharSequence, co: Float = 17f, mau: Int = TRANG, dam: Boolean = false) = TextView(this).apply {
        text = s; textSize = co; setTextColor(mau)
        if (dam) typeface = Typeface.DEFAULT_BOLD
    }

    /** Chữ trong ô bấm được: màu [mau] lúc thường, trắng khi ô đang chọn. */
    private fun chuO(s: CharSequence, co: Float, mau: Int, dam: Boolean = false) = chu(s, co, mau, dam).apply {
        setTextColor(ColorStateList(arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()), intArrayOf(TRANG, mau)))
        isDuplicateParentStateEnabled = true
    }

    /** Một dòng bấm được bằng remote: chữ chính, chữ phụ bên dưới, chữ bên phải. */
    private fun dong(chinh: CharSequence, phu: CharSequence?, phai: CharSequence?, mauPhai: Int, the: String, bam: () -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            isFocusable = true; isClickable = true; background = nenO(); tag = the
            setPadding(px(18), px(10), px(18), px(10))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL; isDuplicateParentStateEnabled = true
                addView(chuO(chinh, 17f, TRANG, dam = true))
                phu?.let { addView(chuO(it, 14f, XAM)) }
            }, LinearLayout.LayoutParams(0, WRAP, 1f))
            phai?.let { addView(chuO(it, 16f, mauPhai).apply { setPadding(px(16), 0, 0, 0) }) }
            setOnClickListener { bam() }
        }

    /** Nút viên thuốc; [chon] = đang được chọn (tím đậm). */
    private fun nut(s: String, the: String, chon: Boolean = false, bam: () -> Unit) = TextView(this).apply {
        text = s; textSize = 16f; setTextColor(TRANG); gravity = Gravity.CENTER
        isFocusable = true; isClickable = true; background = nenO(if (chon) TIM_TOI else O, 22); tag = the
        setPadding(px(20), px(9), px(20), px(9))
        setOnClickListener { bam() }
    }

    /** Ô số lớn (giờ, phút, số phút) chọn bằng remote. */
    private fun soLon(s: String) = TextView(this).apply {
        text = s; textSize = 44f; setTextColor(TRANG); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        isFocusable = true; background = nenO(O, 16); minWidth = px(110)
        setPadding(px(22), px(8), px(22), px(8))
    }

    /**
     * ▼ từ dòng rộng cuối cùng của [cot] đi thẳng tới [nut] (nút đầu của hàng nút sắp thêm): không thì Android chọn nút có
     * tâm gần giữa màn nhất, thường là Thôi.
     */
    private fun xuongToi(cot: LinearLayout, nut: View) {
        val dong = (cot.childCount - 1 downTo 0).map { cot.getChildAt(it) }.firstOrNull { it is LinearLayout && it.isFocusable } ?: return
        if (nut.id == View.NO_ID) nut.id = View.generateViewId()
        dong.nextFocusDownId = nut.id
    }

    private fun hangNut(nuts: List<View>) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        nuts.forEachIndexed { i, n -> addView(n, LinearLayout.LayoutParams(WRAP, WRAP).apply { if (i > 0) marginStart = px(10) }) }
    }

    private fun muc(s: String) = chu(s, 15f, TIM_NHAT, dam = true)

    /** Cột nội dung của một màn. */
    private fun cot() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(px(48), px(28), px(48), px(28)) }

    private fun lp(tren: Int = 0) = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = px(tren) }

    private fun lpBoc(tren: Int = 0) = LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = px(tren) }

    /** Hiện màn [cot] (cuộn được), chọn sẵn ô có thẻ [the]; không có thì ô đầu tiên. */
    private fun hien(cot: LinearLayout, the: String?) {
        setContentView(ScrollView(this).apply { isFillViewport = true; isVerticalScrollBarEnabled = false; addView(cot) })
        (the?.let { cot.findViewWithTag<View>(it) } ?: dauTien(cot))?.requestFocus()
    }

    private fun dauTien(v: View): View? {
        if (v.isFocusable && v.visibility == View.VISIBLE) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) dauTien(v.getChildAt(i))?.let { return it }
        return null
    }

    companion object {
        private const val GOI_KHOA = "vn.tivikit.khoa"
        private const val MA = 7
        private const val NHIP_MS = 15_000L
        private val PHUT_NHANH = listOf(15, 30, 45, 60, 90, 120)
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        private val NEN_TREN = 0xFF0F172A.toInt()
        private val NEN_DUOI = 0xFF2E1065.toInt()
        /** Ô thường: trắng mờ. */
        private const val O = 0x26FFFFFF
        /** Ô đang chọn bằng remote, và ô đang được chọn (đậm vừa). */
        private val TIM = 0xFF7C3AED.toInt()
        private val TIM_TOI = 0x997C3AED.toInt()
        private val TIM_NHAT = 0xFFC4B5FD.toInt()
        private val TRANG = 0xFFF8FAFC.toInt()
        private val XAM = 0xFF94A3B8.toInt()
        private val XANH = 0xFF34D399.toInt()
        private val VANG = 0xFFFBBF24.toInt()
    }
}
