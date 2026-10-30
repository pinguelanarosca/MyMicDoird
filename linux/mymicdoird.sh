#!/usr/bin/env bash
# ==============================================================================
# MyMicDoird - Script Oficial para Ubuntu Linux (PulseAudio / PipeWire 1.6.2+)
# Repositório: pinguelanarosca/MyMicDoird
# Suporta múltiplos dispositivos ADB (Redroid, Fire Stick, etc.) e PipeWire nativo.
# ==============================================================================
set -euo pipefail

PORT="${PORT:-8088}"
RATE="${RATE:-48000}"
CHANNELS="${CHANNELS:-1}"
CHANNEL_MAP="mono"
if [ "$CHANNELS" -eq 2 ]; then
    CHANNEL_MAP="stereo"
fi
DEVICE_NAME="MyMicDoird"
MODULE_ID=""

cleanup() {
    echo ""
    echo "[MyMicDoird] Encerrando transmissão e liberando recursos..."
    # Descarrega EXCLUSIVAMENTE o módulo gerado por esta sessão (não toca em outras fontes do sistema)
    if [ -n "$MODULE_ID" ]; then
        echo "[MyMicDoird] Descarregando módulo ID: $MODULE_ID"
        pactl unload-module "$MODULE_ID" 2>/dev/null || true
    fi
    rm -f /tmp/mymicdoird 2>/dev/null || true
    echo "[MyMicDoird] Concluído. Nenhuma outra fonte de áudio foi modificada."
    exit 0
}

trap cleanup INT TERM EXIT

echo "=========================================================="
echo "          MyMicDoird: Microfone USB para Ubuntu           "
echo "=========================================================="

# 1. Verificar dependências
if ! command -v adb >/dev/null 2>&1; then
    echo "[ERRO] 'adb' não encontrado. Instale com: sudo apt update && sudo apt install -y adb"
    exit 1
fi

if ! command -v nc >/dev/null 2>&1; then
    echo "[ERRO] 'nc' (netcat) não encontrado. Instale com: sudo apt install -y netcat-openbsd"
    exit 1
fi

if ! command -v pactl >/dev/null 2>&1; then
    echo "[ERRO] 'pactl' não encontrado."
    exit 1
fi

# 2. Resolução do dispositivo ADB alvo (evitando conflito com Redroid / Fire Stick)
SERIAL="${ANDROID_SERIAL:-}"

if [ -z "$SERIAL" ]; then
    echo "[MyMicDoird] Buscando smartphone físico conectado via USB..."
    
    # Filtra dispositivos físicos (excluindo portas IP de emuladores e redes como 127.0.0.1:5555 ou 192.168.*)
    PHYSICAL_DEVICES=$(adb devices -l | grep -v "List of devices" | grep "device " | grep -v ":" | awk '{print $1}' || true)
    DEVICE_COUNT=$(echo "$PHYSICAL_DEVICES" | grep -c -v '^$' || true)

    if [ "$DEVICE_COUNT" -eq 1 ]; then
        SERIAL=$(echo "$PHYSICAL_DEVICES" | tr -d '[:space:]')
        echo "[MyMicDoird] Dispositivo USB selecionado automaticamente: $SERIAL"
    elif [ "$DEVICE_COUNT" -gt 1 ]; then
        echo "[AVISO] Múltiplos dispositivos USB detectados:"
        echo "$PHYSICAL_DEVICES"
        echo ""
        echo "Por favor, defina a variável ANDROID_SERIAL com o serial desejado e reexecute:"
        echo "  export ANDROID_SERIAL=$(echo "$PHYSICAL_DEVICES" | head -n 1)"
        echo "  ./linux/mymicdoird.sh"
        exit 1
    else
        # Nenhum USB físico sem IP encontrado, verifica se há algum dispositivo conectado
        ALL_DEVICES=$(adb devices | grep -v "List of devices" | grep "device$" | awk '{print $1}' || true)
        ALL_COUNT=$(echo "$ALL_DEVICES" | grep -c -v '^$' || true)
        if [ "$ALL_COUNT" -eq 1 ]; then
            SERIAL=$(echo "$ALL_DEVICES" | tr -d '[:space:]')
            echo "[MyMicDoird] Usando único dispositivo ADB conectado: $SERIAL"
        else
            echo "[ERRO] Nenhum smartphone USB físico autorizado encontrado!"
            echo "Dispositivos ADB visíveis no momento:"
            adb devices
            echo ""
            echo "Dica: Conecte o cabo USB, ative a 'Depuração USB' e autorize o computador na tela do celular."
            exit 1
        fi
    fi
