package com.ams.io.buffer;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class DataBuffer implements IByteBufferReader, IByteBufferWriter {
    private LinkedList<ByteBuffer> bufferList;

    public DataBuffer() {
        this.bufferList = new LinkedList<ByteBuffer>();
    }

    public DataBuffer(ByteBuffer[] buffers) {
        if (buffers == null)
            throw new NullPointerException();
        this.bufferList = new LinkedList<ByteBuffer>(Arrays.asList(buffers));
    }

    public DataBuffer(List<ByteBuffer> buffers) {
        if (buffers == null)
            throw new NullPointerException();
        this.bufferList = new LinkedList<ByteBuffer>(buffers);
    }
    
    public boolean hasRemaining() {
        boolean hasRemaining = false;
        for (ByteBuffer buf : bufferList) {
            if (buf.hasRemaining()) {
                hasRemaining = true;
                break;
            }
        }
        return hasRemaining;
    }

    public int remaining() {
        int remaining = 0;
        for (ByteBuffer buf : bufferList) {
            remaining += buf.remaining();
        }
        return remaining;
    }

    public ByteBuffer[] getBuffers() {
        return bufferList.toArray(new ByteBuffer[bufferList.size()]);
    }

    public DataBuffer duplicate() {
        List<ByteBuffer> dupList = new ArrayList<ByteBuffer>();
        for (ByteBuffer buf : bufferList) {
            dupList.add(buf.duplicate());
        }
        return new DataBuffer(dupList);
    }
    
    public byte get(int index) {
        if (index < 0) {
            throw new IndexOutOfBoundsException();
        }
        int remain = 0;
        for (ByteBuffer buf : bufferList) {
            remain += buf.remaining();
            if (index < remain) {
                int pos = buf.position() + index;
                return buf.get(pos); 
            }
            index -= remain;
        }
        throw new IndexOutOfBoundsException();
    }
    
    public void put(byte[] data) {
        if (data == null) return;
        ByteBuffer buf = ByteBufferFactory.allocate(data.length);
        buf.put(data);
        buf.flip();
        bufferList.add(buf);
    }
    
    public ByteBuffer[] read(int size) {
        if (bufferList.isEmpty()) return null;
        List<ByteBuffer> list = new ArrayList<ByteBuffer>();
        int length = size;
        while (length > 0 && !bufferList.isEmpty()) {
            // read a buffer
            ByteBuffer buffer = bufferList.peek();
            int remain = buffer.remaining();
            if (length >= remain) {
                list.add(buffer);
                bufferList.poll();
                length -= remain;
            } else {
                ByteBuffer buf = BufferUtils.trim(buffer, length);
                list.add(buf);
                length = 0;
            }
        }
        return list.toArray(new ByteBuffer[list.size()]);
    }

    public void write(ByteBuffer[] data) {
        if (data == null) return;
        for (ByteBuffer buf : data) {
            bufferList.add(buf);
        }
    }

}
