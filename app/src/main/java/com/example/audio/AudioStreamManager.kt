package com.example.audio

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioRecord
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import androidx.core.content.ContextCompat
import com.example.model.StreamConfig
import com.example.model.StreamMetrics
import com.example.model.StreamMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class AudioStreamManager(private val context: Context) {

    private val _metrics = MutableStateFlow(StreamMetrics())
    val metrics: StateFlow<StreamMetrics> = _metrics.asStateFlow()

    private var streamJob: Job? = null
    private var statsJob: Job? = null
    private var activeConfig: StreamConfig = StreamConfig()

    private var audioRecord: AudioRecord? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var autoGainControl: AutomaticGainControl? = null

    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var udpSocket: DatagramSocket? = null

    @Volatile
    private var isMuted = false

    @Volatile
    private var totalBytesTransmitted = 0L

    @Volatile
    private var streamingStartTime = 0L

    fun setMuted(muted: Boolean) {
        isMuted = muted
        _metrics.value = _metrics.value.copy(isMuted = muted)
    }

    fun isCurrentlyMuted(): Boolean = isMuted

    fun updateGain(gain: Float) {
        activeConfig = activeConfig.copy(gainMultiplier = gain.coerceIn(0.1f, 5.0f))
    }

    @Synchronized
    fun start(config: StreamConfig, scope: CoroutineScope) {
        if (_metrics.value.isRunning) return

        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            _metrics.value = StreamMetrics(
                errorMessage = "Permissão de microfone (RECORD_AUDIO) não concedida."
            )
            return
        }

        activeConfig = config
        totalBytesTransmitted = 0L
        streamingStartTime = System.currentTimeMillis()

        _metrics.value = StreamMetrics(
            isRunning = true,
            isListening = true,
            isMuted = isMuted,
            errorMessage = null
        )

        streamJob = scope.launch(Dispatchers.IO) {
            runStreamingPipeline(config)
        }

        statsJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                if (_metrics.value.isRunning && streamingStartTime > 0L) {
                    val duration = (System.currentTimeMillis() - streamingStartTime) / 1000
                    _metrics.value = _metrics.value.copy(
                        durationSeconds = duration,
                        bytesSent = totalBytesTransmitted
                    )
                }
                delay(500)
            }
        }
    }

    @Synchronized
    fun stop() {
        streamJob?.cancel()
        statsJob?.cancel()
        streamJob = null
        statsJob = null

        cleanupHardware()

        _metrics.value = StreamMetrics(
            isRunning = false,
            isListening = false,
            isConnected = false,
            isMuted = isMuted,
            errorMessage = null
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun runStreamingPipeline(config: StreamConfig) {
        val minBufferSize = AudioRecord.getMinBufferSize(
            config.sampleRate,
            config.channelConfig,
            config.audioFormat
        )

        if (minBufferSize <= 0) {
            _metrics.value = _metrics.value.copy(
                isRunning = false,
                errorMessage = "Configuração de áudio não suportada pelo hardware do aparelho."
            )
            return
        }

        // Calculate chunk size according to preset latency
        val bytesPerMs = (config.sampleRate * config.bytesPerSample) / 1000
        val targetChunkBytes = max(minBufferSize, bytesPerMs * config.bufferPreset.millis)
        val readBufferSize = min(targetChunkBytes, 8192)

        try {
            audioRecord = AudioRecord(
                config.audioSource,
                config.sampleRate,
                config.channelConfig,
                config.audioFormat,
                targetChunkBytes * 2
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                _metrics.value = _metrics.value.copy(
                    isRunning = false,
                    errorMessage = "Falha ao inicializar AudioRecord. Verifique se outro app está usando o microfone."
                )
                cleanupHardware()
                return
            }

            val sessionId = audioRecord?.audioSessionId ?: 0
            if (sessionId != 0) {
                if (config.enableNoiseSuppressor && NoiseSuppressor.isAvailable()) {
                    noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply { enabled = true }
                }
                if (config.enableEchoCanceler && AcousticEchoCanceler.isAvailable()) {
                    echoCanceler = AcousticEchoCanceler.create(sessionId)?.apply { enabled = true }
                }
                if (config.enableAutoGainControl && AutomaticGainControl.isAvailable()) {
                    autoGainControl = AutomaticGainControl.create(sessionId)?.apply { enabled = true }
                }
            }

            audioRecord?.startRecording()

            when (config.streamMode) {
                StreamMode.TCP_SERVER -> streamTcpServer(config, readBufferSize)
                StreamMode.TCP_CLIENT -> streamTcpClient(config, readBufferSize)
                StreamMode.UDP -> streamUdp(config, readBufferSize)
            }
        } catch (e: CancellationException) {
            // Normal coroutine cancellation
        } catch (e: Exception) {
            _metrics.value = _metrics.value.copy(
                isRunning = false,
                errorMessage = "Erro no stream: ${e.localizedMessage ?: e.message}"
            )
        } finally {
            cleanupHardware()
        }
    }

    private suspend fun streamTcpServer(config: StreamConfig, bufferSize: Int) {
        try {
            serverSocket = ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(config.port))
            }
        } catch (e: IOException) {
            _metrics.value = _metrics.value.copy(
                isRunning = false,
                errorMessage = "Não foi possível abrir o porto ${config.port}: ${e.localizedMessage}"
            )
            return
        }

        _metrics.value = _metrics.value.copy(
            isListening = true,
            isConnected = false,
            clientAddress = null
        )

        val byteBuffer = ByteArray(bufferSize)
        val shortBuffer = ShortArray(bufferSize / 2)

        while (serverSocket?.isClosed == false) {
            var client: Socket? = null
            try {
                _metrics.value = _metrics.value.copy(
                    isListening = true,
                    isConnected = false,
                    clientAddress = null
                )

                client = serverSocket?.accept() ?: break
                client.tcpNoDelay = true
                client.sendBufferSize = bufferSize * 4

                val clientAddr = "${client.inetAddress.hostAddress}:${client.port}"
                _metrics.value = _metrics.value.copy(
                    isListening = false,
                    isConnected = true,
                    clientAddress = clientAddr
                )

                val outStream: OutputStream = client.getOutputStream()

                while (client.isConnected && !client.isClosed) {
                    val readShorts = audioRecord?.read(shortBuffer, 0, shortBuffer.size) ?: -1
                    if (readShorts <= 0) {
                        delay(5)
                        continue
                    }

                    processAndCalculateLevels(shortBuffer, readShorts, byteBuffer)

                    outStream.write(byteBuffer, 0, readShorts * 2)
                    outStream.flush()
                    totalBytesTransmitted += (readShorts * 2)
                }
            } catch (e: CancellationException) {
                break
            } catch (e: IOException) {
                // Client disconnected from Ubuntu side, wait for next connection
            } finally {
                try { client?.close() } catch (_: Exception) {}
            }
        }
    }

    private suspend fun streamTcpClient(config: StreamConfig, bufferSize: Int) {
        val shortBuffer = ShortArray(bufferSize / 2)
        val byteBuffer = ByteArray(bufferSize)

        while (true) {
            var socket: Socket? = null
            try {
                _metrics.value = _metrics.value.copy(
                    isListening = true,
                    isConnected = false,
                    clientAddress = "Conectando a ${config.targetHost}:${config.port}…"
                )

                socket = Socket()
                socket.tcpNoDelay = true
                socket.connect(InetSocketAddress(config.targetHost, config.port), 4000)

                val outStream = socket.getOutputStream()
                _metrics.value = _metrics.value.copy(
                    isListening = false,
                    isConnected = true,
                    clientAddress = "${config.targetHost}:${config.port}"
                )

                while (socket.isConnected && !socket.isClosed) {
                    val readShorts = audioRecord?.read(shortBuffer, 0, shortBuffer.size) ?: -1
                    if (readShorts <= 0) {
                        delay(5)
                        continue
                    }

                    processAndCalculateLevels(shortBuffer, readShorts, byteBuffer)

                    outStream.write(byteBuffer, 0, readShorts * 2)
                    outStream.flush()
                    totalBytesTransmitted += (readShorts * 2)
                }
            } catch (e: CancellationException) {
                break
            } catch (e: IOException) {
                _metrics.value = _metrics.value.copy(
                    isConnected = false,
                    clientAddress = "Tentando reconectar a ${config.targetHost}:${config.port}…"
                )
                delay(2000)
            } finally {
                try { socket?.close() } catch (_: Exception) {}
            }
        }
    }

    private suspend fun streamUdp(config: StreamConfig, bufferSize: Int) {
        val shortBuffer = ShortArray(bufferSize / 2)
        val byteBuffer = ByteArray(bufferSize)

        udpSocket = DatagramSocket()
        val targetAddr = InetAddress.getByName(config.targetHost)

        _metrics.value = _metrics.value.copy(
            isListening = false,
            isConnected = true,
            clientAddress = "UDP -> ${config.targetHost}:${config.port}"
        )

        while (udpSocket?.isClosed == false) {
            val readShorts = audioRecord?.read(shortBuffer, 0, shortBuffer.size) ?: -1
            if (readShorts <= 0) {
                delay(5)
                continue
            }

            processAndCalculateLevels(shortBuffer, readShorts, byteBuffer)

            val packet = DatagramPacket(byteBuffer, readShorts * 2, targetAddr, config.port)
            udpSocket?.send(packet)
            totalBytesTransmitted += (readShorts * 2)
        }
    }

    private fun processAndCalculateLevels(
        shortBuffer: ShortArray,
        readShorts: Int,
        byteBuffer: ByteArray
    ) {
        val gain = activeConfig.gainMultiplier
        val muted = isMuted

        var sumSquares = 0.0
        var maxPeak = 0

        // Process audio in-place
        val bb = ByteBuffer.wrap(byteBuffer).order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until readShorts) {
            var sample = shortBuffer[i].toInt()

            if (muted) {
                sample = 0
            } else if (gain != 1.0f) {
                val boosted = (sample * gain).toInt()
                sample = boosted.coerceIn(-32768, 32767)
            }

            bb.putShort(i * 2, sample.toShort())

            val absVal = kotlin.math.abs(sample)
            if (absVal > maxPeak) {
                maxPeak = absVal
            }
            sumSquares += (sample.toDouble() * sample.toDouble())
        }

        // Calculate RMS dBFS
        val rms = sqrt(sumSquares / readShorts)
        val rmsDb = if (rms > 0.0001 && !muted) {
            (20 * log10(rms / 32768.0)).toFloat().coerceIn(-60f, 0f)
        } else {
            -60f
        }

        val peakDb = if (maxPeak > 0 && !muted) {
            (20 * log10(maxPeak.toDouble() / 32768.0)).toFloat().coerceIn(-60f, 0f)
        } else {
            -60f
        }

        val normalized = if (muted) 0f else (rmsDb + 60f) / 60f

        // Build a short history for the waveform visualizer
        val currentHistory = _metrics.value.waveformHistory
        val newWaveHistory = if (currentHistory.size >= 32) {
            currentHistory.drop(1) + normalized
        } else {
            currentHistory + normalized
        }

        _metrics.value = _metrics.value.copy(
            currentRmsDb = rmsDb,
            peakDb = peakDb,
            normalizedLevel = normalized.coerceIn(0f, 1f),
            waveformHistory = newWaveHistory
        )
    }

    private fun cleanupHardware() {
        try {
            audioRecord?.stop()
        } catch (_: Exception) {}
        try {
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null

        try { noiseSuppressor?.release() } catch (_: Exception) {}
        try { echoCanceler?.release() } catch (_: Exception) {}
        try { autoGainControl?.release() } catch (_: Exception) {}
        noiseSuppressor = null
        echoCanceler = null
        autoGainControl = null

        try { serverSocket?.close() } catch (_: Exception) {}
        try { clientSocket?.close() } catch (_: Exception) {}
        try { udpSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        clientSocket = null
        udpSocket = null
    }
}
