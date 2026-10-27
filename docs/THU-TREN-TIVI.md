# Danh sách thử TiviKit trên tivi thật

Dùng trước khi phát hành, hoặc khi muốn đổi một tính năng từ ⚠️ sang ✅ trong README. Xếp theo rủi ro: cái nào hỏng thì
tivi khó về như cũ nhất được thử trước. Nên thử trên box rẻ (VD Tanix TX3 Mini) rồi mới tới tivi chính.

Trước khi bắt đầu:

- Điện thoại và tivi chung Wi-Fi, tivi đã bật Gỡ lỗi USB (hoặc Gỡ lỗi không dây).
- Kiểm tra trên tivi đang không phát gì quan trọng.
- Ghi lại bản TiviKit (màn chính → Cập nhật TiviKit) và model tivi để báo kết quả.

Mỗi mục: **Thử** (bấm gì), **Đạt khi** (thấy gì), **Hỏng thì** (cách đưa tivi về như cũ).

## 1. Khôi phục (rủi ro cao nhất)

- **Thử:** trên tivi đã dọn bằng TiviKit, màn chính → Khôi phục → chọn tivi → Khôi phục.
- **Đạt khi:** mọi gói TiviKit đã tắt bật lại; Home gốc của hãng là Home mặc định (bấm Home trên remote ra Home gốc);
  app báo "Đã khôi phục". Khởi động lại tivi, vẫn đúng như vậy.
- **Hỏng thì:** chạy Khôi phục lần nữa; vẫn hỏng thì dùng kịch bản khôi phục cho máy tính (màn Xong → Kịch bản khôi
  phục) với `adb` trên máy tính. Gửi lại ảnh màn lỗi.
- Sau khi đạt: dọn lại tivi như bình thường.

## 2. Cập nhật Home sạch lên 3.0 (lần đầu đổi khoá)

Tivi đang có Home sạch cài từ TiviKit 1.1 trở về trước (bản 2.7, khoá của VGS).

- **Thử:** màn chính → Home sạch → Đổi giao diện → Áp dụng.
- **Đạt khi:** app báo gỡ rồi cài lại Home sạch; bấm Home ra Home sạch mới; giao diện, ảnh nền riêng, nút remote đã
  gán, ô Kênh nhanh vẫn còn. Trong Cài đặt → Ứng dụng, Home sạch (com.gathin.tvhome) là bản 3.0.
- **Hỏng thì:** bấm Áp dụng lần nữa; Home sạch không mở được thì Khôi phục (mục 1) rồi cài lại Home sạch.
- Nhớ: app chọn ở "Mở khi khởi động" trên tivi phải chọn lại một lần.

## 3. Quản lý launcher

Nên có thêm một launcher tự cài (VD Projectivy từ CH Play) để thử đổi qua lại.

| Thử | Đạt khi |
| --- | --- |
| Quản lý launcher | Đủ launcher trên tivi, cả cái đang tắt (ghi "Đang tắt"), mỗi cái có mức ưu tiên; thẻ Nút Home ghi đúng cái bấm Home ra |
| Bấm thử nút Home | Tivi mở Cài đặt rồi về Home; dòng "Vừa bấm thử" ghi đúng launcher hiện ra |
| Projectivy → Đặt làm Home | Bấm Home trên remote ra Projectivy; app báo đã đặt |
| Home gốc đang tắt → Bật lại | App hỏi trước nếu Home gốc sẽ giành Home; bật xong nút Home mở đúng như app báo |
| Projectivy → Đặt làm Home khi Home gốc đang bật | App hỏi tắt Home gốc trước; xong thì bấm Home ra Projectivy, Khôi phục bật lại được Home gốc |
| Đang dùng Projectivy, vào Home sạch → Áp dụng | Giao diện Home sạch đổi; nút Home vẫn mở Projectivy |
| Google TV: Home sạch → Đặt làm Home, rồi Về Home gốc | Home sạch mở đè lên Home Google; về Home gốc thì bấm Home ra Home Google |
| Home gốc đang bật, Projectivy → Đặt làm Home → "Không tắt …: để Home sạch mở Projectivy đè lên" | Home gốc vẫn bật; bấm Home: Home gốc hiện một thoáng rồi Projectivy |
| Google TV: Projectivy → Đặt làm Home | App cập nhật Home sạch nếu cần; bấm Home ra Home Google một thoáng rồi Projectivy; Bấm thử nút Home ghi Projectivy |
| Projectivy → Bật trợ năng, rồi Tắt trợ năng | Cài đặt → Hỗ trợ tiếp cận: Projectivy bật rồi tắt; dịch vụ của Home sạch vẫn bật, nút remote gán riêng vẫn chạy |
| Launcher gợi ý → FLauncher → CH Play | Trang FLauncher mở trên CH Play của tivi; cài xong bấm Đọc lại thì FLauncher có trong danh sách |
| ATV Launcher Pro (nếu có) → Cho phép widget | Thêm widget đồng hồ trong ATV Launcher được, không báo lỗi |
| Home sạch → Đặt làm Home | Bấm Home ra Home sạch như trước |

