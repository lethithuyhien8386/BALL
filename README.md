# BubblePilot Astra v0.5.0 — chính xác đường bay + bật tường

Mã nguồn Android để tự build APK trên GitHub Actions. **Không phải APK đã biên dịch.**

## Sửa lỗi v0.4

1. **Không nhắm xuyên bóng chắn:** mỗi góc bắn được mô phỏng bằng tia bay từ bóng ở nòng, kiểm tra va chạm với **mọi bóng trên từng đoạn**. Bóng gần nhất chặn đường trước cả mục tiêu điểm cao, kể cả bóng trắng. Chỉ tính điểm nơi bóng thật sự va chạm rồi ghép vào lưới.
2. **Tự đo mép tường qua pixel:** đọc hàng nghìn pixel dọc hai mép sân ở vùng giữa màn hình, tìm sự khác biệt màu giữa trong/ngoài sân. Với hai ảnh mẫu, nhận được mép x=23/386 (ảnh 422 px) và x=38/401 (ảnh 441 px); tâm bóng sẽ dội khi chạm mép sân trừ bán kính bóng. Khi không thấy đường biên đủ rõ thì dùng giới hạn suy ra từ lưới làm dự phòng.
3. **Nước bắn nảy tường 1–3 lần:** xét góc bắn trực tiếp và góc suy ra từ các ô ghép tiềm năng bằng cách phản chiếu mục tiêu qua tường. Tia bắn được mô phỏng thực chứ không chỉ dùng khoảng cách đường thẳng để chấm điểm. Chọn kết quả có giá trị mô phỏng cao nhất sau khi giảm điểm những đường quá nhạy với sai số.
4. **Ghép ô sát đúng điểm chạm:** không còn cho ghép vào ô cách quá xa hoặc nằm phía sau quả bóng chắn. Làm tròn tọa độ chạm **trước** khi tính va chạm, tương ứng với thao tác Android thực tế.
5. **Chấm điểm bóng rơi hợp lý hơn:** chỉ cộng bóng thật sự rơi khi trước phát bắn chúng vẫn còn nối với trần, tránh điểm ảo.
6. **Kiểm tra độ ổn định:** thử sai lệch nhỏ của góc bắn (±0.17°, ±0.34°), vị trí mép tường (±2 px) và kích thước bóng (±0.9 px); ưu tiên quỹ đạo ít nhạy với các sai số này.

Không đổi cấu hình trước: `gpt-6-astra`, `https://api.xingchiapi.com/v1`, lưu key mã hóa bằng Android Keystore, CLICK/Accessibility, FLOAT, thanh nổi ▶ / ■ / ×, chạy tiếp khi có bóng mới. Bản mới hiển thị `B1 POP 3` hoặc `B2 AIM ...` khi cú bắn có nảy tường.

## Tạo APK bằng GitHub

1. Giải nén ZIP, đưa **toàn bộ nội dung** lên thư mục gốc GitHub repository (kể cả thư mục ẩn `.github`). Gỡ workflow phiên bản cũ để tránh tải nhầm artifact.
2. Vào **Actions → Build and verify Bubble Pilot Astra v0.5 APK → Run workflow** (hoặc push lên nhánh `main`).
3. Workflow chạy kiểm thử thuần Java, build `app-debug.apk`, kiểm tra ZIP/APK signature/DEX/manifest và thử cài, khởi động trên máy ảo Android 9 và 11.
4. Chỉ sau khi `publish-verified` xanh mới tải artifact **BubblePilot-Astra-v0.5-APK-InstallChecked**, giải nén và cài `app-debug.apk`.
5. Mở app để kiểm tra `v0.5.0`, cấp quyền CLICK và FLOAT. Android 8–10 còn cần quyền CAPTURE. Mở game, bật thanh nổi và nhấn ▶.

**Nếu Android từ chối cập nhật app:** các workflow GitHub sử dụng debug signing key có thể khác giữa những lượt build, nên bản APK mới có thể không cập nhật đè lên bản cũ. Bạn có thể gỡ bản cũ rồi cài bản mới, nhưng việc này sẽ xóa cấu hình đã lưu và API key; hãy ghi lại endpoint/model/key trước khi gỡ.

## Kiểm thử nguồn

- Linux/macOS JDK 17+: `bash tests/test.sh`
- Windows JDK 17+: `tests\test.bat`
- `TrajectoryPhysicsTest`: bóng chắn đầu tiên, hai chiều bật tường, 2 lần bật tường, và chọn cú bật tường khi tốt hơn đường thẳng.
- `GeometryRegressionTest`: đo vị trí biên sân từ cả 2 ảnh và tính được đường đi hợp lệ.
- `NewGamePaletteTest`, `AnalyzeScreenshot`, `ImageScaleTest`, `ShotGateTest`: nhận diện bóng nhiều màu, độ phân giải, trạng thái chờ bóng mới và không bắn lặp.

### Giới hạn chưa được xác minh

Cơ chế bóng đặc biệt, lực bắn, tường thật của game, luật tính điểm và các thay đổi của màn chơi có thể khác mô hình giả định. Không thể cam kết chọn nước tối ưu tuyệt đối. Thử nghiệm này chỉ kiểm tra bằng ảnh tĩnh và các bàn cờ mô phỏng; **chưa cài, chạy và điều khiển trên thiết bị thực** trong môi trường này vì không có Android SDK. Workflow GitHub có các bước kiểm tra cài đặt nhưng không thay thế việc thử tự động chơi trên game thật.

API Xingchi là dịch vụ bên thứ ba, không nên đưa API key thật vào repository công khai.
