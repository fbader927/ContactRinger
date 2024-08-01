package com.example.contactringer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager

class CallReceiver : BroadcastReceiver() {
    private enum class CallState {
        IDLE, RINGING, OFFHOOK
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
        val prefs = context.getSharedPreferences("CallReceiverPrefs", Context.MODE_PRIVATE)
        var isSelectedContact = prefs.getBoolean("isSelectedContact", false)
        var currentCallState = CallState.valueOf(prefs.getString("callState", CallState.IDLE.name) ?: CallState.IDLE.name)

        val contactsManager = ContactsManager(context)

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                val contact = contactsManager.getContactByNumber(incomingNumber)
                if (contact != null) {
                    currentCallState = CallState.RINGING
                    AudioStateManager.overrideSilentMode(context, contact)
                    isSelectedContact = true
                } else {
                    currentCallState = CallState.IDLE
                }
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                if (currentCallState == CallState.RINGING) {
                    currentCallState = CallState.OFFHOOK
                }
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                if (isSelectedContact && (currentCallState == CallState.RINGING || currentCallState == CallState.OFFHOOK)) {
                    AudioStateManager.resetAudioState(context)
                }
                currentCallState = CallState.IDLE
                isSelectedContact = false
            }
        }

        prefs.edit().apply {
            putBoolean("isSelectedContact", isSelectedContact)
            putString("callState", currentCallState.name)
            apply()
        }
    }
}
