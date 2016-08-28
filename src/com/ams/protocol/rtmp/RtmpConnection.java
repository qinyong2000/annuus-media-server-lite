package com.ams.protocol.rtmp;

import java.io.IOException;
import java.util.HashMap;

import com.ams.io.ByteBufferInputStream;
import com.ams.io.ByteBufferOutputStream;
import com.ams.io.network.Connection;
import com.ams.protocol.rtmp.message.RtmpMessage;
import com.ams.protocol.rtmp.message.RtmpMessageAudio;
import com.ams.protocol.rtmp.message.RtmpMessageVideo;

public class RtmpConnection {
    private static int CHUNK_STREAM_ID_PROTOCOL_CONTROL = 2;
    private static int CHUNK_STREAM_ID_DEFAULT          = 3;
    private static int CHUNK_STREAM_ID_VIDEO            = 5;
    private static int CHUNK_STREAM_ID_AUDIO            = 6;
	
    private Connection conn;
    private ByteBufferInputStream in;
    private ByteBufferOutputStream out;

    private HashMap<Integer, RtmpHeader> chunkHeaderMap;
    private HashMap<Integer, RtmpChunkData> chunkDataMap;
    
    private RtmpHeaderSerializer headerSerializer;
    private RtmpMessageSerializer messageSerializer;

    private RtmpHeaderDeserializer headerDeserializer;
    private RtmpMessageDeserializer messageDeserializer;

    private RtmpHeader currentHeader = null;
    private RtmpMessage currentMessage = null;

    public RtmpConnection(Connection conn) {
        this.conn = conn;
        this.in = conn.getInputStream();
        this.out = conn.getOutputStream();
        this.headerSerializer = new RtmpHeaderSerializer(out);
        this.chunkHeaderMap = new HashMap<Integer, RtmpHeader>();
        this.headerDeserializer = new RtmpHeaderDeserializer(in, chunkHeaderMap);
        this.messageSerializer = new RtmpMessageSerializer(out,headerSerializer);
        this.chunkDataMap = new HashMap<Integer, RtmpChunkData>();
        this.messageDeserializer = new RtmpMessageDeserializer(in, chunkDataMap);
    }

    public synchronized boolean readRtmpMessage() throws IOException, RtmpException {
        if (conn.readAvailable() == 0) {
            return false;
        }

        if (currentHeader != null && currentMessage != null) {
            // read new chunk
            currentHeader = null;
            currentMessage = null;
        }
        // read header every time, a message maybe break into several chunks
        if (currentHeader == null) {
            currentHeader = headerDeserializer.read();
        }
        if (currentMessage == null) {
            currentMessage = messageDeserializer.read(currentHeader);
            if (currentMessage == null) {
                currentHeader = null;   // read next chunk
            }
        }

        return isRtmpMessageReady();
    }

    public boolean isRtmpMessageReady() {
        return (currentHeader != null && currentMessage != null);
    }

    public synchronized void writeRtmpMessage(int chunkStreamId, int streamId,
            long timestamp, RtmpMessage message) throws IOException {
        if (chunkStreamId > CHUNK_STREAM_ID_PROTOCOL_CONTROL) {
            messageSerializer.write(chunkStreamId, streamId, timestamp, message);
        }
    }

    public synchronized void writeRtmpMessage(int streamId, long timestamp, RtmpMessage message) throws IOException {
        if (message instanceof RtmpMessageVideo) {
            messageSerializer.write(CHUNK_STREAM_ID_VIDEO, streamId, timestamp, message);
        } else if (message instanceof RtmpMessageAudio) {
            messageSerializer.write(CHUNK_STREAM_ID_AUDIO, streamId, timestamp, message);
        } else {
            messageSerializer.write(CHUNK_STREAM_ID_DEFAULT, streamId, timestamp, message);
        }
    }
    
    public synchronized void writeProtocolControlMessage(RtmpMessage message)
            throws IOException {
        messageSerializer.write(CHUNK_STREAM_ID_PROTOCOL_CONTROL, 0, 0, message);
    }

    public Connection getConnection() {
        return conn;
    }

    public RtmpHeader getCurrentHeader() {
        return currentHeader;
    }

    public RtmpMessage getCurrentMessage() {
        return currentMessage;
    }

    public void flush() throws IOException {
        conn.flush();
    }
}