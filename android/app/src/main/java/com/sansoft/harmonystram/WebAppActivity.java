package com.sansoft.harmonystram;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

public class WebAppActivity extends AppCompatActivity {

    public static final String EXTRA_START_URL = "start_url";
    private static final String HOME_URL = "https://acpsandeepsingh.github.io/harmonystream/";

    private WebView webView;
    private ProgressBar loadingIndicator;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web_app);

        webView = findViewById(R.id.web_app_view);
        loadingIndicator = findViewById(R.id.web_loading_indicator);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new HarmonyWebViewClient());

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            String startUrl = getIntent().getStringExtra(EXTRA_START_URL);
            if (isHttpUrl(startUrl)) {
                webView.loadUrl(startUrl);
            } else {
                webView.loadUrl(HOME_URL);
            }
        }
    }

    private boolean isHttpUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }
        Uri parsed = Uri.parse(url);
        String scheme = parsed.getScheme();
        return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }

    private class HarmonyWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            if (uri == null) {
                return false;
            }

            String scheme = uri.getScheme();
            if (scheme == null) {
                return false;
            }

            // Keep regular web links and YouTube playback inside the app's WebView.
            if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                return false;
            }

            // Block non-web deep links so they don't force-open external apps.
            return true;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            loadingIndicator.setVisibility(View.VISIBLE);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            loadingIndicator.setVisibility(View.GONE);
        }
    }
}
