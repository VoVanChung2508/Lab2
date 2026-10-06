import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MailServer {
    private static final int DEFAULT_PORT = 3456;
    private static final int DEFAULT_DASHBOARD_PORT = 8080;
    private static final Path MAIL_ROOT = Paths.get("mail_data");
    private static final String CREDENTIAL_FILE = ".credentials";
    private static final String WELCOME_TEXT = "Thank you for using this service.\n"
            + "We hope that you will feel comfortable using our mail service.";
    private static final int PASSWORD_ITERATIONS = 210_000;
    private static final int PASSWORD_SALT_BYTES = 16;
    private static final int PASSWORD_HASH_BYTES = 32;
    private static final int SESSION_TOKEN_BYTES = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Object ACCOUNT_LOCK = new Object();
    private static final Map<String, String> ACTIVE_SESSIONS = new ConcurrentHashMap<>();
    private static final int MAX_LOG_ENTRIES = 300;
    private static final DateTimeFormatter LOG_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Deque<String> LOGS = new ArrayDeque<>();

    private final Object lifecycleLock = new Object();
    private volatile int port = DEFAULT_PORT;
    private volatile int dashboardPort = DEFAULT_DASHBOARD_PORT;
    private volatile String lastError = "";
    private DatagramSocket serverSocket;
    private ExecutorService executorService;

    public static void main(String[] args) {
        MailServer server = new MailServer();
        if (args.length > 1) {
            log("Usage: java MailServer [mailPort]");
            return;
        }
        if (args.length == 1) {
            try {
                server.port = Integer.parseInt(args[0]);
                if (server.port < 1 || server.port > 65535) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException e) {
                log("Mail port must be a number from 1 to 65535.");
                return;
            }
        }
        try {
            server.startDashboard();
        } catch (IOException e) {
            log("Could not start dashboard: " + e.getMessage());
        }

        try {
            server.startMailServer();
        } catch (IOException e) {
            server.lastError = e.getMessage();
            log("Mail server failed to start: " + e.getMessage());
        }

        try {
            new CountDownLatch(1).await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            try {
                server.stopMailServer();
            } catch (IOException stopError) {
                log("Could not stop mail service cleanly: " + stopError.getMessage());
            }
        }
    }

    private void startMailServer() throws IOException {
        synchronized (lifecycleLock) {
            if (serverSocket != null && !serverSocket.isClosed()) {
                return;
            }

            Files.createDirectories(MAIL_ROOT);
            DatagramSocket listeningSocket = new DatagramSocket(null);
            try {
                listeningSocket.setReuseAddress(true);
                listeningSocket.bind(new InetSocketAddress(port));
            } catch (IOException e) {
                listeningSocket.close();
                lastError = e.getMessage();
                throw e;
            }

            ExecutorService clients = Executors.newFixedThreadPool(20);
            serverSocket = listeningSocket;
            executorService = clients;
            lastError = "";
            log("Mail server listening on all network interfaces, UDP port " + port);
            log("Mail data directory: " + MAIL_ROOT.toAbsolutePath());
            Thread listener = new Thread(() -> receiveRequests(listeningSocket, clients), "mail-server-udp");
            listener.setDaemon(true);
            listener.start();
        }
    }

    private void receiveRequests(DatagramSocket listeningSocket, ExecutorService clients) {
        while (!listeningSocket.isClosed()) {
            try {
                byte[] buffer = new byte[65_507];
                DatagramPacket requestPacket = new DatagramPacket(buffer, buffer.length);
                listeningSocket.receive(requestPacket);
                String request = new String(
                        requestPacket.getData(), requestPacket.getOffset(), requestPacket.getLength(),
                        StandardCharsets.UTF_8);
                log("UDP request from " + requestPacket.getAddress());
                try {
                    clients.submit(new ClientHandler(listeningSocket, request,
                            requestPacket.getAddress(), requestPacket.getPort()));
                } catch (RejectedExecutionException e) {
                    log("Rejected UDP request because the server is stopping.");
                }
            } catch (SocketException e) {
                if (!listeningSocket.isClosed()) {
                    handleAcceptFailure(listeningSocket, clients, e);
                }
                return;
            } catch (IOException e) {
                handleAcceptFailure(listeningSocket, clients, e);
                return;
            }
        }
    }

    private void handleAcceptFailure(DatagramSocket listeningSocket, ExecutorService clients, IOException error) {
        synchronized (lifecycleLock) {
            if (serverSocket == listeningSocket) {
                lastError = error.getMessage();
                serverSocket = null;
                executorService = null;
                listeningSocket.close();
                clients.shutdown();
                log("Mail server stopped after a UDP receive error: " + error.getMessage());
            }
        }
    }

    private void stopMailServer() throws IOException {
        synchronized (lifecycleLock) {
            if (serverSocket == null) {
                return;
            }

            DatagramSocket listeningSocket = serverSocket;
            ExecutorService clients = executorService;
            listeningSocket.close();
            serverSocket = null;
            executorService = null;
            if (clients != null) {
                clients.shutdown();
            }
            log("Mail server stopped.");
        }
    }

    private void restartMailServer() throws IOException {
        stopMailServer();
        startMailServer();
    }

    private void configurePort(int newPort) throws IOException {
        synchronized (lifecycleLock) {
            boolean wasRunning = serverSocket != null && !serverSocket.isClosed();
            if (port == newPort && wasRunning) {
                return;
            }
            if (wasRunning) {
                stopMailServer();
            }
            port = newPort;
            log("Configured mail UDP port to " + newPort + ".");
            if (wasRunning) {
                startMailServer();
            }
        }
    }

    private void startDashboard() throws IOException {
        HttpServer dashboard = null;
        IOException bindError = null;
        for (int candidatePort = DEFAULT_DASHBOARD_PORT; candidatePort < DEFAULT_DASHBOARD_PORT + 10;
                candidatePort++) {
            try {
                dashboard = HttpServer.create(new InetSocketAddress("127.0.0.1", candidatePort), 0);
                dashboardPort = candidatePort;
                break;
            } catch (IOException e) {
                bindError = e;
            }
        }
        if (dashboard == null) {
            throw bindError == null ? new IOException("No dashboard port is available.") : bindError;
        }
        dashboard.createContext("/", this::serveDashboard);
        dashboard.createContext("/api/", this::handleApi);
        dashboard.setExecutor(Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "mail-dashboard");
            thread.setDaemon(true);
            return thread;
        }));
        dashboard.start();
        log("Admin dashboard available at http://127.0.0.1:" + dashboardPort + " (loopback only).");
    }

    private void serveDashboard(HttpExchange exchange) throws IOException {
        if (!isTrustedHost(exchange)) {
            sendResponse(exchange, 403, "text/plain; charset=utf-8", "Forbidden");
            return;
        }
        if (!"GET".equals(exchange.getRequestMethod()) || !"/".equals(exchange.getRequestURI().getPath())) {
            sendResponse(exchange, 404, "text/plain; charset=utf-8", "Not found");
            return;
        }

        byte[] page;
        try (InputStream resource = MailServer.class.getResourceAsStream("/MailServerDashboard.html")) {
            if (resource == null) {
                sendResponse(exchange, 500, "text/plain; charset=utf-8",
                        "Dashboard resource MailServerDashboard.html was not found.");
                return;
            }
            page = resource.readAllBytes();
        }
        sendResponse(exchange, 200, "text/html; charset=utf-8", page);
    }

    private void handleApi(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        try {
            if (!isTrustedHost(exchange) || (isMutatingMethod(method) && !isTrustedOrigin(exchange))) {
                sendResponse(exchange, 403, "application/json; charset=utf-8",
                        "{\"error\":\"Dashboard requests must come from this local page.\"}");
                return;
            }
            if ("/api/status".equals(path) && "GET".equals(method)) {
                sendResponse(exchange, 200, "application/json; charset=utf-8", statusJson());
            } else if ("/api/logs".equals(path) && "GET".equals(method)) {
                sendResponse(exchange, 200, "application/json; charset=utf-8", logsJson());
            } else if ("/api/start".equals(path) && "POST".equals(method)) {
                startMailServer();
                sendResponse(exchange, 200, "application/json; charset=utf-8", statusJson());
            } else if ("/api/stop".equals(path) && "POST".equals(method)) {
                stopMailServer();
                sendResponse(exchange, 200, "application/json; charset=utf-8", statusJson());
            } else if ("/api/restart".equals(path) && "POST".equals(method)) {
                restartMailServer();
                sendResponse(exchange, 200, "application/json; charset=utf-8", statusJson());
            } else if ("/api/config".equals(path) && "POST".equals(method)) {
                configurePort(readPort(exchange));
                sendResponse(exchange, 200, "application/json; charset=utf-8", statusJson());
            } else if (path.startsWith("/api/")) {
                sendResponse(exchange, 405, "application/json; charset=utf-8",
                        "{\"error\":\"Unsupported API route or method.\"}");
            } else {
                sendResponse(exchange, 404, "application/json; charset=utf-8",
                        "{\"error\":\"Not found.\"}");
            }
        } catch (IllegalArgumentException e) {
            sendResponse(exchange, 400, "application/json; charset=utf-8", errorJson(e.getMessage()));
        } catch (IOException e) {
            lastError = e.getMessage();
            log("Dashboard operation failed: " + e.getMessage());
            sendResponse(exchange, 500, "application/json; charset=utf-8", errorJson(e.getMessage()));
        } finally {
            exchange.close();
        }
    }

    private boolean isTrustedHost(HttpExchange exchange) {
        String host = exchange.getRequestHeaders().getFirst("Host");
        return ("127.0.0.1:" + dashboardPort).equals(host)
                || ("localhost:" + dashboardPort).equals(host);
    }

    private boolean isTrustedOrigin(HttpExchange exchange) {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        return ("http://127.0.0.1:" + dashboardPort).equals(origin)
                || ("http://localhost:" + dashboardPort).equals(origin);
    }

    private boolean isMutatingMethod(String method) {
        return "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method) || "DELETE".equals(method);
    }

    private int readPort(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null || !query.startsWith("port=") || query.indexOf('&') >= 0) {
            throw new IllegalArgumentException("Provide a port number.");
        }
        String value = URLDecoder.decode(query.substring("port=".length()), StandardCharsets.UTF_8);
        try {
            int requestedPort = Integer.parseInt(value);
            if (requestedPort < 1 || requestedPort > 65535) {
                throw new NumberFormatException();
            }
            return requestedPort;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Enter a port number from 1 to 65535.");
        }
    }

    private String statusJson() {
        synchronized (lifecycleLock) {
            boolean running = serverSocket != null && !serverSocket.isClosed();
            return "{\"running\":" + running
                    + ",\"port\":" + port
                    + ",\"error\":\"" + jsonEscape(lastError) + "\"}";
        }
    }

    private static String logsJson() {
        synchronized (LOGS) {
            StringBuilder json = new StringBuilder("{\"logs\":[");
            boolean first = true;
            for (String entry : LOGS) {
                if (!first) {
                    json.append(',');
                }
                json.append('"').append(jsonEscape(entry)).append('"');
                first = false;
            }
            return json.append("]}").toString();
        }
    }

    private static String errorJson(String message) {
        return "{\"error\":\"" + jsonEscape(message == null ? "Unknown error." : message) + "\"}";
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t");
    }

    private static void sendResponse(HttpExchange exchange, int status, String contentType, String body)
            throws IOException {
        sendResponse(exchange, status, contentType, body.getBytes(StandardCharsets.UTF_8));
    }

    private static void sendResponse(HttpExchange exchange, int status, String contentType, byte[] body)
            throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }

    private static void log(String message) {
        String entry = LocalDateTime.now().format(LOG_TIMESTAMP) + "  " + message;
        synchronized (LOGS) {
            LOGS.addLast(entry);
            while (LOGS.size() > MAX_LOG_ENTRIES) {
                LOGS.removeFirst();
            }
        }
        System.out.println(entry);
    }

    private static class ClientHandler implements Runnable {
        private final DatagramSocket socket;
        private final String request;
        private final java.net.InetAddress clientAddress;
        private final int clientPort;

        public ClientHandler(DatagramSocket socket, String request,
                java.net.InetAddress clientAddress, int clientPort) {
            this.socket = socket;
            this.request = request;
            this.clientAddress = clientAddress;
            this.clientPort = clientPort;
        }

        @Override
        public void run() {
            StringWriter responseBuffer = new StringWriter();
            try (BufferedReader input = new BufferedReader(new StringReader(request));
                 BufferedWriter output = new BufferedWriter(responseBuffer)) {

                String command = input.readLine();
                if (command == null) {
                    return;
                }

                Map<String, String> parameters = new HashMap<>();
                String line;
                while ((line = input.readLine()) != null && !line.equals("END")) {
                    if (line.trim().isEmpty()) {
                        continue;
                    }
                    String[] parts = line.split("=", 2);
                    if (parts.length == 2) {
                        parameters.put(parts[0].trim(), parts[1].trim());
                    }
                }

                switch (command.trim().toUpperCase()) {
                    case "PING":
                        sendResponse(output, "PONG");
                        break;
                    case "CREATE_ACCOUNT":
                        handleCreateAccount(parameters, output);
                        break;
                    case "LOGIN":
                        handleLogin(parameters, output);
                        break;
                    case "LIST_MAIL":
                        handleListMail(parameters, output);
                        break;
                    case "LOGOUT":
                        handleLogout(parameters, output);
                        break;
                    case "SEND_EMAIL":
                        handleSendEmail(parameters, output);
                        break;
                    case "READ_EMAIL":
                        handleReadEmail(parameters, output);
                        break;
                    default:
                        sendResponse(output, "INVALID_COMMAND");
                        break;
                }

                byte[] response = responseBuffer.toString().getBytes(StandardCharsets.UTF_8);
                if (response.length > 65_507) {
                    response = "RESPONSE_TOO_LARGE\nEND\n".getBytes(StandardCharsets.UTF_8);
                }
                socket.send(new DatagramPacket(response, response.length, clientAddress, clientPort));
            } catch (IOException e) {
                log("Could not process UDP request from " + clientAddress + ": " + e.getMessage());
            }
        }
    }

    private static void handleCreateAccount(Map<String, String> parameters, BufferedWriter output) throws IOException {
        String username = parameters.get("username");
        if (!isValidUsername(username)) {
            sendResponse(output, "INVALID_USERNAME");
            return;
        }
        String password = readPassword(parameters.get("password64"));
        if (!isValidPassword(password)) {
            sendResponse(output, "INVALID_PASSWORD");
            return;
        }

        Path userDirectory = MAIL_ROOT.resolve(username.trim());
        Path credentials = userDirectory.resolve(CREDENTIAL_FILE);
        boolean securedLegacyAccount;
        synchronized (ACCOUNT_LOCK) {
            Files.createDirectories(MAIL_ROOT);
            boolean accountExists = Files.exists(userDirectory);
            if (accountExists && Files.exists(credentials)) {
                sendResponse(output, "ACCOUNT_EXISTS");
                return;
            }

            Files.createDirectories(userDirectory);
            Files.writeString(credentials, createPasswordRecord(password), StandardCharsets.UTF_8);
            securedLegacyAccount = accountExists;
            if (!accountExists) {
                Path welcomeFile = userDirectory.resolve("new_email.txt");
                Files.writeString(welcomeFile, WELCOME_TEXT, StandardCharsets.UTF_8);
            }
        }

        sendResponse(output, securedLegacyAccount ? "ACCOUNT_SECURED" : "ACCOUNT_CREATED");
        log("Created or secured account for: " + username);
    }

    private static void handleLogin(Map<String, String> parameters, BufferedWriter output) throws IOException {
        String username = parameters.get("username");
        String password = readPassword(parameters.get("password64"));
        if (!isValidUsername(username) || password == null) {
            sendResponse(output, "LOGIN_FAILED");
            return;
        }

        Path userDirectory = MAIL_ROOT.resolve(username.trim());
        Path credentials = userDirectory.resolve(CREDENTIAL_FILE);
        if (!Files.isDirectory(userDirectory) || !Files.isRegularFile(credentials)
                || !verifyPassword(password, Files.readString(credentials, StandardCharsets.UTF_8))) {
            sendResponse(output, "LOGIN_FAILED");
            return;
        }

        List<String> fileNames = listMailFiles(userDirectory);
        List<String> response = new ArrayList<>();
        response.add("LOGIN_SUCCESS");
        String token = createSession(username.trim());
        response.add("SESSION_TOKEN:" + token);
        response.addAll(fileNames);
        sendResponse(output, response);
        log("User logged in: " + username);
    }

    private static void handleListMail(Map<String, String> parameters, BufferedWriter output) throws IOException {
        String username = authenticatedUsername(parameters.get("token"));
        if (username == null) {
            sendResponse(output, "AUTH_REQUIRED");
            return;
        }
        List<String> response = new ArrayList<>();
        response.add("LIST_SUCCESS");
        response.addAll(listMailFiles(MAIL_ROOT.resolve(username)));
        sendResponse(output, response);
    }

    private static void handleLogout(Map<String, String> parameters, BufferedWriter output) throws IOException {
        String token = parameters.get("token");
        String username = token == null ? null : ACTIVE_SESSIONS.remove(token);
        if (username == null) {
            sendResponse(output, "AUTH_REQUIRED");
            return;
        }
        sendResponse(output, "LOGOUT_SUCCESS");
        log("User logged out: " + username);
    }

    private static void handleSendEmail(Map<String, String> parameters, BufferedWriter output) throws IOException {
        String from = authenticatedUsername(parameters.get("token"));
        String to = parameters.get("to");
        String subject = parameters.get("subject");
        String body = parameters.get("body");

        String encodedBody = parameters.get("body64");
        if (encodedBody != null) {
            try {
                body = new String(Base64.getDecoder().decode(encodedBody), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                sendResponse(output, "INVALID_EMAIL");
                return;
            }
        }

        if (from == null) {
            sendResponse(output, "AUTH_REQUIRED");
            return;
        }
        if (to == null || subject == null || body == null) {
            sendResponse(output, "INVALID_EMAIL");
            return;
        }

        if (!isValidUsername(to)) {
            sendResponse(output, "RECIPIENT_NOT_FOUND");
            return;
        }

        Path recipientDirectory = MAIL_ROOT.resolve(to.trim());
        if (!Files.exists(recipientDirectory) || !Files.isDirectory(recipientDirectory)) {
            sendResponse(output, "RECIPIENT_NOT_FOUND");
            return;
        }

        String fileName = generateEmailFileName(recipientDirectory);
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String emailContent = "From: " + from.trim() + "\n"
                + "To: " + to.trim() + "\n"
                + "Subject: " + subject.trim() + "\n"
                + "Date: " + timestamp + "\n\n"
                + body + "\n";

        Files.write(recipientDirectory.resolve(fileName), emailContent.getBytes(StandardCharsets.UTF_8));
        sendResponse(output, "EMAIL_SENT");
        log("Email sent from " + from + " to " + to + " -> " + fileName);
    }

    private static void handleReadEmail(Map<String, String> parameters, BufferedWriter output) throws IOException {
        String username = authenticatedUsername(parameters.get("token"));
        String fileName = parameters.get("filename");

        if (username == null) {
            sendResponse(output, "AUTH_REQUIRED");
            return;
        }
        if (fileName == null || CREDENTIAL_FILE.equals(fileName)
                || !fileName.matches("[A-Za-z0-9._-]+")
                || ".".equals(fileName) || "..".equals(fileName)) {
            sendResponse(output, "INVALID_EMAIL_REQUEST");
            return;
        }

        Path mailFile = MAIL_ROOT.resolve(username.trim()).resolve(fileName.trim());
        if (!Files.exists(mailFile) || !Files.isRegularFile(mailFile)) {
            sendResponse(output, "FILE_NOT_FOUND");
            return;
        }

        List<String> response = new ArrayList<>();
        response.add("EMAIL_CONTENT");
        response.add(Base64.getEncoder().encodeToString(Files.readAllBytes(mailFile)));
        sendResponse(output, response);
    }

    private static boolean isValidUsername(String username) {
        return username != null
                && username.trim().matches("[A-Za-z0-9._-]{1,32}")
                && !".".equals(username.trim())
                && !"..".equals(username.trim());
    }

    private static String readPassword(String encodedPassword) {
        if (encodedPassword == null) {
            return null;
        }
        try {
            return new String(Base64.getDecoder().decode(encodedPassword), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean isValidPassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 128) {
            return false;
        }
        return password.chars().noneMatch(Character::isISOControl);
    }

    private static String createPasswordRecord(String password) throws IOException {
        byte[] salt = new byte[PASSWORD_SALT_BYTES];
        SECURE_RANDOM.nextBytes(salt);
        byte[] hash = derivePasswordHash(password.toCharArray(), salt, PASSWORD_ITERATIONS);
        return "v1$" + PASSWORD_ITERATIONS + "$"
                + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(hash);
    }

    private static boolean verifyPassword(String password, String record) throws IOException {
        String[] parts = record.trim().split("\\$", -1);
        if (parts.length != 4 || !"v1".equals(parts[0])) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expectedHash = Base64.getDecoder().decode(parts[3]);
            if (iterations < 100_000 || iterations > 1_000_000
                    || salt.length != PASSWORD_SALT_BYTES || expectedHash.length != PASSWORD_HASH_BYTES) {
                return false;
            }
            byte[] actualHash = derivePasswordHash(password.toCharArray(), salt, iterations);
            return MessageDigest.isEqual(expectedHash, actualHash);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] derivePasswordHash(char[] password, byte[] salt, int iterations) throws IOException {
        PBEKeySpec keySpec = new PBEKeySpec(password, salt, iterations, PASSWORD_HASH_BYTES * 8);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(keySpec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IOException("Password hashing is unavailable.", e);
        } finally {
            keySpec.clearPassword();
            java.util.Arrays.fill(password, '\0');
        }
    }

    private static String createSession(String username) {
        byte[] tokenBytes = new byte[SESSION_TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        ACTIVE_SESSIONS.put(token, username);
        return token;
    }

    private static String authenticatedUsername(String token) {
        return token == null ? null : ACTIVE_SESSIONS.get(token);
    }

    private static List<String> listMailFiles(Path directory) throws IOException {
        try (Stream<Path> stream = Files.list(directory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> !CREDENTIAL_FILE.equals(path.getFileName().toString()))
                    .map(path -> path.getFileName().toString())
                    .sorted(Comparator.naturalOrder())
                    .collect(Collectors.toList());
        }
    }

    private static String generateEmailFileName(Path directory) throws IOException {
        int index = 1;
        String fileName;

        do {
            fileName = String.format("email_%03d.txt", index);
            index++;
        } while (Files.exists(directory.resolve(fileName)));

        return fileName;
    }

    private static void sendResponse(BufferedWriter output, String response) throws IOException {
        output.write(response);
        output.newLine();
        output.write("END");
        output.newLine();
        output.flush();
    }

    private static void sendResponse(BufferedWriter output, List<String> responseLines) throws IOException {
        for (String line : responseLines) {
            output.write(line);
            output.newLine();
        }
        output.write("END");
        output.newLine();
        output.flush();
    }
}
