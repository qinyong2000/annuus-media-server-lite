package com.ams.server;

import com.ams.io.network.SocketProperties;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

public final class Configuration {
    private SocketProperties socketProperties = new SocketProperties();
    private int memPoolSize = 0;
    private int dispatcherThreadPoolSize = 8;
    private int workerThreadPoolSize = 16;

    private String httpHost = null;
    private int httpPort = 80;
    private String httpContextRoot = "www";
    private String rtmpHost = null;
    private int rtmpPort = 1935;
    private int rtmptPort = 80;

    private String rtmpContextRoot = "video";
    private String replicationHost = null;
    private int replicationPort = 1936;
    private String replicationMasterHost = null;
    private int replicationMasterPort = 1936;
    private String multicastGroupHost = null;
    private int multicastGroupPort = 5000;

    public boolean read() throws FileNotFoundException {
        boolean result = true;
        Properties prop = new Properties();
        try {
            prop.load(new FileInputStream("server.conf"));
            String dispatchersProp = prop.getProperty("dispatchers");
            if (dispatchersProp != null) {
                dispatcherThreadPoolSize = Integer.parseInt(dispatchersProp);
            }
            String workersProp = prop.getProperty("workers");
            if (workersProp != null) {
                workerThreadPoolSize = Integer.parseInt(workersProp);
            }
            String poolProp = prop.getProperty("mempool");
            if (poolProp != null) {
                memPoolSize = Integer.parseInt(poolProp) * 1024 * 1024;
            }
            String hostProp = prop.getProperty("http.host");
            if (hostProp != null) {
                httpHost = hostProp;
            }
            String portProp = prop.getProperty("http.port");
            if (portProp != null) {
                httpPort = Integer.parseInt(portProp);
            }
            String root = prop.getProperty("http.root");
            if (root != null) {
                httpContextRoot = root;
            }

            hostProp = prop.getProperty("rtmp.host");
            if (hostProp != null) {
                rtmpHost = hostProp;
            }
            portProp = prop.getProperty("rtmp.port");
            if (portProp != null) {
                rtmpPort = Integer.parseInt(portProp);
            }

            portProp = prop.getProperty("rtmp.tunnel");
            if (portProp != null) {
                rtmptPort = Integer.parseInt(portProp);
            }

            root = prop.getProperty("rtmp.root");
            if (root != null) {
                rtmpContextRoot = root;
            }

            hostProp = prop.getProperty("repl.ucast.host");
            if (hostProp != null) {
                replicationHost = hostProp;
            }

            portProp = prop.getProperty("repl.ucast.port");
            if (portProp != null) {
                replicationPort = Integer.parseInt(portProp);
            }

            hostProp = prop.getProperty("repl.ucast.master.host");
            if (hostProp != null) {
                replicationMasterHost = hostProp;
            }
            portProp = prop.getProperty("repl.ucast.master.port");
            if (portProp != null) {
                replicationMasterPort = Integer.parseInt(portProp);
            }

            hostProp = prop.getProperty("repl.mcast.group.host");
            if (hostProp != null) {
                multicastGroupHost = hostProp;
            }
            portProp = prop.getProperty("repl.mcast.group.port");
            if (portProp != null) {
                multicastGroupPort = Integer.parseInt(portProp);
            }

        } catch (FileNotFoundException e) {
            throw e;
        } catch (IOException e) {
            result = false;
        }

        return result;
    }

    public int getMemPoolSize() {
        return memPoolSize;
    }

    public int getDispatcherThreadPoolSize() {
        return dispatcherThreadPoolSize;
    }

    public int getWokerThreadPoolSize() {
        return workerThreadPoolSize;
    }

    public String getHttpHost() {
        return httpHost;
    }

    public int getHttpPort() {
        return httpPort;
    }

    public String getHttpContextRoot() {
        return httpContextRoot;
    }

    public String getRtmpHost() {
        return rtmpHost;
    }

    public int getRtmpPort() {
        return rtmpPort;
    }

    public String getRtmpContextRoot() {
        return rtmpContextRoot;
    }

    public int getRtmptPort() {
        return rtmptPort;
    }

    public SocketProperties getSocketProperties() {
        return socketProperties;
    }

    public String getReplicationHost() {
        return replicationHost;
    }

    public int getReplicationPort() {
        return replicationPort;
    }

    public String getReplicationMasterHost() {
        return replicationMasterHost;
    }

    public int getReplicationMasterPort() {
        return replicationMasterPort;
    }

    public String getMulticastGroupHost() {
        return multicastGroupHost;
    }

    public int getMulticastGroupPort() {
        return multicastGroupPort;
    }

}
