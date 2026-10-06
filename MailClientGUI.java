import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Insets;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;

public class MailClientGUI extends JFrame {
    private static final long serialVersionUID = 1L;
    private static final Color NAVY = new Color(25, 43, 72);
    private static final Color ACCENT = new Color(54, 118, 226);
    private static final Color ACCENT_DARK = new Color(37, 92, 191);
    private static final Color BACKGROUND = new Color(242, 245, 250);
    private static final Color TEXT = new Color(34, 48, 69);
    private static final Color MUTED = new Color(111, 125, 145);
    private static final Color BORDER = new Color(224, 230, 239);
    private static final Font BODY_FONT = new Font("Segoe UI", Font.PLAIN, 14);

    private final JTextField hostField = new JTextField(16);
    private final JTextField portField = new JTextField("3456", 5);
    private final JLabel connectionStatus = new JLabel("Chưa kiểm tra kết nối");
    private final JTextField usernameField = new JTextField(22);
    private final JPasswordField passwordField = new JPasswordField(22);
    private final JPasswordField confirmPasswordField = new JPasswordField(22);
    private final JLabel accountStatus = new JLabel("Chưa đăng nhập");
    private final JTextField recipientField = new JTextField(28);
    private final JTextField subjectField = new JTextField(28);
    private final JTextArea bodyField = new JTextArea(10, 28);
    private final JTextArea emailContent = new JTextArea();
    private final DefaultListModel<String> inboxModel = new DefaultListModel<>();
    private final JList<String> inboxList = new JList<>(inboxModel);
    private final CardLayout pageLayout = new CardLayout();
    private final JPanel pages = new JPanel(pageLayout);
    private final transient Map<String, JButton> navigationButtons = new HashMap<>();
    private final JLabel signedInLabel = new JLabel("●  Chưa đăng nhập");
    private final JButton logoutButton = new JButton("Đăng xuất");

    private String currentUsername;
    private String sessionToken;

