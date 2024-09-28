package com.fbb.contactringer

import android.content.Context
import android.content.SharedPreferences
import android.provider.ContactsContract
import android.util.Log
import org.json.JSONObject

class ContactsManager(private val context: Context) {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences("SelectedContacts", Context.MODE_PRIVATE)

    fun getSelectedContacts(): List<Contact> {
        return sharedPreferences.all.mapNotNull { entry ->
            (entry.value as? String)?.let {
                try {
                    val contactData = JSONObject(it)
                    Contact(
                        name = entry.key,
                        number = contactData.optString("number", ""),
                        ringtone = contactData.optString("ringtone"),
                        volume = contactData.optInt("volume", 100),
                        onlyVibrate = contactData.optBoolean("onlyVibrate", false)
                    )
                } catch (e: Exception) {
                    Contact(name = entry.key, number = it)
                }
            }
        }
    }

    companion object {
        fun getContactByNumber(context: Context, phoneNumber: String?): Contact? {
            val sharedPreferences: SharedPreferences = context.getSharedPreferences("SelectedContacts", Context.MODE_PRIVATE)
            return phoneNumber?.let {
                val normalizedIncoming = normalizePhoneNumber(it)
                sharedPreferences.all.mapNotNull { entry ->
                    val contactData = JSONObject(entry.value as String)
                    val contact = Contact(
                        name = entry.key,
                        number = contactData.optString("number", ""),
                        ringtone = contactData.optString("ringtone"),
                        volume = contactData.optInt("volume", 100),
                        onlyVibrate = contactData.optBoolean("onlyVibrate", false)
                    )
                    if (normalizedIncoming.endsWith(normalizePhoneNumber(contact.number).takeLast(10))) {
                        contact
                    } else null
                }.firstOrNull()
            }
        }

        private fun normalizePhoneNumber(phoneNumber: String): String {
            return phoneNumber.replace(Regex("[^\\d]"), "")
        }
    }

    fun selectContact(contact: Contact) {
        val contactData = JSONObject().apply {
            put("number", contact.number)
            put("ringtone", contact.ringtone)
            put("volume", contact.volume)
            put("onlyVibrate", contact.onlyVibrate)
        }
        sharedPreferences.edit().putString(contact.name, contactData.toString()).apply()
    }

    fun deselectContact(contact: Contact) {
        sharedPreferences.edit().remove(contact.name).apply()
    }

    fun fetchContacts(): List<Contact> {
        val contacts = mutableListOf<Contact>()
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME,
            ContactsContract.Contacts.HAS_PHONE_NUMBER
        )

        context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            projection,
            null,
            null,
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                try {
                    val hasPhoneNumber = cursor.getInt(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.HAS_PHONE_NUMBER))
                    if (hasPhoneNumber > 0) {
                        val id = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
                        val name = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME))

                        context.contentResolver.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            null,
                            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                            arrayOf(id),
                            null
                        )?.use { phoneCursor ->
                            if (phoneCursor.moveToNext()) {
                                val phoneNumber = phoneCursor.getString(phoneCursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
                                contacts.add(Contact(name, phoneNumber))
                            }
                        }
                    }
                } catch (e: IllegalArgumentException) {
                    Log.e("ContactsManager", "Error fetching contact: ${e.message}")
                }
            }
        }
        return contacts
    }
}

data class Contact(
    val name: String,
    val number: String,
    var ringtone: String? = null,
    var volume: Int = 100,
    var onlyVibrate: Boolean = false
)
