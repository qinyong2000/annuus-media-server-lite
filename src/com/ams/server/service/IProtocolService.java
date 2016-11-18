package com.ams.server.service;

import com.ams.io.network.Connection;

public interface IProtocolService {
    public void invoke(Connection connection);
    public void shutdown();
}
