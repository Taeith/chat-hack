package fr.upem.net.tcp.nonblocking;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerChaton {

    static private class Context {
    	
    	final private MessageReader messageReader = new MessageReader();
    	
        final private SelectionKey key;
        final private SocketChannel sc;
        final private ByteBuffer bbin = ByteBuffer.allocate(BUFFER_SIZE);
        final private ByteBuffer bbout = ByteBuffer.allocate(BUFFER_SIZE);
        final private Queue<Message> queue = new LinkedList<>();
        final private ServerChaton server;
        private boolean closed = false;

        private Context(ServerChaton server, SelectionKey key){
            this.key = key;
            this.sc = (SocketChannel) key.channel();
            this.server = server;
        }

        /**
         * Process the content of bbin
         *
         * The convention is that bbin is in write-mode before the call
         * to process and after the call
         *
         */
        private void processIn() {
        	System.out.println("<processIn>");
        	while (true) {
        		System.out.println("\t loop");
        		Reader.ProcessStatus status = messageReader.process(bbin);
        		switch (status) {
        			case DONE:
        				System.out.println("\t done");
        				Message message = messageReader.get();
        				server.broadcast(message);
        				messageReader.reset();
        				bbin.compact();
        				System.out.println("sQSD " + bbin.position() + " " + bbin.limit());
        				System.out.println("</processIn>");
        				return;
        			case REFILL:
        				System.out.println("\t refill");
        				return;
        			case ERROR:
        				System.out.println("\t ERRRRRRROORRR");
        				silentlyClose();
        				return;
        		}
        	}    	
        }

        /**
         * Add a message to the message queue, tries to fill bbOut and updateInterestOps
         *
         * @param msg
         */
        private void queueMessage(Message message) {
        	System.out.println("<queueMessage>");
            queue.add(message);
            processOut();
            updateInterestOps();
            System.out.println("</queueMessage>");
        }

        /**
         * Try to fill bbout from the message queue
         *
         */
        private void processOut() {
        	System.out.println("<processOut>");
            while (!queue.isEmpty()) {
            	ByteBuffer messageBytes = queue.peek().getBytes().flip();
            	System.out.println("\t" + bbout.remaining() + " " + messageBytes.limit());
            	if (bbout.remaining() >= messageBytes.limit()) {
            		System.out.println("add / bbout.position " + bbout.position());
            		bbout.put(messageBytes);
            		queue.poll();
            	} else {
            		return;
            	}
            }
            System.out.println("</processOut>");
        }

        /**
         * Update the interestOps of the key looking
         * only at values of the boolean closed and
         * of both ByteBuffers.
         *
         * The convention is that both buffers are in write-mode before the call
         * to updateInterestOps and after the call.
         * Also it is assumed that process has been be called just
         * before updateInterestOps.
         */

        private void updateInterestOps() {
        	System.out.println("<updateInterestOps>");
        	System.out.println("1");
            var interestOps = 0;
            System.out.println("2");
            if (!closed && bbin.hasRemaining()) {
            	System.out.println("\t READ");
                interestOps = interestOps | SelectionKey.OP_READ;
            }
            System.out.println("\t -> " + bbout.position() + " " + bbout.limit());
            if (bbout.position() != 0) {
            	System.out.println("\t WRITE");
                interestOps = interestOps | SelectionKey.OP_WRITE;
            }
            System.out.println("3");
            if (interestOps == 0) {
            	System.out.println("\t ERRRRRRROORRR");
                silentlyClose();
                return;
            }
            System.out.println("5");
            key.interestOps(interestOps);
            System.out.println("</updateInterestOps>");
        }

        private void silentlyClose() {
            try {
                sc.close();
            } catch (IOException e) {
                // ignore exception
            }
        }

        /**
         * Performs the read action on sc
         *
         * The convention is that both buffers are in write-mode before the call
         * to doRead and after the call
         *
         * @throws IOException
         */
        private void doRead() throws IOException {
        	System.out.println("<doWrite>");
            if (sc.read(bbin) == -1) {
                closed = true;              
            }
            processIn();
            updateInterestOps();
            System.out.println("</doWrite>");
        }

        /**
         * Performs the write action on sc
         *
         * The convention is that both buffers are in write-mode before the call
         * to doWrite and after the call
         *
         * @throws IOException
         */

        private void doWrite() throws IOException {
        	System.out.println("<doWrite>");
            bbout.flip();
            sc.write(bbout);
            bbout.compact();
            processOut();
            updateInterestOps();
            System.out.println("</doWrite>");
        }

    }

    static private int BUFFER_SIZE = 1_024;
    static private Logger logger = Logger.getLogger(ServerChatInt.class.getName());    
    
    private final ServerSocketChannel serverSocketChannel;
    private final Selector selector;

    public ServerChaton(int port) throws IOException {
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress(port));
        selector = Selector.open();
    }

    public void launch() throws IOException {
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        while(!Thread.interrupted()) {
            printKeys(); // for debug
            System.out.println("Starting select");
            try {
                selector.select(this::treatKey);
            } catch (UncheckedIOException tunneled) {
                throw tunneled.getCause();
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
        } catch(IOException ioe) {
            // lambda call in select requires to tunnel IOException
            throw new UncheckedIOException(ioe);
        }
        try {
            if (key.isValid() && key.isWritable()) {
                ((Context) key.attachment()).doWrite();
            }
            if (key.isValid() && key.isReadable()) {
                ((Context) key.attachment()).doRead();
            }
        } catch (IOException e) {
            logger.log(Level.INFO,"Connection closed with client due to IOException",e);
            silentlyClose(key);
        }
    }

    private void doAccept(SelectionKey key) throws IOException {
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
        SocketChannel socketChannel = serverSocketChannel.accept();
        if (socketChannel == null) {
            return;
        }
        socketChannel.configureBlocking(false);
        SelectionKey skey = socketChannel.register(selector, SelectionKey.OP_READ);
        skey.attach(new Context(this, skey));
    }

    private void silentlyClose(SelectionKey key) {
        Channel sc = (Channel) key.channel();
        try {
            sc.close();
        } catch (IOException e) {
            // ignore exception
        }
    }

    /**
     * Add a message to all connected clients queue
     *
     * @param msg
     */
    private void broadcast(Message msg) {
        for (SelectionKey key : selector.keys()) {
            SelectableChannel channel = key.channel();
            if (channel instanceof ServerSocketChannel) {
                continue;
            }
            Context context = (Context) key.attachment();
            context.queueMessage(msg);
        }       
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        if (args.length!=1){
            usage();
            return;
        }
        new ServerChaton(Integer.parseInt(args[0])).launch();
    }

    private static void usage(){
        System.out.println("Usage : ServerSumBetter port");
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
