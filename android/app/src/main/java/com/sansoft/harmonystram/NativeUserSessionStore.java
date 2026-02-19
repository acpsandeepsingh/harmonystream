package com.sansoft.harmonystram;

import android.content.Context;
import android.content.SharedPreferences;

public class NativeUserSessionStore {
    private static final String PREFS_NAME = "native_user_session";
    private static final String KEY_SIGNED_IN = "signed_in";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_DISPLAY_NAME = "display_name";
    private static final String KEY_UID = "uid";
    private static final String KEY_ID_TOKEN = "id_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";

    private final SharedPreferences sharedPreferences;

    public NativeUserSessionStore(Context context) {
        sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public UserSession getSession() {
        boolean signedIn = sharedPreferences.getBoolean(KEY_SIGNED_IN, false);
        String email = sharedPreferences.getString(KEY_EMAIL, "");
        String displayName = sharedPreferences.getString(KEY_DISPLAY_NAME, "");
        String uid = sharedPreferences.getString(KEY_UID, "");
        String idToken = sharedPreferences.getString(KEY_ID_TOKEN, "");
        String refreshToken = sharedPreferences.getString(KEY_REFRESH_TOKEN, "");
        return new UserSession(signedIn, email, displayName, uid, idToken, refreshToken);
    }

    public void signIn(String email, String displayName) {
        signIn(email, displayName, "", "", "");
    }

    public void signIn(String email, String displayName, String uid, String idToken, String refreshToken) {
        sharedPreferences.edit()
                .putBoolean(KEY_SIGNED_IN, true)
                .putString(KEY_EMAIL, email)
                .putString(KEY_DISPLAY_NAME, displayName)
                .putString(KEY_UID, uid)
                .putString(KEY_ID_TOKEN, idToken)
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .apply();
    }

    public void updateDisplayName(String displayName) {
        sharedPreferences.edit()
                .putString(KEY_DISPLAY_NAME, displayName)
                .apply();
    }

    public void signOut() {
        sharedPreferences.edit()
                .putBoolean(KEY_SIGNED_IN, false)
                .putString(KEY_EMAIL, "")
                .putString(KEY_DISPLAY_NAME, "")
                .putString(KEY_UID, "")
                .putString(KEY_ID_TOKEN, "")
                .putString(KEY_REFRESH_TOKEN, "")
                .apply();
    }

    public static class UserSession {
        private final boolean signedIn;
        private final String email;
        private final String displayName;
        private final String uid;
        private final String idToken;
        private final String refreshToken;

        public UserSession(boolean signedIn, String email, String displayName, String uid, String idToken, String refreshToken) {
            this.signedIn = signedIn;
            this.email = email;
            this.displayName = displayName;
            this.uid = uid;
            this.idToken = idToken;
            this.refreshToken = refreshToken;
        }

        public boolean isSignedIn() {
            return signedIn;
        }

        public String getEmail() {
            return email;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getUid() {
            return uid;
        }

        public String getIdToken() {
            return idToken;
        }

        public String getRefreshToken() {
            return refreshToken;
        }
    }
}
