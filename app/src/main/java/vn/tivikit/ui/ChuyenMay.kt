package vn.tivikit.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import vn.tivikit.BuildConfig
import vn.tivikit.dulieu.KhoBoCai
import vn.tivikit.dulieu.SaoLuuApp
import vn.tivikit.t
import java.io.File

/**
 * Sao lưu & chuyển máy (25/10/2026): lưu dữ liệu TiviKit ra một file zip (bản sao lưu tivi, bộ cài đặt mẫu, tivi gần
 * đây, tuỳ chọn khoá kết nối) và nhập lại trên điện thoại khác. Nhập khoá kết nối thì phải mở lại app (khoá được nạp
 * một lần lúc mở app).
 */
class ChuyenMay(private val vm: PhienLamViec) {
    /** Kèm khoá kết nối khi lưu (mặc định không: ai có file là điều khiển được tivi đã cho phép). */
    var kemKhoa by mutableStateOf(false)
    var soBoCai by mutableStateOf(0)
        private set
    /** Tổng cỡ file của các bộ cài đặt mẫu (byte). */
    var coBoCai by mutableStateOf(0L)
        private set
    var bao by mutableStateOf("")
        private set
    /** Vừa nhập khoá kết nối: cần mở lại app. */
    var canMoLai by mutableStateOf(false)
        private set
    /** File vừa chọn có kèm khoá kết nối: chờ người dùng chọn dùng khoá trong file hay giữ khoá đang có. */
    var choNhap by mutableStateOf<File?>(null)
        private set

    fun mo() = vm.chay(t("Đang đọc dữ liệu…", "Reading the data…")) {
        bao = ""
        val kho = KhoBoCai(vm.ctx.filesDir)
        soBoCai = vm.io { kho.tatCa().size }
        coBoCai = vm.io { kho.thuMuc.walkTopDown().filter { it.isFile }.sumOf { it.length() } }
        vm.man = Man.CHUYEN_MAY
    }

    fun tenFile(): String = "tivikit-sao-luu-" + java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()) + ".zip"

    fun luu(uri: Uri) = vm.chay(t("Đang lưu dữ liệu ra file…", "Saving the data to a file…")) {
        bao = ""
        val nd = vm.io {
            (vm.ctx.contentResolver.openOutputStream(uri) ?: error(t("Không mở được file để ghi.", "Couldn't open the file for writing."))).use {
                SaoLuuApp.dongGoi(vm.ctx.filesDir, vm.caiDatChoSaoLuu(), kemKhoa, BuildConfig.VERSION_NAME, it)
            }
        }
        bao = t("Đã lưu ${nd.soSaoLuu} bản sao lưu tivi, ${nd.soBoCai} bộ cài đặt mẫu, tivi gần đây", "Saved ${nd.soSaoLuu} TV backups, ${nd.soBoCai} setup sets and recent TVs") +
            (if (nd.coKhoa) t(", kèm khoá kết nối. Giữ file này cẩn thận.", ", plus the connection key. Keep this file safe.") else ".")
    }

    /** Đọc file người dùng chọn; có khoá kết nối thì hỏi trước khi nhập. */
    fun moFile(uri: Uri) = vm.chay(t("Đang đọc file…", "Reading the file…")) {
        bao = ""; canMoLai = false
        val tam = File(vm.ctx.cacheDir, "nhap-sao-luu.zip")
        vm.io { (vm.ctx.contentResolver.openInputStream(uri) ?: error(t("Không mở được file.", "Couldn't open the file."))).use { i -> tam.outputStream().use { i.copyTo(it) } } }
        val nd = runCatching { vm.io { SaoLuuApp.xem(tam) } }.getOrElse {
            tam.delete(); error(t("File này không phải bản sao lưu TiviKit.", "That file isn't a TiviKit backup."))
        }
        if (nd.coKhoa) choNhap = tam else nhap(tam, dungKhoa = false)
    }

    /** Trả lời hộp hỏi khoá kết nối. */
    fun xacNhan(dungKhoa: Boolean) {
        val tam = choNhap ?: return
        choNhap = null
        vm.chay(t("Đang nhập dữ liệu…", "Importing the data…")) { nhap(tam, dungKhoa) }
    }

    fun boNhap() { choNhap?.delete(); choNhap = null }

    private suspend fun nhap(tam: File, dungKhoa: Boolean) {
        val kq = try { vm.io { SaoLuuApp.nhap(tam, vm.ctx.filesDir, dungKhoa) } } finally { tam.delete() }
        kq.caiDat?.optJSONArray("tivi_gan_day")?.let { vm.gopTiviGanDay(it) }
        vm.taiLaiSaoLuu()
        val kho = KhoBoCai(vm.ctx.filesDir)
        soBoCai = vm.io { kho.tatCa().size }
        coBoCai = vm.io { kho.thuMuc.walkTopDown().filter { it.isFile }.sumOf { it.length() } }
        canMoLai = kq.daNhapKhoa
        bao = listOfNotNull(
            t("Đã nhập ${kq.saoLuuMoi + kq.saoLuuGop} bản sao lưu tivi", "Imported ${kq.saoLuuMoi + kq.saoLuuGop} TV backups") +
                (if (kq.saoLuuGop > 0) t(" (${kq.saoLuuGop} gộp với bản đang có)", " (${kq.saoLuuGop} merged with existing ones)") else ""),
            t("${kq.boCaiMoi} bộ cài đặt mẫu", "${kq.boCaiMoi} setup sets") + (if (kq.boCaiTrung > 0) t(" (bỏ qua ${kq.boCaiTrung} bộ đã có)", " (${kq.boCaiTrung} already here, skipped)") else ""),
            t("tivi gần đây", "recent TVs"),
        ).joinToString(", ") + "." + (if (kq.daNhapKhoa) t(" Đã nhập khoá kết nối: mở lại app để dùng.", " The connection key is imported: reopen the app to use it.") else "")
    }

    /** Mở lại app (nạp khoá kết nối vừa nhập). */
    fun moLaiApp() {
        val pm = vm.ctx.packageManager
        val i = pm.getLaunchIntentForPackage(vm.ctx.packageName) ?: pm.getLeanbackLaunchIntentForPackage(vm.ctx.packageName) ?: return
        vm.ctx.startActivity(Intent.makeRestartActivityTask(i.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        Runtime.getRuntime().exit(0)
    }
}
