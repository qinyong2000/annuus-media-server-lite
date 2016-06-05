package com.ams.protocol.rtmp.message;

import com.ams.io.buffer.DataBuffer;

public class RtmpMessageUnknown extends RtmpMessage {
    private int messageType;
    private DataBuffer data;

    public RtmpMessageUnknown(int type, DataBuffer data) {
        super(MESSAGE_UNKNOWN);
        this.messageType = type;
        this.data = data;
    }

    public int getMessageType() {
        return messageType;
    }

    public DataBuffer getData() {
        return data;
    }

}
