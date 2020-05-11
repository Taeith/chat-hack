package fr.upem.net.tcp.nonblocking;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

public class Message {
	
	private final static Charset UTF8 = Charset.forName("UTF-8");
	
	private final String username;
	private final String content;
	private ByteBuffer byteBuffer;
	
	public Message(String username, String content, ByteBuffer byteBuffer) {
		this.username = username;
		this.content = content;
		this.byteBuffer = byteBuffer;			
	}
	
	public ByteBuffer getBytes() {
		return byteBuffer;
	}
	
	public static ByteBuffer bytesFor(String username, String content) {
		ByteBuffer userBytes = UTF8.encode(username);
		ByteBuffer contentBytes = UTF8.encode(content);
		ByteBuffer byteBuffer = ByteBuffer.allocate(Integer.BYTES * 2 + userBytes.limit() + contentBytes.limit());
		byteBuffer.putInt(userBytes.limit()).put(userBytes);
		byteBuffer.putInt(contentBytes.limit()).put(contentBytes);
		return byteBuffer;
	}

	@Override
	public String toString() {
		return "Message [username=" + username + ", content=" + content + "]";
	}

}
