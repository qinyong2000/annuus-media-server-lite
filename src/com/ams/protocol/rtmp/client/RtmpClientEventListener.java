package com.ams.protocol.rtmp.client;

import com.ams.protocol.rtmp.amf.AmfValue;

public interface RtmpClientEventListener {
    public void onResult(AmfValue[] result);
    public void onStatus(AmfValue[] status);
}
