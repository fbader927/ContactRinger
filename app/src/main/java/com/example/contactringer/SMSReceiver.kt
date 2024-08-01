package com.example.contactringer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

class SMSReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            messages?.let {
                for (sms in it) {
                    val sender = sms.originatingAddress
                    val contactsManager = ContactsManager(context)
                    val contact = contactsManager.getContactByNumber(sender)

                    if (contact != null) {
                        AudioStateManager.overrideSilentMode(context, contact)
                        AudioStateManager.scheduleResetAudioState(context, 2000)
                    }
                }
            }
        }
    }
}