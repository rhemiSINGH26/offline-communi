package com.indic.meshvoice.speech

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Base64
import com.indic.meshvoice.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

/**
 * Ultra-low latency 16kHz PCM Voice Audio Engine with VAD Gatekeeper.
 * Compatible with low-RAM devices (1.5GB RAM safe, <320KB audio buffer cap).
 */
class OfflineVoiceAudioEngine(private val context: Context) {

    private val tag = "VoiceAudioEngine"
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat).coerceAtLeast(4096)
    private val maxAudioBytes = 320000 // 10 seconds max buffer (~312KB RAM budget)

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private val audioBufferStream = ByteArrayOutputStream()

    val vad = VoiceActivityDetector(sampleRate = sampleRate, silenceThresholdMs = 180L)

    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    var onVadEarlyTrigger: ((audioBase64: String, durationMs: Long) -> Unit)? = null
    var onVadAsrTrigger: ((durationMs: Long) -> Unit)? = null
    var isAsrMode: Boolean = false

    init {
        vad.onSpeechFinalized = { durationMs ->
            if (isRecording) {
                AppLogger.log(tag, "⚡ VAD Cutoff triggered (180ms silence). Finalizing after ${durationMs}ms of speech")
                if (isAsrMode) {
                    stopRecordingInternal()
                    onVadAsrTrigger?.invoke(durationMs)
                } else {
                    val base64 = stopRecording()
                    if (base64 != null) {
                        onVadEarlyTrigger?.invoke(base64, durationMs)
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startRecording(asrMode: Boolean = false): Boolean {
        if (isRecording) {
            AppLogger.log(tag, "Already recording voice audio")
            return true
        }
        isAsrMode = asrMode

        try {
            audioBufferStream.reset()
            vad.reset()

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                AppLogger.log(tag, "AudioRecord initialization failed (Check mic permission)")
                audioRecord?.release()
                audioRecord = null
                return false
            }

            audioRecord?.startRecording()
            isRecording = true
            AppLogger.log(tag, "🎙️ Direct 16kHz PCM Voice Recording started with VAD Gatekeeper (Mode: ${if (asrMode) "ASR" else "Walkie-Talkie"})")

            CoroutineScope(Dispatchers.IO).launch {
                val buffer = ByteArray(bufferSize)
                while (isRecording && audioRecord != null) {
                    try {
                        val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                        if (read > 0) {
                            if (audioBufferStream.size() < maxAudioBytes) {
                                audioBufferStream.write(buffer, 0, read)
                            } else {
                                AppLogger.log(tag, "Max audio limit reached (10s). Auto-finalizing...")
                                if (isAsrMode) {
                                    stopRecordingInternal()
                                    onVadAsrTrigger?.invoke(10000L)
                                } else {
                                    val base64 = stopRecording()
                                    if (base64 != null) {
                                        onVadEarlyTrigger?.invoke(base64, 10000L)
                                    }
                                }
                                break
                            }

                            // Process through VAD
                            vad.processPcmFrame(buffer, read)

                            // RMS calculation
                            var sum = 0.0
                            val limit = read - 1
                            var sampleCount = 0
                            for (i in 0 until limit step 2) {
                                val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
                                sum += (sample.toShort() * sample.toShort())
                                sampleCount++
                            }

                            if (sampleCount > 0) {
                                val rms = Math.sqrt(sum / sampleCount)
                                _rmsLevel.value = ((rms / 8000.0).toFloat()).coerceIn(0f, 1f)
                            }
                        }
                    } catch (e: Exception) {
                        AppLogger.log(tag, "Audio record read error: ${e.message}")
                    }
                }
            }
            return true
        } catch (e: Exception) {
            AppLogger.log(tag, "startRecording exception: ${e.message}")
            isRecording = false
            return false
        }
    }

    private fun stopRecordingInternal() {
        isRecording = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            _rmsLevel.value = 0f
            vad.reset()
        } catch (e: Exception) {
            AppLogger.log(tag, "stopRecordingInternal exception: ${e.message}")
        }
    }

    fun stopRecording(): String? {
        stopRecordingInternal()
        val pcmBytes = audioBufferStream.toByteArray()
        AppLogger.log(tag, "Voice recording stopped. Captured ${pcmBytes.size} bytes (${pcmBytes.size / 32000.0}s)")

        if (pcmBytes.isNotEmpty()) {
            return Base64.encodeToString(pcmBytes, Base64.NO_WRAP)
        }
        return null
    }

    fun playPcmAudio(base64Audio: String) {
        CoroutineScope(Dispatchers.IO).launch {
            var audioTrack: AudioTrack? = null
            try {
                val pcmBytes = Base64.decode(base64Audio, Base64.NO_WRAP)
                AppLogger.log(tag, "▶️ Playing received 16kHz PCM voice audio (${pcmBytes.size} bytes)...")

                val trackBufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    audioFormat
                ).coerceAtLeast(pcmBytes.size)

                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(audioFormat)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(trackBufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                audioTrack.setVolume(1.0f)
                audioTrack.play()
                audioTrack.write(pcmBytes, 0, pcmBytes.size)

                val playbackDurationMs = (pcmBytes.size / 32.0).toLong()
                Thread.sleep(playbackDurationMs + 80)
                AppLogger.log(tag, "✅ PCM Voice playback completed successfully")
            } catch (e: Exception) {
                AppLogger.log(tag, "playPcmAudio exception: ${e.message}")
            } finally {
                try {
                    audioTrack?.stop()
                    audioTrack?.release()
                } catch (e: Exception) {}
            }
        }
    }
}
