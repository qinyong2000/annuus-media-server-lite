package com.ams.media.flv;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;

import com.ams.io.buffer.DataBuffer;
import com.ams.io.ByteBufferInputStream;
import com.ams.io.ByteBufferOutputStream;
import com.ams.io.RandomAccessFileReader;
import com.ams.media.IMediaDeserializer;
import com.ams.media.MediaSample;
import com.ams.protocol.rtmp.amf.AmfValue;
import com.ams.io.buffer.ByteBufferFactory;

public class FlvDeserializer implements IMediaDeserializer {
    private RandomAccessFileReader reader;
    private long videoFrames = 0, audioFrames = 0;
    private long videoDataSize = 0, audioDataSize = 0;
    private long lastTimestamp = 0;
    private VideoTag firstVideoTag = null;
    private AudioTag firstAudioTag = null;
    private MetaTag firstMetaTag = null;
    private VideoTag lastVideoTag = null;
    private AudioTag lastAudioTag = null;
    private MetaTag lastMetaTag = null;
    private ArrayList<MediaSample> samples = new ArrayList<MediaSample>();

    private static final byte[] H264_VIDEO_HEADER = { (byte) 0x01, (byte) 0x4d,
            (byte) 0x40, (byte) 0x1e, (byte) 0xff, (byte) 0xe1, (byte) 0x00,
            (byte) 0x17, (byte) 0x67, (byte) 0x4d, (byte) 0x40, (byte) 0x1e,
            (byte) 0x92, (byte) 0x42, (byte) 0x01, (byte) 0x40, (byte) 0x5f,
            (byte) 0xd4, (byte) 0xb0, (byte) 0x80, (byte) 0x00, (byte) 0x01,
            (byte) 0xf4, (byte) 0x80, (byte) 0x00, (byte) 0x75, (byte) 0x30,
            (byte) 0x07, (byte) 0x8b, (byte) 0x17, (byte) 0x24, (byte) 0x01,
            (byte) 0x00, (byte) 0x04, (byte) 0x68, (byte) 0xee, (byte) 0x3c,
            (byte) 0x8 };

    private static final byte[] H264_AUDIO_HEADER = { (byte) 0x12, (byte) 0x10 };

    private class SampleTimestampComparator implements
            java.util.Comparator<MediaSample> {
        public int compare(MediaSample s, MediaSample t) {
            return (int) (s.getTimestamp() - t.getTimestamp());
        }
    };

    public class FlvIndex {
        private MediaSample readTag(ByteBufferInputStream in) {
            try {
                int tagType = in.readByte() & 0xFF;
                int size = in.read24Bit();
                long timestamp = in.read32Bit();
                long offset = in.read32Bit();
                if (size == 0) {
                    return null;
                }
                switch (tagType) {
                case MediaSample.MEDIA_AUDIO:
                    return new AudioTag(timestamp, offset, size);
                case MediaSample.MEDIA_VIDEO:
                    return new VideoTag(timestamp, true, offset, size);
                case MediaSample.MEDIA_META:
                    return new MetaTag(timestamp, offset, size);
                default:
                    return null;
                }
            } catch (IOException e) {
                return null;
            }
        }

        public boolean read(ByteBufferInputStream in) {
            try {
                int b1 = in.readByte() & 0xFF;
                int b2 = in.readByte() & 0xFF;
                int b3 = in.readByte() & 0xFF;
                if (b1 != 'F' || b2 != 'L' || b3 != 'X')
                    return false;
                videoFrames = in.read32Bit();
                audioFrames = in.read32Bit();
                videoDataSize = in.read32Bit();
                audioDataSize = in.read32Bit();
                lastTimestamp = in.read32Bit();
            } catch (IOException e) {
                return false;
            }

            firstVideoTag = (VideoTag) readTag(in);
            firstAudioTag = (AudioTag) readTag(in);
            firstMetaTag = (MetaTag) readTag(in);
            lastVideoTag = (VideoTag) readTag(in);
            lastAudioTag = (AudioTag) readTag(in);
            lastMetaTag = (MetaTag) readTag(in);
            MediaSample sample = null;
            samples.clear();
            while ((sample = readTag(in)) != null) {
                samples.add(sample);
            }
            try {
                getTagParameter();
            } catch (IOException e) {
            }
            return samples.size() > 0;
        }

