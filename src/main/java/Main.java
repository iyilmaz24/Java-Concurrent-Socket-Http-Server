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
      String[] requestLineParts = requestParts.get(0).split(" ");
      String[] requestHeaders = requestParts.get(1).split(" ");

      String requestBody = null;
      if (requestParts.size() > 2) {
        requestParts.get(2);
      }
      String[] pathStrings = requestLineParts[1].split("/");

      if ("GET".equals(requestLineParts[0])) {
        if (pathStrings.length == 0) { // if original path was "/"
          socket.getOutputStream().write((String.format("%s %s%s%s", Protocol, RespOK, CRLF, CRLF).getBytes(StandardCharsets.US_ASCII)));
        }
        else if ("echo".equals(pathStrings[1])) { 
          socket.getOutputStream().write((String.format("%s %s%s%s%d%s%s%s", Protocol, RespOK, CRLF, ContentTypeLength, pathStrings[2].length(), CRLF, CRLF, pathStrings[2]).getBytes(StandardCharsets.US_ASCII)));
        }
        else if ("user-agent".equals(pathStrings[1])) {
          for(String header : requestHeaders) {
            if(header.contains("User-Agent:")) {
              String[] userAgentParts = header.split(" ");
              socket.getOutputStream().write((String.format("%s %s%s%s%d%s%s%s", Protocol, RespOK, CRLF, ContentTypeLength, pathStrings[2].length(), CRLF, CRLF, userAgentParts[1]).getBytes(StandardCharsets.US_ASCII)));
            }
          }
        }
      }
      
      socket.getOutputStream().write((String.format("%s %s%s%s", Protocol, RespNotFound, CRLF, CRLF).getBytes(StandardCharsets.US_ASCII))); // return 404 Not Found

    } catch (IOException e) {
      return e;
    }
    return null;
  }

}
