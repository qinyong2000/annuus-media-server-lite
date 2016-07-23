package com.ams.io.network;

public interface ConnectionListener {
    public void onConnectionEstablished(Connection conn);
    public void onConnectionClosed(Connection conn);
    public void onConnectionError(Connection conn, int error);
}
