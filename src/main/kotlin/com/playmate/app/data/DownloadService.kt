package com.playmate.app

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.playmate.app.data.SaveCenter
import com.playmate.app.data.SaveJob
import com.playmate.app.data.SaveState
import com.playmate.app.data.formatSpeed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Keeps downloads running when the app is in the background or the screen is off,
 * and shows their progress in a notification with Pause / Resume and Cancel buttons.
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var ticker: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> SaveCenter.cancelAll()
            ACTION_PAUSE -> SaveCenter.pauseAll()
            ACTION_RESUME -> SaveCenter.resumeAll(this)
        }

        ServiceCompat.startForeground(
            this,
            ONGOING_ID,
            DownloadNotifications.progress(this),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )

        if (ticker?.isActive != true) {
            ticker = scope.launch {
                while (SaveCenter.liveJobs().isNotEmpty()) {
                    NotificationManagerCompat.from(this@DownloadService).let { manager ->
                        runCatching { notifyOngoing(manager) }
                    }
                    delay(800)
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun notifyOngoing(manager: NotificationManagerCompat) {
        manager.notify(ONGOING_ID, DownloadNotifications.progress(this))
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_CANCEL = "com.playmate.app.CANCEL_DOWNLOADS"
        const val ACTION_PAUSE = "com.playmate.app.PAUSE_DOWNLOADS"
        const val ACTION_RESUME = "com.playmate.app.RESUME_DOWNLOADS"
        const val ONGOING_ID = 4101

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
        }
    }
}

object DownloadNotifications {
    private const val CH_PROGRESS = "downloads_progress"
    private const val CH_DONE = "downloads_done"

    private fun ensureChannels(ctx: Context) {
        val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CH_PROGRESS) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CH_PROGRESS, "Downloads in progress", NotificationManager.IMPORTANCE_LOW)
            )
        }
        if (manager.getNotificationChannel(CH_DONE) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CH_DONE, "Finished downloads", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    private fun openApp(ctx: Context): PendingIntent =
        PendingIntent.getActivity(
            ctx,
            0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun serviceAction(ctx: Context, requestCode: Int, action: String): PendingIntent =
        PendingIntent.getService(
            ctx,
            requestCode,
            Intent(ctx, DownloadService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    fun progress(ctx: Context): Notification {
        ensureChannels(ctx)
        val jobs = SaveCenter.liveJobs()
        val single = jobs.singleOrNull()
        val total = jobs.sumOf { it.total }
        val done = jobs.sumOf { it.bytes.get() }
        val percent = if (total > 0) ((done * 100) / total).toInt().coerceIn(0, 100) else 0
        val downloading = jobs.any { it.state == SaveState.Downloading } && total > 0
        val allPaused = jobs.isNotEmpty() && jobs.all { it.state == SaveState.Paused }
        val speed = jobs.filter { it.state == SaveState.Downloading }.sumOf { it.speed }
        val speedText = if (speed > 0) "  ·  ${formatSpeed(speed)}" else ""

        val offline = jobs.isNotEmpty() && jobs.all { it.offline }

        val title = when {
            jobs.size > 1 -> if (offline) "Downloading ${jobs.size} videos" else "Saving ${jobs.size} files to phone"
            single != null -> single.name
            else -> "Playmate"
        }
        val text = when {
            jobs.isEmpty() -> "Finishing up"
            single != null -> when (single.state) {
                SaveState.Queued -> single.message ?: "Waiting to start"
                SaveState.Downloading -> "Downloading  $percent%$speedText"
                SaveState.Joining -> "Getting the file ready"
                SaveState.Saving -> if (offline) "Finishing up" else "Saving to your phone"
                SaveState.Paused -> if (single.autoPaused) "Waiting for Wi-Fi" else "Paused  $percent%"
                else -> "Finishing up"
            }
            allPaused -> "Paused  ·  $percent% overall"
            else -> "$percent% overall$speedText"
        }

        val builder = NotificationCompat.Builder(ctx, CH_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setProgress(100, percent, !downloading && !allPaused)
            .setContentIntent(openApp(ctx))

        if (allPaused) {
            builder.addAction(0, "Resume", serviceAction(ctx, 3, DownloadService.ACTION_RESUME))
        } else {
            builder.addAction(0, "Pause", serviceAction(ctx, 2, DownloadService.ACTION_PAUSE))
        }
        builder.addAction(0, "Cancel", serviceAction(ctx, 1, DownloadService.ACTION_CANCEL))
        return builder.build()
    }

    @SuppressLint("MissingPermission")
    fun finished(ctx: Context, job: SaveJob) {
        if (job.state == SaveState.Cancelled || job.state == SaveState.Paused) return
        ensureChannels(ctx)
        val ok = job.state == SaveState.Done
        val text = if (ok) {
            listOfNotNull(job.name, job.place).joinToString("  ·  ")
        } else {
            job.message ?: "Something went wrong."
        }

        val uri: Uri? = job.uri
        val tap: PendingIntent = if (ok && uri != null) {
            val view = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, job.target.mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            PendingIntent.getActivity(
                ctx,
                job.id,
                view,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        } else if (ok && job.offline) {
            PendingIntent.getActivity(
                ctx,
                job.id,
                Intent(ctx, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_OPEN_DOWNLOADS, true)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        } else {
            openApp(ctx)
        }

        val notification = NotificationCompat.Builder(ctx, CH_DONE)
            .setSmallIcon(if (ok) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
            .setContentTitle(
                when {
                    ok && job.offline -> "Download complete"
                    ok -> "Saved to your phone"
                    job.offline -> "Couldn't download"
                    else -> "Couldn't save"
                }
            )
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(2000 + job.id, notification) }
    }
}
