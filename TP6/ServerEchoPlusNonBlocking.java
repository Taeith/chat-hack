import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.Charset;
import java.util.logging.Logger;

public class ServerEchoPlusNonBlocking {

    private static final Logger logger = Logger.getLogger(ServerEchoPlusNonBlocking.class.getName());

    private final DatagramChannel dc;
    private final Selector selector;
    private final int BUFFER_SIZE = 1024;
    private final ByteBuffer buff = ByteBuffer.allocateDirect(BUFFER_SIZE);
    private SocketAddress exp;
    private int port;

    public ServerEchoPlusNonBlocking(int port) throws IOException {
        this.port=port;
        selector = Selector.open();
        dc = DatagramChannel.open();
        dc.bind(new InetSocketAddress(port));
        dc.configureBlocking(false);
        dc.register(selector, SelectionKey.OP_READ);
   }


    public void serve() throws IOException {
        logger.info("ServerEcho started on port " + port);
        while (!Thread.interrupted()) {
            try {
                selector.select(this::treatKey);
            } catch (UncheckedIOException tunneled) {
                throw tunneled.getCause();
            }
        }
    }

    private void treatKey(SelectionKey key) {
        try{
            if (key.isValid() && key.isWritable()) {
                doWrite(key);
            }
            if (key.isValid() && key.isReadable()) {
                doRead(key);
            }
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }

    }

    private void doRead(SelectionKey key) throws IOException {
        buff.clear();
        exp = dc.receive(buff);
        if (exp == null) {
            return;
        }
        buff.flip();
        int limit = buff.limit();
        ByteBuffer temp = ByteBuffer.allocateDirect(limit); // allocateDirect seulement pour pas dynamique
        while(buff.hasRemaining()) {
            temp.put((byte) ((buff.get() + 1) % 255));
        }
        buff.clear();
        temp.flip();
        buff.put(temp);
        buff.flip();
        key.interestOps(SelectionKey.OP_WRITE);
     }

    private void doWrite(SelectionKey key) throws IOException {     
        dc.send(buff, exp);
        if (buff.hasRemaining()) {
            return;
        }
        key.interestOps(SelectionKey.OP_READ);
    }

    public static void usage() {
        System.out.println("Usage : ServerEcho port");
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            usage();
            return;
        }
        ServerEchoPlusNonBlocking server = new ServerEchoPlusNonBlocking(Integer.valueOf(args[0]));
        server.serve();
    }




}
