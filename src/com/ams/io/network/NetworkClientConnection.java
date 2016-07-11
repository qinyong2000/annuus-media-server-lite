package com.ams.io.network;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class NetworkClientConnection extends NetworkConnection {
    protected static Dispatcher dispatcher = null;
    protected int connectTimeout = DEFAULT_TIMEOUT_MS;
    protected InetSocketAddress remote;
    
	public NetworkClientConnection(InetSocketAddress remote) {
        super();
        this.remote = remote;
    }

    public NetworkClientConnection(String host, int port) {
        super();
        this.remote = new InetSocketAddress(host, port);
    }
    
    protected Dispatcher getDispatcher() throws IOException {
        if (dispatcher == null) {
            synchronized (this) {
                dispatcher = new Dispatcher();
                dispatcher.start();
            }
        }
        return dispatcher;
    }
    
    public void connect(ConnectionListener listener) throws IOException {
        addListener(listener);
        if (channel == null) {
            channel = SocketChannel.open();
            // channel.socket().setReuseAddress(true);
            SocketAddress bindPoint = new InetSocketAddress(0); // bind temp port
            channel.socket().bind(bindPoint);
            channel.configureBlocking(false);
            interestOps = SelectionKey.OP_CONNECT;
            getDispatcher().addChannelToRegister(this);
        }
        channel.connect(remote);
    }
    
    public void connect() throws IOException {
        ConnectionListener listener = new ConnectionListener() {
            public void connectionEstablished(Connection conn) {
                synchronized (conn) {
                    conn.notifyAll();
                }
            }
            public void connectionClosed(Connection conn) {
            }
        };
        long start = System.currentTimeMillis();
        
        try {
            synchronized (this) {
                connect(listener);
                wait(connectTimeout);
            }
        } catch (Exception e) {
            channel = null;
            throw new IOException("connect error");
        } finally {
            removeListener(listener);
        }
        long now = System.currentTimeMillis();
        if (now - start >= connectTimeout) {
            channel = null;
            throw new IOException("connect time out");
        }
    }
    
    public void setConnectTimeout(int timeout) {
        this.connectTimeout = timeout;
    }

    public InetSocketAddress getRemoteAddress() {
        return remote;
    }
    
}
