package dev.linakbed.app.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import dev.linakbed.app.MainActivity
import dev.linakbed.app.R
import dev.linakbed.app.feature.confirm.CancelMotionReceiver
import dev.linakbed.app.feature.confirm.PendingMotionActivity
import dev.linakbed.app.ui.BedErrorMessages
import dev.linakbed.domain.remote.RemoteChannel
import dev.linakbed.domain.remote.RemoteCommand
import dev.linakbed.domain.remote.RemoteOutcome
import dev.linakbed.domain.remote.RemoteRequest
import dev.linakbed.domain.remote.RemoteResult

/** 앱이 띄우는 모든 알림 (서비스 진행 / 원격 명령 결과 / 실행 전 확인). */
class BedNotifications(
    private val context: Context,
) {
    private val manager: NotificationManager get() = context.getSystemService(NotificationManager::class.java)

    fun ensureChannels() {
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_SERVICE, context.getString(R.string.channel_service), NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(CHANNEL_ALERTS, context.getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_DEFAULT),
                // S9: 잠금 화면 위에 띄우는 큰 취소 화면. 소리 없이 짧게 진동만.
                NotificationChannel(
                    CHANNEL_CONFIRM,
                    context.getString(R.string.channel_confirm),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    setSound(null, null)
                    enableVibration(true)
                    vibrationPattern = CONFIRM_VIBRATION
                },
            ),
        )
    }

    fun serviceNotification(): Notification =
        Notification
            .Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.service_running))
            .setContentIntent(openApp())
            .setOngoing(true)
            .build()

    fun showResult(
        request: RemoteRequest,
        outcome: RemoteOutcome,
    ) {
        val channelName = channelName(request.channel)
        val text =
            when (val result = outcome.result) {
                RemoteResult.Executed -> context.getString(R.string.remote_result_executed, describe(request.command))
                is RemoteResult.Failed -> {
                    val error = BedErrorMessages.of(result.error)
                    val reason = if (error.arg == null) context.getString(error.res) else context.getString(error.res, error.arg)
                    context.getString(R.string.remote_result_failed, channelName, reason)
                }
                is RemoteResult.Rejected -> return
            }
        notify(
            ID_RESULT_BASE + (request.requestId.hashCode() and RESULT_ID_MASK),
            Notification
                .Builder(context, CHANNEL_ALERTS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.app_label_launcher))
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
                .setContentIntent(openApp())
                .setAutoCancel(true)
                .build(),
        )
    }

    /** S9: 전체 화면 알림(권한이 없으면 heads-up) + "취소" 액션. */
    fun showConfirmation(
        requestId: String,
        presetLabel: String,
    ) {
        val fullScreen =
            PendingIntent.getActivity(
                context,
                REQUEST_CODE_CONFIRM,
                Intent(context, PendingMotionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val cancel =
            PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_CANCEL,
                CancelMotionReceiver.intent(context, requestId),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        notify(
            ID_CONFIRM,
            Notification
                .Builder(context, CHANNEL_CONFIRM)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.confirm_title, presetLabel))
                .setContentText(context.getString(R.string.confirm_text))
                .setCategory(Notification.CATEGORY_ALARM)
                .setFullScreenIntent(fullScreen, true)
                .setContentIntent(fullScreen)
                .addAction(Notification.Action.Builder(null, context.getString(R.string.confirm_cancel), cancel).build())
                .setOngoing(true)
                .build(),
        )
    }

    fun cancelConfirmation() {
        manager.cancel(ID_CONFIRM)
    }

    private fun notify(
        id: Int,
        notification: Notification,
    ) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        ensureChannels()
        manager.notify(id, notification)
    }

    private fun openApp(): PendingIntent =
        PendingIntent.getActivity(
            context,
            REQUEST_CODE_OPEN,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )

    private fun channelName(channel: RemoteChannel): String =
        when (channel) {
            RemoteChannel.SMARTTHINGS_NOTIFICATION -> context.getString(R.string.remote_channel_smartthings)
        }

    private fun describe(command: RemoteCommand): String =
        when (command) {
            is RemoteCommand.Preset -> context.getString(R.string.preset_fallback_name, command.slot.number)
            RemoteCommand.Stop -> context.getString(R.string.control_stop)
            is RemoteCommand.Light -> context.getString(R.string.control_light)
        }

    companion object {
        const val CHANNEL_SERVICE = "bed_service"
        const val CHANNEL_ALERTS = "bed_alerts"
        const val CHANNEL_CONFIRM = "bed_confirm"
        const val ID_SERVICE = 1001
        const val ID_CONFIRM = 1002
        private const val ID_RESULT_BASE = 2000
        private const val RESULT_ID_MASK = 0xFFF
        private const val REQUEST_CODE_OPEN = 10
        private const val REQUEST_CODE_CONFIRM = 11
        private const val REQUEST_CODE_CANCEL = 12
        private const val CONFIRM_VIBRATION_MS = 200L
        private val CONFIRM_VIBRATION = longArrayOf(0, CONFIRM_VIBRATION_MS)
    }
}
