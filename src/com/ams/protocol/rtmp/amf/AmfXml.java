package com.ams.protocol.rtmp.amf;

public class AmfXml extends AmfValue {
    public AmfXml(String value) {
        this.kind = AMF_XML;
        this.value = value;
    }
}
