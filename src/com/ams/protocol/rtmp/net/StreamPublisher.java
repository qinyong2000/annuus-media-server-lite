package com.ams.protocol.rtmp.net;

import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ams.media.IMediaSerializer;
import com.ams.media.IMsgPublisher;
import com.ams.media.IMsgSubscriber;
import com.ams.media.MediaMessage;

public class StreamPublisher implements IMsgPublisher {
    final private Logger logger = LoggerFactory
            .getLogger(StreamPublisher.class);

    protected String publishName = null;
    private int pingBytes = 0;
    private int lastPing = 0;
    private boolean ping = false;
    private boolean pause = false;
    protected MediaMessage videoHeader = null;
    protected MediaMessage audioHeader = null;
    protected MediaMessage meta = null;
    protected ConcurrentLinkedQueue<IMsgSubscriber> subscribers = new ConcurrentLinkedQueue<IMsgSubscriber>();

    protected IMediaSerializer recorder = null; // record to file stream

    public StreamPublisher(String publishName) {
        this.publishName = publishName;
        String tokens[] = publishName.split(":");
        if (tokens.length >= 2) {
            this.publishName = tokens[1];
        }
    }

    public synchronized void publish(MediaMessage msg)
            throws IOException {
        boolean skipNotify = false;
        if (msg.isH264AudioHeader()) {
            if (audioHeader == null) {
                audioHeader = msg;
                logger.debug("received h264 audio header.");
            } else {
                skipNotify = true;
            }
        } else if (msg.isH264VideoHeader()) {
            if (videoHeader == null) {
                videoHeader = msg;
                logger.debug("received h264 video header.");
            } else {
                skipNotify = true;
            }
        } else if (msg.isMeta()) {
            meta = msg;
        }

        // ping
        ping(msg.getDataSize());

        if (pause)
            return;

        // record to file
        if (recorder != null) {
            recorder.write(msg);
        }
        // publish packet to other stream subscriber
        if (!skipNotify) {
            notify(msg);
        }
    }

    public synchronized void close() {
        if (recorder != null) {
            recorder.close();
        }
        subscribers.clear();
        videoHeader = null;
        audioHeader = null;
        meta = null;
        pause = false;
    }

    private void notify(MediaMessage message) {
        for (IMsgSubscriber subscriber : subscribers) {
            subscriber.messageNotify(message);
        }
    }

    private void ping(int dataSize) {
        pingBytes += dataSize;
        // ping
        ping = false;
        if (pingBytes - lastPing > 1024 * 10) {
            lastPing = pingBytes;
            ping = true;
        }
    }

    public void addSubscriber(IMsgSubscriber subscriber) {
        synchronized (subscribers) {
            subscribers.add(subscriber);
        }
    }

    public void removeSubscriber(IMsgSubscriber subscriber) {
        synchronized (subscribers) {
            subscribers.remove(subscriber);
        }
    }

    public void setRecorder(IMediaSerializer recorder) {
        this.recorder = recorder;
    }

    public boolean isPing() {
        return ping;
    }

    public int getPingBytes() {
        return pingBytes;
    }

    public void pause(boolean pause) {
        this.pause = pause;
    }

    public boolean isPaused() {
        return pause;
    }

    public String getPublishName() {
        return publishName;
    }

    public MediaMessage getVideoHeader() {
        return videoHeader;
    }

    public MediaMessage getAudioHeader() {
        return audioHeader;
    }

    public MediaMessage getMeta() {
        return meta;
    }

}
