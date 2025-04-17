import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Main {
  public static void main(String[] args) {
    // Print statements for debugging - visible when running tests.
    System.out.println("Logs from your program will appear here!");


    try {
      ServerSocket serverSocket = new ServerSocket(4221);
    
      // Since the tester restarts program quite often, setting SO_REUSEADDR
      // ensures that we don't run into 'Address already in use' errors
      serverSocket.setReuseAddress(true);
    
      serverSocket.accept(); // Wait for connection from client.
      System.out.println("accepted new connection");
    } catch (IOException e) {
      System.out.println("IOException: " + e.getMessage());
    }
  }
}
