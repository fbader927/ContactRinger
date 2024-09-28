package com.fbb.contactringer

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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import kotlinx.coroutines.delay
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics



class MainActivity : ComponentActivity() {
    private lateinit var contactsManager: ContactsManager
    private lateinit var notificationManager: NotificationManager

    private val requiredPermissions = arrayOf(
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.MODIFY_AUDIO_SETTINGS
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            requestAdditionalPermissions()
        } else {
            // Handle permission denial
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        contactsManager = ContactsManager(this)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        requestPermissions()
        requestNotificationAccess()

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)

        setContent {
            val isDarkTheme = LocalConfiguration.current.uiMode and 0x30 == 0x20 // Manually detect dark mode

            val customColorScheme = if (isDarkTheme) {
                darkColorScheme(
                    primary = Color(0xFF4682B4) // Light blue as the primary color in dark mode
                )
            } else {
                lightColorScheme(
                    primary = Color(0xFF4682B4) // Light blue as the primary color in light mode
                )
            }

            MaterialTheme(
                colorScheme = customColorScheme.copy(
                    onPrimary = Color(0xFF000000) // Set text color to black for better contrast
                )
            ) {
                var showSplashScreen by remember { mutableStateOf(true) }

                LaunchedEffect(Unit) {
                    delay(500L)
                    showSplashScreen = false
                }

                if (showSplashScreen) {
                    SplashScreen()
                } else {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        MainScreen(contactsManager)
                    }
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

    private fun requestNotificationAccess() {
        if (!isNotificationServiceEnabled()) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val enabledListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        val packageName = packageName
        return enabledListeners != null && enabledListeners.contains(packageName)
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

@Composable
fun SplashScreen() {
    // Display the splash screen image
    Box(
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.splash), // Replace with your image resource
            contentDescription = "Splash Image",
            modifier = Modifier.fillMaxSize() // Adjust size as needed
        )
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
                    title = {
                        Text(
                            "Contact Ringer",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    actions = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { showContactPicker = true }) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "Add Contact",
                                    tint = MaterialTheme.colorScheme.primary // Changed to blue like the rest
                                )
                            }
                        }
                    }
                )
                Divider(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    thickness = 4.dp
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            if (selectedContacts.isEmpty()) {
                // Update text color to match other elements with sufficient contrast
                Text(
                    text = "Add Contact \n (press '+' at top right)",
                    color = MaterialTheme.colorScheme.primary, // Same color as used for "Choose Ringtone" or "Volume"
                    modifier = Modifier
                        .fillMaxSize()
                        .wrapContentSize(Alignment.Center),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.headlineSmall
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
                        Divider()
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
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* Optionally handle click */ }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(contact.name, modifier = Modifier.weight(1f))
        Box {
            IconButton(onClick = { expanded = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More Options")
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                offset = DpOffset(x = (-30).dp, y = -30.dp) // Adjusted offset to shift menu to the left
            ) {
                DropdownMenuItem(
                    text = { Text("Edit Settings") },
                    onClick = {
                        expanded = false
                        onEdit()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Remove") },
                    onClick = {
                        expanded = false
                        onDelete()
                    }
                )
            }
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

                Spacer(modifier = Modifier.height(16.dp))

                Text("Volume")
                Slider(
                    value = volume,
                    onValueChange = { volume = it },
                    valueRange = 0f..100f,
                )


                Spacer(modifier = Modifier.height(16.dp))

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
                onSave(
                    contact.copy(
                        ringtone = ringtone.ifEmpty { null },
                        volume = volume.toInt(),
                        onlyVibrate = onlyVibrate
                    )
                )
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
        title = { Text("Select a Contact", color = MaterialTheme.colorScheme.primary) }, // Use same color for "Select a Contact"
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
                    Divider()
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            // Update the cancel button text color to match other elements
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.primary) // Same color for "Cancel"
            }
        }
    )
}