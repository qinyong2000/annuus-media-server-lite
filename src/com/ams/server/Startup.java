package com.ams.server;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.LogManager;

public class Startup {
    private static Server server;

    private static Server createServerInstance(Configuration config) {
        Server server = null;
        try {
            server = new Server(config);
        } catch (IOException e) {
            System.out.println("Creating server instance failed.");
            return null;
        }
        return server;
    }

    public static void main(String[] args) {
        System.out.println("Start " + Version.SERVER_NAME + " server "
                + Version.VERSION + ".");
        System.setSecurityManager(null);
        try {
            InputStream in = new FileInputStream("log.properties");
            LogManager.getLogManager().readConfiguration(in);
            in.close();
        } catch (Exception e) {
        }
        Configuration config = new Configuration();
        try {
            if (!config.read()) {
                System.out.println("read config error!");
                return;
            }
        } catch (FileNotFoundException e) {
            System.out
                    .println("Not found server.conf file, use default setting!");
            return;
        }

        server = createServerInstance(config);
        if (server != null) {
            server.startup();
        }
    }
}
