package com.ams.server.handler.rtmp.replication;

import java.io.IOException;

import com.ams.media.MediaMessage;
import com.ams.protocol.rtmp.amf.AmfValue;
import com.ams.protocol.rtmp.message.RtmpMessage;
import com.ams.protocol.rtmp.message.RtmpMessageCommand;
import com.ams.protocol.rtmp.net.NetStream;
import com.ams.protocol.rtmp.net.StreamPublisher;
import com.ams.protocol.rtmp.net.StreamSubscriber;

class ReplStreamSubscriber extends StreamSubscriber {
    private static long PUBLISH_COMMAND_INTERVAL = 3000;
    private long keepaliveTime;
    private boolean publishCommandTimer = false;

    public ReplStreamSubscriber(StreamPublisher publisher, NetStream stream) {
        super(publisher, stream);
        keepaliveTime = System.currentTimeMillis();
    }

    public void messageNotify(MediaMessage msg) {
        super.messageNotify(msg);

        if (publishCommandTimer && isTimeout()) {
            sendPublishCommand();
            sendMediaHeader();
        }
    }

    protected void sendPublishCommand() {
        try {
            RtmpMessage message = new RtmpMessageCommand("publish", 1, AmfValue.array(null, publisher.getPublishName(), "live"));
            stream.writeMessage(message);
        } catch (IOException e) {
        }
    }

    protected void sendCloseStreamCommand() throws IOException {
        RtmpMessage message = new RtmpMessageCommand("closeStream", 0, null);
        stream.writeMessage(message);
    }

    protected void setPublishCommandTimer() {
        this.publishCommandTimer = true;
    }

    private boolean isTimeout() {
        long now = System.currentTimeMillis();
        if (now - keepaliveTime > PUBLISH_COMMAND_INTERVAL) {
            keepaliveTime = now;
            return true;
        }
        return false;
    }
}
