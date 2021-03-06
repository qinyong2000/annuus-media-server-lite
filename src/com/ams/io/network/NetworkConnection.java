package com.ams.io.network;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ams.io.buffer.ByteBufferFactory;

public class NetworkConnection extends Connection {
    private final Logger logger = LoggerFactory.getLogger(NetworkConnection.class);
    
    protected static final int MIN_READ_BUFFER_SIZE = 256;
    protected static final int MAX_READ_BUFFER_SIZE = 64 * 1024;

    protected Selector selector;
    protected SocketChannel channel = null;
    protected SelectionKey selectionKey;
    protected int interestOps;

    protected ByteBuffer readBuffer = null;
    protected ByteBuffer[] writeBuffer = null;
    protected long keepAliveTime;
    
    public NetworkConnection() {
        super();
        keepAlive();
    }

    protected void registerChannel(Selector selector) {
        this.selector = selector;
        try {
            this.selectionKey = channel.register(selector, interestOps, this);
            logger.debug("registered connection: {}", this);
        } catch (ClosedChannelException e) {
            logger.debug("register connection error: {}", this);
        }
    }
    
    protected void readChannel() throws IOException {
        if (isReadBlocking()) {
            return;
        }
        keepAlive();
        if (readBuffer == null || readBuffer.remaining() < MIN_READ_BUFFER_SIZE) {
            readBuffer = ByteBufferFactory.allocate(MAX_READ_BUFFER_SIZE);
        }
        int readBytes = channel.read(readBuffer);
        if (readBytes > 0) {
            ByteBuffer slicedBuffer = readBuffer.slice();
            readBuffer.flip();
            offerInboundBuffers(new ByteBuffer[] { readBuffer });
            readBuffer = slicedBuffer;
        } else if (readBytes < 0) {
            throw new EOFException("read channel eof");
        }
    }

    protected synchronized void writeToChannel() throws IOException {
        keepAlive();
        if (writeBuffer == null) {
            writeBuffer = pollOutboundBuffers();
        }
        boolean hasRemaining = false;
        if (writeBuffer.length > 0) {
            long len = channel.write(writeBuffer);
            if (len < 0) {
                throw new EOFException("write channel error");
            }
            for (ByteBuffer buf : writeBuffer) {
                if (buf.hasRemaining()) {
                    hasRemaining = true;
                }
            }
        }
        if (!hasRemaining) {
            writeBuffer = null;
        }
        // dispatch to write
        if (selectionKey != null && selectionKey.isValid()) {
            if (hasRemaining) {
                selectionKey.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                selector.wakeup();
            } else {
                selectionKey.interestOps(SelectionKey.OP_READ);
            }
        }
    }

    protected void keepAlive() {
        keepAliveTime = System.currentTimeMillis();
    }

    protected long getKeepAliveTime() {
        return keepAliveTime;
    }

    @Override
    public void flush() throws IOException {
        super.flush();
        writeToChannel();
    }
    
    @Override
    public void close(boolean keepAlive) {
        super.close();
        if (!keepAlive) {
            try {
                if (channel != null) channel.close();
                selectionKey = null;
            } catch (IOException e) {
            }
        }
    }
    
    @Override
    public String toString() {
        return channel.socket().getLocalSocketAddress().toString();
    }
}
