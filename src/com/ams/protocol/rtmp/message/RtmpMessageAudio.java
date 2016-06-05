package com.ams.protocol.rtmp.message;

import com.ams.io.buffer.DataBuffer;

public class RtmpMessageAudio extends RtmpMessage {
    private DataBuffer data;

    public RtmpMessageAudio(DataBuffer data) {
        super(MESSAGE_AUDIO);
        this.data = data;
    }

    public DataBuffer getData() {
        return data;
    }

}
