package vn.tivikit.dulieu

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import vn.tivikit.t
import java.io.File
import java.util.zip.ZipFile

/** Một phần APK cần gửi lên tivi: [muc] là tên mục trong gói zip ("" = chính file .apk) và cỡ byte. */
data class PhanApk(val muc: String, val co: Long)

/** Đọc từ APK chính bằng PackageManager của điện thoại, trước khi gửi. */
data class ThongTinApk(val pkg: String, val ten: String, val versionName: String, val versionCode: Long, val minSdk: Int)

/**
 * File APK sẽ cài: các phần sẽ cài, thông tin app, cỡ dữ liệu OBB bỏ lại. [tam] = bản chép trong bộ nhớ đệm, cài xong / bỏ
 * thì xoá; false = file của bộ cài mẫu, giữ lại.
 */
class FileApk(val ten: String, val file: File, val laGoi: Boolean, val phan: List<PhanApk>, val thongTin: ThongTinApk?, val coObb: Long, val tam: Boolean = true) {
    val co: Long get() = phan.sumOf { it.co }
}

/**
 * Cài app người dùng chọn lên tivi (25/10/2026): file .apk thường và gói chia nhỏ .apks (SAI / bundletool), .xapk
 * (APKPure), .apkm (APKMirror) — đều là file zip chứa nhiều APK, cài một lượt như `adb install-multiple`.
 */
object GoiApk {
    val DUOI_GOI = setOf("apks", "xapk", "apkm")

    /** Loại file cho trình chọn file: .apk có kiểu riêng, gói chia nhỏ thường hiện là zip / octet-stream. */
    val KIEU_FILE = arrayOf(
        "application/vnd.android.package-archive", "application/octet-stream", "application/zip",
        "application/x-zip-compressed", "application/xapk-package-archive", "application/vnd.apkm",
    )

    /** Tên ABI trong tên file split. x86_64 / armeabi_v7a đứng TRƯỚC x86 / armeabi: tên dài phải khớp trước. */
    private val ABI = listOf("arm64_v8a", "armeabi_v7a", "armeabi", "x86_64", "x86", "mips64", "mips")

    private fun tenFile(p: PhanApk) = p.muc.substringAfterLast('/').lowercase()

    fun abiCua(p: PhanApk): String? {
        val ten = tenFile(p).removeSuffix(".apk")
        return ABI.firstOrNull { a -> Regex("""(^|[._-])$a([._-]|$)""").containsMatchIn(ten) }
    }

    /**
     * Các phần APK cần cài trong gói. bundletool .apks có splits/ (cài cùng nhau) và standalones/ (mỗi file một kiểu
     * máy): chỉ lấy splits/; chỉ có universal.apk thì cài mình nó. Phần cho chip khác bỏ đi, chỉ giữ ABI tivi ưu tiên
     * nhất có trong gói (như SAI); gói không có ABI nào tivi chạy được thì giữ nguyên để Android tự báo lỗi rõ ràng.
     */
    fun chonPhan(muc: List<PhanApk>, abiTv: List<String>): List<PhanApk> {
        var apk = muc.filter { it.muc.endsWith(".apk", ignoreCase = true) }
        if (apk.any { it.muc.startsWith("splits/") }) apk = apk.filter { it.muc.startsWith("splits/") }
        else apk.firstOrNull { tenFile(it) == "universal.apk" }?.let { return listOf(it) }
        val tv = abiTv.map { it.trim().replace('-', '_').lowercase() }.filter { it.isNotEmpty() }
        val coAbi = apk.filter { abiCua(it) != null }
        val tot = tv.firstOrNull { a -> coAbi.any { abiCua(it) == a } } ?: return apk
        return apk.filter { abiCua(it).let { a -> a == null || a == tot } }
    }

    /** Phần chính (base) để đọc tên app: base.apk / base-master.apk, rồi file không mang tên split, rồi file lớn nhất. */
    fun phanChinh(ds: List<PhanApk>): PhanApk? =
        ds.firstOrNull { tenFile(it) == "base.apk" || tenFile(it) == "base-master.apk" }
            ?: ds.firstOrNull { p -> tenFile(p).let { !it.startsWith("config.") && !it.startsWith("split_") && !it.startsWith("base-") } }
            ?: ds.maxByOrNull { it.co }

