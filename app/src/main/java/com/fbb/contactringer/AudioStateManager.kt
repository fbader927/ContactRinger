package com.fbb.contactringer

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.*
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
    private var vibrator: Vibrator? = null

    fun scheduleResetAudioState(context: Context, delayMillis: Long = 3000) {
        Handler(Looper.getMainLooper()).postDelayed({
            resetAudioState(context)
        }, delayMillis)
    }

    fun overrideSilentMode(context: Context, contact: Contact?) {
        if (resettingAudioState) return

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (originalState == null) {
            originalState = AudioState(
                ringerMode = audioManager.ringerMode,
                ringVolume = audioManager.getStreamVolume(AudioManager.STREAM_RING),
                notificationVolume = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION),
                systemVolume = audioManager.getStreamVolume(AudioManager.STREAM_SYSTEM),
                dndMode = notificationManager.currentInterruptionFilter,
                currentRingtone = RingtoneManager.getActualDefaultRingtoneUri(
                    context,
                    RingtoneManager.TYPE_RINGTONE
                )
            )
            Log.d("AudioStateManager", "Original state saved: $originalState")
        }

        if (contact != null) {
            if (contact.onlyVibrate) {
                audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                audioManager.setStreamVolume(AudioManager.STREAM_RING, 0, 0)
                Log.d("AudioStateManager", "Ringer mode set to NORMAL with zero volume for contact ${contact.name}")

                vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                val vibrationEffect = VibrationEffect.createWaveform(
                    longArrayOf(0, 1500, 3000),
                    0
                )
                vibrator?.vibrate(vibrationEffect)
                Log.d("AudioStateManager", "Vibration started for contact ${contact.name}")
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (notificationManager.isNotificationPolicyAccessGranted) {
                        notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                    }
                }
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
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        originalState?.let { state ->
            try {
                vibrator?.cancel()
                vibrator = null
                Log.d("AudioStateManager", "Vibration stopped")

                audioManager.setStreamVolume(AudioManager.STREAM_RING, state.ringVolume, 0)
                audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, state.notificationVolume, 0)
                audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, state.systemVolume, 0)

                audioManager.ringerMode = state.ringerMode

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (notificationManager.isNotificationPolicyAccessGranted) {
                        if (state.dndMode != NotificationManager.INTERRUPTION_FILTER_UNKNOWN) {
                            notificationManager.setInterruptionFilter(state.dndMode)
                        } else {
                            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                        }
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
