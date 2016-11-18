package com.ams.server.service.http;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ams.protocol.http.HTTP;
import com.ams.protocol.http.HttpRequest;
import com.ams.protocol.http.HttpResponse;
import com.ams.protocol.http.ServletContext;

public class DefaultServlet {
    private final Logger logger = LoggerFactory.getLogger(DefaultServlet.class);

    private class MapedFile {
        public String contentType;
        public long lastModified;
        public ByteBuffer data;
    }

    private ServletContext context = null;

    public DefaultServlet(ServletContext context) {
        this.context = context;
    }

    public void service(HttpRequest req, HttpResponse res) throws IOException {
        String realPath = null;
        try {
            realPath = context.getRealPath(req.getLocation());
        } catch (Exception e) {
            logger.warn(e.getMessage());
        }
        File file = new File(realPath);
        if (!file.exists()) {
            res.setHttpResult(HTTP.HTTP_NOT_FOUND);
            res.flush();
        } else if (!context.securize(file)) {
            res.setHttpResult(HTTP.HTTP_FORBIDDEN);
            res.flush();
        } else {
            if (!writeFile(req.getLocation(), file, req, res)) {
                res.setHttpResult(HTTP.HTTP_INTERNAL_ERROR);
                res.flush();
            }
        }
    }

    private boolean writeFile(String url, File file, HttpRequest req,
            HttpResponse res) {
        boolean result = true;
        try {
            // open the resource stream
            MapedFile mapedFile = new MapedFile();
            mapedFile.lastModified = file.lastModified();
            mapedFile.contentType = context.getMimeType(file.getName());
            FileInputStream fis = new FileInputStream(file);
            FileChannel fileChannel = fis.getChannel();
            mapedFile.data = fileChannel.map(FileChannel.MapMode.READ_ONLY,
                    0, fileChannel.size());
            fileChannel.close();
            fis.close();

            res.setContentType(mapedFile.contentType);
            res.setLastModified(mapedFile.lastModified);
            res.setHttpResult(HTTP.HTTP_OK);

            // read all bytes and send them
            ByteBuffer data = mapedFile.data.slice();
            res.flush(data);
        } catch (IOException e) {
            result = false;
            logger.warn(e.getMessage());
        }
        return result;
    }

}