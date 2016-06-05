package com.ams.server.handler.replication;

import java.io.IOException;
import java.util.concurrent.Executor;

import com.ams.io.network.Connection;
import com.ams.server.handler.IProtocolService;

public class ReplMasterService implements IProtocolService {
    private Executor executor;
    
    public ReplMasterService(Executor executor) throws IOException {
        this.executor = executor;
    }

	@Override
    public void invoke(Connection connection) {
        ReplMasterHandler handler = new ReplMasterHandler(connection);
        executor.execute(handler);
    }

}
