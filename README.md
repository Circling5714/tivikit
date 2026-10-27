# TiviKit

**Bộ công cụ cho Android TV.** App Android dọn quảng cáo và chăm sóc Android TV / Google TV qua Wi-Fi: tắt quảng cáo, theo dõi, app rác của hãng, thay màn Home sạch, rồi cài app, điều khiển, gửi ảnh, hẹn giờ bật tắt, khoá trẻ em… Không root, không gỡ gì (chỉ tắt), khôi phục một chạm. Có thêm chế độ điều khiển tivi LG webOS.

**The Android TV toolkit.** An Android app that cleans up and looks after Android TV / Google TV over Wi-Fi: it switches off the maker's ads, tracking and junk, swaps in a clean Home, then installs apps, works as a remote, sends photos, runs on/off timers and a kids lock. No root, nothing uninstalled (only disabled), one-tap restore. LG webOS TVs get a remote mode too.

> **TiviKit phát triển tiếp từ CleanTV 2.8.2 (kèm các bản sửa đưa về đến CleanTV 3.0.0), và nhiều tính năng mới chưa được thử trên tivi thật.** Mỗi tính năng bên dưới ghi rõ ✅ đã thử hay ⚠️ chưa thử; xem thêm [Tình trạng thử nghiệm](#tình-trạng-thử-nghiệm).
>
> *TiviKit builds on CleanTV 2.8.2 (with fixes ported up to CleanTV 3.0.0), and many new features have not been tried on a real TV yet: see [Test status](#test-status).*

[Tải APK bản mới nhất](https://github.com/Circling5714/tivikit/releases/latest) · [Nhật ký các bản](https://github.com/Circling5714/tivikit/releases) · [Nhóm Telegram](https://t.me/tivikit) · [Ủng hộ tác giả](#liên-hệ-và-ủng-hộ) · [Tiếng Việt](#tiếng-việt) · [English](#english)

---

## Tiếng Việt

✅ đã thử trên tivi thật · ⚠️ chưa thử trên tivi thật

### Dọn tivi và Home sạch

Phần này lấy từ CleanTV 2.8.2, bản đã phát hành công khai, kèm các bản sửa đưa về đến 3.0.0; TiviKit giữ nguyên cách dọn, chỉ đổi giao diện, tên gói và lớp kết nối. ✅ TiviKit 1.0 đã dọn tivi và cài Home sạch trên box Tanix TX3 Mini (Android 9). ⚠️ Khôi phục chưa thử lại với TiviKit.

- **Kết nối tivi qua Wi-Fi** bằng ADB: quét mạng hoặc gõ IP; Google TV đời mới ghép bằng mã 6 số (Gỡ lỗi không dây), app tự dò cổng. Hướng dẫn 7 bước có hình trong app.
- **So với 316 danh sách đã thử** của cộng đồng CleanYourTV (nhiều hãng: TCL, Sony, Xiaomi, Philips, Hisense, Skyworth, onn, Chromecast, máy chiếu…), tự cập nhật hằng tuần. Chưa khớp mẫu thì gợi ý mẫu gần giống; tivi lạ dùng luật chung an toàn.
- **Chỉ tắt, không gỡ** quảng cáo, theo dõi, app rác. Không đụng cổng HDMI, remote, bàn phím, CH Play, gói đang giữ Wi-Fi, gói lõi Android và Home đang dùng. Google Home / cast và tìm kiếm giọng nói không bao giờ tích sẵn.
- **Home sạch** (launcher CleanYourTV kèm bản vá của CleanTV): đồng hồ, lịch âm có can chi, thời tiết theo 3.321 phường/xã, dock 8 app, cụm nút Máy chiếu · Mạng · Màn hình chờ · Cài đặt; 8 giao diện, ảnh nền riêng từ điện thoại hoặc từ tivi / USB, 89 tuỳ chọn Nâng cao.
- ⚠️ **Home sạch 3.0** (CleanYourTV 3.0 kèm bản vá của CleanTV 3.0): ô kênh truyền hình trên dock mở thẳng kênh trong app VTVgo (Home sạch vẫn hiện các ô đã chọn ở bản 1.2 – 1.4; nay làm ô kênh VTVgo ở Kênh nhanh), luôn mở Home sạch khi bật tivi, mở app đã chọn cả khi bật lại từ chế độ chờ, Home sạch nói tiếng Việt trên tivi Xiaomi bản Trung Quốc; cùng các bản sửa của CleanYourTV (nút nguồn trên TCL, nút YouTube / Netflix của remote TCL, âm lịch, ít RAM hơn…). Home sạch nay ký bằng khoá TiviKit: lần đầu cập nhật, app gỡ Home sạch cũ rồi cài lại và tự đặt lại giao diện; app chọn ở "Mở khi khởi động" trên tivi thì chọn lại.
- ⚠️ Thêm cho Home sạch (từ CleanTV 2.9 – 3.0): mở một app khi bật tivi (chọn app ngay trên tivi), giữ nút âm lượng để tăng / giảm liên tục, chữ giờ và ngày trắng sáng, giao diện Kính mờ (nền theo màu app đang chọn, dock kính mờ, ô icon đều), ảnh nền riêng vừa khung với viền mờ và nét hơn, thời tiết cho cả thành phố ngoài Việt Nam. Đổi giao diện xong, lần sau mở lại app nhớ đúng giao diện đang dùng.
- **Nút Home về đúng Home sạch**: app bấm thử nút Home thật; tivi không cho tắt Home gốc thì Home sạch tự mở đè lên. **Nút trên remote vẫn chạy** (YouTube, Netflix, Prime, Cài đặt) theo bảng phím của từng mẫu tivi.
- Máy chiếu (Formovie, XGIMI, JMGO…) ghim màn lấy nét / chỉnh hình vào nút Máy chiếu. Xiaomi bản Trung Quốc có Home sạch riêng. Bo mạch CVTE giữ Home hãng.
- ⚠️ Các bản sửa đưa về từ CleanTV 2.8.3 – 3.0.0:
  - Máy không khai là tivi (điện thoại, máy tính bảng) thì khoá nút dọn tới khi bạn xác nhận.
  - Sony 2015 (Android 8) không tích sẵn các gói nằm sau màn Cài đặt và mặc định giữ ADB.
  - Kiểm tra nút Home chờ rồi thử lại khi còn app khác ở trước, tự đóng hộp "chọn Home", tắt launcher tự cài đang tranh Home (có ghi vào sao lưu).
  - Tivi Xiaomi tự gỡ Home sạch thì app cài lại bản mang tên Xiaomi cho phép.
  - Android 8 gửi ảnh nền và file giao diện có đường dự phòng và kiểm cỡ file sau khi gửi.
- **Sao lưu trước mọi thay đổi**, trên điện thoại và trên tivi; **Khôi phục** một chạm, đổi điện thoại vẫn khôi phục được. File sao lưu trên tivi dùng chung với CleanTV, nên tivi đã dọn bằng CleanTV vẫn khôi phục được bằng TiviKit.
- **Tivi tự canh gác**: đang thay Home mà rớt Wi-Fi / hết pin / tắt app thì 3 phút sau tivi tự bật lại Home gốc. Có **kịch bản khôi phục** cho máy tính và **xem trước lệnh** sẽ chạy trên tivi. Rời app thì tivi tự tắt ADB (có tuỳ chọn giữ).
- **Xem màn hình tivi**: chụp màn hình tivi đang hiện, lưu hoặc chia sẻ. **Tivi đã dọn**: nhớ từng tivi, khôi phục lại lúc nào cũng được.

### Tiện ích

- **Cài app**
  - ✅ Cài file APK từ điện thoại; truyền từng khối lớn, rớt Wi-Fi giữa chừng thì tự nối lại và gửi lại một lần. Đã cài được trên tivi thật; tốc độ của bản truyền khối lớn chưa đo.
  - ⚠️ Gói chia nhỏ `.apks`, `.xapk`, `.apkm`; cài từ đường link.
  - ✅ 40 app gợi ý trong 7 nhóm (YouTube và video, truyền hình / phim Việt Nam, anime và phim châu Á có bản quyền, phim nhạc quốc tế, phim từ máy chủ trong nhà, game, tiện ích): tải bản đúng chip của tivi từ GitHub hoặc trang chính thức; app chỉ có trên CH Play thì mở trang CH Play trên tivi. Đã cài SmartTube từ GitHub lên box TX3; các nguồn khác chưa thử.
  - ⚠️ Cập nhật app đã cài: so phiên bản với bản mới nhất, cập nhật một chạm.
- ⚠️ **Điều khiển + gõ chữ** (chỉ trên điện thoại): remote trên điện thoại, gõ mật khẩu, tìm kiếm. Gõ tiếng Việt có dấu nhờ bộ gõ phụ `vn.tivikit.go`, tự cài lên tivi lần đầu gửi chữ có dấu; ô nhập không nhận bộ gõ thì gửi chữ không dấu.
- ✅ **Xem màn hình tivi trực tiếp** ngay trên màn Điều khiển, cách làm của [scrcpy](https://github.com/Genymobile/scrcpy): tivi tự quay màn hình bằng bộ mã hoá H.264 và gửi qua ADB, trễ chưa tới nửa giây; khung hình ghim trên cùng, trượt xuống bấm remote vẫn thấy tivi; chạm lên hình là bấm đúng chỗ đó, phím remote cũng nhạy hơn. Phim có bản quyền (Netflix…) và cổng HDMI ra màn đen. Tivi không quay được video thì app dùng ảnh chụp làm mới 1,5 giây một lần. Đã xem được trên tivi thật từ điện thoại (bản thử 26/10/2026).
- ✅ **Nút remote**: gán một nút thật trên remote (VD nút YouTube) để mở app khác như SmartTube. App đọc mã nút lúc bạn bấm rồi ghi vào Home sạch, nên cần cài Home sạch trước.
  - ✅ Gán nút mở thẳng tìm kiếm bằng giọng nói của SmartTube (chọn "SmartTube – tìm bằng giọng nói"), đã thử trên tivi Skyworth (Android 11). Cần Home sạch bản mới (tivi có bản cũ thì mở Home sạch, bấm Áp dụng một lần); để mic bật ngay, trong SmartTube bật Cài đặt → Tìm kiếm → Tìm kiếm bằng giọng nói tức thì.
- **Kênh nhanh**: tối đa 6 ô, mỗi ô mở thẳng một kênh trong app xem truyền hình, gán được nút remote riêng, Hẹn giờ mở được. Mỗi ô là một app rất nhỏ TiviKit cài lên tivi.
  - TV360: VTV1, VTV3, HTV7… theo số kênh của TV360 (tivi cần có app TV360). ✅ Một ô đã thử; ⚠️ nhiều ô (tới 6) chưa thử.
  - ⚠️ VTVgo: VTV1–9, đài địa phương, VTVcab… từ danh sách kênh của VTVgo (tivi cần có app VTVgo bản tivi). Ô kênh VTVgo kiểu cũ trên Home sạch (không gán được nút remote) chuyển được sang ô Kênh nhanh.
  - ⚠️ Logo kênh trên ô: tải ảnh kênh của VTVgo lúc lưu ô rồi gửi lên tivi (app không đóng sẵn logo của đài); ô TV360 dùng chung khi VTVgo có kênh cùng tên (VTV1–9, HTV9, Hà Nội 1, ANTV, QPVN…). Cần Home sạch bản mới: tivi đang có bản cũ thì mở Home sạch, bấm Áp dụng một lần.
- ✅ **Hẹn giờ**: bật / tắt tivi và mở app theo lịch; báo trước 60 giây khi sắp tắt, cho hoãn 30 phút. Có nút thử "tivi tự bật sau 2 phút"; tivi ngủ sâu hoặc rút điện thì không tự bật được.
- **Khoá trẻ em** (từ CleanTV): hẹn giờ khoá tivi theo ngày trong tuần, mã PIN 4 số mở 60 phút, che cả HDMI và chặn remote.
  - ⚠️ Mới: giới hạn giờ xem mỗi ngày (ngày thường / cuối tuần), báo trước 5 phút, hết giờ thì khoá đến nửa đêm; báo cáo thời gian xem 7 ngày và theo từng app.
- ⚠️ **Gửi ảnh, video** (chỉ trên điện thoại): gửi file vào thư mục `Download/TiviKit` trên tivi rồi mở xem.
- ⚠️ **Bộ cài đặt mẫu**: lưu một bộ app (file APK hoặc app gợi ý) kèm DNS chặn quảng cáo, rồi cài một lượt cho tivi đang nối hoặc lần lượt cho nhiều tivi.

### Chăm sóc tivi

- ⚠️ **Làm mới tivi**: RAM, CPU, nhiệt độ chip, bộ nhớ, thời gian tivi chạy liền; app nào đang ăn CPU và tivi quá tải vì việc gì (VD CH Play đang cập nhật dồn); một chạm đóng app nằm nền, chỉ xoá cache khi bộ nhớ gần đầy; chạy liền quá 7 ngày thì gợi ý khởi động lại.
- ⚠️ **Kiểm tra mạng**: sóng Wi-Fi, băng tần, độ trễ, kèm nhận xét cách sửa khi YouTube giật; rồi xem CH Play và tài khoản Google: gói Google bị tắt, giờ tivi lệch, tài khoản cần đăng nhập lại (lúc CH Play báo nhầm là lỗi Wi-Fi).
- ⚠️ **Cài đặt nhanh**: hiệu ứng nhanh hơn, tự tắt sau 4 giờ không dùng, màn hình chờ, tiếng bấm remote.
- ⚠️ **Quản lý app**: dung lượng, xoá cache, gỡ, lưu APK; tắt / bật lại app (VD tắt YouTube gốc khi đã dùng SmartTube); thẻ Đang chạy ngầm cho thấy RAM từng app chạy nền và chỉ ra app nên tắt (VD microG không còn app nào dùng, trình cập nhật ROM của box).
- ✅ **DNS chặn quảng cáo** (Android 9 trở lên): AdGuard, AdGuard Gia đình, NextDNS hoặc máy chủ DNS-over-TLS tự nhập, chặn cho mọi app trên tivi; đặt xong app kiểm tra lại, không chạy thì trả về như cũ. Đã thử NextDNS trên box TX3 (Android 9).

### Kết nối

- ⚠️ **Tự nối lại**: rớt kết nối thì tự nối, mở app là tự nối tivi lần trước; tìm tivi chắc hơn khi Wi-Fi của tivi ngủ hoặc tivi đổi IP.
- ⚠️ **Khởi động lại** tivi (chờ tivi lên rồi tự nối lại) và **Tắt ADB** ngay trên thẻ tivi ở màn chính.

### Tivi LG (webOS)

- ⚠️ Không cần root, không cần bật Gỡ lỗi (lần đầu tivi hỏi cho phép kết nối): điều khiển, gõ tiếng Việt có dấu, mở app, mở YouTube / trang web, chọn nguồn HDMI, tắt màn hình, tắt tivi, bật tivi qua mạng (Wake-on-LAN), hiện thông báo trên tivi.
- ⚠️ Tivi đã root (Homebrew Channel): app tự nhận ra và cho cài [LG TV Vibehack](https://github.com/hoangvya19/lg-tv-webos-vibehack) (màn hình chính kiểu Apple TV) qua SSH.
- Tivi Samsung (Tizen) chưa hỗ trợ.

### Nâng cao

- ⚠️ **Set App Link**: chọn app mở link của từng trang trên tivi (YouTube, FPT Play, VieON, Netflix, Spotify… hoặc trang tự thêm), VD link YouTube gửi qua Telegram, Zalo, trình duyệt mở thẳng bằng SmartTube thay vì hỏi chọn app. Đặt xong app hỏi lại tivi để báo đã nhận chưa; trả về như cũ bằng "Hỏi mỗi lần". Android 11 trở xuống đặt theo app (`pm set-app-link`), Android 12 trở lên theo lựa chọn của người dùng (`pm set-app-links-user-selection`). Lệnh đã thử tay trên tivi Skyworth (Android 11): mọi dạng link YouTube mở thẳng bằng SmartTube; màn hình trong app và Android 12 trở lên chưa thử.
- ⚠️ **Dòng lệnh ADB**: gõ lệnh shell chạy thẳng trên tivi như `adb shell` trên máy tính; kết quả hiện dần, bấm Dừng được, `cd` được giữ cho lệnh sau, dán lệnh có sẵn `adb shell` ở đầu thì app tự bỏ. Gần 200 lệnh mẫu có giải thích, xếp từ hay dùng tới ít dùng (thông tin tivi, app, tăng tốc, mở màn cài đặt bị giấu, cấp quyền cho app, mạng, remote, màn hình chờ, file, nhật ký…); lệnh có chỗ trống như `<gói>` thì điền trong hộp, chọn được gói đang có trên tivi. Lệnh nguy hiểm (tắt / gỡ gói tivi cần, xoá cả thư mục, tắt Wi-Fi hay ADB, khởi động vào recovery…) được hỏi lại, loại nặng phải tích xác nhận; lệnh tắt gói được ghi vào bản sao lưu để Khôi phục bật lại; đổi cài đặt thì in giá trị cũ để trả lại. Lệnh chỉ nhận từ ô gõ trong app; lịch sử lệnh chỉ lưu trên điện thoại.

### Khác

- ⚠️ **Ảnh nền riêng** cho Home sạch: kéo dời, chụm phóng trên khung xem trước, hoặc để vừa khung không cắt (hai bên là chính ảnh làm mờ).
- ⚠️ **Sao lưu & chuyển máy**: lưu bản sao lưu tivi, bộ cài đặt mẫu, danh sách tivi (và khoá ADB nếu muốn) ra một file zip, nhập trên điện thoại khác.
- Chạy trên **điện thoại Android** hoặc **ngay trên tivi** (cho người dùng iPhone). Giao diện kiểu iOS, sáng / tối theo điện thoại (trên tivi luôn tối), song ngữ Việt / Anh.
- ⚠️ **Tự cập nhật** từ GitHub Releases: Android 12 trở lên tự tải và cài bản mới lúc không mở app, máy cũ hơn chạm một lần để cài; tắt ở mục Cập nhật TiviKit. Không gửi số liệu nào (xem [Quyền riêng tư](#quyền-riêng-tư)).

### Tình trạng thử nghiệm

- ✅ Ngày 26/10/2026, TiviKit 1.0 (gói `vn.tivikit`) trên box Tanix TX3 Mini (Android 9): dọn tivi, cài Home sạch, cài SmartTube từ danh sách gợi ý, DNS chặn quảng cáo (NextDNS).
- ✅ Ngày 26/10/2026, bản thử của 1.3.0: xem màn hình tivi trực tiếp trên điện thoại, khung ghim khi trượt xuống. Phần quay màn hình cũng đã chạy riêng trên box TX3 Mini (bộ mã hoá Amlogic, khung đầu sau ~0,5 giây).
- ✅ Ngày 25/10/2026 trên một tivi Skyworth, ở bản thử trước khi đổi tên gói sang `vn.tivikit`: cài file APK, Nút remote, Kênh nhanh TV360 (một ô), Hẹn giờ.
- ✅ Ngày 27/10/2026 trên tivi Skyworth SWTV-22AE (Google TV, Android 11): cập nhật Home sạch bằng Áp dụng, gán một nút trên remote mở thẳng tìm kiếm bằng giọng nói của SmartTube.
- ⚠️ Việc tự chuyển ô Kênh nhanh, lịch Hẹn giờ và nút remote đã gán từ bản thử cũ (`com.icm.cleantv`) sang gói mới chưa thử.
- ⚠️ Chế độ tivi LG chưa thử trên tivi LG nào.
- Khôi phục và khoá trẻ em gốc đã chạy trong CleanTV 2.8.2; với TiviKit thì chưa thử lại.
- Mỗi lần dựng đều chạy unit test của app và các app phụ, nhưng unit test không thay được việc thử trên tivi thật.
- Danh sách thử trên tivi thật, xếp theo rủi ro: [docs/THU-TREN-TIVI.md](docs/THU-TREN-TIVI.md).

### Dựng từ mã nguồn

Cần JDK 17 và Android SDK (compileSdk 35).

```bash
scripts/dung-khoa.sh       # Khoá trẻ em   → app/src/main/assets/khoa.apk
scripts/dung-kenh.sh       # Kênh nhanh    → kenh.apk, kenh2.apk … kenh6.apk
scripts/dung-hen-gio.sh    # Hẹn giờ       → hengio.apk
scripts/dung-go.sh         # Gõ tiếng Việt → go.apk
./gradlew :app:testReleaseUnitTest :app:assembleRelease
```

- Các app phụ, Home sạch (`tvhome.apk`, `tvhome-xiaomi.apk`) và dữ liệu mẫu tivi (`du-lieu.json`) đã có sẵn trong `app/src/main/assets`, nên chỉ sửa app thì chạy dòng cuối là đủ. `scripts/dung-launcher.sh` tải CleanYourTV về `upstream/`, lấy đúng commit ghi trong `app/src/main/assets/upstream-commit.txt`, áp `patches/launcher-vgs.patch`, dựng Home sạch ký bằng khoá TiviKit và làm lại danh sách Nâng cao. Dữ liệu mẫu tivi do workflow Cập nhật mẫu tivi lo.
- Các script tự đặt `JAVA_HOME` theo JDK 17 của Homebrew trên macOS; máy khác thì đặt `JAVA_HOME` trước.
- Trên Windows, clone với `core.autocrlf=false`: để Git đổi kiểu xuống dòng thì bản vá launcher không áp được và các file trong assets bị đổi.
- Khoá ký đặt trong `ky.properties` (không nằm trong git), dùng chung cho app và các app phụ; thiếu file này thì bản release ký bằng khoá debug. Giữ một khoá cho mọi bản: đổi khoá thì không cài đè được lên TiviKit và các app phụ đã cài trên tivi.
- Số phiên bản ở `version.properties` (versionCode = lớn×10000 + tính năng×100 + vá).
- **Phát hành** bằng GitHub Actions (Actions → Phát hành → Run workflow, chọn bản vá / tính năng / lớn): workflow tự tăng số bản, chạy unit test, dựng và ký APK, viết nhật ký từ tiêu đề commit (`scripts/phat_hanh.py`; commit bắt đầu bằng "Sửa" vào mục Sửa lỗi; bỏ commit có tiêu đề bắt đầu bằng "Nội bộ:", "Tài liệu:", "README:", "CI:"; dòng `EN: …` trong thân commit là bản tiếng Anh của mục đó), gắn tag rồi đăng GitHub Release hai thứ tiếng kèm APK, `.sha256` và `cap-nhat.json` cho tính năng tự cập nhật. Khoá ký nằm trong secrets của môi trường `phat-hanh` (chờ chủ kho duyệt) và `phat-hanh-tu-dong` (không cần duyệt, chỉ dùng khi từ bản trước, thứ vào APK chỉ đổi ở dữ liệu mẫu tivi — `phat_hanh.py chi-du-lieu` kiểm tra). Bản có đổi code luôn chờ duyệt.
- **Mẫu tivi tự cập nhật**: workflow Cập nhật mẫu tivi chạy mỗi thứ Hai (hoặc bấm chạy tay), dựng lại `du-lieu.json` từ CleanYourTV mới nhất (`scripts/cap_nhat_mau.py`), sửa số mẫu, chạy unit test (có bài thử chạy qua mọi mẫu: không mẫu nào được tích sẵn gói tivi cần để chạy). Qua thì commit và tự phát hành (còn code chưa phát hành thì chờ duyệt); không qua, hoặc bỏ quá 5 mẫu một lần, thì mở pull request để xem. Kho 45 ngày chưa có commit thì workflow tạo một commit nhỏ (`.github/giu-lich.txt`), vì GitHub tắt lịch chạy của kho 60 ngày không hoạt động.

### Kiểm tra bản cài

Mọi bản trên [Releases](https://github.com/Circling5714/tivikit/releases) đều ký bằng cùng một chứng chỉ (`CN=CleanTV Rebuild, O=Local, C=VN`), dấu SHA-256:

```text
ec4c282ecefa87e7e959d44de052ccb047a8164c383ff39341d910d85af6f653
```

```bash
apksigner verify --print-certs TiviKit-1.1.0.apk    # phải ra đúng dấu trên
```

TiviKit (`vn.tivikit`) là app riêng: cài song song được với CleanTV và không cài đè lên CleanTV (khác gói, khác khoá ký).

### Quyền riêng tư

- TiviKit **không gửi số liệu sử dụng**. Bộ đếm ẩn danh, nút gửi danh sách gói và các liên kết ra trang web của CleanTV đều tắt khi dựng (`GUI_SO_LIEU`, `LIEN_KET_VGS` trong `app/build.gradle.kts`). Báo lỗi và Gửi danh sách cho CleanYourTV chỉ mở trang GitHub điền sẵn trong trình duyệt: bạn xem lại rồi tự bấm gửi, app không gửi gì.
- Điện thoại chỉ ra Internet để tự cập nhật TiviKit (hỏi GitHub Releases khoảng 12 giờ một lần và khi mở app, tải APK bản mới, kiểm tra SHA-256 rồi mới cài), khi bạn tải app trong mục Cài app (GitHub, VideoLAN, Kodi hoặc đường link bạn nhập), khi kiểm tra bản mới của app đã cài (GitHub), khi tìm nơi xem thời tiết ngoài Việt Nam (dịch vụ tìm địa danh của Open-Meteo, chỉ gửi chữ bạn gõ) và khi bạn chọn ô kênh truyền hình (danh sách kênh và ảnh kênh từ VTVgo).
- Lệnh điều khiển đi thẳng từ điện thoại tới tivi trong mạng nhà: ADB với Android TV, kết nối điều khiển của LG với tivi LG.
- Trên tivi: Home sạch lấy thời tiết từ Open-Meteo; bật DNS chặn quảng cáo thì tên miền tivi truy cập đi qua nhà cung cấp bạn chọn (AdGuard, NextDNS…); tivi LG đã root tải script cài Vibehack từ GitHub khi bạn bấm cài.

### Cấu trúc

| Thư mục | Nội dung |
| --- | --- |
| `app/` | App TiviKit (Kotlin + Jetpack Compose). Package và mã nguồn `vn.tivikit` (tên biến tiếng Việt) |
| `app/.../adb/` | Kết nối ADB (libadb-android) và các thao tác trên tivi: dọn, sao lưu, khôi phục, Home sạch, cài app, remote, gửi file, mạng |
| `app/.../dulieu/` | Mẫu tivi, luật chọn gói (`KeHoach.kt`), app gợi ý (`GoiY.kt`), DNS, kênh TV360, hẹn giờ, bộ cài đặt mẫu, sao lưu |
| `app/.../ui/` | Các màn hình |
| `app/.../lg/` | Tivi LG webOS: điều khiển qua mạng, SSH khi tivi đã root, cài Vibehack |
| `khoa/` | Khoá trẻ em `vn.tivikit.khoa`: dịch vụ trợ năng che màn hình theo giờ, giới hạn giờ xem, mở bằng mã PIN |
| `kenh/` | Kênh nhanh TV360, mỗi ô một app (`vn.tivikit.kenh`, `kenh2` … `kenh6`) |
| `hengio/` | Hẹn giờ bật / tắt tivi, mở app (`vn.tivikit.hengio`) |
| `go/` | Bộ gõ phụ để gõ tiếng Việt có dấu từ điện thoại (`vn.tivikit.go`) |
| `patches/launcher-vgs.patch` | Phần CleanTV sửa trên launcher CleanYourTV (thời tiết, font Lexend, cụm nút, máy chiếu, màn chờ, ô kênh `link_tiles`, logo ô Kênh nhanh qua `app_banners` trỏ tới ảnh `kenh-*.jpg`, nút remote mở link vào app qua đích `intent:` trong `app_keys`, mở app khi bật từ chế độ chờ…), đã chuyển lên commit ghi trong `upstream-commit.txt` |
| `scripts/` | Dựng launcher, dựng các app phụ, dựng dữ liệu mẫu tivi (`lam-du-lieu.py`, `cap_nhat_mau.py`), vẽ ảnh nền giao diện TiviKit (`AnhNenTiviKit.java`), tải máy chủ scrcpy có kiểm SHA-256 (`tai-scrcpy.sh`), phát hành (`phat_hanh.py`) |
| `.github/workflows/` | Phát hành (dựng, ký, đăng GitHub Release), Cập nhật mẫu tivi (hằng tuần từ CleanYourTV) và Kiểm tra (unit test mỗi lần đẩy code) |

### Liên hệ và ủng hộ

- **Nhóm Telegram:** [t.me/tivikit](https://t.me/tivikit): hỏi đáp, góp ý tính năng, báo tivi nào chạy được hay không.
- **Báo lỗi:** [GitHub Issues](https://github.com/Circling5714/tivikit/issues). Trong app: Góp ý / báo lỗi → Báo lỗi trên GitHub, app điền sẵn bản TiviKit, điện thoại, tivi đang nối và lỗi vừa gặp để bạn xem lại rồi gửi.
- **Ủng hộ tác giả:** TiviKit miễn phí, không quảng cáo. Thấy hữu ích thì bạn có thể mời tác giả một ly cà phê:

  | Ngân hàng | Số tài khoản | Chủ tài khoản | Nội dung |
  | --- | --- | --- | --- |
  | MB Bank | `070991988888` | CAO MINH THI | Ung ho TiviKit |

  Trong app: màn chính → Ủng hộ tác giả, mỗi dòng có nút chép.
- ⚠️ **Gửi danh sách cho CleanYourTV:** dọn tivi xong và dùng vài ngày không lỗi, vào màn chính → Gửi danh sách cho CleanYourTV. App soạn `debloat.txt` và `tv.json` đúng định dạng `examples/` của [CleanYourTV](https://github.com/Gathin23/cleanyourtv) từ các gói đã tắt (đang nối tivi đó thì đối chiếu lại: bỏ gói tivi đã bật lại, bỏ app bạn tự cài), rồi mở trang tạo issue "My TV" của họ điền sẵn. Không dùng GitHub thì chép hoặc chia sẻ vào nhóm Telegram. TiviKit tự lấy danh sách mới về mỗi tuần.

---

## English

✅ tried on a real TV · ⚠️ not tried on a real TV yet

### Cleaning and the clean Home

From CleanTV 2.8.2, a publicly released app, with fixes ported up to 3.0.0; TiviKit keeps the clean-up logic and only changes the interface, package names and connection layer. ✅ TiviKit 1.0 cleaned a Tanix TX3 Mini (Android 9) and installed the clean Home. ⚠️ Restore hasn't been re-tested with TiviKit.

- **Connects over Wi-Fi** with ADB: scan or type the IP; newer Google TVs pair with a 6-digit code and the app finds the port. A 7-step picture guide is built in.
- **Matches 316 tested lists** from the CleanYourTV community (many brands), updated weekly; suggests close models; unknown TVs get safe general rules.
- **Disables, never uninstalls** ads, tracking and junk. Leaves HDMI inputs, the remote, keyboards, Play Store, the Wi-Fi owner, core Android packages and the Home in use alone. Google Home / casting and voice search are never pre-ticked.
- **Clean Home** (the CleanYourTV launcher with CleanTV's patches): clock, Vietnamese lunar date, ward-level weather, an 8-app dock, 8 themes, your own wallpaper, 89 Advanced options. The Home key lands on it, and remote keys keep working from each model's key map.
- ⚠️ **Clean Home 3.0** (CleanYourTV 3.0 with CleanTV 3.0's patches): channel tiles on the dock that open a channel straight in the VTVgo app (the clean Home still shows the ones picked in 1.2 – 1.4; VTVgo channel tiles are now made under Quick channel), the clean Home at every power-on, the picked app also when waking from standby, a Vietnamese clean Home on China-market Xiaomi TVs; plus CleanYourTV's fixes (the power key on TCL, the TCL remote's YouTube / Netflix keys, the lunar date, less memory…). The clean Home is now signed with TiviKit's key: on the first update the app removes the old one, installs the new one and puts your look back; pick the "Open on start" app on the TV again.
- ⚠️ New for the clean Home (from CleanTV 2.9 – 3.0): open an app at power-on (picked on the TV), hold volume to keep raising / lowering, a bright white clock and date, the Frosted look (backdrop in the focused app's colour, frosted dock, even icon tiles), your own wallpaper fitted with blurred edges and sharper, weather for cities outside Vietnam. The app remembers the look in use the next time it connects.
- **Backs up before any change**, on the phone and on the TV; one-tap **Restore**. The TV-side backup is shared with CleanTV. If the link drops while Home is being replaced, the TV restores its stock Home by itself. A restore script for a computer and a command preview are included; ADB switches off when you leave the app.
- **TV screenshot** and a list of **cleaned TVs**.
- ⚠️ Fixes brought over from CleanTV 2.8.3 – 3.0.0:
  - A device that doesn't report itself as a TV (a phone, a tablet) can't be cleaned until you confirm it is one.
  - 2015 Sony sets (Android 8) never pre-tick the packages behind Settings and keep ADB on by default.
  - The Home-button check waits and retries while another app is in front, closes the system "pick a Home" dialog, and disables user-installed launchers competing for Home (recorded in the backup).
  - A Xiaomi TV that removes the clean Home gets it again under a name Xiaomi allows.
  - Wallpaper and theme files reach Android 8 TVs by a fallback route and are size-checked after sending.

### Tools

- **Install apps**
  - ✅ APK files from the phone, sent in large chunks, reconnecting and resending once if Wi-Fi drops. Installed fine on a real TV; the speed of the large-chunk version hasn't been measured.
  - ⚠️ Split packages (`.apks`, `.xapk`, `.apkm`) and installs from a link.
  - ✅ 40 suggested apps in 7 groups, downloaded for the TV's chip from GitHub or official sites; Play-only apps open their Play Store page on the TV. SmartTube from GitHub was installed on the TX3 Mini; other sources not yet.
  - ⚠️ App updates: compares versions with the latest release, one-tap update.
- ⚠️ **Remote + typing** (phone only): a remote on the phone, passwords and searches. Vietnamese with accents goes through a helper keyboard (`vn.tivikit.go`); fields that don't accept it get plain letters.
- ✅ **Live TV screen** on the remote screen, the [scrcpy](https://github.com/Genymobile/scrcpy) way: the TV encodes its screen in H.264 and streams it over ADB, under half a second behind; the picture stays pinned at the top while you scroll the remote; tap the picture to press that spot, and remote keys respond faster. Copy-protected films (Netflix…) and HDMI inputs show black. A TV that can't encode video falls back to screenshots refreshed every 1.5 s. Tried from a phone with a real TV (test build, 26 Oct 2026).
- ✅ **Remote buttons**: make a real remote button (e.g. YouTube) open another app such as SmartTube. Needs the clean Home.
  - ✅ A button can open SmartTube's voice search directly (pick "SmartTube – voice search"); tried on a Skyworth TV (Android 11). Needs the new clean Home (on a TV with an older one, open Clean Home and press Apply once); for the mic to start at once, turn on Settings → Search → Instant voice search in SmartTube.
- **Quick channel**: up to 6 tiles, each opening one channel straight in a TV app, each able to take its own remote button and be opened by the Timer. Each tile is a tiny app TiviKit installs on the TV.
  - TV360: VTV1, VTV3, HTV7… by TV360's channel number (the TV needs the TV360 app). ✅ One tile tried; ⚠️ more tiles (up to 6) not yet.
  - ⚠️ VTVgo: VTV1–9, local stations, VTVcab… from VTVgo's channel list (the TV needs the VTVgo app for TV). Old-style VTVgo tiles on the clean Home (no remote button) can be moved to Quick channel tiles.
  - ⚠️ Channel logos on the tiles: VTVgo's channel picture is downloaded when a tile is saved and sent to the TV (no station logos are bundled); TV360 tiles share it when VTVgo has a channel of the same name (VTV1–9, HTV9, Hà Nội 1, ANTV, QPVN…). Needs the new clean Home: on a TV with an older one, open Clean Home and press Apply once.
- ✅ **Timer**: turn the TV on / off and open apps on a schedule, with a 60-second warning and a 30-minute snooze, plus a "turn on in 2 minutes" test. A TV in deep standby can't be woken.
- **Kids lock** (from CleanTV): weekday lock periods, a 4-digit PIN unlocks for 60 minutes, covers HDMI and blocks the remote.
  - ⚠️ New: daily viewing limits (weekdays / weekends), a 5-minute warning, a lock until midnight when time is up, and a 7-day, per-app viewing report.
- ⚠️ **Send photos, videos** (phone only) to `Download/TiviKit` on the TV and open them.
- ⚠️ **Setup sets**: a saved set of apps plus ad-blocking DNS, applied to the connected TV or to several TVs in turn.

### TV care

- ⚠️ **Refresh the TV**: RAM, CPU load, chip temperature, storage and uptime; which processes use the CPU and why the TV is overloaded (e.g. the Play Store catching up on updates); close background apps in one tap, clear caches only when storage is nearly full, suggest a reboot after 7 days up.
- ⚠️ **Network check**: Wi-Fi signal, band and latency, with advice when YouTube stutters; then the Play Store and Google account: disabled Google packages, a wrong clock, an account that needs signing in again (when the Play Store wrongly blames the Wi-Fi).
- ⚠️ **Quick settings**: faster animations, auto-off after 4 idle hours, screensaver, remote click sounds.
- ⚠️ **Manage apps**: size, clear cache, uninstall, save the APK, disable / re-enable (e.g. turn off the stock YouTube when using SmartTube); a Running in the background card shows each app's RAM and flags the ones worth disabling (e.g. microG that no app uses, the box's firmware updater).
- ✅ **Ad-blocking DNS** (Android 9+): AdGuard, AdGuard Family, NextDNS or your own DNS-over-TLS server, for every app on the TV; checked after setting and reverted if it fails. NextDNS tried on the TX3 Mini (Android 9).

### Connection

- ⚠️ **Auto-reconnect** when the link drops or the app opens; more reliable discovery when the TV's Wi-Fi sleeps or its IP changes.
- ⚠️ **Reboot** (reconnects once the TV is back) and **Turn off ADB** right on the TV card.

### LG TVs (webOS)

- ⚠️ No root and no debugging needed (the TV asks once): remote, typing with accents, apps, YouTube / web pages, HDMI inputs, screen off, power off, Wake-on-LAN power on, on-screen messages.
- ⚠️ Rooted TVs (Homebrew Channel) are detected and can install [LG TV Vibehack](https://github.com/hoangvya19/lg-tv-webos-vibehack) (an Apple TV-style home screen) over SSH.
- Samsung (Tizen) TVs are not supported.

### Advanced

- ⚠️ **Set App Link**: choose the app that opens each site's links on the TV (YouTube, FPT Play, VieON, Netflix, Spotify… or a site you add), e.g. YouTube links from Telegram, Zalo or a browser open straight in SmartTube instead of asking. After applying, the app asks the TV whether it took the choice; "Ask every time" puts it back. Android 11 and older work per app (`pm set-app-link`), Android 12 and newer per user selection (`pm set-app-links-user-selection`). The commands were tried by hand on a Skyworth TV (Android 11): every kind of YouTube link opened straight in SmartTube; the in-app screen and Android 12+ are untested.
- ⚠️ **ADB shell**: run shell commands right on the TV like `adb shell` on a computer, with live output, a Stop button and `cd` that carries over; a pasted leading `adb shell` is dropped. Nearly 200 explained examples, most used first (TV info, apps, speed-ups, hidden settings screens, app permissions, network, remote, screensaver, files, logs…); blanks like `<package>` are filled in a dialog that lists the TV's packages. Dangerous commands (disabling or removing packages the TV needs, deleting whole folders, turning off Wi-Fi or ADB, booting into recovery…) ask first, and the worst need a tick; disabled packages go into the backup so Restore turns them back on; changed settings print their old value so you can put it back. Commands only come from the app's own input box; the history stays on the phone.

### Also

- ⚠️ Pan and pinch-zoom your own wallpaper for the clean Home, or fit it whole with blurred edges.
- ⚠️ **Back up & move phones**: TV backups, setup sets, recent TVs and optionally the ADB key in one zip file, imported on another phone.
- Runs on an **Android phone** or **on the TV itself** (for iPhone users). iOS-style light / dark interface (always dark on the TV), Vietnamese / English.
- ⚠️ **Updates itself** from GitHub Releases: Android 12+ installs new versions while the app is closed, older Android asks for one tap; can be turned off under TiviKit updates. No usage data.

### Test status

- ✅ 26 Oct 2026, TiviKit 1.0 (`vn.tivikit`) on a Tanix TX3 Mini (Android 9): cleaning, installing the clean Home, SmartTube from the suggestions, ad-blocking DNS (NextDNS).
- ✅ 26 Oct 2026, a 1.3.0 test build: the live TV screen on a phone, pinned while scrolling. Screen capture also ran on its own on the TX3 Mini (Amlogic encoder, first frame after ~0.5 s).
- ✅ 25 Oct 2026 on one Skyworth TV, with a test build from before the package rename to `vn.tivikit`: APK install, Remote buttons, TV360 quick channel (one tile), Timer.
- ✅ 27 Oct 2026 on a Skyworth SWTV-22AE (Google TV, Android 11): updating the clean Home with Apply, and a remote button opening SmartTube's voice search directly.
- ⚠️ The automatic move of quick-channel tiles, timers and button mappings from the old test build (`com.icm.cleantv`) hasn't been tried.
- ⚠️ The LG mode hasn't been tried on any LG TV.
- Restore and the original kids lock ran in CleanTV 2.8.2; not re-tested with TiviKit.
- Every build runs the unit tests of the app and the helper apps; they don't replace a real TV.
- A real-TV test checklist, riskiest first (Vietnamese): [docs/THU-TREN-TIVI.md](docs/THU-TREN-TIVI.md).

### Build

JDK 17 + Android SDK (compileSdk 35). Build the helper apps with `scripts/dung-khoa.sh`, `dung-kenh.sh`, `dung-hen-gio.sh` and `dung-go.sh` (their APKs are already in `app/src/main/assets`), then `./gradlew :app:assembleRelease`. The scripts default `JAVA_HOME` to Homebrew's JDK 17 on macOS. On Windows, clone with `core.autocrlf=false` or the launcher patch won't apply. Signing comes from `ky.properties` (not in git), shared by the app and its helpers; without it the release is signed with the debug key. Versions live in `version.properties`; the **Phát hành** (release) workflow on GitHub Actions bumps the version, runs the tests, builds and signs the APK, writes release notes from commit titles and publishes a GitHub Release with the APK, its `.sha256` and the `cap-nhat.json` that the app's self-update reads. A weekly workflow rebuilds the TV lists from CleanYourTV; when tests pass and only the lists changed, it releases on its own, while anything touching app code waits for the owner's approval. Code identifiers are in Vietnamese.

### Verify an install

Every APK on [Releases](https://github.com/Circling5714/tivikit/releases) is signed with one certificate (`CN=CleanTV Rebuild`), SHA-256
`ec4c282ecefa87e7e959d44de052ccb047a8164c383ff39341d910d85af6f653` (`apksigner verify --print-certs`). TiviKit (`vn.tivikit`) installs alongside CleanTV and can't update over it.

### Privacy

No usage data: CleanTV's anonymous counter, "send package list" button and website links are switched off at build time (`GUI_SO_LIEU`, `LIEN_KET_VGS`). Bug reports and sharing a TV list with CleanYourTV only open a filled-in GitHub page in your browser; you review it and send it yourself. The phone only goes online to update TiviKit itself (it asks GitHub Releases about every 12 hours and when opened, downloads the new APK and checks its SHA-256 before installing), to download apps you pick (GitHub, VideoLAN, Kodi or your own link), to check installed apps for updates (GitHub), to look up weather places outside Vietnam (Open-Meteo's geocoding, only the text you typed) and when you pick TV channel tiles (the channel list and pictures from VTVgo). Control goes straight from the phone to the TV on your home network. On the TV, the clean Home gets weather from Open-Meteo, ad-blocking DNS sends lookups to the provider you choose, and a rooted LG TV downloads the Vibehack installer from GitHub when you install it.

### Contact and support

- **Telegram group:** [t.me/tivikit](https://t.me/tivikit) for questions, ideas and which TVs work.
- **Bug reports:** [GitHub Issues](https://github.com/Circling5714/tivikit/issues). In the app, Feedback / report a bug → Report a bug on GitHub fills in the TiviKit version, phone, connected TV and last error for you to review before sending.
- **Support the author:** TiviKit is free and ad-free. To buy the author a coffee, bank transfer in Vietnam: MB Bank, account `070991988888`, CAO MINH THI, message "Ung ho TiviKit" (in the app: home screen → Support the author, with copy buttons).
- ⚠️ **Share your list with CleanYourTV:** once a cleaned TV has run fine for a few days, home screen → Share your list with CleanYourTV. The app writes `debloat.txt` and `tv.json` in the format of [CleanYourTV](https://github.com/Gathin23/cleanyourtv)'s `examples/` from the packages it disabled (re-checked against the TV when it is connected, leaving out packages that came back and apps you installed) and opens their "My TV" issue form filled in. Without GitHub, copy it or share it to the Telegram group. TiviKit pulls new lists in weekly.

---

## Ghi công / Credits

- TiviKit dựa trên **CleanTV 2.8.2** của AlexVGS ([mã nguồn](https://github.com/vgscity/vgs-cleantv), MIT), kèm các bản sửa và bản vá Home sạch đưa về đến CleanTV 3.0.0: app gốc, cách dọn tivi, bản vá Home sạch, khoá trẻ em, sao lưu / khôi phục.
  *TiviKit builds on CleanTV 2.8.2 by AlexVGS (MIT), with fixes and the clean Home patch ported up to CleanTV 3.0.0: the original app, the clean-up logic, the clean Home patches, the kids lock, backup and restore.*
- CleanTV dựa trên **[CleanYourTV](https://github.com/Gathin23/cleanyourtv)** của Gathin và cộng đồng (MIT): màn Home sạch và các danh sách tivi đều từ dự án đó.
  *CleanTV is built on CleanYourTV by Gathin and contributors (MIT): the clean Home launcher and the TV lists come from that project.*
- Thư viện / libraries: libadb-android, Conscrypt, Bouncy Castle, OkHttp, JSch, AndroidX / Jetpack Compose. Phông / fonts: Inter (app), Lexend và Outfit (Home sạch), OFL. Thời tiết / weather: Open-Meteo (CC BY 4.0). Toạ độ phường/xã / ward coordinates: © OpenStreetMap contributors (ODbL). Chế độ LG cài [LG TV Vibehack](https://github.com/hoangvya19/lg-tv-webos-vibehack) (MIT) lên tivi đã root / the LG mode installs it on rooted TVs.
- Toàn văn giấy phép / full licence texts: [NOTICE](NOTICE) và mục "Giấy phép mã nguồn mở" trong app / and "Open-source licenses" in the app.

## Giấy phép / License

Mã nguồn theo giấy phép [MIT](LICENSE); phần mã gốc của CleanTV thuộc bản quyền VGS / AlexVGS, ghi trong LICENSE. Giấy phép MIT không bao gồm tên CleanTV và VGS, logo CleanTV / VGS và ảnh nền VGS (xem cuối LICENSE).

*Source code: [MIT](LICENSE); the original CleanTV code is © VGS / AlexVGS (see LICENSE). The licence does not cover the names CleanTV and VGS, their logos or the VGS wallpaper.*
