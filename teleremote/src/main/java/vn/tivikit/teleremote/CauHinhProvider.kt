package vn.tivikit.teleremote

import android.content.ContentProvider
import android.content.ContentValues
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import java.io.IOException
import org.json.JSONArray
import org.json.JSONObject

/**
 * TiviKit đặt / đọc qua ADB:
 *   content call --uri content://vn.tivikit.teleremote --method token --arg '{"token":"123456:ABC…"}'  ("" = bỏ bot)
 *   content call --uri content://vn.tivikit.teleremote --method dat --arg '{"bat":true,"nguoiDung":[{"ten":"@abc","vaiTro":"chu"}],…}'
 *   content call --uri content://vn.tivikit.teleremote --method doc
 *   content call --uri content://vn.tivikit.teleremote --method nhan-thu   (bot nhắn chủ tivi một tin thử)
 *   content call --uri content://vn.tivikit.teleremote --method xoa-cho --arg '{"id":123}'   (bỏ một người xin dùng)
 *   content call --uri content://vn.tivikit.teleremote --method noi-lai   (nối lại ADB của tivi)
 *   content call --uri content://vn.tivikit.teleremote --method thu --arg '{"chu":"ℹ️ Tivi"}' / '{"nut":"k:19"}'
 *     (làm như chủ tivi vừa nhắn / bấm, "vaiTro":"thanh-vien" thì như thành viên; trả chữ bot sẽ trả lời kèm chữ trên nút;
 *     để thử không cần Telegram)
 * Chỉ nhận lời gọi từ shell (ADB) hoặc root: app khác trên tivi không đọc được token hay đổi người được dùng.
 */
class CauHinhProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val uid = Binder.getCallingUid()
        if (uid != Process.SHELL_UID && uid != 0 && uid != Process.myUid()) throw SecurityException("chỉ ADB")
        val ctx = context!!
        val kho = Kho(ctx)
        val dv = TeleRemoteDichVu.dangChay
        val kq = Bundle()
        when (method) {
            "dat" -> {
                val moi = CauHinh.tuJson(arg) ?: return kq.apply { putString("loi", "cau hinh sai") }
                kho.cauHinh = ghep(kho.cauHinh, moi)
                // Người vừa được duyệt (đã có ID trong danh sách) thì thôi chờ.
                kho.cho.filter { x -> kho.cauHinh.nguoiDung.any { it.id == x.id } }.forEach { kho.xoaCho(it.id) }
                dv?.lamLai()
                kq.putString("ok", "1")
            }
            "token" -> {
                val token = runCatching { JSONObject(arg ?: "").optString("token") }.getOrDefault("").trim()
                if (token.isNotEmpty() && !Telegram.TOKEN.matches(token)) return kq.apply { putString("loi", "token-sai-dang") }
                kho.token = token
                kho.bot = ""
                if (token.isNotEmpty()) {
                    try {
                        Telegram(token).toi().optString("username").let { kho.bot = it; kq.putString("bot", it) }
                    } catch (e: Telegram.Loi) {
                        if (e.ma == 401 || e.ma == 404) { kho.token = ""; kq.putString("loi", "sai-token") } else kq.putString("loi", "loi-${e.ma}")
                    } catch (_: IOException) {
                        // Giữ token: bot tự thử lại khi có mạng / khi tivi vào được Telegram.
                        kq.putString("loi", "khong-mang")
                    }
                }
                dv?.lamLai()
                if (!kq.containsKey("loi") || kq.getString("loi") == "khong-mang") kq.putString("ok", "1")
            }
            "doc" -> {
                val ch = kho.cauHinh
                val pm = ctx.packageManager
                val j = JSONObject()
                    .put("phienBan", runCatching { pm.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull().orEmpty())
                    .put("dichVuChay", dv != null)
                    .put("adb", dv?.trangThaiAdb?.ma ?: GiuAdb.TrangThai.TAT.ma)
                    .put("tele", dv?.trangThaiBot?.ma ?: Bot.TrangThai.TAT.ma)
                    .put("lanCuoi", dv?.lanCuoi ?: 0L)
                    .put("coToken", kho.token.isNotEmpty())
                    .put("bot", kho.bot)
                    .put("cauHinh", ch.json())
                    .put("cho", JSONArray().apply { kho.cho.forEach { put(JSONObject().put("id", it.id).put("ten", it.ten).put("tenHien", it.tenHien).put("luc", it.luc)) } })
                    .put("nhatKy", JSONArray().apply { kho.nhatKy.forEach { put(JSONObject().put("luc", it.luc).put("ai", it.ai).put("viec", it.viec)) } })
                kq.putString("json", j.toString())
            }
            "nhan-thu" -> {
                if (kho.token.isEmpty()) return kq.apply { putString("loi", "chua-co-token") }
                val chu = kho.cauHinh.nguoiDung.filter { it.vaiTro == VaiTro.CHU && it.id != 0L }
                if (chu.isEmpty()) return kq.apply { putString("loi", "chua-co-chu") }
                val en = kho.cauHinh.tiengAnh
                val ten = dv?.tenTivi() ?: android.os.Build.MODEL
                val t = Telegram(kho.token)
                var so = 0
                try {
                    chu.forEach {
                        t.gui(it.id, if (en) "✅ Tele Remote on $ten is listening. Send /start for the menu."
                            else "✅ Tele Remote trên $ten đang nghe lệnh. Gõ /start để mở menu.")
                        so++
                    }
                    kq.putString("ok", "1"); kq.putString("so", so.toString())
                } catch (e: Telegram.Loi) {
                    kq.putString("loi", if (e.ma == 401 || e.ma == 404) "sai-token" else "loi-${e.ma}")
                } catch (_: IOException) {
                    kq.putString("loi", "khong-mang")
                }
            }
            "xoa-cho" -> {
                val id = runCatching { JSONObject(arg ?: "").optLong("id") }.getOrDefault(0L)
                kho.xoaCho(id)
                kq.putString("ok", "1")
            }
            "noi-lai" -> {
                if (dv == null) return kq.apply { putString("loi", "dich-vu-tat") }
                dv.noiLai()
                kq.putString("ok", "1")
            }
            "thu" -> {
                if (dv == null) return kq.apply { putString("loi", "dich-vu-tat") }
                val j = runCatching { JSONObject(arg ?: "") }.getOrDefault(JSONObject())
                val vt = if (j.optString("vaiTro") == VaiTro.THANH_VIEN.ma) VaiTro.THANH_VIEN else VaiTro.CHU
                val nd = kho.cauHinh.nguoiDung.firstOrNull { it.vaiTro == vt } ?: NguoiDung("@thu", 1, vt, "TiviKit")
                val ra = GhiRa()
                val d = XuLyLenh.Den(0, XuLyLenh.Nguoi(nd.id, nd.ten, nd.tenHien), nd.id, true, System.currentTimeMillis() / 1000,
                    chu = j.optString("chu", "").takeIf { j.has("chu") }, nut = j.optString("nut", "").takeIf { j.has("nut") })
                LamViec(dv, ra).lam(d, nd)
                kq.putString("ok", "1"); kq.putString("ra", ra.ds.joinToString(" | "))
            }
            else -> kq.putString("loi", "khong biet $method")
        }
        return kq
    }

    /**
     * Danh sách TiviKit gửi, giữ ID / tên hiện tivi đã ghi cho cùng người: TiviKit đọc trạng thái trước khi người đó nhắn
     * bot lần đầu thì gửi lại mục chưa có ID, không được làm mất ID.
     */
    private fun ghep(cu: CauHinh, moi: CauHinh): CauHinh = moi.copy(nguoiDung = moi.nguoiDung.map { m ->
        if (m.id != 0L) m
        else cu.nguoiDung.firstOrNull { it.id != 0L && it.ten.equals(m.ten, ignoreCase = true) }
            ?.let { m.copy(id = it.id, tenHien = m.tenHien.ifEmpty { it.tenHien }) } ?: m
    })

    override fun query(u: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?) = null
    override fun getType(u: Uri): String? = null
    override fun insert(u: Uri, v: ContentValues?): Uri? = null
    override fun delete(u: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(u: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
}
