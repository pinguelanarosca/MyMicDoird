package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.StreamConfig
import com.example.model.StreamMetrics
import com.example.ui.MainViewModel
import com.example.ui.components.AudioWaveformVisualizer
import com.example.ui.components.TerminalCodeCard
import com.example.ui.components.VuMeter
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.TealSecondary
import com.example.ui.theme.VuGreen
import com.example.ui.theme.VuRed
import com.example.ui.theme.VuYellow
import kotlin.math.roundToInt

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToUpdates: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val metrics by viewModel.liveMetrics.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val isPttActive by viewModel.isPushToTalkActive.collectAsStateWithLifecycle()

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        if (granted) {
            viewModel.startStreaming()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse_glow")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF070C14))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top App Bar Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "MYMIC",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text = "DOIRD",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = CyanPrimary,
                        letterSpacing = 1.5.sp
                    )
                }
                Text(
                    text = "Microfone de Baixa Latência para Ubuntu",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF64748B)
                )
            }

            Row {
                IconButton(
                    onClick = onNavigateToUpdates,
                    modifier = Modifier.testTag("nav_updates_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = "Buscar Atualizações",
                        tint = CyanPrimary
                    )
                }
                IconButton(
                    onClick = onNavigateToSettings,
                    modifier = Modifier.testTag("nav_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Configurações",
                        tint = Color(0xFF94A3B8)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Live Connection Status Badge
        StatusBadge(metrics = metrics, config = config)

        Spacer(modifier = Modifier.height(24.dp))

        // Big Main Power / Mic Toggle Button
        val isStreaming = metrics.isRunning
        val isMuted = metrics.isMuted

        val buttonBorderColor by animateColorAsState(
            targetValue = when {
                !isStreaming -> Color(0xFF1E2D42)
                isMuted -> Color(0xFFFF9100)
                metrics.isConnected -> VuGreen
                else -> CyanPrimary
            },
            label = "btn_color"
        )

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(170.dp)
                .scale(if (isStreaming && !isMuted) pulseScale else 1f)
        ) {
            // Glow ring
            if (isStreaming) {
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    buttonBorderColor.copy(alpha = 0.35f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }

            Surface(
                modifier = Modifier
                    .size(136.dp)
                    .clip(CircleShape)
                    .border(3.dp, buttonBorderColor, CircleShape)
                    .clickable {
                        if (!hasAudioPermission) {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            if (isStreaming) {
                                viewModel.stopStreaming()
                            } else {
                                viewModel.startStreaming()
                            }
                        }
                    }
                    .testTag("main_power_button"),
                color = if (isStreaming) Color(0xFF0F2133) else Color(0xFF111827),
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (!isStreaming) Icons.Default.PowerSettingsNew else if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = if (isStreaming) "Parar Gravação" else "Iniciar Gravação",
                        tint = buttonBorderColor,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (!isStreaming) "INICIAR" else if (isMuted) "MUTADO" else "NO AR",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = buttonBorderColor,
                        letterSpacing = 1.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Mute and Push-To-Talk Controls
        AnimatedVisibility(visible = isStreaming) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Toggle Mute Button
                Button(
                    onClick = { viewModel.toggleMute() },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("mute_toggle_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isMuted) Color(0xFFFF9100) else Color(0xFF192535),
                        contentColor = if (isMuted) Color.Black else Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isMuted) "Desmutar" else "Mutar",
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Push-To-Talk Hold Button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isPttActive) CyanPrimary else Color(0xFF192535))
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    viewModel.setPushToTalk(true)
                                    tryAwaitRelease()
                                    viewModel.setPushToTalk(false)
                                }
                            )
                        }
                        .testTag("ptt_hold_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isPttActive) "FALANDO..." else "Pressionar p/ Falar",
                        color = if (isPttActive) Color(0xFF002633) else Color(0xFFE2E8F0),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // VU Meter
        VuMeter(
            rmsDb = metrics.currentRmsDb,
            peakDb = metrics.peakDb,
            isMuted = metrics.isMuted || !metrics.isRunning
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Audio Waveform Visualizer
        AudioWaveformVisualizer(
            waveformHistory = metrics.waveformHistory,
            isStreaming = metrics.isRunning,
            isMuted = metrics.isMuted
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Gain Slider Multiplier
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0B141F)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = null,
                            tint = CyanPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Ganho Digital do Microfone",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Text(
                        text = "${String.format("%.1f", config.gainMultiplier)}x",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = CyanPrimary
                    )
                }

                Slider(
                    value = config.gainMultiplier,
                    onValueChange = { viewModel.updateGain(it) },
                    valueRange = 0.5f..4.0f,
                    steps = 14,
                    colors = SliderDefaults.colors(
                        thumbColor = CyanPrimary,
                        activeTrackColor = CyanPrimary,
                        inactiveTrackColor = Color(0xFF1E2D42)
                    ),
                    modifier = Modifier.testTag("gain_slider")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("0.5x (-6dB)", fontSize = 10.sp, color = Color(0xFF64748B))
                    Text("1.0x (Padrão)", fontSize = 10.sp, color = Color(0xFF94A3B8))
                    Text("4.0x (+12dB)", fontSize = 10.sp, color = Color(0xFF64748B))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Metrics Telemetry Grid
        MetricsGrid(metrics = metrics, config = config)

        Spacer(modifier = Modifier.height(20.dp))

        // Ubuntu Connection Terminal Code Snippet
        TerminalCodeCard(
            adbCommand = viewModel.getAdbForwardCommand(),
            pipeWireCommand = viewModel.getPipeWireCommand(),
            pulseAudioCommand = viewModel.getPulseAudioCommand(),
            testCommand = viewModel.getTestPlaybackCommand(),
            fullScript = viewModel.getCompleteBashScript()
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Quick Instructions on how ADB works
        QuickAdbGuide()
    }
}

@Composable
private fun StatusBadge(metrics: StreamMetrics, config: StreamConfig) {
    val statusText: String
    val badgeColor: Color
    val statusIcon: androidx.compose.ui.graphics.vector.ImageVector

    when {
        metrics.errorMessage != null -> {
            statusText = metrics.errorMessage
            badgeColor = VuRed
            statusIcon = Icons.Outlined.Warning
        }
        !metrics.isRunning -> {
            statusText = "Microfone Desconectado (Porta ${config.port})"
            badgeColor = Color(0xFF64748B)
            statusIcon = Icons.Outlined.Info
        }
        metrics.isMuted -> {
            statusText = "Microfone MUTADO (Transmissão em silêncio)"
            badgeColor = Color(0xFFFF9100)
            statusIcon = Icons.Default.MicOff
        }
        metrics.isConnected -> {
            statusText = "Conectado ao Ubuntu (${metrics.clientAddress ?: "127.0.0.1"})"
            badgeColor = VuGreen
            statusIcon = Icons.Outlined.CheckCircle
        }
        metrics.isListening -> {
            statusText = "Aguardando conexão do Ubuntu no porto ${config.port}…"
            badgeColor = CyanPrimary
            statusIcon = Icons.Outlined.Speed
        }
        else -> {
            statusText = "Pronto"
            badgeColor = CyanPrimary
            statusIcon = Icons.Outlined.CheckCircle
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = badgeColor.copy(alpha = 0.12f)),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = statusIcon,
                contentDescription = null,
                tint = badgeColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = badgeColor
            )
        }
    }
}

