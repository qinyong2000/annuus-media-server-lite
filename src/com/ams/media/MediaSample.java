package com.ams.media;

import com.ams.io.buffer.DataBuffer;

public class MediaSample extends MediaMessage{
    protected boolean keyframe;
    protected long offset;
    protected int size;

    public MediaSample(int mediaType, long timestamp, DataBuffer data) {
        super(mediaType, timestamp, data);
    }
    
    public MediaSample(int mediaType, long timestamp, boolean keyframe,
            long offset, int size) {
        super(mediaType, timestamp, null);
        this.keyframe = keyframe;
        this.offset = offset;
        this.size = size;
    }

    public void setData(DataBuffer data) {
        this.data = data;
    }
    
    public long getOffset() {
        return offset;
    }

    public int getSize() {
        return size;
    }

    public boolean isKeyframe() {
        return keyframe;
    }

}
