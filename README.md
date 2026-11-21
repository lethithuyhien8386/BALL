# Bubble Pilot Offline v0.8.0

Ứng dụng Android thử nghiệm nhận diện Bubble Shooter bằng pixel và tính toán đường bắn tại thiết bị, dành cho môi trường chơi thử offline. Không root, không gửi ảnh lên máy chủ, không dùng AI từ xa. Cần người dùng bật CLICK/FLOAT; Android 8–10 cần CAPTURE. Min SDK 26, compile/target SDK 35, JDK 17, Gradle 8.9 và AGP 8.7.3.

## Build APK bằng GitHub Actions

1. Giải nén ZIP. Đưa **toàn bộ nội dung thư mục**, bao gồm `.github/workflows/build-apk.yml`, lên repository GitHub (nhánh main hoặc master). Nếu dùng Windows, bật hiển thị file/thư mục ẩn để không bỏ sót `.github`.
2. Chọn Actions → **Build and verify Bubble Pilot Offline v0.8 APK** → Run workflow. Workflow chạy kiểm thử Java, build APK debug đã ký, kiểm tra phiên bản/package/chữ ký rồi kiểm tra cài đặt và khởi chạy trên emulator Android 9/11.
3. Khi cả ba job đều xanh, tải artifact **BubblePilot-Offline-v0.8-APK-InstallChecked**. Giải nén và cài `app-debug.apk`. APK candidate chưa qua emulator không phải bản install-checked. Nếu chữ ký debug khác bản cũ, có thể cần gỡ bản cũ trước; việc này xóa dữ liệu ứng dụng.
4. Mở app và kiểm tra phiên bản v0.8.0. Bật CLICK và FLOAT; với Android 8–10 chấp nhận hộp thoại CAPTURE. Nhấn START để mở thanh nổi. Vào bàn chơi offline, đặt thanh nổi không che nút pause, bóng hoặc kỹ năng. Nhấn ▶ để bắt đầu, ■ để dừng, × để đóng.

Build local: cài JDK 17, Android SDK 35, Build Tools 35.0.0 và Gradle 8.9. Chạy `bash tests/test.sh` rồi `bash build-apk.sh` (Windows: `tests\test.bat`, `build-apk.bat`). APK nằm tại `app/build/outputs/apk/debug/app-debug.apk`.

## Tính năng và sửa lỗi

- Loại bỏ phần cấu hình/gọi mô hình AI, mã gửi ảnh, quyền INTERNET và file cấu hình từ xa. `LocalSettings` chỉ lưu hiệu chỉnh hình học; có một đoạn di trú dọn thông tin xác thực cũ khi nâng cấp.
- Tách màu cam (O) / vàng (Y), tím đậm (V) / tím hồng (P); giữ đỏ, xanh lá, xanh lam/cyan và vật thể đặc biệt trung tính. Vật thể đặc biệt trên bàn là vật cản, không bị coi là cùng màu với bóng thường.
- Dùng bố cục cửa sổ quan sát được trong 5 video để khởi tạo lưới, có hiệu chỉnh lệch vị trí/hàng so le. Giữ hiệu chỉnh trước đó khi có thể. Kiểm tra HUD để tránh thao tác trên bảng tổng kết điểm/menu. Kiểm tra bóng bay trong khoảng trống giữa bàn và launcher, dùng hai khung ổn định trước khi bắn.
- Giữ mô phỏng va chạm đầu tiên, bật tường và rơi bóng từ v0.7. Nếu không có POP đáng tin cậy vẫn chọn đường hợp lệ; không kẹt vì tất cả phương án vào vòng cuối là POP có độ ổn định thấp. Tái sử dụng kế hoạch khi chữ ký, vị trí và hình học không đổi. POP trên thanh nổi là **dự đoán mô phỏng**, không phải điểm đã ghi nhận.
- `SkillVision` so khớp mẫu pixel của các biểu tượng thu từ video tại ba vị trí trong cột kỹ năng bên trái, không gán cố định kỹ năng cho thứ tự ô. `SkillController` ưu tiên tăng thời gian, giữ cầu 7 màu cho khi bóng thường không có POP và có cụm màu thay thế, dùng cầu vàng khi có đường trực tiếp đáng tin cậy. Hai quan sát trước khi bấm, hai quan sát mất biểu tượng để xác nhận, timeout nếu không xác nhận; chụp lại và lập kế hoạch sau khi kỹ năng thay đổi. Không kích hoạt biểu tượng xanh viền vàng/cam (IGNORE). Không tự suy diễn bán kính nổ/điểm của cầu vàng. Cơ chế sau khi kích hoạt cầu vàng/cầu 7 màu còn là giả định cần kiểm chứng trực tiếp.
- X2: có kiểu và nhánh ưu tiên trong bộ điều phối, nhưng **chưa có mẫu biểu tượng được xác nhận từ video**, nên bộ nhận diện chưa trả về X2 và app chưa tự bấm X2. Không gán nhầm biểu tượng xanh cho X2. Nhận diện kỹ năng v0.8 được hiệu chỉnh cho bố cục có lề trong video; bố cục khác có thể không kích hoạt.

## Kiểm thử và giới hạn

Xem `TRAINING_REPORT.md` và `tests/results-v0.8.txt`. Bộ kiểm thử Java đã chạy trên môi trường phát triển, bao gồm hồi quy cũ và 5 video mới. Chưa có Android SDK/Gradle/emulator trong môi trường sửa mã này, nên chưa trực tiếp build APK hoặc kiểm tra tốc độ, thao tác, kỹ năng và điểm thực tế trên CloudPhone. Workflow GitHub có sẵn các bước build/kiểm tra, nhưng chưa được chạy ở đây. Cần ghi lại video mới khi thử offline để xác định mẫu X2, hình dạng bóng sau khi kích hoạt kỹ năng và kết quả điểm thực tế. Không cam kết điểm tối đa hoặc độ chính xác tuyệt đối từ ảnh tĩnh.
