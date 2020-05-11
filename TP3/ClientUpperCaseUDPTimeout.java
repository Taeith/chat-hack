package fr.upem.net.udp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.util.Scanner;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;



public class ClientUpperCaseUDPTimeout {

    public static final int BUFFER_SIZE = 1024;

    private static void usage(){
        System.out.println("Usage : NetcatUDP host port charset");
    }

    public static void main(String[] args) throws IOException, InterruptedException {

        if (args.length != 3){
            usage();
            return;
        }

        InetSocketAddress server = new InetSocketAddress(args[0],Integer.parseInt(args[1]));
        
        Charset cs = Charset.forName(args[2]);

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
                } catch (InterruptedException | IOException e) {
                    e.printStackTrace();
                }
            }             
        });
        listener.start();

        try (Scanner scan = new Scanner(System.in);){
            while(scan.hasNextLine()){
                
                // send
                String line = scan.nextLine();
                dc.send(cs.encode(line), server);
                
                // receive                
                String message = queue.poll(1, TimeUnit.SECONDS);
                if (message == null) {
                    System.out.println("Server did not respond in time");
                    continue;
                }
                System.out.println("New  packet");
                System.out.println("  text: " + message);
                
            }
        }

    }
}
