package com.ams.server.handler.replication;

import java.io.IOException;
import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ams.io.network.Connection;
import com.ams.media.IMsgPublisher;
import com.ams.protocol.rtmp.RtmpConnection;
import com.ams.protocol.rtmp.RtmpException;
import com.ams.protocol.rtmp.amf.AmfException;
import com.ams.protocol.rtmp.amf.AmfValue;
import com.ams.protocol.rtmp.message.RtmpMessage;
import com.ams.protocol.rtmp.message.RtmpMessageCommand;
import com.ams.protocol.rtmp.net.NetConnection;
import com.ams.protocol.rtmp.net.NetStream;
import com.ams.protocol.rtmp.net.PublisherManager;
import com.ams.protocol.rtmp.net.StreamPublisher;
import com.ams.server.handler.IProtocolHandler;

public class ReplMasterHandler implements IProtocolHandler {
    final private Logger logger = LoggerFactory.getLogger(ReplMasterHandler.class);

    private Connection connection;
    private RtmpConnection rtmp;
    private NetConnection netConn;
    private HashMap<String, ReplStreamSubscriber> streamSubscribers = new HashMap<String, ReplStreamSubscriber>();

    public ReplMasterHandler(Connection connection) {
        this.connection =connection;
        this.rtmp = new RtmpConnection(connection);
        this.netConn = new NetConnection(rtmp, null);
    }

    public void run() {
        try {
            receive();
            send();
            connection.flush();
        } catch (Exception e) {
            logger.debug(e.getMessage());
            close();
        }
    }

    @Override
    public boolean isKeepAlive() {
        return !connection.isClosed();
    }

    @Override
    public void close() {
        connection.close();
    }
    
    private void receive() throws IOException, AmfException, RtmpException {
        if (!rtmp.readRtmpMessage())
            return;
        RtmpMessage message = rtmp.getCurrentMessage();

        switch (message.getType()) {
        case RtmpMessage.MESSAGE_AMF0_COMMAND:
            RtmpMessageCommand command = (RtmpMessageCommand) message;
            if ("subscribe".equals(command.getName())) {
                AmfValue[] args = command.getArgs();
                for (int i = 1; i < args.length; i++) {
                    String publishName = args[i].string();
                    logger.debug("received subscription from slave: {}", publishName);
                    if (streamSubscribers.containsKey(publishName)) {
                        logger.debug("alreay subscribed: {}", publishName);
                        return;
                    }
                    StreamPublisher publisher = (StreamPublisher)PublisherManager.getPublisher(publishName);
                    if (publisher != null) {
                        NetStream stream = netConn.createStream();
                        ReplStreamSubscriber subscriber = new ReplStreamSubscriber(publisher, stream);
                        publisher.addSubscriber(subscriber);
                        streamSubscribers.put(publishName, subscriber);
                        logger.debug("start publish to slave: {}");
                    } else {
                        logger.debug("not found subscription: {}", publishName);
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
                if (PublisherManager.getPublisher(publishName) == null) {
                    subscriber.sendCloseStreamCommand();
                    streamSubscribers.remove(publishName);
                }
            }
            Thread.sleep(10);
        } catch (Exception e) {
            e.printStackTrace();
            logger.debug(e.getMessage());
        }
    }    
}