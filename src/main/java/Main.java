import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class Main {

  public static void main(String[] args) {
    final String Protocol = "HTTP/1.1";
    final String CRLF = "\r\n";
  
    final String RespOK = "200 OK";
    final String RespNotFound = "404 Not Found";

    // Print statements for debugging - visible when running tests.
    System.out.println("Logs from your program will appear here!");
    
    try (
      ServerSocket serverSocket = new ServerSocket(); // try-with-resources to automatically clean up ServerSocket
    ) {
      // Since the tester restarts program quite often, setting SO_REUSEADDR
      // ensures that we don't run into 'Address already in use' errors
      serverSocket.setReuseAddress(true);
      serverSocket.bind(new InetSocketAddress(4221));

      try (
        Socket socket = serverSocket.accept(); // Wait for connection from client, gets automatically cleaned up at end
      ) {
        System.out.println("accepted new connection");
        
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

        String headerLine;
        while((headerLine = bufferedReader.readLine()) != null && !headerLine.isEmpty()) {
          System.out.printf("Received: %s\n", headerLine);
          String[] headerStrings = headerLine.split(" ");
          if ("GET".equals(headerStrings[0])) {
            if ("/".equals(headerStrings[1])) {
              socket.getOutputStream().write((String.format("%s %s%s%s", Protocol, RespOK, CRLF, CRLF).getBytes(StandardCharsets.US_ASCII)));
            }
            else {
              socket.getOutputStream().write((String.format("%s %s%s%s", Protocol, RespNotFound, CRLF, CRLF).getBytes(StandardCharsets.US_ASCII)));
            }
            break;
          }
        }

        socket.close();
      }

    } catch (IOException e) {
      System.out.println("IOException: " + e.getMessage());
    }

  }

}
