package com.ams.protocol.rtmp.net;

import java.io.IOException;

import com.ams.media.IMsgSubscriber;
import com.ams.media.MediaMessage;

public class StreamSubscriber implements IMsgSubscriber {
    protected StreamPublisher publisher;
    protected NetStream stream;
    private boolean firstKeyframe = false;

    public StreamSubscriber(StreamPublisher publisher, NetStream stream) {
        this.publisher = publisher;
        this.stream = stream;
        sendMediaHeader();
    }

    public void messageNotify(MediaMessage msg) {
        if (msg == null)
            return;

        if (!firstKeyframe) {
            if (!msg.isVideoKeyframe()) {
                return; // drop non-keyframe at head
            }
            firstKeyframe = true;
        }
        // TODO if network is slow, maybe drop some frame
        sendMediaMessage(msg);
    }

    private void sendMediaMessage(MediaMessage msg) {
        if (msg == null)
            return;

        try {
            stream.setTimeStamp(msg.getTimestamp());
            stream.writeMessage(msg.toRtmpMessage());

            // stream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void close() {
        publisher.removeSubscriber(this);
    }

    public void sendMediaHeader() {
        sendMediaMessage(publisher.getVideoHeader());
        sendMediaMessage(publisher.getAudioHeader());
        sendMediaMessage(publisher.getMeta());
    }

}
