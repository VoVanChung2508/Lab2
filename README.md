# Mail Client qua mạng LAN

Ứng dụng Java gồm một máy chủ mail và giao diện GUI Swing cho các máy khách. MailServer giữ tài khoản và thư trong thư mục `mail_data`; các máy khách phải kết nối đến IP LAN của máy đang chạy máy chủ, không dùng `localhost`.

## Yêu cầu

- JDK được cài trên máy chủ và các máy khách.
- Các máy nằm trong cùng mạng LAN và có thể kết nối với nhau.
- Cho phép gói UDP đến cổng `3456` trên máy chủ để nhận yêu cầu mail và broadcast tìm máy chủ.
- Cho phép multicast UDP đến cổng `3457` trên các máy khách để nhận thông báo thư mới.

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

MailServer tự khởi động dịch vụ mail trên cổng UDP `3456` và mở dashboard quản trị tại:

```text
http://127.0.0.1:8080
```

Nếu cổng `8080` đang được sử dụng, MailServer sẽ thử các cổng tiếp theo đến `8089`; khi đó dùng địa chỉ được in trong cửa sổ chương trình. Giữ chương trình đang chạy trong lúc sử dụng. Tệp `MailServerDashboard.html` phải nằm trên classpath (khi chạy theo hướng dẫn trên, để tệp này cùng thư mục với các file `.class`). Dashboard chỉ lắng nghe trên máy chủ cục bộ; không thể mở từ máy khách trong LAN.

Có thể truyền cổng mail khác khi khởi động bằng `java MailServer 3457`; nếu bỏ tham số, cổng mặc định là `3456`.

Dashboard cho phép xem trạng thái, khởi động/dừng/khởi động lại dịch vụ mail, đổi cổng UDP và xem nhật ký hoạt động gần đây. Dừng dịch vụ mail không tắt dashboard, nên có thể khởi động lại từ trình duyệt. Đổi cổng sẽ khởi động lại dịch vụ nếu dịch vụ đang chạy; sau đó cập nhật cổng tương ứng trên MailClient và quy tắc tường lửa nếu có. Cổng được đổi chỉ có hiệu lực trong lần chạy MailServer hiện tại; khởi động chương trình lần sau sẽ dùng lại mặc định `3456`. Nhật ký hiển thị trong dashboard cũng chỉ lưu trong bộ nhớ của lần chạy hiện tại.

MailServer trả lời yêu cầu broadcast tìm máy chủ trên cổng mail hiện tại. Khi thư nội bộ được lưu thành công, server gửi một thông báo multicast tới nhóm `239.255.42.99:3457`. Cổng thông báo multicast này cố định, không đổi theo cổng mail trong dashboard.

Dữ liệu tài khoản và thư vẫn nằm trong `mail_data` tính từ thư mục chạy lệnh.

### Tìm địa chỉ IP LAN của máy chủ trên Windows

Chạy `ipconfig`, tìm địa chỉ IPv4 của bộ điều hợp đang kết nối mạng LAN (thường có dạng `192.168.x.x` hoặc `10.x.x.x`). Không nhập `127.0.0.1` hoặc `localhost` trên các máy khách khác.

### Tường lửa Windows

Nếu máy khách không kết nối được, cho phép Java hoặc mở cổng UDP mail hiện tại trên máy chủ trong cấu hình Windows Defender Firewall. Chỉ mở cho mạng riêng/LAN đáng tin cậy; không mở cổng quản trị `8080` ra LAN. Với cổng mặc định, có thể tạo quy tắc bằng PowerShell chạy với quyền quản trị:

```powershell
New-NetFirewallRule -DisplayName "MailServer UDP 3456" -Direction Inbound -Protocol UDP -LocalPort 3456 -Action Allow -Profile Private
```

Cho phép thêm UDP `3457` cho multicast trên máy khách nếu thông báo thư mới không đến được. Có thể tạo quy tắc inbound trên từng máy khách bằng PowerShell chạy với quyền quản trị:

```powershell
New-NetFirewallRule -DisplayName "MailClient UDP Multicast 3457" -Direction Inbound -Protocol UDP -LocalPort 3457 -Action Allow -Profile Private
```

Broadcast discovery và multicast notification có thể bị chặn bởi firewall, Wi-Fi client isolation, router hoặc VLAN. Broadcast không đi qua router sang subnet khác; trong trường hợp đó, nhập IP và cổng máy chủ thủ công.

## Chạy máy khách

Trên mỗi máy khách, chạy:

```powershell
java MailClient
```

Trong giao diện:

