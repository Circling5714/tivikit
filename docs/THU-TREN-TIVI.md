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
- **Thử khoá theo app:** thẻ Khoá theo app → chọn SmartTube (hay bấm Nhóm xem video) → Theo giờ, khung chứa giờ hiện
  tại → Thêm luật này → Lưu. Mở SmartTube: tivi về Home, hiện "SmartTube đang nghỉ"; Back thì ra ngoài; gõ mã thì
  SmartTube mở lại và dùng được 60 phút. Thẻ Hẹn giờ khoá app → chọn một app → Sau 15 phút → Hẹn khoá: mở app đó, còn
  5 phút thì tivi báo ở góc, tới giờ thì tivi che app lại.
- **Thử bảo vệ Cài đặt:** lúc khoá bật, mở Cài đặt trên tivi → hiện "Cài đặt cần mã của bố mẹ"; gõ mã thì Cài đặt mở,
  10 phút sau mới hỏi lại. Gỡ app khoá từ Cài đặt / trình quản lý file cũng hỏi mã.
- **Thử bảng phụ huynh (không dùng điện thoại):** trên tivi mở ô Khoá trẻ em trong danh sách app → gõ mã → bảng phụ
  huynh: Khoá một app… → chọn app → Khoá ngay (góc màn hình báo, mở app đó thì bị che); trên màn khoá, đi xuống nút
  Phụ huynh → gõ mã → Mở tivi 30 phút thì tivi mở tới đúng giờ; Back trong bảng lùi một bước.
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

## 9b. Cast SmartTube

| Thử | Đạt khi |
| --- | --- |
| Tiện ích → Cast SmartTube → Bật Cast SmartTube | Trong chừng 20 giây hiện "Dịch vụ Cast đang chạy" và "Đã nối YouTube" |
| iPhone / điện thoại Android cùng Wi-Fi: YouTube → nút Cast | Có dòng "SmartTube · <tên tivi>" (hay tên đã đặt) |
| Chọn tivi đó rồi bấm một video | Tivi mở video bằng SmartTube, đúng giây đang xem trên điện thoại |
| Tạm dừng, phát tiếp, tua trên điện thoại | SmartTube làm theo |
| Tạm dừng bằng remote tivi (tivi không phải loại ít RAM) | Điện thoại cũng hiện đang dừng |
| Tivi đang ở màn bảo vệ rồi gửi video | Màn bảo vệ tắt, video lên |
| Đổi tên → Lưu tên, mở lại YouTube trên điện thoại | Nút Cast hiện tên mới |
| SmartTube đang nghe nền (bấm Home lúc đang phát), gửi video khác từ điện thoại | SmartTube hiện lên mở video mới rồi tự về lại launcher đang dùng, video mới phát nền |
| Tivi có Cast của hãng (Skyworth Google TV): mở màn Cast SmartTube | Thẻ "Liên kết bằng mã TV" báo tivi có Cast của hãng chen trước |
| Lấy mã TV, nhập trên điện thoại: YouTube → nút Cast → Thêm thiết bị / Liên kết bằng mã TV | Nút Cast có tên tivi (không mờ), chọn là nối; cả khi SmartTube chưa mở |
| Cast một video (cả video nhạc tắt quyền nhúng), xem thẻ Đang phát trên TiviKit Launcher và ở màn Điều khiển của TiviKit (tivi cho đọc phiên phát) | Hiện tên video và kênh, không chỉ "SmartTube" |
| Trên tivi: mở app "Cast SmartTube" trong danh sách app | Mã TV chữ to đủ 12 số, đếm ngược tới mã mới; danh sách điện thoại đã cast |
| Một điện thoại chưa từng cast (khách) cast lần đầu | Tivi hỏi Cho phép luôn / tới hết hôm nay / 24 giờ / Chặn; chưa trả lời thì chưa phát; Back thì bỏ qua lần này |
| Tivi đang ở màn bảo vệ, máy lạ cast | Màn bảo vệ tắt, hộp hỏi hiện |
| Cho khách "tới hết hôm nay", sang 0 giờ cast lại | Tivi bỏ qua video, báo khách đã hết hạn; danh sách ghi "Khách đã hết hạn" |
| Chặn một máy (trên tivi hay ở TiviKit), máy đó cast | Tivi bỏ qua video và cả lệnh tạm dừng / tua của máy đó, báo nhỏ trên tivi |
| Tắt "Nhận cast", cast từ máy đã được phép | Tivi bỏ qua, báo đang tạm ngừng; bật lại thì cast được |
| Huỷ mọi liên kết (trên tivi hay ở TiviKit), nhập mã TV mới vào điện thoại rồi cast | Mọi điện thoại mất tivi; lúc tivi nối lại thì màn ghi "Đang lấy mã…" chứ không hiện mã cũ, TiviKit tự xin mã mới; nhập mã mới thì cast tới tivi, tivi hỏi lại như máy lạ |
| Rút mạng tivi giữa chừng (hay router khởi động lại) rồi cắm lại | Điện thoại đã liên kết bằng mã TV vẫn thấy tivi, không phải nhập mã mới |
| Tivi có Khoá trẻ em đang bật: chặn / cho phép / huỷ liên kết / đổi tên trên tivi | Hiện lớp "Cần mã của bố mẹ"; đúng mã thì làm, Back thì không đổi gì; xem mã TV thì không hỏi |
| Gỡ Cast SmartTube | Hỏi chắc trước; gỡ xong nút Cast trên điện thoại không còn tivi đó |

