package com.ams.protocol.rtmp;

import java.io.IOException;
import com.ams.io.buffer.DataBuffer;
import com.ams.io.ByteBufferInputStream;

class RtmpChunkData {
    private DataBuffer data = new DataBuffer();
    private int length;
    private int remainBytes;

    public RtmpChunkData(RtmpHeader header) {
        this.length = header.getSize();
        this.remainBytes = length;
    }

    public void readChunk(ByteBufferInputStream in, int chunkSize) throws IOException {
        if (chunkSize <= 0) return;
        data.write(in.readByteBuffer(chunkSize));
        remainBytes -= chunkSize;
    }

    public DataBuffer getData() {
        return data;
    }

    public int getLength() {
        return length;
    }
    
    public int getRemainBytes() {
        return remainBytes;
    }

}