1. Chọn **Tìm máy chủ** để dò các MailServer trong cùng mạng LAN; nếu tìm thấy nhiều server, chọn một server trong danh sách. Có thể nhập IP LAN và cổng thủ công nếu discovery không hoạt động.
2. Chọn **Kiểm tra** để xác nhận máy khách nhận được phản hồi từ máy chủ.
3. Ở mục **Tài khoản**, nhập tên tài khoản và mật khẩu. Mật khẩu được hiển thị khi nhập, dài từ 8 đến 128 ký tự.
4. Chọn **Đăng ký** để tạo tài khoản hoặc **Đăng nhập** để vào hộp thư. Tài khoản đăng nhập sai tên hoặc mật khẩu sẽ nhận cùng một thông báo chung.
5. Client đang đăng nhập tham gia nhóm multicast để tự làm mới hộp thư khi có thư mới gửi đến tài khoản đó; khi đăng xuất, client rời nhóm.
6. Dùng **Hộp thư** để xem thư và **Soạn thư** để gửi thư cho tài khoản đã tạo trên máy chủ. Có thể nhập nhiều username, ngăn cách bằng dấu phẩy, hoặc tạo nhóm nhận dùng lại.

### Tạo nhóm người nhận và gửi thư cho nhiều người

1. Đăng nhập, mở **Soạn thư**, rồi chọn **Quản lý nhóm…**.
2. Chọn **Nhóm mới**, nhập tên nhóm và danh sách username LAN, ngăn cách bằng dấu phẩy; sau đó chọn **Lưu nhóm**. Các username phải là tài khoản đã được tạo trên cùng MailServer.
3. Danh sách nhóm được lưu riêng trong thư mục tài khoản trên máy chủ. Có thể chọn một nhóm đã có để sửa hoặc xóa; mỗi tài khoản lưu tối đa 20 nhóm, mỗi nhóm tối đa 50 người nhận.
4. Khi soạn thư, chọn nhóm ở danh sách **Chọn nhóm người nhận**. Thành viên sẽ được điền vào ô Người nhận và có thể chỉnh sửa trước khi gửi. Hoặc nhập trực tiếp nhiều username, ngăn cách bằng dấu phẩy, dấu chấm phẩy hoặc khoảng trắng.
5. Chọn **Gửi thư**. Server kiểm tra toàn bộ người nhận trước; nếu hợp lệ, lưu một bản thư vào hộp thư của từng người và gửi multicast báo thư mới riêng cho từng tài khoản. Nếu có username không tồn tại, thư không được gửi tới bất kỳ thành viên nào.

Nhóm là danh sách người nhận nội bộ, không phải địa chỉ email Internet. Multicast chỉ giúp client đang đăng nhập làm mới hộp thư; thư vẫn được lưu trên server để người nhận mở sau nếu thông báo UDP bị mất.

Máy khách trong cùng LAN dùng chung dữ liệu trên một MailServer. Nếu đổi cổng máy chủ, cần sửa cổng đang dùng trên giao diện máy khách cho khớp.

Tài khoản cũ được tạo trước khi thêm mật khẩu vẫn giữ nguyên thư: đăng ký lại đúng tên tài khoản cũ để đặt mật khẩu cho tài khoản đó, sau đó đăng nhập. Vì tài khoản cũ chưa từng được bảo vệ bằng mật khẩu, bất kỳ người nào kết nối được tới máy chủ đều có thể đặt mật khẩu trước chủ tài khoản; chỉ nên thực hiện trên mạng LAN đáng tin cậy. Mỗi lần chạy MailServer sẽ tạo phiên đăng nhập mới; đăng xuất hoặc khởi động lại server sẽ vô hiệu hóa phiên đó.

## Giới hạn bảo mật

Mật khẩu được lưu trên máy chủ dưới dạng PBKDF2-HMAC-SHA256 với salt ngẫu nhiên; mật khẩu rõ không được ghi vào tệp tài khoản. Giao thức UDP không đảm bảo gói tin đến nơi hoặc đúng thứ tự và không mã hóa mật khẩu, token phiên hay nội dung thư khi truyền qua mạng; mỗi yêu cầu phải vừa trong một gói UDP. Thông báo multicast chỉ chứa tên tài khoản người nhận (không chứa tiêu đề hoặc nội dung thư), nhưng thiết bị tham gia cùng nhóm multicast có thể đọc được tên đó; các gói discovery/notification cũng không được xác thực và có thể bị giả mạo. Đây là ứng dụng học tập: chỉ sử dụng trên mạng LAN riêng đáng tin cậy; không đưa máy chủ lên Internet hoặc dùng để trao đổi thông tin nhạy cảm.
