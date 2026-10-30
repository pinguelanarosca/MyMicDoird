package com.example.ui.screens

import android.media.MediaRecorder
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.BufferPreset
import com.example.model.StreamConfig
import com.example.model.StreamMode
import com.example.ui.MainViewModel
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.VuRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onNavigateBack() }

    val currentConfig by viewModel.config.collectAsStateWithLifecycle()

    var portText by remember(currentConfig) { mutableStateOf(currentConfig.port.toString()) }
    var selectedMode by remember(currentConfig) { mutableStateOf(currentConfig.streamMode) }
    var targetHostText by remember(currentConfig) { mutableStateOf(currentConfig.targetHost) }
    var bindToLoopbackOnly by remember(currentConfig) { mutableStateOf(currentConfig.bindToLoopbackOnly) }
    var selectedSampleRate by remember(currentConfig) { mutableIntStateOf(currentConfig.sampleRate) }
    var selectedChannels by remember(currentConfig) { mutableIntStateOf(currentConfig.channels) }
    var selectedBufferPreset by remember(currentConfig) { mutableStateOf(currentConfig.bufferPreset) }
    var selectedAudioSource by remember(currentConfig) { mutableIntStateOf(currentConfig.audioSource) }
    var enableNoiseSuppressor by remember(currentConfig) { mutableStateOf(currentConfig.enableNoiseSuppressor) }
    var enableEchoCanceler by remember(currentConfig) { mutableStateOf(currentConfig.enableEchoCanceler) }
    var enableAutoGainControl by remember(currentConfig) { mutableStateOf(currentConfig.enableAutoGainControl) }
    var keepScreenOn by remember(currentConfig) { mutableStateOf(currentConfig.keepScreenOn) }

    val parsedPort = portText.toIntOrNull()
    val isPortValid = parsedPort != null && StreamConfig.isValidPort(parsedPort)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF070C14))
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.testTag("back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Voltar",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "Ajustes de Conexão",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Parâmetros de latência, áudio e segurança",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF64748B)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Redes e Portos Card
        SectionCard(title = "Rede & Protocolo", icon = Icons.Default.Router) {
            OutlinedTextField(
                value = portText,
                onValueChange = { portText = it.filter { ch -> ch.isDigit() }.take(5) },
                label = { Text("Porto TCP / UDP") },
                isError = !isPortValid && portText.isNotBlank(),
                supportingText = {
                    if (!isPortValid && portText.isNotBlank()) {
                        Text("Porto inválido! Deve ser entre 1024 e 65535.", color = VuRed)
                    } else {
                        Text("Padrão: 8088. Portas permitidas: 1024 a 65535.")
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("input_port"),
                colors = textFieldColors()
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text("Modo de Conexão:", style = MaterialTheme.typography.labelMedium, color = Color(0xFF94A3B8))
            Spacer(modifier = Modifier.height(6.dp))

            StreamMode.entries.forEach { mode ->
                FilterChip(
                    selected = selectedMode == mode,
                    onClick = { selectedMode = mode },
                    label = {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text(mode.displayName, fontWeight = FontWeight.SemiBold)
                            Text(mode.description, fontSize = 11.sp, color = Color(0xFF94A3B8))
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    colors = chipColors()
                )
            }

            if (selectedMode != StreamMode.TCP_SERVER) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = targetHostText,
                    onValueChange = { targetHostText = it },
                    label = { Text("IP / Host Alvo do Ubuntu") },
                    supportingText = { Text("127.0.0.1 via adb reverse, ou IP da máquina na rede") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_target_host"),
                    colors = textFieldColors()
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Segurança de Rede Card
        SectionCard(title = "Segurança de Conexão", icon = Icons.Default.Security) {
            SwitchRow(
                title = "Restringir a Loopback (ADB Seguro)",
                subtitle = "Faz bind exclusivo em 127.0.0.1 impedindo acesso não autorizado pela rede Wi-Fi",
                checked = bindToLoopbackOnly,
                onCheckedChange = { bindToLoopbackOnly = it }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Audio Quality & Rate Card
        SectionCard(title = "Taxa de Amostragem & Canais", icon = Icons.Default.GraphicEq) {
            Text("Taxa de Amostragem (Hz):", style = MaterialTheme.typography.labelMedium, color = Color(0xFF94A3B8))
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(48000 to "48 kHz (Padrão Ubuntu)", 44100 to "44.1 kHz", 16000 to "16 kHz (Voz)").forEach { (rate, label) ->
                    FilterChip(
                        selected = selectedSampleRate == rate,
                        onClick = { selectedSampleRate = rate },
                        label = { Text(label, fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        colors = chipColors()
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text("Canais de Entrada:", style = MaterialTheme.typography.labelMedium, color = Color(0xFF94A3B8))
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedChannels == 1,
                    onClick = { selectedChannels = 1 },
                    label = { Text("Mono (1 Canal - Recomendado)", fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                    colors = chipColors()
                )
                FilterChip(
                    selected = selectedChannels == 2,
                    onClick = { selectedChannels = 2 },
                    label = { Text("Stereo (2 Canais)", fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                    colors = chipColors()
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Latência & Buffer Card
        SectionCard(title = "Latência & Buffer", icon = Icons.Default.Speed) {
            Text("Preset de Latência:", style = MaterialTheme.typography.labelMedium, color = Color(0xFF94A3B8))
            Spacer(modifier = Modifier.height(6.dp))
            BufferPreset.entries.forEach { preset ->
                FilterChip(
                    selected = selectedBufferPreset == preset,
                    onClick = { selectedBufferPreset = preset },
                    label = { Text(preset.label, fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    colors = chipColors()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text("Fonte de Entrada Android:", style = MaterialTheme.typography.labelMedium, color = Color(0xFF94A3B8))
            Spacer(modifier = Modifier.height(6.dp))
            val sources = listOf(
                MediaRecorder.AudioSource.MIC to "Microfone Principal (Padrão)",
                MediaRecorder.AudioSource.VOICE_COMMUNICATION to "Voz Comunicação (AEC/DSP)",
                MediaRecorder.AudioSource.VOICE_RECOGNITION to "Reconhecimento de Voz (Foco)",
                MediaRecorder.AudioSource.UNPROCESSED to "Sem Processamento (Raw Studio)"
            )
            sources.forEach { (src, name) ->
                FilterChip(
                    selected = selectedAudioSource == src,
                    onClick = { selectedAudioSource = src },
                    label = { Text(name, fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    colors = chipColors()
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Processamento de Áudio de Hardware Card
        SectionCard(title = "Filtros DSP & Sistema", icon = Icons.Default.Tune) {
            SwitchRow(
                title = "Supressor de Ruído de Fundo",
                subtitle = "Reduz chiado, vento e ruído de ventoinhas se suportado",
                checked = enableNoiseSuppressor,
                onCheckedChange = { enableNoiseSuppressor = it }
            )
            SwitchRow(
                title = "Cancelamento de Eco Acústico",
                subtitle = "Evita retorno de áudio das caixas de som",
                checked = enableEchoCanceler,
                onCheckedChange = { enableEchoCanceler = it }
            )
            SwitchRow(
                title = "Controle Automático de Ganho (AGC)",
                subtitle = "Normaliza volumes baixos e altos automaticamente",
                checked = enableAutoGainControl,
                onCheckedChange = { enableAutoGainControl = it }
            )
            SwitchRow(
                title = "Manter Tela Ligada",
                subtitle = "Mantém a visualização ativa durante a sessão",
                checked = keepScreenOn,
                onCheckedChange = { keepScreenOn = it }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Save & Reset Buttons
        Button(
            onClick = {
                val safePort = StreamConfig.sanitizePort(portText.toIntOrNull() ?: 8088)
                val newConfig = currentConfig.copy(
                    port = safePort,
                    streamMode = selectedMode,
                    targetHost = targetHostText.trim(),
                    bindToLoopbackOnly = bindToLoopbackOnly,
                    sampleRate = selectedSampleRate,
                    channels = selectedChannels,
                    bufferPreset = selectedBufferPreset,
                    audioSource = selectedAudioSource,
                    enableNoiseSuppressor = enableNoiseSuppressor,
                    enableEchoCanceler = enableEchoCanceler,
                    enableAutoGainControl = enableAutoGainControl,
                    keepScreenOn = keepScreenOn
                )
                viewModel.updateConfig(newConfig)
                onNavigateBack()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("save_config_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = CyanPrimary,
                contentColor = Color(0xFF00222D)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Salvar e Aplicar Ajustes", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(
            onClick = {
                viewModel.updateConfig(StreamConfig())
                onNavigateBack()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("reset_defaults_button"),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color(0xFF94A3B8)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Restaurar Padrões de Fábrica")
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1724)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = Color.White, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B), fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF00222D),
                checkedTrackColor = CyanPrimary,
                uncheckedTrackColor = Color(0xFF192535)
            )
        )
    }
}

@Composable
private fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = CyanPrimary,
    unfocusedBorderColor = Color(0xFF26374E),
    focusedLabelColor = CyanPrimary,
    unfocusedLabelColor = Color(0xFF94A3B8),
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White
)

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = Color(0xFF003847),
    selectedLabelColor = CyanPrimary,
    containerColor = Color(0xFF090F17),
    labelColor = Color(0xFFCBD5E1)
)
