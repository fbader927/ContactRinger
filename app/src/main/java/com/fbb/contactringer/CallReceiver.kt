package com.fbb.contactringer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log

class CallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

            when (state) {
                TelephonyManager.EXTRA_STATE_RINGING -> {
                    if (!incomingNumber.isNullOrEmpty()) {
                        handleIncomingCall(context, incomingNumber)
                    }
                }
                TelephonyManager.EXTRA_STATE_IDLE -> {
                    resetAudioState(context)
                }
            }
        }
    }

    private fun handleIncomingCall(context: Context, number: String) {
        val contact = ContactsManager.getContactByNumber(context, number)
        if (contact != null) {
            AudioStateManager.overrideSilentMode(context, contact)
        }
    }

    private fun resetAudioState(context: Context) {
        AudioStateManager.resetAudioState(context)
    }
}
