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
    protected static final int MAX_QUEUE_SIZE = 1024;
    protected ConcurrentLinkedQueue<ByteBuffer> inBufferQueue = new ConcurrentLinkedQueue<ByteBuffer>();
    protected ConcurrentLinkedQueue<ByteBuffer> outBufferQueue = new ConcurrentLinkedQueue<ByteBuffer>();
    protected AtomicLong available = new AtomicLong(0);
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

    public boolean isWriteBlocking() {
        return outBufferQueue.size() > MAX_QUEUE_SIZE;
    }

    public long available() {
        return available.get();
    }

    public void offerInBuffers(ByteBuffer buffers[]) {
        for (ByteBuffer buffer : buffers) {
            inBufferQueue.offer(buffer);
            available.addAndGet(buffer.remaining());
        }
        synchronized (inBufferQueue) {
            inBufferQueue.notifyAll();
        }
    }

    public ByteBuffer[] pollOutBuffers() {
        List<ByteBuffer> buffers = new ArrayList<ByteBuffer>();
        ByteBuffer data;
        while ((data = outBufferQueue.poll()) != null) {
            buffers.add(data);
        }
        return buffers.toArray(new ByteBuffer[buffers.size()]);
    }

    public ByteBuffer[] read(int size) throws IOException {
        List<ByteBuffer> list = new ArrayList<ByteBuffer>();
        int length = size;
        while (length > 0) {
            // read a buffer with blocking
            ByteBuffer buffer = inBufferQueue.peek();
            if (buffer != null) {
                int remain = buffer.remaining();

                if (length >= remain) {
                    list.add(inBufferQueue.poll());
                    length -= remain;
                    available.addAndGet(-remain);
                } else {
                    ByteBuffer slice = BufferUtils.trim(buffer, length);
                    list.add(slice);
                    available.addAndGet(-length);
                    length = 0;
                }
            } else {
                // wait new buffer append to queue
                // sleep for timeout ms
                long start = System.currentTimeMillis();
                try {
                    synchronized (inBufferQueue) {
                        inBufferQueue.wait(readTimeout);
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
            outBufferQueue.offer(buf);
        }
    }

    public void flush() throws IOException {
        if (outStream != null) {
            outStream.flush();
        }
    }

    public boolean waitInData(int time) {
        if (available.get() == 0) {
            long start = System.currentTimeMillis();
            try {
                synchronized (inBufferQueue) {
                    inBufferQueue.wait(time);
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

    public int getTimeout() {
        return readTimeout;
    }

    public void setTimeout(int timeout) {
        this.readTimeout = timeout;
    }

    public ByteBufferInputStream getInputStream() {
        return inStream;
    }

    public ByteBufferOutputStream getOutputStream() {
        return outStream;
    }
    
    public void addListener(ConnectionListener listener) {
        if (!this.listeners.contains(listener)) {
            this.listeners.add(listener);
        }
    }

    public boolean removeListener(ConnectionListener listener) {
        return this.listeners.remove(listener);
    }
    
}
