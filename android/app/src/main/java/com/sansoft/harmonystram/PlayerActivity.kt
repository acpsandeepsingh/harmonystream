package com.sansoft.harmonystram

import android.content.res.Configuration
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity

class PlayerActivity : AppCompatActivity(), PlayerUiBinder.Callback {

    private lateinit var root: FrameLayout
    private lateinit var webView: WebView
    private lateinit var controller: PlayerController
    private lateinit var uiBinder: PlayerUiBinder
    private lateinit var webBridge: PlayerWebBridge

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        root = FrameLayout(this)
        setContentView(root)

        controller = PlayerController()
        uiBinder = PlayerUiBinder(root, this)

        webView = WebView(this)
        val webParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ).apply {
            gravity = Gravity.CENTER
        }
        root.addView(webView, webParams)

        webBridge = PlayerWebBridge(webView) { playing ->
            controller.setPlaying(playing)
        }
        webBridge.configureAndLoad(VIDEO_URL)

        root.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN && controller.state.mode == PlayerController.Mode.VIDEO) {
                uiBinder.showOverlayTemporarily()
            }
            false
        }

        controller.addListener { state ->
            bindLayoutForState(state)
            uiBinder.updateState(state)
            webBridge.setVisible(state.mode == PlayerController.Mode.VIDEO)
            if (state.mode == PlayerController.Mode.VIDEO) {
                uiBinder.showOverlayTemporarily()
            } else {
                uiBinder.disableOverlayAutoHide()
            }
        }

        controller.setProgress(0, DEFAULT_DURATION_MS)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        bindLayoutForState(controller.state)
        uiBinder.updateState(controller.state)
    }

    override fun onTogglePlayPause() {
        controller.togglePlayPause()
        if (controller.state.isPlaying) {
            webBridge.play()
        } else {
            webBridge.pause()
        }
    }

    override fun onNext() {
        webBridge.next()
    }

    override fun onPrevious() {
        webBridge.previous()
    }

    override fun onToggleMode() {
        controller.toggleMode()
    }

    override fun onSeekTo(positionMs: Int) {
        controller.setProgress(positionMs)
        webBridge.seek(positionMs)
    }

    override fun onOverlayTapped() {
        if (controller.state.mode == PlayerController.Mode.VIDEO) {
            uiBinder.showOverlayTemporarily()
        }
    }

    private fun bindLayoutForState(state: PlayerController.State) {
        val controlsLayout = if (isLandscape()) R.layout.player_landscape else R.layout.player_portrait
        if (state.mode == PlayerController.Mode.VIDEO) {
            val overlay = uiBinder.bindVideo(R.layout.video_overlay, controlsLayout)
            ensureWebViewVisibleInContainer(overlay)
        } else {
            uiBinder.bindAudio(controlsLayout)
            ensureWebViewVisibleInContainer(root)
            webView.visibility = View.GONE
        }
    }

    private fun ensureWebViewVisibleInContainer(container: FrameLayout) {
        (webView.parent as? FrameLayout)?.removeView(webView)
        container.addView(webView, 0)
        webView.visibility = View.VISIBLE
    }

    private fun isLandscape(): Boolean {
        return resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    companion object {
        private const val VIDEO_URL = "https://example.com/player"
        private const val DEFAULT_DURATION_MS = 180_000
    }
}
