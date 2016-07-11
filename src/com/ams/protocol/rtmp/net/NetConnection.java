package com.ams.protocol.rtmp.net;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ams.media.flv.FlvException;
import com.ams.protocol.rtmp.amf.*;
import com.ams.protocol.rtmp.message.*;
import com.ams.protocol.rtmp.*;

public class NetConnection {
    private Logger logger = LoggerFactory.getLogger(NetConnection.class);

    private RtmpHandShake handshake;
    private RtmpConnection rtmp;
    private NetContext context;
    private HashMap<Integer, NetStream> streams;

    public NetConnection(RtmpConnection rtmp, NetContext context) {
        this.rtmp = rtmp;
        this.handshake = new RtmpHandShake(rtmp);
        this.streams = new HashMap<Integer, NetStream>();
        this.context = context;
    }

    private void onMediaMessage(RtmpHeader header, RtmpMessage message)
            throws NetConnectionException, IOException {
        NetStream stream = streams.get(header.getStreamId());
        if (stream == null) {
            throw new NetConnectionException("Unknown stream "
                    + header.getStreamId());
        }

        StreamPublisher publisher = stream.getPublisher();
        if (publisher == null) {
            return;
        }

        publisher.publish(message.toMediaMessage(header.getTimestamp()));

        if (publisher.isPing()) {
            rtmp.writeProtocolControlMessage(new RtmpMessageAck(publisher
                    .getPingBytes()));
        }
    }

    private void onSharedMessage(RtmpHeader header, RtmpMessage message) {
        // TODO
    }

    private void onCommandMessage(RtmpHeader header, RtmpMessage message)
            throws NetConnectionException, IOException, FlvException {
        RtmpMessageCommand command = (RtmpMessageCommand) message;
        String cmdName = command.getName();
        logger.debug("command: {}", cmdName);
        if ("connect".equals(cmdName)) {
            onConnect(header, command);
        } else if ("createStream".equals(cmdName)) {
            onCreateStream(header, command);
        } else if ("deleteStream".equals(cmdName)) {
            onDeleteStream(header, command);
        } else if ("closeStream".equals(cmdName)) {
            onCloseStream(header, command);
        } else if ("getStreamLength".equals(cmdName)) {
            onGetStreamLength(header, command);
        } else if ("play".equals(cmdName)) {
            onPlay(header, command);
        } else if ("play2".equals(cmdName)) {
            onPlay2(header, command);
        } else if ("publish".equals(cmdName)) {
            onPublish(header, command);
        } else if ("pause".equals(cmdName) || "pauseRaw".equals(cmdName)) {
            onPause(header, command);
        } else if ("receiveAudio".equals(cmdName)) {
            onReceiveAudio(header, command);
        } else if ("receiveVideo".equals(cmdName)) {
            onReceiveVideo(header, command);
        } else if ("seek".equals(cmdName)) {
            onSeek(header, command);
        } else { // remote rpc call
            onCall(header, command);
        }
    }

    private void onConnect(RtmpHeader header, RtmpMessageCommand command)
            throws NetConnectionException, IOException {
        AmfValue amfObject = command.getCommandObject();
        Map<String, AmfValue> obj = amfObject.object();

        String app = obj.get("app").string();
        if (app == null) {
            netConnectionError(header.getChunkStreamId(), header.getStreamId(),
                    command.getTransactionId(), "Invalid 'Connect' parameters");
            return;
        }
        context.setAttribute("app", app);

        rtmp.writeProtocolControlMessage(new RtmpMessageWindowAckSize(
                128 * 1024));
        rtmp.writeProtocolControlMessage(new RtmpMessagePeerBandwidth(
                128 * 1024, (byte) 2));
        rtmp.writeProtocolControlMessage(new RtmpMessageUserControl(
                RtmpMessageUserControl.EVT_STREAM_BEGIN, header.getStreamId()));

        AmfValue properties = AmfValue.newObject();
        properties.put("capabilities", 31)
                  .put("fmsver", "AMS/0,1,0,0")
                  .put("mode", 1);

        AmfValue info = AmfValue.newObject();
        info.put("level", "status")
            .put("code", "NetConnection.Connect.Success")
            .put("description", "Connection succeeded.");

        AmfValue objectEncoding = obj.get("objectEncoding");
        if (objectEncoding != null) {
            info.put("objectEncoding", objectEncoding);
        }

        rtmp.writeRtmpMessage(header.getChunkStreamId(), 0, 0,
                new RtmpMessageCommand("_result", command.getTransactionId(), 
                        AmfValue.array(properties, info)));

    }

    private void onGetStreamLength(RtmpHeader header, RtmpMessageCommand command)
            throws IOException, NetConnectionException, FlvException {
        String streamName = command.getCommandParameters(0, "").string();
        // rtmp.writeRtmpMessage(header.getChunkStreamId(), 0, 0,
        // new RtmpMessageCommand("_result", command.getTransactionId(),
        // AmfValue.array(null, 140361)));
    }

