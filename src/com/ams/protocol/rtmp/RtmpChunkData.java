package com.ams.protocol.rtmp;

import java.io.IOException;
import com.ams.io.buffer.DataBuffer;
import com.ams.io.ByteBufferInputStream;

class RtmpChunkData {
    private RtmpHeader header;
    private DataBuffer chunkData = new DataBuffer();
    private int chunkSize;

    public RtmpChunkData(RtmpHeader header) {
        this.header = header;
        this.chunkSize = header.getSize();
    }

    public void read(ByteBufferInputStream in, int size) throws IOException {
        if (size <= 0) return;
        chunkData.write(in.readByteBuffer(size));
        chunkSize -= size;
    }

    public DataBuffer getChunkData() {
        return chunkData;
    }

    public int getRemainBytes() {
        return chunkSize;
    }

    public RtmpHeader getHeader() {
        return header;
    }
}
