package com.ams.io.network;

import java.nio.ByteBuffer;

public interface NetworkConnectionListener {
    public void onConnectionEstablished(NetworkConnection conn);
    public void onConnectionDataReceived(NetworkConnection conn, ByteBuffer buffers[]);
    public void onConnectionClosed(NetworkConnection conn);
    public void onConnectionError(NetworkConnection conn, int error);
}