## 9c. Map Voice

Cần remote có mic (nút Trợ lý Google) và SmartTube hay TizenTube trên tivi; muốn thử kênh thì cần VTVgo, TV360 hay FPT Play bản
tivi (FPT Play phải đăng nhập).

| Thử | Đạt khi |
| --- | --- |
| Tiện ích → Map Voice → Bật Map Voice | Tivi hiện "Cho phép gỡ lỗi?" (lần đầu); chọn Luôn cho phép rồi OK; Làm mới thì ghi "Đang nghe câu nói" |
| Bấm mic, nói "mở nhạc piano trên YouTube" | Trợ lý đóng lại, tivi báo nhỏ "Map Voice: …", SmartTube phát video đầu tiên (YouTube gốc có thể hiện chừng một giây) |
| Nói "tìm hoạt hình trên YouTube" | SmartTube mở trang kết quả tìm "hoạt hình", không tự phát |
| Nói "nhạc Trịnh Công Sơn" (không nói YouTube) | SmartTube mở trang tìm kiếm; tắt "Cả câu không nói YouTube" rồi Lưu thì câu này về Trợ lý |
| Nói "thời tiết hôm nay", "mở Netflix" | Trợ lý làm như thường, Map Voice không chen vào |
| Nói tên một bài hát mà Trợ lý tự mở YouTube gốc | Vài giây sau tivi chuyển sang SmartTube tìm đúng câu đó |
| Nói "mở VTV3" (App mở kênh: VTVgo) rồi "xem kênh VTV3 trên TV360" | Lần đầu mở VTV3 trong VTVgo, lần sau trong TV360 |
| Nói "mở HTV7" khi chọn VTVgo | VTVgo không có HTV7 nên mở bằng TV360 |
| Màn Map Voice → Câu vừa nghe | Có đúng các câu vừa nói và việc đã làm |
| Thử một câu → gõ "mo VTV3" → Xem Map Voice hiểu thế nào, rồi Chạy trên tivi | Hiện "Mở kênh VTV3 bằng …"; Chạy thì tivi mở kênh đó |
| Khởi động lại tivi, nói lại một câu | Map Voice tự chạy và nghe lại, không hỏi cho phép lần nữa |
| Cách chuyển → Mở YouTube bằng → TizenTube → Lưu; đóng hẳn TizenTube rồi nói "tìm hoạt hình" | TizenTube mở trang kết quả "hoạt hình" (chữ tìm có thể hiện sau vài giây) |
| Nói "mở nhạc piano" khi chọn TizenTube | TizenTube phát video đầu tiên |
| Tivi có YouTube gốc thật: Mở YouTube bằng → YouTube gốc → nói "tìm nhạc piano" | YouTube gốc mở trang kết quả, không bị đóng ngay |
| Câu riêng: "giá vàng" → Trợ lý Google → Lưu; nói "tìm giá vàng SJC hôm nay" | Trợ lý trả lời, Map Voice không chen vào (Câu vừa nghe: "Câu riêng … để Trợ lý Google làm") |
| Câu riêng: "phim" → Mở app khác… → FPT Play → Lưu; nói "xem phim hay" | FPT Play mở |
| App mở kênh: FPT Play (FPT Play đã đăng nhập) → nói "mở VTV3", rồi "xem VTV1 trên FPT Play" | FPT Play phát đúng kênh |
| Tắt Gỡ lỗi USB / ADB qua mạng trên tivi, mở màn Map Voice | Báo tivi không mở ADB qua mạng; bật lại rồi Nối lại thì nghe lại |

