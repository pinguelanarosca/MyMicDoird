package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.GitHubUpdateManager
import com.example.data.PreferencesManager
import com.example.model.GitHubRelease
import com.example.model.StreamConfig
import com.example.model.StreamMetrics
import com.example.model.UpdateState
import com.example.service.MicStreamService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsManager = PreferencesManager(application)
    private val updateManager = GitHubUpdateManager(application)

    val config: StateFlow<StreamConfig> = prefsManager.configFlow
    val githubRepo: StateFlow<String> = prefsManager.githubRepoFlow
    val updateState: StateFlow<UpdateState> = updateManager.updateState

    private val _isPushToTalkActive = MutableStateFlow(false)
    val isPushToTalkActive: StateFlow<Boolean> = _isPushToTalkActive.asStateFlow()

    private val _liveMetrics = MutableStateFlow(StreamMetrics())
    val liveMetrics: StateFlow<StreamMetrics> = _liveMetrics.asStateFlow()

    init {
        // Reactive observation of the streaming metrics from the foreground service
        viewModelScope.launch {
            while (isActive) {
                val serviceFlow = MicStreamService.getMetricsFlow()
                if (serviceFlow != null) {
                    // Collect reactively until service terminates
                    try {
                        serviceFlow.collect { metrics ->
                            _liveMetrics.value = metrics
                        }
                    } catch (_: Exception) {}
                } else {
                    _liveMetrics.value = StreamMetrics(
                        isRunning = false,
                        isListening = false,
                        isConnected = false
                    )
                    delay(300)
                }
            }
        }
    }

    fun startStreaming() {
        MicStreamService.startStreaming(getApplication())
    }

    fun stopStreaming() {
        MicStreamService.stopStreaming(getApplication())
    }

    fun toggleMute() {
        MicStreamService.toggleMute(getApplication())
    }

    fun setPushToTalk(pressed: Boolean) {
        _isPushToTalkActive.value = pressed
        val manager = MicStreamService.getAudioStreamManager()
        if (manager != null) {
            // When PTT is pressed, unmuted; when released, muted
            manager.setMuted(!pressed)
        }
    }

    fun updateConfig(newConfig: StreamConfig) {
        prefsManager.saveConfig(newConfig)
        MicStreamService.getAudioStreamManager()?.updateGain(newConfig.gainMultiplier)
    }

    fun updateGain(gain: Float) {
        val updated = config.value.copy(gainMultiplier = gain)
        updateConfig(updated)
    }

    fun saveGitHubRepo(repo: String) {
        prefsManager.saveGitHubRepo(repo)
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            updateManager.checkForUpdates(githubRepo.value)
        }
    }

    fun downloadUpdate(release: GitHubRelease) {
        viewModelScope.launch {
            updateManager.downloadAndPrepareInstall(release)
        }
    }

    fun installApk(apkFile: File) {
        updateManager.promptInstall(apkFile)
    }

    fun resetUpdateState() {
        updateManager.resetState()
    }

    // Helper functions for generating copyable Linux bash commands
    fun getAdbForwardCommand(): String {
        val p = config.value.port
        return "# Se houver multiplos dispositivos ADB (Redroid/Fire Stick), use: adb -s <SERIAL> forward tcp:$p tcp:$p\nadb forward tcp:$p tcp:$p"
    }

    fun getPipeWireCommand(): String {
        val p = config.value.port
        val rate = config.value.sampleRate
        val ch = config.value.channels
        return """
# Configuração do Microfone Virtual (PipeWire 1.6.2+ / PulseAudio no Ubuntu)
adb forward tcp:$p tcp:$p
rm -f /tmp/mymicdoird.fifo && mkfifo /tmp/mymicdoird.fifo
pactl load-module module-pipe-source source_name=MyMicDoird file=/tmp/mymicdoird.fifo format=s16le rate=$rate channels=$ch source_properties="device.description='MyMicDoird_Microphone'"
nc 127.0.0.1 $p > /tmp/mymicdoird.fifo
        """.trimIndent()
    }

    fun getPulseAudioCommand(): String {
        val p = config.value.port
        val rate = config.value.sampleRate
        val ch = config.value.channels
        return """
# Fallback / Compatibilidade Universal PulseAudio / PipeWire
adb forward tcp:$p tcp:$p
rm -f /tmp/mymicdoird.fifo && mkfifo /tmp/mymicdoird.fifo
pactl load-module module-pipe-source source_name=MyMicDoird file=/tmp/mymicdoird.fifo format=s16le rate=$rate channels=$ch source_properties="device.description='MyMicDoird_Microphone'"
nc 127.0.0.1 $p > /tmp/mymicdoird.fifo
        """.trimIndent()
    }

    fun getTestPlaybackCommand(): String {
        val p = config.value.port
        val rate = config.value.sampleRate
        val ch = config.value.channels
        return "adb forward tcp:$p tcp:$p && nc 127.0.0.1 $p | ffplay -nodisp -f s16le -ar $rate -ac $ch -"
    }

    fun getCompleteBashScript(): String {
        val p = config.value.port
        val rate = config.value.sampleRate
        val ch = config.value.channels
        return """
#!/usr/bin/env bash
# ==============================================================================
# MyMicDoird - Script Oficial para Ubuntu / Debian Linux
# Suporta múltiplos dispositivos ADB (Redroid/Fire Stick) e PipeWire 1.6.2+
# ==============================================================================
set -euo pipefail

PORT=$p
RATE=$rate
CHANNELS=$ch
DEVICE_NAME="MyMicDoird"
FIFO="/tmp/mymicdoird.fifo"
MODULE_ID=""

cleanup() {
    echo ""
    echo "[MyMicDoird] Encerrando transmissão e liberando recursos..."
    exec 3>&- 2>/dev/null || true
    if [ -n "${'$'}MODULE_ID" ]; then
        pactl unload-module "${'$'}MODULE_ID" 2>/dev/null || true
    fi
    rm -f "${'$'}FIFO" 2>/dev/null || true
    echo "[MyMicDoird] Concluído."
    exit 0
}

trap cleanup INT TERM EXIT

echo "=========================================================="
echo "          MyMicDoird: Microfone Virtual Ubuntu            "
echo "=========================================================="

# 1. Identificar dispositivo ADB (filtra USB físico ignorando emuladores de rede)
SERIAL="${'$'}{ANDROID_SERIAL:-}"
if [ -z "${'$'}SERIAL" ]; then
    PHYSICAL_DEVICES=${'$'}(adb devices -l | grep -v "List of devices" | grep "device " | grep -v ":" | awk '{print ${'$'}1}' || true)
    COUNT=${'$'}(echo "${'$'}PHYSICAL_DEVICES" | grep -c -v '^${'$'}' || true)
    if [ "${'$'}COUNT" -eq 1 ]; then
        SERIAL=${'$'}(echo "${'$'}PHYSICAL_DEVICES" | tr -d '[:space:]')
        echo "[MyMicDoird] Dispositivo USB selecionado: ${'$'}SERIAL"
    else
        echo "[MyMicDoird] Dispositivo padrão ou especificado via ANDROID_SERIAL"
        SERIAL=${'$'}(adb devices | grep -v "List of devices" | grep -w "device" | awk '{print ${'$'}1}' | head -n 1 || true)
    fi
fi

# 2. Configurar porta ADB
echo "[MyMicDoird] Configurando redirecionamento de porta..."
adb -s "${'$'}SERIAL" forward tcp:${'$'}PORT tcp:${'$'}PORT

# 3. Criar microfone virtual isolado no PipeWire / PulseAudio via module-pipe-source
OLD_IDS=${'$'}(pactl list short modules 2>/dev/null | grep -E "source_name=MyMicDoird|file=/tmp/mymicdoird\.fifo" | awk '{print ${'$'}1}' || true)
if [ -n "${'$'}OLD_IDS" ]; then
    for mid in ${'$'}OLD_IDS; do
        pactl unload-module "${'$'}mid" 2>/dev/null || true
    done
fi

rm -f "${'$'}FIFO"
mkfifo "${'$'}FIFO"
exec 3<> "${'$'}FIFO"

MODULE_ID=${'$'}(pactl load-module module-pipe-source source_name="${'$'}DEVICE_NAME" file="${'$'}FIFO" format=s16le rate=${'$'}RATE channels=${'$'}CHANNELS source_properties="device.description='MyMicDoird_Microphone'" 2>/dev/null || true)

if [ -z "${'$'}MODULE_ID" ] || ! echo "${'$'}MODULE_ID" | grep -qE '^[0-9]+${'$'}'; then
    echo "[ERRO] Falha ao carregar module-pipe-source no PulseAudio/PipeWire!" >&2
    exit 1
fi

echo "[MyMicDoird] Microfone '${'$'}DEVICE_NAME' ativado (ID: ${'$'}MODULE_ID)."

while true; do
    echo "[MyMicDoird] Conectando ao áudio em 127.0.0.1:${'$'}PORT..."
    nc 127.0.0.1 "${'$'}PORT" >&3 || true
    echo "[MyMicDoird] Conexão interrompida. Reconectando em 2 segundos..."
    adb -s "${'$'}SERIAL" forward tcp:${'$'}PORT tcp:${'$'}PORT 2>/dev/null || true
    sleep 2
done
        """.trimIndent()
    }
}
