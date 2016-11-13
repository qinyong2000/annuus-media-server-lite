package com.ams.server.handler.rtmp.replication;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ReplSlaveService {
    private ScheduledThreadPoolExecutor executor;
    private ReplSlaveHandler handler = null;
    private static ReplSlaveService instance = null;
    
    private ReplSlaveService() {
         this.executor = new ScheduledThreadPoolExecutor(1);
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
            executor.scheduleWithFixedDelay(handler, 0, 100, TimeUnit.MICROSECONDS);
        }
    }
    
    public synchronized void addSubscription(String name) {
        if (handler != null) {
            handler.addSubscription(name);
        }
    }
}
