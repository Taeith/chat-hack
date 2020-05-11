package fr.upem.net.tcp.nonblocking;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import fr.upem.net.tcp.nonblocking.Reader.ProcessStatus;

public class StringReader implements Reader<String> {
	
	private final static Charset UTF8 = Charset.forName("UTF-8");
    private enum State { DONE, WAITING, ERROR };
    private State state = State.WAITING;    
    private ByteBuffer byteBuffer = null;
    private int length;
    private String value;

    @Override
    public ProcessStatus process(ByteBuffer bb) {
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }
        bb.flip();
        try {
            if (length == 0) {
            	length = bb.getInt();
            	byteBuffer = ByteBuffer.allocate(length);
            }
            if (value == null) {
            	if (!fill(bb)) {
            		return ProcessStatus.REFILL;
            	}
            	value = UTF8.decode(byteBuffer.flip()).toString();
            }	
        } finally {
            bb.compact();
        }
        state = State.DONE;
        return ProcessStatus.DONE;
    }
    
    private boolean fill(ByteBuffer bb) {
    	if (bb.remaining() <= byteBuffer.remaining()){
    		byteBuffer.put(bb);
        } else {
            var oldLimit = bb.limit();
            bb.limit(bb.position() + byteBuffer.remaining());
            byteBuffer.put(bb);
            bb.limit(oldLimit);
        }
    	return !byteBuffer.hasRemaining();
    }

    @Override
    public String get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return value;
    }

    @Override
    public void reset() {
        state = State.WAITING;
        byteBuffer.clear();
        value = null;
        length = 0;
    }
    
    public static void main(String[] args) {
		StringReader reader = new StringReader();
		ByteBuffer content1 = Charset.forName("UTF-8").encode("small");
		ByteBuffer content2 = Charset.forName("UTF-8").encode("a-very-long-tag-with-a-lot-of-words");
		ByteBuffer bb1 = ByteBuffer.allocate(Integer.BYTES + content1.capacity());
		bb1.putInt(content1.limit());
		bb1.put(content1);
		reader.process(bb1);
		System.out.println(reader.get());
		reader.reset();
		ByteBuffer bb2 = ByteBuffer.allocate(Integer.BYTES + content2.capacity());
		bb2.putInt(content2.limit());
		bb2.put(content2);		
		reader.process(bb2);
		System.out.println(reader.get());
		reader.reset();
	}
    
}