- **Hỏng thì:** Quản lý launcher → Về Home gốc; vẫn sai thì Khôi phục (mục 1).

## 4. TiviKit Launcher

Màn chính → TiviKit Launcher (mở màn Quản lý launcher, thẻ TiviKit Launcher ở trên cùng).

| Thử | Đạt khi |
| --- | --- |
| Cài lên tivi | App báo đã cài; thẻ ghi "Bản 1.0 trên tivi"; bấm Home trên remote vẫn ra Home cũ, tivi không hỏi chọn Home |
| Mở thử | Tivi mở TiviKit Launcher: giờ, thứ ngày, âm lịch, RAM / bộ nhớ trống, ba nút kính, dock có app; bấm Home về Home cũ |
| Đặt làm Home → Đặt làm Home | Bấm Home trên remote ra TiviKit Launcher; thẻ có chip "Nút Home mở"; thẻ Nút Home ghi "Mở TiviKit Launcher" |
| Google TV: Đặt làm Home | Home sạch mở TiviKit Launcher đè lên Home Google (như Projectivy ở mục 3) |
| Trên tivi: ◀ ▶ trong dock, OK mở app, giữ OK | App mở đúng; giữ OK hiện menu (Mở, Dời chỗ, Bỏ khỏi dock / Ghim, Thông tin, Gỡ), Back đóng menu về đúng ô |
| Trên tivi: ô lưới cuối dock | Trang Ứng dụng có mọi app; app đã ghim có dấu ghim; Back về dock |
| Trên tivi: Dọn RAM | Báo trống thêm bao nhiêu MB (Android 14 trở đi không có nút này) |
| Trên tivi: nút Wi-Fi | Hiện tên Wi-Fi (TiviKit đã cấp quyền vị trí khi cài), tốc độ dưới nút; bấm mở cài đặt mạng |
| Mở một app từ trang Ứng dụng rồi bấm Home | App đó hiện sau vạch ngăn trong dock |
| Tivi có Home sạch đặt tên lời chào và nơi xem thời tiết → Cài lên tivi / Cập nhật | Lời chào có tên; dòng âm lịch có biểu tượng, nhiệt độ, trời (VD "26°C Nhiều mây") |
| Mở app bằng giọng nói hay nút remote gán riêng, rồi bấm Home | App đó vẫn hiện sau vạch ngăn (thống kê sử dụng) |
| Tivi thường (không phải box RAM thấp): phát nhạc / video trong SmartTube hay YouTube Music, bấm Home | Thẻ Đang phát trên dock: tên bài, tên app, sóng nhạc; OK mở lại đúng màn đang phát; giữ OK tạm dừng / phát tiếp |
| Tuỳ chỉnh → đổi tên lời chào, chọn ảnh nền có sẵn → Áp dụng lên tivi | Màn Home trên tivi đổi tên và ảnh ngay; thẻ ghi "Tivi đang dùng ảnh riêng" |
| Tuỳ chỉnh → Ảnh từ điện thoại → Áp dụng | Ảnh chụp lên làm nền (cắt giữa 16:9), kính mờ theo màu ảnh |
| Tuỳ chỉnh → Đồi chè → Áp dụng; thời tiết "Không hiện" → Áp dụng | Về ảnh mặc định; dòng âm lịch thôi hiện thời tiết |
| Khôi phục (mục 1) khi TiviKit Launcher đang là Home | Home về như trước; TiviKit Launcher bị gỡ |

- **Hỏng thì:** Quản lý launcher → Về Home gốc hay Đặt làm Home cho Home sạch; vẫn sai thì Khôi phục (mục 1).

