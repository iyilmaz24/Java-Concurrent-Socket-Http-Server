import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class Main {  

  private static Path ServerFileDirectory = null;
  public static void main(String[] args) {
    System.out.println("Server starting..."); // Print statements for debugging - visible when running tests.

    if(args.length > 0 && "--directory".equals(args[0])) {
      ServerFileDirectory = Paths.get(args[1]);

      if (!Files.exists(ServerFileDirectory) || !Files.isDirectory(ServerFileDirectory)) {
        System.err.println("**Error with file path " + args[1]);
      }
    }
    
    try (
      ServerSocket serverSocket = new ServerSocket(); // try-with-resources to automatically clean up ServerSocket
    ) {
      // Since the tester restarts program quite often, setting SO_REUSEADDR
      // ensures that we don't run into 'Address already in use' errors
      serverSocket.setReuseAddress(true);
      serverSocket.bind(new InetSocketAddress(4221));

      ThreadFactory virtualThreadFactory = Thread.ofVirtual() 
        .name("worker-", 1)
        .uncaughtExceptionHandler((t, e) -> System.err.printf("***Error in %s: %s%n", t, e))
        .factory(); // create virtual thread factory

      ExecutorService executor = Executors.newThreadPerTaskExecutor(virtualThreadFactory);
      
      while (true) {
        Socket socket = serverSocket.accept(); 
        executor.submit(() -> {
          try {
            handleConnection(socket);
          } catch (IOException e) {
            System.err.println("**Connection error: " + e.getMessage());
          } catch (Exception e) {
            System.err.println("**Unexpected error: " + e.getMessage());
          }
        });
      }
      
    } catch (IOException e) {
      System.out.println("**IOException: " + e.getMessage());
    }

  }

  public static void handleConnection(Socket socket) throws IOException, Exception {
    try (
      InputStream socketInStream = socket.getInputStream();
      InputStreamReader socketInReader = new InputStreamReader(socketInStream, StandardCharsets.UTF_8);
      BufferedReader socketBufferedReader = new BufferedReader(socketInReader);
    ) {
      System.out.println("accepted new connection");
      List<String> requestStrings = new ArrayList<>();
      String tempString;
  
      while((tempString = socketBufferedReader.readLine()) != null && !tempString.isEmpty()) {
        // System.out.printf("Received: %s\n", tempString);
        requestStrings.add(tempString);
      }
      handleRequest(requestStrings, socket);

      socket.close(); 
    }
  }

  private static final String Protocol = "HTTP/1.1";
  private static final String CRLF = "\r\n";

  private static final String RespOK = "200 OK";
  private static final String RespNotFound = "404 Not Found";

  private static final String ContentType = "Content-Type: ";
  private static final String TextContent = "text/plain";
  private static final String AppOctetStreamContent = "application/octet-stream";

  private static final String ContentLength = "Content-Length: ";

  public static void handleRequest(List<String> requestParts, Socket socket) throws IOException, Exception {
    try (
      OutputStream socketOutStream = socket.getOutputStream();
    ) {
      boolean responseMade = false;
      String[] requestLineParts = requestParts.get(0).split(" ");
      String[] pathStrings = requestLineParts[1].split("/");

      if ("GET".equals(requestLineParts[0])) {
        byte[] byteMessage;
        if (pathStrings.length == 0) { // if original path was "/", respond 200 OK
          byteMessage = String.format("%s %s%s%s", Protocol, RespOK, CRLF, CRLF).getBytes(StandardCharsets.US_ASCII);
          socketOutStream.write((byteMessage));
          responseMade = true;
        }
        else if ("echo".equals(pathStrings[1])) { // send response where * after /echo/* is the body 
          byteMessage = String.format("%s %s%s%s%s%s%s%d%s%s%s", Protocol, RespOK, CRLF, ContentType, TextContent, CRLF, ContentLength, pathStrings[2].length(), CRLF, CRLF, pathStrings[2]).getBytes(StandardCharsets.US_ASCII);
          socketOutStream.write((byteMessage));
          responseMade = true;
        }
        else if ("user-agent".equals(pathStrings[1])) { // send response where the User-Agent header's content is the body
          String currentHeader;
          for(int i = 1; i < requestParts.size(); i++) { // init "i" as 1 to skip to header section
            currentHeader = requestParts.get(i);
            if(currentHeader.contains("User-Agent:")) {
              String[] userAgentParts = requestParts.get(i).split(" ");
              byteMessage = String.format("%s %s%s%s%s%s%s%d%s%s%s", Protocol, RespOK, CRLF, ContentType, TextContent, CRLF, ContentLength, userAgentParts[1].length(), CRLF, CRLF, userAgentParts[1]).getBytes(StandardCharsets.US_ASCII);
              socketOutStream.write((byteMessage));
              responseMade = true;
            }
          }
        }
        else if ("files".equals(pathStrings[1])) {
          Path requestedFile = ServerFileDirectory.resolve(pathStrings[2]);
          if (Files.exists(requestedFile) && Files.isRegularFile(requestedFile) && Files.isReadable(requestedFile)) { // check file exists, isn't directory or link, and is readable
            byte[] fileBytes = Files.readAllBytes(requestedFile);
            byteMessage = String.format("%s %s%s%s%s%s%s%d%s%s", Protocol, RespOK, CRLF, ContentType, AppOctetStreamContent, CRLF, ContentLength, fileBytes.length, CRLF, CRLF).getBytes(StandardCharsets.US_ASCII);
            socketOutStream.write((byteMessage)); 
            socketOutStream.write(fileBytes);
            responseMade = true;
          }
        }
      }
      if (!responseMade) {
        socketOutStream.write((String.format("%s %s%s%s", Protocol, RespNotFound, CRLF, CRLF).getBytes(StandardCharsets.US_ASCII))); // return 404 Not Found
      }
    }
  }

}