    private void onPlay(RtmpHeader header, RtmpMessageCommand command)
            throws IOException, NetConnectionException, FlvException {
        String streamName = command.getCommandParameters(0, "").string();
        int start = command.getCommandParameters(1, -2).integer();
        int duration = command.getCommandParameters(2, -1).integer();
        NetStream stream = streams.get(header.getStreamId());
        if (stream == null) {
            streamError(header.getChunkStreamId(), header.getStreamId(),
                    command.getTransactionId(), "Invalid 'Play' stream id "
                            + header.getStreamId());
            return;
        }
        stream.setTransactionId(command.getTransactionId());
        logger.debug("play stream: {}", streamName);
        stream.play(context, streamName, start, duration);
    }

    private void onPlay2(RtmpHeader header, RtmpMessageCommand command)
            throws IOException, NetConnectionException, FlvException {
        int startTime = command.getCommandParameters(0, -1).integer();
        String oldStreamName = command.getCommandParameters(1, "").string();
        String streamName = command.getCommandParameters(2, "").string();
        int duration = command.getCommandParameters(3, -1).integer();
        String transition = command.getCommandParameters(4, "switch").string(); // switch or swap
        // TODO
    }

    private void onSeek(RtmpHeader header, RtmpMessageCommand command)
            throws IOException, NetConnectionException, FlvException {
        int offset = command.getCommandParameters(0, -1).integer();
        NetStream stream = streams.get(header.getStreamId());
        if (stream == null) {
            streamError(header.getChunkStreamId(), header.getStreamId(),
                    command.getTransactionId(), "Invalid 'Seek' stream id "
                            + header.getStreamId());
            return;
        }
        stream.setTransactionId(command.getTransactionId());
        stream.seek(offset);
    }

    private void onPause(RtmpHeader header, RtmpMessageCommand command)
            throws IOException, NetConnectionException, FlvException {
        boolean pause = command.getCommandParameters(0, false).bool();
        int time = command.getCommandParameters(1, -1).integer();
        NetStream stream = streams.get(header.getStreamId());
        if (stream == null) {
            streamError(header.getChunkStreamId(), header.getStreamId(),
                    command.getTransactionId(), "Invalid 'Pause' stream id "
                            + header.getStreamId());
            return;
        }
        stream.setTransactionId(command.getTransactionId());
        stream.pause(pause, time);
    }

    private void onPublish(RtmpHeader header, RtmpMessageCommand command)
            throws IOException, NetConnectionException {
        String publishName = command.getCommandParameters(0, "").string();
        if (PublisherManager.getPublisher(publishName) != null) {
            streamError(header.getChunkStreamId(), header.getStreamId(),
                    command.getTransactionId(), "The publish '" + publishName
                            + "' is already used");
            return;
        }

        String type = command.getCommandParameters(1, "").string();
        NetStream stream = streams.get(header.getStreamId());
        if (stream == null) {
            streamError(header.getChunkStreamId(), header.getStreamId(),
                    command.getTransactionId(), "Invalid 'Publish' stream id "
                            + header.getStreamId());
            return;
        }
        logger.debug("publish stream: {}", publishName);
        stream.setTransactionId(command.getTransactionId());
        stream.publish(context, publishName, type);
    }

    private void onReceiveAudio(RtmpHeader header, RtmpMessageCommand command)
            throws IOException {
        boolean flag = command.getCommandParameters(0, false).bool();
        NetStream stream = streams.get(header.getStreamId());
        if (stream == null) {
            streamError(header.getChunkStreamId(), header.getStreamId(),
                    command.getTransactionId(),
                    "Invalid 'ReceiveAudio' stream id " + header.getStreamId());
            return;
        }
        stream.receiveAudio(flag);
    }

    private void onReceiveVideo(RtmpHeader header, RtmpMessageCommand command)
            throws IOException {
        boolean flag = command.getCommandParameters(0, false).bool();
        NetStream stream = streams.get(header.getStreamId());
        if (stream == null) {
            streamError(header.getChunkStreamId(), header.getStreamId(),
                    command.getTransactionId(),
                    "Invalid 'ReceiveVideo' stream id " + header.getStreamId());
            return;
        }
        stream.receiveVideo(flag);
    }

    private void onCreateStream(RtmpHeader header, RtmpMessageCommand command)
            throws IOException {
        NetStream stream = createStream();
        rtmp.writeRtmpMessage(header.getChunkStreamId(), 0, 0,
                new RtmpMessageCommand("_result", command.getTransactionId(), null, stream.getStreamId()));
    }

    private void onDeleteStream(RtmpHeader header, RtmpMessageCommand command)
            throws IOException, NetConnectionException {
        int streamId = command.getCommandParameters(0, -1).integer();
        NetStream stream = streams.get(streamId);
        if (stream == null) {
            streamError(header.getChunkStreamId(), header.getStreamId(),
                    command.getTransactionId(),
                    "Invalid 'deleteStream' stream id");
            return;
        }
        closeStream(stream);
    }

