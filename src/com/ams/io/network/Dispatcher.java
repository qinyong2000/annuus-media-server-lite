package com.ams.io.network;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ams.server.handler.IProtocolService;

public class Dispatcher extends NetworkHandler {
    private final Logger logger = LoggerFactory.getLogger(Dispatcher.class);

    private static final long SELECT_TIMEOUT = 2 * 1000;
    private long timeExpire = 2 * 60 * 1000;
    private long lastExpirationTime = 0;
    private Selector selector = null;
    private ConcurrentLinkedQueue<NetworkConnection> registerConnectionQueue = null;
    private IProtocolService protocolService = null;

    public Dispatcher() throws IOException {
        super("dispatcher");
        this.selector = Selector.open();
        this.registerConnectionQueue = new ConcurrentLinkedQueue<NetworkConnection>();
    }

    public Dispatcher(IProtocolService protocolService) throws IOException {
        this();
        this.protocolService = protocolService;
    }
    
    public void addChannelToRegister(NetworkConnection connection) {
        registerConnectionQueue.offer(connection);
        selector.wakeup();
    }

    public void run() {
        while (isRunning()) {
            // register a new channel
            registerNewChannel();

            // do select
            doSelect();

            // collect idle keys that will not be used
            expireIdleKeys();
        }

        closeAllKeys();
    }

    private void registerNewChannel() {
    	NetworkConnection connection = null;
        while ((connection = registerConnectionQueue.poll()) != null) {
            try {
                SelectableChannel channel = connection.getChannel();
                int interestOps = connection.getInterestOps();
                channel.configureBlocking(false);
                channel.register(selector, interestOps, connection);
            } catch (Exception e) {
                logger.debug("register channel error: {}", connection);
            }
        }
    }

    private void doSelect() {
        int selectedKeys = 0;
        try {
            selectedKeys = selector.select(SELECT_TIMEOUT);
        } catch (Exception e) {
            logger.debug("select key error");
            if (selector.isOpen()) {
                return;
            }
        }

        if (selectedKeys == 0) {
            return;
        }

        Iterator<SelectionKey> it = selector.selectedKeys().iterator();
        while (it.hasNext()) {
            SelectionKey key = it.next();
            it.remove();

            if (!key.isValid()) {
                continue;
            }
            NetworkConnection connection = (NetworkConnection) key.attachment();
            try {
                if (key.isConnectable()) {
                    if (connection.finishConnect()) {
                        key.interestOps(SelectionKey.OP_READ);
                        openConnection(connection);
                    }
                }

                if (key.isReadable()) {
                    connection.readChannel();
                    if (connection.isClosed()) {
                        openConnection(connection);
                    }
                    
                }

            } catch (Exception e) {
                logger.debug("read channel error: {}, {}", connection, e.getMessage());
                key.cancel();
                key.attach(null);
                connection.close();
            }
        }
    }
    
    private void openConnection(NetworkConnection connection) {
        connection.open();
        if (protocolService != null) {
          protocolService.invoke(connection);
        }
    }
    
    private void expireIdleKeys() {
        // check every timeExpire
        long now = System.currentTimeMillis();
        long elapsedTime = now - lastExpirationTime;
        if (elapsedTime < timeExpire) {
            return;
        }
        lastExpirationTime = now;

        for (SelectionKey key : selector.keys()) {
            // Keep-alive expired
            NetworkConnection connection = (NetworkConnection) key.attachment();
            if (connection != null) {
                long keepAliveTime = connection.getKeepAliveTime();
                if (now - keepAliveTime > timeExpire) {
                    logger.debug("close expired idle key: {}", connection);
                    key.cancel();
                    key.attach(null);
                    connection.close();
                }
            }
        }
    }

    private void closeAllKeys() {
        // close all keys
        for (SelectionKey key : selector.keys()) {
            // Keep-alive expired
            NetworkConnection connector = (NetworkConnection) key.attachment();
            if (connector != null) {
                key.cancel();
                key.attach(null);
                connector.close();
            }
        }

    }

    public void setTimeExpire(long timeExpire) {
        this.timeExpire = timeExpire;
    }
}