- **Hỏng thì:** tắt công tắc Bật Map Voice rồi Lưu (câu nói về Trợ lý như cũ), hay Gỡ Map Voice khỏi tivi. Trợ lý Google không
  nghe gì nữa: Cài đặt → Ứng dụng → Google (katniss) → Buộc dừng, hay khởi động lại tivi. Gửi kèm nội dung mục Câu vừa nghe.

## 9d. Hẹn giờ

Nên có SmartTube hay một ô Kênh nhanh trên tivi để hẹn mở.

| Thử | Đạt khi |
| --- | --- |
| Tiện ích → Hẹn giờ → Mở app / kênh lúc 2 phút nữa (chọn SmartTube) → Thêm vào lịch → Lưu lên tivi | App báo "Đã lưu lên tivi. Việc kế tiếp: …"; tới giờ SmartTube mở trên tivi |
| Như trên, nhưng để tivi vào màn bảo vệ trước giờ hẹn | Tới giờ màn bảo vệ tắt và SmartTube hiện lên, không mở phía sau màn bảo vệ |
| Bật tivi lúc 2 phút nữa, để tivi ở màn bảo vệ | Tới giờ màn bảo vệ tắt |
| Tắt tivi lúc 2 phút nữa | Trước 1 phút tivi báo có nút "Hoãn 30 phút" (chọn sẵn); bấm Hoãn thì không tắt; để yên thì tivi tắt đúng giờ |
| Thử: tivi tự bật sau 2 phút, rồi tắt tivi bằng remote ngay | Khoảng 2 phút sau tivi tự bật, hiện "Hẹn giờ: tivi đã tự bật" (tivi ngủ sâu thì không tự bật được) |
| Làm mới → Lần chạy gần đây | Mỗi dòng là câu dễ hiểu, VD "Mở SmartTube – đã mở", "Tắt tivi – hoãn 30 phút", không còn mã như danh-thuc, lo-gio |
| Bấm bút ở một việc → đổi giờ / ngày / app → Cập nhật việc → Lưu lên tivi | Dòng đang sửa có viền xanh; bấm Cập nhật thì việc đó đổi, không thêm dòng mới, công tắc bật / tắt giữ nguyên; Thôi sửa thì không đổi gì |
| Đang sửa mà bấm Lưu lên tivi | App nhắc bấm Cập nhật việc hoặc Thôi sửa trước |
| Trên tivi: mở ô "Hẹn giờ (TiviKit)" trong danh sách app (hay Mở trên tivi ở TiviKit) | Màn Hẹn giờ có việc kế tiếp, nút hẹn tắt, lịch, lần chạy gần đây; ◀ ▶ ▲ ▼ OK đi được mọi chỗ, Back về màn trước |
| Trên tivi: hẹn tắt 30 phút, rồi OK ở dòng việc một lần → Huỷ việc | Có dòng "Tắt tivi lúc … còn 30 phút"; huỷ xong thì mất; TiviKit (Làm mới) thấy giống vậy |
| Trên tivi: + Thêm việc → ◀ ▶ đổi việc, Giờ (gõ phím số), Lặp lại, App → ▼ tới Lưu | ▼ từ dòng cuối vào thẳng nút Lưu; việc mới có trong lịch, TiviKit cũng thấy |
| Trên tivi: OK ở một việc → đổi Trạng thái → Lưu; rồi Xoá việc | Việc thành Đang tắt, rồi mất khỏi lịch; màn hỏi lại chọn sẵn Thôi |
| Khoá trẻ em đang bật: trên tivi thêm / sửa / xoá một việc | Hiện màn "Cần mã của bố mẹ"; Back thì không đổi gì, đúng mã thì đổi; hẹn tắt tivi thì không hỏi mã |

- **Hỏng thì:** tắt công tắc của việc đó rồi Lưu lên tivi, hay Gỡ Hẹn giờ khỏi tivi (cuối màn Hẹn giờ).

## 9e. Tele Remote

Cần một bot Telegram mới (tạo ở @BotFather, chỉ dùng cho tivi này) và tài khoản Telegram có @username.

