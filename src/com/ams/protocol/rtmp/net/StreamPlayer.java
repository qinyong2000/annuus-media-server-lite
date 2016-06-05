package com.ams.protocol.rtmp.net;

import java.io.IOException;

import com.ams.io.buffer.DataBuffer;
import com.ams.media.IMediaDeserializer;
import com.ams.media.MediaMessage;
import com.ams.protocol.rtmp.amf.AmfValue;
import com.ams.protocol.rtmp.message.*;

public class StreamPlayer {
    private static int BUFFER_TIME = 3 * 1000; // x seconds of buffering
    private NetStream stream = null;
    private IMediaDeserializer deserializer;
    private long startTime = -1;
    private long bufferTime = BUFFER_TIME;
    private boolean pause = false;
    private boolean audioPlaying = true;
    private boolean videoPlaying = true;

    public StreamPlayer(IMediaDeserializer deserializer, NetStream stream)
            throws IOException {
        this.deserializer = deserializer;
        this.stream = stream;
    }

    public void close() {
        deserializer.close();
    }

    private void writeStartData() throws IOException {
        // |RtmpSampleAccess
        stream.writeDataMessage(AmfValue.array("|RtmpSampleAccess", false,
                false));

        // NetStream.Data.Start
        stream.writeDataMessage(AmfValue.array("onStatus", AmfValue.newObject()
                .put("code", "NetStream.Data.Start")));

        MediaMessage metaData = deserializer.metaData();
        if (metaData != null) {
            stream.writeMessage(metaData.toRtmpMessage());
        }

        MediaMessage videoHeaderData = deserializer.videoHeaderData();
        if (videoHeaderData != null) {
            stream.writeMessage(videoHeaderData.toRtmpMessage());
        }
        MediaMessage audioHeaderData = deserializer.audioHeaderData();
        if (audioHeaderData != null) {
            stream.writeMessage(audioHeaderData.toRtmpMessage());
        }

    }

    public void seek(long seekTime) throws IOException {
        MediaMessage sample = deserializer.seek(seekTime);
        if (sample != null) {
            long currentTime = sample.getTimestamp();
            startTime = System.currentTimeMillis() - bufferTime - currentTime;
            stream.setTimeStamp(currentTime);
        }
        pause(false);
        writeStartData();
    }

    public void play() throws IOException {
        if (pause)
            return;
        long time = System.currentTimeMillis() - startTime;
        while (stream.getTimeStamp() < time) {
            MediaMessage sample = deserializer.readNext();
            if (sample == null) {
                break;
            }
            long timestamp = sample.getTimestamp();
            stream.setTimeStamp(timestamp);
            DataBuffer data = sample.getData();
            if (sample.isAudio() && audioPlaying) {
                stream.writeMessage(new RtmpMessageAudio(data));
            } else if (sample.isVideo() && videoPlaying) {
                stream.writeMessage(new RtmpMessageVideo(data));
            } else if (sample.isMeta()) {
                stream.writeMessage(new RtmpMessageData(data));
            }
        }
    }

    public void pause(boolean pause) {
        this.pause = pause;
    }

    public boolean isPaused() {
        return pause;
    }

    public void audioPlaying(boolean flag) {
        this.audioPlaying = flag;

    }

    public void videoPlaying(boolean flag) {
        this.videoPlaying = flag;
    }

    public void setBufferTime(long bufferTime) {
        this.bufferTime = bufferTime;
    }

}
