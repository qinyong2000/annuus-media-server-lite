package com.ams.io.network;

import java.nio.ByteBuffer;

public interface ConnectionListener {
    public void onConnectionEstablished(Connection conn);
    public void onConnectionDataReceived(Connection conn, ByteBuffer buffers[]);
    public void onConnectionClosed(Connection conn);
    public void onConnectionError(Connection conn, int error);
}
