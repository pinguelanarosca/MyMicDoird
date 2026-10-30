package com.example.model

import java.io.File

data class GitHubRelease(
    val tagName: String,
    val name: String,
    val body: String,
    val publishedAt: String,
    val apkDownloadUrl: String?,
    val apkFileName: String?,
    val apkSizeBytes: Long
)

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    data class Available(val release: GitHubRelease) : UpdateState()
    data class UpToDate(val currentVersion: String) : UpdateState()
    data class Downloading(
        val progressPercentage: Int,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : UpdateState()
    data class ReadyToInstall(val apkFile: File, val release: GitHubRelease) : UpdateState()
    data class Error(val message: String) : UpdateState()
}
