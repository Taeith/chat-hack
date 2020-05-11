package fr.upem.net.buffers;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class ReadStandardInputWithEncoding {

    private static void usage(){
        System.out.println("Usage: ReadStandardInputWithEncoding charset");
    }
    
    private static String stringFromStandardInput(Charset cs) throws IOException {
        ReadableByteChannel channel = Channels.newChannel(System.in);
        ByteBuffer byteBuffer = ByteBuffer.allocate(10);
        int numberOfBytes = 0;
        do {
        	numberOfBytes = channel.read(byteBuffer);
        	ByteBuffer tmp = ByteBuffer.allocate(byteBuffer.capacity() * 2);
        	byteBuffer.flip();
        	tmp.put(byteBuffer);
        	byteBuffer = tmp;
         } while (numberOfBytes != -1);
         byteBuffer.flip();
        return cs.decode(byteBuffer).toString();
    }

    public static void main(String[] args) throws IOException {
        if (args.length!=1){
            usage();
            return;
        }
        Charset cs=Charset.forName(args[0]);
        System.out.print(stringFromStandardInput(cs));
    }


}
