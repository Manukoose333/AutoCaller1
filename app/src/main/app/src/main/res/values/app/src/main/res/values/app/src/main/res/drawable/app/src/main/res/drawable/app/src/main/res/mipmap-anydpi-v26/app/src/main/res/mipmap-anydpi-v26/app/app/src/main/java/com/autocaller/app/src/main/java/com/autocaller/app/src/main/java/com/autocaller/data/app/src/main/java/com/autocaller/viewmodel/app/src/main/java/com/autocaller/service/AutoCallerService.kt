package com.autocaller.service

import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.autocaller.AutoCallerApp
import com.autocaller.MainActivity
import com.autocaller.R
import com.autocaller.data.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AutoCallerService : LifecycleService() {

    private lateinit var repository: SettingsRepository
    private lateinit var alarmManager: AlarmManager
    private lateinit var telephonyManager: TelephonyManager

    override fun onCreate() {
        super.onCreate()
        repository = SettingsRepository(applicationContext)
        alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> handleStart()
            ACTION_STOP -> handleStop()
            ACTION_CALL_TICK -> handleCallTick()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private fun handleStart() {
        startForeground(AutoCallerApp.NOTIFICATION_ID, buildNotification("AutoCaller is running"))
        lifecycleScope.launch {
            repository.saveIsRunning(true)
            val intervalMinutes = repository.intervalMinutesFlow.first()
            scheduleNextAlarm(intervalMinutes)
            attemptCall()
        }
    }

    private fun handleStop() {
        lifecycleScope.launch { repository.saveIsRunning(false) }
        cancelAlarm()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun handleCallTick() {
        lifecycleScope.launch {
            val isRunning = repository.isRunningFlow.first()
            if (!isRunning) return@launch
            attemptCall()
            val intervalMinutes = repository.intervalMinutesFlow.first()
            scheduleNextAlarm(intervalMinutes)
        }
    }

    private suspend fun attemptCall() {
        val phoneNumber = repository.phoneNumberFlow.first()
        if (phoneNumber.isBlank()) return
        val callState = telephonyManager.callState
        if (callState != TelephonyManager.CALL_STATE_IDLE) return
        try {
            val callIntent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:${Uri.encode(phoneNumber)}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(callIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to place call", e)
        }
    }

    private fun scheduleNextAlarm(intervalMinutes: Int) {
        val triggerAtMillis = SystemClock.elapsedRealtime() + intervalMinutes * 60_000L
        val pendingIntent = alarmPendingIntent()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    private fun cancelAlarm() {
        alarmManager.cancel(alarmPendingIntent())
    }

    private fun alarmPendingIntent(): PendingIntent {
        val intent = Intent(this, AlarmReceiver::class.java).apply {
            action = ACTION_CALL_TICK
        }
        return PendingIntent.getBroadcast(
            this, REQUEST_CODE_ALARM, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildNotification(contentText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPending = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = Intent(this, AutoCallerService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, AutoCallerApp.CHANNEL_ID)
            .setContentTitle("AutoCaller")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setContentIntent(openAppPending)
            .addAction(0, "Stop", stopPending)
            .build()
    }

    companion object {
        private const val TAG = "AutoCallerService"
        private const val REQUEST_CODE_ALARM = 42
        const val ACTION_START = "com.autocaller.ACTION_START"
        const val ACTION_STOP = "com.autocaller.ACTION_STOP"
        const val ACTION_CALL_TICK = "com.autocaller.ACTION_CALL_TICK"

        fun startIntent(context: Context) =
            Intent(context, AutoCallerService::class.java).apply { action = ACTION_START }

        fun stopIntent(context: Context) =
            Intent(context, AutoCallerService::class.java).apply { action = ACTION_STOP }
    }
}
