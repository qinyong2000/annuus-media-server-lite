package com.ams.server.handler.rtmp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ams.io.network.Connection;
import com.ams.protocol.rtmp.RtmpConnection;
import com.ams.protocol.rtmp.net.NetConnection;
import com.ams.protocol.rtmp.net.NetContext;
import com.ams.server.handler.IProtocolHandler;

public class RtmpHandler implements IProtocolHandler {
    final private Logger logger = LoggerFactory.getLogger(RtmpHandler.class);

    private Connection connection;
    private RtmpConnection rtmp;
    private NetConnection netConnection;

    public RtmpHandler(Connection connection, NetContext context) {
        this.connection = connection;
        this.rtmp = new RtmpConnection(connection);
        this.netConnection = new NetConnection(rtmp, context);
    }

    public void run() {
        try {
            // read & process rtmp message
            netConnection.readAndProcessRtmpMessage();

            // write client video/audio streams
            netConnection.playStreams();

            // write to socket channel
            connection.flush();
        } catch (Exception e) {
            logger.debug(e.getMessage());
            close();
        }
    }

    public boolean isKeepAlive() {
        return !connection.isClosed();
    }

	@Override
    public void close() {
        connection.close();
        netConnection.close();
    }

}