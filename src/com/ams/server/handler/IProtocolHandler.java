package com.ams.server.handler;

public interface IProtocolHandler extends Runnable {
    public boolean isKeepAlive();
    public void close();
}