package fr.upem.net.tcp.nonblocking;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Optional;

import fr.upem.net.tcp.nonblocking.Reader.ProcessStatus;

public class MessageReader implements Reader<Message> {

    private enum State { DONE, WAITING, ERROR };
    private State state = State.WAITING;
    private final StringReader stringReader = new StringReader();
    private Message message = null;
    private String username;
    private String content;

    @Override
    public ProcessStatus process(ByteBuffer bb) {
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }
        try {
            if (username == null) {
            	username = readString(bb);
            }
            if (content == null) {
            	content = readString(bb);	
            }	
        } finally {
            bb.compact();
        }
        state = State.DONE;
        message = new Message(username, content, Message.bytesFor(username, content));
        return ProcessStatus.DONE;
    }
    
    private String readString(ByteBuffer bb) {    	
    	while (true) {
		   Reader.ProcessStatus status = stringReader.process(bb);
		   switch (status){
		      case DONE:
		    	  String value = stringReader.get();
		    	  stringReader.reset();
		          return value;
		      case REFILL:
		          continue;
		      case ERROR:
		          throw new IllegalStateException();
		    }
		  }
    }
    
    @Override
    public Message get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return message;
    }

    @Override
    public void reset() {
        state = State.WAITING;
        message = null;
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