| Thử | Đạt khi |
| --- | --- |
| Tiện ích → Tele Remote → Bật Tele Remote | Tivi hiện "Cho phép gỡ lỗi?" (lần đầu); chọn Luôn cho phép rồi OK; Làm mới thì ghi "Đã nối ADB của tivi" |
| Dán token của bot → Nối bot | Hiện "Đã nối bot @…"; Làm mới thì ghi "Bot đang nghe lệnh" |
| Thêm @username của bạn làm chủ, mở bot trên Telegram, bấm Start | Bot chào kèm tên tivi, hiện bàn phím 🎮 📺 ▶️ ℹ️ 📴; ở TiviKit người đó ghi "đã nhắn bot" |
| 🎮 Điều khiển → bấm ⬆️ ⬇️ OK ↩️ 🏠, 🔉 🔊 | Tivi làm đúng phím; tin bảng điều khiển đổi "Vừa bấm …" và app đang mở, khung chat không trôi |
| 📺 Kênh nhanh (tivi có ô Kênh nhanh) → bấm một kênh | Tivi mở kênh đó |
| ▶️ YouTube, ℹ️ Tivi | SmartTube (hay TizenTube) mở; bot gửi màn hình, app đang mở, âm lượng, RAM, mạng |
| 📴 Tắt tivi → Tắt ngay | Tivi về chế độ chờ; thành viên bấm thì bot báo chỉ chủ mới tắt được |
| Một tài khoản Telegram khác (không có trong danh sách) nhắn bot | Bot báo chưa được phép, chủ nhận tin "muốn dùng bot"; TiviKit hiện ở Đang xin dùng; Duyệt thì người đó dùng được |
| Tắt tivi hẳn, nhắn bot một lệnh, bật tivi lại | Lệnh cũ không làm; lệnh mới sau khi tivi bật thì làm |
| Đổi sang bot khác, hay dùng cùng token cho tivi thứ hai | Bot mới nghe lệnh; tivi thứ hai báo "Bot này đang chạy ở tivi / máy khác" |
| Gõ "mở nhạc trịnh", "mở VTV3" (tivi có Map Voice) | Map Voice làm như vừa nói: SmartTube phát video đầu tiên / mở kênh; bot báo việc đã làm |
| Gõ "mở <tên một app trên tivi>", rồi một câu khác (VD "giá vàng hôm nay") | App đó mở; câu kia mở Trợ lý Google trên tivi (tivi tắt Trợ lý thì bot báo) |
| Dán link YouTube, link fptplay.vn | Video mở trong app YouTube của Map Voice (SmartTube…); link FPT Play mở FPT Play |
| Gửi một ảnh, một video ngắn | Tivi chiếu ảnh toàn màn hình / phát video; Back trên remote để tắt; ảnh tự tắt sau 10 phút |
| 🎮 → ⌨️ Gõ chữ, dùng ⬆️⬇️⬅️➡️ chọn ô tìm kiếm của SmartTube, gõ "phở bò", rồi ⌨️ Gõ tiếp " hà nội", ↵ Enter | "phở bò" thay chữ cũ trong ô (chữ có dấu đúng), rồi thành "phở bò hà nội"; Enter thì SmartTube tìm |
| 📱 Ứng dụng → bấm một app; chủ bấm ⏹ Buộc dừng | App mở; app đang mở bị dừng |
| ⏰ Hẹn giờ → Tắt sau 15 phút → ✕ huỷ | Hẹn hiện trong bảng rồi mất; TiviKit → Hẹn giờ thấy giống vậy |
| 🔒 Khoá trẻ em → Khoá ngay, rồi 🔓 30 phút, gõ mã 6 số của bố mẹ | Tivi khoá; mã đúng thì mở tới giờ bot báo và tin chứa mã bị xoá; mã sai báo còn mấy lần |
| 🧹 Làm mới → Đóng app nền; bật Chụp màn hình ở TiviKit rồi bấm 📸 Màn hình | RAM trống tăng; bot gửi ảnh màn hình tivi |
| Bật Báo tin ở TiviKit, tắt tivi bằng remote, vài phút sau bật lại | Bot nhắn các chủ "vừa tắt", rồi "vừa bật" |
| 📢 Nhắn tivi → gõ "ăn cơm thôi", rồi bấm OK trên remote | Lời nhắn hiện to trên tivi kèm tiếng bíp; OK thì đóng và bot báo "đã xem" |
| ⚙️ Thêm → 🌐 Kiểm tra mạng | Bot báo Wi-Fi (băng, sóng, tốc độ) hay dây LAN, độ trễ tới router / Internet / YouTube, nhận xét |
| Gõ "âm lượng 10", "to lên", "tắt tiếng", "bật tiếng" | Âm lượng đổi đúng, bot báo mức mới |
| Tivi ở màn bảo vệ: ℹ️ Tivi → ☀️ Bật màn hình (hay gõ "bật tivi") | Tivi thoát màn bảo vệ |
| Một tài khoản lạ nhắn bot; chủ bấm ✅ Duyệt thành viên ngay trong tin báo | Người đó nhận tin "đã được duyệt", dùng được bot; 👥 hiện họ |
| ⚙️ Thêm → 👥 Người dùng → bấm một người → đổi vai trò / bỏ; ➕ thêm @username | Danh sách đổi theo; tivi không cho bỏ / hạ chủ cuối cùng |
| ⚙️ Thêm → ⏸ Khoá thành viên 1 giờ, thành viên bấm thử | Thành viên bị báo "tạm khoá tới …"; ▶️ Mở lại (hay nút ở TiviKit) thì dùng lại được |
| ⚙️ Thêm → 📜 Việc vừa làm | 15 việc gần nhất, ai làm, lúc nào |
| 📸 Màn hình → 📹 Quay 10 giây (đang phát video) | Bot gửi video 10 giây màn hình tivi, xem được ngay trong Telegram |
| Gửi một ảnh, bấm 🖼 Đặt làm ảnh nền Home (tivi có TiviKit Launcher) | Home của TiviKit Launcher đổi nền thành ảnh đó (cắt giữa 16:9); ↩️ thì về ảnh nền mặc định |
| 📺 Kênh nhanh → 📺 Tất cả kênh → bấm VTV3, HTV7 | Map Voice mở kênh bằng app kênh đã chọn (TV360 / VTVgo / FPT Play) |
| Một điện thoại lạ (chưa liên kết) cast YouTube lên tivi | Tivi hỏi trên màn hình, đồng thời bot nhắn chủ kèm nút; bấm ✅ / 📅 / ⏱ / 🚫 thì máy đó cast được / bị chặn |

