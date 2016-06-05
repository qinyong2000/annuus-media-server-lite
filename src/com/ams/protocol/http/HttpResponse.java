package com.ams.protocol.http;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimeZone;

import com.ams.io.buffer.ByteBufferFactory;
import com.ams.io.ByteBufferOutputStream;

public class HttpResponse {
    private ByteBufferOutputStream out;
    private StringBuilder headerBuffer = new StringBuilder(1024);
    private StringBuilder bodyBuffer = new StringBuilder();

    private String resultHeader;
    private Map<String, String> headers = new LinkedHashMap<String, String>();

    private boolean headerWrote = false;
    private static String NEWLINE = "\r\n";

    private static SimpleDateFormat dateFormatGMT;
    static {
        dateFormatGMT = new SimpleDateFormat("d MMM yyyy HH:mm:ss 'GMT'");
        dateFormatGMT.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    public HttpResponse(ByteBufferOutputStream out) {
        this.out = out;
        init();
    }

    public void clear() {
        headerBuffer = new StringBuilder(1024);
        bodyBuffer = new StringBuilder();
        resultHeader = null;
        headers.clear();
        headerWrote = false;
        init();
    }

    private void init() {
        resultHeader = "HTTP/1.1 200 OK";
        headers.put(HTTP.HEADER_DATE, dateFormatGMT.format(new Date()));
        headers.put(HTTP.HEADER_SERVER, "annuus http server");
        headers.put(HTTP.HEADER_CONTENT_TYPE, "text/html; charset=utf-8");
        headers.put(HTTP.HEADER_CACHE_CONTROL,
                "no-cache, no-store, must-revalidate, private");
        headers.put(HTTP.HEADER_PRAGMA, "no-cache");
    }

    private void putHeader(String data) throws IOException {
        headerBuffer.append(data);
    }

    private void putHeaderValue(String name, String value) throws IOException {
        putHeader(name + ": " + value + NEWLINE);
    }

    public void setHttpResult(int code) {
        StringBuilder message = new StringBuilder();
        message.append("HTTP/1.1 " + code + " ");
        switch (code) {
        case HTTP.HTTP_OK:
            message.append("OK");
            break;
        case HTTP.HTTP_PARTIAL_CONTENT:
            message.append("Partial content");
            break;
        case HTTP.HTTP_BAD_REQUEST:
            message.append("Bad Request");
            break;
        case HTTP.HTTP_NOT_FOUND:
            message.append("Not Found");
            break;
        case HTTP.HTTP_BAD_METHOD:
            message.append("Method Not Allowed");
            break;
        case HTTP.HTTP_LENGTH_REQUIRED:
            message.append("Length Required");
            break;
        case HTTP.HTTP_INTERNAL_ERROR:
        default:
            message.append("Internal Server Error");
            break;
        }
        this.resultHeader = message.toString();
    }

    public void setContentLength(long length) {
        setHeader(HTTP.HEADER_CONTENT_LENGTH, Long.toString(length));
    }

    public void setContentType(String contentType) {
        setHeader(HTTP.HEADER_CONTENT_TYPE, contentType);
    }

    public void setLastModified(long lastModified) {
        setHeader(HTTP.HEADER_LAST_MODIFIED,
                dateFormatGMT.format(new Date(lastModified)));
    }

    public void setKeepAlive(boolean keepAlive) {
        if (keepAlive) {
            setHeader(HTTP.HEADER_CONN_DIRECTIVE, HTTP.CONN_KEEP_ALIVE);
        } else {
            setHeader(HTTP.HEADER_CONN_DIRECTIVE, HTTP.CONN_CLOSE);
        }
    }

    public void setHeader(String name, String value) {
        if (headers.containsKey(name)) {
            headers.remove(name);
        }
        headers.put(name, value);
    }

    public void print(String data) throws IOException {
        bodyBuffer.append(data);
    }

    public void println(String data) throws IOException {
        print(data + NEWLINE);
    }

    public ByteBuffer writeHeader() throws IOException {
        if (headerWrote) {
            return null;
        }
        headerWrote = true;

        // write the headers
        putHeader(resultHeader + NEWLINE);

        // write all headers
        Iterator<String> it = this.headers.keySet().iterator();
        while (it.hasNext()) {
            String name = it.next();
            String value = headers.get(name);
            putHeaderValue(name, value);
        }

        putHeader(NEWLINE);

        // write header to socket channel
        byte[] data = headerBuffer.toString().getBytes("UTF-8");
        ByteBuffer buffer = ByteBufferFactory.allocate(data.length);
        buffer.put(data);
        buffer.flip();
        return buffer;
    }

    public void flush() throws IOException {
        byte[] body = bodyBuffer.toString().getBytes("UTF-8");
        setHeader(HTTP.HEADER_CONTENT_LENGTH, Long.toString(body.length));
        ByteBuffer headerBuffer = writeHeader();

        ByteBuffer bodyBuffer = ByteBufferFactory.allocate(body.length);
        bodyBuffer.put(body);
        bodyBuffer.flip();

        ByteBuffer[] buf = { headerBuffer, bodyBuffer };
        // write to socket
        out.writeByteBuffer(buf);
    }

    public void flush(ByteBuffer data) throws IOException {
        flush(new ByteBuffer[] { data });
    }

    public void flush(ByteBuffer[] data) throws IOException {
        long dataSize = 0;
        // body
        ByteBuffer[] buf = new ByteBuffer[data.length + 1];
        for (int i = 0, len = data.length; i < len; i++) {
            buf[i + 1] = data[i];
            dataSize += data[i].remaining();
        }
        // set length header
        setHeader(HTTP.HEADER_CONTENT_LENGTH, Long.toString(dataSize));

        // header
        ByteBuffer headerBuffer = writeHeader();
        buf[0] = headerBuffer;

        // write to socket
        out.writeByteBuffer(buf);
    }

}