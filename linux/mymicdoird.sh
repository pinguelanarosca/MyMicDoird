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
DEVICE_NAME="MyMicDoird"
FIFO="/tmp/mymicdoird.fifo"
MODULE_ID=""

cleanup() {
    echo ""
    echo "[MyMicDoird] Encerrando transmissão e liberando recursos..."
    # Fecha descritor persistente da FIFO se aberto
    exec 3>&- 2>/dev/null || true
    # Descarrega EXCLUSIVAMENTE o módulo gerado por esta sessão (não toca em outras fontes do sistema)
    if [ -n "$MODULE_ID" ]; then
        echo "[MyMicDoird] Descarregando módulo ID: $MODULE_ID"
        pactl unload-module "$MODULE_ID" 2>/dev/null || true
    fi
    rm -f "$FIFO" 2>/dev/null || true
    echo "[MyMicDoird] Concluído. Nenhuma outra fonte de áudio foi modificada."
    exit 0
}

trap cleanup INT TERM EXIT

echo "=========================================================="
echo "          MyMicDoird: Microfone USB para Ubuntu           "
echo "=========================================================="

# 1. Verificar dependências essenciais
if ! command -v adb >/dev/null 2>&1; then
    echo "[ERRO] 'adb' não encontrado. Instale com: sudo apt update && sudo apt install -y adb" >&2
    exit 1
fi

if ! command -v nc >/dev/null 2>&1; then
    echo "[ERRO] 'nc' (netcat) não encontrado. Instale com: sudo apt install -y netcat-openbsd" >&2
    exit 1
fi

if ! command -v pactl >/dev/null 2>&1; then
    echo "[ERRO] 'pactl' não encontrado. Certifique-se de ter o PulseAudio ou PipeWire-Pulse instalado." >&2
    exit 1
fi

# 2. Resolução do dispositivo ADB alvo (evitando conflito com Redroid / Fire Stick)
SERIAL="${ANDROID_SERIAL:-}"

if [ -z "$SERIAL" ]; then
    echo "[MyMicDoird] Buscando smartphone físico conectado via USB..."
    
    # Filtra dispositivos físicos ignorando emuladores de rede/portas IP (como 127.0.0.1:5555 ou 192.168.*)
    PHYSICAL_DEVICES=$(adb devices -l | grep -v "List of devices" | grep "device " | grep -v ":" | awk '{print $1}' || true)
    DEVICE_COUNT=$(echo "$PHYSICAL_DEVICES" | grep -c -v '^$' || true)

    if [ "$DEVICE_COUNT" -eq 1 ]; then
        SERIAL=$(echo "$PHYSICAL_DEVICES" | tr -d '[:space:]')
        echo "[MyMicDoird] Smartphone USB selecionado automaticamente: $SERIAL"
    elif [ "$DEVICE_COUNT" -gt 1 ]; then
        echo "[AVISO] Múltiplos dispositivos USB detectados:"
        echo "$PHYSICAL_DEVICES"
        echo ""
        echo "Por favor, defina a variável ANDROID_SERIAL com o serial desejado e reexecute:"
        echo "  export ANDROID_SERIAL=$(echo "$PHYSICAL_DEVICES" | head -n 1)"
        echo "  ./linux/mymicdoird.sh"
        exit 1
    else
        # Se nenhum dispositivo sem ':' foi encontrado, verifica se há exatamente 1 dispositivo no adb devices
        ALL_DEVICES=$(adb devices | grep -v "List of devices" | grep -w "device" | awk '{print $1}' || true)
        ALL_COUNT=$(echo "$ALL_DEVICES" | grep -c -v '^$' || true)
        if [ "$ALL_COUNT" -eq 1 ]; then
            SERIAL=$(echo "$ALL_DEVICES" | tr -d '[:space:]')
            echo "[MyMicDoird] Usando único dispositivo ADB conectado: $SERIAL"
        else
            echo "[ERRO] Nenhum smartphone USB físico autorizado encontrado!" >&2
            echo "Dispositivos ADB visíveis no momento:" >&2
            adb devices -l >&2
            echo "" >&2
            echo "Dica: Conecte o cabo USB, ative a 'Depuração USB' e autorize o computador na tela do celular." >&2
            echo "Para conectar via Wi-Fi ou forçar um dispositivo: export ANDROID_SERIAL=<IP:PORTA_OU_SERIAL>" >&2
            exit 1
        fi
    fi
else
    echo "[MyMicDoird] Usando ANDROID_SERIAL configurado: $SERIAL"
fi

# 3. Encaminhamento de porta ADB com o serial explícito
echo "[MyMicDoird] Configurando redirecionamento: adb -s $SERIAL forward tcp:$PORT tcp:$PORT"
if ! adb -s "$SERIAL" forward tcp:"$PORT" tcp:"$PORT"; then
    echo "[ERRO] Falha ao configurar adb forward na porta $PORT para o serial $SERIAL." >&2
    exit 1
fi

# 4. Criar nó de microfone virtual via module-pipe-source
echo "[MyMicDoird] Configurando microfone virtual '$DEVICE_NAME'..."

# Limpeza seletiva apenas de instâncias anteriores do MyMicDoird (sem tocar em philco_tv, relay_mic, etc.)
OLD_IDS=$(pactl list short modules 2>/dev/null | grep -E "source_name=MyMicDoird|file=/tmp/mymicdoird\.fifo" | awk '{print $1}' || true)
if [ -n "$OLD_IDS" ]; then
    for mid in $OLD_IDS; do
        echo "[MyMicDoird] Descarregando módulo anterior MyMicDoird (ID: $mid)..."
        pactl unload-module "$mid" 2>/dev/null || true
    done
fi

rm -f "$FIFO"
mkfifo "$FIFO"
exec 3<> "$FIFO"

MODULE_ID=$(pactl load-module module-pipe-source \
    source_name="$DEVICE_NAME" \
    file="$FIFO" \
    format=s16le \
    rate="$RATE" \
    channels="$CHANNELS" \
    source_properties="device.description='MyMicDoird_Microphone' device.class='sound'" 2>/dev/null || true)

if [ -z "$MODULE_ID" ] || ! echo "$MODULE_ID" | grep -qE '^[0-9]+$'; then
    echo "[ERRO] Falha ao carregar module-pipe-source no PulseAudio/PipeWire!" >&2
    exit 1
fi

echo "[MyMicDoird] -> Microfone virtual '$DEVICE_NAME' ativado com sucesso (Módulo ID: $MODULE_ID)!"
echo "[MyMicDoird] Dispositivo visível nas Configurações de Som do Ubuntu como: MyMicDoird_Microphone"
echo "[MyMicDoird] Iniciando fluxo de áudio com auto-reconexão contínua (Pressione Ctrl+C para encerrar)..."
echo ""

# 5. Loop resiliente de streaming e auto-reconexão
while true; do
    echo "[MyMicDoird] Conectando ao fluxo de áudio em 127.0.0.1:$PORT..."
    nc 127.0.0.1 "$PORT" >&3 || true
    echo "[MyMicDoird] Conexão finalizada ou interrompida. Verificando ADB e reconectando em 2 segundos..."
    adb -s "$SERIAL" forward tcp:"$PORT" tcp:"$PORT" 2>/dev/null || true
    sleep 2
done
