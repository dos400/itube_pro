package uz.donfinity.itubepro.system

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Installs an update APK with the PackageInstaller session API. The download is streamed
 * straight into the session, so there's no temporary file and no FileProvider.
 *
 * Flow: [install] writes + commits the session → the system calls [InstallStatusReceiver]
 * with STATUS_PENDING_USER_ACTION → we open the system's "Update this app?" dialog → on
 * success Android replaces the app (and kills this process). On Android 12+ later updates
 * may skip the dialog, because by then this app is the installer of record.
 *
 * The APK must be signed with the same key as the installed app, or Android refuses it
 * (see the signing comment in app/build.gradle.kts).
 */
@Singleton
class ApkInstaller @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val client: OkHttpClient,
) {
    sealed interface Result {
        /** The user closed the system dialog. */
        data object Cancelled : Result
        data class Failed(val message: String?) : Result
    }

    /** Outcomes the receiver reports. Success isn't reported: the process is replaced. */
    val results: SharedFlow<Result> = resultFlow.asSharedFlow()

    /** "Install unknown apps" must be allowed for this app (a per-app system setting). */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun installPermissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())

    /** Downloads [apkUrl] into a new session and commits it. [onProgress] gets 0..1. */
    suspend fun install(apkUrl: String, expectedSize: Long, onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                client.newCall(Request.Builder().url(apkUrl).build()).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("APK download HTTP ${response.code}")
                    val body = response.body
                    val total = body.contentLength().takeIf { it > 0 } ?: expectedSize
                    session.openWrite("base.apk", 0, total).use { out ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            var written = 0L
                            while (true) {
                                ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                out.write(buffer, 0, read)
                                written += read
                                if (total > 0) onProgress((written.toFloat() / total).coerceAtMost(1f))
                            }
                        }
                        session.fsync(out)
                    }
                }
                session.commit(statusReceiver(sessionId))
            }
        } catch (e: Throwable) {
            // Includes CancellationException: drop the half-written session, then rethrow.
            runCatching { installer.abandonSession(sessionId) }
                .onFailure { Log.w(TAG, "Couldn't abandon session $sessionId", it) }
            throw e
        }
    }

    private fun statusReceiver(sessionId: Int): IntentSender {
        // MUTABLE: the system adds the status extras to this intent.
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        val intent = Intent(context, InstallStatusReceiver::class.java).setPackage(context.packageName)
        return PendingIntent.getBroadcast(context, sessionId, intent, flags).intentSender
    }

    companion object {
        private const val BUFFER_SIZE = 64 * 1024
        internal const val TAG = "ApkInstaller"
        internal val resultFlow = MutableSharedFlow<Result>(extraBufferCapacity = 4)
    }
}

/** Receives the session status from the system (registered, not exported, in the manifest). */
class InstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        Log.i(ApkInstaller.TAG, "Install status $status: $message")
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                if (confirm == null) {
                    ApkInstaller.resultFlow.tryEmit(ApkInstaller.Result.Failed(message))
                } else {
                    // The user just tapped "Update" in our UI, so the app is in the foreground
                    // and may start the system dialog.
                    context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            PackageInstaller.STATUS_FAILURE_ABORTED -> ApkInstaller.resultFlow.tryEmit(ApkInstaller.Result.Cancelled)
            else -> ApkInstaller.resultFlow.tryEmit(ApkInstaller.Result.Failed(message))
        }
    }
}
