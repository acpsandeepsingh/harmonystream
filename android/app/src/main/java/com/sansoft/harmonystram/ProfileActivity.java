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

    private TextView statusText;
    private View loginSection;
    private View signupSection;
    private View settingsSection;

    private EditText loginEmailInput;
    private EditText loginDisplayNameInput;
    private EditText signupEmailInput;
    private EditText signupDisplayNameInput;
    private EditText settingsDisplayNameInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        userSessionStore = new NativeUserSessionStore(this);

        statusText = findViewById(R.id.profile_status_text);
        loginSection = findViewById(R.id.profile_login_section);
        signupSection = findViewById(R.id.profile_signup_section);
        settingsSection = findViewById(R.id.profile_settings_section);

        loginEmailInput = findViewById(R.id.profile_login_email_input);
        loginDisplayNameInput = findViewById(R.id.profile_login_display_name_input);
        signupEmailInput = findViewById(R.id.profile_signup_email_input);
        signupDisplayNameInput = findViewById(R.id.profile_signup_display_name_input);
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

        refreshUi();
    }

    private void showSection(View section) {
        loginSection.setVisibility(section == loginSection ? View.VISIBLE : View.GONE);
        signupSection.setVisibility(section == signupSection ? View.VISIBLE : View.GONE);
        settingsSection.setVisibility(section == settingsSection ? View.VISIBLE : View.GONE);
    }

    private void loginFromForm() {
        String email = safeText(loginEmailInput);
        if (email.isEmpty()) {
            loginEmailInput.setError("Email is required");
            return;
        }

        String displayName = safeText(loginDisplayNameInput);
        if (displayName.isEmpty()) {
            displayName = deriveDisplayName(email);
        }

        userSessionStore.signIn(email, displayName);
        Toast.makeText(this, "Signed in locally", Toast.LENGTH_SHORT).show();
        refreshUi();
        showSection(settingsSection);
    }

    private void signupFromForm() {
        String email = safeText(signupEmailInput);
        if (email.isEmpty()) {
            signupEmailInput.setError("Email is required");
            return;
        }

        String displayName = safeText(signupDisplayNameInput);
        if (displayName.isEmpty()) {
            displayName = deriveDisplayName(email);
        }

        userSessionStore.signIn(email, displayName);
        Toast.makeText(this, "Account created locally", Toast.LENGTH_SHORT).show();
        refreshUi();
        showSection(settingsSection);
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

        userSessionStore.updateDisplayName(displayName);
        Toast.makeText(this, "Display name updated", Toast.LENGTH_SHORT).show();
        refreshUi();
    }

    private void signOut() {
        userSessionStore.signOut();
        Toast.makeText(this, "Signed out", Toast.LENGTH_SHORT).show();
        refreshUi();
        showSection(loginSection);
    }

    private void refreshUi() {
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
