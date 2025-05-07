import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
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
          try (
            socket;
            ) { // handle socket cleanup for worker thread
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
      OutputStream socketOutStream = socket.getOutputStream();
    ) {
      System.out.println("***INFO: Accepted new connection");
      Map<String, String> headersMap = new HashMap<>();
      int BUFFER_SIZE = 8192;   // size of chunk to read & process at once, stored in RAM - can't make too big
      byte[] currentBytes = new byte[BUFFER_SIZE];

      int readBytes;
      int bytesInBuffer = 0;
      int bytesProcessed = 0; 

      byte[] currentHeaderBytes;
      int currentHeaderLength;
      boolean allHeadersConsumed = false;

      byte[] partialBody = new byte[0];

      // Example Request:
          // POST /submit-form HTTP/1.1\r\n
          // Host: mywebapp.com\r\n
          // Content-Type: application/x-www-form-urlencoded\r\n
          // Content-Length: 27\r\n
          // \r\n
          // name=John+Doe&age=30
      
      while(!allHeadersConsumed) {   // only process headers
        readBytes = socketInStream.read(currentBytes, bytesInBuffer, BUFFER_SIZE - bytesInBuffer);
        if (readBytes == -1) throw new IOException("Client disconnected prematurely"); 
        bytesInBuffer += readBytes;

        for(int i = 0; i < bytesInBuffer; i++) {  // process the bytes currently in the buffer           
          if(i > 0 && currentBytes[i] == '\n' && currentBytes[i-1] == '\r') {   // found CRLF
            currentHeaderLength = i - 1 - bytesProcessed;

            if (currentHeaderLength == 0) {    // found double CRLF
              allHeadersConsumed = true;
              bytesProcessed = i + 1;   // consume final '\n' in double CRLF
              partialBody = Arrays.copyOfRange(currentBytes, bytesProcessed, bytesInBuffer);    // returns new array sized to number of bytes (.length is accurate)
              break;
            }
            currentHeaderBytes = Arrays.copyOfRange(currentBytes, bytesProcessed, i-1);
            String headerString = new String(currentHeaderBytes, 0, currentHeaderLength, StandardCharsets.US_ASCII);

            if (headersMap.isEmpty()) {     // is empty for first line of request - ex. "Method URI Version CRLF"
              String[] requestLinePieces = headerString.split(" ");
              if (requestLinePieces.length != 3) {
                throw new IOException("HTTP request line malformed/incorrect");
              }
              headersMap.put("request-line", headerString);
              headersMap.put("method", requestLinePieces[0]);
              headersMap.put("uri", requestLinePieces[1]);
              headersMap.put("version", requestLinePieces[2]);
            } else if (currentHeaderLength > 0) {
              int colonIndex = headerString.indexOf(":");
              if (colonIndex > 0) {
                String headerName = headerString.substring(0, colonIndex).trim().toLowerCase();     // normalize the headerName for look-ups
                String headerValue = headerString.substring(colonIndex + 1).trim();
                headersMap.put(headerName, headerValue);
              } else {
                  System.err.println("***WARN: Malformed header line ignored: " + headerString);
              } 
            }
            bytesProcessed = i + 1;   // processed i / BUFFER_SIZE bytes so far
          }
        }

        int unprocessedBytes = bytesInBuffer - bytesProcessed;   
        if (!allHeadersConsumed) {
          System.arraycopy(currentBytes, bytesProcessed, currentBytes, 0, unprocessedBytes);    // void System.arraycopy(Object src, int srcPos, Object dest, int destPos, int length)
          bytesProcessed = 0;     // reset for next iteration
        }
        bytesInBuffer = unprocessedBytes;    // the leftover bytes, ex. partial header at end of buffer   
      }

      int remainingBodyLength;
      try {
        remainingBodyLength = Integer.parseInt(headersMap.getOrDefault("content-length", "0")) - partialBody.length;
        if (remainingBodyLength < 0) remainingBodyLength = 0;
      } catch (NumberFormatException e) {
        remainingBodyLength = 0;
      }
      handleRequest(headersMap, socket, socketInStream, socketOutStream, partialBody, remainingBodyLength);
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

  public static void handleRequest(Map<String, String> headersMap, Socket socket, InputStream socketInStream, OutputStream socketOutStream, byte[] partialBody, int remainingBodyLength) throws IOException, Exception {
    
    boolean responseMade = false;
    String method = headersMap.get("method");
    String[] pathStrings = headersMap.get("uri").split("/");
    byte[] byteMessage;
  
    if ("GET".equals(method)) {
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
        String userAgentValue = headersMap.get("user-agent");
        byteMessage = String.format("%s %s%s%s%s%s%s%d%s%s%s", Protocol, RespOK, CRLF, ContentType, TextContent, CRLF, ContentLength, userAgentValue.length(), CRLF, CRLF, userAgentValue).getBytes(StandardCharsets.US_ASCII);
        socketOutStream.write((byteMessage));
        responseMade = true;
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
    } else if ("POST".equals(method)) {
      if ("files".equals(pathStrings[1])) { // POST "files/{fileName}" - create file with name as fileName and content as the request's body

      if (ServerFileDirectory == null) {
        System.err.println("***ERROR (POST files/{fileName}): Request to interact with null ServerFileDirectory, not initialized");
        sendHttpErrorResponse(socketOutStream, RespInternalErr);
        responseMade = true;
      }

      String contentType = headersMap.getOrDefault("content-type", TextContent);
      int contentLength = 0;
      try {
        contentLength = Integer.parseInt(headersMap.get("content-length"));
        if (contentLength < 0 || contentLength > 10_000_000) {
          System.err.println("***ERROR: Invalid Content-Length header, size of " + contentLength);
          sendHttpErrorResponse(socketOutStream, RespInternalErr);
          responseMade = true;
          return;
        }
      } catch (NumberFormatException e) {
        contentLength = 0;
      }

      byte[] requestBody = new byte[contentLength];
      System.arraycopy(partialBody, 0, requestBody, 0, partialBody.length);     // copy over partially read body
      int bytesRead = socketInStream.readNBytes(requestBody, partialBody.length, remainingBodyLength);     // read rest of body from input stream

      System.out.printf("bytesRead: %d%n", bytesRead);
      System.out.printf("contentLength: %d%n", contentLength);
      int receivedBytes = bytesRead + partialBody.length;
      if (receivedBytes < contentLength) {
        System.err.printf("***ERROR: Only received %d / %d bytes specified in Content-Length header", receivedBytes, contentLength);
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

  static void sendHttpErrorResponse(OutputStream socketOutStream, String httpErrorString) throws IOException {
    socketOutStream.write(String.format("%s %s%s%s", Protocol, httpErrorString, CRLF, CRLF).getBytes(StandardCharsets.US_ASCII));
    return;
  }
}

