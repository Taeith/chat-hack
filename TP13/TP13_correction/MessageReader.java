package fr.upem.net.tcp.nonblocking;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

public class MessageReader implements Reader<Message> {

    private enum State { DONE, WAITING_FOR_USERNAME, WAITING_FOR_CONTENT, ERROR };    
    private final StringReader stringReader = new StringReader();
    private State state = State.WAITING_FOR_USERNAME;
    private String username;
    private String content;

    @Override
    public ProcessStatus process(ByteBuffer byteBuffer) {
        switch (state) {
        	case WAITING_FOR_USERNAME:
        		ProcessStatus status = stringReader.process(byteBuffer);
        		switch (status) {
        			case REFILL:
        				return status;
        			case ERROR:
        				state = State.ERROR;
        				return status;
        		}
        		username = stringReader.get();
        		state = State.WAITING_FOR_CONTENT;
        		stringReader.reset();
        	case WAITING_FOR_CONTENT:
        		status = stringReader.process(byteBuffer);
        		switch (status) {
        			case REFILL:
        				return status;
        			case ERROR:
        				state = State.ERROR;
        				return status;
        		}
        		content = stringReader.get();
        		state = State.DONE;
        		stringReader.reset();
        		return ProcessStatus.DONE;
        	default:
        		throw new AssertionError();
        }
    }
        
    @Override
    public Message get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return new Message(username, content);
    }

    @Override
    public void reset() {
        state = State.WAITING_FOR_USERNAME;
        stringReader.reset();
        username = null;
        content = null;
    }
    
    public static void main(String[] args) {
		MessageReader reader = new MessageReader();
		ByteBuffer bb1 = Charset.forName("UTF-8").encode("Taeith");
		ByteBuffer bb2 = Charset.forName("UTF-8").encode("Hello");
		ByteBuffer bb = ByteBuffer.allocate(Integer.BYTES * 2 + bb1.capacity() + bb2.capacity());
		bb.putInt(bb1.limit());
		bb.put(bb1);		
		bb.putInt(bb2.limit());
		bb.put(bb2);
		reader.process(bb);
		System.out.println(reader.get());
	}
    
}
