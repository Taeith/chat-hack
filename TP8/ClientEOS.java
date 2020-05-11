
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.logging.Logger;

public class ClientEOS {

    public static final Charset UTF8 = Charset.forName("UTF-8");
    public static final int BUFFER_SIZE = 1024;
    public static final Logger logger = Logger.getLogger(ClientEOS.class.getName());

    /**
     * This method:
     *  - connect to server
     *  - writes the bytes corresponding to request in UTF8
     *  - closes the write-channel to the server
     *  - stores the bufferSize first bytes of server response
     *  - return the corresponding string in UTF8
     *
     * @param request
     * @param server
     * @param bufferSize
     * @return the UTF8 string corresponding to bufferSize first bytes of server response
     * @throws IOException
     */
    public static String getFixedSizeResponse(String request, SocketAddress serverAddress, int bufferSize) throws IOException {
        ByteBuffer byteBuffer = ByteBuffer.allocate(BUFFER_SIZE);
        SocketChannel socketChannel = SocketChannel.open();
        socketChannel.connect(serverAddress);
        socketChannel.write(UTF8.encode(request));
        socketChannel.shutdownOutput();
        readFully(socketChannel, byteBuffer);
        byteBuffer.flip();
        return UTF8.decode(byteBuffer).toString();
    }

    /**
     * This method:
       *  - connect to server
       *  - writes the bytes corresponding to request in UTF8
       *  - closes the write-channel to the server
       *  - reads and stores all bytes from server until read-channel is closed
       *  - return the corresponding string in UTF8
       *
       * @param request
       * @param server
       * @return the UTF8 string corresponding the full response of the server
       * @throws IOException
     */

    public static String getUnboundedResponse(String request, SocketAddress serverAddress) throws IOException {
        ByteBuffer byteBuffer = ByteBuffer.allocate(BUFFER_SIZE);        
        ByteBuffer tempBuffer = ByteBuffer.allocate(BUFFER_SIZE);
        int x = 2;
        SocketChannel socketChannel = SocketChannel.open();
        socketChannel.connect(serverAddress);
        socketChannel.write(UTF8.encode(request));
        socketChannel.shutdownOutput();
        while (readFully(socketChannel, byteBuffer)) {
            byteBuffer.flip();
            tempBuffer = ByteBuffer.allocate(BUFFER_SIZE * x);
            tempBuffer.put(byteBuffer);
            byteBuffer = tempBuffer.duplicate();            
            x++;
        }
        byteBuffer.flip();
        return UTF8.decode(byteBuffer).toString();
    }

   /**
      * Fill the workspace of the Bytebuffer with bytes read from sc.
      *
      * @param sc
      * @param bb
      * @return false if read returned -1 at some point and true otherwise
      * @throws IOException
      */
    static boolean readFully(SocketChannel socketChannel, ByteBuffer byteBuffer) throws IOException {
        while (byteBuffer.hasRemaining()) {
            int read = socketChannel.read(byteBuffer);
            if (read == -1) {
                return false;
            }
        }
        return true;
    }

    public static void main(String[] args) throws IOException {
          InetSocketAddress google = new InetSocketAddress("www.google.fr", 80);
          //System.out.println(getFixedSizeResponse("GET / HTTP/1.1\r\nHost: www.google.fr\r\n\r\n", google,    512));
          System.out.println(getUnboundedResponse("GET / HTTP/1.1\r\nHost: www.google.fr\r\n\r\n", google));
    }
}
