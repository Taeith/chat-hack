package fr.upem.net.udp;


import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ClientUpperCaseUDPFile {

        private static final Charset UTF8 = Charset.forName("UTF8");
        private static final int BUFFER_SIZE = 1024;
        private static void usage() {
            System.out.println("Usage : ClientUpperCaseUDPFile in-filename out-filename timeout host port ");
        }

        public static String getMessage(DatagramChannel dc, InetSocketAddress server, ArrayBlockingQueue<String> queue, Charset cs, String line) throws IOException, InterruptedException {	                   
            String message;
            do {
                dc.send(cs.encode(line), server);
                message = queue.poll(250, TimeUnit.MILLISECONDS);
            } while (message == null);
            return message;
	    }

        public static void main(String[] args) throws IOException, InterruptedException {

            if (args.length != 5) {
                usage();
                return;
            }

            String inFilename = args[0];
            String outFilename = args[1];
            int timeout = Integer.valueOf(args[2]);
            String host = args[3];
            int port = Integer.valueOf(args[4]);
            SocketAddress dest = new InetSocketAddress(host, port);

            InetSocketAddress server = new InetSocketAddress(host, port);	        
	        Charset cs = Charset.forName("UTF-8");
	        DatagramChannel dc = DatagramChannel.open();
	        dc.bind(null);	        
	        ArrayBlockingQueue<String> queue = new ArrayBlockingQueue(10);	        
	        Thread listener = new Thread(() ->  {
	            ByteBuffer byteBuffer = ByteBuffer.allocate(BUFFER_SIZE);
	            while (!Thread.interrupted()) {
	                try {
	                    dc.receive(byteBuffer);
	                    byteBuffer.flip();
	                    queue.put(cs.decode(byteBuffer).toString());
	                    byteBuffer.clear();
	                } catch (AsynchronousCloseException | InterruptedException e) {
		                logger.info("interrupted");
		            } catch (IOException e) {
		                logger.severe("interrupted");
		            }
	            }             
	        });
	        listener.start(); 

            //Read all lines of inFilename opened in UTF-8
            List<String> lines = Files.readAllLines(Paths.get(args[0]),UTF8);
            ArrayList<String> upperCaseLines = new ArrayList<>();

			for (String line : lines) {
				upperCaseLines.add(getMessage(dc, server, queue, cs, line));
			}

            // Write upperCaseLines to outFilename in UTF-8
            Files.write(Paths.get(outFilename),upperCaseLines, UTF8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        }
    }


