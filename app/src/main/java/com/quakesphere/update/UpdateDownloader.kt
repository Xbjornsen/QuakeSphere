package com.quakesphere.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Handles the "tap Update → download APK → launch installer" flow.
 *
 * We use [DownloadManager] rather than a manual OkHttp download because it
 * gives us a proper system notification with progress bar, retries on flaky
 * connectivity, and survives the activity being destroyed mid-download.
 *
 * The APK lands in `externalCacheDir/updates/` which is gitignored by Android
 * itself (no permission needed, evicted with the app) and is wired up to our
 * FileProvider so the install Intent gets a content:// URI.
 */
@Singleton
class UpdateDownloader @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val _state = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val state: StateFlow<DownloadState> = _state

    private val downloadManager: DownloadManager
        get() = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    /**
     * Kick off a download for the given [UpdateInfo]. Result will surface via
     * [state]; on success we automatically launch the package installer.
     */
    fun startDownload(info: UpdateInfo) {
        if (_state.value is DownloadState.Downloading) return

        val targetDir = File(context.externalCacheDir, "updates").apply { mkdirs() }
        // Sweep old APKs so we don't accumulate them.
        targetDir.listFiles()?.forEach { if (it.name != info.apkName) it.delete() }
        val targetFile = File(targetDir, info.apkName)

        if (targetFile.exists() && targetFile.length() == info.apkSizeBytes) {
            // Already have the matching APK on disk — skip straight to install.
            _state.value = DownloadState.Ready(targetFile)
            launchInstaller(targetFile)
            return
        }
        targetFile.delete()

        val req = DownloadManager.Request(Uri.parse(info.apkUrl)).apply {
            setTitle("QuakeStation ${info.tag}")
            setDescription("Downloading update…")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationUri(Uri.fromFile(targetFile))
            setMimeType("application/vnd.android.package-archive")
        }
        val id = downloadManager.enqueue(req)
        _state.value = DownloadState.Downloading(id)

        // Listen for completion. Receiver self-unregisters after firing.
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                val finishedId = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: -1L
                if (finishedId != id) return
                try { context.unregisterReceiver(this) } catch (_: Throwable) {}

                val query = DownloadManager.Query().setFilterById(id)
                downloadManager.query(query)?.use { c2 ->
                    if (!c2.moveToFirst()) {
                        _state.value = DownloadState.Failed("download row missing")
                        return
                    }
                    val statusCol = c2.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val status = c2.getInt(statusCol)
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        _state.value = DownloadState.Ready(targetFile)
                        launchInstaller(targetFile)
                    } else {
                        _state.value = DownloadState.Failed("status=$status")
                    }
                }
            }
        }
        // From Android 13 (TIRAMISU) onwards, runtime-registered receivers
        // for non-system broadcasts need an explicit exported flag.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_EXPORTED
            )
        } else {
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                ContextCompat.RECEIVER_EXPORTED
            )
        }
    }

    /**
     * Hand the downloaded APK to the system package installer via a content://
     * URI. The user has to confirm in the installer dialog; if they've never
     * granted "Install unknown apps" to QuakeStation, Android shows that
     * prompt first.
     */
    private fun launchInstaller(apk: File) {
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", apk
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(installIntent)
        } catch (t: Throwable) {
            _state.value = DownloadState.Failed("installer launch: ${t.message}")
        }
    }
}

sealed class DownloadState {
    data object Idle : DownloadState()
    data class Downloading(val downloadId: Long) : DownloadState()
    data class Ready(val apk: File) : DownloadState()
    data class Failed(val reason: String) : DownloadState()
}
