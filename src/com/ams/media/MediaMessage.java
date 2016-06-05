package com.ams.media;

import com.ams.io.buffer.DataBuffer;
import com.ams.protocol.rtmp.message.RtmpMessage;
import com.ams.protocol.rtmp.message.RtmpMessageAudio;
import com.ams.protocol.rtmp.message.RtmpMessageData;
import com.ams.protocol.rtmp.message.RtmpMessageVideo;

public class MediaMessage {
    public static final int MEDIA_AUDIO = 0;
    public static final int MEDIA_VIDEO = 1;
    public static final int MEDIA_META = 2;

    protected int mediaType;
    protected long timestamp = 0;
    protected DataBuffer data;

    public MediaMessage(int mediaType, long timestamp, DataBuffer data) {
        this.mediaType = mediaType;
        this.timestamp = timestamp;
        this.data = data;
    }

    public int getMediaType() {
        return mediaType;
    }
    
    public long getTimestamp() {
        return timestamp;
    }

    public DataBuffer getData() {
        return data.duplicate();
    }

    public int getDataSize() {
        return data.remaining();
    }
    
    public boolean isAudio() {
        return mediaType == MEDIA_AUDIO;
    }

    public boolean isVideo() {
        return mediaType == MEDIA_VIDEO;
    }

    public boolean isMeta() {
        return mediaType == MEDIA_META;
    }
    
    public boolean isVideoKeyframe() {
        if (mediaType != MEDIA_VIDEO)
            return false;
        int h = data.get(0) & 0xFF;
        return isVideo() && ((h >>> 4) == 1 || h == 0x17);
    }

    public boolean isH264Video() {
        if (mediaType != MEDIA_VIDEO)
            return false;
        int h = data.get(0) & 0xFF;
        return h == 0x17 || h == 0x27;
    }

    public boolean isH264Audio() {
        if (mediaType != MEDIA_AUDIO)
            return false;
        int h = data.get(0) & 0xFF;
        return h == 0xAF;
    }

    public boolean isH264AudioHeader() {
        if (mediaType != MEDIA_AUDIO)
            return false;
        int h1 = data.get(0) & 0xFF;
        int h2 = data.get(1) & 0xFF;
        return h1 == 0xAF && h2 == 0x00;
    }

    public boolean isH264VideoHeader() {
        if (mediaType != MEDIA_VIDEO)
            return false;
        int h1 = data.get(0) & 0xFF;
        int h2 = data.get(1) & 0xFF;
        return h1 == 0x17 && h2 == 0x00;
    }

    
    public RtmpMessage toRtmpMessage() {
        RtmpMessage msg = null;
        switch (mediaType) {
        case MEDIA_META:
            msg = new RtmpMessageData(getData());
            break;
        case MEDIA_VIDEO:
            msg = new RtmpMessageVideo(getData());
            break;
        case MEDIA_AUDIO:
            msg = new RtmpMessageAudio(getData());
            break;
        }
        return msg;
    }
    
}