## 5. Home sạch 3.0: các công tắc mới

| Thử | Đạt khi |
| --- | --- |
| Tuỳ chỉnh → Mở một app khi bật tivi → Áp dụng → Mở màn chọn app trên tivi → chọn SmartTube | Tắt nguồn rồi bật lại (rút điện), SmartTube tự mở |
| Bật thêm "Cả khi bật lại từ chế độ chờ" → Áp dụng | Tắt bằng remote, bật lại bằng remote: SmartTube tự mở |
| Tuỳ chỉnh → Luôn mở Home sạch khi bật tivi → Áp dụng | Đang ở HDMI / app, tắt rồi bật: khoảng 1 giây sau Home sạch hiện |
| Kênh nhanh → Thêm ô kênh → VTVgo (cần VTVgo bản tivi) → VTV3 → Thêm lên tivi; rồi gán một nút remote cho ô | Ô "VTV3" trên Home sạch và nút remote đều mở thẳng VTV3 trong VTVgo |
| Giao diện Kính mờ; Chữ giờ, ngày trắng sáng; ảnh riêng "vừa khung" | Hình đúng như xem trước; đổi lại giao diện khác không dính khoá của Kính mờ |
| Thời tiết: gõ "Osaka" | Có kết quả ở Nhật, chọn được, Home sạch hiện thời tiết Osaka |
| Remote → Giữ nút âm lượng để tăng / giảm liên tục (nên thử trên Sony) | Giữ nút âm lượng trên remote thật thì âm lượng chạy liên tục |

- **Hỏng thì:** tắt công tắc vừa bật, Áp dụng lại.

## 6. Khoá trẻ em: mã OTP, giới hạn giờ xem

Cần một app xác thực (Google Authenticator, Microsoft Authenticator, app Mật khẩu của iPhone…) trên điện thoại.

- **Thử mã OTP:** Khoá trẻ em → thẻ Mã mở khoá → Tạo mã OTP → quét QR bằng app xác thực → nhập mã app đang hiện →
  Kiểm tra → cất 10 mã dự phòng, đánh dấu Đã cất → Lưu & áp dụng.
- **Đạt khi:** về màn chính rồi vào lại Khoá trẻ em thì app hỏi mã; mã sai báo còn mấy lần, mã đúng mở màn khoá; mỗi
  mã dự phòng dùng được một lần; Dòng lệnh ADB và Khôi phục cũng hỏi mã.
- **Thử màn khoá:** thêm khung giờ khoá chứa giờ hiện tại → Lưu. Tivi hiện màn khoá với 6 ô số và giờ tivi; gõ mã
  bằng remote (số hoặc mũi tên + OK) thì mở 60 phút; gõ một mã dự phòng 8 số cũng mở được.
- **Thử giới hạn:** giới hạn giờ xem ngày thường và cuối tuần đều 6 phút → Lưu; xem tivi liên tục. Còn 5 phút (tức
  sau khoảng 1 phút xem) thì tivi báo trước; hết giờ thì tivi khoá tới nửa đêm, nhập mã mở được 60 phút; báo cáo
  thời gian xem trên điện thoại có số phút đúng.
- **Hỏng thì:** nhập mã (hoặc mã dự phòng) trong TiviKit, tắt khung giờ / giới hạn rồi Lưu, hay Tắt và gỡ Khoá trẻ
  em. Mất cả app xác thực lẫn mã dự phòng: hộp nhập mã → Quên mã?, 24 giờ sau vào lại được không cần mã.

## 7. Quản lý app, Làm mới tivi, Kiểm tra mạng, Cài đặt nhanh

| Thử | Đạt khi |
| --- | --- |
| Quản lý app → chọn một app ít dùng → Tắt, rồi Bật lại | App biến mất khỏi Home rồi hiện lại, mở được |
| Quản lý app → thẻ Đang chạy ngầm | Có danh sách app và RAM, mục "nên tắt" hợp lý |
| Làm mới tivi → Làm mới | RAM trống tăng; tivi vẫn chạy bình thường |
| Kiểm tra mạng → Kiểm tra | Có sóng, độ trễ, nhận xét; thẻ CH Play đúng tình trạng tài khoản |
| Cài đặt nhanh → hiệu ứng Nhanh, tự tắt 4 giờ | Cài đặt tivi hiện đúng giá trị mới; đặt lại được như cũ |

