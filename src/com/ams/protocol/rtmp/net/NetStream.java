package com.ams.protocol.rtmp.net;

import java.io.EOFException;
import java.io.IOException;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ams.io.ByteBufferInputStream;
import com.ams.io.RandomAccessFileReader;
import com.ams.io.RandomAccessFileWriter;
import com.ams.media.IMediaDeserializer;
import com.ams.media.flv.FlvDeserializer;
import com.ams.media.flv.FlvSerializer;
import com.ams.media.mp4.Mp4Deserializer;
import com.ams.protocol.rtmp.RtmpConnection;
import com.ams.protocol.rtmp.amf.AmfValue;
import com.ams.protocol.rtmp.message.RtmpMessage;
import com.ams.protocol.rtmp.message.RtmpMessageChunkSize;
import com.ams.protocol.rtmp.message.RtmpMessageCommand;
import com.ams.protocol.rtmp.message.RtmpMessageData;
import com.ams.protocol.rtmp.message.RtmpMessageUserControl;
import com.ams.server.service.rtmp.replication.ReplSlaveService;

public class NetStream {
    private Logger logger = LoggerFactory.getLogger(NetStream.class);
    private RtmpConnection rtmp;
    private int streamId;
    private String playStreamName;
    private int transactionId = 0;
    private long timeStamp = 0;

    private StreamPublisher publisher = null;
    private StreamPlayer player = null;

    public NetStream(RtmpConnection rtmp, int streamId) {
        this.rtmp = rtmp;
        this.streamId = streamId;
    }

    public void writeMessage(RtmpMessage message) throws IOException {
        rtmp.writeRtmpMessage(streamId, timeStamp, message);
    }

    public void writeStatusMessage(String status, AmfValue info) throws IOException {
        AmfValue value = AmfValue.newObject();
        value.put("level", "status")
             .put("code", status);
        Map<String, AmfValue> v = info.object();
        for (String key : v.keySet()) {
            value.put(key, v.get(key).toString());
        }
        writeMessage(new RtmpMessageCommand("onStatus", transactionId,
                AmfValue.array(null, value)));
    }

    public void writeErrorMessage(String msg) throws IOException {
        AmfValue value = AmfValue.newObject();
        value.put("level", "error")
             .put("code", "NetStream.Error")
             .put("details", msg);
        writeMessage(new RtmpMessageCommand("onStatus", transactionId,
                AmfValue.array(null, value)));
    }

    public void writeDataMessage(AmfValue[] values) throws IOException {
        writeMessage(new RtmpMessageData(AmfValue.toBinary(values)));
    }
    
    public void doPlay() throws IOException {
        if (player != null) {
            try {
                player.play();
            } catch (EOFException e) {
                player.pause(true);
                stop();
            }
        }
    }
    
    public synchronized void close() {
        if (player != null) {
            player.close();
        }
        if (publisher != null) {
            publisher.close();
            PublisherManager.getInstance().removePublisher(publisher.getPublishName());
        }
    }

    public boolean isWriteBlocking() {
        return rtmp.getConnection().isWriteBlocking();
    }

    public void setTransactionId(int transactionId) {
        this.transactionId = transactionId;
    }

    public int getStreamId() {
        return streamId;
    }

    public StreamPublisher getPublisher() {
        return publisher;
    }

    public long getTimeStamp() {
        return timeStamp;
    }

    public void setTimeStamp(long timeStamp) {
        this.timeStamp = timeStamp;
    }

    public void seek(int time) throws IOException {
        if (player == null) {
            writeErrorMessage("Invalid 'Seek' stream id " + streamId);
            return;
        }
        // clear
        rtmp.writeProtocolControlMessage(new RtmpMessageUserControl(
                RtmpMessageUserControl.EVT_STREAM_EOF, streamId));
        rtmp.writeProtocolControlMessage(new RtmpMessageUserControl(
                RtmpMessageUserControl.EVT_STREAM_IS_RECORDED, streamId));
        rtmp.writeProtocolControlMessage(new RtmpMessageUserControl(
                RtmpMessageUserControl.EVT_STREAM_BEGIN, streamId));

        player.seek(time);

        writeStatusMessage(
                "NetStream.Seek.Notify",
                AmfValue.newObject()
                        .put("description", "Seeking " + time + ".")
                        .put("details", playStreamName)
                        .put("clientId", streamId));

        writeStatusMessage(
                "NetStream.Play.Start",
                AmfValue.newObject()
                        .put("description", "Start playing " + playStreamName + ".")
                        .put("clientId", streamId));

    }

