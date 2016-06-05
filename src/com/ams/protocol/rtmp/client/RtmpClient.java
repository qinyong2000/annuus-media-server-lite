package com.ams.protocol.rtmp.client;

import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ams.io.network.NetworkClientConnection;
import com.ams.protocol.rtmp.RtmpConnection;
import com.ams.protocol.rtmp.RtmpException;
import com.ams.protocol.rtmp.RtmpHandShake;
import com.ams.protocol.rtmp.amf.AmfException;
import com.ams.protocol.rtmp.amf.AmfValue;
import com.ams.protocol.rtmp.message.RtmpMessage;
import com.ams.protocol.rtmp.message.RtmpMessageCommand;
import com.ams.protocol.rtmp.net.NetStream;
import com.ams.protocol.rtmp.net.StreamPlayer;

public class RtmpClient implements Runnable {
    final static private Logger logger = LoggerFactory
            .getLogger(RtmpClient.class);
    private static int DEFAULT_TIMEOUT = 60 * 1000;
    private NetworkClientConnection conn;
    private RtmpConnection rtmp;
    private RtmpHandShake handshake;

    private final static int TANSACTION_ID = 1;

    private final static int CHANNEL_RTMP_COMMAND = 3;
    private final static int CHANNEL_RTMP_PUBLISH = 7;

    private final static String CREATE_STREAM_ID_KEY = "createStreamId";
    private final static String PUBLISH_STREAM_ID_KEY = "publishStreamId";
    private final static String PUBLISH_FILE_NAME_KEY = "publishFileName";
    private final static String PUBLISH_PLAYER_KEY = "publishPlayer";

    private HashMap<String, Object> context = new HashMap<String, Object>();
    private boolean success = false;
    private String errorMsg = "";

    private RtmpClientEventListener eventListener = null;
    private boolean running = true;

    public RtmpClient(String host, int port) throws IOException {
        conn = new NetworkClientConnection();
        conn.connect(new InetSocketAddress(host, port));
        rtmp = new RtmpConnection(conn);
        handshake = new RtmpHandShake(rtmp);
        if (!handShake())
            throw new IOException("handshake failed");
        Thread t = new Thread(this, "rtmp client");
        // t.setDaemon(true);
        t.start();
    }

    private void readResponse() throws IOException, AmfException, RtmpException {
        // waiting for data arriving
        rtmp.readRtmpMessage();

        if (!rtmp.isRtmpMessageReady())
            return;

        RtmpMessage message = rtmp.getCurrentMessage();
        if (!(message instanceof RtmpMessageCommand))
            return;

        RtmpMessageCommand msg = (RtmpMessageCommand) message;

        if (eventListener == null)
            return;

        if ("_result".equals(msg.getName())) {
            eventListener.onResult(msg.getArgs());
        } else if ("onStatus".equals(msg.getName())) {
            eventListener.onStatus(msg.getArgs());
        }
        synchronized (this) {
            notifyAll();
        }

    }

    private boolean waitResponse() {
        boolean timeout = false;
        long start = System.currentTimeMillis();
        try {
            synchronized (this) {
                wait(DEFAULT_TIMEOUT);
            }
        } catch (InterruptedException e) {
        }
        long now = System.currentTimeMillis();
        if (now - start >= DEFAULT_TIMEOUT) {
            timeout = true;
        }
        if (timeout) {
            errorMsg = "Waitint response timeout";
        }
        return !timeout;
    }

    public boolean connect(String app) throws IOException {
        success = false;
        errorMsg = "";
        eventListener = new RtmpClientEventListener() {
            public void onResult(AmfValue[] result) {
                Map<String, AmfValue> r = result[1].object();
                if ("NetConnection.Connect.Success".equals(r.get("code")
                        .string())) {
                    logger.debug("rtmp connected.");
                    success = true;
                }
            }

            public void onStatus(AmfValue[] status) {
                Map<String, AmfValue> r = status[1].object();
                if ("NetConnection.Error".equals(r.get("code").string())) {
                    success = false;
                    errorMsg = r.get("details").string();
                }
            }
        };
        AmfValue[] args = { AmfValue.newObject().put("app", app) };
        RtmpMessage message = new RtmpMessageCommand("connect", TANSACTION_ID,
                args);
        rtmp.writeRtmpMessage(CHANNEL_RTMP_COMMAND, 0, 0, message);
        if (!waitResponse()) {
            success = false;
        }
        return success;
    }

