package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.model.GitHubRelease
import com.example.model.UpdateState
import com.example.ui.MainViewModel
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.VuGreen
import com.example.ui.theme.VuRed

@Composable
fun UpdateScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onNavigateBack() }

    val currentRepo by viewModel.githubRepo.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()

    var repoInput by remember(currentRepo) { mutableStateOf(currentRepo) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF070C14))
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Top App Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.testTag("back_button_updates")
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
                    text = "Atualizações GitHub",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Verificação e instalação automática de novas versões",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF64748B)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Current App Version Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1724)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "VERSÃO INSTALADA",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF64748B),
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF003847))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Nativo",
                        color = CyanPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // GitHub Repository Configuration Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1724)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Repositório GitHub de Origem",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "O aplicativo consulta os releases públicos da API do GitHub deste repositório.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8)
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = repoInput,
                    onValueChange = { repoInput = it },
                    label = { Text("usuario/repositorio") },
                    placeholder = { Text("ex: alexandreoops/droidmic") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("github_repo_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanPrimary,
                        unfocusedBorderColor = Color(0xFF26374E),
                        focusedLabelColor = CyanPrimary,
                        unfocusedLabelColor = Color(0xFF94A3B8),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    trailingIcon = {
                        if (repoInput.trim() != currentRepo) {
                            IconButton(
                                onClick = { viewModel.saveGitHubRepo(repoInput) },
                                modifier = Modifier.testTag("save_repo_button")
                            ) {
                                Icon(Icons.Default.Save, contentDescription = "Salvar Repositório", tint = CyanPrimary)
                            }
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Action: Check for Updates Button
        Button(
            onClick = {
                viewModel.saveGitHubRepo(repoInput)
                viewModel.checkForUpdates()
            },
            enabled = updateState !is UpdateState.Checking && updateState !is UpdateState.Downloading,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("check_updates_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = CyanPrimary,
                contentColor = Color(0xFF00222D)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (updateState is UpdateState.Checking) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color(0xFF00222D), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Text("Consultando GitHub...", fontWeight = FontWeight.Bold)
            } else {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Buscar Atualizações Agora", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Dynamic State Content
        when (val state = updateState) {
            is UpdateState.Idle -> {
                // Info prompt
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0C131D))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Toque no botão acima para verificar se há novas versões disponíveis.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF64748B)
                    )
                }
            }

            is UpdateState.Checking -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1724)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = CyanPrimary)
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Verificando releases no GitHub...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFCBD5E1)
                        )
                    }
                }
            }

            is UpdateState.UpToDate -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0A1F18)),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, VuGreen.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = VuGreen, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Seu aplicativo está atualizado!", fontWeight = FontWeight.Bold, color = VuGreen)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("A versão instalada (v${state.currentVersion}) é a mais recente disponível no repositório.", fontSize = 12.sp, color = Color(0xFFA7F3D0))
                        }
                    }
                }
            }

            is UpdateState.Available -> {
                AvailableReleaseCard(
                    release = state.release,
                    onDownload = { viewModel.downloadUpdate(state.release) }
                )
            }

            is UpdateState.Downloading -> {
                DownloadingCard(state = state)
            }

            is UpdateState.ReadyToInstall -> {
                ReadyToInstallCard(
                    state = state,
                    onInstall = { viewModel.installApk(state.apkFile) }
                )
            }

            is UpdateState.Error -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF240D11)),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, VuRed.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.ErrorOutline, contentDescription = null, tint = VuRed)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Aviso de Atualização", fontWeight = FontWeight.Bold, color = VuRed)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(state.message, style = MaterialTheme.typography.bodySmall, color = Color(0xFFFECDD3))
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { viewModel.resetUpdateState() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("OK")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AvailableReleaseCard(
    release: GitHubRelease,
    onDownload: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1E2E)),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CyanPrimary.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "NOVA VERSÃO DISPONÍVEL",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = CyanPrimary,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = release.name.ifEmpty { release.tagName },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(CyanPrimary)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = release.tagName,
                        color = Color(0xFF00222D),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // APK details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Arquivo: ${release.apkFileName ?: "release.apk"}",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8)
                )
                if (release.apkSizeBytes > 0) {
                    val mb = release.apkSizeBytes / (1024.0 * 1024.0)
                    Text(
                        text = "${String.format("%.1f", mb)} MB",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Release Notes Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF080D15))
                    .padding(10.dp)
            ) {
                Column {
                    Text("Notas da Versão:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = release.body,
                        fontSize = 12.sp,
                        color = Color(0xFFCBD5E1),
                        maxLines = 6
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onDownload,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("download_update_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyanPrimary,
                    contentColor = Color(0xFF00222D)
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Baixar e Instalar Atualização", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DownloadingCard(state: UpdateState.Downloading) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1E2E)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Baixando atualização do GitHub...",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Text(
                    text = "${state.progressPercentage}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = CyanPrimary,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { state.progressPercentage / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = CyanPrimary,
                trackColor = Color(0xFF1E2D42)
            )

            Spacer(modifier = Modifier.height(8.dp))

            val downloadedMb = state.downloadedBytes / (1024.0 * 1024.0)
            val totalMb = state.totalBytes / (1024.0 * 1024.0)
            Text(
                text = "${String.format("%.1f", downloadedMb)} MB de ${String.format("%.1f", totalMb)} MB",
                fontSize = 11.sp,
                color = Color(0xFF94A3B8),
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun ReadyToInstallCard(
    state: UpdateState.ReadyToInstall,
    onInstall: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A2219)),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, VuGreen.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = VuGreen, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Download Concluído com Sucesso!",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = VuGreen
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "O arquivo ${state.apkFile.name} está pronto para ser instalado no aparelho.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFA7F3D0)
            )

            if (state.sha256.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "SHA-256: ${state.sha256}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = Color(0xFF6EE7B7),
                    lineHeight = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF6EE7B7), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Pacote verificado. Se solicitado pelo sistema, conceda permissão de 'Instalar apps desconhecidos'.",
                    fontSize = 11.sp,
                    color = Color(0xFF6EE7B7)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onInstall,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("prompt_install_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VuGreen,
                    contentColor = Color(0xFF002919)
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Instalar Atualização Agora", fontWeight = FontWeight.Bold)
            }
        }
    }
}
