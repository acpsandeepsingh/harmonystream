package com.sansoft.harmonystram;

import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
        return makeRequest(request);
    }

    @Override
    public Response post(Request request) throws IOException, ReCaptchaException {
        return makeRequest(request);
    }

    private Response makeRequest(Request request) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(request.url()).openConnection();
        connection.setRequestMethod(request.httpMethod());
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);

        // Set Headers
        Map<String, List<String>> headers = request.headers();
        if (headers != null) {
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                if (entry.getValue() == null) continue;
                for (String value : entry.getValue()) {
                    connection.addRequestProperty(entry.getKey(), value);
                }
            }
        }

        // Handle POST/PUT data
        byte[] dataToSend = request.dataToSend();
        if (dataToSend != null && dataToSend.length > 0) {
            connection.setDoOutput(true);
            try (OutputStream os = connection.getOutputStream()) {
                os.write(dataToSend);
            }
        }

        connection.connect();
        int code = connection.getResponseCode();
        String message = connection.getResponseMessage();
        Map<String, List<String>> responseHeaders = connection.getHeaderFields();
        String finalUrl = connection.getURL().toString();
        
        // Handle stream and conversion to String
        InputStream stream = (code >= 200 && code < 400) ? connection.getInputStream() : connection.getErrorStream();
        String body = readAllAsString(stream);
        
        // Exact constructor: (int responseCode, String responseMessage, Map<String, List<String>> responseHeaders, String responseBody, String latestUrl)
        return new Response(code, message, responseHeaders, body, finalUrl);
    }

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
