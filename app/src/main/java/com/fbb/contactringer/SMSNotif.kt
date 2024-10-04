package com.fbb.contactringer

import android.content.Context
import android.media.AudioAttributes
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
    private val processedNotificationKeys = mutableSetOf<String>()

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.let {
            val packageName = sbn.packageName
            val notificationKey = sbn.key
            Log.d("SMSNotification", "Notification from package: $packageName, key: $notificationKey")

            if (processedNotificationKeys.contains(notificationKey)) {
                Log.d("SMSNotification", "Notification already processed: $notificationKey")
                return
            }

            when (packageName) {
                Telephony.Sms.getDefaultSmsPackage(this) -> {
                    Log.d("SMSNotification", "Notification is from the default SMS app")
                    // Cancel the notification to prevent default sound from playing
                    cancelNotification(sbn.key)
                    val extras = sbn.notification.extras
                    val smsSender = extras.getString("android.title")
                    smsSender?.let { sender ->
                        handleIncomingSMS(sender, this)
                        processedNotificationKeys.add(notificationKey)
                    } ?: run {
                        Log.d("SMSNotification", "SMS sender is null, skipping handling")
                    }
                }
                "com.samsung.android.incallui" -> {
                    Log.d("SMSNotification", "Incoming call detected via notification")
                    handleIncomingCallNotification(sbn)
                    processedNotificationKeys.add(notificationKey)
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
            val notificationKey = sbn.key
            Log.d("SMSNotification", "Notification removed from package: $packageName, key: $notificationKey")

            processedNotificationKeys.remove(notificationKey)

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
                    Log.d("SMSNotification", "Call from selected contact: ${contact.name}")
                    activeCallNotificationKey = sbn.key
                }, 0)
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

                // Adjust the notification volume
                AudioStateManager.overrideNotificationVolumeForContact(context, contact)

                // Play the notification sound immediately
                playNotificationSound(context, contact)

                // Schedule resetting the audio state
                AudioStateManager.scheduleResetAudioState(context, SMS_RING_DURATION)
                lastProcessedTime = currentTime
            } else {
                Log.d("SMSNotification", "Sender not in selected contacts: $sender")
            }
        } else {
            Log.d("SMSNotification", "Skipping processing due to recent activity")
        }
    }

    private fun playNotificationSound(context: Context, contact: Contact) {
        if (contact.onlyVibrate) {
            Log.d("SMSNotification", "Contact ${contact.name} is set to Only Vibrate. Skipping notification sound.")
            return
        }
        try {
            // Always use the default notification sound for SMS
            val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            Log.d("SMSNotification", "Using notification sound URI: $notificationUri")

            val ringtone = RingtoneManager.getRingtone(context, notificationUri)
            if (ringtone != null) {
                ringtone.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                ringtone.play()
                Log.d("SMSNotification", "Playing notification sound for contact: ${contact.name}")

                // Schedule stopping the ringtone after a certain duration
                Handler(Looper.getMainLooper()).postDelayed({
                    if (ringtone.isPlaying) {
                        ringtone.stop()
                    }
                }, SMS_RING_DURATION)
            } else {
                Log.e("SMSNotification", "Unable to get ringtone for contact: ${contact.name}")
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
