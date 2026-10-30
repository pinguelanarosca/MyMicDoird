package com.example.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.BuildConfig
import com.example.model.GitHubRelease
import com.example.model.UpdateState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

class GitHubUpdateManager(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    fun resetState() {
        _updateState.value = UpdateState.Idle
    }

    suspend fun checkForUpdates(repo: String) = withContext(Dispatchers.IO) {
        _updateState.value = UpdateState.Checking
        val cleanRepo = repo.trim().removePrefix("https://github.com/").removeSuffix("/")

        val url = "https://api.github.com/repos/$cleanRepo/releases/latest"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github.v3+json")
            .header("User-Agent", "DroidMic-Android-App")
            .build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                if (response.code == 404) {
                    _updateState.value = UpdateState.Error("Repositório '$cleanRepo' não encontrado ou não possui releases publicados.")
                } else {
                    _updateState.value = UpdateState.Error("Erro do GitHub: código ${response.code} (${response.message})")
                }
                return@withContext
            }

            val jsonString = response.body?.string() ?: ""
            val json = JSONObject(jsonString)

            val tagName = json.optString("tag_name", "").trim()
            val releaseName = json.optString("name", tagName)
            val body = json.optString("body", "Sem notas de versão.")
            val publishedAt = json.optString("published_at", "")

            var apkUrl: String? = null
            var apkName: String? = null
            var apkSize: Long = 0L

            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url")
                        apkName = name
                        apkSize = asset.optLong("size", 0L)
                        break
                    }
                }
            }

            val release = GitHubRelease(
                tagName = tagName,
                name = releaseName,
                body = body,
                publishedAt = publishedAt,
                apkDownloadUrl = apkUrl,
                apkFileName = apkName,
                apkSizeBytes = apkSize
            )

            val currentVersion = BuildConfig.VERSION_NAME
            if (isNewerVersion(tagName, currentVersion)) {
                _updateState.value = UpdateState.Available(release)
            } else {
                _updateState.value = UpdateState.UpToDate(currentVersion)
            }
        } catch (e: Exception) {
            _updateState.value = UpdateState.Error("Falha na conexão: ${e.localizedMessage ?: "Erro desconhecido"}")
        }
    }

    private fun isNewerVersion(remoteTag: String, currentVersion: String): Boolean {
        // Strip 'v' prefix if present
        val remoteClean = remoteTag.removePrefix("v").removePrefix("V").trim()
        val currentClean = currentVersion.removePrefix("v").removePrefix("V").trim()

        if (remoteClean.isEmpty()) return false
        if (remoteClean == currentClean) return false

        // Parse semver parts: 1.2.0 vs 1.1.0
        val remoteParts = remoteClean.split(".", "-", "_").mapNotNull { it.toIntOrNull() }
        val currentParts = currentClean.split(".", "-", "_").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }

        // If numeric parts identical, check string inequality
        return remoteClean != currentClean
    }

    suspend fun downloadAndPrepareInstall(release: GitHubRelease) = withContext(Dispatchers.IO) {
        val downloadUrl = release.apkDownloadUrl
        if (downloadUrl.isNullOrEmpty()) {
            _updateState.value = UpdateState.Error("A release '${release.tagName}' não contém um arquivo .apk nos assets.")
            return@withContext
        }

        _updateState.value = UpdateState.Downloading(0, 0L, release.apkSizeBytes)

        val request = Request.Builder()
            .url(downloadUrl)
            .header("User-Agent", "DroidMic-Android-App")
            .build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                _updateState.value = UpdateState.Error("Falha ao baixar APK: HTTP ${response.code}")
                return@withContext
            }

            val body = response.body
            if (body == null) {
                _updateState.value = UpdateState.Error("Resposta vazia do servidor.")
                return@withContext
            }

            val totalBytes = if (release.apkSizeBytes > 0) release.apkSizeBytes else body.contentLength()
            val destDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val outputFile = File(destDir, release.apkFileName ?: "droidmic_update.apk")

            var inputStream: InputStream? = null
            var outputStream: FileOutputStream? = null

            try {
                inputStream = body.byteStream()
                outputStream = FileOutputStream(outputFile)
                val buffer = ByteArray(8192)
                var downloadedBytes = 0L
                var read: Int
                var lastProgress = 0

                while (inputStream.read(buffer).also { read = it } != -1) {
                    outputStream.write(buffer, 0, read)
                    downloadedBytes += read

                    val progress = if (totalBytes > 0) {
                        ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                    } else {
                        50
                    }

                    if (progress != lastProgress) {
                        lastProgress = progress
                        _updateState.value = UpdateState.Downloading(
                            progressPercentage = progress,
                            downloadedBytes = downloadedBytes,
                            totalBytes = totalBytes
                        )
                    }
                }
                outputStream.flush()
                _updateState.value = UpdateState.ReadyToInstall(outputFile, release)
            } finally {
                inputStream?.close()
                outputStream?.close()
            }
        } catch (e: Exception) {
            _updateState.value = UpdateState.Error("Erro ao baixar atualização: ${e.localizedMessage}")
        }
    }

    fun promptInstall(apkFile: File): Boolean {
        if (!apkFile.exists()) return false

        // Check canRequestPackageInstalls on Android 8+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(settingsIntent)
                return false
            }
        }

        try {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(installIntent)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}
