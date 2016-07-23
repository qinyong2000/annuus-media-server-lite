package com.ams.io.network;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class NetworkClientConnection extends NetworkConnection {
    public static int CONNECT_ERROR_TIMEOUT = 1;
    public static int CONNECT_ERROR = 2;

	protected static Dispatcher networkDispatcher = null;
    protected int connectTimeout = DEFAULT_TIMEOUT_MS;
    protected InetSocketAddress remote;
    protected long startTime;
    
    
	public NetworkClientConnection(InetSocketAddress remote) {
        super();
        this.remote = remote;
    }

    public NetworkClientConnection(String host, int port) {
        super();
        this.remote = new InetSocketAddress(host, port);
    }
    
    protected Dispatcher getNetworkDispatcher() throws IOException {
        if (networkDispatcher == null) {
            synchronized (this) {
                networkDispatcher = new Dispatcher();
                networkDispatcher.start();
            }
        }
        return networkDispatcher;
    }
    
    protected boolean finishConnect() throws IOException {
        // connect timeout?
        if (isConnectTimeout()) {
             dispatchConnectError(CONNECT_ERROR_TIMEOUT);
             throw new IOException("connect timeout");
        }
        if (channel.isConnectionPending()) {
            try {
	            return channel.finishConnect();
            } catch (IOException e) {
                dispatchConnectError(CONNECT_ERROR_TIMEOUT);
               throw e;
            }
        }
        return false;
    }

    protected boolean isConnectTimeout() {
        long now = System.currentTimeMillis();
        return now - startTime >= connectTimeout;
    }
    
    protected void dispatchConnectError(final int error) {
        // dispatch event
        if (listener != null) {
            dispatchEvent(new Runnable() {
                @Override
                public void run() {
                    listener.onConnectionError(NetworkClientConnection.this, error);
                }
            });
        }
    }
    
    public void connect(ConnectionListener listener) throws IOException {
        setListener(listener);
        if (channel == null) {
            channel = SocketChannel.open();
            channel.socket().setTcpNoDelay(true);
            SocketAddress bindPoint = new InetSocketAddress(0); // bind temp port
            channel.socket().bind(bindPoint);
            channel.configureBlocking(false);
            interestOps = SelectionKey.OP_CONNECT;
            getNetworkDispatcher().addChannelToRegister(this);
        }
        channel.connect(remote);
        startTime = System.currentTimeMillis();
    }

    public void connect() throws IOException {
        ConnectionListener listener = new ConnectionListener() {
            @Override
            public void onConnectionEstablished(Connection conn) {
                synchronized (conn) {
                    conn.notifyAll();
                }
            }
            @Override
            public void onConnectionClosed(Connection conn) {
            }
            @Override
            public void onConnectionError(Connection conn, int error) {
                synchronized (conn) {
                    conn.notifyAll();
                }
            }
        };
        try {
            synchronized (this) {
                connect(listener);
                wait();
            }
        } catch (Exception e) {
            channel = null;
            throw new IOException("connect error");
        } finally {
            setListener(null);
        }
        if (isConnectTimeout()) {
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
