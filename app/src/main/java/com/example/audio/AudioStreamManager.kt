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
import kotlinx.coroutines.flow.update
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
import java.net.SocketException
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class AudioStreamManager(private val context: Context) {

    private val _metrics = MutableStateFlow(StreamMetrics())
    val metrics: StateFlow<StreamMetrics> = _metrics.asStateFlow()

    private val sessionCounter = AtomicLong(0L)
    private var streamJob: Job? = null
    private var statsJob: Job? = null

    @Volatile
    private var activeConfig: StreamConfig = StreamConfig()

    private var audioRecord: AudioRecord? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var autoGainControl: AutomaticGainControl? = null

    private var serverSocket: ServerSocket? = null
    private var activeClientSocket: Socket? = null
    private var outgoingSocket: Socket? = null
    private var udpSocket: DatagramSocket? = null

    @Volatile
    private var isMuted = false

    @Volatile
    private var totalBytesTransmitted = 0L

    @Volatile
    private var streamingStartTime = 0L

    private val isStopping = AtomicBoolean(false)

    // Callbacks for service integration
    var onCaptureFatalError: ((String) -> Unit)? = null
    var onCaptureStateChanged: ((isCapturing: Boolean) -> Unit)? = null

    fun setMuted(muted: Boolean) {
        isMuted = muted
        _metrics.update { it.copy(isMuted = muted) }
    }

    fun isCurrentlyMuted(): Boolean = isMuted

    fun updateGain(gain: Float) {
        activeConfig = activeConfig.copy(gainMultiplier = gain.coerceIn(0.1f, 5.0f))
    }

    @Synchronized
    fun start(config: StreamConfig, scope: CoroutineScope) {
        if (_metrics.value.isRunning && !isStopping.get()) return

        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            _metrics.update {
                StreamMetrics(
                    isRunning = false,
                    isListening = false,
                    isConnected = false,
                    errorMessage = "Permissão de microfone (RECORD_AUDIO) não concedida."
                )
            }
            onCaptureFatalError?.invoke("Permissão de microfone não concedida.")
            return
        }

        // Cancel any pending previous session and clean up
        stopInternal(clearError = false)

        val sessionId = sessionCounter.incrementAndGet()
        isStopping.set(false)
        val sanitizedPort = StreamConfig.sanitizePort(config.port)
        activeConfig = config.copy(port = sanitizedPort)
        totalBytesTransmitted = 0L
        streamingStartTime = System.currentTimeMillis()

        _metrics.update {
            StreamMetrics(
                isRunning = true,
                isListening = true,
                isConnected = false,
                isMuted = isMuted,
                errorMessage = null
            )
        }

        streamJob = scope.launch(Dispatchers.IO) {
            runStreamingPipeline(activeConfig, sessionId)
        }

        statsJob = scope.launch(Dispatchers.Default) {
            while (isActive && !isStopping.get() && sessionId == sessionCounter.get()) {
                if (_metrics.value.isRunning && streamingStartTime > 0L) {
                    val duration = (System.currentTimeMillis() - streamingStartTime) / 1000
                    _metrics.update { current ->
                        current.copy(
                            durationSeconds = duration,
                            bytesSent = totalBytesTransmitted
                        )
                    }
                }
                delay(500)
            }
        }
    }

    @Synchronized
    fun stop() {
        stopInternal(clearError = true)
    }

    private fun stopInternal(clearError: Boolean) {
        sessionCounter.incrementAndGet() // invalidate session
        isStopping.set(true)

        streamJob?.cancel()
        statsJob?.cancel()
        streamJob = null
        statsJob = null

        cleanupHardware()
        onCaptureStateChanged?.invoke(false)

        _metrics.update { current ->
            StreamMetrics(
                isRunning = false,
                isListening = false,
                isConnected = false,
                isMuted = isMuted,
                errorMessage = if (clearError) null else current.errorMessage
            )
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun runStreamingPipeline(config: StreamConfig, sessionId: Long) {
        val minBufferSize = AudioRecord.getMinBufferSize(
            config.sampleRate,
            config.channelConfig,
            config.audioFormat
        )

        if (minBufferSize <= 0) {
            handleFatalError(
                sessionId,
                "Taxa de amostragem (${config.sampleRate}Hz) ou formato de canal não suportado pelo aparelho."
            )
            return
        }

        val bytesPerMs = (config.sampleRate * config.bytesPerSample) / 1000
        val targetChunkBytes = max(minBufferSize, bytesPerMs * config.bufferPreset.millis)
        val readBufferSize = min(targetChunkBytes, 8192)

        if (!initializeAudioRecord(config, targetChunkBytes * 2)) {
            handleFatalError(
                sessionId,
                "Falha ao inicializar AudioRecord. O microfone pode estar ocupado por outro app."
            )
            return
        }

        onCaptureStateChanged?.invoke(true)

        try {
            when (config.streamMode) {
                StreamMode.TCP_SERVER -> streamTcpServer(config, readBufferSize, sessionId)
                StreamMode.TCP_CLIENT -> streamTcpClient(config, readBufferSize, sessionId)
                StreamMode.UDP -> streamUdp(config, readBufferSize, sessionId)
            }
        } catch (_: CancellationException) {
            // Normal coroutine cancellation on stop
        } catch (e: Exception) {
            if (sessionId == sessionCounter.get() && !isStopping.get()) {
                handleFatalError(sessionId, "Erro no fluxo de áudio: ${e.localizedMessage ?: e.message}")
            }
        } finally {
            if (sessionId == sessionCounter.get()) {
                cleanupHardware()
                onCaptureStateChanged?.invoke(false)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun initializeAudioRecord(config: StreamConfig, bufferSize: Int): Boolean {
        try {
            cleanupAudioRecordOnly()

            val record = AudioRecord(
                config.audioSource,
                config.sampleRate,
                config.channelConfig,
                config.audioFormat,
                bufferSize
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                return false
            }

            val sessionId = record.audioSessionId
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

            record.startRecording()
            audioRecord = record
            return true
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * Reads audio samples with strict error handling, non-busy waits, and limited self-recovery.
     */
    private suspend fun readAudioSamples(
        shortBuffer: ShortArray,
        sessionId: Long,
        config: StreamConfig,
        bufferSize: Int
    ): Int {
        var recoveryAttempts = 0
        val maxRecoveryAttempts = 3

        while (!isStopping.get() && sessionId == sessionCounter.get()) {
            val record = audioRecord
            if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
                return AudioRecord.ERROR_INVALID_OPERATION
            }

            val readResult = record.read(shortBuffer, 0, shortBuffer.size)

            when {
                readResult > 0 -> {
                    // Positive samples read successfully
                    return readResult
                }
                readResult == 0 -> {
                    // Non-busy wait: audio hardware hasn't filled chunk yet
                    delay(5)
                    continue
                }
                else -> {
                    // Negative error code returned by AudioRecord
                    val errorDesc = interpretAudioRecordError(readResult)

                    if (recoveryAttempts < maxRecoveryAttempts) {
                        recoveryAttempts++
                        delay(100)
                        val reinitialized = initializeAudioRecord(config, bufferSize * 2)
                        if (reinitialized) {
                            continue
                        }
                    }

                    // Fatal capture error
                    handleFatalError(sessionId, "Falha na captura do microfone: $errorDesc (código $readResult)")
                    return readResult
                }
            }
        }
        return -1
    }

    private fun interpretAudioRecordError(errorCode: Int): String {
        return when (errorCode) {
            AudioRecord.ERROR_INVALID_OPERATION -> "Operação inválida (objeto não inicializado ou microfone ocupado)"
            AudioRecord.ERROR_BAD_VALUE -> "Parâmetro inválido na leitura de áudio"
            AudioRecord.ERROR_DEAD_OBJECT -> "Serviço de áudio do sistema Android finalizado (audioserver dead)"
            AudioRecord.ERROR -> "Erro genérico de E/S no microfone"
            else -> "Erro desconhecido de captura ($errorCode)"
        }
    }

    private fun handleFatalError(sessionId: Long, message: String) {
        if (sessionId != sessionCounter.get()) return

        isStopping.set(true)
        cleanupHardware()
        onCaptureStateChanged?.invoke(false)

        _metrics.update {
            StreamMetrics(
                isRunning = false,
                isListening = false,
                isConnected = false,
                isMuted = isMuted,
                errorMessage = message
            )
        }
        onCaptureFatalError?.invoke(message)
    }

    private suspend fun streamTcpServer(config: StreamConfig, bufferSize: Int, sessionId: Long) {
        val bindAddress = if (config.bindToLoopbackOnly) {
            InetAddress.getByName("127.0.0.1")
        } else {
            InetAddress.getByName("0.0.0.0")
        }

        try {
            serverSocket = ServerSocket().apply {
                reuseAddress = true
                soTimeout = 2000 // 2 second timeout for periodic cancellation checks
                bind(InetSocketAddress(bindAddress, config.port))
            }
        } catch (e: IOException) {
            handleFatalError(sessionId, "Não foi possível abrir o porto ${config.port}: ${e.localizedMessage}")
            return
        }

        _metrics.update {
            it.copy(
                isListening = true,
                isConnected = false,
                clientAddress = null
            )
        }

        val byteBuffer = ByteArray(bufferSize)
        val shortBuffer = ShortArray(bufferSize / 2)

        while (!isStopping.get() && sessionId == sessionCounter.get() && serverSocket?.isClosed == false) {
            var client: Socket? = null
            try {
                _metrics.update {
                    it.copy(
                        isListening = true,
                        isConnected = false,
                        clientAddress = null
                    )
                }

                try {
                    client = serverSocket?.accept() ?: break
                } catch (_: SocketTimeoutException) {
                    continue
                }

                client.tcpNoDelay = true
                client.sendBufferSize = bufferSize * 4
                client.soTimeout = 5000
                activeClientSocket = client

                val clientAddr = "${client.inetAddress.hostAddress}:${client.port}"
                _metrics.update {
                    it.copy(
                        isListening = false,
                        isConnected = true,
                        clientAddress = clientAddr
                    )
                }

                val outStream: OutputStream = client.getOutputStream()

                while (!isStopping.get() && sessionId == sessionCounter.get() && client.isConnected && !client.isClosed) {
                    val readShorts = readAudioSamples(shortBuffer, sessionId, config, bufferSize)
                    if (readShorts <= 0) {
                        if (readShorts < 0 && isStopping.get()) break
                        continue
                    }

                    processAndCalculateLevels(shortBuffer, readShorts, byteBuffer)

                    try {
                        outStream.write(byteBuffer, 0, readShorts * 2)
                        outStream.flush()
                        totalBytesTransmitted += (readShorts * 2)
                    } catch (_: IOException) {
                        // Client closed pipe on Ubuntu side (broken pipe / connection reset)
                        break
                    }
                }
            } catch (_: CancellationException) {
                break
            } catch (_: IOException) {
                // Connection cycle ended; continue listening
            } finally {
                try { client?.close() } catch (_: Exception) {}
                activeClientSocket = null
            }
        }
    }

    private suspend fun streamTcpClient(config: StreamConfig, bufferSize: Int, sessionId: Long) {
        val shortBuffer = ShortArray(bufferSize / 2)
        val byteBuffer = ByteArray(bufferSize)

        while (!isStopping.get() && sessionId == sessionCounter.get()) {
            var socket: Socket? = null
            try {
                _metrics.update {
                    it.copy(
                        isListening = true,
                        isConnected = false,
                        clientAddress = "Conectando a ${config.targetHost}:${config.port}…"
                    )
                }

                socket = Socket()
                socket.tcpNoDelay = true
                socket.soTimeout = 3000
                socket.connect(InetSocketAddress(config.targetHost, config.port), 3000)
                outgoingSocket = socket

                val outStream = socket.getOutputStream()
                _metrics.update {
                    it.copy(
                        isListening = false,
                        isConnected = true,
                        clientAddress = "${config.targetHost}:${config.port}"
                    )
                }

                while (!isStopping.get() && sessionId == sessionCounter.get() && socket.isConnected && !socket.isClosed) {
                    val readShorts = readAudioSamples(shortBuffer, sessionId, config, bufferSize)
                    if (readShorts <= 0) {
                        if (readShorts < 0 && isStopping.get()) break
                        continue
                    }

                    processAndCalculateLevels(shortBuffer, readShorts, byteBuffer)

                    try {
                        outStream.write(byteBuffer, 0, readShorts * 2)
                        outStream.flush()
                        totalBytesTransmitted += (readShorts * 2)
                    } catch (_: IOException) {
                        break
                    }
                }
            } catch (_: CancellationException) {
                break
            } catch (_: IOException) {
                if (isStopping.get() || sessionId != sessionCounter.get()) break
                _metrics.update {
                    it.copy(
                        isConnected = false,
                        clientAddress = "Reconectando a ${config.targetHost}:${config.port}…"
                    )
                }
                delay(2000)
            } finally {
                try { socket?.close() } catch (_: Exception) {}
                outgoingSocket = null
            }
        }
    }

    private suspend fun streamUdp(config: StreamConfig, bufferSize: Int, sessionId: Long) {
        val shortBuffer = ShortArray(bufferSize / 2)
        val byteBuffer = ByteArray(bufferSize)

        udpSocket = DatagramSocket()
        val targetAddr = InetAddress.getByName(config.targetHost)

        _metrics.update {
            it.copy(
                isListening = false,
                isConnected = true,
                clientAddress = "UDP -> ${config.targetHost}:${config.port}"
            )
        }

        while (!isStopping.get() && sessionId == sessionCounter.get() && udpSocket?.isClosed == false) {
            val readShorts = readAudioSamples(shortBuffer, sessionId, config, bufferSize)
            if (readShorts <= 0) {
                if (readShorts < 0 && isStopping.get()) break
                continue
            }

            processAndCalculateLevels(shortBuffer, readShorts, byteBuffer)

            try {
                val packet = DatagramPacket(byteBuffer, readShorts * 2, targetAddr, config.port)
                udpSocket?.send(packet)
                totalBytesTransmitted += (readShorts * 2)
            } catch (_: IOException) {
                if (isStopping.get()) break
            }
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

        _metrics.update { current ->
            val history = current.waveformHistory
            val newHistory = if (history.size >= 32) history.drop(1) + normalized else history + normalized
            current.copy(
                currentRmsDb = rmsDb,
                peakDb = peakDb,
                normalizedLevel = normalized.coerceIn(0f, 1f),
                waveformHistory = newHistory
            )
        }
    }

    private fun cleanupAudioRecordOnly() {
        try { audioRecord?.stop() } catch (_: Exception) {}
        try { audioRecord?.release() } catch (_: Exception) {}
        audioRecord = null

        try { noiseSuppressor?.release() } catch (_: Exception) {}
        try { echoCanceler?.release() } catch (_: Exception) {}
        try { autoGainControl?.release() } catch (_: Exception) {}
        noiseSuppressor = null
        echoCanceler = null
        autoGainControl = null
    }

    @Synchronized
    private fun cleanupHardware() {
        cleanupAudioRecordOnly()

        try { serverSocket?.close() } catch (_: Exception) {}
        try { activeClientSocket?.close() } catch (_: Exception) {}
        try { outgoingSocket?.close() } catch (_: Exception) {}
        try { udpSocket?.close() } catch (_: Exception) {}

        serverSocket = null
        activeClientSocket = null
        outgoingSocket = null
        udpSocket = null
    }
}
