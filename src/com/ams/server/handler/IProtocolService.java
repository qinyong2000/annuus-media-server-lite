package com.ams.server.handler;

import com.ams.io.network.Connection;

public interface IProtocolService {
    public void invoke(Connection connection);
}
