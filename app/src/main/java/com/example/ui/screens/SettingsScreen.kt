package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.firebase.AuthManager
import com.example.data.media.MediaCacheManager
import com.example.data.model.VideoQualityOption
import com.example.ui.components.SnapAvatar
import com.example.ui.theme.SnapCoral
import com.example.ui.theme.SnapEmerald
import com.example.ui.viewmodel.ReelsViewModel
import com.example.ui.viewmodel.SnapViewModel
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    snapViewModel: SnapViewModel,
    reelsViewModel: ReelsViewModel,
    onOpenMyCreatorProfile: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val profile by snapViewModel.currentUserProfile.collectAsStateWithLifecycle()
    val privateSettings by snapViewModel.privateSettings.collectAsStateWithLifecycle()
    val discoverableUsers by snapViewModel.discoverableUsers.collectAsStateWithLifecycle()
    val qualityOption by reelsViewModel.qualityOption.collectAsStateWithLifecycle()
    val autoPlayNext by reelsViewModel.autoPlayNext.collectAsStateWithLifecycle()

    var username by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var avatarUrl by remember { mutableStateOf("") }
    var privateAccount by remember { mutableStateOf(false) }

    var readReceipts by remember { mutableStateOf(true) }
    var lastSeenVisible by remember { mutableStateOf(true) }
    var dataSaver by remember { mutableStateOf(false) }
    var selectedQuality by remember { mutableStateOf(qualityOption) }
    var autoPlaySetting by remember { mutableStateOf(autoPlayNext) }

    var cacheSizeMb by remember { mutableDoubleStateOf(MediaCacheManager.getCacheSizeMb(context)) }
    var showBlockedDialog by remember { mutableStateOf(false) }

    LaunchedEffect(profile) {
        profile?.let {
            username = it.username
            displayName = it.displayName
            bio = it.bio
            avatarUrl = it.avatarUrl
            privateAccount = it.privateAccount
        }
    }

    LaunchedEffect(privateSettings) {
        privateSettings?.let { settings ->
            readReceipts = settings.readReceipts
            lastSeenVisible = settings.lastSeenVisible
            dataSaver = settings.dataSaver
            val parsedQuality = VideoQualityOption.fromValue(settings.videoQuality)
            selectedQuality = parsedQuality
            autoPlaySetting = settings.autoPlayNextReel
            reelsViewModel.setQualityOption(parsedQuality)
            reelsViewModel.setAutoPlayNext(settings.autoPlayNextReel)
        }
    }

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val savedFile = runCatching {
                val out = File(context.filesDir, "avatar_${snapViewModel.currentUserId}_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    out.outputStream().use { output -> input.copyTo(output) }
                }
                Uri.fromFile(out).toString()
            }.getOrElse { uri.toString() }
            avatarUrl = savedFile
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.headlineSmall) }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Creator & Messaging Profile Card
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            SnapAvatar(
                                avatarUrl = avatarUrl,
                                name = displayName.ifBlank { "SNAP" },
                                size = 68.dp,
                                ringColor = SnapEmerald,
                                modifier = Modifier.clickable {
                                    avatarPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = displayName.ifBlank { "SNAP Creator" },
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Text(
                                    text = "@${username.ifBlank { "snapuser" }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (!privateSettings?.email.isNullOrBlank()) {
                                    Text(
                                        text = privateSettings?.email ?: "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = {
                                    avatarPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier.testTag("change_avatar_button")
                            ) {
                                Icon(Icons.Rounded.PhotoCamera, contentDescription = "Change avatar", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Photo")
                            }
                        }

                        OutlinedButton(
                            onClick = { onOpenMyCreatorProfile(snapViewModel.currentUserId) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("open_my_creator_profile_button")
                        ) {
                            Icon(Icons.Rounded.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("View My Creator Profile & Saved Reels")
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { displayName = it.take(50) },
                            label = { Text("Display Name") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_display_name_input")
                        )

                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it.take(30) },
                            label = { Text("Username") },
                            prefix = { Text("@") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_username_input")
                        )

                        OutlinedTextField(
                            value = bio,
                            onValueChange = { bio = it.take(200) },
                            label = { Text("Creator Bio") },
                            minLines = 2,
                            maxLines = 3,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_bio_input")
                        )

                        SettingsSwitchRow(
                            icon = Icons.Rounded.Lock,
                            title = "Private Account",
                            subtitle = "Limit non-follower visibility across discovery",
                            checked = privateAccount,
                            onCheckedChange = { privateAccount = it }
                        )

                        Button(
                            onClick = {
                                snapViewModel.updateProfile(
                                    username = username,
                                    displayName = displayName,
                                    bio = bio,
                                    avatarUrl = avatarUrl,
                                    privateAccount = privateAccount
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("save_profile_button")
                        ) {
                            Text("Save Profile")
                        }
                    }
                }
            }

            // Messaging Privacy & Safety Card
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Privacy & Safety", style = MaterialTheme.typography.titleMedium)

                        SettingsSwitchRow(
                            icon = Icons.Rounded.Shield,
                            title = "Read Receipts",
                            subtitle = "Show and receive message read indicators in SNAP chats",
                            checked = readReceipts,
                            onCheckedChange = { next ->
                                readReceipts = next
                                snapViewModel.updateSettings(next, lastSeenVisible, autoPlaySetting, selectedQuality, dataSaver)
                            }
                        )

                        SettingsSwitchRow(
                            icon = Icons.Rounded.NotificationsActive,
                            title = "Last Seen & Online Status",
                            subtitle = "Allow SNAP contacts to see when you are active",
                            checked = lastSeenVisible,
                            onCheckedChange = { next ->
                                lastSeenVisible = next
                                snapViewModel.updateSettings(readReceipts, next, autoPlaySetting, selectedQuality, dataSaver)
                            }
                        )

                        val blockedCount = profile?.blockedUserIds?.size ?: 0
                        OutlinedButton(
                            onClick = { showBlockedDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("manage_blocked_users_button")
                        ) {
                            Icon(Icons.Rounded.Block, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Blocked Accounts ($blockedCount)")
                        }
                    }
                }
            }

            // Reels Performance, Quality & Cache Card
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("SNAP Reels Performance & Quality", style = MaterialTheme.typography.titleMedium)

                        SettingsSwitchRow(
                            icon = Icons.Rounded.PlayCircleOutline,
                            title = "Auto-Play Next Reel",
                            subtitle = "Automatically advance to the next vertical video when one ends",
                            checked = autoPlaySetting,
                            onCheckedChange = { next ->
                                autoPlaySetting = next
                                reelsViewModel.setAutoPlayNext(next)
                                snapViewModel.updateSettings(readReceipts, lastSeenVisible, next, selectedQuality, dataSaver)
                            }
                        )

                        SettingsSwitchRow(
                            icon = Icons.Rounded.HighQuality,
                            title = "Data Saver Mode",
                            subtitle = "Compress uploads more aggressively and limit cellular bitrate",
                            checked = dataSaver,
                            onCheckedChange = { next ->
                                dataSaver = next
                                val nextQuality = if (next) VideoQualityOption.DATA_SAVER_480 else selectedQuality
                                selectedQuality = nextQuality
                                reelsViewModel.setQualityOption(nextQuality)
                                snapViewModel.updateSettings(readReceipts, lastSeenVisible, autoPlaySetting, nextQuality, next)
                            }
                        )

                        Text("Playback Quality Optimization", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            VideoQualityOption.entries.forEach { option ->
                                FilterChip(
                                    selected = selectedQuality == option,
                                    onClick = {
                                        selectedQuality = option
                                        reelsViewModel.setQualityOption(option)
                                        snapViewModel.updateSettings(readReceipts, lastSeenVisible, autoPlaySetting, option, dataSaver)
                                    },
                                    label = { Text(option.label) }
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Video Cache (LRU)", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = String.format(Locale.US, "%.1f MB used (180 MB max)", cacheSizeMb),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    MediaCacheManager.clearCache(context)
                                    cacheSizeMb = MediaCacheManager.getCacheSizeMb(context)
                                },
                                modifier = Modifier.testTag("clear_video_cache_button")
                            ) {
                                Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Clear")
                            }
                        }
                    }
                }
            }

            // Firebase Cloud Status Card
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Rounded.CloudDone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "Firebase Cloud Sync Active",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                "Google Sign-In & Cloud Firestore realtime sync are live for chats, status updates, calls, Reels metadata, likes, comments, and follows. Configure Storage Rules, Cloud Functions, and FCM in Firebase Console for multi-device media sync and push alerts.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Sign Out
            item {
                Button(
                    onClick = { AuthManager.signOut(credentialManager, scope) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SnapCoral,
                        contentColor = androidx.compose.ui.graphics.Color.White
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("sign_out_button")
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign Out", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
            }
        }
    }

    if (showBlockedDialog) {
        val blockedIds = profile?.blockedUserIds.orEmpty()
        AlertDialog(
            onDismissRequest = { showBlockedDialog = false },
            title = { Text("Blocked Accounts") },
            text = {
                if (blockedIds.isEmpty()) {
                    Text("You haven't blocked any SNAP accounts.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        blockedIds.forEach { uid ->
                            val user = discoverableUsers.firstOrNull { it.userId == uid }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = user?.let { "${it.displayName} (@${it.username})" } ?: uid.take(16),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = { snapViewModel.toggleBlockUser(uid, currentlyBlocked = true) }
                                ) {
                                    Text("Unblock")
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBlockedDialog = false }) {
                    Text("Done")
                }
            }
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