    private void onCloseStream(RtmpHeader header, RtmpMessageCommand command)
            throws NetConnectionException, IOException {
        NetStream stream = streams.get(header.getStreamId());
        if (stream == null) {
            streamError(header.getChunkStreamId(), header.getStreamId(),
                    command.getTransactionId(),
                    "Invalid 'CloseStream' stream id " + header.getStreamId());
            return;
        }
        closeStream(stream);
    }

    private void onCall(RtmpHeader header, RtmpMessageCommand command) {
        // String procedureName = command.getName();
    }

    public void writeError(int chunkStreamId, int streamId, int transactionId,
            String code, String msg) throws IOException {
        AmfValue value = AmfValue.newObject();
        value.put("level", "error").put("code", code).put("details", msg);
        rtmp.writeRtmpMessage(
                chunkStreamId,
                streamId,
                0,
                new RtmpMessageCommand("onStatus", transactionId, null, value));
    }

    public void netConnectionError(int chunkStreamId, int streamId,
            int transactionId, String msg) throws IOException {
        writeError(chunkStreamId, streamId, transactionId, "NetConnection", msg);
    }

    public void streamError(int chunkStreamId, int streamId, int transactionId,
            String msg) throws IOException {
        writeError(chunkStreamId, streamId, transactionId, "NetStream.Error",
                msg);
    }

    public void readAndProcessRtmpMessage() throws IOException, RtmpException,
            AmfException {
        if (!handshake.isHandshakeDone()) {
            handshake.doServerHandshake();
            return;
        }

        if (!rtmp.readRtmpMessage())
            return;
        RtmpHeader header = rtmp.getCurrentHeader();
        RtmpMessage message = rtmp.getCurrentMessage();
        try {
            switch (message.getType()) {
            case RtmpMessage.MESSAGE_AMF0_COMMAND:
            case RtmpMessage.MESSAGE_AMF3_COMMAND:
                onCommandMessage(header, message);
                break;
            case RtmpMessage.MESSAGE_AUDIO:
            case RtmpMessage.MESSAGE_VIDEO:
            case RtmpMessage.MESSAGE_AMF0_DATA:
            case RtmpMessage.MESSAGE_AMF3_DATA:
                onMediaMessage(header, message);
                break;
            case RtmpMessage.MESSAGE_USER_CONTROL:
                RtmpMessageUserControl userControl = (RtmpMessageUserControl) message;
                logger.debug(
                        "read message USER_CONTROL: {}, {}, {}",
                        new Integer[] { userControl.getEvent(),
                                userControl.getStreamId(),
                                userControl.getTimestamp() });
                break;
            case RtmpMessage.MESSAGE_SHARED_OBJECT:
                onSharedMessage(header, message);
                break;
            case RtmpMessage.MESSAGE_CHUNK_SIZE:
                RtmpMessageChunkSize chunkSize = (RtmpMessageChunkSize) message;
                logger.debug("read message chunk size: {}",
                        chunkSize.getChunkSize());
                break;
            case RtmpMessage.MESSAGE_ABORT:
                RtmpMessageAbort abort = (RtmpMessageAbort) message;
                logger.debug("read message abort: {}", abort.getStreamId());
                break;
            case RtmpMessage.MESSAGE_ACK:
                RtmpMessageAck ack = (RtmpMessageAck) message;
                //logger.debug("read message ack: {}", ack.getBytes());
                break;
            case RtmpMessage.MESSAGE_WINDOW_ACK_SIZE:
                RtmpMessageWindowAckSize ackSize = (RtmpMessageWindowAckSize) message;
                logger.debug("read message window ack size: {}",
                        ackSize.getSize());
                break;
            case RtmpMessage.MESSAGE_PEER_BANDWIDTH:
                RtmpMessagePeerBandwidth peer = (RtmpMessagePeerBandwidth) message;
                logger.debug("read message peer bandwidth: {}, {}",
                        peer.getWindowAckSize(), peer.getLimitType());
                break;
            // case RtmpMessage.MESSAGE_AGGREGATE:
            // break;
            case RtmpMessage.MESSAGE_UNKNOWN:
                logger.warn("read message UNKNOWN!");
                break;
            }
        } catch (Exception e) {
            logger.debug("Exception", e);
        }
    }

    public void playStreams() {
        for (NetStream stream : streams.values()) {
            try {
                stream.doPlay();
            } catch (IOException e) {
            }
        }
    }

    public void close() {
        for (NetStream stream : streams.values()) {
            closeStream(stream);
        }
        streams.clear();
    }

    public NetStream createStream() {
        int id = 1;
        for (int len = streams.size(); id <= len; id++) {
            if (streams.get(id) == null)
                break;
        }
        NetStream stream = new NetStream(rtmp, id);
        streams.put(id, stream);
        return stream;
    }

    private void closeStream(NetStream stream) {
        stream.close();
        streams.remove(stream.getStreamId());
    }
}
