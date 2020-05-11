package fr.upem.net.tcp;

import java.io.IOException;
import java.nio.channels.SocketChannel;

public class ThreadData {

        private SocketChannel client;
        private long lastTimeRecorded;
        private final static Object lock = new Object();

        public void setSocketChannel(SocketChannel client) {
            synchronized (lock) {
                this.client = client;
            }       
        }

        public boolean isConnected() {
        	return client != null;
        }

        public SocketChannel getSocketChannel() {
            synchronized (lock) {
                return client;
            }       
        }
        
        public void tick() {
            synchronized (lock) {
                lastTimeRecorded = System.currentTimeMillis();
            }       
        }

        public void closeIfInactive(int timeout) {
            synchronized (lock) {
                if (System.currentTimeMillis() - lastTimeRecorded > timeout) {
                    close();
                }
            }       
        }

        public void close() {
            synchronized (lock) {
                silentlyClose(client);
            }       
        }

        private void silentlyClose(SocketChannel sc) {
            synchronized (lock) {
                if (sc != null) {
                    try {
                        sc.close();
                    } catch (IOException e) {
                        // Do nothing
                    }
                }
            }        
        }

    }