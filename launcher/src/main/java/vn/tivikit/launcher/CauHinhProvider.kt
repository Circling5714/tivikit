package vn.tivikit.launcher

import android.content.ComponentName
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.Process
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException

/**
 * TiviKit đặt / đọc cấu hình qua ADB:
 *   content call --uri content://vn.tivikit.launcher --method dat --arg '{"ten":"Nhà bác Hùng","dock":["org.smarttube.stable"],"ngon_ngu":"vi"}'
 *   content call --uri content://vn.tivikit.launcher --method doc
 *   content call --uri content://vn.tivikit.launcher --method bat-home   (hoặc tat-home)
 *   content write --uri content://vn.tivikit.launcher/anh-nen.jpg < ảnh.jpg   (ảnh nền riêng; content delete để về ảnh mặc định)
 *   content write --uri content://vn.tivikit.launcher/anh-app/vn.tivikit.kenh.jpg < logo.jpg   (ảnh thay banner của một app:
 *     logo kênh của ô Kênh nhanh, như app_banners của Home sạch; content delete để về banner của app)
 *   content write --uri content://vn.tivikit.launcher/cau.txt < bo-cau.txt   (bộ câu riêng thay Kinh Pháp Cú đi kèm, bản 1.4
 *     trở đi; content delete để về Kinh Pháp Cú; bật / tắt và tên bộ: dat {"cau":{"bat":true,"ten":"…"}})
 * bat-home / tat-home bật / tắt bí danh .Home (bộ lọc HOME): ADB không đổi được trạng thái từng thành phần của app thường
 * ("Shell cannot change component state", chỉ đổi được cả gói), còn app thì tự đổi được thành phần của mình.
 * Chỉ nhận lời gọi từ shell (ADB) hoặc root: app khác trên tivi không đổi được.
 */
class CauHinhProvider : ContentProvider() {
    override fun onCreate() = true

    private fun chiAdb() {
        val uid = Binder.getCallingUid()
        if (uid != Process.SHELL_UID && uid != 0 && uid != Process.myUid()) throw SecurityException("chỉ ADB")
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        chiAdb()
        val ctx = context!!
        val ch = CauHinh(ctx)
        val kq = Bundle()
        when (method) {
            "dat" -> {
                val moi = runCatching { JSONObject(arg.orEmpty()) }.getOrNull() ?: return kq.apply { putString("loi", "json sai") }
                var nhan = emptyList<String>()
                ch.sua { nhan = CauHinh.tron(it, moi) }
                kq.putString("ok", nhan.joinToString(",").ifEmpty { "-" })
            }
            "doc" -> {
                val pm = ctx.packageManager
                val pi = pm.getPackageInfo(ctx.packageName, 0)
                @Suppress("DEPRECATION")
                val ma = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
                val homeBat = pm.getComponentEnabledSetting(ComponentName(ctx, HOME)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                // Bài đang phát (như thẻ Đang phát đọc), hoặc "khong-co-quyen" (box RAM thấp không cho đọc phiên phát): TiviKit
                // báo ở thẻ, và thử được phần đọc khi app phát nhạc đang mở.
                val dangPhat: Any = try {
                    DangPhat.doc(ctx, { g -> runCatching { pm.getApplicationLabel(pm.getApplicationInfo(g, 0)).toString() }.getOrDefault(g) })?.first?.let { b ->
                        JSONObject().put("goi", b.goi).put("tieuDe", b.tieuDe).put("phu", b.phu).put("dangPhat", b.dangPhat)
                    } ?: JSONObject.NULL
                } catch (_: SecurityException) {
                    "khong-co-quyen"
                } catch (_: Exception) {
                    JSONObject.NULL
                }
                // Ảnh riêng của từng app đang giữ: gói → cỡ file (TiviKit biết launcher nhận ảnh riêng, bản 1.1 trở đi).
                val anhApp = JSONObject()
                File(ctx.filesDir, ANH_APP).listFiles()?.filter { it.name.endsWith(".jpg") }?.forEach { anhApp.put(it.name.removeSuffix(".jpg"), it.length()) }
                // Câu kinh mỗi ngày (bản 1.4 trở đi): bật không, bộ riêng hay Kinh Pháp Cú đi kèm, có bao nhiêu câu.
                val cj = ch.doc()
                val (rieng, bo) = runCatching { docBoCau(ctx) }.getOrDefault(false to emptyList())
                val cau = JSONObject().put("bat", CauHinh.cauBat(cj)).put("ten", CauHinh.tenCau(cj)).put("rieng", rieng)
                    .put("so", bo.sumOf { it.soLuong }).put("co", File(ctx.filesDir, CAU).length())
                val o = JSONObject().put("ban", pi.versionName).put("ma", ma).put("homeBat", homeBat).put("cauHinh", cj)
                    .put("nen", File(ctx.filesDir, ANH_NEN).length()).put("anhApp", anhApp).put("dangPhat", dangPhat).put("cau", cau)
                kq.putString("json", o.toString())
            }
            "bat-home", "tat-home" -> {
                // tat-home trả về mặc định trong manifest (tắt). DONT_KILL_APP: đang chạy chính trong tiến trình này.
                val trangThai = if (method == "bat-home") PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
                ctx.packageManager.setComponentEnabledSetting(ComponentName(ctx, HOME), trangThai, PackageManager.DONT_KILL_APP)
                kq.putString("ok", "1")
            }
            else -> kq.putString("loi", "khong biet $method")
        }
        return kq
    }

    /**
     * Ảnh nền riêng, hoặc ảnh riêng của một app (anh-app/<gói>.jpg): ghi vào file tạm, đóng xong mới kiểm (giải mã được,
     * không quá 12 MB / 2 MB) rồi thay ảnh cũ và báo màn Home vẽ lại; ảnh hỏng thì bỏ, ảnh cũ giữ nguyên. Đọc ("r") trả
     * ảnh đang dùng.
     */
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        chiAdb()
        if (uri.pathSegments == listOf(CAU)) return moCau(mode)
        val goi = goiAnhApp(uri)
        val dich = fileCua(uri, goi) ?: throw FileNotFoundException(uri.toString())
        if (!mode.contains('w')) return ParcelFileDescriptor.open(dich, ParcelFileDescriptor.MODE_READ_ONLY)
        dich.parentFile?.mkdirs()
        val tam = File(dich.path + ".tmp")
        val toiDa = if (goi != null) 2L * 1024 * 1024 else 12L * 1024 * 1024
        val ghi = ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE
        return ParcelFileDescriptor.open(tam, ghi, Handler(Looper.getMainLooper())) { loi ->
            if (loi == null && tam.length() in 1..toiDa && AnhNen.hopLe(tam) && tam.renameTo(dich)) daDoi(goi)
            else tam.delete()
        }
    }

