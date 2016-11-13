package com.ams.server.handler.rtmp.replication;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ams.io.network.ClientNetworkConnection;
import com.ams.media.IMsgPublisher;
import com.ams.protocol.rtmp.RtmpConnection;
import com.ams.protocol.rtmp.RtmpException;
import com.ams.protocol.rtmp.RtmpHeader;
import com.ams.protocol.rtmp.amf.AmfValue;
import com.ams.protocol.rtmp.message.RtmpMessage;
import com.ams.protocol.rtmp.message.RtmpMessageCommand;
import com.ams.protocol.rtmp.net.PublisherManager;

public class ReplSlaveHandler implements Runnable {
    private final Logger logger = LoggerFactory.getLogger(ReplSlaveHandler.class);

    private ClientNetworkConnection connection;
    private RtmpConnection rtmp;
    private HashMap<Integer, String> publishingStreams = new HashMap<Integer, String>();
    private TreeSet<String> subscribingRequest = new TreeSet<String>();
    private static long SUBSCRIBE_COMMAND_INTERVAL = 1000;
    private long keepaliveTime;
    
    public ReplSlaveHandler(String host, int port) {
        this.connection = new ClientNetworkConnection(host, port);
        this.rtmp = new RtmpConnection(connection);
    }

    private boolean connectMaster() {
        try {
            InetSocketAddress remote = connection.getRemoteAddress();
            logger.info("connect to master {}:{} ...", remote.getHostString(), remote.getPort());
            connection.connect();
            logger.info("connected");
        } catch (IOException e) {
            logger.info("connect master error");
            return false;
        }
        return true;
    }

    public void run() {
        if (connection.isClosed()) {
            if (!connectMaster()) {
                logger.info("connect master error, retry.");
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                }
                return;
            }
        }

        try {
            receive();
            send();
            connection.flush();
        } catch (Exception e) {
            e.printStackTrace();
            logger.debug(e.getMessage());
            connection.close();
        }
    }

    private void receive() throws IOException, RtmpException {
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
                publishingStreams.put(streamId, publishName);
                if (PublisherManager.getInstance().getPublisher(publishName) == null) {
                    PublisherManager.getInstance().addPublisher(new ReplStreamPublisher(publishName));
                }
                // remove subscribe request
                subscribingRequest.remove(publishName);
                logger.debug("received publish: {}", publishName);
            } else if ("closeStream".equals(command.getName())) {
                int streamId = header.getStreamId();
                String publishName = publishingStreams.get(streamId);
                if (publishName == null)
                    break;
                publishingStreams.remove(streamId);
                IMsgPublisher publisher = PublisherManager.getInstance().getPublisher(publishName);
                if (publisher != null) {
                    publisher.close();
                    PublisherManager.getInstance().removePublisher(publishName);
                    logger.debug("received close stream: {}", publishName);
                }
            }

            break;
        case RtmpMessage.MESSAGE_AUDIO:
        case RtmpMessage.MESSAGE_VIDEO:
        case RtmpMessage.MESSAGE_AMF0_DATA:
            int streamId = header.getStreamId();
            String publishName = publishingStreams.get(streamId);
            if (publishName == null)
                break;
            IMsgPublisher publisher = PublisherManager.getInstance().getPublisher(publishName);
            if (publisher != null) {
                publisher.publish(message.toMediaMessage(header.getTimestamp()));
            }
            break;
        }
    }
    
    private void send() {
        if (!isTimeout()) return;
        try {
            String[] subscibes = new String[subscribingRequest.size()];
            subscribingRequest.toArray(subscibes);
            AmfValue[] args = new AmfValue[subscibes.length + 1];
            args[0] = new AmfValue(null);
            for (int i = 1; i < args.length; i++) {
                args[i] = new AmfValue(subscibes[i - 1]);
            }
            RtmpMessage message = new RtmpMessageCommand("subscribe", 0, args);
            rtmp.writeRtmpMessage(0, 0, message);
        } catch (IOException e) {
        }
        
    }
    
    private boolean isTimeout() {
        long now = System.currentTimeMillis();
        if (now - keepaliveTime > SUBSCRIBE_COMMAND_INTERVAL) {
            keepaliveTime = now;
            return true;
        }
        return false;
    }
    
    public void addSubscription(String name) {
        logger.info("subscribe stream: {} from master", name);
        subscribingRequest.add(name);
    }
    
}
