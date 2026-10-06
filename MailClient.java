import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.SwingUtilities;

public class MailClient {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MailClientGUI().setVisible(true));
    }

    static List<String> sendRequest(String serverHost, int serverPort, String command,
            Map<String, String> parameters) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(serverHost, serverPort), 5000);
            socket.setSoTimeout(10000);
            try (BufferedWriter output = new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                    BufferedReader input = new BufferedReader(
                            new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

                output.write(command);
                output.newLine();
                for (Map.Entry<String, String> entry : parameters.entrySet()) {
                    output.write(entry.getKey() + "=" + entry.getValue());
                    output.newLine();
                }
                output.write("END");
                output.newLine();
                output.flush();

                List<String> response = new ArrayList<>();
                String line;
                while ((line = input.readLine()) != null && !"END".equals(line)) {
                    response.add(line);
                }
                return response;
            }
        }
    }
}
