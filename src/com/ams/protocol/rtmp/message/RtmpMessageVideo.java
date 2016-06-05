package com.ams.protocol.rtmp.message;

import com.ams.io.buffer.DataBuffer;

public class RtmpMessageVideo extends RtmpMessage {
    private DataBuffer data;

    public RtmpMessageVideo(DataBuffer data) {
        super(MESSAGE_VIDEO);
        this.data = data;
    }

    public DataBuffer getData() {
        return data;
    }

}
