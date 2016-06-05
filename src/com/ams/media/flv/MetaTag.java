package com.ams.media.flv;

import java.io.DataInputStream;
import java.io.IOException;

import com.ams.io.buffer.DataBuffer;
import com.ams.io.ByteBufferInputStream;
import com.ams.media.MediaSample;
import com.ams.protocol.rtmp.amf.Amf0Deserializer;
import com.ams.protocol.rtmp.amf.AmfException;
import com.ams.protocol.rtmp.amf.AmfValue;

public class MetaTag extends MediaSample {
    private String event = null;
    private AmfValue metaData = null;

    public MetaTag(long timestamp, DataBuffer data) {
        super(MediaSample.MEDIA_META, timestamp, data);
    }

    public MetaTag(long timestamp, long offset, int size) {
        super(MediaSample.MEDIA_META, timestamp, false, offset, size);
    }

    public void getParameters() throws IOException {
        ByteBufferInputStream bi = new ByteBufferInputStream(getData());
        Amf0Deserializer amf0 = new Amf0Deserializer(new DataInputStream(bi));
        AmfValue value;
        try {
            value = amf0.read();
            event = value.string();
            metaData = amf0.read();
        } catch (AmfException e) {
            e.printStackTrace();
        }
    }

    public String getEvent() {
        return event;
    }

    public AmfValue getMetaData() {
        return metaData;
    }
}
