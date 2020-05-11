package fr.umlv.set;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.logging.Logger;

public class ClientIdUpperCaseUDPBurst {

        private static Logger logger = Logger.getLogger(ClientIdUpperCaseUDPBurst.class.getName());
        private static final Charset UTF8 = StandardCharsets.UTF_8;
        private static final int BUFFER_SIZE = 1024;
        private final List<String> lines;
        private final int nbLines;
        private final String[] upperCaseLines;
        private final int timeout;
        private final String outFilename;
        private final InetSocketAddress serverAddress;
        private final DatagramChannel dc;
        private final BitSet received;
        private final static Object lock = new Object();

        private static void usage() {
            System.out.println("Usage : ClientIdUpperCaseUDPBurst in-filename out-filename timeout host port ");
        }

        private ClientIdUpperCaseUDPBurst(List<String> lines,int timeout,InetSocketAddress serverAddress,String outFilename) throws IOException {
            this.lines = lines;
            this.nbLines = lines.size();
            this.timeout = timeout;
            this.outFilename = outFilename;
            this.serverAddress = serverAddress;
            this.dc = DatagramChannel.open();
            dc.bind(null);
            this.received= new BitSet(nbLines);
            this.upperCaseLines = new String[nbLines];
        }
        
        private boolean areAllLinesReceived() {
            synchronized(lock) {
                boolean areAllLinesReceived = true;
                for (int i = 0; i < nbLines; i++) {
                    if (received.get(i) == false) {
                        areAllLinesReceived = false;
                    }
                }
                return areAllLinesReceived;
            }
        }
        
        private void sendPacket(long id, String message) {
            ByteBuffer byteBuffer = ByteBuffer.allocate(BUFFER_SIZE);
            byteBuffer.putLong(id);
            byteBuffer.put(UTF8.encode(message));
            byteBuffer.flip();
            try {
                dc.send(byteBuffer, serverAddress);
            } catch (IOException e) {
                logger.info("IOException");
            }
        }

        private void senderThreadRun() {
            while (!Thread.interrupted()) {
                for (int i = 0; i < nbLines; i++) {
                    synchronized(lock) {
                        if (received.get(i) == false) {
                            sendPacket(i, lines.get(i));
                        }
                    }                   
                }
                try {
                    Thread.sleep(timeout);
                } catch (InterruptedException e) {
                    logger.info("Close");
                }
            }
        }

        private void launch() throws IOException {
            Thread senderThread = new Thread(this::senderThreadRun);
            senderThread.start();
            
            ByteBuffer byteBuffer = ByteBuffer.allocate(BUFFER_SIZE);
            
            while (areAllLinesReceived() == false) {
                try {
                    dc.receive(byteBuffer);
                    byteBuffer.flip();                    
                    long id = byteBuffer.getLong();
                    String message = UTF8.decode(byteBuffer).toString();
                    synchronized(lock) {
                        
                        if (received.get((int) id) == false) {
                            
                            upperCaseLines[(int) id] = message;
                            received.set((int) id);
                        }
                    }           
                    byteBuffer.clear();
                } catch (AsynchronousCloseException e) {
                    logger.info("interrupted");
                } catch (IOException e) {
                    logger.severe("interrupted");
                }
            }
            senderThread.interrupt();
            
            Files.write(Paths.get(outFilename),Arrays.asList(upperCaseLines), UTF8,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING);

        }

        public static void main(String[] args) throws IOException, InterruptedException {
            if (args.length !=5) {
                usage();
                return;
            }

            String inFilename = args[0];
            String outFilename = args[1];
            int timeout = Integer.valueOf(args[2]);
            String host=args[3];
            int port = Integer.valueOf(args[4]);
            InetSocketAddress serverAddress = new InetSocketAddress(host,port);   

            //Read all lines of inFilename opened in UTF-8
            List<String> lines= Files.readAllLines(Paths.get(inFilename),UTF8);
            //Create client with the parameters and launch it
            ClientIdUpperCaseUDPBurst client = new ClientIdUpperCaseUDPBurst(lines,timeout,serverAddress,outFilename);

            client.launch();

        }
    }


