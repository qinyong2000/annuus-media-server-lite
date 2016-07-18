package com.ams.tools;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ams.tools.RtmpClient;
import com.ams.tools.RtmpClient.ResponseListener;

public class VideoPublisher {
    private static Logger logger = LoggerFactory.getLogger(VideoPublisher.class);

    public static void publish(final String publishName, final String fileName,
            String host, int port) throws IOException {
        final RtmpClient client = new RtmpClient(host, port);

        logger.info("connect to rtmp server...");
        client.connect("webcam", new ResponseListener<Void>() {
            @Override
            public void onSuccess(Void response) {
                logger.info("create stream...");
                try {
	                client.createStream(new ResponseListener<Integer>() {
	                    @Override
	                    public void onSuccess(Integer streamId) {
	                        try {
	                            client.publish(streamId, publishName, fileName, new ResponseListener<Void>() {
	                                @Override
	                                public void onSuccess(Void response) {
	                                    logger.info("start publish...");
	                                }
	                                @Override
	                                public void onError(String message) {
	                                    logger.info(message);
	                                }
	                            });
                            } catch (IOException e) {
	                            e.printStackTrace();
                            }
	                    }
	                    @Override
	                    public void onError(String message) {
	                        logger.info(message);
	                    }
	                });
                } catch (IOException e) {
	                e.printStackTrace();
                }
            }
            @Override
            public void onError(String message) {
                logger.info(message);
            }
        });
        
        client.waitForEnd();
    }

    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("VideoPublisher.main publishName fileName host [port]");
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
