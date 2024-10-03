package com.fbb.contactringer

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.app.Person

class SMSNotif : NotificationListenerService() {
    private var lastProcessedTime: Long = 0
    private val PROCESSING_COOLDOWN = 2000
    private val SMS_RING_DURATION = 3000L
    private var activeCallNotificationKey: String? = null

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.let {
            val packageName = sbn.packageName
            Log.d("SMSNotification", "Notification from package: $packageName")

            when (packageName) {
                Telephony.Sms.getDefaultSmsPackage(this) -> {
                    Log.d("SMSNotification", "Notification is from the default SMS app")
                    val extras = sbn.notification.extras
                    val smsSender = extras.getString("android.title")
                    smsSender?.let { sender ->
                        handleIncomingSMS(sender, this)
                    } ?: run {
                        Log.d("SMSNotification", "SMS sender is null, skipping handling")
                    }
                }
                "com.samsung.android.incallui" -> {
                    Log.d("SMSNotification", "Incoming call detected via notification")
                    handleIncomingCallNotification(sbn)
                }
                else -> {
                    Log.d("SMSNotification", "Notification is not from the default SMS app or call UI")
                }
            }
        } ?: run {
            Log.d("SMSNotification", "StatusBarNotification is null")
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn?.let {
            val packageName = sbn.packageName
            Log.d("SMSNotification", "Notification removed from package: $packageName")

            if (packageName == "com.samsung.android.incallui" && sbn.key == activeCallNotificationKey) {
                Log.d("SMSNotification", "Call notification removed. Resetting volume.")
                AudioStateManager.resetAudioState(this)
                activeCallNotificationKey = null
            }
        }
    }

    private fun handleIncomingCallNotification(sbn: StatusBarNotification) {
        val contactsManager = ContactsManager(this)
        val extras = sbn.notification.extras

        val callPerson = extras.getParcelable<Person>("android.callPerson")
        if (callPerson != null) {
            val personUri = callPerson.uri
            val personName = callPerson.name?.toString()

            Log.d("SMSNotification", "Person URI: $personUri, Name: $personName")

            val phoneNumber = personUri?.let {
                val parsedUri = Uri.parse(it)
                if (parsedUri.scheme == "tel") parsedUri.schemeSpecificPart else null
            }

            var contact: Contact? = null

            if (phoneNumber != null) {
                contact = ContactsManager.getContactByNumber(this, phoneNumber)
            }

            if (contact == null && personName != null) {
                contact = contactsManager.getSelectedContacts().find { it.name.equals(personName, ignoreCase = true) }
            }

            contact?.let {
                Handler(Looper.getMainLooper()).postDelayed({
                    AudioStateManager.overrideSilentMode(this, contact)
                    adjustVolumeForContact(contact, this)
                    Log.d("SMSNotification", "Call from selected contact: ${contact.name}")
                    activeCallNotificationKey = sbn.key
                }, 1000)
            } ?: run {
                Log.d("SMSNotification", "Call from unknown contact. Skipping volume adjustment.")
            }
        } else {
            Log.d("SMSNotification", "No Person object found in notification. Skipping.")
        }
    }

    private fun handleIncomingSMS(sender: String, context: Context) {
        Log.d("SMSNotification", "Handling SMS from sender: $sender")

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastProcessedTime > PROCESSING_COOLDOWN) {
            val contactsManager = ContactsManager(context)
            var contact = contactsManager.getSelectedContacts().find { it.name == sender }

            if (contact == null) {
                val possiblePhoneNumber = extractPhoneNumberFromSender(sender)
                if (possiblePhoneNumber != null) {
                    val normalizedSender = normalizePhoneNumber(possiblePhoneNumber)
                    Log.d("SMSNotification", "Normalized sender phone number: $normalizedSender")
                    contact = ContactsManager.getContactByNumber(context, normalizedSender)
                }
            }

            if (contact != null) {
                Log.d("SMSNotification", "Sender is in selected contacts: ${contact.name}")
                Log.d("SMSNotification", "Adjusting ringer volume for contact: ${contact.name}")

                AudioStateManager.overrideSilentMode(context, contact)
                adjustVolumeForContact(contact, context)

                Handler(Looper.getMainLooper()).postDelayed({
                    playNotificationSound(context, contact)
                }, 100)

                AudioStateManager.scheduleResetAudioState(context, SMS_RING_DURATION)
                lastProcessedTime = currentTime
            } else {
                Log.d("SMSNotification", "Sender not in selected contacts: $sender")
            }
        } else {
            Log.d("SMSNotification", "Skipping processing due to recent activity")
        }
    }

    private fun adjustVolumeForContact(contact: Contact, context: Context) {
        if (contact.onlyVibrate) {
            Log.d("SMSNotification", "Contact ${contact.name} is set to Only Vibrate. Skipping volume adjustment.")
            return
        }
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
        val contactVolume = (maxVolume * contact.volume / 100f).toInt()
        audioManager.setStreamVolume(AudioManager.STREAM_RING, contactVolume, 0)
        Log.d("SMSNotification", "Adjusted volume to: $contactVolume for contact: ${contact.name}")
    }

    private fun playNotificationSound(context: Context, contact: Contact) {
        if (contact.onlyVibrate) {
            Log.d("SMSNotification", "Contact ${contact.name} is set to Only Vibrate. Skipping notification sound.")
            return
        }
        try {
            val notificationUri = contact.ringtone?.let { Uri.parse(it) }
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            if (notificationUri != null && context.contentResolver.getType(notificationUri) != null) {
                val mediaPlayer = MediaPlayer()
                mediaPlayer.setDataSource(context, notificationUri)
                mediaPlayer.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                mediaPlayer.prepare()
                mediaPlayer.start()

                mediaPlayer.setOnCompletionListener { mp ->
                    mp.release()
                }

                Log.d("SMSNotification", "Playing notification sound for contact: ${contact.name}")
            } else {
                Log.e("SMSNotification", "Invalid URI or no access to notification sound for contact: ${contact.name}")
            }
        } catch (e: Exception) {
            Log.e("SMSNotification", "Error playing notification sound: ${e.message}")
        }
    }

    private fun extractPhoneNumberFromSender(sender: String): String? {
        return if (sender.matches(Regex("\\+?\\d+"))) {
            sender
        } else {
            null
        }
    }

    private fun normalizePhoneNumber(phoneNumber: String): String {
        return phoneNumber.replace(Regex("[^\\d]"), "")
    }
}
