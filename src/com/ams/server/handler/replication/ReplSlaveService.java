package com.ams.server.handler.replication;

import com.ams.server.handler.ProtocolHandlerExecutor;

public class ReplSlaveService {
    private ProtocolHandlerExecutor executor;
    private ReplSlaveHandler handler = null;
    private static ReplSlaveService instance = null;
    
    private ReplSlaveService() {
        this.executor = new ProtocolHandlerExecutor(1);
    }
    
    public static synchronized ReplSlaveService getInstance() {
        if (instance == null) {
            instance = new ReplSlaveService();
        }
        return instance;
    }
    
    public void invoke(String host, int port) {
        if (handler == null) {
            handler = new ReplSlaveHandler(host, port);
            executor.execute(handler);
        }
    }
    
    public void addSubscription(String name) {
        if (handler != null) {
            handler.addSubscription(name);
        }
    }
}
