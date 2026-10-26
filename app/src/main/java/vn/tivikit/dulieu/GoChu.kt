package vn.tivikit.dulieu

import org.json.JSONObject
import vn.tivikit.adb.PhimTv
import java.text.Normalizer
import java.util.Base64

/**
 * Gõ tiếng Việt có dấu lên tivi (25/10/2026): lệnh `input text` chỉ gõ được chữ không dấu, nên chữ có dấu (hay emoji…)
 * đi qua app phụ vn.tivikit.go trên tivi (module go/): một bộ gõ không hiện bàn phím, bật tạm vài giây để đưa chữ vào
 * ô nhập đang chọn rồi trả bàn phím cũ của tivi.
 */
object GoChu {
    const val GOI = "vn.tivikit.go"
    const val BO_GO = "$GOI/.BoGo"
    private val TEN_BO_GO = Regex("^[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+$")

    /** Chữ chuẩn dựng sẵn (NFC): bàn phím điện thoại có loại gửi dấu tách rời, app trên tivi tìm không ra. */
    fun chuan(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFC)

    /** Có ký tự `input text` không gõ được (chữ có dấu, đ, emoji…): phải đi qua bộ gõ. */
    fun canBoGo(s: String): Boolean = PhimTv.boDau(s) != s

    /** Bộ gõ đang dùng trên tivi đọc được là tên hợp lệ, không phải bộ gõ của TiviKit (lần trước bị ngắt giữa chừng). */
    fun laBoGoCu(s: String): Boolean = TEN_BO_GO.matches(s) && !s.startsWith("$GOI/")

    /**
     * Một lệnh shell: bật bộ gõ, gửi [chu] (base64 của JSON, qua shell nguyên vẹn), bộ gõ đưa chữ vào rồi tự trả bàn phím
     * [cu]; chưa trả được thì lệnh trả, rồi tắt bộ gõ khỏi danh sách bàn phím của tivi. Kết quả: dòng "ok=1" hoặc
     * "loi=<mã>" của provider.
     */
    fun lenh(chu: String, cu: String): String {
        val coCu = laBoGoCu(cu)
        val goi = Base64.getEncoder().encodeToString(JSONObject().put("chu", chuan(chu)).put("cu", if (coCu) cu else "").toString().toByteArray())
        val tra = if (coCu) "[ \"\$(settings get secure default_input_method)\" = '$cu' ] || ime set '$cu' >/dev/null 2>&1; " else ""
        return "ime enable $BO_GO >/dev/null 2>&1; ime set $BO_GO >/dev/null 2>&1; " +
            "content call --uri content://$GOI --method chu --arg '$goi'; " + tra + "ime disable $BO_GO >/dev/null 2>&1; true"
    }

    /** Kết quả lệnh: "ok", mã lỗi của bộ gõ ("khong-o-nhap"…), hoặc "khong-tra-loi". */
    fun ketQua(out: String): String {
        if (out.contains("ok=1")) return "ok"
        return Regex("loi=([a-z-]+)").find(out)?.groupValues?.get(1) ?: "khong-tra-loi"
    }
}
