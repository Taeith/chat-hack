package fr.upem.net.tcp.nonblocking;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.ArrayList;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerSumOneShot {
    
    class Context {
        
        private final ByteBuffer byteBuffer;
        private final SocketChannel socketChannel;
        
        public Context(ByteBuffer byteBuffer, SocketChannel socketChannel) {
            this.byteBuffer = byteBuffer;
            this.socketChannel = socketChannel;
        }
        
        public ByteBuffer getByteBuffer() {
            return byteBuffer;
        }
        
        public SocketChannel getSocketChannel() {
            return socketChannel;
        }
        
    }

    static private int BUFFER_SIZE = 2 * Integer.BYTES;
    private final ServerSocketChannel serverSocketChannel;  
    private final Selector selector;

    public ServerSumOneShot(int port) throws IOException {      
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress(port));
        selector = Selector.open();
    }

    public void launch() throws IOException {
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);     
        while (!Thread.interrupted()) {
            printKeys();
            System.out.println("Starting select");
            try {
                selector.select(this::treatKey);
            } catch (RuntimeException e) {
                throw new IOException();
            }           
            System.out.println("Select finished");
        }
    }

    private void treatKey(SelectionKey key) {
        printSelectedKey(key); // for debug
        try {
            if (key.isValid() && key.isAcceptable()) {
                doAccept(key);
            }
        } catch (IOException e) {
            throw new RuntimeException();
        }
        try {
            if (key.isValid() && key.isWritable()) {
                doWrite(key);
            }
            if (key.isValid() && key.isReadable()) {
                doRead(key);
            }
        } catch (IOException e) {
            silentlyClose(key);
        }       
    }

    private void doAccept(SelectionKey key) throws IOException {    
        System.out.println("a");
        SocketChannel socketChannel = serverSocketChannel.accept();
        if (socketChannel == null) {
            return;
        }
        System.out.println("b" + socketChannel.toString());
        key.attach(new Context(ByteBuffer.allocate(BUFFER_SIZE), socketChannel));
        System.out.println("c");
        return;
        try {
            key.interestOps(SelectionKey.OP_READ);
        } catch (Exception e) {
            System.out.println("here");
            e.printStackTrace();
        }       
        System.out.println("d");
    }

    private void doRead(SelectionKey key) throws IOException {
        System.out.println("e");
        Context context = (Context) key.attachment();
        ByteBuffer byteBuffer = context.getByteBuffer();
        SocketChannel socketChannel = context.getSocketChannel();
        socketChannel.read(byteBuffer);
        if (byteBuffer.hasRemaining()) {
            return;
        }       
        byteBuffer.flip();
        ByteBuffer sumBuffer = ByteBuffer.allocate(Integer.BYTES);
        sumBuffer.putInt(byteBuffer.getInt() + byteBuffer.getInt()).flip();
        byteBuffer.clear().put(sumBuffer).flip();       
        key.interestOps(SelectionKey.OP_WRITE);
    }

    private void doWrite(SelectionKey key) throws IOException {
        Context context = (Context) key.attachment();
        ByteBuffer byteBuffer = context.getByteBuffer();
        SocketChannel socketChannel = context.getSocketChannel();
        socketChannel.write(byteBuffer);
        if (byteBuffer.hasRemaining()) {
            return;
        }
        key.interestOps(SelectionKey.OP_ACCEPT);
    }

    private void silentlyClose(SelectionKey key) {
        Channel sc = (Channel) key.channel();
        try {
            sc.close();
        } catch (IOException e) {
            // ignore exception
        }
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        if (args.length != 1) {
            usage();
            return;
        }
        new ServerSumOneShot(Integer.parseInt(args[0])).launch();
    }

    private static void usage(){
        System.out.println("Usage : ServerSumOneShot port");
    }

    /***
     *  Theses methods are here to help understanding the behavior of the selector
     ***/

    private String interestOpsToString(SelectionKey key){
        if (!key.isValid()) {
            return "CANCELLED";
        }
        int interestOps = key.interestOps();
        ArrayList<String> list = new ArrayList<>();
        if ((interestOps&SelectionKey.OP_ACCEPT)!=0) list.add("OP_ACCEPT");
        if ((interestOps&SelectionKey.OP_READ)!=0) list.add("OP_READ");
        if ((interestOps&SelectionKey.OP_WRITE)!=0) list.add("OP_WRITE");
        return String.join("|",list);
    }

    public void printKeys() {
        Set<SelectionKey> selectionKeySet = selector.keys();
        if (selectionKeySet.isEmpty()) {
            System.out.println("The selector contains no key : this should not happen!");
            return;
        }
        System.out.println("The selector contains:");
        for (SelectionKey key : selectionKeySet){
            SelectableChannel channel = key.channel();
            if (channel instanceof ServerSocketChannel) {
                System.out.println("\tKey for ServerSocketChannel : "+ interestOpsToString(key));
            } else {
                SocketChannel sc = (SocketChannel) channel;
                System.out.println("\tKey for Client "+ remoteAddressToString(sc) +" : "+ interestOpsToString(key));
            }
        }
    }

    private String remoteAddressToString(SocketChannel sc) {
        try {
            return sc.getRemoteAddress().toString();
        } catch (IOException e){
            return "???";
        }
    }

    public void printSelectedKey(SelectionKey key) {
        SelectableChannel channel = key.channel();
        if (channel instanceof ServerSocketChannel) {
            System.out.println("\tServerSocketChannel can perform : " + possibleActionsToString(key));
        } else {
            SocketChannel sc = (SocketChannel) channel;
            System.out.println("\tClient " + remoteAddressToString(sc) + " can perform : " + possibleActionsToString(key));
        }
    }

    private String possibleActionsToString(SelectionKey key) {
        if (!key.isValid()) {
            return "CANCELLED";
        }
        ArrayList<String> list = new ArrayList<>();
        if (key.isAcceptable()) list.add("ACCEPT");
        if (key.isReadable()) list.add("READ");
        if (key.isWritable()) list.add("WRITE");
        return String.join(" and ",list);
    }
}
