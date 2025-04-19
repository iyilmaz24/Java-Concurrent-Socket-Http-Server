import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class Main {
  private static final String Protocol = "HTTP/1.1";
  private static final String CRLF = "\r\n";

  private static final String RespOK = "200 OK";
  private static final String RespNotFound = "404 Not Found";

  private static final String ContentTypeLength = "Content-Type: text/plain\r\nContent-Length: ";
  
  public static void main(String[] args) {
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

        List<String> requestStrings = new ArrayList<>();
        String tempString;

        while((tempString = bufferedReader.readLine()) != null && !tempString.isEmpty()) {
          System.out.printf("Received: %s\n", tempString);
          requestStrings.add(tempString);
        }
        handleRequest(requestStrings, socket);

        socket.close();
      }

    } catch (IOException e) {
      System.out.println("IOException: " + e.getMessage());
    }

  }

  public static IOException handleRequest(List<String> requestParts, Socket socket) {
    try {
      for (String string : requestParts) {
        String[] headerStrings = string.split(" ");

        if ("GET".equals(headerStrings[0])) {
          if ("/".equals(headerStrings[1])) {
            socket.getOutputStream().write((String.format("%s %s%s%s", Protocol, RespOK, CRLF, CRLF).getBytes(StandardCharsets.US_ASCII)));
          }

          String[] pathStrings = headerStrings[1].split("/");
          System.out.printf("path: %s", pathStrings[0]);
          System.out.printf("path: %s", pathStrings[1]);
          System.out.printf("path: %s", pathStrings[2]);
          if ("echo".equals(pathStrings[0])) {
            System.out.printf("Sending: %s", String.format("%s %s%s%s%d%s%s%s", Protocol, RespOK, CRLF, ContentTypeLength, pathStrings[1].length(), CRLF, CRLF, pathStrings[1]).getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().write((String.format("%s %s%s%s%d%s%s%s", Protocol, RespOK, CRLF, ContentTypeLength, pathStrings[1].length(), CRLF, CRLF, pathStrings[1]).getBytes(StandardCharsets.US_ASCII)));
          }
          
        }
        break;
      }
      socket.getOutputStream().write((String.format("%s %s%s%s", Protocol, RespNotFound, CRLF, CRLF).getBytes(StandardCharsets.US_ASCII)));

    } catch (IOException e) {
      return e;
    }
    return null;
  }

}