    public MailClientGUI() {
        super("Mail Client - Mạng LAN");
        configureLookAndFeel();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(860, 570));
        setSize(1000, 680);
        setLocationRelativeTo(null);
        buildInterface();
    }

    private void buildInterface() {
        getContentPane().setBackground(BACKGROUND);
        setLayout(new BorderLayout());
        add(buildHeader(), BorderLayout.NORTH);
        add(buildNavigation(), BorderLayout.WEST);

        pages.setBackground(BACKGROUND);
        pages.setBorder(new EmptyBorder(30, 34, 30, 34));
        pages.add(buildAccountPage(), "account");
        pages.add(buildInboxPage(), "inbox");
        pages.add(buildComposePage(), "compose");
        add(pages, BorderLayout.CENTER);
        showPage("account");
    }

    private JPanel buildHeader() {
        JPanel header = new GradientPanel();
        header.setLayout(new BorderLayout(24, 0));
        header.setBorder(new EmptyBorder(15, 28, 15, 28));

        JPanel brand = new JPanel();
        brand.setLayout(new javax.swing.BoxLayout(brand, javax.swing.BoxLayout.Y_AXIS));
        brand.setOpaque(false);
        JLabel title = new JLabel("✉  MAIL");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("Segoe UI", Font.BOLD, 23));
        JLabel tagline = new JLabel("LAN MESSAGE CENTER");
        tagline.setForeground(new Color(190, 208, 235));
        tagline.setFont(new Font("Segoe UI", Font.BOLD, 10));
        brand.add(title);
        brand.add(tagline);
        header.add(brand, BorderLayout.WEST);

        JPanel connection = new JPanel(new BorderLayout(12, 0));
        connection.setOpaque(false);
        JPanel fields = new JPanel(new GridLayout(2, 1, 0, 3));
        fields.setOpaque(false);
        fields.add(headerLabel("MÁY CHỦ"));
        JPanel inputs = new JPanel(new BorderLayout(8, 0));
        inputs.setOpaque(false);
        hostField.setToolTipText("Nhập địa chỉ IPv4 LAN của máy đang chạy MailServer");
        styleTextField(hostField);
        styleTextField(portField);
        hostField.setPreferredSize(new Dimension(155, 34));
        portField.setPreferredSize(new Dimension(62, 34));
        JPanel hostGroup = new JPanel(new BorderLayout(6, 0));
        hostGroup.setOpaque(false);
        JLabel ipPrefix = new JLabel("IP");
        ipPrefix.setForeground(new Color(218, 228, 244));
        hostGroup.add(ipPrefix, BorderLayout.WEST);
        hostGroup.add(hostField, BorderLayout.CENTER);
        JPanel portGroup = new JPanel(new BorderLayout(6, 0));
        portGroup.setOpaque(false);
        JLabel portPrefix = new JLabel("CỔNG");
        portPrefix.setForeground(new Color(218, 228, 244));
        portGroup.add(portPrefix, BorderLayout.WEST);
        portGroup.add(portField, BorderLayout.CENTER);
        inputs.add(hostGroup, BorderLayout.CENTER);
        inputs.add(portGroup, BorderLayout.EAST);
        fields.add(inputs);
        connection.add(fields, BorderLayout.CENTER);

        JButton checkButton = new JButton("Kiểm tra kết nối");
        styleSecondaryButton(checkButton);
        checkButton.addActionListener(event -> checkConnection());
        connection.add(checkButton, BorderLayout.EAST);

        connectionStatus.setForeground(new Color(220, 230, 242));
        connectionStatus.setHorizontalAlignment(SwingConstants.RIGHT);
        connectionStatus.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        connection.add(connectionStatus, BorderLayout.SOUTH);
        header.add(connection, BorderLayout.EAST);
        return header;
    }

    private JPanel buildNavigation() {
        JPanel navigation = new JPanel();
        navigation.setLayout(new javax.swing.BoxLayout(navigation, javax.swing.BoxLayout.Y_AXIS));
        navigation.setBackground(Color.WHITE);
        navigation.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, BORDER),
                new EmptyBorder(24, 14, 18, 14)));
        navigation.setPreferredSize(new Dimension(194, 0));

        JLabel navTitle = new JLabel("MENU");
        navTitle.setForeground(MUTED);
        navTitle.setFont(new Font("Segoe UI", Font.BOLD, 11));
        navTitle.setBorder(new EmptyBorder(0, 11, 14, 0));
        navigation.add(navTitle);
        navigation.add(navButton("♙", "Tài khoản", "account"));
        navigation.add(navButton("▣", "Hộp thư", "inbox"));
        navigation.add(navButton("✎", "Soạn thư", "compose"));
        navigation.add(javax.swing.Box.createVerticalGlue());
        signedInLabel.setForeground(MUTED);
        signedInLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        signedInLabel.setBorder(new EmptyBorder(14, 10, 4, 4));
        navigation.add(signedInLabel);
        styleSecondaryButton(logoutButton);
        logoutButton.setAlignmentX(LEFT_ALIGNMENT);
        logoutButton.setVisible(false);
        logoutButton.addActionListener(event -> logout());
        navigation.add(logoutButton);
        return navigation;
    }

    private JButton navButton(String icon, String label, String page) {
        JButton button = new JButton("  " + icon + "    " + label);
        button.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        button.setPreferredSize(new Dimension(160, 46));
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setBorder(new EmptyBorder(8, 10, 8, 8));
        navigationButtons.put(page, button);
        button.addActionListener(event -> showPage(page));
        return button;
    }

    private JLabel headerLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(new Color(190, 208, 235));
        label.setFont(new Font("Segoe UI", Font.BOLD, 10));
        return label;
    }

    private JPanel buildAccountPage() {
        JPanel page = pagePanel();
        page.add(heading("Tài khoản", "Tạo tài khoản mới hoặc đăng nhập vào hộp thư trên máy chủ LAN."));

        JPanel form = cardPanel();
        form.setMaximumSize(new Dimension(660, 390));
        addLabeledField(form, "Tên tài khoản", usernameField);
        passwordField.setToolTipText("Mật khẩu có từ 8 đến 128 ký tự.");
        addLabeledField(form, "Mật khẩu", passwordField);
        addLabeledField(form, "Xác nhận mật khẩu (khi đăng ký)", confirmPasswordField);
        JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.setAlignmentX(LEFT_ALIGNMENT);
        actions.setBorder(new EmptyBorder(16, 0, 4, 0));
        JButton createButton = primaryButton("Đăng ký");
        createButton.addActionListener(event -> createAccount());
        JButton loginButton = new JButton("Đăng nhập");
        styleSecondaryButton(loginButton);
        loginButton.addActionListener(event -> login());
        actions.add(createButton);
        actions.add(loginButton);
        form.add(actions);
        accountStatus.setFont(BODY_FONT);
        accountStatus.setForeground(MUTED);
        accountStatus.setBorder(new EmptyBorder(10, 4, 0, 4));
        form.add(accountStatus);
        page.add(form);
        page.add(infoLabel("Mật khẩu phải dài từ 8 đến 128 ký tự. Tài khoản cũ có thể đặt mật khẩu bằng cách đăng ký lại đúng tên; hộp thư hiện có được giữ nguyên."));
        return page;
    }

    private JPanel buildInboxPage() {
        JPanel page = pagePanel();
        page.add(heading("Hộp thư", "Chọn một thư để xem nội dung."));

        JButton refreshButton = new JButton("Làm mới");
        styleSecondaryButton(refreshButton);
        refreshButton.addActionListener(event -> refreshInbox());
        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setOpaque(false);
        toolbar.setBorder(new EmptyBorder(0, 0, 12, 0));
        toolbar.add(refreshButton, BorderLayout.EAST);
        page.add(toolbar);

        inboxList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        inboxList.setFont(BODY_FONT);
        inboxList.setFixedCellHeight(44);
        inboxList.setBackground(Color.WHITE);
        inboxList.setSelectionBackground(new Color(230, 239, 255));
        inboxList.setSelectionForeground(NAVY);
        inboxList.setBorder(new EmptyBorder(6, 8, 6, 8));
        inboxList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && inboxList.getSelectedValue() != null) {
                readSelectedEmail();
            }
        });

        emailContent.setEditable(false);
        emailContent.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        emailContent.setLineWrap(true);
        emailContent.setWrapStyleWord(true);
        emailContent.setBorder(new EmptyBorder(12, 12, 12, 12));
        JScrollPane listScroll = new JScrollPane(inboxList);
        JScrollPane contentScroll = new JScrollPane(emailContent);
        styleScrollPane(listScroll);
        styleScrollPane(contentScroll);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listScroll, contentScroll);
        split.setResizeWeight(0.30);
        split.setBorder(BorderFactory.createLineBorder(BORDER));
        split.setDividerSize(8);
        split.setBackground(BACKGROUND);
        split.setPreferredSize(new Dimension(650, 400));
        page.add(split);
        return page;
    }

    private JPanel buildComposePage() {
        JPanel page = pagePanel();
        page.add(heading("Soạn thư", "Nội dung có thể gồm nhiều dòng."));

        JPanel form = cardPanel();
        addLabeledField(form, "Người nhận", recipientField);
        addLabeledField(form, "Tiêu đề", subjectField);
        bodyField.setFont(BODY_FONT);
        bodyField.setLineWrap(true);
        bodyField.setWrapStyleWord(true);
        bodyField.setBorder(new EmptyBorder(8, 8, 8, 8));
        JScrollPane bodyScroll = new JScrollPane(bodyField);
        bodyScroll.setPreferredSize(new Dimension(450, 210));
        styleScrollPane(bodyScroll);
        addLabeledField(form, "Nội dung", bodyScroll);
        JButton sendButton = primaryButton("Gửi thư");
        sendButton.addActionListener(event -> sendEmail());
        JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.setAlignmentX(LEFT_ALIGNMENT);
        actions.setBorder(new EmptyBorder(16, 0, 0, 0));
        actions.add(sendButton);
        form.add(actions);
        page.add(form);
        return page;
    }

    private JPanel pagePanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS));
        panel.setBackground(BACKGROUND);
        return panel;
    }

    private JPanel cardPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS));
        panel.setBackground(Color.WHITE);
        panel.setAlignmentX(LEFT_ALIGNMENT);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                new EmptyBorder(22, 24, 22, 24)));
        return panel;
    }

    private JLabel heading(String title, String subtitle) {
        JLabel label = new JLabel("<html><div style='font-size:25px;font-weight:bold;color:#192b48'>"
                + title + "</div><div style='font-size:13px;color:#6f7d91;margin-top:6px'>"
                + subtitle + "</div></html>");
        label.setBorder(new EmptyBorder(0, 0, 22, 0));
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }

    private JLabel infoLabel(String text) {
        JLabel label = new JLabel("<html>" + text + "</html>");
        label.setForeground(MUTED);
        label.setBorder(new EmptyBorder(16, 4, 0, 4));
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }

    private void addLabeledField(JPanel parent, String label, JComponent field) {
        JLabel caption = new JLabel(label);
        caption.setFont(new Font("Segoe UI", Font.BOLD, 13));
        caption.setForeground(TEXT);
        caption.setBorder(new EmptyBorder(12, 2, 7, 2));
        caption.setAlignmentX(LEFT_ALIGNMENT);
        parent.add(caption);
        field.setFont(BODY_FONT);
        if (field instanceof JTextField) {
            styleTextField((JTextField) field);
        }
        if (field instanceof JScrollPane) {
            ((JScrollPane) field).setAlignmentX(LEFT_ALIGNMENT);
        }
        field.setMaximumSize(new Dimension(600, Math.max(38, field.getPreferredSize().height)));
        field.setAlignmentX(LEFT_ALIGNMENT);
        parent.add(field);
    }

    private JButton primaryButton(String label) {
        JButton button = new JButton(label);
        button.setFont(new Font("Segoe UI", Font.BOLD, 14));
        button.setMargin(new Insets(10, 18, 10, 18));
        button.setBackground(ACCENT);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setOpaque(true);
        return button;
    }

    private void styleSecondaryButton(JButton button) {
        button.setFont(new Font("Segoe UI", Font.BOLD, 12));
        button.setForeground(NAVY);
        button.setBackground(new Color(246, 248, 252));
        button.setMargin(new Insets(8, 13, 8, 13));
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                new EmptyBorder(3, 5, 3, 5)));
    }

    private void styleTextField(JTextField field) {
        field.setFont(BODY_FONT);
        field.setForeground(TEXT);
        field.setBackground(Color.WHITE);
        field.setCaretColor(ACCENT);
        field.setMargin(new Insets(7, 10, 7, 10));
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                new EmptyBorder(2, 4, 2, 4)));
    }

    private void styleScrollPane(JScrollPane pane) {
        pane.setBorder(BorderFactory.createLineBorder(BORDER));
        pane.getViewport().setBackground(Color.WHITE);
    }

    private void configureLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception exception) {
            System.err.println("Could not load the system look and feel: " + exception.getMessage());
        }
    }

    private final class GradientPanel extends JPanel {
        private static final long serialVersionUID = 1L;

        private GradientPanel() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D graphics2D = (Graphics2D) graphics.create();
            graphics2D.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics2D.setPaint(new GradientPaint(0, 0, NAVY, getWidth(), getHeight(), ACCENT_DARK));
            graphics2D.fillRect(0, 0, getWidth(), getHeight());
            graphics2D.dispose();
            super.paintComponent(graphics);
        }
    }

    private void showPage(String page) {
        if (("inbox".equals(page) || "compose".equals(page)) && currentUsername == null) {
            JOptionPane.showMessageDialog(this, "Đăng nhập trước để sử dụng chức năng này.",
                    "Chưa đăng nhập", JOptionPane.INFORMATION_MESSAGE);
            page = "account";
        }
        pageLayout.show(pages, page);
        for (Map.Entry<String, JButton> entry : navigationButtons.entrySet()) {
            boolean selected = entry.getKey().equals(page);
            JButton button = entry.getValue();
            button.setBackground(selected ? new Color(235, 242, 255) : Color.WHITE);
            button.setForeground(selected ? ACCENT_DARK : MUTED);
            button.setFont(new Font("Segoe UI", selected ? Font.BOLD : Font.PLAIN, 14));
            button.setBorder(selected
                    ? BorderFactory.createCompoundBorder(
                            BorderFactory.createMatteBorder(0, 3, 0, 0, ACCENT),
                            new EmptyBorder(8, 7, 8, 8))
                    : new EmptyBorder(8, 10, 8, 8));
        }
    }

    private void checkConnection() {
        final InetSocketAddress address;
        try {
            address = new InetSocketAddress(hostField.getText().trim(), readPort());
            if (hostField.getText().trim().isEmpty()) {
                throw new IllegalArgumentException("Hãy nhập IP LAN của máy chủ.");
            }
        } catch (IllegalArgumentException exception) {
            showError(exception.getMessage());
            return;
        }

        setConnectionStatus("Đang kiểm tra...", new Color(255, 224, 145));
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws IOException {
                try (Socket socket = new Socket()) {
                    socket.connect(address, 4000);
                }
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    setConnectionStatus("Đã kết nối tới " + address.getHostString(),
                            new Color(170, 240, 202));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    setConnectionStatus("Kiểm tra bị gián đoạn", new Color(255, 190, 190));
                    showError("Kiểm tra kết nối bị gián đoạn.");
                } catch (ExecutionException exception) {
                    setConnectionStatus("Không kết nối được", new Color(255, 190, 190));
                    showError("Không thể kết nối tới máy chủ: " + rootMessage(exception));
                }
            }
        }.execute();
    }

    private int readPort() {
        try {
            int port = Integer.parseInt(portField.getText().trim());
            if (port < 1 || port > 65535) {
                throw new NumberFormatException();
            }
            return port;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Cổng phải là số từ 1 đến 65535.");
        }
    }

    private void createAccount() {
        String username = usernameField.getText().trim();
        if (username.isEmpty()) {
            showError("Hãy nhập tên tài khoản.");
            return;
        }
        char[] password = passwordField.getPassword();
        char[] confirmation = confirmPasswordField.getPassword();
        if (password.length < 8 || password.length > 128) {
            Arrays.fill(password, '\0');
            Arrays.fill(confirmation, '\0');
            showError("Mật khẩu phải dài từ 8 đến 128 ký tự.");
            return;
        }
        if (!Arrays.equals(password, confirmation)) {
            Arrays.fill(password, '\0');
            Arrays.fill(confirmation, '\0');
            showError("Mật khẩu xác nhận không khớp.");
            return;
        }
        Map<String, String> parameters = new HashMap<>();
        parameters.put("username", username);
        parameters.put("password64", encodePassword(password));
        Arrays.fill(password, '\0');
        Arrays.fill(confirmation, '\0');
        request("CREATE_ACCOUNT", parameters, response -> {
            String status = response.get(0);
            if ("ACCOUNT_CREATED".equals(status)) {
                accountStatus.setText("Đã tạo tài khoản. Hãy đăng nhập bằng mật khẩu vừa đăng ký.");
                accountStatus.setForeground(new Color(36, 137, 94));
                passwordField.setText("");
                confirmPasswordField.setText("");
            } else if ("ACCOUNT_SECURED".equals(status)) {
                accountStatus.setText("Đã đặt mật khẩu cho tài khoản cũ; hộp thư được giữ nguyên.");
                accountStatus.setForeground(new Color(36, 137, 94));
                passwordField.setText("");
                confirmPasswordField.setText("");
            } else if ("ACCOUNT_EXISTS".equals(status)) {
                accountStatus.setText("Tài khoản đã tồn tại. Hãy đăng nhập.");
                accountStatus.setForeground(new Color(185, 72, 72));
            } else if ("INVALID_PASSWORD".equals(status)) {
                showError("Mật khẩu phải dài từ 8 đến 128 ký tự và không chứa ký tự điều khiển.");
            } else {
                showError("Máy chủ phản hồi: " + status);
            }
        });
    }

    private void login() {
        String username = usernameField.getText().trim();
        if (username.isEmpty()) {
            showError("Hãy nhập tên tài khoản.");
            return;
        }
        char[] password = passwordField.getPassword();
        if (password.length == 0) {
            showError("Hãy nhập mật khẩu.");
            Arrays.fill(password, '\0');
            return;
        }
        Map<String, String> parameters = new HashMap<>();
        parameters.put("username", username);
        parameters.put("password64", encodePassword(password));
        Arrays.fill(password, '\0');
        request("LOGIN", parameters, response -> {
            if ("LOGIN_SUCCESS".equals(response.get(0))) {
                if (response.size() < 2 || !response.get(1).startsWith("SESSION_TOKEN:")) {
                    showError("Phản hồi đăng nhập từ máy chủ không hợp lệ.");
                    return;
                }
                currentUsername = username;
                sessionToken = response.get(1).substring("SESSION_TOKEN:".length());
                accountStatus.setText("Đã đăng nhập: " + currentUsername);
                accountStatus.setForeground(new Color(36, 137, 94));
                signedInLabel.setText("●  " + currentUsername);
                signedInLabel.setForeground(new Color(36, 137, 94));
                logoutButton.setVisible(true);
                passwordField.setText("");
                confirmPasswordField.setText("");
                setInbox(response.subList(2, response.size()));
                showPage("inbox");
            } else if ("LOGIN_FAILED".equals(response.get(0))) {
                passwordField.setText("");
                accountStatus.setText("Tên tài khoản hoặc mật khẩu không đúng.");
                accountStatus.setForeground(new Color(185, 72, 72));
            } else {
                showError("Máy chủ phản hồi: " + response.get(0));
            }
        });
    }

    private void refreshInbox() {
        if (currentUsername == null) {
            showPage("inbox");
            return;
        }
        Map<String, String> parameters = new HashMap<>();
        parameters.put("token", sessionToken);
        request("LIST_MAIL", parameters, response -> {
            if ("LIST_SUCCESS".equals(response.get(0))) {
                setInbox(response.subList(1, response.size()));
            } else if ("AUTH_REQUIRED".equals(response.get(0))) {
                expireSession();
            } else {
                showError("Máy chủ phản hồi: " + response.get(0));
            }
        });
    }

    private void setInbox(List<String> fileNames) {
        inboxModel.clear();
        for (String fileName : fileNames) {
            inboxModel.addElement(fileName);
        }
        emailContent.setText(fileNames.isEmpty() ? "Hộp thư chưa có thư." : "");
    }

    private void readSelectedEmail() {
        if (currentUsername == null) {
            return;
        }
        String fileName = inboxList.getSelectedValue();
        if (fileName == null) {
            return;
        }
        Map<String, String> parameters = new HashMap<>();
        parameters.put("token", sessionToken);
        parameters.put("filename", fileName);
        request("READ_EMAIL", parameters, response -> {
            if ("EMAIL_CONTENT".equals(response.get(0)) && response.size() > 1) {
                try {
                    byte[] decoded = Base64.getDecoder().decode(response.get(1));
                    emailContent.setText(new String(decoded, StandardCharsets.UTF_8));
                    emailContent.setCaretPosition(0);
                } catch (IllegalArgumentException exception) {
                    showError("Nội dung thư từ máy chủ không đúng định dạng.");
                }
            } else if ("FILE_NOT_FOUND".equals(response.get(0))) {
                showError("Không tìm thấy thư trên máy chủ.");
                refreshInbox();
            } else if ("AUTH_REQUIRED".equals(response.get(0))) {
                expireSession();
            } else {
                showError("Máy chủ phản hồi: " + response.get(0));
            }
        });
    }

    private void sendEmail() {
        if (currentUsername == null) {
            showPage("compose");
            return;
        }
        String recipient = recipientField.getText().trim();
        String subject = subjectField.getText().trim();
        String body = bodyField.getText();
        if (recipient.isEmpty() || subject.isEmpty() || body.trim().isEmpty()) {
            showError("Hãy điền người nhận, tiêu đề và nội dung thư.");
            return;
        }

        Map<String, String> parameters = new HashMap<>();
        parameters.put("token", sessionToken);
        parameters.put("to", recipient);
        parameters.put("subject", subject);
        parameters.put("body64", Base64.getEncoder().encodeToString(body.getBytes(StandardCharsets.UTF_8)));
        request("SEND_EMAIL", parameters, response -> {
            if ("EMAIL_SENT".equals(response.get(0))) {
                JOptionPane.showMessageDialog(this, "Đã gửi thư tới " + recipient + ".",
                        "Gửi thư thành công", JOptionPane.INFORMATION_MESSAGE);
                recipientField.setText("");
                subjectField.setText("");
                bodyField.setText("");
            } else if ("RECIPIENT_NOT_FOUND".equals(response.get(0))) {
                showError("Tài khoản người nhận không tồn tại.");
            } else if ("AUTH_REQUIRED".equals(response.get(0))) {
                expireSession();
            } else {
                showError("Máy chủ phản hồi: " + response.get(0));
            }
        });
    }

    private String encodePassword(char[] password) {
        byte[] passwordBytes = new String(password).getBytes(StandardCharsets.UTF_8);
        try {
            return Base64.getEncoder().encodeToString(passwordBytes);
        } finally {
            Arrays.fill(passwordBytes, (byte) 0);
        }
    }

    private void logout() {
        if (sessionToken != null) {
            Map<String, String> parameters = new HashMap<>();
            parameters.put("token", sessionToken);
            request("LOGOUT", parameters, response -> {
                if (!"LOGOUT_SUCCESS".equals(response.get(0)) && !"AUTH_REQUIRED".equals(response.get(0))) {
                    showError("Máy chủ phản hồi: " + response.get(0));
                }
            });
        }
        expireSession();
    }

    private void expireSession() {
        currentUsername = null;
        sessionToken = null;
        accountStatus.setText("Chưa đăng nhập");
        accountStatus.setForeground(MUTED);
        signedInLabel.setText("●  Chưa đăng nhập");
        signedInLabel.setForeground(MUTED);
        logoutButton.setVisible(false);
        setInbox(List.of());
        showPage("account");
    }

    private void request(String command, Map<String, String> parameters, Consumer<List<String>> onResponse) {
        final String host = hostField.getText().trim();
        final int port;
        try {
            if (host.isEmpty()) {
                throw new IllegalArgumentException("Nhập IP LAN của máy chủ ở thanh trên.");
            }
            port = readPort();
        } catch (IllegalArgumentException exception) {
            showError(exception.getMessage());
            return;
        }

        new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() throws IOException {
                return MailClient.sendRequest(host, port, command, parameters);
            }

            @Override
            protected void done() {
                try {
                    List<String> response = get();
                    if (response.isEmpty()) {
                        setConnectionStatus("Máy chủ không phản hồi", new Color(255, 190, 190));
                        showError("Máy chủ đóng kết nối mà không gửi phản hồi.");
                        return;
                    }
                    setConnectionStatus("Đã kết nối tới " + host, new Color(170, 240, 202));
                    onResponse.accept(response);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    setConnectionStatus("Yêu cầu bị gián đoạn", new Color(255, 190, 190));
                    showError("Yêu cầu bị gián đoạn.");
                } catch (ExecutionException exception) {
                    setConnectionStatus("Không kết nối được", new Color(255, 190, 190));
                    showError("Không thể trao đổi dữ liệu với máy chủ: " + rootMessage(exception));
                }
            }
        }.execute();
    }

    private String rootMessage(Exception exception) {
        Throwable cause = exception.getCause();
        return cause == null || cause.getMessage() == null ? exception.getMessage() : cause.getMessage();
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Có lỗi", JOptionPane.ERROR_MESSAGE);
    }

    private void setConnectionStatus(String message, Color color) {
        connectionStatus.setText("●  " + message);
        connectionStatus.setForeground(color);
    }
}
