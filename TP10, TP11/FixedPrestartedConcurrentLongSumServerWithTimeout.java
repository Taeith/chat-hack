package fr.upem.net.tcp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ClosedChannelException;

import static java.util.concurrent.TimeUnit.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Scanner;

public class FixedPrestartedConcurrentLongSumServerWithTimeout {

    private static final Logger logger = Logger.getLogger(FixedPrestartedConcurrentLongSumServerWithTimeout.class.getName());
    private final ServerSocketChannel serverSocketChannel;
    private final int numberOfThreads;
    private final Thread[] threads;
    private final ThreadData[] threadDatas;

    public FixedPrestartedConcurrentLongSumServerWithTimeout(int port, int numberOfThreads) throws IOException {
        this.serverSocketChannel = ServerSocketChannel.open();
        this.serverSocketChannel.bind(new InetSocketAddress(port));
        this.numberOfThreads = numberOfThreads;
        this.threads = new Thread[numberOfThreads];
        this.threadDatas = new ThreadData[numberOfThreads];
        logger.info(this.getClass().getName() + " starts on port " + port);
    }

    /**
     * Iterative server main loop
     *
     * @throws IOException
     */
    public void launch() throws IOException, InterruptedException {
    	Arrays.setAll(threadDatas, i -> new ThreadData());
        logger.info("Server started");
        Arrays.setAll(threads, i -> new Thread(() -> {
        	try {
        		ThreadData threadData = threadDatas[i];
                while (!Thread.interrupted()) {
                    SocketChannel client = serverSocketChannel.accept();
                    threadData.setSocketChannel(client);
                    try {
                        logger.info("Connection accepted from " + client.getRemoteAddress());
                        serve(client, threadData);
                    } catch (IOException ioe) {
                        logger.log(Level.INFO,"Connection terminated with client by IOException",ioe.getCause());
                    } catch (InterruptedException ie) {
                        logger.info("Server interrupted");
                        return;
                    } finally {
                        silentlyClose(client);
                        threadData.setSocketChannel(null);
                    }
                }
            } catch (ClosedByInterruptException e) {
                logger.info("Worker has been asked to shutdown immediately.");
                return;
            } catch (AsynchronousCloseException e) {
                logger.info("Worker thread has stopped.");
            } catch (ClosedChannelException e) {
                logger.info("Worker has been asked to not take client anymore.");
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Worker thread has stopped.", e.getCause());
            }
        }));
        for (Thread thread : threads) {
        	thread.start();
        }
        // Monitor
        ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor();
        monitor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                for (ThreadData threadData : threadDatas) {
                    threadData.closeIfInactive(2000);
                }
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
        // COMMANDS
        Scanner scanner = new Scanner(System.in);
        while (true) {
            String command = scanner.next();
            switch (command) {
                case "INFO":
                    long numberOfClients = Arrays.stream(threadDatas).filter(t -> t.isConnected()).count();
                    System.out.println(numberOfClients + " clients sont connectés au serveur.");
                    break;
                case "SHUTDOWN":
                    serverSocketChannel.close();
                    break;
                case "SHUTDOWNNOW":
                    for (Thread thread : threads) {
                        thread.interrupt();
                    }
                    break;
            }
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
    private void serve(SocketChannel client, ThreadData threadData) throws IOException, InterruptedException {
        ByteBuffer intBuffer = ByteBuffer.allocate(Integer.BYTES);
        ByteBuffer longBuffer = ByteBuffer.allocate(Long.BYTES);
        while (!Thread.interrupted()) {
            intBuffer.clear();
            if (!readFully(client, intBuffer)) {
                logger.info("Connection closed by client.");
                return;
            }
            threadData.tick();
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
            threadData.tick();
            long sum = 0l;
            buff.flip();
            while (buff.hasRemaining()) {
                sum += buff.getLong();
            }
            longBuffer.clear();
            longBuffer.putLong(sum);
            longBuffer.flip();
            client.write(longBuffer);
            threadData.tick();
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
        while (bb.hasRemaining()) {
            if (sc.read(bb) == -1){
                logger.info("Input stream closed");
                return false;
            }            
        }
        return true;
    }

    public static void main(String[] args) throws NumberFormatException, IOException, InterruptedException{
        FixedPrestartedConcurrentLongSumServerWithTimeout server = new FixedPrestartedConcurrentLongSumServerWithTimeout(Integer.parseInt(args[0]), Integer.parseInt(args[1]));
        server.launch();
    }

    

}