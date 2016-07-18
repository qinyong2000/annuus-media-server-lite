package com.ams.protocol.rtmp.net;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ams.protocol.rtmp.RtmpConnection;
import com.ams.protocol.rtmp.RtmpException;
import com.ams.protocol.rtmp.RtmpHandShake;
import com.ams.protocol.rtmp.RtmpHeader;
import com.ams.protocol.rtmp.amf.AmfValue;
import com.ams.protocol.rtmp.message.RtmpMessage;
import com.ams.protocol.rtmp.message.RtmpMessageAbort;
import com.ams.protocol.rtmp.message.RtmpMessageAck;
import com.ams.protocol.rtmp.message.RtmpMessageChunkSize;
import com.ams.protocol.rtmp.message.RtmpMessageCommand;
import com.ams.protocol.rtmp.message.RtmpMessagePeerBandwidth;
import com.ams.protocol.rtmp.message.RtmpMessageUserControl;
import com.ams.protocol.rtmp.message.RtmpMessageWindowAckSize;

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

    private void onMediaMessage(RtmpHeader header, RtmpMessage message) throws IOException {
        NetStream stream = streams.get(header.getStreamId());
        if (stream == null) {
            logger.warn("Unknown Stream Id: {} in media message", header.getStreamId());
            return;
        }

        StreamPublisher publisher = stream.getPublisher();
        if (publisher == null) {
            return;
        }

        publisher.publish(message.toMediaMessage(header.getTimestamp()));

        if (publisher.isPing()) {
            rtmp.writeProtocolControlMessage(new RtmpMessageAck(publisher.getPingBytes()));
        }
    }

    private void onSharedMessage(RtmpHeader header, RtmpMessage message) {
        // TODO
    }

    private void onCommandMessage(RtmpHeader header, RtmpMessage message)
            throws IOException {
        RtmpMessageCommand command = (RtmpMessageCommand) message;
        // TODO authorization
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
            throws IOException {
        AmfValue amfObject = command.getCommandObject();
        Map<String, AmfValue> obj = amfObject.object();

        String app = obj.get("app").string();
        if (app == null) {
            writeCommandError(header, command, "NetConnection", "Invalid 'Connect' parameters");
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

        writeCommandResult(header, command, properties, info);
    }

    private void onGetStreamLength(RtmpHeader header, RtmpMessageCommand command)
            throws IOException {
        String streamName = command.getCommandParameter(0, "").string();
        // writeCommandResult(header, command, null, 0);
    }

    private void onPlay(RtmpHeader header, RtmpMessageCommand command)
            throws IOException {
        String streamName = command.getCommandParameter(0, "").string();
        int start = command.getCommandParameter(1, -2).integer();
        int duration = command.getCommandParameter(2, -1).integer();
        boolean reset = command.getCommandParameter(3, true).bool();
        NetStream stream = streams.get(header.getStreamId());
        if (stream == null) {
            writeStreamError(header, command, "Invalid 'Play' stream id " + header.getStreamId());
            return;
        }
        stream.setTransactionId(command.getTransactionId());
        logger.debug("play stream: {}", streamName);
        stream.play(context, streamName, start, duration, reset);
    }

    private void onPlay2(RtmpHeader header, RtmpMessageCommand command) throws IOException {
        int startTime = command.getCommandParameter(0, -1).integer();
        String oldStreamName = command.getCommandParameter(1, "").string();
        String streamName = command.getCommandParameter(2, "").string();
        int duration = command.getCommandParameter(3, -1).integer();
        String transition = command.getCommandParameter(4, "switch").string(); // switch or swap
        // TODO switch to new bitrate
    }

    private void onSeek(RtmpHeader header, RtmpMessageCommand command) throws IOException {
        int offset = command.getCommandParameter(0, -1).integer();
        NetStream stream = streams.get(header.getStreamId());
        if (stream == null) {
            writeStreamError(header, command, "Invalid 'Seek' stream id " + header.getStreamId());
            return;
        }
        stream.setTransactionId(command.getTransactionId());
        stream.seek(offset);
    }

    private void onPause(RtmpHeader header, RtmpMessageCommand command) throws IOException {
        boolean pause = command.getCommandParameter(0, false).bool();
        int time = command.getCommandParameter(1, -1).integer();
        NetStream stream = streams.get(header.getStreamId());
        if (stream == null) {
            writeStreamError(header, command, "Invalid 'Pause' stream id " + header.getStreamId());
            return;
        }
        stream.setTransactionId(command.getTransactionId());
        stream.pause(pause, time);
    }

    private void onPublish(RtmpHeader header, RtmpMessageCommand command) throws IOException {
        String publishName = command.getCommandParameter(0, "").string();
        if (PublisherManager.getInstance().getPublisher(publishName) != null) {
            writeStreamError(header, command, "The publish '" + publishName + "' is already used");
            return;
        }

        String type = command.getCommandParameter(1, "").string();
        NetStream stream = streams.get(header.getStreamId());
        if (stream == null) {
            writeStreamError(header, command, "Invalid 'Publish' stream id " + header.getStreamId());
            return;
        }
        logger.debug("publish stream: {}", publishName);
        stream.setTransactionId(command.getTransactionId());
        stream.publish(context, publishName, type);
    }

    private void onReceiveAudio(RtmpHeader header, RtmpMessageCommand command)
            throws IOException {
        boolean flag = command.getCommandParameter(0, false).bool();
        NetStream stream = streams.get(header.getStreamId());
        if (stream == null) {
            writeStreamError(header, command, "Invalid 'ReceiveAudio' stream id " + header.getStreamId());
            return;
        }
        stream.receiveAudio(flag);
    }

    private void onReceiveVideo(RtmpHeader header, RtmpMessageCommand command)
            throws IOException {
        boolean flag = command.getCommandParameter(0, false).bool();
        NetStream stream = streams.get(header.getStreamId());
        if (stream == null) {
            writeStreamError(header, command, "Invalid 'ReceiveVideo' stream id " + header.getStreamId());
            return;
        }
        stream.receiveVideo(flag);
    }

    private void onCreateStream(RtmpHeader header, RtmpMessageCommand command)
            throws IOException {
        NetStream stream = createStream();
        writeCommandResult(header, command, null, stream.getStreamId());
    }

    private void onDeleteStream(RtmpHeader header, RtmpMessageCommand command) throws IOException {
        int streamId = command.getCommandParameter(0, -1).integer();
        NetStream stream = streams.get(streamId);
        if (stream == null) {
            writeStreamError(header, command, "Invalid 'deleteStream' stream id");
            return;
        }
        closeStream(stream);
    }

    private void onCloseStream(RtmpHeader header, RtmpMessageCommand command) throws IOException {
        NetStream stream = streams.get(header.getStreamId());
        if (stream == null) {
            writeStreamError(header, command, "Invalid 'CloseStream' stream id " + header.getStreamId());
            return;
        }
        closeStream(stream);
    }

    private void onCall(RtmpHeader header, RtmpMessageCommand command) {
        // String procedureName = command.getName();
    }

    public void writeCommandResult(RtmpHeader header, RtmpMessageCommand command, AmfValue... result) throws IOException {
        rtmp.writeRtmpMessage(header.getChunkStreamId(), 0, 0,
                new RtmpMessageCommand("_result", command.getTransactionId(), result));
    }

    public void writeCommandResult(RtmpHeader header, RtmpMessageCommand command, Object... result) throws IOException {
        rtmp.writeRtmpMessage(header.getChunkStreamId(), 0, 0,
                new RtmpMessageCommand("_result", command.getTransactionId(), AmfValue.array(result)));
    }
    
    public void writeCommandError(RtmpHeader header, RtmpMessageCommand command,
            String code, String msg) throws IOException {
        AmfValue value = AmfValue.newObject();
        value.put("level", "error")
             .put("code", code)
             .put("details", msg);
        rtmp.writeRtmpMessage(
                header.getChunkStreamId(),
                header.getStreamId(),
                0,
                new RtmpMessageCommand("onStatus", command.getTransactionId(), AmfValue.array(null, value)));
    }

    public void writeStreamError(RtmpHeader header, RtmpMessageCommand command, String msg) throws IOException {
        writeCommandError(header, command, "NetStream.Error", msg);
    }

    public void readAndProcessRtmpMessage() throws IOException, RtmpException {
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
                logger.debug("read message ack: {}", ack.getBytes());
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
