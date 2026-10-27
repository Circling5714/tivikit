package vn.tivikit.launcher

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Thời tiết hiện tại từ Open-Meteo (miễn phí, không cần khoá, CC BY 4.0) cho nơi TiviKit gửi (như dòng thời tiết của Home
 * sạch). Mã thời tiết WMO và chữ theo Weather.java của Home sạch (CleanYourTV, MIT).
 */
object ThoiTiet {
    data class Noi(val ten: String, val lat: Double, val lon: Double)
    data class HienTai(val nhietDo: Int, val ma: Int)

    enum class Loai { QUANG, IT_MAY, MAY, SUONG, MUA_PHUN, MUA, TUYET, DONG, KHAC }

    fun url(n: Noi): String = String.format(Locale.ROOT,
        "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code&timezone=auto", n.lat, n.lon)

    /** Đọc phần "current" của câu trả lời; null nếu sai dạng. */
    fun docJson(s: String): HienTai? = runCatching {
        val c = JSONObject(s).getJSONObject("current")
        HienTai(c.getDouble("temperature_2m").roundToInt(), c.getInt("weather_code"))
    }.getOrNull()

    fun loai(ma: Int): Loai = when {
        ma == 0 -> Loai.QUANG
        ma == 1 || ma == 2 -> Loai.IT_MAY
        ma == 3 -> Loai.MAY
        ma == 45 || ma == 48 -> Loai.SUONG
        ma in 51..57 -> Loai.MUA_PHUN
        ma in 71..77 || ma == 85 || ma == 86 -> Loai.TUYET
        ma >= 95 -> Loai.DONG
        ma in 61..67 || ma in 80..82 -> Loai.MUA
        else -> Loai.KHAC
    }

    /** "Nhiều mây" / "Overcast"; mã lạ thì rỗng. */
    fun chu(ma: Int, viet: Boolean): String = when {
        ma == 0 -> if (viet) "Trời quang" else "Clear"
        ma == 1 -> if (viet) "Ít mây" else "Mostly clear"
        ma == 2 -> if (viet) "Có mây" else "Partly cloudy"
        ma == 3 -> if (viet) "Nhiều mây" else "Overcast"
        ma == 45 || ma == 48 -> if (viet) "Sương mù" else "Fog"
        ma in 51..57 -> if (viet) "Mưa phùn" else "Drizzle"
        ma == 65 || ma == 67 -> if (viet) "Mưa to" else "Heavy rain"
        ma in 61..67 -> if (viet) "Mưa" else "Rain"
        ma in 71..77 || ma == 85 || ma == 86 -> if (viet) "Tuyết" else "Snow"
        ma in 80..82 -> if (viet) "Mưa rào" else "Showers"
        ma >= 95 -> if (viet) "Dông" else "Thunderstorm"
        else -> ""
    }

    /** "26°C Nhiều mây". */
    fun dong(h: HienTai, viet: Boolean): String = ("${h.nhietDo}°C " + chu(h.ma, viet)).trim()

    /** Hỏi Open-Meteo (chạy ngoài luồng giao diện); null nếu mạng lỗi. */
    fun tai(n: Noi): HienTai? {
        var c: HttpURLConnection? = null
        return try {
            c = URL(url(n)).openConnection() as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 8000
            if (c.responseCode != 200) return null
            val out = ByteArrayOutputStream()
            c.inputStream.use { it.copyTo(out) }
            docJson(out.toString("UTF-8"))
        } catch (_: Exception) {
            null
        } finally {
            c?.disconnect()
        }
    }
}
