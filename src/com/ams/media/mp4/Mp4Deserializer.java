package com.ams.media.mp4;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.ams.media.IMediaDeserializer;
import com.ams.media.MediaSample;
import com.ams.media.mp4.STSD.AudioSampleDescription;
import com.ams.media.mp4.STSD.VideoSampleDescription;
import com.ams.protocol.rtmp.amf.AmfValue;
import com.ams.io.buffer.DataBuffer;
import com.ams.io.ByteBufferInputStream;
import com.ams.io.RandomAccessFileReader;

public class Mp4Deserializer implements IMediaDeserializer {
    private RandomAccessFileReader reader;
    private TRAK videoTrak = null;
    private TRAK audioTrak = null;
    private Mp4Sample[] videoSamples;
    private Mp4Sample[] audioSamples;
    private List<Mp4Sample> samples = new ArrayList<Mp4Sample>();
    private int sampleIndex = 0;
    private long moovPosition = 0;
    private static Comparator<Mp4Sample> sampleTimestampComparator = new Comparator<Mp4Sample>() {
        public int compare(Mp4Sample s, Mp4Sample t) {
            return (int) (s.getTimestamp() - t.getTimestamp());
        }
    };

    public Mp4Deserializer(RandomAccessFileReader reader) {
        this.reader = reader;
        MOOV moov = readMoov(reader);
        TRAK trak = moov.getVideoTrak();
        if (trak != null) {
            videoTrak = trak;
            videoSamples = trak.getAllSamples(MediaSample.MEDIA_VIDEO);
            samples.addAll(Arrays.asList(videoSamples));
        }
        trak = moov.getAudioTrak();
        if (trak != null) {
            audioTrak = trak;
            audioSamples = trak.getAllSamples(MediaSample.MEDIA_AUDIO);
            samples.addAll(Arrays.asList(audioSamples));
        }

        Collections.sort(samples, sampleTimestampComparator);
    }

    private MOOV readMoov(RandomAccessFileReader reader) {
        MOOV moov = null;
        try {
            reader.seek(0);
            for (;;) {
                // find moov box
                BOX.Header header = BOX.readHeader(new ByteBufferInputStream(
                        reader));
                long payloadSize = header.payloadSize;
                if ("moov".equalsIgnoreCase(header.type)) {
                    moovPosition = reader.getPosition();
                    byte[] b = new byte[(int) payloadSize];
                    reader.read(b, 0, b.length);
                    DataInputStream bin = new DataInputStream(
                            new ByteArrayInputStream(b));
                    moov = new MOOV();
                    moov.read(bin);
                    break;
                } else {
                    reader.skip(payloadSize);
                }
            }
        } catch (IOException e) {
            moov = null;
        }
        return moov;
    }

    private ByteBuffer[] readSampleData(Mp4Sample sample) throws IOException {
        reader.seek(sample.getOffset());
        return reader.read(sample.getSize());
    }

