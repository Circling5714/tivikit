package vn.tivikit.dulieu

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Hỏi lại khi người dùng tự tích gói nhiều danh sách ghi phải giữ (23/10/2026, TCL BeyondTV4 tắt com.tcl.suspension). */
class NenGiuTest {
    private val du = DuLieu.docJson(File("src/main/assets/du-lieu.json").readText())
    private fun muc(pkg: String, chonSan: Boolean = false): MucGoi {
        val tk = du.thongKe[pkg] ?: ThongKeGoi(0, 0)
        return MucGoi(pkg, LoaiMuc.CHUNG, "", "", tk.soMauTat, tk.soMauGiu, chonSan)
    }

    @Test fun nutInputTcl_hoiLai() = assertTrue(KeHoach.nenGiu(muc("com.tcl.suspension")))
    @Test fun appTichSan_khongHoi() = assertFalse(KeHoach.nenGiu(muc("com.tcl.suspension", chonSan = true)))
    @Test fun goiHayTat_khongHoi() = assertFalse(KeHoach.nenGiu(muc("com.tcl.logkit")))
}
