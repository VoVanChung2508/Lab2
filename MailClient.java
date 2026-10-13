import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.SwingUtilities;

public class MailClient {
    private static final int MAX_UDP_PAYLOAD = 65_507;
    private static final int REQUEST_TIMEOUT_MILLIS = 10_000;
    private static final int DISCOVERY_TIMEOUT_MILLIS = 1_500;
    static final String NOTIFICATION_GROUP = "239.255.42.99";
    static final int NOTIFICATION_PORT = 3457;
    static final int BROADCAST_NOTIFICATION_PORT = 3458;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MailClientGUI().setVisible(true));
    }

    static List<String> sendRequest(String serverHost, int serverPort, String command,
            Map<String, String> parameters) throws IOException {
        StringBuilder request = new StringBuilder(command).append('\n');
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            request.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
        }
        request.append("END\n");
        byte[] requestBytes = request.toString().getBytes(StandardCharsets.UTF_8);
        if (requestBytes.length > MAX_UDP_PAYLOAD) {
            throw new IOException("Yêu cầu vượt quá giới hạn kích thước của một gói UDP.");
        }

        InetSocketAddress address = new InetSocketAddress(serverHost, serverPort);
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(REQUEST_TIMEOUT_MILLIS);
            socket.send(new DatagramPacket(requestBytes, requestBytes.length, address));

            byte[] responseBuffer = new byte[MAX_UDP_PAYLOAD];
            DatagramPacket responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length);
            socket.receive(responsePacket);

            String responseText = new String(responsePacket.getData(), responsePacket.getOffset(),
                    responsePacket.getLength(), StandardCharsets.UTF_8);
            List<String> response = new ArrayList<>();
            for (String line : responseText.split("\\R")) {
                if ("END".equals(line)) {
                    break;
                }
                if (!line.isEmpty()) {
                    response.add(line);
                }
            }
            return response;
        }
    }

    static List<InetSocketAddress> discoverServers(int serverPort) throws IOException {
        byte[] request = "DISCOVER\nEND\n".getBytes(StandardCharsets.UTF_8);
        Map<String, InetSocketAddress> servers = new LinkedHashMap<>();

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            socket.setSoTimeout(DISCOVERY_TIMEOUT_MILLIS);

            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            boolean broadcastSent = false;
            if (interfaces != null) {
                while (interfaces.hasMoreElements()) {
                    NetworkInterface networkInterface = interfaces.nextElement();
                    if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                        continue;
                    }
                    for (InterfaceAddress interfaceAddress : networkInterface.getInterfaceAddresses()) {
                        if (interfaceAddress.getBroadcast() != null) {
                            socket.send(new DatagramPacket(request, request.length,
                                    interfaceAddress.getBroadcast(), serverPort));
                            broadcastSent = true;
                        }
                    }
                }
            }

            if (!broadcastSent) {
                socket.send(new DatagramPacket(request, request.length,
                        java.net.InetAddress.getByName("255.255.255.255"), serverPort));
            }

            long deadline = System.nanoTime() + DISCOVERY_TIMEOUT_MILLIS * 1_000_000L;
            byte[] responseBuffer = new byte[128];
            while (true) {
                long remainingMillis = (deadline - System.nanoTime()) / 1_000_000L;
                if (remainingMillis <= 0) {
                    break;
                }
                socket.setSoTimeout((int) Math.max(1, remainingMillis));
                DatagramPacket response = new DatagramPacket(responseBuffer, responseBuffer.length);
                try {
                    socket.receive(response);
                } catch (SocketTimeoutException e) {
                    break;
                }

                String responseText = new String(response.getData(), response.getOffset(),
                        response.getLength(), StandardCharsets.UTF_8);
                String[] responseLines = responseText.split("\\R");
                if (responseLines.length >= 2
                        && responseLines[0].startsWith("MAIL_SERVER:")
                        && responseLines[0].length() > "MAIL_SERVER:".length()
                        && "END".equals(responseLines[1])) {
                    InetSocketAddress address = new InetSocketAddress(response.getAddress(), response.getPort());
                    String serverId = responseLines[0].substring("MAIL_SERVER:".length());
                    servers.putIfAbsent(serverId, address);
                }
            }
        }
        return new ArrayList<>(servers.values());
    }

    static MulticastSocket openNotificationSocket() throws IOException {
        MulticastSocket socket = new MulticastSocket(null);
        try {
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(NOTIFICATION_PORT));
            socket.setSoTimeout(1_000);

            InetSocketAddress group = new InetSocketAddress(
                    java.net.InetAddress.getByName(NOTIFICATION_GROUP), NOTIFICATION_PORT);
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            List<NetworkInterface> multicastInterfaces = new ArrayList<>();
            List<NetworkInterface> loopbackInterfaces = new ArrayList<>();
            if (interfaces != null) {
                while (interfaces.hasMoreElements()) {
                    NetworkInterface networkInterface = interfaces.nextElement();
                    if (networkInterface.isUp() && networkInterface.supportsMulticast()) {
                        if (networkInterface.isLoopback()) {
                            loopbackInterfaces.add(networkInterface);
                        } else {
                            multicastInterfaces.add(networkInterface);
                        }
                    }
                }
            }
            multicastInterfaces.addAll(loopbackInterfaces);

            IOException joinFailure = null;
            boolean joined = false;
            for (NetworkInterface networkInterface : multicastInterfaces) {
                try {
                    socket.joinGroup(group, networkInterface);
                    joined = true;
                } catch (IOException e) {
                    if (joinFailure == null) {
                        joinFailure = new IOException("Không thể tham gia nhóm multicast trên interface mạng.");
                    }
                    joinFailure.addSuppressed(e);
                }
            }
            if (!joined) {
                if (joinFailure != null) {
                    throw joinFailure;
                }
                throw new IOException("Không tìm thấy interface mạng hỗ trợ multicast.");
            }
            return socket;
        } catch (IOException | RuntimeException e) {
            socket.close();
            throw e;
        }
    }

    static DatagramSocket openBroadcastNotificationSocket() throws IOException {
        DatagramSocket socket = new DatagramSocket(null);
        try {
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(BROADCAST_NOTIFICATION_PORT));
            socket.setSoTimeout(1_000);
            return socket;
        } catch (IOException | RuntimeException e) {
            socket.close();
            throw e;
        }
    }
}
