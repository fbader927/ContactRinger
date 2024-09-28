package com.fbb.contactringer

import android.app.NotificationManager
import android.content.Context
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

    private var resettingAudioState: Boolean = false
    private var originalState: AudioState? = null

    fun scheduleResetAudioState(context: Context, delayMillis: Long = 3000) {
        Handler(Looper.getMainLooper()).postDelayed({
            resetAudioState(context)
        }, delayMillis)
    }

    fun overrideSilentMode(context: Context, contact: Contact?) {
        if (resettingAudioState) return

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        if (originalState == null) {
            originalState = AudioState(
                ringerMode = audioManager.ringerMode,
                ringVolume = audioManager.getStreamVolume(AudioManager.STREAM_RING),
                notificationVolume = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION),
                systemVolume = audioManager.getStreamVolume(AudioManager.STREAM_SYSTEM),
                dndMode = Settings.Global.getInt(context.contentResolver, "zen_mode", 0),
                currentRingtone = RingtoneManager.getActualDefaultRingtoneUri(
                    context,
                    RingtoneManager.TYPE_RINGTONE
                )
            )
            Log.d("AudioStateManager", "Original state saved: $originalState")
        }

        if (contact != null) {
            if (contact.onlyVibrate) {
                audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
            } else {
                audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
                val contactVolume = (maxVolume * contact.volume / 100f).toInt()
                audioManager.setStreamVolume(
                    AudioManager.STREAM_RING,
                    contactVolume,
                    AudioManager.FLAG_SHOW_UI or AudioManager.FLAG_PLAY_SOUND
                )
                Log.d("AudioStateManager", "Volume set to $contactVolume for contact ${contact.name}")
            }

            contact.ringtone?.let {
                try {
                    RingtoneManager.setActualDefaultRingtoneUri(
                        context,
                        RingtoneManager.TYPE_RINGTONE,
                        Uri.parse(it)
                    )
                    Log.d("AudioStateManager", "Ringtone set for contact ${contact.name}")
                } catch (e: Exception) {
                    Log.e("AudioStateManager", "Error setting ringtone: ${e.message}")
                }
            }
        }
    }


    fun resetAudioState(context: Context) {
        if (resettingAudioState || originalState == null) {
            Log.d("AudioStateManager", "No original state to reset or resetting already in progress")
            return
        }

        resettingAudioState = true
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        originalState?.let { state ->
            try {
                audioManager.setStreamVolume(AudioManager.STREAM_RING, state.ringVolume, 0)
                audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, state.notificationVolume, 0)
                audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, state.systemVolume, 0)

                audioManager.ringerMode = state.ringerMode

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    if (notificationManager.isNotificationPolicyAccessGranted) {
                        notificationManager.setInterruptionFilter(state.dndMode)
                    }
                }

                state.currentRingtone?.let {
                    try {
                        RingtoneManager.setActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE, it)
                        Log.d("AudioStateManager", "Ringtone reset to: $it")
                    } catch (e: Exception) {
                        Log.e("AudioStateManager", "Failed to reset ringtone URI: ${e.message}")
                    }
                }

                Log.d("AudioStateManager", "Reset complete. Ringer mode: ${audioManager.ringerMode}, Volume: ${audioManager.getStreamVolume(AudioManager.STREAM_RING)}")
            } catch (e: Exception) {
                Log.e("AudioStateManager", "Error resetting audio state: ${e.message}")
            } finally {
                resettingAudioState = false
                originalState = null
            }
        }
    }


}
