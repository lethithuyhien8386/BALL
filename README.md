# BubblePilot Astra v0.7.0 — Continuous Fire / Video 2 Fix

Dự án Android Java (Android 8.0+, minSdk 26) để build APK bằng **GitHub Actions**. Không phải APK có sẵn và chưa được thử chơi trực tiếp trên máy thật.

## Thay đổi theo video `video ball 2.mp4` (105,4 giây)

- **Không dừng khi không có POP**: khi có bóng hợp lệ ở nòng súng và tìm được đường bay, bot luôn chọn một cú bắn. Thứ tự: (1) nổ cụm >=3 quả cùng màu, tối ưu bóng nổ + bóng rơi + pixel hiển thị; (2) ghép chuẩn bị combo; (3) bắn ít bất lợi nhất để đổi bóng. Các lượt (2) và (3) có thể **bị trừ điểm**, đúng theo yêu cầu của người dùng.
- **Sửa nhận sai bóng nòng khi súng nghiêng**: quét ±2,15 bán kính quanh tâm nòng (thay vì ±0,45), giới hạn vùng dọc để không lấy phần đầu nhân vật màu xanh, cộng điểm cho màu tồn tại trên lưới. Tại giây 68 nhận bóng tím bị lệch trái; tại giây 100 nhận bóng đỏ bị lệch phải.
- **Tự hiệu chuẩn lại lưới** khi số bóng nhận diện giảm đột ngột; khung giây 52 từ 3 bóng (sai) thành 51 bóng. Giữ lưới so le 9/10, vị trí tường dựa trên pixel và mô phỏng chắn đường/bật tường tối đa 3 lần.
- **Chọn cú bắn tốt bằng cách xét nhiều đường**: mở rộng danh sách ứng viên lên 96, giữ đường bật tường, đánh giá lân cận góc ±0,17°/±0,34° và tường ±2 px. Xét xác suất vẫn có POP khi ô ghép thay đổi nhẹ, thay vì bắt buộc đúng cùng một ô.
- **Giảm thời gian chờ**: chu kỳ kiểm tra khung hình cơ sở 55 ms thay vì 115 ms, khoảng tối thiểu giữa hai lượt 120 ms thay vì 240 ms (vẫn cần **hai khung hình ổn định**), thao tác click 35 ms. Nếu Android chặn screenshot vì chụp quá dày, tự động tăng thời gian đợi. Không thể vượt quá giới hạn ảnh chụp của Android bằng sửa thuật toán.
- **Astra (`gpt-6-astra`)** vẫn nằm trong Settings với API key mã hóa; tư vấn nền qua `https://api.xingchiapi.com/v1` chỉ khi người dùng bật. Không chờ mạng trước khi bắn, vì đó là nguồn trễ không kiểm soát được. Kết quả API chỉ được dùng nếu đã đến kịp và qua mô phỏng va chạm + ghép màu. Với chế độ bắn ngay, hầu hết quyết định dựa vào bộ tối ưu tại thiết bị, không phải suy luận Astra qua mạng.
- Giữ quyền **CLICK (Accessibility)**, **FLOAT**, thanh nổi kéo thả ▶/■/×, Android 8–10 cần thêm CAPTURE. Trên Android 11+ ứng dụng dùng ảnh chụp Accessibility và chịu giới hạn hệ thống.

## Build APK trên GitHub

1. Giải nén ZIP và tải **mọi nội dung bên trong** lên thư mục gốc GitHub repository; có `.github/workflows/build-apk.yml`.
2. GitHub → **Actions** → **Build and verify Bubble Pilot Astra v0.7 APK** → **Run workflow**.
3. Chỉ sử dụng artifact **`BubblePilot-Astra-v0.7-APK-InstallChecked`** khi ba job `build`, `emulator-install-check` (Android 9/11) và `publish-verified` đều hoàn thành. Giải nén artifact để lấy `app-debug.apk`.
4. Cài APK. Nếu ứng dụng cũ dùng chữ ký khác, sao lưu key rồi gỡ phiên bản cũ trước khi cài lại. Xác nhận màn hình ghi v0.7.0.
5. Cấp CLICK/FLOAT (Android 8–10 thêm CAPTURE), mở game và nhấn ▶.

## Kiểm thử

- `bash tests/test.sh` (yêu cầu JDK 17 trở lên; không cần Android SDK cho phép thử thuật toán).
- `VideoContinuousTest` dùng **13 khung hình lấy từ video 2** được đưa về 480 × 856 như ảnh đầu vào của ứng dụng: kiểm tra màn menu, màu nòng súng, lưới đổi chỗ, có POP thì POP và không có POP thì vẫn chọn được phát bắn hợp lệ.
- `TrajectoryPhysicsTest`: chạm bóng chắn đầu tiên, bật tường hai phía và nhiều lượt; `ShotGateTest`: chống bắn trùng dù có ảnh lỗi. Các kiểm thử cũ được giữ lại và cập nhật tương ứng chính sách bắn liên tục.
- Kết quả hiệu năng trên môi trường kiểm thử Java: lập kế hoạch thường vài mili-giây (đôi khi vài chục ms); tự tìm lại lưới có thể mất khoảng 100–150 ms. **Không phải số đo trên thiết bị Android**, còn tùy tốc độ chụp màn hình và độ trễ thao tác.

**Giới hạn cần biết**: Test ảnh tĩnh không cho biết chính xác tốc độ/độ chính xác khi chơi trực tiếp. Kết quả POP/drop là ước lượng, luật bóng đặc biệt và vật lý thực tế có thể làm sai điểm. Nếu Android đang hiển thị menu, bóng còn bay hoặc không xác nhận được bóng nòng, app sẽ tạm không chạm để tránh bắn vào sai chỗ; **không có trạng thái dừng chỉ vì `matched=0`**. Không thể cam kết luôn đạt số điểm cao nhất hoặc phản ứng tức thời trên mọi thiết bị.

**Bảo mật**: Xingchi là dịch vụ bên thứ ba, không phải endpoint chính thức của OpenAI. Chỉ nhập key trong ứng dụng và chủ động bật tư vấn Astra nếu tin tưởng dịch vụ; không đưa key lên GitHub.