    public int createStream() throws IOException {
        context.put(CREATE_STREAM_ID_KEY, -1);
        errorMsg = "";
        eventListener = new RtmpClientEventListener() {
            public void onResult(AmfValue[] result) {
                int streamId = result[1].integer();
                context.put(CREATE_STREAM_ID_KEY, streamId);
                logger.debug("rtmp stream created.");
            }

            public void onStatus(AmfValue[] status) {
            }
        };
        AmfValue[] args = { new AmfValue(null) };
        RtmpMessage message = new RtmpMessageCommand("createStream",
                TANSACTION_ID, args);
        rtmp.writeRtmpMessage(CHANNEL_RTMP_COMMAND, 0, 0, message);
        if (!waitResponse()) {
            context.put(CREATE_STREAM_ID_KEY, -1);
        }
        return (Integer) context.get(CREATE_STREAM_ID_KEY);
    }

    public boolean publish(int streamId, String publishName, String fileName)
            throws IOException {
        success = false;
        errorMsg = "";

        context.put(PUBLISH_STREAM_ID_KEY, streamId);
        context.put(PUBLISH_FILE_NAME_KEY, fileName);
        eventListener = new RtmpClientEventListener() {
            public void onResult(AmfValue[] result) {
            }

            public void onStatus(AmfValue[] status) {
                Map<String, AmfValue> result = status[1].object();
                String level = result.get("level").string();
                if ("status".equals(level)) {
                    int streamId = (Integer) context.get(PUBLISH_STREAM_ID_KEY);
                    String fileName = (String) context
                            .get(PUBLISH_FILE_NAME_KEY);
                    NetStream stream = new NetStream(rtmp, streamId);
                    try {
                        StreamPlayer player = stream.createPlayer(null,
                                fileName);
                        if (player != null) {
                            player.seek(0);
                            context.put(PUBLISH_PLAYER_KEY, player);
                            logger.debug("rtmp stream start to publish.");
                            success = true;
                        }
                    } catch (IOException e) {
                        logger.debug(e.getMessage());
                    }
                } else {
                    errorMsg = result.get("details").string();
                }
            }
        };
        AmfValue[] args = AmfValue.array(null, publishName, "live");
        RtmpMessage message = new RtmpMessageCommand("publish", TANSACTION_ID,
                args);
        rtmp.writeRtmpMessage(CHANNEL_RTMP_PUBLISH, streamId, 0, message);
        if (!waitResponse()) {
            success = false;
        }
        return success;
    }

    public void closeStream(int streamId) throws IOException {
        AmfValue[] args = { new AmfValue(null) };
        RtmpMessage message = new RtmpMessageCommand("closeStream", 0, args);
        rtmp.writeRtmpMessage(CHANNEL_RTMP_COMMAND, streamId, 0, message);
        success = true;
        errorMsg = "";
        logger.debug("rtmp stream closed.");
    }

    public NetStream createNetStream(int streamId) {
        return new NetStream(rtmp, streamId);
    }

    public void close() {
        running = false;
    }

    private boolean handShake() {
        boolean success = true;
        while (!handshake.isHandshakeDone()) {
            try {
                handshake.doClientHandshake();
                // write to socket channel
                conn.flush();
            } catch (Exception e) {
                success = false;
                break;
            }
        }
        return success;
    }

    public void run() {
        try {
            while (running) {
                StreamPlayer player = (StreamPlayer) context
                        .get(PUBLISH_PLAYER_KEY);
                if (player != null) {
                    player.play();
                }
                readResponse();
                // write to socket channel
                conn.flush();
            }
        } catch (EOFException e) {
            Integer streamId = (Integer) context.get(PUBLISH_STREAM_ID_KEY);
            if (streamId != null)
                try {
                    closeStream(streamId);
                } catch (IOException e1) {
                }
        } catch (Exception e) {
        }
        conn.close();
    }

    public String getErrorMsg() {
        return errorMsg;
    }
}
