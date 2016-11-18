package com.ams.server.service.rtmp.replication;

import java.io.IOException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.ams.io.network.Connection;
import com.ams.server.service.IProtocolService;

public class ReplMasterService implements IProtocolService {
    private ScheduledThreadPoolExecutor executor;
    
    public ReplMasterService() throws IOException {
        this.executor = new ScheduledThreadPoolExecutor(16);
    }

	@Override
    public void invoke(Connection connection) {
        ReplMasterHandler handler = new ReplMasterHandler(connection);
        Future<?> future = executor.scheduleWithFixedDelay(handler, 0, 100, TimeUnit.MICROSECONDS);
        handler.setFuture(future);
    }

	@Override
    public void shutdown() {
        executor.shutdown();
    }

}
