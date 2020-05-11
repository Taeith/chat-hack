package fr.upem.net.udp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.util.Scanner;



public class NetcatUDP {

    public static final int BUFFER_SIZE = 1024;

    private static void usage(){
        System.out.println("Usage : NetcatUDP host port charset");
    }

    public static void main(String[] args) throws IOException {

        if (args.length != 3){
            usage();
            return;
        }

        InetSocketAddress server = new InetSocketAddress(args[0],Integer.parseInt(args[1]));
        
        Charset cs = Charset.forName(args[2]);
        ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);

        DatagramChannel dc = DatagramChannel.open();
        dc.bind(null);

        try (Scanner scan = new Scanner(System.in);){
            while(scan.hasNextLine()){
                // send
                String line = scan.nextLine();
                dc.send(cs.encode(line), server);
                // receive
                InetSocketAddress exp = (InetSocketAddress) dc.receive(bb);
                bb.flip();
                System.out.println("New  packet");
                System.out.println("  address: " + exp);
                System.out.println("  number of bytes received: " + bb.remaining());
                System.out.println("  text: " + cs.decode(bb).toString());
                bb.clear();
            }
        }

    }
}
