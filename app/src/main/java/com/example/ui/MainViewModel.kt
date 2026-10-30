package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioStreamManager
import com.example.data.GitHubUpdateManager
import com.example.data.PreferencesManager
import com.example.model.BufferPreset
import com.example.model.GitHubRelease
import com.example.model.StreamConfig
import com.example.model.StreamMetrics
import com.example.model.StreamMode
import com.example.model.UpdateState
import com.example.service.MicStreamService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsManager = PreferencesManager(application)
    private val updateManager = GitHubUpdateManager(application)

    // Local fallback manager for metering when previewing before service starts
    private val fallbackManager = AudioStreamManager(application)

    val config: StateFlow<StreamConfig> = prefsManager.configFlow
    val githubRepo: StateFlow<String> = prefsManager.githubRepoFlow
    val updateState: StateFlow<UpdateState> = updateManager.updateState

    private val _isPushToTalkActive = MutableStateFlow(false)
    val isPushToTalkActive: StateFlow<Boolean> = _isPushToTalkActive.asStateFlow()

    private val _liveMetrics = MutableStateFlow(StreamMetrics())
    val liveMetrics: StateFlow<StreamMetrics> = _liveMetrics.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                val serviceFlow = MicStreamService.getMetricsFlow()
                if (serviceFlow != null) {
                    _liveMetrics.value = serviceFlow.value
                } else {
                    _liveMetrics.value = StreamMetrics(
                        isRunning = false,
                        isListening = false,
                        isConnected = false
                    )
                }
                kotlinx.coroutines.delay(50)
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
            // When PTT is active, unmuted when pressed, muted when released
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
        return "adb forward tcp:$p tcp:$p"
    }

    fun getPipeWireCommand(): String {
        val p = config.value.port
        val rate = config.value.sampleRate
        val ch = config.value.channels
        val chMap = if (ch == 2) "stereo" else "mono"
        return """
# 1. Encaminhar porta ADB do celular para o Ubuntu
adb forward tcp:$p tcp:$p

# 2. Criar Microfone Virtual nativo no PipeWire (Ubuntu 24.04 LTS / 22.04 LTS)
pactl load-module module-null-sink media.class=Audio/Source/Virtual sink_name=MyMicDoird channel_map=$chMap

# 3. Transmitir audio direto para o dispositivo virtual
nc 127.0.0.1 $p | pw-cat -p --rate=$rate --channels=$ch --format=s16le --target=MyMicDoird
        """.trimIndent()
    }

    fun getPulseAudioCommand(): String {
        val p = config.value.port
        val rate = config.value.sampleRate
        val ch = config.value.channels
        return """
# Fallback para PulseAudio (Ubuntu 20.04 ou anterior)
adb forward tcp:$p tcp:$p
[ -p /tmp/mymicdoird ] || mkfifo /tmp/mymicdoird
pactl load-module module-pipe-source source_name=MyMicDoird file=/tmp/mymicdoird format=s16le rate=$rate channels=$ch
nc 127.0.0.1 $p > /tmp/mymicdoird
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
        val chMap = if (ch == 2) "stereo" else "mono"
        return """
#!/usr/bin/env bash
# ==============================================================================
# MyMicDoird - Script de Inicializacao e Auto-Reconexao para Ubuntu / Debian
# Suporta: PipeWire Nativo (pw-cat), PulseAudio (pacat) e Fallback FIFO
# ==============================================================================
set -euo pipefail

PORT=$p
RATE=$rate
CHANNELS=$ch
CHANNEL_MAP="$chMap"
DEVICE_NAME="MyMicDoird"
MODULE_ID=""

cleanup() {
    echo ""
    echo "[MyMicDoird] Encerrando transmissao e liberando recursos..."
    # Descarrega UNICAMENTE o modulo criado por este script, sem afetar outros apps
    if [ -n "${'$'}MODULE_ID" ]; then
        pactl unload-module "${'$'}MODULE_ID" 2>/dev/null || true
    fi
    rm -f /tmp/mymicdoird 2>/dev/null || true
    echo "[MyMicDoird] Desconectado com sucesso."
    exit 0
}

trap cleanup INT TERM EXIT

echo "=========================================================="
echo "          MyMicDoird: Microfone Virtual Ubuntu            "
echo "=========================================================="

# 1. Verificar presenca do ADB
if ! command -v adb >/dev/null 2>&1; then
    echo "[ERRO] 'adb' nao encontrado. Instale com: sudo apt update && sudo apt install -y adb"
    exit 1
fi

# 2. Verificar presenca de netcat (nc)
if ! command -v nc >/dev/null 2>&1; then
    echo "[ERRO] 'nc' nao encontrado. Instale com: sudo apt install -y netcat-openbsd"
    exit 1
fi

# 3. Verificar dispositivo conectado via ADB
echo "[MyMicDoird] Verificando conexao USB via ADB..."
DEVICE_COUNT=${'$'}(adb devices | grep -v "List of devices" | grep -c "device" || true)
if [ "${'$'}DEVICE_COUNT" -eq 0 ]; then
    echo "[AVISO] Nenhum celular detectado pelo ADB!"
    echo "  -> Certifique-se de ativar 'Depuracao USB' no Android."
    echo "  -> Verifique o cabo e autorize a chave RSA na tela do celular."
fi

# 4. Configurar encaminhamento de porta
echo "[MyMicDoird] Configurando porta ADB tcp:${'$'}PORT..."
adb forward tcp:${'$'}PORT tcp:${'$'}PORT

# 5. Criar fonte virtual no PipeWire ou PulseAudio
echo "[MyMicDoird] Configurando nó de áudio '${'$'}DEVICE_NAME'..."

# Remove modulo anterior especifico do MyMicDoird se existir
OLD_ID=${'$'}(pactl list short modules 2>/dev/null | grep "${'$'}DEVICE_NAME" | awk '{print ${'$'}1}' | head -n 1 || true)
if [ -n "${'$'}OLD_ID" ]; then
    pactl unload-module "${'$'}OLD_ID" 2>/dev/null || true
fi

# Tenta carregar null-sink com classe de microfone virtual (Padrao PipeWire no Ubuntu 24.04/22.04)
MODULE_ID=${'$'}(pactl load-module module-null-sink media.class=Audio/Source/Virtual sink_name="${'$'}DEVICE_NAME" channel_map="${'$'}CHANNEL_MAP" 2>/dev/null || true)

if [ -z "${'$'}MODULE_ID" ]; then
    # Fallback para PulseAudio classico
    echo "[MyMicDoird] PipeWire virtual source falhou, usando fallback PulseAudio module-pipe-source..."
    [ -p /tmp/mymicdoird ] || mkfifo /tmp/mymicdoird
    MODULE_ID=${'$'}(pactl load-module module-pipe-source source_name="${'$'}DEVICE_NAME" file=/tmp/mymicdoird format=s16le rate=${'$'}RATE channels=${'$'}CHANNELS 2>/dev/null || true)
fi

echo "[MyMicDoird] -> Microfone '${'$'}DEVICE_NAME' ativado com sucesso (ID: ${'$'}MODULE_ID)!"
echo "[MyMicDoird] Selecione 'MyMicDoird' no Discord, OBS, Zoom, Google Meet, etc."
echo "[MyMicDoird] Iniciando loop de transmissao com auto-reconexo (Pressione Ctrl+C para sair)..."

# 6. Loop resiliente de streaming e auto-reconexo
while true; do
    echo "[MyMicDoird] Conectando ao fluxo de audio em 127.0.0.1:${'$'}PORT..."

    if command -v pw-cat >/dev/null 2>&1; then
        # Metodo nativo PipeWire
        nc 127.0.0.1 "${'$'}PORT" | pw-cat -p --rate="${'$'}RATE" --channels="${'$'}CHANNELS" --format=s16le --target="${'$'}DEVICE_NAME" || true
    elif command -v pacat >/dev/null 2>&1; then
        # Metodo PulseAudio compat
        nc 127.0.0.1 "${'$'}PORT" | pacat --playback -p --rate="${'$'}RATE" --channels="${'$'}CHANNELS" --format=s16le -d "${'$'}DEVICE_NAME" || true
    else
        # Fallback FIFO
        [ -p /tmp/mymicdoird ] || mkfifo /tmp/mymicdoird
        nc 127.0.0.1 "${'$'}PORT" > /tmp/mymicdoird || true
    fi

    echo "[MyMicDoird] Conexao interrompida. Tentando reconectar em 2 segundos..."
    adb forward tcp:${'$'}PORT tcp:${'$'}PORT 2>/dev/null || true
    sleep 2
done
        """.trimIndent()
    }
}
