package vn.tivikit.dulieu

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Sao lưu dữ liệu TiviKit ra một file zip, để giữ lại hoặc mang sang điện thoại khác (25/10/2026, sau lần đổi tên gói
 * làm mất dữ liệu trên điện thoại): bản sao lưu từng tivi (filesDir/sao-luu), bộ cài đặt mẫu kèm file APK
 * (filesDir/bo-cai), tivi gần đây; tuỳ chọn kèm khoá kết nối ADB (filesDir/adb) để tivi đã cho phép không hỏi lại.
 * Nhập là gộp: bản sao lưu cùng tivi giữ ảnh "trước khi dọn" của bản cũ hơn và cộng các gói đã tắt; bộ cài trùng id giữ
 * bộ đang có.
 */
object SaoLuuApp {
    const val LOAI = "tivikit-sao-luu"
    private val KHOA_ADB = listOf("adb/khoa.der", "adb/cert.der")
    /** Tên file / thư mục được phép (không "..", không "/"). */
    private val TEN_AN_TOAN = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,119}$")

    /** Có gì trong một bản sao lưu. */
    data class NoiDung(val soSaoLuu: Int, val soBoCai: Int, val coKhoa: Boolean, val luc: Long)

    data class KetQuaNhap(val saoLuuMoi: Int, val saoLuuGop: Int, val boCaiMoi: Int, val boCaiTrung: Int, val caiDat: JSONObject?, val daNhapKhoa: Boolean)

    /** Ghi zip vào [ra] từ thư mục dữ liệu [goc] (filesDir) cùng [caiDat] (tivi gần đây…). */
    fun dongGoi(goc: File, caiDat: JSONObject, kemKhoa: Boolean, phienBanApp: String, ra: OutputStream): NoiDung {
        val saoLuu = File(goc, "sao-luu").listFiles { f -> f.isFile && f.name.endsWith(".json") && TEN_AN_TOAN.matches(f.name) }.orEmpty().sortedBy { it.name }
        val thuMucBo = File(goc, "bo-cai")
        val ds = runCatching { JSONArray(File(thuMucBo, "ds.json").readText()) }.getOrNull()
        val coKhoa = kemKhoa && KHOA_ADB.all { File(goc, it).isFile }
        val luc = System.currentTimeMillis()
        ZipOutputStream(ra).use { z ->
            fun chu(ten: String, s: String) { z.putNextEntry(ZipEntry(ten)); z.write(s.toByteArray()); z.closeEntry() }
            fun tep(ten: String, f: File) { z.putNextEntry(ZipEntry(ten)); f.inputStream().use { it.copyTo(z) }; z.closeEntry() }
            chu("manifest.json", JSONObject().put("loai", LOAI).put("luc", luc).put("app", phienBanApp)
                .put("soSaoLuu", saoLuu.size).put("soBoCai", ds?.length() ?: 0).toString(1))
            chu("cai-dat.json", caiDat.toString(1))
            saoLuu.forEach { tep("sao-luu/${it.name}", it) }
            if (ds != null) {
                chu("bo-cai/ds.json", ds.toString())
                for (i in 0 until ds.length()) {
                    val id = ds.getJSONObject(i).optString("id").takeIf { TEN_AN_TOAN.matches(it) } ?: continue
                    File(thuMucBo, id).listFiles { f -> f.isFile && TEN_AN_TOAN.matches(f.name) }.orEmpty().forEach { tep("bo-cai/$id/${it.name}", it) }
                }
            }
            if (coKhoa) KHOA_ADB.forEach { tep(it, File(goc, it)) }
        }
        return NoiDung(saoLuu.size, ds?.length() ?: 0, coKhoa, luc)
    }

    /** Mục lục của file [f], chưa ghi gì; không phải bản sao lưu TiviKit thì lỗi. */
    fun xem(f: File): NoiDung = ZipFile(f).use { z ->
        val m = z.getEntry("manifest.json")?.let { JSONObject(z.getInputStream(it).readBytes().toString(Charsets.UTF_8)) }
        require(m != null && m.optString("loai") == LOAI) { "khong phai ban sao luu TiviKit" }
        NoiDung(m.optInt("soSaoLuu"), m.optInt("soBoCai"), KHOA_ADB.all { z.getEntry(it) != null }, m.optLong("luc"))
    }

    /** Nhập file [f] vào [goc]; [dungKhoa] = thay khoá kết nối bằng khoá trong file (nếu có). */
    fun nhap(f: File, goc: File, dungKhoa: Boolean): KetQuaNhap {
        xem(f)
        ZipFile(f).use { z ->
            fun doc(ten: String) = z.getEntry(ten)?.let { z.getInputStream(it).readBytes() }

            // Bản sao lưu từng tivi: chưa có thì thêm, có rồi thì gộp.
            val thuMucSl = File(goc, "sao-luu").apply { mkdirs() }
            var slMoi = 0; var slGop = 0
            z.entries().asSequence().filter { !it.isDirectory && it.name.startsWith("sao-luu/") }.forEach { e ->
                val b = runCatching { BanSaoLuu.tuJson(JSONObject(z.getInputStream(e).readBytes().toString(Charsets.UTF_8))) }.getOrNull() ?: return@forEach
                if (!TEN_AN_TOAN.matches("${b.khoa}.json")) return@forEach
                val dich = File(thuMucSl, "${b.khoa}.json")
                val coSan = runCatching { BanSaoLuu.tuJson(JSONObject(dich.readText())) }.getOrNull()
                ghiAnToan(dich, (if (coSan == null) b else gop(coSan, b)).toJson().toString(1))
                if (coSan == null) slMoi++ else slGop++
            }

            // Bộ cài đặt mẫu: thêm bộ chưa có (kèm file APK), bộ trùng id giữ bộ đang có.
            val kho = KhoBoCai(goc)
            val coSanBo = kho.tatCa().map { it.id }.toSet()
            var boMoi = 0; var boTrung = 0
            val ds = doc("bo-cai/ds.json")?.let { runCatching { JSONArray(it.toString(Charsets.UTF_8)) }.getOrNull() } ?: JSONArray()
            for (i in 0 until ds.length()) {
                val o = ds.optJSONObject(i) ?: continue
                val id = o.optString("id").takeIf { TEN_AN_TOAN.matches(it) } ?: continue
                if (id in coSanBo) { boTrung++; continue }
                val bo = runCatching { BoCai.tuJson(o) }.getOrNull() ?: continue
                val thuMuc = kho.thuMucBo(id)
                z.entries().asSequence().filter { !it.isDirectory && it.name.startsWith("bo-cai/$id/") }.forEach { e ->
                    val ten = e.name.removePrefix("bo-cai/$id/")
                    if (TEN_AN_TOAN.matches(ten)) File(thuMuc, ten).outputStream().use { ra -> z.getInputStream(e).use { it.copyTo(ra) } }
                }
                kho.luu(bo); boMoi++
            }

            val caiDat = doc("cai-dat.json")?.let { runCatching { JSONObject(it.toString(Charsets.UTF_8)) }.getOrNull() }
            val khoa = KHOA_ADB.map { doc(it) }
            val nhapKhoa = dungKhoa && khoa.all { it != null && it.isNotEmpty() }
            if (nhapKhoa) KHOA_ADB.forEachIndexed { i, ten -> File(goc, ten).apply { parentFile?.mkdirs() }.writeBytes(khoa[i]!!) }
            return KetQuaNhap(slMoi, slGop, boMoi, boTrung, caiDat, nhapKhoa)
        }
    }

    /**
     * Gộp hai bản sao lưu của cùng một tivi: ảnh "trước khi dọn" lấy từ bản cũ hơn (chụp trước khi app đụng vào), cộng
     * các gói đã tắt và việc đã làm của cả hai, IP lấy bản mới hơn.
     */
    fun gop(a: BanSaoLuu, b: BanSaoLuu): BanSaoLuu {
        val (cu, moi) = if (a.luc <= b.luc) a to b else b to a
        return cu.copy(ip = moi.ip.ifBlank { cu.ip }, daTatBoiApp = (cu.daTatBoiApp + moi.daTatBoiApp).toCollection(linkedSetOf()),
            launcherDaCai = cu.launcherDaCai || moi.launcherDaCai, homeGocDaTat = cu.homeGocDaTat.ifBlank { moi.homeGocDaTat },
            dnsDaDoi = cu.dnsDaDoi || moi.dnsDaDoi)
    }

    private fun ghiAnToan(dich: File, s: String) {
        val tam = File(dich.parentFile, dich.name + ".tam")
        tam.writeText(s)
        if (!tam.renameTo(dich)) { dich.delete(); tam.renameTo(dich) }
    }
}
