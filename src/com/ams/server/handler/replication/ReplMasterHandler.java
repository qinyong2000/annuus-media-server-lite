package com.ams.server.handler.replication;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ams.io.network.Connection;
import com.ams.protocol.rtmp.RtmpConnection;
import com.ams.protocol.rtmp.RtmpHandShake;
import com.ams.server.handler.IProtocolHandler;

public class ReplMasterHandler implements IProtocolHandler {
    final private Logger logger = LoggerFactory.getLogger(ReplMasterHandler.class);

    private Connection connection;
    private RtmpConnection rtmp;
    private RtmpHandShake handshake;
    private ReplSender sender;

    public ReplMasterHandler(Connection connection) {
        this.connection =connection;
        this.rtmp = new RtmpConnection(connection);
        this.sender = new ReplSender(rtmp);
        this.handshake = new RtmpHandShake(rtmp);
    }

    public void run() {
        try {
            // wait until server handshake done
            if (!handshake.isHandshakeDone()) {
                handshake.doServerHandshake();
                // write to socket channel
                connection.flush();
                return;
            }

            sender.send();
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

}