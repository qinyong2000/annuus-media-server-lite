package com.ams.server.handler;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
public class ProtocolHandlerExecutor extends ScheduledThreadPoolExecutor {

    public ProtocolHandlerExecutor(int poolSize) {
        super(poolSize);
    }

    protected void afterExecute(Runnable r, Throwable t) {
        if (r instanceof IProtocolHandler) {
            IProtocolHandler handler = (IProtocolHandler) r;
            if (handler.isKeepAlive()) {
                schedule(handler, 10, TimeUnit.MILLISECONDS);
            }
        }
    }

}
