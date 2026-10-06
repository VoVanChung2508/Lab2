# Mail Client qua mạng LAN

Ứng dụng Java gồm một máy chủ mail và giao diện GUI Swing cho các máy khách. MailServer giữ tài khoản và thư trong thư mục `mail_data`; các máy khách phải kết nối đến IP LAN của máy đang chạy máy chủ, không dùng `localhost`.

## Yêu cầu

- JDK được cài trên máy chủ và các máy khách.
- Các máy nằm trong cùng mạng LAN và có thể kết nối với nhau.
- Cho phép kết nối TCP đến cổng `3456` trên máy chủ.

## Biên dịch

Mở PowerShell hoặc Terminal tại thư mục chứa các file `.java`, rồi chạy:

```powershell
javac MailServer.java MailClient.java MailClientGUI.java
```

Cần chép các file `.java` sang máy khách và biên dịch tại đó, hoặc chép các file `.class` vừa biên dịch sang máy khách. Dùng cùng phiên bản ứng dụng ở cả máy chủ và máy khách.

## Chạy máy chủ

Chỉ chọn một máy làm máy chủ. Trên máy đó, chạy:

```powershell
java MailServer
```

MailServer tự khởi động dịch vụ mail trên cổng TCP `3456` và mở dashboard quản trị tại:

```text
http://127.0.0.1:8080
```

Nếu cổng `8080` đang được sử dụng, MailServer sẽ thử các cổng tiếp theo đến `8089`; khi đó dùng địa chỉ được in trong cửa sổ chương trình. Giữ chương trình đang chạy trong lúc sử dụng. Tệp `MailServerDashboard.html` phải nằm trên classpath (khi chạy theo hướng dẫn trên, để tệp này cùng thư mục với các file `.class`). Dashboard chỉ lắng nghe trên máy chủ cục bộ; không thể mở từ máy khách trong LAN.

Có thể truyền cổng mail khác khi khởi động bằng `java MailServer 3457`; nếu bỏ tham số, cổng mặc định là `3456`.

Dashboard cho phép xem trạng thái, khởi động/dừng/khởi động lại dịch vụ mail, đổi cổng TCP và xem nhật ký hoạt động gần đây. Dừng dịch vụ mail không tắt dashboard, nên có thể khởi động lại từ trình duyệt. Đổi cổng sẽ khởi động lại dịch vụ nếu dịch vụ đang chạy; sau đó cập nhật cổng tương ứng trên MailClient và quy tắc tường lửa nếu có. Cổng được đổi chỉ có hiệu lực trong lần chạy MailServer hiện tại; khởi động chương trình lần sau sẽ dùng lại mặc định `3456`. Nhật ký hiển thị trong dashboard cũng chỉ lưu trong bộ nhớ của lần chạy hiện tại.

Dữ liệu tài khoản và thư vẫn nằm trong `mail_data` tính từ thư mục chạy lệnh.

### Tìm địa chỉ IP LAN của máy chủ trên Windows

Chạy `ipconfig`, tìm địa chỉ IPv4 của bộ điều hợp đang kết nối mạng LAN (thường có dạng `192.168.x.x` hoặc `10.x.x.x`). Không nhập `127.0.0.1` hoặc `localhost` trên các máy khách khác.

### Tường lửa Windows

Nếu máy khách không kết nối được, Cho phép Java hoặc mở cổng TCP mail hiện tại trên máy chủ trong cấu hình Windows Defender Firewall. Chỉ mở cho mạng riêng/LAN đáng tin cậy; không mở cổng quản trị `8080` ra LAN. Với cổng mặc định, có thể tạo quy tắc bằng PowerShell chạy với quyền quản trị:

```powershell
New-NetFirewallRule -DisplayName "MailServer TCP 3456" -Direction Inbound -Protocol TCP -LocalPort 3456 -Action Allow -Profile Private
```

## Chạy máy khách

Trên mỗi máy khách, chạy:

```powershell
java MailClient
```

Trong giao diện:

1. Nhập IP LAN của máy chủ và cổng `3456` ở thanh phía trên.
2. Chọn **Kiểm tra kết nối** để xác nhận máy khách truy cập được máy chủ.
3. Ở mục **Tài khoản**, nhập tên tài khoản và mật khẩu. Mật khẩu phải dài từ 8 đến 128 ký tự.
4. Chọn **Đăng ký** để tạo tài khoản hoặc **Đăng nhập** để vào hộp thư. Tài khoản đăng nhập sai tên hoặc mật khẩu sẽ nhận cùng một thông báo chung.
5. Dùng **Hộp thư** để xem thư và **Soạn thư** để gửi thư cho tài khoản đã tạo trên máy chủ.

Máy khách trong cùng LAN dùng chung dữ liệu trên một MailServer. Nếu đổi cổng máy chủ, cần sửa cổng đang dùng trên giao diện máy khách cho khớp.

Tài khoản cũ được tạo trước khi thêm mật khẩu vẫn giữ nguyên thư: đăng ký lại đúng tên tài khoản cũ để đặt mật khẩu cho tài khoản đó, sau đó đăng nhập. Vì tài khoản cũ chưa từng được bảo vệ bằng mật khẩu, bất kỳ người nào kết nối được tới máy chủ đều có thể đặt mật khẩu trước chủ tài khoản; chỉ nên thực hiện trên mạng LAN đáng tin cậy. Mỗi lần chạy MailServer sẽ tạo phiên đăng nhập mới; đăng xuất hoặc khởi động lại server sẽ vô hiệu hóa phiên đó.

## Giới hạn bảo mật

Mật khẩu được lưu trên máy chủ dưới dạng PBKDF2-HMAC-SHA256 với salt ngẫu nhiên; mật khẩu rõ không được ghi vào tệp tài khoản. Tuy nhiên, giao thức TCP hiện không mã hóa mật khẩu, token phiên hoặc nội dung thư khi truyền qua mạng. Đây là ứng dụng học tập: chỉ sử dụng trên mạng LAN riêng đáng tin cậy; không đưa máy chủ lên Internet hoặc dùng để trao đổi thông tin nhạy cảm.
