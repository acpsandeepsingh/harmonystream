package com.sansoft.harmonystram

class PlayerController {
    enum class Mode {
        AUDIO,
        VIDEO
    }

    data class State(
        val mode: Mode = Mode.AUDIO,
        val isPlaying: Boolean = false,
        val positionMs: Int = 0,
        val durationMs: Int = 0
    )

    var state: State = State()
        private set

    private val listeners = mutableSetOf<(State) -> Unit>()

    fun addListener(listener: (State) -> Unit) {
        listeners += listener
        listener(state)
    }

    fun removeListener(listener: (State) -> Unit) {
        listeners -= listener
    }

    fun toggleMode() {
        state = state.copy(mode = if (state.mode == Mode.AUDIO) Mode.VIDEO else Mode.AUDIO)
        notifyState()
    }

    fun setPlaying(isPlaying: Boolean) {
        state = state.copy(isPlaying = isPlaying)
        notifyState()
    }

    fun togglePlayPause() {
        setPlaying(!state.isPlaying)
    }

    fun setProgress(positionMs: Int, durationMs: Int = state.durationMs) {
        state = state.copy(positionMs = positionMs.coerceAtLeast(0), durationMs = durationMs.coerceAtLeast(0))
        notifyState()
    }

    private fun notifyState() {
        listeners.forEach { it(state) }
    }
}
