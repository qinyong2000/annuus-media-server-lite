package com.ams.protocol.http;

import java.util.HashMap;

public class MimeTypes {
    public static HashMap<String, String> mimeMap = new HashMap<String, String>();
    static {
        mimeMap.put("", "content/unknown");
        mimeMap.put("gif", "image/gif");
        mimeMap.put("jpe", "image/jpeg");
        mimeMap.put("jpg", "image/jpeg");
        mimeMap.put("jpeg", "image/jpeg");
        mimeMap.put("png", "image/png");
        mimeMap.put("htm", "text/html");
        mimeMap.put("html", "text/html");
        mimeMap.put("text", "text/plain");
        mimeMap.put("txt", "text/plain");
        mimeMap.put("css", "text/css");
        mimeMap.put("xml", "text/xml");

    };

    public static String getContentType(String extension) {
        String contentType = mimeMap.get(extension);
        if (contentType == null)
            contentType = "unkown/unkown";
        return contentType;
    }

}