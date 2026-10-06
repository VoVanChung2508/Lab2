import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
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

    // ===== Modern palette =====
    private static final Color NAVY = new Color(16, 31, 55);
    private static final Color NAVY_2 = new Color(28, 53, 91);
    private static final Color ACCENT = new Color(47, 111, 237);
    private static final Color ACCENT_HOVER = new Color(36, 91, 204);
    private static final Color ACCENT_SOFT = new Color(235, 242, 255);

    private static final Color BACKGROUND = new Color(246, 248, 252);
    private static final Color SURFACE = Color.WHITE;
    private static final Color TEXT = new Color(29, 41, 57);
    private static final Color MUTED = new Color(105, 119, 140);
    private static final Color BORDER = new Color(226, 231, 239);

    private static final Color SUCCESS = new Color(27, 145, 101);
    private static final Color DANGER = new Color(197, 68, 75);
    private static final Color WARNING = new Color(222, 161, 53);

    private static final Font BODY_FONT = new Font("Segoe UI", Font.PLAIN, 14);
    private static final Font BODY_MEDIUM = new Font("Segoe UI", Font.BOLD, 13);

    // ===== Inputs =====
    private final JTextField hostField = new JTextField("127.0.0.1", 16);
    private final JTextField portField = new JTextField("3456", 5);
    private final JLabel connectionStatus = new JLabel("●  Chưa kiểm tra");

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
        super("Mail Client • LAN");
        configureLookAndFeel();
        passwordField.setEchoChar((char) 0);
        confirmPasswordField.setEchoChar((char) 0);
 
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(980, 640));
        setSize(1120, 740);
        setLocationRelativeTo(null);

        buildInterface();
    }

    // =========================================================
    // UI
    // =========================================================

    private void buildInterface() {
        getContentPane().setBackground(BACKGROUND);
        setLayout(new BorderLayout());

        add(buildHeader(), BorderLayout.NORTH);
        add(buildNavigation(), BorderLayout.WEST);

        pages.setBackground(BACKGROUND);
        pages.setBorder(new EmptyBorder(28, 32, 28, 32));
        pages.add(buildAccountPage(), "account");
        pages.add(buildInboxPage(), "inbox");
        pages.add(buildComposePage(), "compose");

        add(pages, BorderLayout.CENTER);
        showPage("account");
    }

    private JPanel buildHeader() {
        JPanel header = new GradientPanel();
        header.setLayout(new BorderLayout(24, 0));
        header.setBorder(new EmptyBorder(18, 26, 18, 26));
        header.setPreferredSize(new Dimension(0, 96));

        JPanel brand = new JPanel();
        brand.setOpaque(false);
        brand.setLayout(new BoxLayout(brand, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("MAILSPACE");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("Segoe UI", Font.BOLD, 23));

        JLabel tagline = new JLabel("LAN MAIL CLIENT");
        tagline.setForeground(new Color(185, 204, 232));
        tagline.setFont(new Font("Segoe UI", Font.BOLD, 10));

        brand.add(title);
        brand.add(Box.createVerticalStrut(2));
        brand.add(tagline);
        header.add(brand, BorderLayout.WEST);

        JPanel right = new JPanel(new BorderLayout(12, 0));
        right.setOpaque(false);

        JPanel serverBlock = new JPanel(new BorderLayout(10, 5));
        serverBlock.setOpaque(false);

        JLabel serverTitle = new JLabel("MÁY CHỦ");
        serverTitle.setForeground(new Color(185, 204, 232));
        serverTitle.setFont(new Font("Segoe UI", Font.BOLD, 10));
        serverBlock.add(serverTitle, BorderLayout.NORTH);

        JPanel serverInputs = new JPanel(new BorderLayout(8, 0));
        serverInputs.setOpaque(false);

        styleHeaderTextField(hostField);
        styleHeaderTextField(portField);
        hostField.setPreferredSize(new Dimension(170, 36));
        portField.setPreferredSize(new Dimension(72, 36));
        hostField.setToolTipText("IP máy chạy MailServer. Nếu cùng máy, dùng 127.0.0.1");

        JPanel ipGroup = smallHeaderField("IP", hostField);
        JPanel portGroup = smallHeaderField("CỔNG", portField);

        serverInputs.add(ipGroup, BorderLayout.CENTER);
        serverInputs.add(portGroup, BorderLayout.EAST);

        serverBlock.add(serverInputs, BorderLayout.CENTER);
        right.add(serverBlock, BorderLayout.CENTER);

        JButton checkButton = secondaryHeaderButton("Kiểm tra");
        checkButton.addActionListener(event -> checkConnection());
        right.add(checkButton, BorderLayout.EAST);

        connectionStatus.setForeground(new Color(214, 225, 241));
        connectionStatus.setHorizontalAlignment(SwingConstants.RIGHT);
        connectionStatus.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        right.add(connectionStatus, BorderLayout.SOUTH);

        header.add(right, BorderLayout.EAST);
        return header;
    }

    private JPanel smallHeaderField(String label, JComponent field) {
        JPanel panel = new JPanel(new BorderLayout(6, 0));
        panel.setOpaque(false);

        JLabel prefix = new JLabel(label);
        prefix.setForeground(new Color(214, 225, 241));
        prefix.setFont(new Font("Segoe UI", Font.BOLD, 10));

        panel.add(prefix, BorderLayout.WEST);
        panel.add(field, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildNavigation() {
        JPanel navigation = new JPanel();
        navigation.setLayout(new BoxLayout(navigation, BoxLayout.Y_AXIS));
        navigation.setBackground(SURFACE);
        navigation.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, BORDER),
                new EmptyBorder(24, 14, 18, 14)));
        navigation.setPreferredSize(new Dimension(210, 0));

        JLabel navTitle = new JLabel("KHÔNG GIAN LÀM VIỆC");
        navTitle.setForeground(MUTED);
        navTitle.setFont(new Font("Segoe UI", Font.BOLD, 10));
        navTitle.setBorder(new EmptyBorder(0, 10, 15, 0));
        navTitle.setAlignmentX(LEFT_ALIGNMENT);
        navigation.add(navTitle);

        navigation.add(navButton("●", "Tài khoản", "account"));
        navigation.add(Box.createVerticalStrut(5));
        navigation.add(navButton("▣", "Hộp thư", "inbox"));
        navigation.add(Box.createVerticalStrut(5));
        navigation.add(navButton("✎", "Soạn thư", "compose"));

        navigation.add(Box.createVerticalGlue());

        JPanel profile = new RoundedPanel(16, new Color(248, 250, 253));
        profile.setLayout(new BoxLayout(profile, BoxLayout.Y_AXIS));
        profile.setBorder(new EmptyBorder(12, 12, 12, 12));
        profile.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));
        profile.setAlignmentX(LEFT_ALIGNMENT);

        JLabel profileCaption = new JLabel("PHIÊN HIỆN TẠI");
        profileCaption.setForeground(MUTED);
        profileCaption.setFont(new Font("Segoe UI", Font.BOLD, 9));
        profileCaption.setAlignmentX(LEFT_ALIGNMENT);

        signedInLabel.setForeground(MUTED);
        signedInLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        signedInLabel.setAlignmentX(LEFT_ALIGNMENT);
        signedInLabel.setBorder(new EmptyBorder(7, 0, 9, 0));

        styleGhostButton(logoutButton);
        logoutButton.setAlignmentX(LEFT_ALIGNMENT);
        logoutButton.setVisible(false);
        logoutButton.addActionListener(event -> logout());

        profile.add(profileCaption);
        profile.add(signedInLabel);
        profile.add(logoutButton);

        navigation.add(profile);
        return navigation;
    }

    private JButton navButton(String icon, String label, String page) {
        JButton button = new JButton(icon + "    " + label);
        button.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        button.setPreferredSize(new Dimension(176, 46));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setFocusPainted(false);
        button.setBorder(new EmptyBorder(8, 12, 8, 10));
        button.setOpaque(true);

        navigationButtons.put(page, button);
        button.addActionListener(event -> showPage(page));
        return button;
    }

    private JPanel buildAccountPage() {
        JPanel page = pagePanel();

        page.add(heading(
                "Tài khoản",
                "Đăng ký hoặc đăng nhập để sử dụng hộp thư nội bộ trong mạng LAN."
        ));
        page.add(Box.createVerticalStrut(4));

        JPanel card = cardPanel();
        card.setMaximumSize(new Dimension(720, 430));

        JLabel cardTitle = sectionTitle("Thông tin đăng nhập");
        card.add(cardTitle);
        card.add(Box.createVerticalStrut(4));

        addLabeledField(card, "Tên tài khoản", usernameField);
        passwordField.setToolTipText("Mật khẩu từ 8 đến 128 ký tự.");
        addLabeledField(card, "Mật khẩu", passwordField);
        addLabeledField(card, "Xác nhận mật khẩu", confirmPasswordField);

        JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.setLayout(new BoxLayout(actions, BoxLayout.X_AXIS));
        actions.setAlignmentX(LEFT_ALIGNMENT);
        actions.setBorder(new EmptyBorder(20, 0, 2, 0));

        JButton loginButton = primaryButton("Đăng nhập");
        loginButton.addActionListener(event -> login());

        JButton createButton = secondaryButton("Tạo tài khoản");
        createButton.addActionListener(event -> createAccount());

        actions.add(loginButton);
        actions.add(Box.createHorizontalStrut(10));
        actions.add(createButton);
        actions.add(Box.createHorizontalGlue());

        card.add(actions);

        accountStatus.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        accountStatus.setForeground(MUTED);
        accountStatus.setBorder(new EmptyBorder(13, 2, 0, 2));
        accountStatus.setAlignmentX(LEFT_ALIGNMENT);
        card.add(accountStatus);

        page.add(card);
        page.add(Box.createVerticalStrut(14));
        page.add(infoBox(
                "Mẹo",
                "Nếu MailServer chạy trên chính máy này, dùng IP 127.0.0.1 và cổng 3456. "
                        + "Nếu chạy qua LAN, nhập IPv4 của máy chủ."
        ));

        return page;
    }

    private JPanel buildInboxPage() {
        JPanel page = pagePanel();

        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        titleRow.setAlignmentX(LEFT_ALIGNMENT);
        titleRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));

        titleRow.add(heading(
                "Hộp thư",
                "Chọn một thư ở danh sách bên trái để xem toàn bộ nội dung."
        ), BorderLayout.CENTER);

        JButton refreshButton = secondaryButton("Làm mới");
        refreshButton.addActionListener(event -> refreshInbox());

        JPanel buttonWrap = new JPanel(new BorderLayout());
        buttonWrap.setOpaque(false);
        buttonWrap.setBorder(new EmptyBorder(8, 10, 0, 0));
        buttonWrap.add(refreshButton, BorderLayout.NORTH);
        titleRow.add(buttonWrap, BorderLayout.EAST);

        page.add(titleRow);
        page.add(Box.createVerticalStrut(14));

        inboxList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        inboxList.setFont(BODY_FONT);
        inboxList.setFixedCellHeight(48);
        inboxList.setBackground(SURFACE);
        inboxList.setForeground(TEXT);
        inboxList.setSelectionBackground(ACCENT_SOFT);
        inboxList.setSelectionForeground(NAVY);
        inboxList.setBorder(new EmptyBorder(6, 8, 6, 8));

        inboxList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && inboxList.getSelectedValue() != null) {
                readSelectedEmail();
            }
        });

        emailContent.setEditable(false);
        emailContent.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        emailContent.setForeground(TEXT);
        emailContent.setBackground(SURFACE);
        emailContent.setLineWrap(true);
        emailContent.setWrapStyleWord(true);
        emailContent.setBorder(new EmptyBorder(18, 18, 18, 18));

        JScrollPane listScroll = new JScrollPane(inboxList);
        JScrollPane contentScroll = new JScrollPane(emailContent);
        styleScrollPane(listScroll);
        styleScrollPane(contentScroll);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listScroll, contentScroll);
        split.setResizeWeight(0.34);
        split.setDividerLocation(300);
        split.setBorder(BorderFactory.createLineBorder(BORDER));
        split.setDividerSize(6);
        split.setBackground(BACKGROUND);
        split.setPreferredSize(new Dimension(760, 470));
        split.setAlignmentX(LEFT_ALIGNMENT);

        page.add(split);
        return page;
    }

    private JPanel buildComposePage() {
        JPanel page = pagePanel();

        page.add(heading(
                "Soạn thư",
                "Gửi thư trực tiếp tới tài khoản khác trên cùng MailServer."
        ));
        page.add(Box.createVerticalStrut(4));

        JPanel card = cardPanel();
        card.setMaximumSize(new Dimension(760, 560));

        card.add(sectionTitle("Thư mới"));
        card.add(Box.createVerticalStrut(4));

        addLabeledField(card, "Người nhận", recipientField);
        addLabeledField(card, "Tiêu đề", subjectField);

        bodyField.setFont(BODY_FONT);
        bodyField.setForeground(TEXT);
        bodyField.setBackground(SURFACE);
        bodyField.setLineWrap(true);
        bodyField.setWrapStyleWord(true);
        bodyField.setMargin(new Insets(12, 12, 12, 12));

        JScrollPane bodyScroll = new JScrollPane(bodyField);
        bodyScroll.setPreferredSize(new Dimension(520, 230));
        styleScrollPane(bodyScroll);
        addLabeledField(card, "Nội dung", bodyScroll);

        JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.setLayout(new BoxLayout(actions, BoxLayout.X_AXIS));
        actions.setAlignmentX(LEFT_ALIGNMENT);
        actions.setBorder(new EmptyBorder(18, 0, 0, 0));

        JButton sendButton = primaryButton("Gửi thư");
        sendButton.addActionListener(event -> sendEmail());

        actions.add(sendButton);
        actions.add(Box.createHorizontalGlue());

        card.add(actions);
        page.add(card);
        return page;
    }

    private JPanel pagePanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(BACKGROUND);
        return panel;
    }

    private JPanel cardPanel() {
        JPanel panel = new RoundedPanel(20, SURFACE);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(LEFT_ALIGNMENT);
        panel.setBorder(new EmptyBorder(24, 26, 24, 26));
        return panel;
    }

    private JLabel heading(String title, String subtitle) {
        JLabel label = new JLabel(
                "<html>"
                        + "<div style='font-size:26px;font-weight:700;color:#101f37;'>"
                        + title
                        + "</div>"
                        + "<div style='font-size:13px;color:#69778c;margin-top:7px;'>"
                        + subtitle
                        + "</div>"
                        + "</html>"
        );
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }

    private JLabel sectionTitle(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.BOLD, 15));
        label.setForeground(TEXT);
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }

    private JPanel infoBox(String title, String text) {
        JPanel box = new RoundedPanel(16, new Color(238, 244, 255));
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBorder(new EmptyBorder(13, 16, 13, 16));
        box.setAlignmentX(LEFT_ALIGNMENT);
        box.setMaximumSize(new Dimension(720, 92));

        JLabel titleLabel = new JLabel(title.toUpperCase());
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 10));
        titleLabel.setForeground(ACCENT_HOVER);
        titleLabel.setAlignmentX(LEFT_ALIGNMENT);

        JLabel textLabel = new JLabel("<html><div style='width:600px'>" + text + "</div></html>");
        textLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        textLabel.setForeground(MUTED);
        textLabel.setAlignmentX(LEFT_ALIGNMENT);
        textLabel.setBorder(new EmptyBorder(5, 0, 0, 0));

        box.add(titleLabel);
        box.add(textLabel);
        return box;
    }

    private void addLabeledField(JPanel parent, String labelText, JComponent field) {
        JLabel caption = new JLabel(labelText);
        caption.setFont(BODY_MEDIUM);
        caption.setForeground(TEXT);
        caption.setBorder(new EmptyBorder(14, 1, 7, 1));
        caption.setAlignmentX(LEFT_ALIGNMENT);

        parent.add(caption);

        if (field instanceof JTextField) {
            styleTextField((JTextField) field);
        }

        if (field instanceof JScrollPane) {
            ((JScrollPane) field).setAlignmentX(LEFT_ALIGNMENT);
        }

        field.setFont(BODY_FONT);
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, Math.max(40, field.getPreferredSize().height)));
        field.setAlignmentX(LEFT_ALIGNMENT);

        parent.add(field);
    }

    // =========================================================
    // Styling helpers
    // =========================================================

    private JButton primaryButton(String label) {
        ModernButton button = new ModernButton(label, ACCENT, ACCENT_HOVER, Color.WHITE);
        button.setFont(new Font("Segoe UI", Font.BOLD, 13));
        button.setPreferredSize(new Dimension(126, 40));
        button.setMaximumSize(new Dimension(160, 40));
        return button;
    }

    private JButton secondaryButton(String label) {
        ModernButton button = new ModernButton(label, new Color(242, 245, 250), new Color(231, 236, 244), NAVY);
        button.setFont(new Font("Segoe UI", Font.BOLD, 12));
        button.setPreferredSize(new Dimension(120, 38));
        button.setMaximumSize(new Dimension(160, 38));
        return button;
    }

    private JButton secondaryHeaderButton(String label) {
        ModernButton button = new ModernButton(
                label,
                new Color(255, 255, 255, 30),
                new Color(255, 255, 255, 48),
                Color.WHITE
        );
        button.setFont(new Font("Segoe UI", Font.BOLD, 11));
        button.setPreferredSize(new Dimension(96, 36));
        return button;
    }

    private void styleGhostButton(JButton button) {
        button.setFont(new Font("Segoe UI", Font.BOLD, 11));
        button.setForeground(DANGER);
        button.setBackground(new Color(255, 244, 245));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setFocusPainted(false);
        button.setBorder(new EmptyBorder(7, 10, 7, 10));
        button.setOpaque(true);
    }

    private void styleTextField(JTextField field) {
        field.setFont(BODY_FONT);
        field.setForeground(TEXT);
        field.setBackground(SURFACE);
        field.setCaretColor(ACCENT);
        field.setMargin(new Insets(8, 11, 8, 11));
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                new EmptyBorder(2, 4, 2, 4)));
        field.setPreferredSize(new Dimension(field.getPreferredSize().width, 40));
    }

    private void styleHeaderTextField(JTextField field) {
        field.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        field.setForeground(TEXT);
        field.setBackground(new Color(250, 252, 255));
        field.setCaretColor(ACCENT);
        field.setMargin(new Insets(7, 9, 7, 9));
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(255, 255, 255, 60)),
                new EmptyBorder(1, 3, 1, 3)));
    }

    private void styleScrollPane(JScrollPane pane) {
        pane.setBorder(BorderFactory.createLineBorder(BORDER));
        pane.getViewport().setBackground(SURFACE);
        pane.getVerticalScrollBar().setUnitIncrement(14);
    }

    private void configureLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            UIManager.put("OptionPane.messageFont", BODY_FONT);
            UIManager.put("OptionPane.buttonFont", BODY_MEDIUM);
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
            graphics2D.setPaint(new GradientPaint(0, 0, NAVY, getWidth(), getHeight(), NAVY_2));
            graphics2D.fillRect(0, 0, getWidth(), getHeight());
            graphics2D.dispose();
            super.paintComponent(graphics);
        }
    }

    private static final class RoundedPanel extends JPanel {
        private static final long serialVersionUID = 1L;

        private final int radius;
        private final Color fill;

        private RoundedPanel(int radius, Color fill) {
            this.radius = radius;
            this.fill = fill;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
            g2.dispose();
            super.paintComponent(graphics);
        }
    }

    private static final class ModernButton extends JButton {
        private static final long serialVersionUID = 1L;

        private final Color normal;
        private final Color hover;
        private boolean hovered;

        private ModernButton(String text, Color normal, Color hover, Color foreground) {
            super(text);
            this.normal = normal;
            this.hover = hover;

            setForeground(foreground);
            setBackground(normal);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setMargin(new Insets(9, 15, 9, 15));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent event) {
                    hovered = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent event) {
                    hovered = false;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(hovered ? hover : normal);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
            g2.dispose();
            super.paintComponent(graphics);
        }
    }

    // =========================================================
    // Navigation state
    // =========================================================

    private void showPage(String page) {
        if (("inbox".equals(page) || "compose".equals(page)) && currentUsername == null) {
            JOptionPane.showMessageDialog(
                    this,
                    "Đăng nhập trước để sử dụng chức năng này.",
                    "Chưa đăng nhập",
                    JOptionPane.INFORMATION_MESSAGE
            );
            page = "account";
        }

        pageLayout.show(pages, page);

        for (Map.Entry<String, JButton> entry : navigationButtons.entrySet()) {
            boolean selected = entry.getKey().equals(page);
            JButton button = entry.getValue();

            button.setBackground(selected ? ACCENT_SOFT : SURFACE);
            button.setForeground(selected ? ACCENT_HOVER : MUTED);
            button.setFont(new Font("Segoe UI", selected ? Font.BOLD : Font.PLAIN, 14));
            button.setBorder(selected
                    ? BorderFactory.createCompoundBorder(
                            BorderFactory.createMatteBorder(0, 3, 0, 0, ACCENT),
                            new EmptyBorder(8, 9, 8, 10))
                    : new EmptyBorder(8, 12, 8, 10));
        }
    }

    // =========================================================
    // Connection + existing application logic
    // =========================================================

    private void checkConnection() {
        final String host = hostField.getText().trim();
        final int port;

        try {
            if (host.isEmpty()) {
                throw new IllegalArgumentException("Hãy nhập IP LAN của máy chủ.");
            }
            port = readPort();
        } catch (IllegalArgumentException exception) {
            showError(exception.getMessage());
            return;
        }

        setConnectionStatus("Đang kiểm tra...", WARNING);

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws IOException {
                List<String> response = MailClient.sendRequest(host, port, "PING", Map.of());
                if (!response.equals(List.of("PONG"))) {
                    throw new IOException("Máy chủ không phản hồi gói kiểm tra UDP hợp lệ.");
                }
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    setConnectionStatus("Đã kết nối tới " + host, new Color(154, 232, 194));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    setConnectionStatus("Kiểm tra bị gián đoạn", new Color(255, 180, 180));
                    showError("Kiểm tra kết nối bị gián đoạn.");
                } catch (ExecutionException exception) {
                    setConnectionStatus("Không kết nối được", new Color(255, 180, 180));
                    showError("Không thể kiểm tra UDP tới máy chủ: " + rootMessage(exception));
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
                accountStatus.setText("●  Đã tạo tài khoản. Hãy đăng nhập.");
                accountStatus.setForeground(SUCCESS);
                passwordField.setText("");
                confirmPasswordField.setText("");
            } else if ("ACCOUNT_SECURED".equals(status)) {
                accountStatus.setText("●  Đã đặt mật khẩu cho tài khoản cũ.");
                accountStatus.setForeground(SUCCESS);
                passwordField.setText("");
                confirmPasswordField.setText("");
            } else if ("ACCOUNT_EXISTS".equals(status)) {
                accountStatus.setText("●  Tài khoản đã tồn tại. Hãy đăng nhập.");
                accountStatus.setForeground(DANGER);
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

                accountStatus.setText("●  Đã đăng nhập: " + currentUsername);
                accountStatus.setForeground(SUCCESS);

                signedInLabel.setText("●  " + currentUsername);
                signedInLabel.setForeground(SUCCESS);
                logoutButton.setVisible(true);

                passwordField.setText("");
                confirmPasswordField.setText("");

                setInbox(response.subList(2, response.size()));
                showPage("inbox");

            } else if ("LOGIN_FAILED".equals(response.get(0))) {
                passwordField.setText("");
                accountStatus.setText("●  Tên tài khoản hoặc mật khẩu không đúng.");
                accountStatus.setForeground(DANGER);
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

        emailContent.setText(fileNames.isEmpty()
                ? "Hộp thư chưa có thư.\n\nCác thư mới sẽ xuất hiện tại danh sách bên trái."
                : "");
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
        parameters.put(
                "body64",
                Base64.getEncoder().encodeToString(body.getBytes(StandardCharsets.UTF_8))
        );

        request("SEND_EMAIL", parameters, response -> {
            if ("EMAIL_SENT".equals(response.get(0))) {
                JOptionPane.showMessageDialog(
                        this,
                        "Đã gửi thư tới " + recipient + ".",
                        "Gửi thư thành công",
                        JOptionPane.INFORMATION_MESSAGE
                );

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
                if (!"LOGOUT_SUCCESS".equals(response.get(0))
                        && !"AUTH_REQUIRED".equals(response.get(0))) {
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

    private void request(
            String command,
            Map<String, String> parameters,
            Consumer<List<String>> onResponse
    ) {
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
                        setConnectionStatus("Máy chủ không phản hồi", new Color(255, 180, 180));
                        showError("Máy chủ đóng kết nối mà không gửi phản hồi.");
                        return;
                    }

                    setConnectionStatus("Đã kết nối tới " + host, new Color(154, 232, 194));
                    onResponse.accept(response);

                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    setConnectionStatus("Yêu cầu bị gián đoạn", new Color(255, 180, 180));
                    showError("Yêu cầu bị gián đoạn.");

                } catch (ExecutionException exception) {
                    setConnectionStatus("Không kết nối được", new Color(255, 180, 180));
                    showError("Không thể trao đổi dữ liệu với máy chủ: " + rootMessage(exception));
                }
            }
        }.execute();
    }

    private String rootMessage(Exception exception) {
        Throwable cause = exception.getCause();
        return cause == null || cause.getMessage() == null
                ? exception.getMessage()
                : cause.getMessage();
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(
                this,
                message,
                "Có lỗi",
                JOptionPane.ERROR_MESSAGE
        );
    }

    private void setConnectionStatus(String message, Color color) {
        connectionStatus.setText("●  " + message);
        connectionStatus.setForeground(color);
    }
}
