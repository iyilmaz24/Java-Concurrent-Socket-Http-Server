import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class Main {

  public static void main(String[] args) {
    final String Protocol = "HTTP/1.1";
    final String CRLF = "\r\n";
  
    final String RespOK = "200 OK";

    // Print statements for debugging - visible when running tests.
    System.out.println("Logs from your program will appear here!");

    
    try (
      ServerSocket serverSocket = new ServerSocket(); // try-with-resources to automatically clean up ServerSocket
    ) {
      serverSocket.setReuseAddress(true);
      serverSocket.bind(new InetSocketAddress(4221));
      Socket socket = serverSocket.accept(); // Wait for connection from client.
      System.out.println("accepted new connection");

      // Since the tester restarts program quite often, setting SO_REUSEADDR
      // ensures that we don't run into 'Address already in use' errors


      // DataInputStream dataInStream = new DataInputStream(socket.getInputStream());
      // String stringData = dataInStream.readUTF();
      // System.out.printf("**MESSAGE: %s", stringData);

      // DataOutputStream dataOutStream = new DataOutputStream(socket.getOutputStream());
      // dataOutStream.writeUTF(String.format("%s %s%s%s", Protocol, RespOK, CRLF, CRLF));
      // dataOutStream.flush();

      socket.getOutputStream().write((String.format("%s %s%s%s", Protocol, RespOK, CRLF, CRLF).getBytes(StandardCharsets.US_ASCII)));
      socket.close();
    } catch (IOException e) {
      System.out.println("IOException: " + e.getMessage());
    }

  }
}
