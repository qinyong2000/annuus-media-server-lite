package com.ams.io.buffer;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface IByteBufferReader {
    public ByteBuffer[] read(int size) throws IOException;
}
