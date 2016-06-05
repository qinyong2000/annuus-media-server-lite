package com.ams.io.buffer;

import java.nio.ByteBuffer;

public interface IByteBufferAllocator {
    ByteBuffer allocate(int size);
}
