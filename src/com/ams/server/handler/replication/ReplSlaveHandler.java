package com.ams.server.handler.replication;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ams.io.network.NetworkClientConnection;
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
import com.ams.server.handler.IProtocolHandler;

public class ReplSlaveHandler implements IProtocolHandler {
    final private Logger logger = LoggerFactory.getLogger(ReplSlaveHandler.class);
    private static final int DEFAULT_TIMEOUT_MS = 24 * 60 * 60 * 1000;

    private String masterHost = null;
    private int masterPort;
    private NetworkClientConnection connection;
    private RtmpConnection rtmp;
    private HashMap<Integer, String> streams = new HashMap<Integer, String>();

    public ReplSlaveHandler(String host, int port) {
        this.masterHost = host;
        this.masterPort = port;
        this.connection = new NetworkClientConnection();
        this.rtmp = new RtmpConnection(connection);
    }

    private boolean connectToMaster() {
        logger.info("connect to master {}:{} ...", masterHost, masterPort);
        try {
            connection.setTimeout(5000);
            connection.connect(new InetSocketAddress(masterHost, masterPort));
            logger.info("connected.");
        } catch (IOException e) {
            logger.info("connect master error");
            return false;
        }
        connection.setTimeout(DEFAULT_TIMEOUT_MS);
        return true;
    }

    public void run() {
        if (connection.isClosed()) {
            if (!connectToMaster()) {
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
        }
    }

    @Override
    public boolean isKeepAlive() {
        return true;
    }

    @Override
    public void close() {
        connection.close();
    }

    private void receive() throws NetConnectionException, IOException, RtmpException, AmfException {
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
                    PublisherManager.addPublisher(new ReplStreamPublisher(publishName));
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
    private void send() {
        try {
            AmfValue[] args = AmfValue.array(null, publishName);
            RtmpMessage message = new RtmpMessageCommand("subscribe", TANSACTION_ID,
                    args);
            rtmp.writeRtmpMessage(CHANNEL_RTMP_PUBLISH, streamId, 0, message);
        } catch (IOException e) {
        }
        
    }
}