package com.ams.server.handler.replication;

import java.io.IOException;
import java.net.InetSocketAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ams.io.network.NetworkClientConnection;
import com.ams.protocol.rtmp.RtmpConnection;
import com.ams.protocol.rtmp.RtmpHandShake;
import com.ams.server.handler.IProtocolHandler;

public class ReplSlaveHandler implements IProtocolHandler {
    final private Logger logger = LoggerFactory.getLogger(ReplSlaveHandler.class);
    private static final int DEFAULT_TIMEOUT_MS = 24 * 60 * 60 * 1000;

    private String senderHost = null;
    private int senderPort;
    private NetworkClientConnection connection;
    private RtmpConnection rtmp;
    private RtmpHandShake handshake;
    private ReplReceiver receiver;

    public ReplSlaveHandler(String host, int port) {
        this.senderHost = host;
        this.senderPort = port;
        this.connection = new NetworkClientConnection();
        this.rtmp = new RtmpConnection(connection);
        this.receiver = new ReplReceiver(rtmp);
        this.handshake = new RtmpHandShake(rtmp);
    }

    private boolean connectToSender() {
        logger.info("connect to {}:{} ...", senderHost, senderPort);
        try {
            connection.setReadTimeout(5000);
            connection.connect(new InetSocketAddress(senderHost, senderPort));
            logger.info("connected.");
        } catch (IOException e) {
            logger.info("connect sender error");
            return false;
        }
        connection.setReadTimeout(DEFAULT_TIMEOUT_MS);
        return true;
    }

    private void doClientHandshake() {
        logger.info("do rtmp handshake...");
        while (!handshake.isHandshakeDone()) {
            try {
                handshake.doClientHandshake();
                // write to socket channel
                connection.flush();
            } catch (Exception e) {
                break;
            }
        }
    }

    public void run() {
        if (connection.isClosed()) {
            if (!connectToSender()) {
                logger.info("connect sender error, retry.");
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                }
                return;
            }
            // do client handshake
            doClientHandshake();
            logger.info("rtmp handshake done.");
        }

        try {
            receiver.receive();
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

}