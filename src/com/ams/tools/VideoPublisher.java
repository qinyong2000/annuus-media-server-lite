package com.ams.tools;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ams.tools.RtmpClient;

public class VideoPublisher {
    private static Logger logger = LoggerFactory.getLogger(VideoPublisher.class);

    public static void publish(String publishName, String fileName,
            String host, int port) throws IOException {
        logger.info("connect to server...");

        RtmpClient client = new RtmpClient(host, port);

        client.connect("webcam");

        logger.info("create stream...");
        
        int streamId = client.createStream();

        logger.info("start publish...");

        if (!client.publish(streamId, publishName, fileName)) {
            logger.info(client.getErrorMsg());
            client.close();
        }

    }

    public static void main(String[] args) {
        if (args.length < 3) {
            System.out
                    .println("VideoPublisher.main publishName fileName host [port]");
            return;
        }
        String publishName = args[0];
        String fileName = args[1];
        String host = args[2];
        int port = args.length == 4 ? Integer.parseInt(args[3]) : 1935;
        try {
            publish(publishName, fileName, host, port);
        } catch (IOException e) {
            logger.debug(e.toString());
        }
    }
}
