package com.ams.server.handler.rtmp;

import java.util.concurrent.Executor;

import com.ams.io.network.Connection;
import com.ams.protocol.rtmp.net.NetContext;
import com.ams.server.handler.IProtocolService;

public class RtmpService implements IProtocolService {
    private NetContext context;
    private Executor executor;

    public RtmpService(String contextRoot, Executor executor) {
        this.context = new NetContext(contextRoot);
        this.executor = executor;
    }
    
	@Override
    public void invoke(Connection connection) {
        RtmpHandler handler = new RtmpHandler(connection, context);
        executor.execute(handler);
    }

}
