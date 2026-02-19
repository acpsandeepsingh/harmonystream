package com.sansoft.harmonystram;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class FirebaseNativeAuthRepository {

    public static class AuthResult {
        public final String email;
        public final String displayName;
        public final String uid;
        public final String idToken;
        public final String refreshToken;

        public AuthResult(String email, String displayName, String uid, String idToken, String refreshToken) {
            this.email = email;
            this.displayName = displayName;
            this.uid = uid;
            this.idToken = idToken;
            this.refreshToken = refreshToken;
        }
    }

    public AuthResult signIn(String email, String password) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("email", email);
        payload.put("password", password);
        payload.put("returnSecureToken", true);

        JSONObject response = executeAuthRequest("accounts:signInWithPassword", payload);
        String resolvedEmail = response.optString("email", email);
        String uid = response.optString("localId", "");
        String idToken = response.optString("idToken", "");
        String refreshToken = response.optString("refreshToken", "");

        String displayName = "";
        try {
            displayName = lookupDisplayName(idToken, uid);
        } catch (Exception ignored) {
            int at = resolvedEmail.indexOf("@");
            displayName = at > 0 ? resolvedEmail.substring(0, at) : resolvedEmail;
        }

        return new AuthResult(resolvedEmail, displayName, uid, idToken, refreshToken);
    }

    public AuthResult signUp(String email, String password, String fullName) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("email", email);
        payload.put("password", password);
        payload.put("returnSecureToken", true);

        JSONObject response = executeAuthRequest("accounts:signUp", payload);
        String uid = response.optString("localId", "");
        String idToken = response.optString("idToken", "");
        String refreshToken = response.optString("refreshToken", "");

        updateDisplayName(idToken, fullName);
        upsertUserDocument(uid, email, fullName, idToken);

        return new AuthResult(email, fullName, uid, idToken, refreshToken);
    }

    public void updateDisplayName(String idToken, String displayName) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("idToken", idToken);
        payload.put("displayName", displayName);
        executeAuthRequest("accounts:update", payload);
    }

    private String lookupDisplayName(String idToken, String uid) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("idToken", idToken);
        JSONObject response = executeAuthRequest("accounts:lookup", payload);
        if (response.optJSONArray("users") == null || response.optJSONArray("users").length() == 0) {
            return uid;
        }
        JSONObject user = response.optJSONArray("users").optJSONObject(0);
        if (user == null) return uid;
        String name = user.optString("displayName", "");
        return name.isEmpty() ? uid : name;
    }

    private JSONObject executeAuthRequest(String action, JSONObject payload) throws Exception {
        String endpoint = "https://identitytoolkit.googleapis.com/v1/" + action + "?key=" + BuildConfig.FIREBASE_API_KEY;
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(12000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

        try (OutputStream output = connection.getOutputStream();
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8))) {
            writer.write(payload.toString());
            writer.flush();
        }

        int statusCode = connection.getResponseCode();
        InputStream stream = (statusCode >= 200 && statusCode < 300)
                ? connection.getInputStream()
                : connection.getErrorStream();
        String body = readFully(stream);

        if (statusCode < 200 || statusCode >= 300) {
            String message = "Authentication failed";
            try {
                JSONObject errorBody = new JSONObject(body);
                JSONObject error = errorBody.optJSONObject("error");
                if (error != null) {
                    String apiMessage = error.optString("message", "");
                    if (!apiMessage.isEmpty()) {
                        message = apiMessage;
                    }
                }
            } catch (Exception ignored) {
            }
            throw new IllegalStateException(message);
        }

        return new JSONObject(body);
    }

    private void upsertUserDocument(String uid, String email, String fullName, String idToken) throws Exception {
        String endpoint = "https://firestore.googleapis.com/v1/projects/"
                + BuildConfig.FIREBASE_PROJECT_ID
                + "/databases/(default)/documents/users/"
                + encode(uid)
                + "?key=" + BuildConfig.FIREBASE_API_KEY;

        JSONObject fields = new JSONObject();
        fields.put("id", stringField(uid));
        fields.put("email", stringField(email));
        fields.put("username", stringField(fullName));
        fields.put("themePreference", stringField("system"));
        fields.put("usernameIsSet", boolField(false));
        fields.put("dateJoined", stringField(String.valueOf(System.currentTimeMillis())));

        JSONObject payload = new JSONObject();
        payload.put("fields", fields);

        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setRequestMethod("PATCH");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(12000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        if (idToken != null && !idToken.isEmpty()) {
            connection.setRequestProperty("Authorization", "Bearer " + idToken);
        }

        try (OutputStream output = connection.getOutputStream();
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8))) {
            writer.write(payload.toString());
            writer.flush();
        }

        int statusCode = connection.getResponseCode();
        if (statusCode < 200 || statusCode >= 300) {
            throw new IllegalStateException("Unable to create Firebase user profile document");
        }
    }

    private JSONObject stringField(String value) throws Exception {
        JSONObject object = new JSONObject();
        object.put("stringValue", value == null ? "" : value);
        return object;
    }

    private JSONObject boolField(boolean value) throws Exception {
        JSONObject object = new JSONObject();
        object.put("booleanValue", value);
        return object;
    }

    private String readFully(InputStream inputStream) throws Exception {
        if (inputStream == null) return "";
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }

    private String encode(String value) {
        if (value == null || value.isEmpty()) {
            return "guest";
        }
        return value.replace("%", "%25")
                .replace("/", "%2F")
                .replace("?", "%3F")
                .replace("#", "%23")
                .replace(" ", "%20");
    }
}
