package com.ams.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ams.io.buffer.ByteBufferAllocator;
import com.ams.io.buffer.ByteBufferFactory;
import com.ams.io.network.Acceptor;
import com.ams.server.handler.IProtocolHandler;
import com.ams.server.handler.IProtocolService;
import com.ams.server.handler.ProtocolHandlerExecutor;
import com.ams.server.handler.http.HttpService;
import com.ams.server.handler.replication.ReplMasterService;
import com.ams.server.handler.replication.ReplSlaveService;
import com.ams.server.handler.rtmp.RtmpService;

public class Server {
    private Logger logger = LoggerFactory.getLogger(Server.class);

    private Configuration config;
    private ArrayList<Acceptor> acceptors;
    private IProtocolHandler replicationSlaveHandler;
    private ProtocolHandlerExecutor executor;

    public Server(Configuration config) throws IOException {
        this.config = config;
        this.acceptors = new ArrayList<Acceptor>();
        int poolSize = config.getWokerThreadPoolSize();
        executor = new ProtocolHandlerExecutor(poolSize);
        initByteBufferFactory(config);
        
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                shutdown();
            }
        });
    }

    private void initByteBufferFactory(Configuration config) {
        ByteBufferAllocator allocator = new ByteBufferAllocator();
        if (config.getMemPoolSize() > 0) {
            allocator.setPoolSize(config.getMemPoolSize());
        }
        allocator.init();
        ByteBufferFactory.setAllocator(allocator);
    }

    private void addTcpListenEndpoint(SocketAddress endpoint,
            IProtocolService processor) throws IOException {
        int dispatcherSize = config.getDispatcherThreadPoolSize();
        Acceptor acceptor = new Acceptor(endpoint, dispatcherSize, processor);
        acceptor.setSocketProperties(config.getSocketProperties());
        acceptors.add(acceptor);
    }

    private void createService() {
        // http service
        try {
            if (config.getHttpHost() != null) {
                SocketAddress httpEndpoint = new InetSocketAddress(
                        config.getHttpHost(), config.getHttpPort());
                addTcpListenEndpoint(httpEndpoint,
                        new HttpService(config.getHttpContextRoot(), executor));
            }
        } catch (Exception e) {
            logger.info("Creating http service failed.");
        }

        // rtmp service
        try {
            if (config.getRtmpHost() != null) {
                SocketAddress rtmpEndpoint = new InetSocketAddress(config.getRtmpHost(), config.getRtmpPort());
                addTcpListenEndpoint(rtmpEndpoint,
                        new RtmpService(config.getRtmpContextRoot(), executor));
            }
        } catch (Exception e) {
            logger.info("Creating rtmp service failed.");
        }

    }

    private void createReplicationService() {
        // unicast replication service
        if (config.getReplicationHost() != null) {
            try {
                InetSocketAddress replicationEndpoint = new InetSocketAddress(config.getReplicationHost(), config.getReplicationPort());
                addTcpListenEndpoint(replicationEndpoint, new ReplMasterService(executor));
            } catch (Exception e) {
                logger.info("Creating unicast replication service failed.");
            }
        }

        if (config.getReplicationMasterHost() != null) {
            ReplSlaveService.getInstance().invoke(config.getReplicationMasterHost(), config.getReplicationMasterPort());
        }
    }

    private void startupService() {
        for (Acceptor acceptor : acceptors) {
            acceptor.start();
            logger.info("Start service on port: {}", acceptor.getListenAddress());
        }
    }

    public void startup() {
        createService();
        createReplicationService();
        startupService();
        logger.info("Server is started.");
    }

    public void shutdown() {
        if (replicationSlaveHandler != null) {
            replicationSlaveHandler.close();
        }
        for (Acceptor acceptor : acceptors) {
            acceptor.stop();
            SocketAddress endpoint = acceptor.getListenAddress();
            logger.info("Shutdown service on port: {}", endpoint);
            System.out.println("Shutdown service on port: " + endpoint);
        }
        executor.shutdown();
        logger.info("Server is shutdown.");
        System.out.println("Server is shutdown.");
    }

}