package com.ams.tools;

import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ams.io.network.Connection;
import com.ams.io.network.ConnectionListener;
import com.ams.io.network.NetworkClientConnection;
import com.ams.protocol.rtmp.RtmpConnection;
import com.ams.protocol.rtmp.RtmpException;
import com.ams.protocol.rtmp.RtmpHandShake;
import com.ams.protocol.rtmp.amf.AmfValue;
import com.ams.protocol.rtmp.message.RtmpMessage;
import com.ams.protocol.rtmp.message.RtmpMessageCommand;
import com.ams.protocol.rtmp.net.NetStream;
import com.ams.protocol.rtmp.net.StreamPlayer;

public class RtmpClient {
    public interface ResponseListener<T> {
        public void onSuccess(T response);
        public void onError(String message);
    }
    private interface InternalEventListener {
        public void onResult(AmfValue[] result);
        public void onStatus(AmfValue[] status);
    }
    
    private static Logger logger = LoggerFactory.getLogger(RtmpClient.class);
    private final static int STREAM_ID = 0;
    private final static int TIMESTAMP = 0;
    private final static int TANSACTION_ID = 1;  // always 1
    private NetworkClientConnection conn;
    private RtmpConnection rtmp;
    private LinkedBlockingQueue<InternalEventListener> pipeLine;
    private StreamPlayer player = null;
    private boolean running = true;
    private Executor executor = null;

    public RtmpClient(String host, int port) {
        conn = new NetworkClientConnection(new InetSocketAddress(host, port));
        rtmp = new RtmpConnection(conn);
        executor = Executors.newCachedThreadPool();
    }
    
    private void startRtmpClient() {
      executor.execute(new Runnable() {
        @Override
        public void run() {
          logger.debug("rtmp client start.");
          try {
              while (running) {
                  if (player != null) {
                      player.play();
                  }
                  readRtmpResponse();
                  // write to socket channel
                  conn.flush();
              }
          } catch (EOFException e) {
              if (player != null) {
                  try {
                      closeStream(player.getStream().getStreamId());
                  } catch (IOException e1) {}
              }
          } catch (Exception e) {
              e.printStackTrace();
          }
          conn.close();
          notifyEnd();
          logger.debug("rtmp client end.");
        }
      });
    }
    
    private void readRtmpResponse() throws IOException, RtmpException {
        rtmp.readRtmpMessage();

        if (!rtmp.isRtmpMessageReady())
            return;

        RtmpMessage message = rtmp.getCurrentMessage();
        if (!(message instanceof RtmpMessageCommand))
            return;

        RtmpMessageCommand msg = (RtmpMessageCommand) message;
        InternalEventListener eventListener = pipeLine.peek();
        if (eventListener == null)
            return;
        pipeLine.poll();
        if ("_result".equals(msg.getName())) {
            eventListener.onResult(msg.getArgs());
        } else if ("onStatus".equals(msg.getName())) {
            eventListener.onStatus(msg.getArgs());
        }
    }

    private boolean doHandShake(RtmpHandShake handshake) {
        logger.debug("start rtmp hand shake");
        boolean success = true;
        while (!handshake.isHandshakeDone()) {
            try {
                handshake.doClientHandshake();
                conn.flush();
                Thread.sleep(1);
            } catch (Exception e) {
                success = false;
                break;
            }
        }
        return success;
    }
    
    public void connect(final String app, final ResponseListener<Void> listener) throws IOException {
        final InternalEventListener eventListener = new InternalEventListener() {
            public void onResult(AmfValue[] result) {
                Map<String, AmfValue> r = result[1].object();
                if ("NetConnection.Connect.Success".equals(r.get("code").string())) {
                    logger.debug("connected rtmp server.");
                    listener.onSuccess(null);
                }
            }

            public void onStatus(AmfValue[] status) {
                Map<String, AmfValue> r = status[1].object();
                if ("NetConnection.Error".equals(r.get("code").string())) {
                    String errorMsg = r.get("details").string();
                    listener.onError(errorMsg);
                }
            }
        };
        conn.connect(new ConnectionListener() {
            @Override
            public void onConnectionEstablished(Connection conn) {
                RtmpHandShake handshake = new RtmpHandShake(rtmp);
                if (!doHandShake(handshake)) {
                    listener.onError("Handshake error");
                    return;
                }
                // start rtmp client
                startRtmpClient();
                // send connect command
                pipeLine.offer(eventListener);
                AmfValue[] args = { AmfValue.newObject().put("app", app) };
                RtmpMessage message = new RtmpMessageCommand("connect", TANSACTION_ID, args);
                try {
                    rtmp.writeRtmpMessage(STREAM_ID, TIMESTAMP, message);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            @Override
            public void onConnectionClosed(Connection conn) {
            }
            @Override
            public void onConnectionError(Connection conn, int error) {
                listener.onError("connect rtmp server error:" + error);
            }
            @Override
            public void onConnectionDataReceived(Connection conn, ByteBuffer[] buffers) {
            }
        });
    }

    public void createStream(final ResponseListener<Integer> listener) throws IOException {
        InternalEventListener eventListener = new InternalEventListener() {
            public void onResult(AmfValue[] result) {
                int streamId = result[1].integer();
                logger.debug("rtmp stream created.");
                listener.onSuccess(streamId);
            }

            public void onStatus(AmfValue[] status) {
            }
        };
        pipeLine.offer(eventListener);
        RtmpMessage message = new RtmpMessageCommand("createStream", TANSACTION_ID, null);
        rtmp.writeRtmpMessage(STREAM_ID, TIMESTAMP, message);
    }

    public void publish(final int streamId, String publishName, final String fileName, final ResponseListener<Void> listener) throws IOException {
        InternalEventListener eventListener = new InternalEventListener() {
            public void onResult(AmfValue[] result) {
            }

            public void onStatus(AmfValue[] status) {
                Map<String, AmfValue> result = status[1].object();
                String level = result.get("level").string();
                String code =  result.get("code").string();
                if ("status".equals(level) && 
                    "NetStream.Publish.Start".equals(code)) {
                    try {
                        NetStream stream = new NetStream(rtmp, streamId);
                        player = stream.createPlayer(null, fileName);
                        if (player != null) {
                            player.seek(0);
                            logger.debug("start to publish stream.");
                            listener.onSuccess(null);
                            return;
                        }
                    } catch (IOException e) {
                        logger.debug(e.getMessage());
                    }
                    listener.onError("Cannot start to publish stream:" + fileName);
                } else {
                    String errorMsg = result.get("details").string();
                    listener.onError(errorMsg);
                }
            }
        };
        pipeLine.offer(eventListener);
        RtmpMessage message = new RtmpMessageCommand("publish", TANSACTION_ID, AmfValue.array(null, publishName, "live"));
        rtmp.writeRtmpMessage(streamId, TIMESTAMP, message);
    }

    public void closeStream(int streamId) throws IOException {
        RtmpMessage message = new RtmpMessageCommand("closeStream", 0, null);
        rtmp.writeRtmpMessage(streamId, TIMESTAMP, message);
        logger.debug("rtmp stream closed.");
    }

    public void close() {
        running = false;
    }

    public void notifyEnd() {
        synchronized(this) {
            notify();
        }
    }
    
    public void waitForEnd() {
        synchronized(this) {
            try {
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