        public void writeTag(ByteBufferOutputStream out, MediaSample sample)
                throws IOException {
            if (sample == null) {
                out.writeByte(0);
                out.write24Bit(0);
                out.write32Bit(0);
                out.write32Bit(0);
                return;
            }
            out.writeByte(sample.getMediaType());
            out.write24Bit(sample.getSize());
            out.write32Bit(sample.getTimestamp());
            out.write32Bit(sample.getOffset());
        }

        public void write(ByteBufferOutputStream out) {
            try {
                out.writeByte('F');
                out.writeByte('L');
                out.writeByte('X');
                out.write32Bit(videoFrames);
                out.write32Bit(audioFrames);
                out.write32Bit(videoDataSize);
                out.write32Bit(audioDataSize);
                out.write32Bit(lastTimestamp);
                writeTag(out, firstVideoTag);
                writeTag(out, firstAudioTag);
                writeTag(out, firstMetaTag);
                writeTag(out, lastVideoTag);
                writeTag(out, lastAudioTag);
                writeTag(out, lastMetaTag);

                for (MediaSample sample : samples) {
                    writeTag(out, sample);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public FlvDeserializer(RandomAccessFileReader reader) {
        this.reader = reader;
    }

    private MediaSample readSampleData(ByteBufferInputStream in)
            throws IOException, FlvException {
        int tagType = in.readByte() & 0xFF;
        int dataSize = in.read24Bit(); // 24Bit read
        long timestamp = in.read24Bit(); // 24Bit read
        timestamp |= (in.readByte() & 0xFF) << 24; // time stamp extended

        int streamId = in.read24Bit(); // 24Bit read
        ByteBuffer[] data = in.readByteBuffer(dataSize);

        int previousTagSize = (int) in.read32Bit();

        switch (tagType) {
        case 0x08:
            return new AudioTag(timestamp, new DataBuffer(data));
        case 0x09:
            return new VideoTag(timestamp, new DataBuffer(data));
        case 0x12:
            return new MetaTag(timestamp, new DataBuffer(data));
        default:
            throw new FlvException("Invalid FLV tag " + tagType);
        }
    }

    private MediaSample readSampleOffset(RandomAccessFileReader reader)
            throws IOException, FlvException {
        int tagType;
        ByteBufferInputStream in = new ByteBufferInputStream(reader);
        try {
            tagType = in.readByte() & 0xFF;
        } catch (EOFException e) {
            return null;
        }
        int dataSize = in.read24Bit(); // 24Bit read
        long timestamp = in.read24Bit(); // 24Bit read
        timestamp |= (in.readByte() & 0xFF) << 24; // time stamp extended

        int streamId = in.read24Bit(); // 24Bit read
        long offset = reader.getPosition();

        int header = in.readByte();
        boolean keyframe = (header >>> 4) == 1 || header == 0x17;

        reader.seek(offset + dataSize);
        int previousTagSize = (int) in.read32Bit();
        switch (tagType) {
        case 0x08:
            return new AudioTag(timestamp, offset, dataSize);
        case 0x09:
            return new VideoTag(timestamp, keyframe, offset, dataSize);
        case 0x12:
            return new MetaTag(timestamp, offset, dataSize);
        default:
            throw new FlvException("Invalid FLV tag " + tagType);
        }
    }

    private void readData(MediaSample tag) throws IOException {
        reader.seek(tag.getOffset());
        DataBuffer data = new DataBuffer(reader.read(tag.getSize()));
        tag.setData(data);
    }
    
    private void getTagParameter() throws IOException {
        if (firstVideoTag != null) {
            readData(firstVideoTag);
            firstVideoTag.getParameters();
        }
        if (firstAudioTag != null) {
            readData(firstAudioTag);
            firstAudioTag.getParameters();
        }
        if (firstMetaTag != null) {
            readData(firstMetaTag);
            firstMetaTag.getParameters();
        }
    }

    public void readSamples() {
        try {
            reader.seek(0);
            ByteBufferInputStream in = new ByteBufferInputStream(reader);
            FlvHeader.read(in);
            MediaSample tag = null;
            while ((tag = readSampleOffset(reader)) != null) {
                if (tag.isVideo()) {
                    videoFrames++;
                    videoDataSize += tag.getSize();
                    if (firstVideoTag == null)
                        firstVideoTag = (VideoTag) tag;
                    if (tag.isKeyframe())
                        samples.add(tag);
                    lastVideoTag = (VideoTag) tag;
                }
                if (tag.isAudio()) {
                    audioFrames++;
                    audioDataSize += tag.getSize();
                    if (firstAudioTag == null)
                        firstAudioTag = (AudioTag) tag;
                    lastAudioTag = (AudioTag) tag;
                }

                if (tag.isMeta()) {
                    if (firstMetaTag == null)
                        firstMetaTag = (MetaTag) tag;
                    lastMetaTag = (MetaTag) tag;
                }

                lastTimestamp = tag.getTimestamp();
            }

            getTagParameter();
        } catch (Exception e) {
        }
    }

    public MediaSample metaData() {
        AmfValue[] metaData;
        if (firstMetaTag != null
                && "onMetaData".equals(firstMetaTag.getEvent())
                && firstMetaTag.getMetaData() != null) {
            metaData = AmfValue.array("onMetaData", firstMetaTag.getMetaData());
        } else {
            AmfValue value = AmfValue.newEcmaArray();
            float duration = (float) lastTimestamp / 1000;
            value.put("duration", duration);
            if (firstVideoTag != null) {
                value.put("width", firstVideoTag.getWidth())
                        .put("height", firstVideoTag.getHeight())
                        .put("videodatarate",
                                (float) videoDataSize * 8 / duration / 1024)
                        // kBits/sec
                        .put("canSeekToEnd", lastVideoTag.isKeyframe())
                        .put("videocodecid", firstVideoTag.getCodecId())
                        .put("framerate", (float) videoFrames / duration);
            }
            if (firstAudioTag != null) {
                value.put("audiodatarate",
                        (float) audioDataSize * 8 / duration / 1024) // kBits/sec
                        .put("audiocodecid", firstAudioTag.getSoundFormat());
            }
            metaData = AmfValue.array("onMetaData", value);
        }
        return new MediaSample(MediaSample.MEDIA_META, 0,
                AmfValue.toBinary(metaData));
    }

    public MediaSample videoHeaderData() {
        if (firstVideoTag != null && firstVideoTag.isH264Video()) {
            byte[] data = H264_VIDEO_HEADER;
            ByteBuffer[] buf = new ByteBuffer[1];
            buf[0] = ByteBufferFactory.allocate(5 + data.length);
            buf[0].put(new byte[] { 0x17, 0x00, 0x00, 0x00, 0x00 });
            buf[0].put(data);
            buf[0].flip();
            return new MediaSample(MediaSample.MEDIA_VIDEO, 0,
                    new DataBuffer(buf));
        }
        return null;
    }

    public MediaSample audioHeaderData() {
        if (firstAudioTag != null && firstAudioTag.isH264Audio()) {
            byte[] data = H264_AUDIO_HEADER;
            ByteBuffer[] buf = new ByteBuffer[1];
            buf[0] = ByteBufferFactory.allocate(2 + data.length);
            buf[0].put(new byte[] { (byte) 0xaf, 0x00 });
            buf[0].put(data);
            buf[0].flip();
            return new MediaSample(MediaSample.MEDIA_AUDIO, 0,
                    new DataBuffer(buf));
        }
        return null;
    }

    public MediaSample seek(long seekTime) throws IOException {
        MediaSample flvTag = firstVideoTag;
        int idx = Collections.binarySearch(samples, new MediaSample(
                MediaSample.MEDIA_VIDEO, seekTime, true, 0, 0),
                new SampleTimestampComparator());
        int i = (idx >= 0) ? idx : -(idx + 1);
        while (i > 0) {
            flvTag = samples.get(i);
            if (flvTag.isVideo() && flvTag.isKeyframe()) {
                break;
            }
            i--;
        }
        reader.seek(flvTag.getOffset() - 11);
        return flvTag;
    }

    public MediaSample readNext() throws IOException {
        try {
            return readSampleData(new ByteBufferInputStream(reader));
        } catch (Exception e) {
            throw new EOFException();
        }
    }

    public void close() {
        try {
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
