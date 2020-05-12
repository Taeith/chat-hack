package fr.upem.net.tcp.nonblocking;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import fr.upem.net.tcp.nonblocking.Reader.ProcessStatus;

public class StringReader implements Reader<String> {
	
	private final static Charset UTF8 = Charset.forName("UTF-8");
    private enum State { DONE, WAITING_FOR_SIZE, WAITING_FOR_CONTENT, ERROR };
    private final int MAX_SIZE = 1_024;
    private State state = State.WAITING_FOR_SIZE;
    private final IntReader intReader = new IntReader();
    private ByteBuffer internalBuffer = ByteBuffer.allocate(MAX_SIZE);
    private int size;
    private String value;

    @Override
    public ProcessStatus process(ByteBuffer buffer) {
        switch (state) {
            case WAITING_FOR_SIZE:
            	ProcessStatus status = intReader.process(buffer);
                switch (status) {
                    case DONE:
                        break;
                    case REFILL:
                        return ProcessStatus.REFILL;
                    default:
                        throw new AssertionError();
                }
                size = intReader.get();
                if (size == 0 || size > MAX_SIZE) {
                    state = State.ERROR;
                    return ProcessStatus.ERROR;
                }
                state = State.WAITING_FOR_CONTENT;
            case WAITING_FOR_CONTENT:
                int missing = size - internalBuffer.position();
                buffer.flip();
                if (buffer.remaining() <= missing) {
                    internalBuffer.put(buffer);
                } else {
                	int oldLimit = buffer.limit();
                    buffer.limit(missing);
                    internalBuffer.put(buffer);
                    buffer.limit(oldLimit);
                }
                buffer.compact();
                if (internalBuffer.position() < size) {
                    return ProcessStatus.REFILL;
                }
                state = State.DONE;
                internalBuffer.flip();
                value = UTF8.decode(internalBuffer).toString();
                return ProcessStatus.DONE;
            default:
                throw new AssertionError();
        }
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
        state = State.WAITING_FOR_SIZE;
        internalBuffer.clear();
        intReader.reset();
        value = null;
        size = 0;
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
