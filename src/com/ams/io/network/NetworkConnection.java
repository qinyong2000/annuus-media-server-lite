package com.ams.io.network;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;

import com.ams.io.buffer.ByteBufferFactory;

public class NetworkConnection extends Connection {
    protected static final int MIN_READ_BUFFER_SIZE = 256;
    protected static final int MAX_READ_BUFFER_SIZE = 64 * 1024;
    protected SocketChannel channel = null;
    protected int interestOps;
    protected ByteBuffer readBuffer = null;
    protected long keepAliveTime;
    protected int writeTimeout = DEFAULT_TIMEOUT_MS;
    protected int writeRetryCount = 2;
    
    public NetworkConnection() {
        super();
        keepAlive();
    }

    protected boolean finishConnect() throws IOException {
        if (channel.isConnectionPending()) {
            return channel.finishConnect();
        }
        return false;
    }

    protected void readChannel() throws IOException {
        if (isReadBlocking()) {
            return;
        }
        if (readBuffer == null || readBuffer.remaining() < MIN_READ_BUFFER_SIZE) {
            readBuffer = ByteBufferFactory.allocate(MAX_READ_BUFFER_SIZE);
        }
        int readBytes = channel.read(readBuffer);
        if (readBytes > 0) {
            ByteBuffer slicedBuffer = readBuffer.slice();
            readBuffer.flip();
            offerInboundBuffers(new ByteBuffer[] { readBuffer });
            readBuffer = slicedBuffer;
        } else if (readBytes == -1) {
            throw new EOFException("read channel eof");
        }
    }

    protected void writeToChannel(ByteBuffer[] data)
            throws IOException {
        Selector writeSelector = null;
        SelectionKey writeKey = null;
        int retry = 0;
        try {
            while (data != null) {
System.out.println("write channel");
                long len = channel.write(data);
                if (len < 0) {
                    throw new EOFException();
                }

                boolean hasRemaining = false;
                for (ByteBuffer buf : data) {
                    if (buf.hasRemaining()) {
                        hasRemaining = true;
                        break;
                    }
                }

                if (!hasRemaining) {
                    break;
                }
                if (len > 0) {
                    retry = 0;
                } else {
                    retry++;

                    // Check if there are more to be written.
                    if (writeSelector == null) {
                        writeSelector = Selector.open();
                        try {
                            writeKey = channel.register(writeSelector,
                                    SelectionKey.OP_WRITE);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    if (writeSelector.select(writeTimeout) == 0) {
                        if (retry > writeRetryCount) {
                            throw new IOException("Client disconnected");
                        }
                    }
                }
            }
        } finally {
            if (writeKey != null) {
                writeKey.cancel();
                writeKey = null;
            }
            if (writeSelector != null) {
                writeSelector.selectNow();
            }
            keepAlive();
            data = null;
        }
    }

    protected void keepAlive() {
        keepAliveTime = System.currentTimeMillis();
    }

    public long getKeepAliveTime() {
        return keepAliveTime;
    }

    public void setChannelInterestOps(SocketChannel channel, int interestOps) {
        this.channel = channel;
        this.interestOps = interestOps;
    }
    
    public SelectableChannel getChannel() {
        return channel;
    }

    public int getInterestOps() {
        return interestOps;
    }
    
    public synchronized void flush() throws IOException {
        if (outStream != null) {
            outStream.flush();
        }
        ByteBuffer[] buf = pollOutboundBuffers();
        if (buf.length > 0) {
            writeToChannel(buf);
        }
    }

    public void close(boolean keepAlive) {
        super.close();
        if (!keepAlive) {
            try {
                if (channel != null) channel.close();
            } catch (IOException e) {
            }
        }
    }
}
