# Báo cáo hồi quy từ 5 video mới

Nguồn: 1.mp4 (65.57s), 2.mp4 (95.80s), 3.mp4 (96.67s), 4.mp4 (96.57s), 5.mp4 (34.10s). Đã lấy ảnh tổng quan cách 5 giây và chọn 35 khung hình 480px chiều rộng để phân tích mẫu, nhận diện trạng thái và kiểm thử. `tests/training` chứa các ảnh đã trích, tên `vN_TTT.jpg` tương ứng video và giây. Không kèm các MP4 gốc.

| Video | Bằng chứng quan sát | Sửa và kiểm tra |
|---|---|---|
| 1 | 0s: 76 bóng; 15s cầu vàng/biểu tượng xanh; 30s cầu 7 màu/cầu vàng/biểu tượng xanh. | Trước sửa bản phân màu mới nhưng chưa hiệu chỉnh lưới chỉ thấy 49 bóng ở 0s; sau hiệu chỉnh thấy 76. Kiểm tra đúng bộ biểu tượng 15s, 30s. |
| 2 | Cam và vàng là hai bóng khác nhau; 25s tăng thời gian; 75s cầu 7 màu/biểu tượng xanh/tăng thời gian. | Tách màu và nhận diện bộ biểu tượng thay đổi vị trí. |
| 3 | 45s cầu vàng ở trên, cầu 7 màu ở giữa; 95s bảng điểm có nút SUBMIT. | Nhận diện theo hình dạng chứ không theo vị trí; HUD guard trả về null trên bảng tổng kết, không bắn. |
| 4 | Tím hồng và tím đậm; 40s bóng đang bay; 60s tăng thời gian nằm trên cầu vàng. | Tách màu, chặn bắn khi có bóng bay trong khung kiểm tra, nhận diện đúng biểu tượng. |
| 5 | Bàn pha vàng, đỏ, hai sắc tím; 20s chưa có kỹ năng đầy; 30s tăng thời gian. | Tách màu, bỏ ô đang nạp, nhận diện biểu tượng tăng thời gian. |

Mẫu biểu tượng được trích từ chính video nên đây là kiểm thử hồi quy nội mẫu, không phải đánh giá tổng quát trên dữ liệu chưa từng thấy. Các video thể hiện biểu tượng đã tích nhưng không cung cấp đầy đủ quá trình kích hoạt để xác minh hình bóng sau kích hoạt, cơ chế cầu vàng hoặc biểu tượng X2. X2 không tự bấm để tránh nhầm kỹ năng xanh cần bỏ qua. Giữ nguyên quy tắc POP/mô phỏng của mã nguồn cũ vì chưa suy ra đủ luật thực tế. Không dùng điểm dự đoán để báo tỷ lệ thành công thực tế.

## Kết quả đã chạy

`bash tests/test.sh` hoàn thành với mã thoát 0. Gồm bài kiểm tra ảnh gốc, tỉ lệ ảnh, palette, ShotGate, vật cản/va chạm/bật tường, geometry, hai bộ video cũ, TrainingRegressionTest (5 video, 14 bộ biểu tượng kể cả ô trống), SkillControllerTest. Chi tiết stdout ở `tests/results-v0.8.txt`. Các số mili giây trong log chỉ là xử lý ảnh tĩnh trên môi trường Java của máy phát triển, không đo tốc độ Android.

## Cần kiểm chứng tiếp trên Android

Build/sign/install/launch qua GitHub Actions, sau đó thử trong bàn offline: kiểm tra 3 kỹ năng thực sự được tiêu thụ, nhận diện quả đặc biệt đã nạp, kết quả cú bắn, tình huống bóng cùng màu liên tiếp, Start/Stop, tốc độ và các bố cục màn hình khác. Cần mẫu riêng của X2 để kích hoạt an toàn. Chưa có kết quả các bước này trong lần sửa mã hiện tại.
