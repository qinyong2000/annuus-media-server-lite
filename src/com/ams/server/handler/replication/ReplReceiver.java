package com.ams.server.handler.replication;

import java.io.IOException;
import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ams.media.IMsgPublisher;
import com.ams.protocol.rtmp.RtmpConnection;
import com.ams.protocol.rtmp.RtmpException;
import com.ams.protocol.rtmp.RtmpHeader;
import com.ams.protocol.rtmp.amf.AmfException;
import com.ams.protocol.rtmp.amf.AmfValue;
import com.ams.protocol.rtmp.message.RtmpMessage;
import com.ams.protocol.rtmp.message.RtmpMessageCommand;
import com.ams.protocol.rtmp.net.NetConnectionException;
import com.ams.protocol.rtmp.net.PublisherManager;

class ReplReceiver {
    final private Logger logger = LoggerFactory.getLogger(ReplReceiver.class);

    private RtmpConnection rtmp;
    private HashMap<Integer, String> streams = new HashMap<Integer, String>();

    public ReplReceiver(RtmpConnection rtmp) {
        this.rtmp = rtmp;
    }

    public void receive() throws NetConnectionException, IOException,
            RtmpException, AmfException {
        if (!rtmp.readRtmpMessage())
            return;
        RtmpHeader header = rtmp.getCurrentHeader();
        RtmpMessage message = rtmp.getCurrentMessage();

        switch (message.getType()) {
        case RtmpMessage.MESSAGE_AMF0_COMMAND:
            RtmpMessageCommand command = (RtmpMessageCommand) message;
            if ("publish".equals(command.getName())) {
                int streamId = header.getStreamId();
                AmfValue[] args = command.getArgs();
                String publishName = args[1].string();
                streams.put(streamId, publishName);
                if (PublisherManager.getPublisher(publishName) == null) {
                    PublisherManager.addPublisher(new ReplStreamPublisher(
                            publishName));
                }
                logger.debug("received publish: {}", publishName);
            } else if ("closeStream".equals(command.getName())) {
                int streamId = header.getStreamId();
                String publishName = streams.get(streamId);
                if (publishName == null)
                    break;
                streams.remove(streamId);
                IMsgPublisher publisher = PublisherManager.getPublisher(publishName);
                if (publisher != null) {
	                publisher.close();
	                PublisherManager.removePublisher(publishName);
	                logger.debug("received close stream: {}", publishName);
                }
            }

            break;
        case RtmpMessage.MESSAGE_AUDIO:
        case RtmpMessage.MESSAGE_VIDEO:
        case RtmpMessage.MESSAGE_AMF0_DATA:
            int streamId = header.getStreamId();
            String publishName = streams.get(streamId);
            if (publishName == null)
                break;
            IMsgPublisher publisher = PublisherManager.getPublisher(publishName);
            if (publisher != null) {
                publisher.publish(message.toMediaMessage(header.getTimestamp()));
            }
            break;
        }
    }
}