# Contact Ringer

Contact Ringer is an Android application that ensures important calls and SMS messages are never missed, even when your phone is in silent or vibrate mode. When a call or SMS is received from a selected contact, the ringer volume is temporarily turned on, then reverts back to the previous state after the call or message.

## Features

- **Contact Selection**: Choose specific contacts whose calls and SMS messages should always ring through.
- **Override Silent Mode**: Automatically turn on the ringer volume for selected contacts, even if the phone is in silent or vibrate mode.
- **Automatic Reset**: After the call or SMS, the phone's audio settings revert to the original state.
- **Custom Ringtones and Volume**: Set custom ringtones and volume levels for selected contacts.

## Components

- **MainActivity**: Handles the main interface for selecting and managing contacts.
- **CallReceiver**: Broadcast receiver that detects incoming calls and overrides the silent mode for selected contacts.
- **SMSReceiver**: Broadcast receiver that detects incoming SMS messages and overrides the silent mode for selected contacts.
- **ContactsManager**: Manages contact selection and retrieval from the device's contacts.
- **AudioStateManager**: Manages the audio state of the phone, overriding silent mode and resetting it after calls or messages.

## Permissions

This app requires various permissions to read contacts, call logs, and SMS, modify audio settings, and bypass battery optimizations to function.
