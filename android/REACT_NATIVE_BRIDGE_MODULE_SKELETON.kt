package com.sansoft.harmonystram.bridge

// Example skeleton only. Add React Native deps and register this module in
// your ReactPackage when embedding HarmonyStream inside a RN host app.

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.modules.core.DeviceEventManagerModule

class HarmonyPlaybackModule(
    private val reactContext: ReactApplicationContext
) : ReactContextBaseJavaModule(reactContext) {

    override fun getName(): String = "HarmonyPlayback"

    @ReactMethod
    fun loadMedia(mediaUrl: String, mediaType: String, title: String?, artist: String?) {
        // Forward to PlaybackService ACTION_PLAY + ACTION_SET_MODE.
    }

    @ReactMethod
    fun play() {}

    @ReactMethod
    fun pause() {}

    @ReactMethod
    fun seekTo(positionMs: Double) {}

    private fun emit(eventName: String, payload: com.facebook.react.bridge.WritableMap) {
        reactContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit(eventName, payload)
    }

    fun emitState(playing: Boolean, positionMs: Long, durationMs: Long) {
        val map = Arguments.createMap().apply {
            putBoolean("playing", playing)
            putDouble("positionMs", positionMs.toDouble())
            putDouble("durationMs", durationMs.toDouble())
        }
        emit("HarmonyPlaybackState", map)
    }

    fun emitProgress(positionMs: Long, durationMs: Long) {
        val map = Arguments.createMap().apply {
            putDouble("positionMs", positionMs.toDouble())
            putDouble("durationMs", durationMs.toDouble())
        }
        emit("HarmonyPlaybackProgress", map)
    }

    fun emitCompletion() {
        emit("HarmonyPlaybackCompleted", Arguments.createMap())
    }
}
