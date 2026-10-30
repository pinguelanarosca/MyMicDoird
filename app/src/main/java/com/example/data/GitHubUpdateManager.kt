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
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
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
        val cleanRepo = PreferencesManager.sanitizeRepoName(repo)

        if (cleanRepo.isBlank() || !cleanRepo.contains("/")) {
            _updateState.value = UpdateState.Error("Nome de repositório inválido. Formato esperado: usuario/repositorio")
            return@withContext
        }

        val url = "https://api.github.com/repos/$cleanRepo/releases/latest"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github.v3+json")
            .header("User-Agent", "MyMicDoird-Android-App")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    when (response.code) {
                        404 -> _updateState.value = UpdateState.Error("Repositório '$cleanRepo' não foi encontrado ou não possui releases publicados.")
                        403 -> _updateState.value = UpdateState.Error("Limite temporário de requisições da API do GitHub atingido. Tente novamente mais tarde.")
                        else -> _updateState.value = UpdateState.Error("Erro do GitHub (HTTP ${response.code}): ${response.message}")
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
            }
        } catch (e: Exception) {
            _updateState.value = UpdateState.Error("Falha na conexão: ${e.localizedMessage ?: "Erro de rede"}")
        }
    }

    suspend fun downloadAndPrepareInstall(release: GitHubRelease) = withContext(Dispatchers.IO) {
        val downloadUrl = release.apkDownloadUrl
        if (downloadUrl.isNullOrEmpty()) {
            _updateState.value = UpdateState.Error("A release '${release.tagName}' não contém um arquivo .apk nos assets para download.")
            return@withContext
        }

        _updateState.value = UpdateState.Downloading(0, 0L, release.apkSizeBytes)

        val request = Request.Builder()
            .url(downloadUrl)
            .header("User-Agent", "MyMicDoird-Android-App")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    _updateState.value = UpdateState.Error("Falha ao baixar APK: HTTP ${response.code}")
                    return@withContext
                }

                val body = response.body
                if (body == null) {
                    _updateState.value = UpdateState.Error("Resposta vazia recebida do servidor do GitHub.")
                    return@withContext
                }

                val totalBytes = if (release.apkSizeBytes > 0) release.apkSizeBytes else body.contentLength()
                val destDir = File(context.cacheDir, "updates").apply { mkdirs() }
                val outputFile = File(destDir, release.apkFileName ?: "mymicdoird_update.apk")

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
                } finally {
                    inputStream?.close()
                    outputStream?.close()
                }

                // Verify integrity of the downloaded APK archive
                val archiveInfo = context.packageManager.getPackageArchiveInfo(outputFile.absolutePath, 0)
                if (archiveInfo == null) {
                    outputFile.delete()
                    _updateState.value = UpdateState.Error("Arquivo APK corrompido ou incompleto. Download descartado.")
                    return@withContext
                }

                // Verify packageName matches
                val expectedPackage = context.packageName
                if (archiveInfo.packageName != expectedPackage) {
                    outputFile.delete()
                    _updateState.value = UpdateState.Error(
                        "Incompatibilidade de aplicativo: o pacote '${archiveInfo.packageName}' não corresponde a '$expectedPackage'."
                    )
                    return@withContext
                }

                // Calculate SHA-256 for integrity verification
                val sha256 = calculateFileSha256(outputFile)

                _updateState.value = UpdateState.ReadyToInstall(
                    apkFile = outputFile,
                    release = release,
                    sha256 = sha256
                )
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

    companion object {
        fun calculateFileSha256(file: File): String {
            return try {
                val digest = MessageDigest.getInstance("SHA-256")
                FileInputStream(file).use { fis ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (fis.read(buffer).also { read = it } != -1) {
                        digest.update(buffer, 0, read)
                    }
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            } catch (_: Exception) {
                ""
            }
        }

        data class SemVer(
            val major: Int,
            val minor: Int,
            val patch: Int,
            val preRelease: String?
        ) : Comparable<SemVer> {
            override fun compareTo(other: SemVer): Int {
                if (major != other.major) return major.compareTo(other.major)
                if (minor != other.minor) return minor.compareTo(other.minor)
                if (patch != other.patch) return patch.compareTo(other.patch)

                // SemVer 2.0.0 Rule 11: Normal version has higher precedence than pre-release
                // e.g. 1.0.0 > 1.0.0-rc1
                if (preRelease == null && other.preRelease != null) return 1
                if (preRelease != null && other.preRelease == null) return -1
                if (preRelease == null && other.preRelease == null) return 0

                // Both have pre-release strings
                return comparePreReleaseIdentifiers(preRelease!!, other.preRelease!!)
            }

            private fun comparePreReleaseIdentifiers(p1: String, p2: String): Int {
                val parts1 = p1.split(".")
                val parts2 = p2.split(".")
                val max = maxOf(parts1.size, parts2.size)

                for (i in 0 until max) {
                    if (i >= parts1.size) return -1
                    if (i >= parts2.size) return 1

                    val id1 = parts1[i]
                    val id2 = parts2[i]

                    val num1 = id1.toIntOrNull()
                    val num2 = id2.toIntOrNull()

                    if (num1 != null && num2 != null) {
                        if (num1 != num2) return num1.compareTo(num2)
                    } else if (num1 != null) {
                        return -1 // numeric identifiers have lower precedence than alphanumeric
                    } else if (num2 != null) {
                        return 1
                    } else {
                        val cmp = id1.compareTo(id2)
                        if (cmp != 0) return cmp
                    }
                }
                return 0
            }

            companion object {
                fun parse(raw: String): SemVer? {
                    var clean = raw.trim().removePrefix("v").removePrefix("V")
                    if (clean.isBlank()) return null

                    // SemVer 2.0.0 Rule 10: Build metadata MUST be ignored when determining version precedence
                    val plusIdx = clean.indexOf('+')
                    if (plusIdx >= 0) {
                        clean = clean.substring(0, plusIdx)
                    }

                    val dashIdx = clean.indexOf('-')
                    val mainPart = if (dashIdx >= 0) clean.substring(0, dashIdx) else clean
                    val prePart = if (dashIdx >= 0) clean.substring(dashIdx + 1) else null

                    val numbers = mainPart.split(".").mapNotNull { it.toIntOrNull() }
                    val major = numbers.getOrNull(0) ?: 0
                    val minor = numbers.getOrNull(1) ?: 0
                    val patch = numbers.getOrNull(2) ?: 0

                    return SemVer(major, minor, patch, prePart)
                }
            }
        }

        fun isNewerVersion(remoteTag: String, currentVersion: String): Boolean {
            val remoteSemVer = SemVer.parse(remoteTag) ?: return false
            val currentSemVer = SemVer.parse(currentVersion) ?: return false

            return remoteSemVer > currentSemVer
        }
    }
}
