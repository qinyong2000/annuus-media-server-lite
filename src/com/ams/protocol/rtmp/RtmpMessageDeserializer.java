package com.ams.protocol.rtmp;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

import com.ams.io.buffer.DataBuffer;
import com.ams.io.ByteBufferInputStream;
import com.ams.protocol.rtmp.amf.Amf0Deserializer;
import com.ams.protocol.rtmp.amf.Amf3Deserializer;
import com.ams.protocol.rtmp.amf.AmfException;
import com.ams.protocol.rtmp.amf.AmfSwitchToAmf3Exception;
import com.ams.protocol.rtmp.amf.AmfValue;
import com.ams.protocol.rtmp.message.RtmpMessage;
import com.ams.protocol.rtmp.message.RtmpMessageAbort;
import com.ams.protocol.rtmp.message.RtmpMessageAck;
import com.ams.protocol.rtmp.message.RtmpMessageAudio;
import com.ams.protocol.rtmp.message.RtmpMessageChunkSize;
import com.ams.protocol.rtmp.message.RtmpMessageCommand;
import com.ams.protocol.rtmp.message.RtmpMessageData;
import com.ams.protocol.rtmp.message.RtmpMessagePeerBandwidth;
import com.ams.protocol.rtmp.message.RtmpMessageUnknown;
import com.ams.protocol.rtmp.message.RtmpMessageUserControl;
import com.ams.protocol.rtmp.message.RtmpMessageVideo;
import com.ams.protocol.rtmp.message.RtmpMessageWindowAckSize;

public class RtmpMessageDeserializer {
    private int readChunkSize = 128;
    private Map<Integer, RtmpChunkData> chunkDataMap;
    private ByteBufferInputStream in;

    public RtmpMessageDeserializer(ByteBufferInputStream in, Map<Integer, RtmpChunkData> chunkDataMap) {
        this.in = in;
        this.chunkDataMap = chunkDataMap;
    }

    public RtmpMessage read(RtmpHeader header) throws IOException, RtmpException {
        int chunkStreamId = header.getChunkStreamId();
        RtmpChunkData chunkData = chunkDataMap.get(chunkStreamId);
        if (chunkData == null) {
            chunkData = new RtmpChunkData(header);
            chunkDataMap.put(chunkStreamId, chunkData);
        }
        int remain = chunkData.getRemainBytes();
        if (remain > readChunkSize) {
            // continue to read a chunk
            chunkData.readChunk(in, readChunkSize);
            return null;
        }
        chunkData.readChunk(in, remain);
        // read all chunk data of one message
        chunkDataMap.remove(chunkStreamId);
        RtmpMessage message = null;
        try {
            message = parseChunkData(header, chunkData);
        } catch(AmfException e) {
            throw new RtmpException("Invalid Rtmp Message");
        }
        return message;
    }

    private RtmpMessage parseChunkData(RtmpHeader header, RtmpChunkData chunkData) throws IOException, AmfException {
        DataBuffer data = chunkData.getData();
        ByteBufferInputStream bis = new ByteBufferInputStream(data);
        RtmpMessage message = null;
        switch (header.getType()) {
        case RtmpMessage.MESSAGE_USER_CONTROL:
            int event = bis.read16Bit();
            int streamId = -1;
            int timestamp = -1;
            switch (event) {
            case RtmpMessageUserControl.EVT_STREAM_BEGIN:
            case RtmpMessageUserControl.EVT_STREAM_EOF:
            case RtmpMessageUserControl.EVT_STREAM_DRY:
            case RtmpMessageUserControl.EVT_STREAM_IS_RECORDED:
                streamId = (int) bis.read32Bit();
                break;
            case RtmpMessageUserControl.EVT_SET_BUFFER_LENGTH:
                streamId = (int) bis.read32Bit();
                timestamp = (int) bis.read32Bit(); // buffer length
                break;
            case RtmpMessageUserControl.EVT_PING_REQUEST:
            case RtmpMessageUserControl.EVT_PING_RESPONSE:
                timestamp = (int) bis.read32Bit(); // time stamp
                break;
            default:
                event = RtmpMessageUserControl.EVT_UNKNOW;
            }
            message = new RtmpMessageUserControl(event, streamId, timestamp);
            break;
        case RtmpMessage.MESSAGE_AMF3_COMMAND:
            bis.readByte(); // no used byte, continue to amf0 parsing
        case RtmpMessage.MESSAGE_AMF0_COMMAND: 
            {
                DataInputStream dis = new DataInputStream(bis);
                Amf0Deserializer amf0Deserializer = new Amf0Deserializer(dis);
                Amf3Deserializer amf3Deserializer = new Amf3Deserializer(dis);

                String name = amf0Deserializer.read().string();
                int transactionId = amf0Deserializer.read().integer();
                ArrayList<AmfValue> argArray = new ArrayList<AmfValue>();
                boolean amf3Object = false;
                while (true) {
                    try {
                        if (amf3Object) {
                            argArray.add(amf3Deserializer.read());
                        } else {
                            argArray.add(amf0Deserializer.read());
                        }
                    } catch (AmfSwitchToAmf3Exception e) {
                        amf3Object = true;
                    } catch (IOException e) {
                        break;
                    }
                }
                AmfValue[] args = new AmfValue[argArray.size()];
                argArray.toArray(args);
                message = new RtmpMessageCommand(name, transactionId, args);
            } // end case MESSAGE_AMF0_COMMAND
            break;
        case RtmpMessage.MESSAGE_VIDEO:
            message = new RtmpMessageVideo(data);
            break;
        case RtmpMessage.MESSAGE_AUDIO:
            message = new RtmpMessageAudio(data);
            break;
        case RtmpMessage.MESSAGE_AMF0_DATA:
        case RtmpMessage.MESSAGE_AMF3_DATA:
            message = new RtmpMessageData(data);
            break;
        case RtmpMessage.MESSAGE_CHUNK_SIZE:
            readChunkSize = (int) bis.read32Bit();
            message = new RtmpMessageChunkSize(readChunkSize);
            break;
        case RtmpMessage.MESSAGE_ABORT:
        	message = new RtmpMessageAbort((int) bis.read32Bit());
            break;
        case RtmpMessage.MESSAGE_ACK:
        	message = new RtmpMessageAck((int) bis.read32Bit());
            break;
        case RtmpMessage.MESSAGE_WINDOW_ACK_SIZE:
        	message = new RtmpMessageWindowAckSize((int) bis.read32Bit());
            break;
        case RtmpMessage.MESSAGE_PEER_BANDWIDTH:
            int windowAckSize = (int) bis.read32Bit();
            byte limitType = bis.readByte();
            message = new RtmpMessagePeerBandwidth(windowAckSize, limitType);
            break;
        default:
            message = new RtmpMessageUnknown(header.getType(), data);
            break;
        }
        return message;
    }

    public int getReadChunkSize() {
        return readChunkSize;
    }

    public void setReadChunkSize(int readChunkSize) {
        this.readChunkSize = readChunkSize;
    }
}
