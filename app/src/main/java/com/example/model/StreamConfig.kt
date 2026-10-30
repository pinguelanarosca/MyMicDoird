package com.example.model

import android.media.AudioFormat
import android.media.MediaRecorder

enum class StreamMode(val displayName: String, val description: String) {
    TCP_SERVER(
        "TCP Servidor (Recomendado via adb forward)",
        "O celular aguarda conexão no porto. No Ubuntu: adb forward tcp:8088 tcp:8088"
    ),
    TCP_CLIENT(
        "TCP Cliente (via adb reverse)",
        "O celular conecta ao Ubuntu. No Ubuntu: adb reverse tcp:8088 tcp:8088 e nc -l -p 8088"
    ),
    UDP(
        "UDP Stream (Ultra Baixa Latência)",
        "Transmissão contínua de pacotes UDP sem confirmação de entrega."
    )
}

enum class BufferPreset(val label: String, val millis: Int) {
    ULTRA_LOW("Ultra Baixa (~20ms)", 20),
    BALANCED("Balanceada (~50ms)", 50),
    STABLE("Estável / Sem Falhas (~100ms)", 100)
}

data class StreamConfig(
    val port: Int = 8088,
    val sampleRate: Int = 48000, // Standard Linux PipeWire / PulseAudio rate
    val channels: Int = 1, // 1 = Mono, 2 = Stereo
    val streamMode: StreamMode = StreamMode.TCP_SERVER,
    val targetHost: String = "127.0.0.1",
    val audioSource: Int = MediaRecorder.AudioSource.MIC,
    val bufferPreset: BufferPreset = BufferPreset.BALANCED,
    val enableNoiseSuppressor: Boolean = true,
    val enableEchoCanceler: Boolean = false,
    val enableAutoGainControl: Boolean = false,
    val gainMultiplier: Float = 1.0f,
    val keepScreenOn: Boolean = true
) {
    val channelConfig: Int
        get() = if (channels == 2) AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO

    val audioFormat: Int
        get() = AudioFormat.ENCODING_PCM_16BIT

    val bytesPerSample: Int
        get() = 2 * channels // 16-bit = 2 bytes per sample per channel
}

data class StreamMetrics(
    val isRunning: Boolean = false,
    val isListening: Boolean = false,
    val isConnected: Boolean = false,
    val isMuted: Boolean = false,
    val clientAddress: String? = null,
    val bytesSent: Long = 0L,
    val durationSeconds: Long = 0L,
    val currentRmsDb: Float = -60f,
    val peakDb: Float = -60f,
    val normalizedLevel: Float = 0f,
    val waveformHistory: List<Float> = emptyList(),
    val errorMessage: String? = null
)