    /** Chép file từ trình chọn file vào bộ nhớ đệm (cần cỡ chính xác cho `-S` và đọc được bằng ZipFile). */
    fun doc(ctx: Context, uri: Uri, abiTv: List<String>, tienDo: (Float) -> Unit = {}): FileApk {
        val cr = ctx.contentResolver
        var ten = "app.apk"; var coKhai = -1L
        cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getString(0)?.let { ten = it }
                if (!c.isNull(1)) coKhai = c.getLong(1)
            }
        }
        val duoi = ten.substringAfterLast('.', "").lowercase()
        if (duoi != "apk" && duoi !in DUOI_GOI) error(t("\"$ten\" không phải file APK (.apk, .apks, .xapk, .apkm).", "\"$ten\" is not an APK file (.apk, .apks, .xapk, .apkm)."))
        val f = File(thuMuc(ctx), "${System.nanoTime()}.$duoi")
        try {
            (cr.openInputStream(uri) ?: error(t("Không mở được \"$ten\".", "Couldn't open \"$ten\"."))).use { vao ->
                f.outputStream().use { ra ->
                    val buf = ByteArray(64 * 1024)
                    var da = 0L
                    while (true) {
                        val n = vao.read(buf)
                        if (n < 0) break
                        ra.write(buf, 0, n)
                        da += n
                        if (coKhai > 0) tienDo(da.toFloat() / coKhai)
                    }
                }
            }
            return tuFile(ctx, ten, f, abiTv, tam = true)
        } catch (e: Throwable) {
            f.delete(); throw e
        }
    }

    /** FileApk từ file đã có trên điện thoại (bản chép của doc, hoặc file của bộ cài mẫu với [tam] = false). */
    fun tuFile(ctx: Context, ten: String, f: File, abiTv: List<String>, tam: Boolean): FileApk =
        if (f.name.substringAfterLast('.', "").lowercase() == "apk") FileApk(ten, f, false, listOf(PhanApk("", f.length())), thongTin(ctx, f), 0, tam)
        else docGoi(ctx, ten, f, abiTv, tam)

    private fun docGoi(ctx: Context, ten: String, f: File, abiTv: List<String>, tam: Boolean): FileApk {
        val muc = runCatching { ZipFile(f).use { z -> z.entries().toList().filter { !it.isDirectory }.map { PhanApk(it.name, it.size) } } }
            .getOrElse { error(t("\"$ten\" hỏng hoặc bị mã hoá, không mở được như file zip.", "\"$ten\" is damaged or encrypted and can't be opened as a zip.")) }
        val phan = chonPhan(muc, abiTv)
        if (phan.isEmpty()) error(t("Trong \"$ten\" không có file APK nào.", "There is no APK inside \"$ten\"."))
        // Gói .xapk của game có thể kèm dữ liệu OBB: chưa gửi được, chỉ báo cỡ để người dùng biết.
        val coObb = muc.filter { it.muc.startsWith("Android/obb/", ignoreCase = true) }.sumOf { it.co }
        val chinh = phanChinh(phan)
        val tt = chinh?.let { c ->
            val tam = File(thuMuc(ctx), "${System.nanoTime()}.base.apk")
            try {
                ZipFile(f).use { z -> z.getInputStream(z.getEntry(c.muc)).use { vao -> tam.outputStream().use { vao.copyTo(it) } } }
                thongTin(ctx, tam)
            } catch (e: Exception) { null } finally { tam.delete() }
        }
        return FileApk(ten, f, true, phan, tt, coObb, tam)
    }

    private fun thongTin(ctx: Context, apk: File): ThongTinApk? = runCatching {
        val pm = ctx.packageManager
        @Suppress("DEPRECATION")
        val pi = pm.getPackageArchiveInfo(apk.path, 0) ?: return null
        val ai = pi.applicationInfo ?: return null
        ai.sourceDir = apk.path; ai.publicSourceDir = apk.path
        @Suppress("DEPRECATION")
        val so = if (android.os.Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
        ThongTinApk(pi.packageName, pm.getApplicationLabel(ai).toString(), pi.versionName ?: "", so, ai.minSdkVersion)
    }.getOrNull()

    fun thuMuc(ctx: Context) = File(ctx.cacheDir, "cai-apk").apply { mkdirs() }

    /** Xoá mọi file đã chép (rời màn, kết nối tivi khác). */
    fun donDep(ctx: Context) { File(ctx.cacheDir, "cai-apk").deleteRecursively() }

    /** Lỗi này gỡ bản trên tivi rồi cài lại là qua: khác chữ ký hoặc file cũ hơn bản trên tivi (mất dữ liệu app đó). */
    fun canGoTruoc(out: String) = Regex("UPDATE_INCOMPATIBLE|INCONSISTENT_CERTIFICATES|VERSION_DOWNGRADE").containsMatchIn(out)

    /** Giải thích dòng `pm install` in ra ("Failure [INSTALL_FAILED_…: …]") cho người không rành ADB. */
    fun giaiThich(out: String, sdkTv: Int): String {
        val ma = Regex("""INSTALL_[A-Z_]+""").find(out)?.value ?: ""
        val tho = out.trim().lines().lastOrNull { it.isNotBlank() }?.trim() ?: ""
        return when {
            "UPDATE_INCOMPATIBLE" in ma || "INCONSISTENT_CERTIFICATES" in ma ->
                t("Tivi đã có app này nhưng ký khoá khác (bản lấy từ nguồn khác). Phải gỡ bản trên tivi rồi cài lại, dữ liệu của app đó sẽ mất.",
                    "The TV already has this app signed with another key (from another source). Remove it from the TV and install again; that app's data will be lost.")
            "VERSION_DOWNGRADE" in ma ->
                t("Tivi đang có bản mới hơn file này. Muốn dùng bản cũ thì phải gỡ bản trên tivi trước, dữ liệu của app đó sẽ mất.",
                    "The TV has a newer version than this file. To use the older one, remove it from the TV first; that app's data will be lost.")
            "OLDER_SDK" in ma -> t("App cần Android mới hơn tivi (tivi đang là API $sdkTv).", "The app needs a newer Android than the TV has (API $sdkTv).")
            "NO_MATCHING_ABIS" in ma -> t("App không có bản cho chip của tivi (ví dụ chỉ có bản 64-bit hoặc x86).", "The app has no build for the TV's chip (e.g. 64-bit or x86 only).")
            "INSUFFICIENT_STORAGE" in ma -> t("Tivi hết bộ nhớ. Gỡ bớt app hoặc xoá dữ liệu rồi thử lại.", "The TV is out of storage. Remove some apps or data and try again.")
            "MISSING_SPLIT" in ma -> t("App này chia thành nhiều phần mà file chỉ có một phần. Hãy tải bản .apks / .xapk / .apkm đầy đủ.", "This app comes in several parts and the file has only one. Get the full .apks / .xapk / .apkm.")
            "INVALID_APK" in ma || "PARSE_FAILED" in ma -> t("File APK hỏng hoặc chưa tải xong.", "The APK is damaged or not fully downloaded.")
            "TEST_ONLY" in ma -> t("Đây là bản thử của nhà phát triển (test-only), tivi không cho cài.", "This is a developer test-only build; the TV won't install it.")
            "DUPLICATE_PERMISSION" in ma || "CONFLICTING_PROVIDER" in ma -> t("App trùng quyền / thành phần với một app khác trên tivi: $tho", "The app clashes with another app on the TV: $tho")
            "USER_RESTRICTED" in ma || "VERIFICATION" in ma || "ABORTED" in ma ->
                t("Tivi chặn hoặc huỷ việc cài. Xem trên tivi có hộp hỏi (Play Protect / Xác minh ứng dụng) không, bấm Cho phép rồi thử lại.",
                    "The TV blocked or cancelled the install. Check the TV for a prompt (Play Protect / Verify apps), allow it and retry.")
            "TIMEOUT" in out || "không trả lời" in out ->
                t("Tivi không trả lời. Có thể tivi đang hiện hộp hỏi xác minh app: xem trên tivi, bấm Cho phép rồi thử lại.",
                    "The TV didn't answer. It may be showing an app-verification prompt: check the TV, allow it and retry.")
            else -> tho.ifEmpty { t("Cài không được (tivi không báo lý do).", "Install failed (the TV gave no reason).") }
        }
    }
}
