package com.ams.server.handler.http;

import java.io.IOException;
import java.util.concurrent.Executor;

import com.ams.io.network.Connection;
import com.ams.protocol.http.ServletContext;
import com.ams.server.handler.IProtocolService;

public class HttpService implements IProtocolService {
    private ServletContext context;
    private Executor executor;
    
    public HttpService(String contextRoot, Executor executor) throws IOException {
        this.context = new ServletContext(contextRoot);
        this.executor = executor;
    }

	@Override
    public void invoke(Connection connection) {
        HttpHandler handler = new HttpHandler(connection, context);
        executor.execute(handler);
    }

}
