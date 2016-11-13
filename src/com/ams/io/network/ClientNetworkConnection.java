package com.ams.io.network;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class ClientNetworkConnection extends NetworkConnection {
    public static int CONNECT_ERROR_TIMEOUT = 1;
    public static int CONNECT_ERROR = 2;

    protected static Dispatcher networkDispatcher = null;
    protected int connectTimeout = DEFAULT_TIMEOUT_MS;
    protected InetSocketAddress remote;
    protected long startTime;
    protected NetworkConnectionListener listener = null;
    protected Executor eventDispatcher = null;
    
    
    public ClientNetworkConnection(InetSocketAddress remote) {
        super();
        this.remote = remote;
        setEventDispatcher(Executors.newSingleThreadExecutor());
    }

    public ClientNetworkConnection(String host, int port) {
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
                if (selectionKey != null && selectionKey.isValid()) {
                    selectionKey.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                }
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
                    listener.onConnectionError(ClientNetworkConnection.this, error);
                }
            });
        }
    }
    
    public void connect(NetworkConnectionListener listener) throws IOException {
        setListener(listener);
        if (channel == null) {
            channel = SocketChannel.open();
            channel.socket().setTcpNoDelay(true);
            SocketAddress bindPoint = new InetSocketAddress(0); // bind temp port
            channel.socket().bind(bindPoint);
            channel.configureBlocking(false);
            interestOps = SelectionKey.OP_CONNECT;
            getNetworkDispatcher().addConnectionToRegister(this);
        }
        channel.connect(remote);
        startTime = System.currentTimeMillis();
    }

    public void connect() throws IOException {
        NetworkConnectionListener listener = new NetworkConnectionListener() {
            @Override
            public void onConnectionEstablished(NetworkConnection conn) {
                synchronized (conn) {
                    conn.notify();
                }
            }
            @Override
            public void onConnectionClosed(NetworkConnection conn) {
            }
            @Override
            public void onConnectionError(NetworkConnection conn, int error) {
                synchronized (conn) {
                    conn.notify();
                }
            }
            @Override
            public void onConnectionDataReceived(NetworkConnection conn, ByteBuffer[] buffers) {
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
    
    @Override
    public void offerInboundBuffers(final ByteBuffer buffers[]) {
        super.offerInboundBuffers(buffers);
        
        // dispatch event
        if (listener != null) {
            dispatchEvent(new Runnable() {
                @Override
                public void run() {
                    listener.onConnectionDataReceived(ClientNetworkConnection.this, buffers);
                }
            });
        }
        
    }

    @Override
    public void open() {
        super.open();
        // dispatch event
        if (listener != null) {
            dispatchEvent(new Runnable() {
                @Override
                public void run() {
                    listener.onConnectionEstablished(ClientNetworkConnection.this);
                }
            });
        }
    }
    
    @Override
    public void close() {
        super.close();
        // dispatch event
        if (listener != null) {
            dispatchEvent(new Runnable() {
                @Override
                public void run() {
                    listener.onConnectionClosed(ClientNetworkConnection.this);
                }
            });
        }
    }
    
    public void setConnectTimeout(int timeout) {
        this.connectTimeout = timeout;
    }

    public InetSocketAddress getRemoteAddress() {
        return remote;
    }
    
    protected void dispatchEvent(Runnable event) {
        if (eventDispatcher == null) {
            event.run();
        } else {
            eventDispatcher.execute(event);
        }
    }
    
    public void setEventDispatcher(Executor eventDispatcher) {
        this.eventDispatcher = eventDispatcher;
    }
    
    public void setListener(NetworkConnectionListener listener) {
        this.listener = listener;
    }
    
    protected NetworkConnectionListener getListener() {
        return listener;
    }
    
}
