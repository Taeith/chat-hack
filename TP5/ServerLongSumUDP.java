package fr.upem.net.udp;

import java.util.HashMap;
import java.util.logging.Logger;
import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class ServerLongSumUDP {

   private static final Logger logger = Logger.getLogger(ServerLongSumUDP.class.getName());
   private static final Charset UTF8 = StandardCharsets.UTF_8;
    private static final int BUFFER_SIZE = 1024;
    private final DatagramChannel dc;
    private final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
    // address -> session -> packet-id -> value-of-packet
    private final HashMap<InetSocketAddress, HashMap<Session, HashMap<Long, Long>>> map;
    
    private class Session {
        private final long id;
        private final long total;
        public Session(long id, long total) {
            this.id = id;
            this.total = total;
        }       
        public long getId() {
            return id;
        }
        public long getTotal() {
            return total;
        }
        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + getEnclosingInstance().hashCode();
            result = prime * result + (int) (id ^ (id >>> 32));
            result = prime * result + (int) (total ^ (total >>> 32));
            return result;
        }
        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            Session other = (Session) obj;
            if (!getEnclosingInstance().equals(other.getEnclosingInstance()))
                return false;
            if (id != other.id)
                return false;
            if (total != other.total)
                return false;
            return true;
        }
        private ServerLongSumUDP getEnclosingInstance() {
            return ServerLongSumUDP.this;
        }       
    }
    
    public ServerLongSumUDP(int port) throws IOException {
        dc = DatagramChannel.open();
        dc.bind(new InetSocketAddress(port));
        map = new HashMap<InetSocketAddress, HashMap<Session, HashMap<Long, Long>>>();
        logger.info("ServerBetterUpperCaseUDP started on port " + port);
    }
    
    private boolean isSessionClosed(InetSocketAddress address, Session session) {
        return map.get(address).get(session).keySet().size() == session.getTotal();
    }
    
    private long getSessionSum(InetSocketAddress address, Session session) {
        return map.get(address).get(session).values().stream().mapToLong(l -> l).sum();
    }

    public void serve() throws IOException {
        while (!Thread.interrupted()) {
            byteBuffer.clear();
            InetSocketAddress exp = (InetSocketAddress) dc.receive(byteBuffer);
            byteBuffer.flip();
            //System.out.println(byteBuffer.remaining());
            if (byteBuffer.remaining() != 4 * Long.BYTES + Byte.BYTES) {
                logger.warning("Packet défectueux!");
                continue;
            }
            /*
            for (HashMap<Session, HashMap<Long, Long>> sessionMap : map.values()) {
                for (Session key : sessionMap.keySet()) {
                    System.out.println("Session (" + key.getId() + ", " + key.getTotal() + ")");
                    for (Long packet : sessionMap.get(key).keySet()) {
                        System.out.println("    Packet (" + packet + ", " + sessionMap.get(key).get(packet) + ")");
                    }
                }
            }
    */
            byte type = byteBuffer.get();
            long sessionId = byteBuffer.getLong();
            long packetId = byteBuffer.getLong();
            long numberOfPackets = byteBuffer.getLong();
            long packetValue = byteBuffer.getLong();
            /*
            System.out.println("type " + type);
            System.out.println("sessionId " + sessionId);
            System.out.println("packetId " + packetId);
            System.out.println("numberOfPackets " + numberOfPackets);
            System.out.println("packetValue " + packetValue);
            
            System.out.println();
            */
            if (type != (byte) 1) {
                throw new IllegalStateException();
            }
            Session session = new Session(sessionId, numberOfPackets);
            HashMap<Session, HashMap<Long, Long>> addressMap = map.get(exp);
            if (addressMap == null) {
                map.put(exp, new HashMap<Session, HashMap<Long, Long>>());
                addressMap = map.get(exp);
            }
            HashMap<Long, Long> sessionMap = addressMap.get(session);
            if (sessionMap == null) {
                addressMap.put(session, new HashMap<Long, Long>());
                sessionMap = addressMap.get(session);
            }
            Long packet = sessionMap.get(packetId);
            if (packet == null) {
                sessionMap.put(packetId, packetValue);
            }
            // process
            byteBuffer.clear();
            
            if (isSessionClosed(exp, session)) {
                byteBuffer.put((byte) 3);
                byteBuffer.putLong(sessionId);
                byteBuffer.putLong(getSessionSum(exp, session));
            }
            else {
                byteBuffer.put((byte) 2);
                byteBuffer.putLong(sessionId);
                byteBuffer.putLong(packetId);
            }
            byteBuffer.flip();
          dc.send(byteBuffer, exp);
        }
    }

    public static void usage() {
        System.out.println("Usage : ServerIdUpperCaseUDP port");
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            usage();
            return;
        }
        ServerLongSumUDP server;
        int port = Integer.valueOf(args[0]);
        if (!(port >= 1024) & port <= 65535) {
            logger.severe("The port number must be between 1024 and 65535");
            return;
        }
        try {
            server = new ServerLongSumUDP(port);
        } catch (BindException e) {
            logger.severe("Server could not bind on " + port + "\nAnother server is probably running on this port.");
            return;
        }
        server.serve();
    }
}
