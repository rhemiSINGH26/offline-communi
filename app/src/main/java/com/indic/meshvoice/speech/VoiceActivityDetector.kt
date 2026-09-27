package com.indic.meshvoice.speech

import com.indic.meshvoice.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

enum class VadState {
    SILENCE,
    SPEECH_DETECTED,
    SPEAKING,
    SPEECH_ENDED
}

class VoiceActivityDetector(
    private val sampleRate: Int = 16000,
    private val frameSizeSamples: Int = 320, // 20ms frames at 16kHz
    private val silenceThresholdMs: Long = 180L, // Ultra-snappy early trigger cutoff (180ms)
    private val energyThresholdMultiplier: Double = 1.35
) {
    private val tag = "VAD"

    private var noiseFloorEnergy: Double = 180.0
    private var isCalibrated = true
    private var currentEnergy: Double = 0.0
    private var silenceStartTime: Long = 0L
    private var speechStartTime: Long = 0L
    private var totalSpeechDurationMs: Long = 0L

    private val _vadState = MutableStateFlow(VadState.SILENCE)
    val vadState: StateFlow<VadState> = _vadState.asStateFlow()

    private val _isSpeechActive = MutableStateFlow(false)
    val isSpeechActive: StateFlow<Boolean> = _isSpeechActive.asStateFlow()

    private val _liveRmsLevel = MutableStateFlow(0f)
    val liveRmsLevel: StateFlow<Float> = _liveRmsLevel.asStateFlow()

    var onSpeechOnset: (() -> Unit)? = null
    var onSpeechFinalized: ((speechDurationMs: Long) -> Unit)? = null

    fun reset() {
        _vadState.value = VadState.SILENCE
        _isSpeechActive.value = false
        silenceStartTime = 0L
        speechStartTime = 0L
        totalSpeechDurationMs = 0L
        _liveRmsLevel.value = 0f
    }

    fun processPcmFrame(buffer: ByteArray, bytesRead: Int) {
        if (bytesRead < 2) return

        var sumSquare = 0.0
        var zeroCrossings = 0
        var prevSample = 0
        val sampleCount = bytesRead / 2

        for (i in 0 until bytesRead - 1 step 2) {
            val sample = ((buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)).toShort().toInt()
            sumSquare += (sample * sample)

            if ((sample >= 0 && prevSample < 0) || (sample < 0 && prevSample >= 0)) {
                zeroCrossings++
            }
            prevSample = sample
        }

        val frameRms = sqrt(sumSquare / sampleCount)
        currentEnergy = frameRms
        _liveRmsLevel.value = ((frameRms / 6000.0).toFloat()).coerceIn(0f, 1f)

        // Instant dynamic threshold
        val speechEnergyThreshold = (noiseFloorEnergy * energyThresholdMultiplier).coerceIn(240.0, 1800.0)
        val isVoicePresent = frameRms > speechEnergyThreshold

        val now = System.currentTimeMillis()

        when (_vadState.value) {
            VadState.SILENCE -> {
                if (isVoicePresent) {
                    _vadState.value = VadState.SPEECH_DETECTED
                    speechStartTime = now
                    _isSpeechActive.value = true
                    AppLogger.log(tag, "⚡ Instant Speech Onset detected (RMS: ${frameRms.toInt()} vs Thresh: ${speechEnergyThreshold.toInt()})")
                    onSpeechOnset?.invoke()
                    _vadState.value = VadState.SPEAKING
                    silenceStartTime = 0L
                } else {
                    // Update ambient floor smoothly during silence
                    noiseFloorEnergy = (noiseFloorEnergy * 0.92) + (frameRms * 0.08)
                }
            }

            VadState.SPEAKING -> {
                if (isVoicePresent) {
                    silenceStartTime = 0L // Reset silence timer
                } else {
                    if (silenceStartTime == 0L) {
                        silenceStartTime = now
                    } else if (now - silenceStartTime >= silenceThresholdMs) {
                        // VAD Instant Silence Cutoff Triggered!
                        _vadState.value = VadState.SPEECH_ENDED
                        _isSpeechActive.value = false
                        totalSpeechDurationMs = (now - speechStartTime - silenceThresholdMs).coerceAtLeast(300L)
                        AppLogger.log(tag, "⚡ Ultra-Fast VAD Silence Cutoff Triggered (${silenceThresholdMs}ms pause). Speech: ${totalSpeechDurationMs}ms")
                        onSpeechFinalized?.invoke(totalSpeechDurationMs)
                        _vadState.value = VadState.SILENCE
                        silenceStartTime = 0L
                    }
                }
            }

            else -> {}
        }
    }
}
