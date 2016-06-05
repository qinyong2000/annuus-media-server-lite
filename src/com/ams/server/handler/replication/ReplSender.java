package com.ams.server.handler.replication;

import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ams.protocol.rtmp.RtmpConnection;
import com.ams.protocol.rtmp.net.NetConnection;
import com.ams.protocol.rtmp.net.NetStream;
import com.ams.protocol.rtmp.net.PublisherManager;
import com.ams.protocol.rtmp.net.StreamPublisher;
import com.ams.server.handler.rtmp.RtmpHandler;

class ReplSender {
    final private Logger logger = LoggerFactory.getLogger(RtmpHandler.class);
    private NetConnection netConn;
    private HashMap<String, ReplStreamSubscriber> publishingSubscriber = new HashMap<String, ReplStreamSubscriber>();

    public ReplSender(RtmpConnection rtmp) {
        this.netConn = new NetConnection(rtmp, null);
    }

    public void send() {
        try {
            // publish stream
            for (String publishName : PublisherManager.getAllPublishName()) {
                StreamPublisher publisher = (StreamPublisher) PublisherManager
                        .getPublisher(publishName);
                // found a new publisher, if it is not a replication publisher,
                // create a subscriber
                if (publisher instanceof ReplStreamPublisher) {
                    continue;
                }

                if (!publishingSubscriber.containsKey(publishName)) {
                    NetStream stream = netConn.createStream();
                    // subscriber to publisher
                    ReplStreamSubscriber subscriber = new ReplStreamSubscriber(
                            publisher, stream);

                    publisher.addSubscriber(subscriber);

                    publishingSubscriber.put(publishName, subscriber);
                }
            }

            // close stream
            for (String publishName : publishingSubscriber.keySet()) {
                ReplStreamSubscriber subscriber = publishingSubscriber
                        .get(publishName);
                if (PublisherManager.getPublisher(publishName) == null) {
                    subscriber.sendCloseStreamCommand();
                    publishingSubscriber.remove(publishName);
                }
            }

            Thread.sleep(10);
        } catch (Exception e) {
            e.printStackTrace();
            logger.debug(e.getMessage());
        }
    }
}