package com.ams.server.handler.http;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ams.io.network.Connection;
import com.ams.io.network.NetworkConnection;
import com.ams.protocol.http.HttpRequest;
import com.ams.protocol.http.HttpResponse;
import com.ams.protocol.http.ServletContext;
import com.ams.server.handler.IProtocolService;

public class HttpService implements IProtocolService {
    private final Logger logger = LoggerFactory.getLogger(NetworkConnection.class);

    private static final int CORE_POOL_SIZE = 8;
    private static final int QUEUE_SIZE = 256;
    private ServletContext context;
    private ThreadPoolExecutor executor;
    
    public HttpService(String contextRoot, int maxPoolSize) throws IOException {
        this.context = new ServletContext(contextRoot);
        this.executor = new ThreadPoolExecutor(CORE_POOL_SIZE, maxPoolSize, 60, TimeUnit.SECONDS,
                new ArrayBlockingQueue<Runnable>(QUEUE_SIZE));
    }

    @Override
    public void invoke(final Connection connection) {
        final DefaultServlet servlet = new DefaultServlet(context);
        final HttpRequest request = new HttpRequest(connection.getInputStream());
        final HttpResponse response = new HttpResponse(connection.getOutputStream());
        Runnable handler = new Runnable() {
            @Override
            public void run() {
                try {
                    request.parse();
                    servlet.service(request, response);
                    if (request.isKeepAlive()) {
                        connection.close(true);
                    } else {
                        connection.close();
                    }
                } catch (IOException e) {
                    logger.debug(e.getMessage());
                    connection.close();
                }
            }
        };
        executor.execute(handler);
    }

	@Override
    public void shutdown() {
        executor.shutdown();
    }

}
