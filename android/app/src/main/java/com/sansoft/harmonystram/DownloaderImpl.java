package com.sansoft.harmonystram;

import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;

public class DownloaderImpl extends Downloader {

    private static final String FALLBACK_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 8.1; Mobile) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

    private static final RequestBody EMPTY_BODY =
            RequestBody.create(new byte[0], (MediaType) null);

    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build();

    public static DownloaderImpl create() {
        return new DownloaderImpl();
    }

    /**
     * Required for GET requests.
     */
    @Override
    public Response execute(Request request) throws IOException, ReCaptchaException {
        return makeRequest(request);
    }

    /**
     * Handles POST requests when the extractor invokes this overload.
     */
    public Response post(Request request) throws IOException, ReCaptchaException {
        return makeRequest(request);
    }

    private Response makeRequest(Request request) throws IOException {
        okhttp3.Request.Builder builder = new okhttp3.Request.Builder()
                .url(request.url());

        // 1. Set headers from extractor request
        Map<String, List<String>> headers = request.headers();
        if (headers != null) {
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                String key = entry.getKey();
                if (key == null || entry.getValue() == null) continue;
                for (String value : entry.getValue()) {
                    if (value != null) builder.addHeader(key, value);
                }
            }
        }

        // 2. Add fallback UA only when caller did not provide one (case-insensitive)
        if (!hasHeaderIgnoreCase(headers, "User-Agent")) {
            builder.header("User-Agent", FALLBACK_USER_AGENT);
        }

        // 3. Attach request body if extractor supplied one
        byte[] dataToSend = request.dataToSend();
        RequestBody requestBody = null;
        if (dataToSend != null && dataToSend.length > 0) {
            requestBody = RequestBody.create(dataToSend, (MediaType) null);
        }

        String method = normalizeHttpMethod(request.httpMethod());
        if (requiresRequestBody(method) && requestBody == null) {
            // OkHttp requires a non-null body for methods like POST/PUT/PATCH.
            requestBody = EMPTY_BODY;
        }
        if (!permitsRequestBody(method)) {
            requestBody = null;
        }
        builder.method(method, requestBody);

        try (okhttp3.Response response = HTTP_CLIENT.newCall(builder.build()).execute()) {
            // 4. Collect response metadata
            int code = response.code();
            String message = response.message();
            String finalUrl = response.request().url().toString();

            // 5. Read response body as string (expected by extractor Response contract)
            String body = "";
            ResponseBody responseBody = response.body();
            if (responseBody != null) {
                body = responseBody.string();
            }

            // 6. Convert OkHttp headers into NewPipe-compatible map
            Map<String, List<String>> responseHeaders = new LinkedHashMap<>();
            for (String name : response.headers().names()) {
                responseHeaders.put(name, new ArrayList<>(response.headers(name)));
            }

            // 7. Build Response using 5-args constructor:
            // (int code, String message, Map headers, String body, String latestUrl)
            return new Response(code, message, responseHeaders, body, finalUrl);
        }
    }

    private static String normalizeHttpMethod(String method) {
        if (method == null || method.trim().isEmpty()) {
            return "GET";
        }
        return method.trim().toUpperCase(Locale.US);
    }

    private static boolean hasHeaderIgnoreCase(Map<String, List<String>> headers, String target) {
        if (headers == null || target == null) return false;
        for (String key : headers.keySet()) {
            if (key != null && key.equalsIgnoreCase(target)) {
                return true;
            }
        }
        return false;
    }

    private static boolean permitsRequestBody(String method) {
        return !("GET".equals(method) || "HEAD".equals(method));
    }

    private static boolean requiresRequestBody(String method) {
        return "POST".equals(method)
                || "PUT".equals(method)
                || "PATCH".equals(method)
                || "PROPPATCH".equals(method)
                || "REPORT".equals(method);
    }
}