else
    echo "[MyMicDoird] Usando ANDROID_SERIAL configurado: $SERIAL"
fi

# 3. Encaminhamento de porta ADB com o serial explícito
echo "[MyMicDoird] Configurando redirecionamento: adb -s $SERIAL forward tcp:$PORT tcp:$PORT"
adb -s "$SERIAL" forward tcp:"$PORT" tcp:"$PORT"

# 4. Criar nó de microfone virtual no PipeWire ou PulseAudio
echo "[MyMicDoird] Criando microfone virtual '$DEVICE_NAME'..."

# Remove instância prévia com o mesmo nome se houver resquício de execução abortada
OLD_ID=$(pactl list short modules 2>/dev/null | grep "$DEVICE_NAME" | awk '{print $1}' | head -n 1 || true)
if [ -n "$OLD_ID" ]; then
    echo "[MyMicDoird] Limpando módulo anterior MyMicDoird (ID: $OLD_ID)..."
    pactl unload-module "$OLD_ID" 2>/dev/null || true
fi

# Carrega módulo null-sink com classe de microfone virtual no PipeWire / PulseAudio
MODULE_ID=$(pactl load-module module-null-sink media.class=Audio/Source/Virtual sink_name="$DEVICE_NAME" channel_map="$CHANNEL_MAP" 2>/dev/null || true)

if [ -z "$MODULE_ID" ]; then
    echo "[MyMicDoird] Fallback para module-pipe-source..."
    [ -p /tmp/mymicdoird ] || mkfifo /tmp/mymicdoird
    MODULE_ID=$(pactl load-module module-pipe-source source_name="$DEVICE_NAME" file=/tmp/mymicdoird format=s16le rate="$RATE" channels="$CHANNELS" 2>/dev/null || true)
fi

echo "[MyMicDoird] -> Microfone virtual '$DEVICE_NAME' pronto (Módulo ID: $MODULE_ID)!"
echo "[MyMicDoird] Abra as configurações de som do Ubuntu e selecione 'MyMicDoird' no Discord, OBS, Google Meet, etc."
echo "[MyMicDoird] Iniciando fluxo de áudio com auto-reconexão (Pressione Ctrl+C para encerrar)..."

# 5. Loop resiliente de streaming e auto-reconexão
while true; do
    echo "[MyMicDoird] Conectando ao fluxo de áudio em 127.0.0.1:$PORT..."

    if command -v pw-cat >/dev/null 2>&1; then
        # Método nativo PipeWire 1.6.2+
        nc 127.0.0.1 "$PORT" | pw-cat -p --rate="$RATE" --channels="$CHANNELS" --format=s16 - || true
    elif command -v pacat >/dev/null 2>&1; then
        # Camada PulseAudio
        nc 127.0.0.1 "$PORT" | pacat --playback -p --rate="$RATE" --channels="$CHANNELS" --format=s16le -d "$DEVICE_NAME" || true
    else
        # Fallback FIFO
        [ -p /tmp/mymicdoird ] || mkfifo /tmp/mymicdoird
        nc 127.0.0.1 "$PORT" > /tmp/mymicdoird || true
    fi

    echo "[MyMicDoird] Conexão interrompida. Verificando ADB e reconectando em 2 segundos..."
    adb -s "$SERIAL" forward tcp:"$PORT" tcp:"$PORT" 2>/dev/null || true
    sleep 2
done
