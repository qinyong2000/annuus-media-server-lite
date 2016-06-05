package com.ams.io.buffer;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface IByteBufferWriter {
    public void write(ByteBuffer[] data) throws IOException;
}
