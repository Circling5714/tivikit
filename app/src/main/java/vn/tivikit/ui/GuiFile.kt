package vn.tivikit.ui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import vn.tivikit.adb.AppTrenTv
import vn.tivikit.adb.FileTrenTv
import vn.tivikit.adb.ThaoTacTv
import vn.tivikit.t

/** Gửi ảnh / video / file từ điện thoại lên tivi rồi mở bằng app trên tivi (25/10/2026). */
class GuiFile(private val vm: PhienLamViec) {
    enum class TrangThai { CHO, DANG_GUI, XONG, LOI }

    class FileGui(val uri: Uri, val ten: String, val co: Long, val kieu: String) {
        var trangThai by mutableStateOf(TrangThai.CHO)
        var tienDo by mutableStateOf(0f)
        var bao by mutableStateOf("")
        var duongDanTv by mutableStateOf<String?>(null)
    }

    val ds = mutableStateListOf<FileGui>()
    @Volatile private var phanTramCu = -1
    private val fileTv by lazy { FileTrenTv(vm.adb) }

    fun mo() { vm.man = Man.GUI_FILE }

    fun quen() { ds.clear() }

    /** Thêm file đã chọn: tên, cỡ, loại đọc từ trình chọn (không cần tivi). */
    fun them(uris: List<Uri>) {
        vm.viewModelScope.launch {
            for (u in uris) {
                if (ds.any { it.uri == u }) continue
                val f = vm.io {
                    val cr = vm.ctx.contentResolver
                    var ten = "file"; var co = -1L
                    cr.query(u, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                        if (c.moveToFirst()) { c.getString(0)?.let { ten = it }; if (!c.isNull(1)) co = c.getLong(1) }
                    }
                    if (co < 0) co = runCatching { cr.openAssetFileDescriptor(u, "r")?.use { it.length } }.getOrNull() ?: -1
                    FileGui(u, FileTrenTv.tenAnToan(ten), co, cr.getType(u) ?: "*/*")
                }
                if (f.co < 0) { f.trangThai = TrangThai.LOI; f.bao = t("Không biết cỡ file này, không gửi được.", "Unknown file size, can't send it.") }
                ds += f
            }
        }
    }

    fun bo(f: FileGui) { ds.remove(f) }

    fun guiTatCa() = vm.chay(t("Đang gửi lên tivi…", "Sending to the TV…")) {
        vm.damBaoKetNoi()
        val cho = ds.filter { it.trangThai == TrangThai.CHO || it.trangThai == TrangThai.LOI && it.co >= 0 }
        if (cho.isEmpty()) return@chay
        val tong = cho.sumOf { it.co }
        val con = vm.io { AppTrenTv(vm.adb).boNho() }?.conKb?.times(1024)
        if (con != null && tong + 50_000_000 > con) error(t("Tivi chỉ còn trống ${coChu(con)}, không đủ cho ${coChu(tong)}. Dọn bớt ở Quản lý app trên tivi.",
            "The TV has only ${coChu(con)} free, not enough for ${coChu(tong)}. Free some space under Apps on the TV."))
        val coSan = vm.io { fileTv.chuanBi() }.toMutableSet()
        for (f in cho) {
            val ten = FileTrenTv.tenKhongTrung(f.ten, coSan)
            coSan += ten
            f.trangThai = TrangThai.DANG_GUI; f.bao = ""; f.tienDo = 0f
            val viec = t("Đang gửi ${f.ten} lên tivi…", "Sending ${f.ten} to the TV…")
            vm.thongBao = viec; phanTramCu = -1
            val batDau = System.nanoTime()
            try {
                f.duongDanTv = vm.io {
                    (vm.ctx.contentResolver.openInputStream(f.uri) ?: error(t("Không mở được file.", "Couldn't open the file."))).use { vao ->
                        fileTv.gui(ten, vao, f.co) { n -> baoTienDo(f, viec, n, batDau) }
                    }
                }
                f.trangThai = TrangThai.XONG; f.tienDo = 1f
            } catch (e: Throwable) {
                f.trangThai = TrangThai.LOI
                f.bao = if (ThaoTacTv.laMatKetNoi(e)) t("Mất kết nối giữa chừng. Bấm Gửi để gửi lại.", "Lost the connection. Press Send to retry.") else (e.message ?: e.javaClass.simpleName)
                if (ThaoTacTv.laMatKetNoi(e)) break
            }
        }
    }

    fun moTrenTv(f: FileGui) = vm.chay(t("Đang mở ${f.ten} trên tivi…", "Opening ${f.ten} on the TV…")) {
        val p = f.duongDanTv ?: return@chay
        vm.damBaoKetNoi()
        if (!vm.io { fileTv.mo(p, f.kieu) }) error(t("Tivi không có app mở được loại file này. Cài VLC (Cài app lên tivi → Gợi ý) rồi thử lại, hoặc mở thư mục Download/TiviKit trên tivi.",
            "No app on the TV can open this kind of file. Install VLC (Install an app → Suggestions) and retry, or open Download/TiviKit on the TV."))
    }

    private fun baoTienDo(f: FileGui, viec: String, da: Long, batDau: Long) {
        val pt = if (f.co > 0) (da * 100 / f.co).toInt() else 0
        if (pt == phanTramCu) return
        phanTramCu = pt
        val them = vm.tocDo(da, f.co, batDau)
        vm.viewModelScope.launch(Dispatchers.Main) { f.tienDo = pt / 100f; vm.thongBao = "$viec $pt%$them" }
    }
}