- **Thành viên:** cùng các việc trên trừ Hẹn giờ, Khoá trẻ em, Làm mới, chụp màn hình, tắt / khởi động lại tivi, buộc dừng app
  (bot báo chỉ chủ làm được).
- **Hỏng thì:** tắt công tắc Bật Tele Remote (bot thôi nghe), hay Gỡ Tele Remote khỏi tivi. Bot nhắn chậm hay không trả lời:
  xem dòng Telegram ở mục Trên tivi (tivi chưa vào được Telegram thì đặt DNS ở mục DNS chặn quảng cáo).

## 10. Các tính năng còn lại

| Thử | Đạt khi |
| --- | --- |
| Điều khiển → gõ tiếng Việt có dấu vào ô tìm kiếm YouTube | Chữ có dấu hiện đúng trên tivi |
| Điều khiển → Xem màn hình tivi → chạm vào một ô app trên hình | App đó mở trên tivi |
| Google TV có Home sạch mở launcher đã chọn đè Home gốc: Điều khiển → nút Home vài lần | Lần nào cũng về đúng launcher đã chọn, không kẹt ở Home Google TV |
| Gửi ảnh, video → chọn một ảnh | Ảnh nằm trong Download/TiviKit trên tivi và mở xem được |
| Bộ cài đặt mẫu → tạo bộ có 1 app + DNS → Cài cho tivi này | App được cài, DNS đặt đúng |
| Sao lưu & chuyển máy → Lưu ra file → Nhập trên điện thoại khác | Tivi đã dọn, bộ cài đặt hiện đủ trên máy mới |
| Kênh nhanh với 2 ô trở lên (TV360 và VTVgo lẫn nhau) | Mỗi ô mở đúng kênh trong đúng app của nó |
| Kênh nhanh → FPT Play → VTV3 → Thêm lên tivi (FPT Play đã đăng nhập), bấm ô trên Home rồi gán một nút remote cho ô | Ô mang logo VTV3; bấm ô hay nút thì FPT Play phát VTV3; Thử nút báo được |
| Home sạch → Áp dụng (lên bản mới), rồi Nút remote → dò một nút → "SmartTube – tìm bằng giọng nói" → Lưu → Thử nút | SmartTube mở màn tìm kiếm; đã bật "Tìm kiếm bằng giọng nói tức thì" trong SmartTube thì mic bật ngay |
| Nối TiviKit vào một điện thoại (Gỡ lỗi không dây) | Hiện cảnh báo đỏ "không khai là tivi", nút dọn bị khoá |
| Chế độ tivi LG (nếu có tivi LG) | Ghép đôi được, remote và gõ chữ chạy |

## Báo kết quả

Gửi vào nhóm [Telegram](https://t.me/tivikit) hoặc [GitHub Issues](https://github.com/Circling5714/tivikit/issues):
bản TiviKit, model tivi, mục nào đạt / hỏng, ảnh màn lỗi nếu có. Mục đạt được đổi sang ✅ trong README.