    public MediaSample metaData() {
        AmfValue track1 = null;
        if (videoTrak != null) {
            track1 = AmfValue.newEcmaArray();
            track1.put("length", videoTrak.getDuration())
                    .put("timescale", videoTrak.getTimeScale())
                    .put("language", videoTrak.getLanguage())
                    .put("sampledescription",
                            AmfValue.newArray(AmfValue.newEcmaArray().put(
                                    "sampletype", videoTrak.getType())));

        }

        AmfValue track2 = null;
        if (audioTrak != null) {
            track2 = AmfValue.newEcmaArray();
            track2.put("length", audioTrak.getDuration())
                    .put("timescale", audioTrak.getTimeScale())
                    .put("language", audioTrak.getLanguage())
                    .put("sampledescription",
                            AmfValue.newArray(AmfValue.newEcmaArray().put(
                                    "sampletype", audioTrak.getType())));
        }

        AmfValue value = AmfValue.newEcmaArray();
        if (videoTrak != null) {
            VideoSampleDescription videoSd = videoTrak
                    .getVideoSampleDescription();
            value.put("duration", videoTrak.getDurationBySecond())
                    .put("moovPosition", moovPosition)
                    .put("width", videoSd.width)
                    .put("height", videoSd.height)
                    .put("canSeekToEnd",
                            videoSamples[videoSamples.length - 1].isKeyframe())
                    .put("videocodecid", videoTrak.getType())
                    .put("avcprofile", videoSd.getAvcProfile())
                    .put("avclevel", videoSd.getAvcLevel())
                    .put("videoframerate",
                            (float) videoSamples.length
                                    / videoTrak.getDurationBySecond());
        }
        if (audioTrak != null) {
            AudioSampleDescription audioSd = audioTrak
                    .getAudioSampleDescription();
            value.put("audiocodecid", audioTrak.getType())
                    .put("aacaot", audioSd.getAudioCodecType())
                    .put("audiosamplerate", audioSd.sampleRate)
                    .put("audiochannels", audioSd.channelCount);
        }
        value.put("trackinfo", AmfValue.newArray(track1, track2));

        return new MediaSample(MediaSample.MEDIA_META, 0,
                AmfValue.toBinary(AmfValue.array("onMetaData", value)));
    }

    public MediaSample videoHeaderData() {
        if (videoTrak == null)
            return null;
        DataBuffer buf = new DataBuffer();
        buf.put(new byte[] { 0x17, 0x00, 0x00, 0x00, 0x00 });
        buf.put(videoTrak.getVideoDecoderConfigData());
        return new MediaSample(MediaSample.MEDIA_VIDEO, 0, buf);
    }

    public MediaSample audioHeaderData() {
        if (audioTrak == null)
            return null;
        DataBuffer buf = new DataBuffer();
        buf.put(new byte[] { (byte) 0xaf, 0x00 });
        buf.put(audioTrak.getAudioDecoderConfigData());
        return new MediaSample(MediaSample.MEDIA_AUDIO, 0, buf);
    }

    private DataBuffer createVideoTag(Mp4Sample sample) throws IOException {
        DataBuffer buf = new DataBuffer();
        byte type = (byte) (sample.isKeyframe() ? 0x17 : 0x27);
        buf.put(new byte[] { type, 0x01, 0, 0, 0 });
        ByteBuffer[] data = readSampleData(sample);
        buf.write(data);
        return buf;
    }

    private DataBuffer createAudioTag(Mp4Sample sample) throws IOException {
        DataBuffer buf = new DataBuffer();
        buf.put(new byte[] { (byte) 0xaf, 0x01 });
        ByteBuffer[] data = readSampleData(sample);
        buf.write(data);
        return buf;
    }

    public MediaSample seek(long seekTime) {
        Mp4Sample seekSample = videoSamples[0];
        int idx = Collections.binarySearch(samples, new Mp4Sample(
                MediaSample.MEDIA_VIDEO, seekTime, true, 0, 0, 0),
                sampleTimestampComparator);
        int i = (idx >= 0) ? idx : -(idx + 1);
        while (i > 0) {
            seekSample = samples.get(i);
            if (seekSample.isVideo() && seekSample.isKeyframe()) {
                break;
            }
            i--;
        }
        sampleIndex = i;
        return seekSample;
    }

    public MediaSample readNext() throws IOException {
        if (sampleIndex >= samples.size()) {
            throw new EOFException();
        }
        Mp4Sample sample = samples.get(sampleIndex++);
        if (sample.isVideo()) {
            return new MediaSample(MediaSample.MEDIA_VIDEO,
                    sample.getTimestamp(), createVideoTag(sample));
        }
        if (sample.isAudio()) {
            return new MediaSample(MediaSample.MEDIA_AUDIO,
                    sample.getTimestamp(), createAudioTag(sample));
        }
        return null;
    }

    public void close() {
        try {
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
