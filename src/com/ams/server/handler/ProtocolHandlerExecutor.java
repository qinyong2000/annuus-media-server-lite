package com.ams.server.handler;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ProtocolHandlerExecutor extends ThreadPoolExecutor {
    private static final int CORE_POOL_SIZE = 8;
    private static final int QUEUE_SIZE = 256;

    public ProtocolHandlerExecutor(int maxPoolSize) {
        super(CORE_POOL_SIZE, maxPoolSize, 60, TimeUnit.SECONDS,
                new ArrayBlockingQueue<Runnable>(QUEUE_SIZE));
    }

    protected void afterExecute(Runnable r, Throwable t) {
        if (r instanceof IProtocolHandler) {
            IProtocolHandler handler = (IProtocolHandler) r;
            if (handler.isKeepAlive()) {
                execute(handler);
            }
        }
    }

}