## 8. Dòng lệnh ADB

| Thử | Đạt khi |
| --- | --- |
| Nâng cao → Dòng lệnh ADB → Lệnh mẫu → "Tóm tắt máy" (nút ▶) | Hiện hãng, model, bản Android của tivi |
| Gõ `ping 8.8.8.8`, đợi vài dòng rồi bấm Dừng | Từng dòng hiện ngay khi tới; sau khi Dừng, `ps -A \| grep ping` không còn ping |
| Gõ `cd /sdcard`, rồi `ls` | `ls` liệt kê bộ nhớ trong; dấu nhắc ghi `/sdcard` |
| Lệnh mẫu "Tắt app" với một app ít dùng → điền gói → Chạy → Vẫn chạy | Có hộp hỏi lại; app tắt; khung báo đã ghi vào bản sao lưu; Khôi phục bật lại được |
| Gõ `rm -rf /sdcard/Download` rồi bấm Chạy (chưa đồng ý) | Hộp "Lệnh nguy hiểm"; Vẫn chạy chỉ bấm được sau khi tích; bấm Thôi |
| Dán `adb shell getprop ro.product.model` | App bỏ "adb shell", chạy được, có dòng báo đã bỏ |
| Lệnh mẫu "Hiệu ứng chuyển màn nhanh gấp đôi" | In giá trị cũ trước khi đổi; cài đặt tivi đúng giá trị mới |
| `logcat` chạy rồi bấm Quay lại | Lệnh dừng, không chạy ngầm trên tivi |

## 9. Set App Link

| Thử | Đạt khi |
| --- | --- |
| Nâng cao → Set App Link | Hiện các trang có app nhận link (VD YouTube khi có SmartTube), mỗi trang ghi đang mở bằng gì |
| Bấm YouTube → chọn SmartTube | App báo đã đặt; mở một link YouTube từ Telegram / trình duyệt trên tivi thì vào thẳng SmartTube |
| Bấm YouTube → Hỏi mỗi lần | App báo đã trả về; mở link YouTube thì tivi lại hỏi chọn app |
| Trang khác → gõ một tên miền (VD vimeo.com) | Thêm dòng mới, ghi đúng app nào nhận (hoặc chưa có app nào) |
| Tivi Android 12 trở lên có YouTube gốc: chọn SmartTube cho YouTube | Link YouTube mở bằng SmartTube; app báo đã tắt tự mở link của YouTube gốc |

## 10. Các tính năng còn lại

| Thử | Đạt khi |
| --- | --- |
| Điều khiển → gõ tiếng Việt có dấu vào ô tìm kiếm YouTube | Chữ có dấu hiện đúng trên tivi |
| Điều khiển → Xem màn hình tivi → chạm vào một ô app trên hình | App đó mở trên tivi |
| Gửi ảnh, video → chọn một ảnh | Ảnh nằm trong Download/TiviKit trên tivi và mở xem được |
| Bộ cài đặt mẫu → tạo bộ có 1 app + DNS → Cài cho tivi này | App được cài, DNS đặt đúng |
| Sao lưu & chuyển máy → Lưu ra file → Nhập trên điện thoại khác | Tivi đã dọn, bộ cài đặt hiện đủ trên máy mới |
| Kênh nhanh với 2 ô trở lên (TV360 và VTVgo lẫn nhau) | Mỗi ô mở đúng kênh trong đúng app của nó |
| Home sạch → Áp dụng (lên bản mới), rồi Nút remote → dò một nút → "SmartTube – tìm bằng giọng nói" → Lưu → Thử nút | SmartTube mở màn tìm kiếm; đã bật "Tìm kiếm bằng giọng nói tức thì" trong SmartTube thì mic bật ngay |
| Nối TiviKit vào một điện thoại (Gỡ lỗi không dây) | Hiện cảnh báo đỏ "không khai là tivi", nút dọn bị khoá |
| Chế độ tivi LG (nếu có tivi LG) | Ghép đôi được, remote và gõ chữ chạy |

## Báo kết quả

Gửi vào nhóm [Telegram](https://t.me/tivikit) hoặc [GitHub Issues](https://github.com/Circling5714/tivikit/issues):
bản TiviKit, model tivi, mục nào đạt / hỏng, ảnh màn lỗi nếu có. Mục đạt được đổi sang ✅ trong README.
