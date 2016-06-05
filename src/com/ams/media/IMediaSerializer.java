package com.ams.media;

import java.io.IOException;

public interface IMediaSerializer {
    public void write(MediaMessage sample) throws IOException;

    public void close();
}
