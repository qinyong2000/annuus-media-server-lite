package com.ams.media.flv;

import java.io.IOException;

import com.ams.io.buffer.DataBuffer;
import com.ams.io.ByteBufferOutputStream;
import com.ams.io.RandomAccessFileWriter;
import com.ams.media.IMediaSerializer;
import com.ams.media.MediaMessage;
import com.ams.media.MediaSample;

public class FlvSerializer implements IMediaSerializer {
    private ByteBufferOutputStream writer; // record to file stream
    private boolean headerWrite = false;

    public FlvSerializer(RandomAccessFileWriter writer) {
        this.writer = new ByteBufferOutputStream(writer);
    }

    public void write(MediaMessage flvTag) throws IOException {
        write(writer, flvTag);
        writer.flush();
    }

    private void write(ByteBufferOutputStream out, MediaMessage flvTag)
            throws IOException {
        if (!headerWrite) {
            FlvHeader header = new FlvHeader(true, true);
            FlvHeader.write(out, header);
            headerWrite = true;
        }

        byte tagType = -1;
        switch (flvTag.getMediaType()) {
        case MediaSample.MEDIA_AUDIO:
            tagType = 0x08;
            break;
        case MediaSample.MEDIA_VIDEO:
            tagType = 0x09;
            break;
        case MediaSample.MEDIA_META:
            tagType = 0x12;
            break;
        }
        // tag type
        out.writeByte(tagType);

        DataBuffer data = flvTag.getData();
        // data size
        int dataSize = data.remaining();

        out.write24Bit(dataSize); // 24Bit write
        // time stamp
        int timestamp = (int) flvTag.getTimestamp();
        out.write32Bit(timestamp); // 32Bit write
        out.writeByte((byte) ((timestamp & 0xFF000000) >>> 24));
        // stream ID
        out.write24Bit(0);
        // data
        out.writeByteBuffer(data);
        // previousTagSize
        out.write32Bit(dataSize + 11);
    }

    public void close() {
        try {
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