    /** Bộ câu riêng: như ảnh, ghi vào file tạm, đóng xong mới kiểm (không quá 1 MB, tách ra được ít nhất một câu) rồi thay. */
    private fun moCau(mode: String): ParcelFileDescriptor {
        val dich = File(context!!.filesDir, CAU)
        if (!mode.contains('w')) return ParcelFileDescriptor.open(dich, ParcelFileDescriptor.MODE_READ_ONLY)
        val tam = File(dich.path + ".tmp")
        val ghi = ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE
        return ParcelFileDescriptor.open(tam, ghi, Handler(Looper.getMainLooper())) { loi ->
            val ok = loi == null && tam.length() in 1..CAU_TOI_DA &&
                runCatching { CauMoiNgay.tach(CauMoiNgay.giaiMa(tam.readBytes())).isNotEmpty() }.getOrDefault(false)
            if (ok && tam.renameTo(dich)) CauHinh.lanDoi.value++ else tam.delete()
        }
    }

    /** Gói trong Uri anh-app/<gói>.jpg; null nếu Uri không phải ảnh riêng của app. */
    private fun goiAnhApp(u: Uri): String? {
        val p = u.pathSegments
        if (p.size != 2 || p[0] != ANH_APP || !p[1].endsWith(".jpg")) return null
        return p[1].removeSuffix(".jpg").takeIf(Dock::hopLe)
    }

    /** File của ảnh nền riêng, hoặc của ảnh riêng app [goi]; null nếu [u] không phải hai loại đó. */
    private fun fileCua(u: Uri, goi: String?): File? {
        val dir = context!!.filesDir
        return if (goi != null) anhApp(dir, goi) else if (u.pathSegments == listOf(ANH_NEN)) File(dir, ANH_NEN) else null
    }

    /** Báo màn Home: ảnh nền ([goi] null) hoặc ảnh riêng của app [goi] vừa đổi. */
    private fun daDoi(goi: String?) {
        if (goi == null) { CauHinh.lanNen.value++; return }
        CauHinh.anhDoi += goi
        CauHinh.lanAnh.value++
    }

    companion object {
        /** Bí danh có bộ lọc HOME (AndroidManifest.xml). */
        const val HOME = "vn.tivikit.launcher.Home"
        const val ANH_NEN = "anh-nen.jpg"
        /** Thư mục ảnh riêng của từng app (logo kênh của ô Kênh nhanh) TiviKit gửi. */
        const val ANH_APP = "anh-app"
        /** Bộ câu kinh riêng TiviKit gửi, thay Kinh Pháp Cú đi kèm (assets/phap-cu.txt). */
        const val CAU = "cau.txt"
        private const val CAU_TOI_DA = 1024L * 1024

        fun anhApp(dir: File, goi: String) = File(File(dir, ANH_APP), "$goi.jpg")

        /** Bộ câu đang dùng: bộ riêng (cau.txt) nếu đọc được, không thì Kinh Pháp Cú đi kèm; true = bộ riêng. */
        fun docBoCau(ctx: Context): Pair<Boolean, List<CauMoiNgay.Cau>> {
            val f = File(ctx.filesDir, CAU)
            if (f.isFile) runCatching { CauMoiNgay.tach(CauMoiNgay.giaiMa(f.readBytes())) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return true to it }
            return false to ctx.assets.open("phap-cu.txt").use { CauMoiNgay.tach(CauMoiNgay.giaiMa(it.readBytes())) }
        }
    }

    override fun query(u: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?) = null
    override fun getType(u: Uri): String? = null
    override fun insert(u: Uri, v: ContentValues?): Uri? = null

    /** Bỏ ảnh nền riêng (về ảnh mặc định), ảnh riêng của một app (về banner của app), hay bộ câu riêng (về Kinh Pháp Cú). */
    override fun delete(u: Uri, s: String?, a: Array<out String>?): Int {
        chiAdb()
        if (u.pathSegments == listOf(CAU)) {
            val xoa = File(context!!.filesDir, CAU).delete()
            CauHinh(context!!).sua { j -> j.optJSONObject("cau")?.let { it.remove("ten"); if (it.length() == 0) j.remove("cau") } }
            return if (xoa) 1 else 0
        }
        val goi = goiAnhApp(u)
        val f = fileCua(u, goi) ?: return 0
        val xoa = f.delete()
        daDoi(goi)
        return if (xoa) 1 else 0
    }
    override fun update(u: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
}
