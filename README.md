# Bubble Pilot Offline v0.9.0

Ứng dụng Android thử nghiệm nhận diện Bubble Shooter bằng pixel và tính toán đường bắn tại thiết bị, dành cho môi trường chơi thử offline. Không root, không gửi ảnh lên máy chủ, không dùng AI từ xa. Cần người dùng bật CLICK/FLOAT; Android 8–10 cần CAPTURE. Min SDK 26, compile/target SDK 35, JDK 17, Gradle 8.9 và AGP 8.7.3.

## Build APK bằng GitHub Actions

1. Giải nén ZIP. Đưa **toàn bộ nội dung thư mục**, bao gồm `.github/workflows/build-apk.yml`, lên repository GitHub (nhánh main hoặc master). Nếu dùng Windows, bật hiển thị file/thư mục ẩn để không bỏ sót `.github`.
2. Chọn Actions → **Build and verify Bubble Pilot Offline v0.9 APK** → Run workflow. Workflow chạy kiểm thử Java, build APK debug đã ký, kiểm tra phiên bản/package/chữ ký rồi kiểm tra cài đặt và khởi chạy trên emulator Android 9/11.
3. Khi cả ba job đều xanh, tải artifact **BubblePilot-Offline-v0.9-APK-InstallChecked**. Giải nén và cài `app-debug.apk`. APK candidate chưa qua emulator không phải bản install-checked. Nếu chữ ký debug khác bản cũ, có thể cần gỡ bản cũ trước; việc này xóa dữ liệu ứng dụng.
4. Mở app và kiểm tra phiên bản v0.9.0. Bật CLICK và FLOAT; với Android 8–10 chấp nhận hộp thoại CAPTURE. Nhấn START để mở thanh nổi. Vào bàn chơi offline, đặt thanh nổi không che nút pause, bóng hoặc kỹ năng. Nhấn ▶ để bắt đầu, ■ để dừng, × để đóng.

Build local: cài JDK 17, Android SDK 35, Build Tools 35.0.0 và Gradle 8.9. Chạy `bash tests/test.sh` rồi `bash build-apk.sh` (Windows: `tests\test.bat`, `build-apk.bat`). APK nằm tại `app/build/outputs/apk/debug/app-debug.apk`.

## Tính năng và sửa lỗi

- Loại bỏ phần cấu hình/gọi mô hình AI, mã gửi ảnh, quyền INTERNET và file cấu hình từ xa. `LocalSettings` chỉ lưu hiệu chỉnh hình học; có một đoạn di trú dọn thông tin xác thực cũ khi nâng cấp.
- Tách màu cam (O) / vàng (Y), tím đậm (V) / tím hồng (P); giữ đỏ, xanh lá, xanh lam/cyan và vật thể đặc biệt trung tính. Vật thể đặc biệt trên bàn là vật cản, không bị coi là cùng màu với bóng thường.
- Dùng bố cục cửa sổ quan sát được trong 5 video để khởi tạo lưới, có hiệu chỉnh lệch vị trí/hàng so le. Giữ hiệu chỉnh trước đó khi có thể. Kiểm tra HUD để tránh thao tác trên bảng tổng kết điểm/menu. Kiểm tra bóng bay trong khoảng trống giữa bàn và launcher, dùng hai khung ổn định trước khi bắn.
- Giữ mô phỏng va chạm đầu tiên, bật tường và rơi bóng từ v0.7. Nếu không có POP đáng tin cậy vẫn chọn đường hợp lệ; không kẹt vì tất cả phương án vào vòng cuối là POP có độ ổn định thấp. Tái sử dụng kế hoạch khi chữ ký, vị trí và hình học không đổi. POP trên thanh nổi là **dự đoán mô phỏng**, không phải điểm đã ghi nhận.
- **Bản v0.9 chỉ vá kỹ năng phụ trợ**, dựa trên v0.8 đang bắn thường tốt. Không đổi thuật toán bắn thường, phân loại màu thường, tính điểm, đường đi, vật cản, bật tường, hoặc `ShotGate`. `GameSolver` có thêm các hàm riêng cho bóng kỹ năng và bảng chọn màu.
- `SkillVision` tìm tâm biểu tượng từ mẫu pixel và viền nút trong vùng kỹ năng bên trái, hỗ trợ màn hình toàn màn hình và bố cục có lề; kỹ năng có thể đổi ô. Không dựa trên số thứ tự của nút. X2 dùng mẫu từ ảnh mới, được ưu tiên bấm ở khung hình hợp lệ đầu tiên (không bị `CHỜ LƯỢT` chặn); tăng thời gian là ưu tiên tiếp theo. Biểu tượng xanh viền vàng vẫn bị bỏ qua.
- Cầu 7 màu: giữ khi bóng thường có POP. Nếu không có POP thường, và có màu khác dự đoán POP thì kích hoạt; sau đó nhận diện hộp `Choose a color combo`, đọc đúng các màu đang hiển thị, dùng bộ tính điểm v0.8 so sánh từng màu, chọn ô dự đoán tốt nhất. Sau khi hộp đóng và bóng chứa màu đã chọn được xác nhận, lập kế hoạch từ ảnh mới **chỉ theo màu đã chọn**. Không dùng cú bắn wildcard mù, không coi nhấp chọn màu là bắn.
- Cầu vàng: chỉ dùng nếu không còn POP thường và không có cầu 7 màu phù hợp. Đợi nút đã tiêu thụ và bóng vàng/ánh vàng ở vị trí ném được xác nhận; sau đó mới tính đường bắn. Không giả định bán kính nổ hoặc điểm thưởng khi chưa có dữ liệu thực nghiệm.
- Có chống nhấp trùng, xác nhận nút đã tiêu thụ, xác nhận bóng kỹ năng từ pixel, timeout cho nút không tiêu thụ, dừng bắn thường khi hộp chọn màu còn mở. Nếu đã tiêu thụ nút nhưng chưa thể xác nhận bóng kỹ năng, app giữ trạng thái chờ để tránh đánh sai; kiểm tra màn hình, có thể nhấn Stop rồi Start nếu trạng thái không phục hồi. Khi nút bị thanh nổi che, di chuyển thanh nổi.

## Kiểm thử và giới hạn

Xem `SKILLS_REPORT_v0.9.md`, `tests/results-v0.9.txt` và báo cáo cũ `TRAINING_REPORT.md`. Bộ kiểm thử Java đã chạy trên môi trường phát triển: hồi quy cũ, ảnh mới với X2/cầu vàng/cầu 7 màu, đổi vị trí nút, ba độ phân giải, nhận diện bảng combo và so sánh điểm các màu. Kiểm thử bóng vàng/ghép hai màu sau kích hoạt dùng hình **mô phỏng** vì ảnh thực sau kích hoạt chưa có. Chưa có Android SDK/Gradle/emulator trong môi trường sửa mã này, nên chưa trực tiếp build APK hoặc xác nhận gesture, kết quả điểm và tốc độ trên CloudPhone. Workflow GitHub vẫn có sẵn các bước build/kiểm tra cài đặt.
