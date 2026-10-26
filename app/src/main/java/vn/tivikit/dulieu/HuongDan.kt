package vn.tivikit.dulieu

/**
 * Hướng dẫn bật chế độ nhà phát triển + gỡ lỗi trên tivi. Ảnh chụp thật từ Android TV 14 (máy ảo, tiếng Việt và
 * English) ở assets/huongdan/{vi,en}/<anh>.webp. Chữ trong ngoặc kép là đúng tên mục trên tivi.
 */
data class BuocHuongDan(val anh: String, val tieuDeVi: String, val tieuDeEn: String, val chuVi: String, val chuEn: String)

val HUONG_DAN = listOf(
    BuocHuongDan(
        "1-caidat",
        "Mở Cài đặt của tivi", "Open the TV's Settings",
        "Bấm nút bánh răng trên remote, hoặc chọn biểu tượng Cài đặt ở góc trên bên phải màn Home. Chọn \"Lựa chọn ưu tiên về thiết bị\". Google TV: chọn \"Hệ thống\".",
        "Press the gear button on the remote, or pick the Settings icon at the top right of Home. Choose \"Device Preferences\". On Google TV: choose \"System\".",
    ),
    BuocHuongDan(
        "2-tuychon",
        "Vào Giới thiệu", "Open About",
        "Chọn \"Giới thiệu\" (có hãng ghi \"Thông tin\" hoặc \"About\").",
        "Choose \"About\" (some brands call it \"Information\").",
    ),
    BuocHuongDan(
        "3-bandung",
        "Bấm OK vào Bản dựng 7 lần", "Press OK on Build 7 times",
        "Kéo xuống cuối, đứng ở \"Bản dựng hệ điều hành Android TV\" (hoặc \"Bản dựng\") rồi bấm OK liên tục 7 lần, tới khi hiện \"Bạn đã là nhà phát triển!\". Tivi hỏi mã PIN thì nhập mã của tivi.",
        "Scroll to the bottom, stay on \"Android TV OS build\" (or \"Build\") and press OK 7 times until \"You are now a developer!\" appears. Enter the TV's PIN if it asks.",
    ),
    BuocHuongDan(
        "4-tuychonnpt",
        "Mở Tùy chọn nhà phát triển", "Open Developer options",
        "Bấm Back một lần. Cuối danh sách \"Lựa chọn ưu tiên về thiết bị\" (Google TV: trong \"Hệ thống\") có thêm \"Tùy chọn nhà phát triển\" — chọn nó.",
        "Press Back once. A new \"Developer options\" item is at the end of \"Device Preferences\" (Google TV: inside \"System\") — open it.",
    ),
    BuocHuongDan(
        "5-goloi",
        "Bật Gỡ lỗi qua USB", "Turn on USB debugging",
        "Bật \"Gỡ lỗi qua USB\". Tivi nào có thêm \"Gỡ lỗi qua mạng\" / \"ADB debugging\" thì bật luôn. Không cần cắm dây USB nào cả — app kết nối qua Wi-Fi.",
        "Turn on \"USB debugging\". If the TV also has \"Network debugging\" / \"ADB debugging\", turn that on too. No USB cable is needed — the app connects over Wi-Fi.",
    ),
    BuocHuongDan(
        "6-goloiwifi",
        "Google TV đời mới: Gỡ lỗi qua Wi-Fi", "Newer Google TV: Wireless debugging",
        "Nếu app quét không thấy tivi: bật \"Gỡ lỗi qua Wi-Fi\", vào trong chọn \"Ghép nối thiết bị bằng mã ghép nối\". Nhập IP:cổng và mã 6 số hiện trên tivi vào phần \"Ghép đôi bằng mã\" trong app.",
        "If the app can't find the TV: turn on \"Wireless debugging\", open it and choose \"Pair device with pairing code\". Type the IP:port and 6-digit code shown on the TV into \"Pair with code\" in the app.",
    ),
    BuocHuongDan(
        "7-chophep",
        "Bấm Cho phép trên tivi", "Press Allow on the TV",
        "Lần đầu app kết nối, tivi hiện \"Cho phép gỡ lỗi qua USB?\". Tích \"Luôn cho phép từ máy tính này\" rồi bấm \"Cho phép\". Không thấy hộp này thì bấm Kết nối lại.",
        "The first time the app connects, the TV shows \"Allow USB debugging?\". Tick \"Always allow from this computer\" and press \"Allow\". No dialog? Press Connect again.",
    ),
)
