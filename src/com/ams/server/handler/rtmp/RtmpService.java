package com.ams.server.handler.rtmp;

import java.io.IOException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ams.io.network.Connection;
import com.ams.protocol.rtmp.RtmpConnection;
import com.ams.protocol.rtmp.RtmpException;
import com.ams.protocol.rtmp.net.NetConnection;
import com.ams.protocol.rtmp.net.NetContext;
import com.ams.server.handler.IProtocolService;

public class RtmpService implements IProtocolService {
    private final Logger logger = LoggerFactory.getLogger(RtmpService.class);
  
    private NetContext context;
    private ScheduledThreadPoolExecutor executor;

    public RtmpService(String contextRoot, int poolSize) {
        this.context = new NetContext(contextRoot);
        this.executor = new ScheduledThreadPoolExecutor(poolSize);
   }
    
	@Override
    public void invoke(final Connection connection) {
        logger.debug("invoke a rtmp connection service");
        final RtmpConnection rtmp = new RtmpConnection(connection);
        final NetConnection netConnection = new NetConnection(rtmp, context);
        final Future<?> futures[] = new Future<?>[1];
        Runnable handler = new Runnable() {
            @Override
            public void run() {
                if (connection.isClosed()) {
                   if (futures[0] != null) {
                       futures[0].cancel(true);
                       logger.debug("rtmp handle cancelled");
                   }
                   return;
                }
                try {
                    // read & process rtmp message
                    netConnection.readAndProcessRtmpMessage();
                    // write client video/audio streams
                    netConnection.playStreams();
                    connection.flush();
                } catch (IOException e) {
                    logger.debug(e.getMessage());
                    netConnection.close();
                    connection.close();
                } catch (RtmpException e) {
                    logger.debug(e.getMessage());
                }
                
            }
        };
        futures[0] = executor.scheduleWithFixedDelay(handler, 0, 100, TimeUnit.MICROSECONDS);
    }

    @Override
    public void shutdown() {
        executor.shutdown();
    }

}
