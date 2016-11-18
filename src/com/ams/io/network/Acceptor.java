package com.ams.io.network;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.*;
import java.util.ArrayList;
import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ams.server.service.IProtocolService;

public class Acceptor extends NetworkHandler {
    private final Logger logger = LoggerFactory.getLogger(Acceptor.class);

    private SocketAddress listenAddress;
    private SocketProperties socketProperties = null;
    private ServerSocketChannel serverChannel;
    private Selector selector;
    private int dispatcherSize;
    private ArrayList<Dispatcher> dispatchers = new ArrayList<Dispatcher>();
    private int nextDispatcher = 0;
    private IProtocolService protocolService;
    
    public Acceptor(SocketAddress host, int dispatcherSize, IProtocolService protocolService) {
        super("acceptor:" + host.toString());
        this.listenAddress = host;
        this.dispatcherSize = dispatcherSize;
        this.protocolService = protocolService;
    }

    private void openChannel() throws IOException {
        serverChannel = ServerSocketChannel.open();
        serverChannel.socket().bind(listenAddress);
        serverChannel.configureBlocking(false);
        selector = Selector.open();
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
    }

    private void closeChannel() {
        try {
            serverChannel.close();
            selector.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public void run() {
        while (isRunning()) {
            int selectedKeys = 0;
            try {
                selectedKeys = selector.select();
            } catch (Exception e) {
                if (selector.isOpen()) {
                    continue;
                } else {
                    try {
                        selector.selectNow();
                        selector = Selector.open();
                        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
                    } catch (Exception e1) {
                    }
                }
            }
            if (selectedKeys == 0) {
                continue;
            }

            Iterator<SelectionKey> it = selector.selectedKeys().iterator();
            while (it.hasNext()) {
                SelectionKey key = it.next();
                it.remove();

                if (!key.isValid()) {
                    continue;
                }

                try {
                    ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
                    SocketChannel channel = serverChannel.accept();
                    channel.configureBlocking(false);

                    if (socketProperties != null) {
                        socketProperties.setSocketProperties(channel.socket());
                    }

                    if (dispatchers != null) {
                        Dispatcher dispatcher = dispatchers.get(nextDispatcher++);
                        // create connection
                        NetworkConnection connection = new NetworkConnection();
                        connection.channel = channel;
                        connection.interestOps = SelectionKey.OP_READ | SelectionKey.OP_WRITE;
                        dispatcher.addConnectionToRegister(connection);
                        logger.debug("accept connection: {}", connection);
                        if (nextDispatcher >= dispatchers.size()) {
                            nextDispatcher = 0;
                        }
                    }
                } catch (Exception e) {
                    key.cancel();
                }
            }
        }
    }

    public void setSocketProperties(SocketProperties socketProperties) {
        this.socketProperties = socketProperties;
    }
    
    public SocketAddress getListenAddress() {
        return this.listenAddress;
    }

    public void start() {
        try {
            for (int i = 0; i < dispatcherSize; i++) {
                Dispatcher dispatcher = new Dispatcher(protocolService);
                dispatchers.add(dispatcher);
                dispatcher.start();
            }
            openChannel();
            super.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void shutdown() {
        for (int i = 0; i < dispatchers.size(); i++) {
            dispatchers.get(i).shutdown();
        }
        protocolService.shutdown();
        closeChannel();
        super.shutdown();
    }

}
