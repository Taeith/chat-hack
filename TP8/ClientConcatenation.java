
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.*;

public class ClientConcatenation {
	
	private static final int BUFFER_SIZE = 1024;
	public static final Charset UTF8 = Charset.forName("UTF-8");

	private static boolean check(List<String> list, String response) {
		StringBuilder stringBuilder = new StringBuilder();
        for (String word : list) {
        	stringBuilder.append(word).append(",");
        }
        String concatenation = stringBuilder.toString();
        concatenation = concatenation.substring(0, concatenation.length() - 1);
        System.out.println("(" + concatenation + ", " + response + ")");
        return concatenation.equals(response);
    }
	
    private static Optional<String> request(SocketChannel socketChannel, List<String> list) throws IOException {
       ByteBuffer byteBuffer = ByteBuffer.allocate(BUFFER_SIZE);
       ByteBuffer intBuffer = ByteBuffer.allocate(Integer.BYTES);
       byteBuffer.putInt(list.size());
       byteBuffer.flip();
       socketChannel.write(byteBuffer);
       for (String word : list) {
    	   byteBuffer.clear();
    	   byteBuffer.putInt(word.length());
    	   byteBuffer.put(UTF8.encode(word));
    	   byteBuffer.flip();
         socketChannel.write(byteBuffer);
       }
       if (!readFully(socketChannel, intBuffer)) {
       		return Optional.empty();
       }
       intBuffer.flip();
       int numberOfBytes = intBuffer.getInt();
       ByteBuffer stringBuffer = ByteBuffer.allocate(numberOfBytes);
       if (!readFully(socketChannel, stringBuffer)) {
          return Optional.empty();
       }
       stringBuffer.flip();
       return Optional.of(UTF8.decode(stringBuffer).toString());
    }

    public static void main(String[] args) throws IOException {
        InetSocketAddress server = new InetSocketAddress(args[0], Integer.valueOf(args[1]));
        try (SocketChannel sc = SocketChannel.open(server)) {
        	ArrayList<String> list = new ArrayList<String>();
        	list.addAll(List.of("A", "B", "C", "D", "E", "F"));
            Optional<String> l = request(sc, list);
            if (!l.isPresent()) {
                System.err.println("Connection with server lost.");
                return;
            }
            if (!check(list, l.get())) {
                System.err.println("Oups! Something wrong happens!");
            }
        }
    }

  static boolean readFully(SocketChannel socketChannel, ByteBuffer byteBuffer) {
    byteBuffer.clear();
    while (byteBuffer.hasRemaining()) {
		  try {
			 int read = socketChannel.read(byteBuffer);
			 if (read == -1) {
        return false;
      }
		   } catch (IOException exception) {
	    	   return false;
	       }
	   }
	   return true;
    }
    
}
