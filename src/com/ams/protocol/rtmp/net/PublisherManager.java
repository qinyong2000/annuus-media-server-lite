package com.ams.protocol.rtmp.net;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ams.media.IMsgPublisher;
import com.ams.util.ObjectCache;

public class PublisherManager {
    final private static Logger logger = LoggerFactory.getLogger(PublisherManager.class);

    private static int DEFAULT_EXPIRE_TIME = 24 * 60 * 60;

    private static ObjectCache<IMsgPublisher> streamPublishers = new ObjectCache<IMsgPublisher>();

    public static IMsgPublisher getPublisher(String publishName) {
        return streamPublishers.get(publishName);
    }

    public static void addPublisher(StreamPublisher publisher) {
        String publishName = publisher.getPublishName();
        streamPublishers.put(publishName, publisher, DEFAULT_EXPIRE_TIME);
        logger.info("add publisher:{}", publishName);
    }

    public static void removePublisher(String publishName) {
        streamPublishers.remove(publishName);
        logger.info("remove publisher:{}", publishName);
    }

    public static Set<String> getAllPublishName() {
        return streamPublishers.keySet();
    }
}
