package com.sansoft.harmonystram

import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.LayoutRes

class PlayerUiBinder(
    private val rootContainer: FrameLayout,
    private val callback: Callback
) {
    interface Callback {
        fun onTogglePlayPause()
        fun onNext()
        fun onPrevious()
        fun onToggleMode()
        fun onSeekTo(positionMs: Int)
        fun onOverlayTapped()
    }

    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable {
        controlsView?.visibility = View.GONE
    }

    private var controlsView: View? = null
    private var seekBar: SeekBar? = null
    private var timeCurrent: TextView? = null
    private var timeDuration: TextView? = null
    private var btnPlay: ImageButton? = null
    private var isSeeking = false

    fun bindAudio(@LayoutRes layoutRes: Int) {
        rootContainer.removeAllViews()
        val view = LayoutInflater.from(rootContainer.context).inflate(layoutRes, rootContainer, false)
        rootContainer.addView(view)
        bindControls(view)
        disableOverlayAutoHide()
    }

    fun bindVideo(overlayLayoutRes: Int, @LayoutRes controlsLayoutRes: Int): FrameLayout {
        rootContainer.removeAllViews()
        val overlay = LayoutInflater.from(rootContainer.context).inflate(overlayLayoutRes, rootContainer, false)
        val overlayFrame = overlay as? FrameLayout
            ?: error("video_overlay root must be FrameLayout")
        rootContainer.addView(overlayFrame)

        val controls = LayoutInflater.from(rootContainer.context).inflate(controlsLayoutRes, overlayFrame, false)
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.BOTTOM
        }
        overlayFrame.addView(controls, params)
        bindControls(controls)
        enableOverlayAutoHide(controls)
        return overlayFrame
    }

    fun updateState(state: PlayerController.State) {
        btnPlay?.setImageResource(if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow)

        if (!isSeeking) {
            val duration = state.durationMs.coerceAtLeast(1)
            seekBar?.max = duration
            seekBar?.progress = state.positionMs.coerceIn(0, duration)
            timeCurrent?.text = formatTime(state.positionMs)
            timeDuration?.text = formatTime(state.durationMs)
        }
    }

    fun showOverlayTemporarily() {
        controlsView?.visibility = View.VISIBLE
        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.postDelayed(hideRunnable, 3_000)
    }

    fun disableOverlayAutoHide() {
        hideHandler.removeCallbacks(hideRunnable)
        controlsView?.visibility = View.VISIBLE
    }

    private fun enableOverlayAutoHide(controls: View) {
        controlsView = controls
        controlsView?.visibility = View.VISIBLE
        val touchListener = View.OnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                callback.onOverlayTapped()
            }
            false
        }
        controls.setOnTouchListener(touchListener)
        showOverlayTemporarily()
    }

    private fun bindControls(view: View) {
        controlsView = view
        btnPlay = view.findViewById(R.id.btnPlay)
        val btnNext: View = view.findViewById(R.id.btnNext)
        val btnPrev: View = view.findViewById(R.id.btnPrev)
        val btnMode: View = view.findViewById(R.id.btnMode)
        seekBar = view.findViewById(R.id.seekBar)
        timeCurrent = view.findViewById(R.id.timeCurrent)
        timeDuration = view.findViewById(R.id.timeDuration)

        btnPlay?.setOnClickListener { callback.onTogglePlayPause() }
        btnNext.setOnClickListener { callback.onNext() }
        btnPrev.setOnClickListener { callback.onPrevious() }
        btnMode.setOnClickListener { callback.onToggleMode() }

        seekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    timeCurrent?.text = formatTime(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                isSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                isSeeking = false
                callback.onSeekTo(seekBar.progress)
            }
        })
    }

    private fun formatTime(ms: Int): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }
}
