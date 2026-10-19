# CrossFollow OCR — Android

## Tính năng
- OCR màn hình Android theo thời gian thực sau khi người dùng chấp thuận quyền chia sẻ màn hình.
- Quét khoảng mỗi 1,8 giây bằng Google ML Kit Text Recognition.
- Tìm từ khóa follow chéo tùy chỉnh, phát hiện theo nội dung OCR.
- Mẫu bình luận ngắn có icon; sao chép để người dùng tự kiểm tra và đăng.
- Mở TikTok, nút Start/Stop, thống kê khung hình và số lần phát hiện.
- Lịch sử cục bộ, xóa lịch sử, chia sẻ/xuất lịch sử.
- GitHub Actions tự biên dịch APK debug.

## Giới hạn
Ứng dụng không tự động Like, Follow, đăng bình luận hoặc vuốt video. Các hành động này vẫn thực hiện thủ công để tránh tương tác giả/spam và nguy cơ tài khoản bị hạn chế. OCR phụ thuộc chất lượng màn hình, kích thước chữ, ngôn ngữ và quyền chia sẻ màn hình Android. Một số nội dung có thể bị bỏ sót hoặc nhận diện nhầm.

## Build APK bằng GitHub Actions
1. Tạo repository mới tại https://github.com/new.
2. Tải toàn bộ nội dung dự án lên GitHub, giữ nguyên thư mục ẩn `.github/workflows`.
3. Vào **Actions** → **Build CrossFollow OCR APK** → **Run workflow**.
4. Khi hoàn thành, mở run → **Artifacts** → tải `CrossFollow-OCR-debug-apk`.
5. Giải nén artifact để lấy `app-debug.apk`.

## Lưu ý
APK debug là bản thử nghiệm. Mã nguồn cần được biên dịch trong GitHub Actions/Android Studio để xác nhận build trên môi trường Android thực tế.
