package com.sansoft.harmonystram;

import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;

public class DownloaderImpl extends Downloader {

    public static DownloaderImpl create() {
        return new DownloaderImpl();
    }

    @Override
    public Response execute(Request request) throws IOException, ReCaptchaException {
        HttpURLConnection connection = (HttpURLConnection) new URL(request.url()).openConnection();
        connection.setRequestMethod(request.httpMethod());
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);

        Map<String, List<String>> headers = request.headers();
        if (headers != null) {
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                if (entry.getValue() == null) continue;
                for (String value : entry.getValue()) {
                    connection.addRequestProperty(entry.getKey(), value);
                }
            }
        }

        connection.connect();
        int code = connection.getResponseCode();
        
        // Use error stream if the request was not successful
        InputStream stream = (code >= 200 && code < 400) ? connection.getInputStream() : connection.getErrorStream();
        
        // 🔥 FIX 1: Read the stream and convert it to a String
        String body = readAllAsString(stream);
        
        // 🔥 FIX 2: The Response constructor now expects a String body
        return new Response(code, connection.getResponseMessage(), connection.getHeaderFields(), body, request.url());
    }

    /**
     * Reads the entire InputStream and converts it to a UTF-8 String.
     * NewPipeExtractor v0.25+ requires a String body in the Response object.
     */
    private static String readAllAsString(InputStream stream) throws IOException {
        if (stream == null) return "";
        
        try (ByteArrayOutputStream result = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = stream.read(buffer)) != -1) {
                result.write(buffer, 0, length);
            }
            return result.toString("UTF-8");
        }
    }
}
