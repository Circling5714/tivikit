package vn.tivikit.launcher

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDateTime

/** Mọi thứ màn Home vẽ ra. Trên tivi DieuKhien cập nhật; ảnh xem trước (Paparazzi) dựng tay. */
class TrangThaiHome {
    var nen by mutableStateOf<NenKinh?>(null)
    var bayGio by mutableStateOf(LocalDateTime.now())
    var ten by mutableStateOf("")
    var apps by mutableStateOf<List<AppTv>>(emptyList())
    val theoGoi by derivedStateOf { apps.associateBy(AppTv::goi) }
    var dock by mutableStateOf<List<String>>(emptyList())
    var ganDay by mutableStateOf<List<String>>(emptyList())
    val anh = mutableStateMapOf<String, AnhApp>()

    var ramTrong by mutableLongStateOf(0L)
    var boNhoTrong by mutableLongStateOf(0L)
    var mang by mutableStateOf<HeThong.Mang?>(null)
    /** Byte mỗi giây; -1 = tivi không đếm được. */
    var tocXuong by mutableLongStateOf(-1L)
    var tocLen by mutableLongStateOf(-1L)
    var coDonRam by mutableStateOf(true)
    var dangDon by mutableStateOf(false)
    /** Thời tiết nơi TiviKit gửi; null = chưa có nơi / chưa lấy được. */
    var thoiTiet by mutableStateOf<ThoiTiet.HienTai?>(null)
    /** Bài / video đang phát hay tạm dừng trên tivi (thẻ Đang phát); null = không có. */
    var dangPhat by mutableStateOf<BaiDangPhat?>(null)
    /** Câu kinh hôm nay dưới âm lịch; null = đã tắt / chưa đọc xong. */
    var cau by mutableStateOf<CauHomNay?>(null)
    /** Tivi có app Khoá trẻ em của TiviKit: menu ⏻ có mục Khoá trẻ em ngay. */
    var coKhoa by mutableStateOf(false)

    var thuVien by mutableStateOf(false)
    /** Gói đang mở menu (giữ OK). */
    var menu by mutableStateOf<String?>(null)
    /** Lớp đè lên màn Home: bảng lịch (OK ở đồng hồ), cả câu kinh (OK ở thẻ câu), menu ⏻. */
    var lich by mutableStateOf(false)
    var xemCau by mutableStateOf(false)
    var menuNguon by mutableStateOf(false)
    /** Có lớp đè (thư viện, menu, bảng lịch…): các ô bên dưới không nhận lựa chọn. */
    val coLopDe: Boolean get() = thuVien || menu != null || lich || xemCau || menuNguon
    /** Gói đang dời chỗ trong dock. */
    var dangDoi by mutableStateOf<String?>(null)
    /** Ô cần nhận lại lựa chọn sau khi đóng menu / thư viện / lớp đè (tên gói, hoặc [TAT_CA], [GIO], [CAU], [NGUON]). */
    var traChon by mutableStateOf<String?>(null)
    /** Tăng mỗi lần bấm Home: chọn lại ô đầu dock. */
    var lanVeDau by mutableIntStateOf(0)
    var bao by mutableStateOf<String?>(null)

    var hd: HanhDong = object : HanhDong {}

    companion object {
        const val TAT_CA = "*tat-ca*"
        const val GIO = "*gio*"
        const val CAU = "*cau*"
        const val NGUON = "*nguon*"
    }
}

/** Câu kinh của hôm nay: [ten] tên bộ TiviKit gửi kèm bộ riêng (null = Kinh Pháp Cú đi kèm, "" = bộ riêng không tên). */
class CauHomNay(val ten: String?, val cau: CauMoiNgay.Cau)

/** Việc màn Home nhờ làm (mở app, sửa dock…); mặc định không làm gì để ảnh xem trước dựng được. */
interface HanhDong {
    fun mo(goi: String) {}
    fun canAnh(goi: String) {}
    fun donRam() {}
    fun moMang() {}
    fun moCaiDat() {}
    fun ghim(goi: String) {}
    fun boGhim(goi: String) {}
    fun doi(goi: String, buoc: Int) {}
    fun xongDoi() {}
    fun thongTin(goi: String) {}
    fun go(goi: String) {}
    fun moThuVien() {}
    fun dongThuVien() {}
    fun dongMenu() {}
    /** Mở app đang phát (thẻ Đang phát). */
    fun moDangPhat() {}
    /** Tạm dừng / phát tiếp bài đang phát. */
    fun dungPhat() {}
    /** Menu ⏻: tắt tivi như nút nguồn, mở menu nguồn của tivi (khởi động lại), khoá trẻ em ngay. */
    fun tatTivi() {}
    fun khoiDongLai() {}
    fun khoaTreEm() {}
}
