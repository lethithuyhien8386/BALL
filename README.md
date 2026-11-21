# BubblePilot Astra v0.6.0 — Video Accuracy & No-Penalty Safety

**Mã nguồn Android để build APK trên GitHub Actions** (không chứa APK sẵn). Phiên bản này sửa lỗi chọn sai màu, chọn ngẫu nhiên và vị trí va chạm, dựa trên 7 khung hình trích từ video game 95,6 giây do người dùng gửi.

## Thay đổi cốt lõi sau khi xem video

1. **Tuyệt đối không phát bắn `matched=0`.** Game trừ điểm khi bắn không trúng cụm cùng màu. `bestShot()` chỉ trả về phương án dự đoán làm biến mất **ít nhất 3 bóng**, tính cả bóng đang bắn. Nếu không có góc đủ an toàn, bot hiện `KHÔNG CÓ POP` và **không bắn**. Điều này có thể khiến bot dừng nếu trò chơi không có chức năng đổi bóng; không coi đó là lỗi treo.
2. **Nhận diện vị trí bóng nòng từ pixel.** Ở video, bóng đang bắn nằm cao hơn cấu hình cũ khoảng 20–30 px (tại độ phân giải 720×1292). Bản cũ đôi khi nhận **màu xanh của nhân vật** là bóng xanh. Bản mới quét tìm quả bóng tròn thực sự ở vùng nòng, dùng mức độ tròn, độ đồng nhất màu, và kiểm tra màu có hiện diện trên lưới. Nếu bóng đang bay hoặc nòng chưa sẵn sàng, không bắn.
3. **Tự căn kích thước lưới theo sân thực tế.** Video có viền sân nhỏ hơn ảnh chụp trước: không dùng cố định khoảng cách bóng bằng phần trăm màn hình. Phát hiện cặp tường phản xạ thật, bỏ qua đường viền ngoài của màn hình quay, tính bước ô theo bề rộng sân rồi dò vị trí hàng bóng.
4. **Hỗ trợ đảo so le 9/10 bóng ở hàng đầu.** Trò chơi có thể thay đổi hàng đầu từ 9 thành 10 bóng. Bản cũ làm lệch toàn bộ lưới và đếm sai bóng rơi. Bản này mô hình hóa hai hướng lệch, tự kiểm tra phương án khi bàn cờ đổi trạng thái, xác định hàng trên cùng thực sự.
5. **Giữ mô phỏng va chạm đầu tiên và bật tường 1–3 lần** từ v0.5. Đường bắn có bóng chắn sẽ dừng ở bóng chắn, không được tính điểm của nhóm phía sau. Phương án phải vượt kiểm tra góc ±0,17°/±0,34°, kích thước bóng và vị trí tường; chấp nhận nếu độ ổn định ≥80%.
6. **Astra chỉ là tư vấn**, gọi khi thuật toán trên máy không tìm được đường nổ cụm. Tọa độ API phải được kiểm tra qua đúng mô phỏng va chạm, điều kiện nổ ≥3 bóng và độ ổn định ≥80%. **Không có cơ chế “AI đoán rồi bắn thử”.** Bắn được bằng CPU thì không chờ mạng.
7. **Vòng lặp tự động**: chụp màn hình > phân tích > tìm hướng bắn có nổ cụm > xác thực > click > chờ bàn cờ chuyển lượt ổn định > lặp. Giữ CLICK (Accessibility), FLOAT, kéo thả Start/Stop, Android 8+, API key mã hóa, `gpt-6-astra` tại `https://api.xingchiapi.com/v1`.

## Build APK trên GitHub

1. Giải nén, tải **nội dung** thư mục dự án lên thư mục gốc GitHub repository (gồm cả `.github/workflows/build-apk.yml`). Xóa các workflow cũ để tránh tải nhầm APK.
2. Trong GitHub: **Actions → Build and verify Bubble Pilot Astra v0.6 APK → Run workflow**.
3. Chờ các jobs `build`, `emulator-install-check` (Android 9 và Android 11), `publish-verified` thành công.
4. Tải artifact **BubblePilot-Astra-v0.6-APK-InstallChecked** và giải nén để lấy `app-debug.apk` (không cài file ZIP trực tiếp).
5. Nếu bản mới báo chữ ký khác bản đang cài, có thể cần gỡ bản cũ trước khi cài bản mới; sao lưu API key trước vì dữ liệu ứng dụng sẽ bị xóa.
6. Mở app, kiểm tra hiển thị **v0.6.0**, cấp CLICK và FLOAT (Android 8–10 thêm quyền CAPTURE), mở game và nhấn ▶ trên thanh nổi.

## Kiểm thử nguồn tại máy tính

- Linux/macOS (JDK 17+): `bash tests/test.sh`
- Windows (JDK 17+): `tests\test.bat`
- `VideoSafetyTest` dùng ảnh chụp trực tiếp từ video tại giây 0, 24, 40, 52, 64, 76 và 92; xác nhận bóng đang bắn đúng màu, khung hình giữa lượt không bắn, không có phương án nổ cụm thì dừng, và AI không thể vượt rào kiểm tra.
- `TrajectoryPhysicsTest`: bóng đầu tiên chắn đường, bật tường trái/phải/2 lần, lựa chọn cú bật khi tốt hơn.
- `ImageScaleTest`, `NewGamePaletteTest`, `GeometryRegressionTest`, `ShotGateTest`: ảnh mẫu cũ, màu tím/xanh dương/bóng đặc biệt, phát hiện tường và không bắn lặp.

**Giới hạn:** Trên các ảnh và mô phỏng, giải thuật có thể loại bỏ các cú bắn dự đoán trừ điểm, nhưng chưa thể bảo đảm thắng hoặc tối ưu tuyệt đối trên máy thật. Luật bóng đặc biệt, phân loại hiệu ứng và vật lý thực tế có thể khác mô hình. Môi trường tạo dự án chưa có Android SDK để biên dịch APK hay điều khiển game trên thiết bị thực; GitHub Actions có bước kiểm tra build và cài APK trên giả lập, vẫn cần thử trong game thật để đánh giá tỷ lệ ghi điểm.

**API bên thứ ba:** endpoint Xingchi không phải máy chủ chính thức của OpenAI. Đừng đưa API key vào repo công khai; chỉ nhập key trong cài đặt trên thiết bị nếu bạn tin tưởng dịch vụ.
