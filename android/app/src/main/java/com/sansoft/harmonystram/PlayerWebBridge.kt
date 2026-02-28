package com.sansoft.harmonystram

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Build
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

class PlayerWebBridge(
    private val webView: WebView,
    private val onPlaybackState: (Boolean) -> Unit = {}
) {

    @SuppressLint("SetJavaScriptEnabled")
    fun configureAndLoad(url: String) {
        webView.setBackgroundColor(Color.TRANSPARENT)
        webView.isLongClickable = false
        webView.isHapticFeedbackEnabled = false
        webView.setOnLongClickListener { true }
        webView.overScrollMode = View.OVER_SCROLL_NEVER
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            builtInZoomControls = false
            displayZoomControls = false
            setSupportZoom(false)
            loadWithOverviewMode = true
            useWideViewPort = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = true
            }
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        webView.addJavascriptInterface(AndroidPlayerJsBridge(), "AndroidPlayerBridge")
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, pageUrl: String) {
                injectAndroidPlayerClass()
                injectHideWebControlsStyle()
                registerWebPlaybackHooks()
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                return false
            }
        }

        webView.loadUrl(url)
    }

    fun setVisible(isVisible: Boolean) {
        webView.visibility = if (isVisible) View.VISIBLE else View.GONE
    }

    fun play() = callPlayer("play")

    fun pause() = callPlayer("pause")

    fun next() = callPlayer("next")

    fun previous() = callPlayer("previous")

    fun seek(ms: Int) {
        webView.evaluateJavascript("window.Player && window.Player.seek(${ms.coerceAtLeast(0)});", null)
    }

    private fun callPlayer(functionName: String) {
        webView.evaluateJavascript("window.Player && window.Player.$functionName();", null)
    }

    private fun injectAndroidPlayerClass() {
        webView.evaluateJavascript(
            "document.body && document.body.classList.add('android-player');",
            null
        )
    }

    private fun injectHideWebControlsStyle() {
        val script = """
            (function() {
              if (!document.getElementById('android-player-style')) {
                var style = document.createElement('style');
                style.id = 'android-player-style';
                style.textContent = '\n'
                  + '.android-player [class*=control], .android-player [id*=control], '
                  + '.android-player [class*=Controls], .android-player [id*=Controls], '
                  + '.android-player button, .android-player .ytp-chrome-bottom, '
                  + '.android-player .ytp-chrome-top { display: none !important; }\n'
                  + '.android-player video { width: 100vw !important; height: 100vh !important; object-fit: cover !important; }\n';
                document.head.appendChild(style);
              }
            })();
        """.trimIndent()
        webView.evaluateJavascript(script, null)
    }

    private fun registerWebPlaybackHooks() {
        val script = """
            (function() {
              function bindVideo() {
                var v = document.querySelector('video');
                if (!v) return;
                v.onplay = function() { if (window.AndroidPlayerBridge) AndroidPlayerBridge.onPlaybackState(true); };
                v.onpause = function() { if (window.AndroidPlayerBridge) AndroidPlayerBridge.onPlaybackState(false); };
              }
              bindVideo();
              setTimeout(bindVideo, 1200);
            })();
        """.trimIndent()
        webView.evaluateJavascript(script, null)
    }

    inner class AndroidPlayerJsBridge {
        @JavascriptInterface
        fun onPlaybackState(playing: Boolean) {
            onPlaybackState(playing)
        }
    }
}
