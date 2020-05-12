package fr.upem.net.tcp.nonblocking;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

public class Message {
	
	private final static Charset UTF8 = Charset.forName("UTF-8");
	
	private final String username;
	private final String content;
	
	public Message(String username, String content) {
		this.username = username;
		this.content = content;		
	}
	public ByteBuffer toByteBuffer() {
		ByteBuffer userBytes = UTF8.encode(username);
		ByteBuffer contentBytes = UTF8.encode(content);
		ByteBuffer byteBuffer = ByteBuffer.allocate(Integer.BYTES * 2 + userBytes.limit() + contentBytes.limit());
		byteBuffer.putInt(userBytes.limit()).put(userBytes);
		byteBuffer.putInt(contentBytes.limit()).put(contentBytes);
		return byteBuffer.flip();
	}

	@Override
	public String toString() {
		return "Message [username=" + username + ", content=" + content + "]";
	}

}
