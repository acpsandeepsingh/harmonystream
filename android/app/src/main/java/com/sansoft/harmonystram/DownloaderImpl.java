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
        InputStream stream = (code >= 200 && code < 400) ? connection.getInputStream() : connection.getErrorStream();
        byte[] body = readAll(stream);
        return new Response(code, connection.getResponseMessage(), connection.getHeaderFields(), body, request.url());
    }

    @Override
    public String getCookies(String url) {
        return "";
    }

    private static byte[] readAll(InputStream stream) throws IOException {
        if (stream == null) return new byte[0];
        try (InputStream in = stream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }
}
