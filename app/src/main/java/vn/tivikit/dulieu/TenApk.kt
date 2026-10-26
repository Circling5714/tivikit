package vn.tivikit.dulieu

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import java.io.File
import java.util.zip.Inflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Tên thật và ảnh (banner / icon) của app trên tivi mà không tải cả APK (25/10/2026, Quản lý app). Tivi không có lệnh
 * nào in ra tên app: đọc mục lục zip ở cuối file, lấy AndroidManifest.xml + resources.arsc (+ file ảnh), ghép thành một
 * APK tí hon trên điện thoại rồi để PackageManager của điện thoại đọc.
 */
object TenApk {
    /** Đọc [co] byte từ vị trí [tu] của file (ít hơn nếu chạm cuối file). */
    fun interface DocDoan { fun doc(tu: Long, co: Int): ByteArray }

    class Muc(val ten: String, val cachNen: Int, val coNen: Long, val coGoc: Long, val viTri: Long, val doDaiTen: Int, val doDaiThem: Int)

    class KetQua(val ten: String?, val anh: Bitmap?)

    private fun u16(b: ByteArray, i: Int) = (b[i].toInt() and 0xff) or ((b[i + 1].toInt() and 0xff) shl 8)
    private fun u32(b: ByteArray, i: Int) = u16(b, i).toLong() or (u16(b, i + 2).toLong() shl 16)

    /** Mục lục (central directory) của zip cỡ [coFile]: tìm EOCD trong 64 KB cuối. Zip64 (APK trên 4 GB) không hỗ trợ. */
    fun mucLuc(coFile: Long, doc: DocDoan): Map<String, Muc> {
        val coDuoi = minOf(coFile, 22L + 65535).toInt()
        val tuDuoi = coFile - coDuoi
        val duoi = doc.doc(tuDuoi, coDuoi)
        var i = duoi.size - 22
        while (i >= 0 && u32(duoi, i) != 0x06054b50L) i--
        require(i >= 0) { "Không thấy mục lục zip" }
        val coMl = u32(duoi, i + 12)
        val viTriMl = u32(duoi, i + 16)
        require(viTriMl != 0xFFFFFFFFL && coMl != 0xFFFFFFFFL) { "Zip64" }
        val ml = if (viTriMl >= tuDuoi) duoi.copyOfRange((viTriMl - tuDuoi).toInt(), (viTriMl - tuDuoi + coMl).toInt())
            else doc.doc(viTriMl, coMl.toInt())
        val kq = LinkedHashMap<String, Muc>()
        var p = 0
        while (p + 46 <= ml.size && u32(ml, p) == 0x02014b50L) {
            val n = u16(ml, p + 28); val m = u16(ml, p + 30); val k = u16(ml, p + 32)
            val ten = String(ml, p + 46, n, Charsets.UTF_8)
            kq[ten] = Muc(ten, u16(ml, p + 10), u32(ml, p + 20), u32(ml, p + 24), u32(ml, p + 42), n, m)
            p += 46 + n + m + k
        }
        return kq
    }

    /** Nội dung đã giải nén của một mục. Phần "extra" ở local header có thể dài hơn ở mục lục (zipalign đệm thêm). */
    fun duLieu(m: Muc, doc: DocDoan): ByteArray {
        var b = doc.doc(m.viTri, (30 + m.doDaiTen + m.doDaiThem + 4096 + m.coNen).toInt())
        require(b.size >= 30 && u32(b, 0) == 0x04034b50L) { "Sai local header ${m.ten}" }
        val batDau = 30 + u16(b, 26) + u16(b, 28)
        if (batDau + m.coNen > b.size) b = doc.doc(m.viTri, (batDau + m.coNen).toInt())
        val nen = b.copyOfRange(batDau, (batDau + m.coNen).toInt())
        if (m.cachNen == 0) return nen
        require(m.cachNen == 8) { "Kiểu nén ${m.cachNen} (${m.ten})" }
        val inf = Inflater(true)
        try {
            inf.setInput(nen + byteArrayOf(0)) // nowrap cần thêm một byte giả ở cuối (tài liệu Inflater)
            val ra = ByteArray(m.coGoc.toInt())
            var d = 0
            while (d < ra.size && !inf.finished()) {
                val k = inf.inflate(ra, d, ra.size - d)
                if (k == 0 && (inf.needsInput() || inf.needsDictionary())) break
                d += k
            }
            require(d == ra.size) { "Giải nén thiếu ${m.ten}" }
            return ra
        } finally {
            inf.end()
        }
    }

