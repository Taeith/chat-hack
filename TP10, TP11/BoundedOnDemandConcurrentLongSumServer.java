package fr.upem.net.tcp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.Semaphore;

public class BoundedOnDemandConcurrentLongSumServer {

    private static final Logger logger = Logger.getLogger(BoundedOnDemandConcurrentLongSumServer.class.getName());
    private static final int BUFFER_SIZE = 1024;
    private static final int MAX_THREAD = 10;
    private final ServerSocketChannel serverSocketChannel;
    private final Semaphore semaphore;

    public BoundedOnDemandConcurrentLongSumServer(int port) throws IOException {
        this.serverSocketChannel = ServerSocketChannel.open();
        this.serverSocketChannel.bind(new InetSocketAddress(port));
        this.semaphore = new Semaphore(MAX_THREAD); 
        logger.info(this.getClass().getName() + " starts on port " + port);
    }

    /**
     * Iterative server main loop
     *
     * @throws IOException
     */
    public void launch() throws IOException, InterruptedException {
        logger.info("Server started");
        while(!Thread.interrupted()) {
            semaphore.acquire();
            SocketChannel client = serverSocketChannel.accept();
            new Thread(() -> {
                try {
                    logger.info("Connection accepted from " + client.getRemoteAddress());
                    serve(client);
                } catch (IOException ioe) {
                    logger.log(Level.INFO,"Connection terminated with client by IOException",ioe.getCause());
                } catch (InterruptedException ie) {
                    logger.info("Server interrupted");
                    return;
                } finally {
                    silentlyClose(client);
                    semaphore.release();
                }
            }).start();            
        }
    }

    /**
     * Treat the connection sc applying the protocole
     * All IOException are thrown
     *
     * @param sc
     * @throws IOException
     * @throws InterruptedException
     */
    private void serve(SocketChannel client) throws IOException, InterruptedException {
        ByteBuffer intBuffer = ByteBuffer.allocate(Integer.BYTES);
        ByteBuffer longBuffer = ByteBuffer.allocate(Long.BYTES);
        while (!Thread.interrupted()) {
            intBuffer.clear();
            if (!readFully(client, intBuffer)) {
                logger.info("Connection closed by client.");
                return;
            }
            intBuffer.flip();
            int numberOfLong = intBuffer.getInt();
            if (numberOfLong <= 0) {
                return;
            }
            ByteBuffer buff = ByteBuffer.allocate(numberOfLong * Long.BYTES);
            if (!readFully(client, buff)) {
                logger.info("Connection closed by client before sendings the longs.");
                return;
            }
            long sum = 0l;
            buff.flip();
            while (buff.hasRemaining()) {
                sum += buff.getLong();
            }
            longBuffer.clear();
            longBuffer.putLong(sum);
            longBuffer.flip();
            client.write(longBuffer);
        }       
     }

    /**
     * Close a SocketChannel while ignoring IOExecption
     *
     * @param sc
     */

    private void silentlyClose(SocketChannel sc) {
        if (sc != null) {
            try {
                sc.close();
            } catch (IOException e) {
                // Do nothing
            }
        }
    }

    static boolean readFully(SocketChannel sc, ByteBuffer bb) throws IOException {
        while(bb.hasRemaining()) {
            if (sc.read(bb)==-1){
                logger.info("Input stream closed");
                return false;
            }
        }
        return true;
    }

    public static void main(String[] args) throws NumberFormatException, IOException, InterruptedException {
        BoundedOnDemandConcurrentLongSumServer server = new BoundedOnDemandConcurrentLongSumServer(Integer.parseInt(args[0]));
        server.launch();
    }
}