    public void play(NetContext context, String streamName, int start, int duration, boolean reset)
            throws IOException {
        if (player != null) {
            writeErrorMessage("This channel is already playing");
            return;
        }
        this.playStreamName = streamName;

        // set chunk size
        rtmp.writeProtocolControlMessage(new RtmpMessageChunkSize(1024));

        // clear
        rtmp.writeProtocolControlMessage(new RtmpMessageUserControl(
                RtmpMessageUserControl.EVT_STREAM_IS_RECORDED, streamId));
        rtmp.writeProtocolControlMessage(new RtmpMessageUserControl(
                RtmpMessageUserControl.EVT_STREAM_BEGIN, streamId));

        // NetStream.Play.Reset
        if (reset) {
            writeStatusMessage(
                    "NetStream.Play.Reset",
                    AmfValue.newObject()
                            .put("description", "Resetting " + streamName + ".")
                            .put("details", streamName)
                            .put("clientId", streamId));
        }
        // NetStream.Play.Start
        writeStatusMessage(
                "NetStream.Play.Start",
                AmfValue.newObject()
                        .put("description", "Start playing " + streamName + ".")
                        .put("clientId", streamId));

        String app = context.getAttribute("app");
        switch (start) {
        case -1: // live only
        {
            String publishName = context.getPublishName(app, streamName);
            StreamPublisher publisher = (StreamPublisher) PublisherManager.getInstance().getPublisher(publishName);
            if (publisher == null) {
                // NetStream.Play.StreamNotFound
                writeStatusMessage(
                        "NetStream.Play.StreamNotFound",
                        AmfValue.newObject()
                                .put("description", "Live stream " + streamName + "is not found.")
                                .put("clientId", streamId));
                // try to subscribe live stream from matser
                ReplSlaveService.getInstance().addSubscription(streamName);
                return;
            }
            StreamSubscriber subscriber = new StreamSubscriber(publisher, this);
            publisher.addSubscriber(subscriber);
        }
            break;
        case -2: // first find live
        {
            String publishName = context.getPublishName(app, streamName);
            StreamPublisher publisher = (StreamPublisher) PublisherManager.getInstance().getPublisher(publishName);
            if (publisher != null) {
                StreamSubscriber subscriber = new StreamSubscriber(publisher, this);
                publisher.addSubscriber(subscriber);
                break;
            } else {
                start = 0;
            }
        }
        default: // >= 0
            String tokens[] = streamName.split(":");
            String type = "";
            String file = streamName;
            if (tokens.length >= 2) {
                type = tokens[0];
                file = tokens[1];
            }
            String path = context.getRealPath(app, file, type);
            player = createPlayer(type, path);
            if (player != null) {
                player.seek(start);
            } else {
                // NetStream.Play.StreamNotFound
                writeStatusMessage(
                        "NetStream.Play.StreamNotFound",
                        AmfValue.newObject()
                                .put("description", "Stream " + streamName + "is not found.")
                                .put("clientId", streamId));
            }
        }

    }

    public StreamPlayer createPlayer(String type, String file)
            throws IOException {
        RandomAccessFileReader reader;
        try {
            reader = new RandomAccessFileReader(file, 0);
        } catch (IOException e) {
            logger.info("can not open file: {}", file);
            return null;
        }
        IMediaDeserializer sampleDeserializer = null;
        if ("mp4".equalsIgnoreCase(type)) {
            sampleDeserializer = new Mp4Deserializer(reader);
        } else {
            String ext = file.substring(file.lastIndexOf('.') + 1);
            if ("f4v".equalsIgnoreCase(ext) || "mp4".equalsIgnoreCase(ext)) {
                sampleDeserializer = new Mp4Deserializer(reader);
            } else {
                FlvDeserializer flvDeserializer = new FlvDeserializer(reader);
                try {
                    RandomAccessFileReader indexReader = new RandomAccessFileReader(
                            file + ".idx", 0);
                    FlvDeserializer.FlvIndex flvIndex = flvDeserializer.new FlvIndex();
                    flvIndex.read(new ByteBufferInputStream(indexReader));
                    indexReader.close();
                } catch (Exception e) {
                    flvDeserializer.readSamples();
                }
                sampleDeserializer = flvDeserializer;
            }
        }
        return new StreamPlayer(sampleDeserializer, this);
    }

    public void stop() throws IOException {
        // clear
        rtmp.writeProtocolControlMessage(new RtmpMessageUserControl(
                RtmpMessageUserControl.EVT_STREAM_EOF, streamId));

        // NetStream.Play.Complete
        writeDataMessage(AmfValue.array("onPlayStatus", AmfValue.newObject()
                .put("level", "status")
                .put("code", "NetStream.Play.Complete")));

        // NetStream.Play.Stop
        writeStatusMessage(
                "NetStream.Play.Stop",
                AmfValue.newObject()
                        .put("description", "Stoped playing " + playStreamName + ".")
                        .put("clientId", streamId));
    }

    public void pause(boolean pause, long time) throws IOException {
        if (player == null) {
            writeErrorMessage("This channel is already closed");
            return;
        }
        player.pause(pause);

        writeStatusMessage((pause ? "NetStream.Pause.Notify"
                : "NetStream.Unpause.Notify"), AmfValue.newObject());
    }

    public void publish(NetContext context, String name, String type) throws IOException {
        String app = context.getAttribute("app");
        String streamName = name.split("\\?")[0];
        // save to share or file
        String publishName = context.getPublishName(app, streamName);
        publisher = new StreamPublisher(publishName);
        if ("record".equals(type)) {
            String file = context.getRealPath(app, streamName, "");
            try {
                RandomAccessFileWriter writer = new RandomAccessFileWriter(file, false);
                publisher.setRecorder(new FlvSerializer(writer));
            } catch (IOException e) {
                logger.debug(e.getMessage());
            }
        } else if ("append".equals(type)) {
            String file = context.getRealPath(app, streamName, "");
            RandomAccessFileWriter writer = new RandomAccessFileWriter(file, true);
            publisher.setRecorder(new FlvSerializer(writer));
        } else if ("live".equals(type)) {
            // nothing to do
        }
        PublisherManager.getInstance().addPublisher(publisher);

        writeStatusMessage("NetStream.Publish.Start",
                AmfValue.newObject().put("details", streamName));
    }

    public void receiveAudio(boolean flag) throws IOException {
        if (player != null) {
            player.audioPlaying(flag);
        }
    }

    public void receiveVideo(boolean flag) throws IOException {
        if (player != null) {
            player.videoPlaying(flag);
        }
    }

    public void flush() throws IOException {
        rtmp.flush();
    }
}