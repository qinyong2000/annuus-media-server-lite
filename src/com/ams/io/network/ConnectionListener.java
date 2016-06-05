package com.ams.io.network;

public interface ConnectionListener {
    public void connectionEstablished(Connection conn);
    public void connectionClosed(Connection conn);
}
