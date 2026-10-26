package vn.tivikit.dulieu

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Một app trong bộ cài: [file] là tên file đã chép vào thư mục của bộ (.apk / .apks / .xapk / .apkm); hoặc [goiY] là id
 * một app trong GoiY.DS — khi áp dụng mới tải bản mới nhất đúng chip của tivi (file = "").
 */
data class MucBoCai(val file: String, val ten: String, val pkg: String, val versionName: String, val versionCode: Long, val co: Long, val goiY: String = "") {
    fun json(): JSONObject = JSONObject().put("file", file).put("ten", ten).put("pkg", pkg)
        .put("versionName", versionName).put("versionCode", versionCode).put("co", co).put("goiY", goiY)

    companion object {
        fun tuJson(o: JSONObject) = MucBoCai(o.optString("file"), o.optString("ten"), o.optString("pkg"),
            o.optString("versionName"), o.optLong("versionCode"), o.optLong("co"), o.optString("goiY"))

        fun tuGoiY(g: GoiY.App) = MucBoCai("", g.ten, "", "", 0, 0, g.id)
    }
}

/**
 * Bộ cài đặt mẫu (25/10/2026): danh sách app + DNS riêng (AdGuard / NextDNS…), áp dụng một lần cho tivi đang nối hoặc lần lượt cho
 * nhiều tivi. Lưu ở filesDir/bo-cai: ds.json + mỗi bộ một thư mục chứa bản chép của các APK.
 */
data class BoCai(
    val id: String, val ten: String, val apps: List<MucBoCai>,
    /** DNS riêng đặt cho tivi; [dnsGiaTri] = ID NextDNS hoặc tên máy chủ tự nhập. */
    val dnsLoai: DnsRieng.Loai = DnsRieng.Loai.TAT, val dnsGiaTri: String = "",
) {
    fun json(): JSONObject = JSONObject().put("id", id).put("ten", ten).put("dnsLoai", dnsLoai.name).put("dnsGiaTri", dnsGiaTri)
        .put("apps", JSONArray(apps.map { it.json() }))

    companion object {
        fun tuJson(o: JSONObject) = BoCai(o.getString("id"), o.optString("ten"),
            (o.optJSONArray("apps") ?: JSONArray()).let { a -> (0 until a.length()).map { MucBoCai.tuJson(a.getJSONObject(it)) } },
            // Bộ lưu trước khi có NextDNS chỉ có cờ "dns" = DNS AdGuard.
            runCatching { DnsRieng.Loai.valueOf(o.getString("dnsLoai")) }.getOrElse { if (o.optBoolean("dns")) DnsRieng.Loai.ADGUARD else DnsRieng.Loai.TAT },
            o.optString("dnsGiaTri"))
    }
}

class KhoBoCai(goc: File) {
    val thuMuc = File(goc, "bo-cai").apply { mkdirs() }
    private val fileDs = File(thuMuc, "ds.json")

    fun thuMucBo(id: String) = File(thuMuc, id).apply { mkdirs() }

    fun tatCa(): List<BoCai> = runCatching {
        val a = JSONArray(fileDs.readText())
        (0 until a.length()).map { BoCai.tuJson(a.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    /** Lưu (thêm hoặc thay bộ cùng id), rồi xoá file trong thư mục bộ không còn app nào dùng. */
    fun luu(b: BoCai) {
        val ds = tatCa().filter { it.id != b.id } + b
        ghi(ds)
        val dung = b.apps.map { it.file }.toSet()
        thuMucBo(b.id).listFiles()?.filter { it.name !in dung }?.forEach { it.delete() }
    }

    fun xoa(id: String) {
        ghi(tatCa().filter { it.id != id })
        File(thuMuc, id).deleteRecursively()
    }

    /** Bỏ phần đang soạn: xoá file đã chép mà bản đã lưu không dùng (bộ chưa từng lưu thì xoá cả thư mục). */
    fun donFileThua(id: String) {
        val daLuu = tatCa().firstOrNull { it.id == id } ?: return run { File(thuMuc, id).deleteRecursively() }
        val dung = daLuu.apps.map { it.file }.toSet()
        File(thuMuc, id).listFiles()?.filter { it.name !in dung }?.forEach { it.delete() }
    }

    private fun ghi(ds: List<BoCai>) {
        val tam = File(thuMuc, "ds.json.tam")
        tam.writeText(JSONArray(ds.map { it.json() }).toString())
        // Android đổi tên đè được file cũ; máy tính chạy test (Windows) thì không, phải xoá trước.
        if (!tam.renameTo(fileDs)) { fileDs.delete(); tam.renameTo(fileDs) }
    }
}