    /**
     * Tên + ảnh từ APK chính cỡ [coFile] trên tivi ([doc] đọc đoạn file đó). Ảnh lấy banner (Android TV) trước, không có
     * thì icon. Icon thích ứng / vector là XML trỏ tới file khác: Resources báo đúng tên file thiếu trong lỗi
     * ("File res/... from drawable resource ID") → đọc thêm file đó rồi thử lại, tối đa 6 lần.
     */
    fun doc(ctx: Context, coFile: Long, doc: DocDoan): KetQua {
        val ml = mucLuc(coFile, doc)
        val co = LinkedHashMap<String, ByteArray>()
        for (ten in listOf("AndroidManifest.xml", "resources.arsc")) co[ten] = duLieu(ml[ten] ?: error("Thiếu $ten"), doc)
        val pm = ctx.packageManager
        val thuMuc = File(ctx.cacheDir, "ten-apk").apply { mkdirs() }
        var tenApp: String? = null
        var lan = 0
        while (true) {
            // Tên file mới mỗi lần: PackageManager giữ Resources theo đường dẫn.
            val f = File(thuMuc, "${System.nanoTime()}.apk")
            try {
                ZipOutputStream(f.outputStream()).use { z -> co.forEach { (k, v) -> z.putNextEntry(ZipEntry(k)); z.write(v); z.closeEntry() } }
                @Suppress("DEPRECATION")
                val ai = pm.getPackageArchiveInfo(f.path, 0)?.applicationInfo ?: return KetQua(tenApp, null)
                ai.sourceDir = f.path
                ai.publicSourceDir = f.path
                if (tenApp == null) tenApp = runCatching { pm.getApplicationLabel(ai).toString() }.getOrNull()
                val id = ai.banner.takeIf { it != 0 } ?: ai.icon
                if (id == 0) return KetQua(tenApp, null)
                val res = pm.getResourcesForApplication(ai)
                val loi = try {
                    @Suppress("DEPRECATION")
                    return KetQua(tenApp, veAnh(res.getDrawable(id, null)))
                } catch (e: Exception) { e }
                val thieu = generateSequence(loi as Throwable?) { it.cause }
                    .mapNotNull { it.message }
                    .flatMap { msg -> Regex("""File (res/\S+) from""").findAll(msg).map { it.groupValues[1] } }
                    .firstOrNull { it !in co && it in ml }
                if (thieu == null || ++lan > 6) return KetQua(tenApp, null)
                co[thieu] = duLieu(ml.getValue(thieu), doc)
            } finally {
                f.delete()
            }
        }
    }

    /** Vẽ ảnh ra bitmap, giữ tỉ lệ, cạnh dài nhất 320 px. */
    private fun veAnh(d: Drawable): Bitmap {
        val w0 = d.intrinsicWidth.takeIf { it > 0 } ?: 192
        val h0 = d.intrinsicHeight.takeIf { it > 0 } ?: 192
        val ti = minOf(1f, 320f / maxOf(w0, h0))
        val w = (w0 * ti).toInt().coerceAtLeast(1); val h = (h0 * ti).toInt().coerceAtLeast(1)
        val bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, w, h)
        d.draw(Canvas(bm))
        return bm
    }
}
