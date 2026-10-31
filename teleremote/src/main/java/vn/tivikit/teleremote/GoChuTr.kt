package vn.tivikit.teleremote

import java.text.Normalizer
import java.util.Base64
import org.json.JSONObject

/**
 * Gõ chữ từ Telegram vào ô nhập đang chọn trên tivi (31/10/2026), cùng cách với nút Gõ chữ của TiviKit (GoChu, PhimTv):
 * chữ thuần ASCII gõ bằng `input text`; chữ có dấu (hay emoji) đi qua bộ gõ vn.tivikit.go (TiviKit cài cùng Tele Remote),
 * bật tạm vài giây để đưa chữ vào rồi trả bàn phím cũ của tivi.
 */
object GoChuTr {
    const val GOI = "vn.tivikit.go"
    const val BO_GO = "$GOI/.BoGo"
    private val TEN_BO_GO = Regex("^[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+$")

    /** Có ký tự `input text` không gõ được (chữ có dấu, đ, emoji…): phải đi qua bộ gõ. */
    fun canBoGo(s: String): Boolean = s.any { it !in ' '..'~' }

    /** Bộ gõ đang dùng trên tivi đọc được là tên hợp lệ, không phải bộ gõ của TiviKit (lần trước bị ngắt giữa chừng). */
    fun laBoGoCu(s: String): Boolean = TEN_BO_GO.matches(s) && !s.startsWith("$GOI/")

    /**
     * Lệnh `input text` gõ đúng [chu] (chỉ ASCII): khoảng trắng viết %s, cả chuỗi bọc nháy đơn cho shell, nháy đơn bên trong
     * viết '\''. Chuỗi dài chia đoạn 200 ký tự (một số tivi bỏ sót khi quá dài).
     */
    fun lenhAscii(chu: String): String = chu.filter { it in ' '..'~' }.chunked(200)
        .joinToString("; ") { doan -> "input text '" + doan.replace("'", "'\\''").replace(" ", "%s") + "'" }

    /**
     * Một lệnh shell qua bộ gõ: bật bộ gõ, gửi [chu] (base64 của JSON, thuần ASCII qua ADB), bộ gõ đưa chữ vào rồi tự trả
     * bàn phím [cu]; chưa trả được thì lệnh trả, rồi tắt bộ gõ khỏi danh sách bàn phím. Kết quả: "ok=1" hay "loi=<mã>".
     */
    fun lenhBoGo(chu: String, cu: String): String {
        val coCu = laBoGoCu(cu)
        val goi = Base64.getEncoder().encodeToString(JSONObject().put("chu", Normalizer.normalize(chu, Normalizer.Form.NFC))
            .put("cu", if (coCu) cu else "").toString().toByteArray())
        val tra = if (coCu) "[ \"\$(settings get secure default_input_method)\" = '$cu' ] || ime set '$cu' >/dev/null 2>&1; " else ""
        return "ime enable $BO_GO >/dev/null 2>&1; ime set $BO_GO >/dev/null 2>&1; " +
            "content call --uri content://$GOI --method chu --arg '$goi'; " + tra + "ime disable $BO_GO >/dev/null 2>&1; true"
    }

    /** Kết quả lệnh bộ gõ: "ok", mã lỗi của bộ gõ ("khong-o-nhap"…), hay "khong-tra-loi". */
    fun ketQua(out: String): String {
        if (out.contains("ok=1")) return "ok"
        return Regex("loi=([a-z-]+)").find(out)?.groupValues?.get(1) ?: "khong-tra-loi"
    }
}
