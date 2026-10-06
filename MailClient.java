import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.SwingUtilities;

public class MailClient {
    private static final int MAX_UDP_PAYLOAD = 65_507;
    private static final int REQUEST_TIMEOUT_MILLIS = 10_000;

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
}
