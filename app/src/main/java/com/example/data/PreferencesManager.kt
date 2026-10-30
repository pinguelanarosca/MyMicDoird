package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.media.MediaRecorder
import com.example.model.BufferPreset
import com.example.model.StreamConfig
import com.example.model.StreamMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("mymicdoird_prefs", Context.MODE_PRIVATE)

    private val _configFlow = MutableStateFlow(loadConfig())
    val configFlow: StateFlow<StreamConfig> = _configFlow.asStateFlow()

    private val _githubRepoFlow = MutableStateFlow(getGitHubRepo())
    val githubRepoFlow: StateFlow<String> = _githubRepoFlow.asStateFlow()

    fun loadConfig(): StreamConfig {
        val rawPort = prefs.getInt(KEY_PORT, 8088)
        val port = StreamConfig.sanitizePort(rawPort)
        val sampleRate = prefs.getInt(KEY_SAMPLE_RATE, 48000)
        val channels = if (prefs.getInt(KEY_CHANNELS, 1) == 2) 2 else 1
        val modeStr = prefs.getString(KEY_MODE, StreamMode.TCP_SERVER.name) ?: StreamMode.TCP_SERVER.name
        val streamMode = try { StreamMode.valueOf(modeStr) } catch (_: Exception) { StreamMode.TCP_SERVER }
        val targetHost = prefs.getString(KEY_HOST, "127.0.0.1") ?: "127.0.0.1"
        val loopbackOnly = prefs.getBoolean(KEY_LOOPBACK_ONLY, true)
        val audioSource = prefs.getInt(KEY_SOURCE, MediaRecorder.AudioSource.MIC)
        val bufferPresetStr = prefs.getString(KEY_BUFFER_PRESET, BufferPreset.BALANCED.name) ?: BufferPreset.BALANCED.name
        val bufferPreset = try { BufferPreset.valueOf(bufferPresetStr) } catch (_: Exception) { BufferPreset.BALANCED }
        val enableNoise = prefs.getBoolean(KEY_NOISE_SUPPR, true)
        val enableEcho = prefs.getBoolean(KEY_ECHO_CANCEL, false)
        val enableAgc = prefs.getBoolean(KEY_AGC, false)
        val gain = prefs.getFloat(KEY_GAIN, 1.0f).coerceIn(0.1f, 5.0f)
        val keepScreen = prefs.getBoolean(KEY_KEEP_SCREEN, true)

        return StreamConfig(
            port = port,
            sampleRate = sampleRate,
            channels = channels,
            streamMode = streamMode,
            targetHost = targetHost,
            bindToLoopbackOnly = loopbackOnly,
            audioSource = audioSource,
            bufferPreset = bufferPreset,
            enableNoiseSuppressor = enableNoise,
            enableEchoCanceler = enableEcho,
            enableAutoGainControl = enableAgc,
            gainMultiplier = gain,
            keepScreenOn = keepScreen
        )
    }

    fun saveConfig(config: StreamConfig) {
        val safePort = StreamConfig.sanitizePort(config.port)
        prefs.edit().apply {
            putInt(KEY_PORT, safePort)
            putInt(KEY_SAMPLE_RATE, config.sampleRate)
            putInt(KEY_CHANNELS, config.channels)
            putString(KEY_MODE, config.streamMode.name)
            putString(KEY_HOST, config.targetHost)
            putBoolean(KEY_LOOPBACK_ONLY, config.bindToLoopbackOnly)
            putInt(KEY_SOURCE, config.audioSource)
            putString(KEY_BUFFER_PRESET, config.bufferPreset.name)
            putBoolean(KEY_NOISE_SUPPR, config.enableNoiseSuppressor)
            putBoolean(KEY_ECHO_CANCEL, config.enableEchoCanceler)
            putBoolean(KEY_AGC, config.enableAutoGainControl)
            putFloat(KEY_GAIN, config.gainMultiplier)
            putBoolean(KEY_KEEP_SCREEN, config.keepScreenOn)
            apply()
        }
        _configFlow.value = config.copy(port = safePort)
    }

    fun getGitHubRepo(): String {
        return prefs.getString(KEY_GITHUB_REPO, DEFAULT_REPO) ?: DEFAULT_REPO
    }

    fun saveGitHubRepo(repo: String) {
        val sanitized = sanitizeRepoName(repo)
        prefs.edit().putString(KEY_GITHUB_REPO, sanitized).apply()
        _githubRepoFlow.value = sanitized
    }

    companion object {
        const val DEFAULT_REPO = "pinguelanarosca/MyMicDoird"

        private const val KEY_PORT = "cfg_port"
        private const val KEY_SAMPLE_RATE = "cfg_sample_rate"
        private const val KEY_CHANNELS = "cfg_channels"
        private const val KEY_MODE = "cfg_mode"
        private const val KEY_HOST = "cfg_host"
        private const val KEY_LOOPBACK_ONLY = "cfg_loopback_only"
        private const val KEY_SOURCE = "cfg_source"
        private const val KEY_BUFFER_PRESET = "cfg_buffer_preset"
        private const val KEY_NOISE_SUPPR = "cfg_noise_suppr"
        private const val KEY_ECHO_CANCEL = "cfg_echo_cancel"
        private const val KEY_AGC = "cfg_agc"
        private const val KEY_GAIN = "cfg_gain"
        private const val KEY_KEEP_SCREEN = "cfg_keep_screen"
        private const val KEY_GITHUB_REPO = "cfg_github_repo"

        fun sanitizeRepoName(repo: String): String {
            return repo.trim()
                .removePrefix("https://github.com/")
                .removePrefix("http://github.com/")
                .removeSuffix("/")
                .removeSuffix(".git")
        }
    }
}
