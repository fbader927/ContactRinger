package com.example.contactringer

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log

object AudioStateManager {
    private data class AudioState(
        val ringerMode: Int,
        val ringVolume: Int,
        val notificationVolume: Int,
        val systemVolume: Int,
        val dndMode: Int,
        val currentRingtone: Uri?
    )

    fun overrideSilentMode(context: Context, contact: Contact) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val prefs = context.getSharedPreferences("AudioStateManagerPrefs", Context.MODE_PRIVATE)

        val originalState = AudioState(
            ringerMode = audioManager.ringerMode,
            ringVolume = audioManager.getStreamVolume(AudioManager.STREAM_RING),
            notificationVolume = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION),
            systemVolume = audioManager.getStreamVolume(AudioManager.STREAM_SYSTEM),
            dndMode = Settings.Global.getInt(context.contentResolver, "zen_mode", 0),
            currentRingtone = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
        )

        with(prefs.edit()) {
            putInt("originalRingerMode", originalState.ringerMode)
            putInt("originalRingVolume", originalState.ringVolume)
            putInt("originalNotificationVolume", originalState.notificationVolume)
            putInt("originalSystemVolume", originalState.systemVolume)
            putInt("originalDndMode", originalState.dndMode)
            putString("originalRingtone", originalState.currentRingtone?.toString())
            apply()
        }

        if (contact.onlyVibrate) {
            audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
        } else {
            audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
            val contactVolume = (maxVolume * contact.volume / 100f).toInt()
            audioManager.setStreamVolume(AudioManager.STREAM_RING, contactVolume, AudioManager.FLAG_SHOW_UI or AudioManager.FLAG_PLAY_SOUND)
        }

        contact.ringtone?.let { RingtoneManager.setActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE, Uri.parse(it)) }
    }

    fun resetAudioState(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val prefs = context.getSharedPreferences("AudioStateManagerPrefs", Context.MODE_PRIVATE)

        val originalState = AudioState(
            ringerMode = prefs.getInt("originalRingerMode", AudioManager.RINGER_MODE_NORMAL),
            ringVolume = prefs.getInt("originalRingVolume", audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)),
            notificationVolume = prefs.getInt("originalNotificationVolume", audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION)),
            systemVolume = prefs.getInt("originalSystemVolume", audioManager.getStreamMaxVolume(AudioManager.STREAM_SYSTEM)),
            dndMode = prefs.getInt("originalDndMode", NotificationManager.INTERRUPTION_FILTER_ALL),
            currentRingtone = prefs.getString("originalRingtone", null)?.let { Uri.parse(it) }
        )

        try {
            audioManager.setStreamVolume(AudioManager.STREAM_RING, originalState.ringVolume, 0)
            audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, originalState.notificationVolume, 0)
            audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, originalState.systemVolume, 0)
            audioManager.ringerMode = originalState.ringerMode

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (notificationManager.isNotificationPolicyAccessGranted) {
                    notificationManager.setInterruptionFilter(originalState.dndMode)
                }
            }

            originalState.currentRingtone?.let {
                RingtoneManager.setActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE, it)
            }

        } catch (e: Exception) {
            Log.e("AudioStateManager", "Error resetting audio state: ${e.message}")
        }

        with(prefs.edit()) {
            clear()
            apply()
        }
    }

    fun scheduleResetAudioState(context: Context, delayMillis: Long = 10000) {
        Handler(Looper.getMainLooper()).postDelayed({
            resetAudioState(context)
        }, delayMillis)
    }
}

class ResetAudioStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AudioStateManager.resetAudioState(context)
    }
}
