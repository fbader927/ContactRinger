package com.example.contactringer

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.example.contactringer.ui.theme.ContactRingerTheme
import androidx.activity.compose.rememberLauncherForActivityResult

class MainActivity : ComponentActivity() {
    private lateinit var contactsManager: ContactsManager
    private lateinit var notificationManager: NotificationManager

    private val requiredPermissions = arrayOf(
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.MODIFY_AUDIO_SETTINGS,
        Manifest.permission.READ_PHONE_NUMBERS,
        Manifest.permission.ANSWER_PHONE_CALLS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_SMS
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            requestAdditionalPermissions()
        } else {
            // do nothing
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        contactsManager = ContactsManager(this)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        requestPermissions()

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)

        setContent {
            ContactRingerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(contactsManager)
                }
            }
        }
    }

    private fun requestPermissions() {
        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest)
        } else {
            requestAdditionalPermissions()
        }
    }

    private fun requestAdditionalPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!notificationManager.isNotificationPolicyAccessGranted) {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            }

            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                })
            }

            if (!Settings.System.canWrite(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(contactsManager: ContactsManager) {
    var selectedContacts by remember { mutableStateOf(contactsManager.getSelectedContacts()) }
    var showContactPicker by remember { mutableStateOf(false) }
    var editingContact by remember { mutableStateOf<Contact?>(null) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Contact Ringer", textAlign = TextAlign.Center) },
                    actions = {
                        IconButton(onClick = { showContactPicker = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add Contact")
                        }
                    }
                )
                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            if (selectedContacts.isEmpty()) {
                Text(
                    "No contacts added yet",
                    modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.Center)
                )
            } else {
                LazyColumn {
                    items(selectedContacts) { contact ->
                        ContactItem(
                            contact = contact,
                            onDelete = {
                                contactsManager.deselectContact(contact)
                                selectedContacts = contactsManager.getSelectedContacts()
                            },
                            onEdit = {
                                editingContact = contact
                            }
                        )
                    }
                }
            }
        }
    }

    if (showContactPicker) {
        ContactPickerDialog(
            contactsManager = contactsManager,
            onDismiss = { showContactPicker = false },
            onContactSelected = { contact ->
                contactsManager.selectContact(contact)
                selectedContacts = contactsManager.getSelectedContacts()
            }
        )
    }

    editingContact?.let { contact ->
        EditContactDialog(
            contact = contact,
            onDismiss = { editingContact = null },
            onSave = { updatedContact ->
                contactsManager.selectContact(updatedContact)
                selectedContacts = contactsManager.getSelectedContacts()
                editingContact = null
            }
        )
    }
}

@Composable
fun ContactItem(contact: Contact, onDelete: () -> Unit, onEdit: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(contact.name, modifier = Modifier.weight(1f))
        IconButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, contentDescription = "Edit Contact")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Close, contentDescription = "Remove Contact")
        }
    }
}

@Composable
fun EditContactDialog(
    contact: Contact,
    onDismiss: () -> Unit,
    onSave: (Contact) -> Unit
) {
    var ringtone by remember { mutableStateOf(contact.ringtone ?: "") }
    var volume by remember { mutableStateOf(contact.volume.toFloat()) }
    var onlyVibrate by remember { mutableStateOf(contact.onlyVibrate) }
    val context = LocalContext.current
    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == ComponentActivity.RESULT_OK) {
            result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)?.let { uri ->
                ringtone = uri.toString()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Contact Settings") },
        text = {
            Column {
                Text("Select Ringtone")
                Button(onClick = {
                    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
                    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Ringtone")
                    if (ringtone.isNotEmpty()) {
                        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(ringtone))
                    }
                    ringtonePickerLauncher.launch(intent)
                }) {
                    Text("Choose Ringtone")
                }

                Text("Volume")
                Slider(
                    value = volume,
                    onValueChange = { volume = it },
                    valueRange = 0f..100f
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = onlyVibrate,
                        onCheckedChange = { onlyVibrate = it }
                    )
                    Text("Only Vibrate")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(contact.copy(
                    ringtone = ringtone.ifEmpty { null },
                    volume = volume.toInt(),
                    onlyVibrate = onlyVibrate
                ))
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ContactPickerDialog(
    contactsManager: ContactsManager,
    onDismiss: () -> Unit,
    onContactSelected: (Contact) -> Unit
) {
    var contacts by remember { mutableStateOf(listOf<Contact>()) }

    LaunchedEffect(Unit) {
        contacts = contactsManager.fetchContacts()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select a Contact") },
        text = {
            LazyColumn {
                items(contacts) { contact ->
                    Text(
                        text = contact.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onContactSelected(contact)
                                onDismiss()
                            }
                            .padding(vertical = 8.dp)
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
