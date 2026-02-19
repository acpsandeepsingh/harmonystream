package com.sansoft.harmonystram;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class ProfileActivity extends AppCompatActivity {

    private NativeUserSessionStore userSessionStore;
    private PlaylistSyncManager playlistSyncManager;
    private FirebaseNativeAuthRepository firebaseAuthRepository;
    private volatile boolean syncInProgress = false;

    private TextView statusText;
    private TextView syncStateText;
    private View loginSection;
    private View signupSection;
    private View settingsSection;

    private EditText loginEmailInput;
    private EditText loginDisplayNameInput;
    private EditText loginPasswordInput;
    private EditText signupEmailInput;
    private EditText signupDisplayNameInput;
    private EditText signupPasswordInput;
    private EditText signupConfirmPasswordInput;
    private EditText settingsDisplayNameInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        userSessionStore = new NativeUserSessionStore(this);
        playlistSyncManager = new PlaylistSyncManager(this);
        firebaseAuthRepository = new FirebaseNativeAuthRepository();

        statusText = findViewById(R.id.profile_status_text);
        syncStateText = findViewById(R.id.profile_sync_state_text);
        loginSection = findViewById(R.id.profile_login_section);
        signupSection = findViewById(R.id.profile_signup_section);
        settingsSection = findViewById(R.id.profile_settings_section);

        loginEmailInput = findViewById(R.id.profile_login_email_input);
        loginDisplayNameInput = findViewById(R.id.profile_login_display_name_input);
        loginPasswordInput = findViewById(R.id.profile_login_password_input);
        signupEmailInput = findViewById(R.id.profile_signup_email_input);
        signupDisplayNameInput = findViewById(R.id.profile_signup_display_name_input);
        signupPasswordInput = findViewById(R.id.profile_signup_password_input);
        signupConfirmPasswordInput = findViewById(R.id.profile_signup_confirm_password_input);
        settingsDisplayNameInput = findViewById(R.id.profile_settings_display_name_input);

        Button showLoginButton = findViewById(R.id.btn_profile_show_login);
        Button showSignupButton = findViewById(R.id.btn_profile_show_signup);
        Button showSettingsButton = findViewById(R.id.btn_profile_show_settings);
        Button closeButton = findViewById(R.id.btn_profile_close);

        showLoginButton.setOnClickListener(v -> showSection(loginSection));
        showSignupButton.setOnClickListener(v -> showSection(signupSection));
        showSettingsButton.setOnClickListener(v -> {
            if (!userSessionStore.getSession().isSignedIn()) {
                Toast.makeText(this, "Sign in to manage settings", Toast.LENGTH_SHORT).show();
                return;
            }
            showSection(settingsSection);
        });
        closeButton.setOnClickListener(v -> finish());

        findViewById(R.id.btn_profile_login_submit).setOnClickListener(v -> loginFromForm());
        findViewById(R.id.btn_profile_signup_submit).setOnClickListener(v -> signupFromForm());
        findViewById(R.id.btn_profile_update_display_name).setOnClickListener(v -> updateDisplayName());
        findViewById(R.id.btn_profile_sign_out).setOnClickListener(v -> signOut());
        findViewById(R.id.btn_profile_sync_now).setOnClickListener(v -> runManualSync());

        refreshUi();
    }

    private void refreshSyncState() {
        if (syncInProgress) {
            syncStateText.setText("Sync: in-progress · Loading latest playlists...");
            return;
        }

        syncInProgress = true;
        syncStateText.setText("Sync: in-progress · Loading latest playlists...");
        new Thread(() -> {
            PlaylistSyncModels.SyncStatus status;
            try {
                status = playlistSyncManager.syncNow();
            } catch (Exception error) {
                status = new PlaylistSyncModels.SyncStatus("error", "Sync failed: " + error.getMessage());
            }
            PlaylistSyncModels.SyncStatus finalStatus = status;
            runOnUiThread(() -> {
                syncInProgress = false;
                syncStateText.setText("Sync: " + finalStatus.state + " · " + finalStatus.detail);
            });
        }).start();
    }

    private void showSection(View section) {
        loginSection.setVisibility(section == loginSection ? View.VISIBLE : View.GONE);
        signupSection.setVisibility(section == signupSection ? View.VISIBLE : View.GONE);
        settingsSection.setVisibility(section == settingsSection ? View.VISIBLE : View.GONE);
    }

    private void loginFromForm() {
        String email = safeText(loginEmailInput);
        String password = safeText(loginPasswordInput);
        if (email.isEmpty()) {
            loginEmailInput.setError("Email is required");
            return;
        }
        if (password.isEmpty()) {
            loginPasswordInput.setError("Password is required");
            return;
        }

        String displayName = safeText(loginDisplayNameInput);
        String preferredDisplayName = displayName;
        setBusy(true);
        new Thread(() -> {
            try {
                FirebaseNativeAuthRepository.AuthResult result = firebaseAuthRepository.signIn(email, password);
                String resolvedDisplayName = preferredDisplayName.isEmpty() ? result.displayName : preferredDisplayName;
                userSessionStore.signIn(result.email, resolvedDisplayName, result.uid, result.idToken, result.refreshToken);
                runOnUiThread(() -> {
                    setBusy(false);
                    Toast.makeText(this, "Signed in with Firebase", Toast.LENGTH_SHORT).show();
                    refreshUi();
                    showSection(settingsSection);
                    completeLoginFlow();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    setBusy(false);
                    Toast.makeText(this, "Login failed: " + error.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void signupFromForm() {
        String email = safeText(signupEmailInput);
        String password = safeText(signupPasswordInput);
        String confirmPassword = safeText(signupConfirmPasswordInput);
        if (email.isEmpty()) {
            signupEmailInput.setError("Email is required");
            return;
        }
        if (password.length() < 6) {
            signupPasswordInput.setError("Password must be at least 6 characters");
            return;
        }
        if (!password.equals(confirmPassword)) {
            signupConfirmPasswordInput.setError("Passwords do not match");
            return;
        }

        String displayName = safeText(signupDisplayNameInput);
        if (displayName.isEmpty()) {
            displayName = deriveDisplayName(email);
        }

        String finalDisplayName = displayName;
        setBusy(true);
        new Thread(() -> {
            try {
                FirebaseNativeAuthRepository.AuthResult result = firebaseAuthRepository.signUp(email, password, finalDisplayName);
                userSessionStore.signIn(result.email, result.displayName, result.uid, result.idToken, result.refreshToken);
                runOnUiThread(() -> {
                    setBusy(false);
                    Toast.makeText(this, "Account created in Firebase", Toast.LENGTH_SHORT).show();
                    refreshUi();
                    showSection(settingsSection);
                    completeLoginFlow();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    setBusy(false);
                    Toast.makeText(this, "Signup failed: " + error.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }


    private void completeLoginFlow() {
        syncInProgress = true;
        syncStateText.setText("Sync: in-progress · Loading latest playlists...");
        new Thread(() -> {
            PlaylistSyncModels.SyncStatus status;
            try {
                status = playlistSyncManager.syncNow();
            } catch (Exception error) {
                status = new PlaylistSyncModels.SyncStatus("error", "Sync failed: " + error.getMessage());
            }
            PlaylistSyncModels.SyncStatus finalStatus = status;
            runOnUiThread(() -> {
                syncInProgress = false;
                syncStateText.setText("Sync: " + finalStatus.state + " · " + finalStatus.detail);
                if ("error".equals(finalStatus.state)) {
                    Toast.makeText(this, finalStatus.detail, Toast.LENGTH_LONG).show();
                    return;
                }
                setResult(RESULT_OK);
                finish();
            });
        }).start();
    }

    private void updateDisplayName() {
        if (!userSessionStore.getSession().isSignedIn()) {
            Toast.makeText(this, "Sign in first", Toast.LENGTH_SHORT).show();
            return;
        }

        String displayName = safeText(settingsDisplayNameInput);
        if (displayName.isEmpty()) {
            settingsDisplayNameInput.setError("Display name is required");
            return;
        }

        NativeUserSessionStore.UserSession session = userSessionStore.getSession();
        setBusy(true);
        new Thread(() -> {
            try {
                if (session.getIdToken() != null && !session.getIdToken().isEmpty()) {
                    firebaseAuthRepository.updateDisplayName(session.getIdToken(), displayName);
                }
                userSessionStore.updateDisplayName(displayName);
                runOnUiThread(() -> {
                    setBusy(false);
                    Toast.makeText(this, "Display name updated", Toast.LENGTH_SHORT).show();
                    refreshUi();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    setBusy(false);
                    Toast.makeText(this, "Update failed: " + error.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void signOut() {
        userSessionStore.signOut();
        Toast.makeText(this, "Signed out", Toast.LENGTH_SHORT).show();
        refreshUi();
        showSection(loginSection);
    }

    private void runManualSync() {
        if (syncInProgress) {
            Toast.makeText(this, "Sync already in progress", Toast.LENGTH_SHORT).show();
            return;
        }

        setBusy(true);
        syncInProgress = true;
        syncStateText.setText("Sync: in-progress · Loading latest playlists...");
        new Thread(() -> {
            PlaylistSyncModels.SyncStatus status;
            try {
                status = playlistSyncManager.syncNow();
            } catch (Exception error) {
                status = new PlaylistSyncModels.SyncStatus("error", "Sync failed: " + error.getMessage());
            }
            PlaylistSyncModels.SyncStatus finalStatus = status;
            runOnUiThread(() -> {
                setBusy(false);
                syncInProgress = false;
                syncStateText.setText("Sync: " + finalStatus.state + " · " + finalStatus.detail);
                Toast.makeText(this, "Sync status: " + finalStatus.state, Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    private void setBusy(boolean busy) {
        findViewById(R.id.btn_profile_login_submit).setEnabled(!busy);
        findViewById(R.id.btn_profile_signup_submit).setEnabled(!busy);
        findViewById(R.id.btn_profile_update_display_name).setEnabled(!busy);
        findViewById(R.id.btn_profile_sync_now).setEnabled(!busy);
    }

    private void refreshUi() {
        refreshSyncState();
        NativeUserSessionStore.UserSession session = userSessionStore.getSession();
        if (session.isSignedIn()) {
            String displayName = session.getDisplayName();
            if (displayName == null || displayName.isEmpty()) {
                displayName = session.getEmail();
            }
            statusText.setText("Signed in as " + displayName + " (" + session.getEmail() + ")");
            settingsDisplayNameInput.setText(session.getDisplayName());
            findViewById(R.id.btn_profile_show_settings).setEnabled(true);
            showSection(settingsSection);
        } else {
            statusText.setText("Guest mode active");
            settingsDisplayNameInput.setText("");
            findViewById(R.id.btn_profile_show_settings).setEnabled(false);
            showSection(loginSection);
        }
    }

    private String safeText(EditText input) {
        if (input.getText() == null) return "";
        return input.getText().toString().trim();
    }

    private String deriveDisplayName(String email) {
        int atIndex = email.indexOf("@");
        if (atIndex > 0) {
            return email.substring(0, atIndex);
        }
        return email;
    }
}