@Composable
private fun MetricsGrid(metrics: StreamMetrics, config: StreamConfig) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Bitrate / Format
        MetricCard(
            title = "TAXA / CANAL",
            value = "${config.sampleRate / 1000}kHz ${if (config.channels == 1) "Mono" else "Stereo"}",
            subtext = "PCM 16-bit",
            modifier = Modifier.weight(1f)
        )

        // Data Transmitted
        val bytes = metrics.bytesSent
        val formattedBytes = when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${String.format("%.1f", bytes / 1024.0)} KB"
            else -> "${String.format("%.2f", bytes / (1024.0 * 1024.0))} MB"
        }
        MetricCard(
            title = "ENVIADOS",
            value = formattedBytes,
            subtext = "${config.port} (${config.streamMode.name.substringBefore("_")})",
            modifier = Modifier.weight(1f)
        )

        // Duration
        val seconds = metrics.durationSeconds
        val durationFormatted = String.format("%02d:%02d", seconds / 60, seconds % 60)
        MetricCard(
            title = "DURAÇÃO",
            value = durationFormatted,
            subtext = config.bufferPreset.label.substringBefore(" ("),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    subtext: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1722)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF64748B),
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtext,
                fontSize = 9.sp,
                color = CyanPrimary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun QuickAdbGuide() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0B141F)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Como Conectar ao Ubuntu via USB:",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            GuideStep(num = "1", text = "Conecte o cabo USB ao Ubuntu e ative 'Depuração USB' nas opções de desenvolvedor do celular.")
            GuideStep(num = "2", text = "No terminal do Ubuntu execute: adb devices (autorize na tela do celular se solicitado).")
            GuideStep(num = "3", text = "Toque em 'INICIAR' no app e cole o comando do terminal mostrado acima no Ubuntu.")
            GuideStep(num = "4", text = "Pronto! O Ubuntu reconhecerá o DroidMic como entrada no Discord, OBS, Meets, etc.")
        }
    }
}

@Composable
private fun GuideStep(num: String, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(CyanPrimary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = num,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFF00222D)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF94A3B8),
            lineHeight = 16.sp
        )
    }
}
