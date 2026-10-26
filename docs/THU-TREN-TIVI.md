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

## 3. Home sạch 3.0: các công tắc mới

| Thử | Đạt khi |
| --- | --- |
| Tuỳ chỉnh → Mở một app khi bật tivi → Áp dụng → Mở màn chọn app trên tivi → chọn SmartTube | Tắt nguồn rồi bật lại (rút điện), SmartTube tự mở |
| Bật thêm "Cả khi bật lại từ chế độ chờ" → Áp dụng | Tắt bằng remote, bật lại bằng remote: SmartTube tự mở |
| Tuỳ chỉnh → Luôn mở Home sạch khi bật tivi → Áp dụng | Đang ở HDMI / app, tắt rồi bật: khoảng 1 giây sau Home sạch hiện |
| Home → Kênh truyền hình trên màn chính (cần VTVgo bản tivi) → chọn VTV1, VTV3 → Áp dụng | Đầu dock có hai ô kênh có ảnh; bấm ô mở thẳng kênh trong VTVgo |
| Giao diện Kính mờ; Chữ giờ, ngày trắng sáng; ảnh riêng "vừa khung" | Hình đúng như xem trước; đổi lại giao diện khác không dính khoá của Kính mờ |
| Thời tiết: gõ "Osaka" | Có kết quả ở Nhật, chọn được, Home sạch hiện thời tiết Osaka |
| Remote → Giữ nút âm lượng để tăng / giảm liên tục (nên thử trên Sony) | Giữ nút âm lượng trên remote thật thì âm lượng chạy liên tục |

- **Hỏng thì:** tắt công tắc vừa bật, Áp dụng lại.

## 4. Khoá trẻ em: giới hạn giờ xem

- **Thử:** Khoá trẻ em → giới hạn giờ xem ngày thường và cuối tuần đều 6 phút → Lưu; xem tivi liên tục.
- **Đạt khi:** còn 5 phút (tức sau khoảng 1 phút xem) thì tivi báo trước; hết giờ thì tivi khoá tới nửa đêm, nhập PIN
  mở được 60 phút; báo cáo thời gian xem trên điện thoại có số phút đúng.
- **Hỏng thì:** mở bằng PIN, tắt giới hạn rồi Lưu.

## 5. Quản lý app, Làm mới tivi, Kiểm tra mạng, Cài đặt nhanh

| Thử | Đạt khi |
| --- | --- |
| Quản lý app → chọn một app ít dùng → Tắt, rồi Bật lại | App biến mất khỏi Home rồi hiện lại, mở được |
| Quản lý app → thẻ Đang chạy ngầm | Có danh sách app và RAM, mục "nên tắt" hợp lý |
| Làm mới tivi → Làm mới | RAM trống tăng; tivi vẫn chạy bình thường |
| Kiểm tra mạng → Kiểm tra | Có sóng, độ trễ, nhận xét; thẻ CH Play đúng tình trạng tài khoản |
| Cài đặt nhanh → hiệu ứng Nhanh, tự tắt 4 giờ | Cài đặt tivi hiện đúng giá trị mới; đặt lại được như cũ |

## 6. Các tính năng còn lại

| Thử | Đạt khi |
| --- | --- |
| Điều khiển → gõ tiếng Việt có dấu vào ô tìm kiếm YouTube | Chữ có dấu hiện đúng trên tivi |
| Điều khiển → Xem màn hình tivi → chạm vào một ô app trên hình | App đó mở trên tivi |
| Gửi ảnh, video → chọn một ảnh | Ảnh nằm trong Download/TiviKit trên tivi và mở xem được |
| Bộ cài đặt mẫu → tạo bộ có 1 app + DNS → Cài cho tivi này | App được cài, DNS đặt đúng |
| Sao lưu & chuyển máy → Lưu ra file → Nhập trên điện thoại khác | Tivi đã dọn, bộ cài đặt hiện đủ trên máy mới |
| Kênh nhanh TV360 với 2 ô trở lên | Mỗi ô mở đúng kênh của nó |
| Nối TiviKit vào một điện thoại (Gỡ lỗi không dây) | Hiện cảnh báo đỏ "không khai là tivi", nút dọn bị khoá |
| Chế độ tivi LG (nếu có tivi LG) | Ghép đôi được, remote và gõ chữ chạy |

## Báo kết quả

Gửi vào nhóm [Telegram](https://t.me/tivikit) hoặc [GitHub Issues](https://github.com/Circling5714/tivikit/issues):
bản TiviKit, model tivi, mục nào đạt / hỏng, ảnh màn lỗi nếu có. Mục đạt được đổi sang ✅ trong README.
