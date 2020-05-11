package fr.upem.net.tcp.http;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;

public class HTTPClient {
	
	private final Charset ASCII_CHARSET = Charset.forName("ASCII");
	private final ByteBuffer byteBuffer;
	private SocketChannel socketChannel;
	private HTTPReader reader;
	private final InetSocketAddress address;
	private final String request;
	
	public HTTPClient(String address, String request) {
		this.byteBuffer = ByteBuffer.allocate(50);        
		this.address = new InetSocketAddress(address, 80);
		this.request = request;
		this.socketChannel = null;
		this.reader = null;
	}
	
	public void getResource() throws IOException, HTTPException { // code de retour -> 
		socketChannel = SocketChannel.open();
		socketChannel.connect(this.address);
		this.reader = new HTTPReader(socketChannel, byteBuffer);
		socketChannel.write(ASCII_CHARSET.encode(request));
        HTTPHeader header = reader.readHeader();
        if (!header.getContentType().contains("text/html")) {
        	throw new IllegalStateException("Not an HTML document.");
        }
        ByteBuffer content = null;
        if (header.isChunkedTransfer()) {
        	content = reader.readChunks();
        } else {
        	content = reader.readBytes(header.getContentLength());
        }        
        content.flip();
        String text = header.getCharset().decode(content).toString();
        try (PrintStream out = new PrintStream(new FileOutputStream("C:\\Users\\Nils_2.PERNETN\\Desktop\\TP3\\tp8networkprog.html"))) {
            out.print(text);
        }
        System.out.println(text);
        socketChannel.close();
	}

}
