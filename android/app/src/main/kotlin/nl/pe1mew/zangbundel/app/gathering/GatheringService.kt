// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.gathering

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import nl.pe1mew.zangbundel.app.MainActivity
import nl.pe1mew.zangbundel.app.R
import nl.pe1mew.zangbundel.app.ZangbundelApp

/**
 * Foreground service of type `connectedDevice` that owns the one gathering engine for leader and
 * singer alike (architecture A-06). The notification is silent, low importance, opens the app, has
 * no actions, and its text is fixed and neutral, also on the lock screen (assessment SP-17).
 * A singer who swipes the app away leaves the gathering: the service stays until the leave notice of
 * ble-protocol 10.11 is out, so the leader's count drops at once instead of never. A leader's gathering
 * keeps running; the notification brings the leader back.
 */
class GatheringService : LifecycleService() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> startInForeground()
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val gathering = (application as ZangbundelApp).container.gathering
        if (gathering.mode.value !is Gathering.Mode.Singing) return
        lifecycleScope.launch {
            gathering.leave(keepService = true)
            gathering.awaitLeave()
            ServiceCompat.stopForeground(this@GatheringService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun startInForeground() {
        createChannel()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_gathering_running))
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // the public version is the same neutral text
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel_gathering), NotificationManager.IMPORTANCE_LOW)
            channel.setShowBadge(false)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "gathering"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "nl.pe1mew.zangbundel.app.action.START_GATHERING"
        const val ACTION_STOP = "nl.pe1mew.zangbundel.app.action.STOP_GATHERING"

        fun start(context: Context) {
            val intent = Intent(context, GatheringService::class.java).setAction(ACTION_START)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, GatheringService::class.java).setAction(ACTION_STOP))
        }
    }
}
