package com.ams.protocol.rtmp;

public class RtmpHeader {
    private int chunkStreamId;
    private long timestamp;
    private int size;
    private int type;
    private int streamId;

    public RtmpHeader(int chunkStreamId, long timestamp, int size, int type,
            int streamId) {
        this.chunkStreamId = chunkStreamId;
        this.timestamp = timestamp;
        this.size = size;
        this.type = type;
        this.streamId = streamId;
    }

    public int getChunkStreamId() {
        return chunkStreamId;
    }

    protected void setChunkStreamId(int chunkStreamId) {
        this.chunkStreamId = chunkStreamId;
    }

    public int getType() {
        return type;
    }

    protected void setType(int type) {
        this.type = type;
    }

    public int getSize() {
        return size;
    }

    protected void setSize(int size) {
        this.size = size;
    }

    public int getStreamId() {
        return streamId;
    }

    protected void setStreamId(int streamId) {
        this.streamId = streamId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    protected void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String toString() {
        return chunkStreamId + ":" + timestamp + ":" + size + ":" + type + ":"
                + streamId;
    }
}