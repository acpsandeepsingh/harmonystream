package com.sansoft.harmonystram;

import android.content.Context;
import android.content.SharedPreferences;

public class NativeUserSessionStore {
    private static final String PREFS_NAME = "native_user_session";
    private static final String KEY_SIGNED_IN = "signed_in";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_DISPLAY_NAME = "display_name";

    private final SharedPreferences sharedPreferences;

    public NativeUserSessionStore(Context context) {
        sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public UserSession getSession() {
        boolean signedIn = sharedPreferences.getBoolean(KEY_SIGNED_IN, false);
        String email = sharedPreferences.getString(KEY_EMAIL, "");
        String displayName = sharedPreferences.getString(KEY_DISPLAY_NAME, "");
        return new UserSession(signedIn, email, displayName);
    }

    public void signIn(String email, String displayName) {
        sharedPreferences.edit()
                .putBoolean(KEY_SIGNED_IN, true)
                .putString(KEY_EMAIL, email)
                .putString(KEY_DISPLAY_NAME, displayName)
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
                .apply();
    }

    public static class UserSession {
        private final boolean signedIn;
        private final String email;
        private final String displayName;

        public UserSession(boolean signedIn, String email, String displayName) {
            this.signedIn = signedIn;
            this.email = email;
            this.displayName = displayName;
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
    }
}
