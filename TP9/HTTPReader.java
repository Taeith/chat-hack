package fr.upem.net.tcp.http;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.HashMap;


public class HTTPReader {

    private final Charset ASCII_CHARSET = Charset.forName("ASCII");
    private final SocketChannel socketChannel;
    private final ByteBuffer byteBuffer;

    public HTTPReader(SocketChannel socketChannel, ByteBuffer byteBuffer) {
        this.socketChannel = socketChannel;
        this.byteBuffer = byteBuffer;
    }

    /**
     * @return The ASCII string terminated by CRLF without the CRLF
     * <p>
     * The method assume that buff is in write mode and leave it in write-mode
     * The method never reads from the socket as long as the buffer is not empty
     * @throws IOException HTTPException if the connection is closed before a line could be read
     */
    public String readLineCRLF() throws IOException {
    	StringBuilder stringBuilder = new StringBuilder();
    	ByteBuffer asciiBuffer = ByteBuffer.allocate(1);
    	// Check if ByteBuffer is empty
    	if (byteBuffer.position() == 0) {
    		if (socketChannel.read(byteBuffer) == -1) {
				throw new HTTPException();
			}
    	}
    	byteBuffer.flip();
    	while (true) {
    		// Check if ByteBuffer needs to be filled
    		if (!byteBuffer.hasRemaining()) {
    			byteBuffer.clear();
    			if (socketChannel.read(byteBuffer) == -1) {
    				throw new HTTPException();
    			}
    			byteBuffer.flip(); 			
    		}
    		// Read an ASCII character
    		asciiBuffer.clear();
    		asciiBuffer.put(byteBuffer.get());
    		asciiBuffer.flip();
    		stringBuilder.append(ASCII_CHARSET.decode(asciiBuffer).toString()); // juste cast de l'octer -> (char) bb.get() voir la vidéo
    		// Check if this is the last character
    		String line = stringBuilder.toString(); // rappel des precedents octets lus / o(1) au lieu de o(n)
    		if (line.length() > 1 && 
    			line.charAt(line.length() - 2 ) == '\r' &&
    			line.charAt(line.length() - 1 ) == '\n') {
    			byteBuffer.compact();
    			return line.substring(0, line.length() - 2);
    		}
    	}
    }
    
    /**
     * @return The HTTPHeader object corresponding to the header read
     * @throws IOException HTTPException if the connection is closed before a header could be read
     *                     if the header is ill-formed
     */
    public HTTPHeader readHeader() throws IOException {
    	String status = readLineCRLF();
    	HashMap<String, String> map = new HashMap<String, String>();
    	String line = readLineCRLF();
    	while (!line.equals("")) {    		
    		String[] parts = line.split(": "); // ne spliter que sur le premier : / cas d'erreur si pas de : -> HTTPException
    		String key = parts[0];
    		String value = parts[1];
    		if (map.containsKey(key)) {
    			map.put(key, map.get(key) + value); // merge
    		} else {
    			map.put(key, value);
    		}
    		line = readLineCRLF();
    	}
    	return HTTPHeader.create(status, map);
    }

    /**
     * @param size
     * @return a ByteBuffer in write-mode containing size bytes read on the socket
     * @throws IOException HTTPException is the connection is closed before all bytes could be read
     */
    public ByteBuffer readBytes(int size) throws IOException {
    	ByteBuffer buffer = ByteBuffer.allocate(size);
    	if (byteBuffer.position() == 0) { // pas efficace
			socketChannel.read(byteBuffer);
    	}
    	byteBuffer.flip();
    	while (true) {
    		if (!byteBuffer.hasRemaining()) {
    			byteBuffer.clear();
    			socketChannel.read(byteBuffer);
    			byteBuffer.flip();
    		}   
    		buffer.put(byteBuffer.get());
    		if (!buffer.hasRemaining()) {
    			byteBuffer.compact();
    	        return buffer;
    		}
    	}    	
    }

    /**
     * @return a ByteBuffer in write-mode containing a content read in chunks mode
     * @throws IOException HTTPException if the connection is closed before the end of the chunks
     *                     if chunks are ill-formed
     */
    public ByteBuffer readChunks() throws IOException {
    	int bufferSize = 1;
    	ByteBuffer buffer = ByteBuffer.allocate(bufferSize);
    	ByteBuffer temper = ByteBuffer.allocate(bufferSize);
    	while (true) {
    		int size = Integer.parseInt(readLineCRLF(), 16);
    		if (size == 0) {
    			break;
    		}
    		while (buffer.remaining() < size) {
    			buffer.flip();
    			bufferSize *= 2;
    			temper = ByteBuffer.allocate(bufferSize);
    			temper.put(buffer);
    			buffer = temper.duplicate();
    		}
    		ByteBuffer chunkBuffer = readBytes(size);
    		chunkBuffer.flip();    		
    		buffer.put(chunkBuffer);
    		readLineCRLF(); 
    	}
        return buffer;
    }


    public static void main(String[] args) throws IOException {
        String request = "/";
        HTTPClient client = new HTTPClient("www.w3.org", request);
        //client.connect();
        
        String chunkedRequest = "GET / HTTP/1.1\r\n"
                + "Host: www.u-pem.fr\r\n"
                + "\r\n";
        HTTPClient chunkedClient = new HTTPClient("www.u-pem.fr", chunkedRequest);
        chunkedClient.connect();
    }
}
