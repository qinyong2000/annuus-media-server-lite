package com.ams.protocol.rtmp.message;

import com.ams.io.buffer.DataBuffer;

public class RtmpMessageData extends RtmpMessage {
    private DataBuffer data;

    public RtmpMessageData(DataBuffer data) {
        super(MESSAGE_AMF0_DATA);
        this.data = data;
    }

    public DataBuffer getData() {
        return data;
    }
}
