# Kiểm thử bản vá kỹ năng v0.9.0

## Nguyên nhân

- v0.8 gọi bộ nhận diện kỹ năng chỉ khi `recordedHud` đúng với bố cục có lề của video. Hai ảnh mới là màn hình đầy đủ và có tọa độ nút khác. Ngoài ra X2 chưa có mẫu, kiểm tra nút phụ thuộc `canShoot`, cầu vàng chỉ sử dụng khi có POP thường và cơ chế rainbow chưa đọc bảng chọn màu.
- v0.9 dùng kiểm tra HUD riêng và tìm mẫu trong vùng cột kỹ năng, tách luồng nút thụ động khỏi kiểm tra ổn định của cú bắn. Cơ chế `bestShot` và `ShotGate` giữ nguyên.

## Ảnh thực người dùng cung cấp

- `tests/skills-full.png` (447×795): nhận diện GOLD (112,598), X2 (114,668), RAINBOW (112,740). Tọa độ do thuật toán tìm, không phải tọa độ tap ghi cứng.
- `tests/skills-combo.png` (445×797): nhận diện bảng màu và các ô P (248,525), V (352,525), O (296,609). Trên bảng nhận diện trong ảnh này, P (tím hồng) được chọn theo điểm mô phỏng 1238,288; dự đoán 8 bóng ghép, 0 bóng rơi. Các con số **không phải điểm của game** hoặc kết quả đã bắn thật. Tại các tỉ lệ kiểm tra khác (480×860, 320×573), màu chọn vẫn P; số bóng dự đoán có thể thay đổi theo nhận diện lưới v0.8.

## Kiểm thử đã chạy

`bash tests/test.sh` chạy thành công, gồm toàn bộ bộ test trước đó (ảnh/lưới, palette, vật cản, bật tường, chuyển lượt, 5 video training và 14 tập biểu tượng), kiểm thử state machine mới, 3 độ phân giải, vị trí nút hoán đổi/dịch chuyển, bảng chọn màu và xác nhận bóng kỹ năng tổng hợp từ hình. Hai ảnh mới là ảnh thực; ảnh dịch nút, đổi tỉ lệ/ghép màu và bóng vàng sau kích hoạt là biến đổi/mô phỏng phục vụ kiểm thử.

- X2: ưu tiên hành động trước TIME và trước `ShotGate`; không nhấp trùng khi đang chờ xác nhận tiêu thụ.
- Rainbow: chỉ mất biểu tượng chưa đủ để xác nhận; phải đi qua chọn màu, chờ xác nhận bóng chứa màu đã chọn. So sánh điểm của đúng ba tùy chọn hiện trên hộp, không chọn màu không có trong hộp.
- Gold: không kích hoạt khi có POP thường; không bắn chỉ vì mất biểu tượng. Chờ bóng vàng thực sự được phát hiện tại launcher. Không tự ước lượng điểm nổ.
- IGNORE: không kích hoạt.

## Giới hạn

Không có SDK/Gradle/emulator để build APK tại máy sửa mã nguồn; chưa gửi gesture thực tế và chưa xác nhận hiệu ứng điểm, thời gian, giới hạn kích hoạt hay hình bóng sau khi chọn màu ở thiết bị. Nên chạy workflow GitHub và thử trên bản trò chơi offline. Nếu có lỗi sau kích hoạt, chụp ảnh bóng thật ngay tại launcher để đối chiếu với bộ mẫu. Không có bảo đảm đạt điểm tuyệt đối; không sửa thuật toán bắn thường theo yêu cầu.
