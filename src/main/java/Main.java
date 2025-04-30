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

    if(args.length > 0 && "--directory".equals(args[0])) { // if "--directory {fileDirectoryPath}" cmd line args are supplied
      ServerFileDirectory = Paths.get(args[1]);

      if (!Files.exists(ServerFileDirectory) || !Files.isDirectory(ServerFileDirectory)) {
        System.err.println("***ERROR: Issue with file directory path " + args[1]);
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
        .uncaughtExceptionHandler((t, e) -> System.err.printf("***ERROR: Issue in %s: %s%n", t, e))
        .factory(); // use virtual thread factory for scalable handling of concurrent, blocking socket I/O

      ExecutorService executor = Executors.newThreadPerTaskExecutor(virtualThreadFactory);
      
      while (true) {
        Socket socket = serverSocket.accept(); 
        executor.submit(() -> {
          try {
            handleConnection(socket);
          } catch (IOException e) {
            System.err.println("***ERROR: " + e.getMessage());
          } catch (Exception e) {
            System.err.println("***ERROR: unexpected error - " + e.getMessage());
          }
        });
      }
      
    } catch (IOException e) {
      System.out.println("***ERROR: IOException - " + e.getMessage());
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
      handleRequest(requestStrings, socket, socketInStream);

      socket.close(); 
    }
  }

  private static final String Protocol = "HTTP/1.1";
  private static final String CRLF = "\r\n";

  private static final String RespOK = "200 OK";
  private static final String RespNotFound = "404 Not Found";
  private static final String RespForbidden= "403 Forbidden";
  private static final String RespInternalErr = "500 Internal Server Error";

  private static final String ContentType = "Content-Type: ";
  private static final String TextContent = "text/plain";
  private static final String AppOctetStreamContent = "application/octet-stream";

  private static final String ContentLength = "Content-Length: ";

  public static void handleRequest(List<String> requestParts, Socket socket, InputStream socketInStream) throws IOException, Exception {
    try (
      OutputStream socketOutStream = socket.getOutputStream();
    ) {
      boolean responseMade = false;
      String[] requestLineParts = requestParts.get(0).split(" ");
      String[] pathStrings = requestLineParts[1].split("/");
      byte[] byteMessage;
    
      if ("GET".equals(requestLineParts[0])) {
        if (pathStrings.length == 0) { // GET "/" - if original path was "/", respond 200 OK
          byteMessage = String.format("%s %s%s%s", Protocol, RespOK, CRLF, CRLF).getBytes(StandardCharsets.US_ASCII);
          socketOutStream.write((byteMessage));
          responseMade = true;
        }
        else if ("echo".equals(pathStrings[1])) { // GET "/echo/{message}" - send response where message is the body 
          byteMessage = String.format("%s %s%s%s%s%s%s%d%s%s%s", Protocol, RespOK, CRLF, ContentType, TextContent, CRLF, ContentLength, pathStrings[2].length(), CRLF, CRLF, pathStrings[2]).getBytes(StandardCharsets.US_ASCII);
          socketOutStream.write((byteMessage));
          responseMade = true;
        }
        else if ("user-agent".equals(pathStrings[1])) { // GET "/user-agent" - send response where the User-Agent header's content is the body
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
        else if ("files".equals(pathStrings[1])) { // GET "files/{filePath}" - send response with requested file as body
          if (ServerFileDirectory == null) {
            System.err.println("***ERROR (POST files/{fileName}): Request to interact with null ServerFileDirectory, not initialized");
            sendHttpErrorResponse(socketOutStream, RespInternalErr);
            responseMade = true;
          }
          Path requestedFile = ServerFileDirectory.resolve(pathStrings[2]);

          if (Files.exists(requestedFile) && Files.isRegularFile(requestedFile) && Files.isReadable(requestedFile)) { // check file exists, isn't directory or link, and is readable
            byte[] fileBytes = Files.readAllBytes(requestedFile);
            byteMessage = String.format("%s %s%s%s%s%s%s%d%s%s", Protocol, RespOK, CRLF, ContentType, AppOctetStreamContent, CRLF, ContentLength, fileBytes.length, CRLF, CRLF).getBytes(StandardCharsets.US_ASCII);
            socketOutStream.write((byteMessage));
            socketOutStream.write(fileBytes); // write file's bytes seperately (byte[] too large for String.format)
            responseMade = true;
          }
        }
      } else if ("POST".equals(requestLineParts[0])) {
        if ("files".equals(pathStrings[1])) { // POST "files/{fileName}" - create file with name as fileName and content as the request's body

        if (ServerFileDirectory == null) {
          System.err.println("***ERROR (POST files/{fileName}): Request to interact with null ServerFileDirectory, not initialized");
          sendHttpErrorResponse(socketOutStream, RespInternalErr);
          responseMade = true;
        }
        int bodyLength = 0; String bodyType; String currentHeader;

        for(int i = 1; i < requestParts.size(); i++) { // init "i" as 1 to skip to header section
          currentHeader = requestParts.get(i);
          if(currentHeader.contains("Content-Length:")) {
            String[] headerParts = requestParts.get(i).split(" ");
            try {
              bodyLength = Integer.parseInt(headerParts[1]);
              System.out.println(headerParts[1]);
              if (bodyLength < 0 || bodyLength > 10_000_000) {
                System.err.println("***ERROR: Invalid Content-Length header, size of " + bodyLength);
                sendHttpErrorResponse(socketOutStream, RespInternalErr);
                responseMade = true;
              }
            } catch (NumberFormatException e) {
              bodyLength = 0;
            }
          } else if (currentHeader.contains("Content-Type:")) {
            String[] headerParts = requestParts.get(i).split(" ");
            bodyType = headerParts[1];
          }
        }
        byte[] requestBody = new byte[bodyLength];
        int bytesRead = socketInStream.readNBytes(requestBody, 0, bodyLength);

        System.out.printf("bytesRead: %d", bytesRead);
        System.out.printf("bodyLength: %d", bodyLength);
        if (bytesRead < bodyLength) {
          System.err.printf("***ERROR: Only read %d / %d bytes specified", bytesRead, bodyLength);
          sendHttpErrorResponse(socketOutStream, RespInternalErr);
          responseMade = true;
        }
        
        Path filePath = ServerFileDirectory.resolve(pathStrings[2]);
        Path normalizedPath = filePath.normalize();
        if (!normalizedPath.startsWith(ServerFileDirectory)) {
          System.err.println("***ERROR: HTTP request attempts to access file outside of server's file directory");
          sendHttpErrorResponse(socketOutStream, RespForbidden); // return 403 Forbidden
          responseMade = true;
          return;
        } else {
          Files.write(filePath, requestBody);
        }
        
        byteMessage = String.format("%s %s%s%s", Protocol, RespOK, CRLF, CRLF).getBytes(StandardCharsets.US_ASCII);
        socketOutStream.write((byteMessage));
        responseMade = true;
        
        }
      }
      if (!responseMade) {
        sendHttpErrorResponse(socketOutStream, RespNotFound); // return 404 Not Found
      }
    }
  }

  static void sendHttpErrorResponse(OutputStream socketOutStream, String httpErrorString) throws IOException {
    socketOutStream.write(String.format("%s %s%s%s", Protocol, httpErrorString, CRLF, CRLF).getBytes(StandardCharsets.US_ASCII));
    return;
  }
}

