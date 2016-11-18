package com.ams.server.service.rtmp.replication;

import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ams.io.network.Connection;
import com.ams.protocol.rtmp.RtmpConnection;
import com.ams.protocol.rtmp.RtmpException;
import com.ams.protocol.rtmp.amf.AmfValue;
import com.ams.protocol.rtmp.message.RtmpMessage;
import com.ams.protocol.rtmp.message.RtmpMessageCommand;
import com.ams.protocol.rtmp.net.NetConnection;
import com.ams.protocol.rtmp.net.NetStream;
import com.ams.protocol.rtmp.net.PublisherManager;
import com.ams.protocol.rtmp.net.StreamPublisher;

public class ReplMasterHandler implements Runnable {
    final private Logger logger = LoggerFactory.getLogger(ReplMasterHandler.class);

    private Connection connection;
    private RtmpConnection rtmp;
    private NetConnection netConn;
    private HashMap<String, ReplStreamSubscriber> streamSubscribers = new HashMap<String, ReplStreamSubscriber>();
    private Future<?> future;
    
    public ReplMasterHandler(Connection connection) {
        this.connection =connection;
        this.rtmp = new RtmpConnection(connection);
        this.netConn = new NetConnection(rtmp, null);
    }

    public void run() {
        if (connection.isClosed()) {
            if (future != null) {
                future.cancel(true);
                logger.debug("rtmp repl master handle cancelled");
            }
            return;
         }

        try {
            receive();
            send();
            connection.flush();
        } catch (Exception e) {
            logger.debug(e.getMessage());
            connection.close();
        }
    }

    private void receive() throws IOException, RtmpException {
        if (!rtmp.readRtmpMessage())
            return;
        RtmpMessage message = rtmp.getCurrentMessage();

        switch (message.getType()) {
        case RtmpMessage.MESSAGE_AMF0_COMMAND:
            RtmpMessageCommand command = (RtmpMessageCommand) message;
            if ("subscribe".equals(command.getName())) {
                AmfValue[] args = command.getArgs();
                for (int i = 0; i < args.length; i++) {
                    String publishName = args[i].string();
                    logger.debug("received subscribe request from slave: {}", publishName);
                    if (streamSubscribers.containsKey(publishName)) {
                        logger.debug("alreay in subscribing: {}", publishName);
                        return;
                    }
                    StreamPublisher publisher = (StreamPublisher)PublisherManager.getInstance().getPublisher(publishName);
                    if (publisher != null) {
                        NetStream stream = netConn.createStream();
                        ReplStreamSubscriber subscriber = new ReplStreamSubscriber(publisher, stream);
                        publisher.addSubscriber(subscriber);
                        streamSubscribers.put(publishName, subscriber);
                        logger.debug("start publish to slave: {}");
                    } else {
                        logger.debug("not found publish name for subscibe request: {}", publishName);
                    }
                }
            }
        }
        
    }
    
    private void send() {
        try {
            // try to close stream
            for (String publishName : streamSubscribers.keySet()) {
                ReplStreamSubscriber subscriber = streamSubscribers.get(publishName);
                if (PublisherManager.getInstance().getPublisher(publishName) == null) {
                    subscriber.sendCloseStreamCommand();
                    streamSubscribers.remove(publishName);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.debug(e.getMessage());
        }
    }    

    public void setFuture(Future<?> future) {
        this.future = future;
    }


}