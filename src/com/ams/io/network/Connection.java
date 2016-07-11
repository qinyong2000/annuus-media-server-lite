package com.ams.io.network;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

import com.ams.io.buffer.IByteBufferReader;
import com.ams.io.buffer.IByteBufferWriter;
import com.ams.io.buffer.BufferUtils;
import com.ams.io.ByteBufferInputStream;
import com.ams.io.ByteBufferOutputStream;

public class Connection implements IByteBufferReader, IByteBufferWriter {
    protected static final int DEFAULT_TIMEOUT_MS = 30000;
    protected static final int MAX_INBOUND_QUEUE_SIZE = 512;
    protected static final int MAX_OUTBOUND_QUEUE_SIZE = 512;
    protected ConcurrentLinkedQueue<ByteBuffer> inboundBufferQueue = new ConcurrentLinkedQueue<ByteBuffer>();
    protected ConcurrentLinkedQueue<ByteBuffer> outboundBufferQueue = new ConcurrentLinkedQueue<ByteBuffer>();
    protected AtomicLong readAvailable = new AtomicLong(0);
    protected boolean closed = true;
    protected int readTimeout = DEFAULT_TIMEOUT_MS;
    
    protected ByteBufferInputStream inStream;
    protected ByteBufferOutputStream outStream;
    protected List<ConnectionListener> listeners = new ArrayList<ConnectionListener>();

    public Connection() {
        this.inStream = new ByteBufferInputStream(this);
        this.outStream = new ByteBufferOutputStream(this);
    }

    public void open() {
        if (!isClosed())
            return;
        closed = false;
        for (ConnectionListener listener : listeners) {
            listener.connectionEstablished(this);
        }
    }

    public void close() {
        closed = true;
        try {
            flush();
        } catch (IOException e) {
        }
        for (ConnectionListener listener : listeners) {
            listener.connectionClosed(this);
        }
    }
    
    public void close(boolean keepAlive) {
        close();
    }

    public boolean isClosed() {
        return closed;
    }
    
    public void clear() {
        inboundBufferQueue.clear();
        outboundBufferQueue.clear();
        readAvailable.set(0);
        closed = true;
    }
    
    public boolean isReadBlocking() {
      return inboundBufferQueue.size() > MAX_INBOUND_QUEUE_SIZE;
    }
    
    public boolean isWriteBlocking() {
        return outboundBufferQueue.size() > MAX_OUTBOUND_QUEUE_SIZE;
    }

    public long readAvailable() {
        return readAvailable.get();
    }

    public void offerInboundBuffers(ByteBuffer buffers[]) {
        for (ByteBuffer buffer : buffers) {
            inboundBufferQueue.offer(buffer);
            readAvailable.addAndGet(buffer.remaining());
        }
        synchronized (inboundBufferQueue) {
            inboundBufferQueue.notifyAll();
        }
    }

    public ByteBuffer[] pollOutboundBuffers() {
        List<ByteBuffer> buffers = new ArrayList<ByteBuffer>();
        ByteBuffer data;
        while ((data = outboundBufferQueue.poll()) != null) {
            buffers.add(data);
        }
        return buffers.toArray(new ByteBuffer[buffers.size()]);
    }

    public ByteBuffer[] read(int size) throws IOException {
        List<ByteBuffer> list = new ArrayList<ByteBuffer>();
        int length = size;
        while (length > 0) {
            // read a buffer with blocking
            ByteBuffer buffer = inboundBufferQueue.peek();
            if (buffer != null) {
                int remain = buffer.remaining();

                if (length >= remain) {
                    list.add(inboundBufferQueue.poll());
                    length -= remain;
                    readAvailable.addAndGet(-remain);
                } else {
                    ByteBuffer slice = BufferUtils.trim(buffer, length);
                    list.add(slice);
                    readAvailable.addAndGet(-length);
                    length = 0;
                }
            } else {
                // wait new buffer append to queue
                // sleep for timeout ms
                long start = System.currentTimeMillis();
                try {
                    synchronized (inboundBufferQueue) {
                        inboundBufferQueue.wait(readTimeout);
                    }
                } catch (InterruptedException e) {
                    throw new IOException("read interrupted");
                }
                long now = System.currentTimeMillis();
                if (now - start >= readTimeout) {
                    throw new IOException("read time out");
                }
            }
        } // end while
        return list.toArray(new ByteBuffer[list.size()]);
    }

    public void write(ByteBuffer[] data) throws IOException {
        if (data == null) {
            return;
        }
        for (ByteBuffer buf : data) {
            outboundBufferQueue.offer(buf);
        }
    }

    public void flush() throws IOException {
        if (outStream != null) {
            outStream.flush();
        }
    }

    public boolean waitForInboundData(int time) {
        if (readAvailable.get() == 0) {
            long start = System.currentTimeMillis();
            try {
                synchronized (inboundBufferQueue) {
                    inboundBufferQueue.wait(time);
                }
            } catch (InterruptedException e) {
            }

            long now = System.currentTimeMillis();
            if (now - start >= readTimeout) {
                return false;
            }
        }
        return true;
    }

    public void setReadTimeout(int timeout) {
        this.readTimeout = timeout;
    }

    public ByteBufferInputStream getInputStream() {
        return inStream;
    }

    public ByteBufferOutputStream getOutputStream() {
        return outStream;
    }
    
    public void addListener(ConnectionListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public boolean removeListener(ConnectionListener listener) {
        return listeners.remove(listener);
    }
    
}
