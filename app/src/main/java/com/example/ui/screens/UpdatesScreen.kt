package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.StatusUpdate
import com.example.data.model.UiState
import com.example.ui.components.SnapAvatar
import com.example.ui.components.SnapEmptyState
import com.example.ui.components.SnapErrorState
import com.example.ui.components.SnapLoadingState
import com.example.ui.components.formatRelativeTime
import com.example.ui.theme.SnapEmerald
import com.example.ui.theme.SnapObsidian
import com.example.ui.viewmodel.SnapViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatesScreen(
    snapViewModel: SnapViewModel
) {
    val updatesState by snapViewModel.updatesState.collectAsStateWithLifecycle()
    val currentProfile by snapViewModel.currentUserProfile.collectAsStateWithLifecycle()
    val currentUserId = snapViewModel.currentUserId

    var showCreateSheet by remember { mutableStateOf(false) }
    var viewingStatus by remember { mutableStateOf<StatusUpdate?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Updates", style = MaterialTheme.typography.headlineSmall) }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateSheet = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("create_status_fab")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add status update")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // My Status Header Card
            Surface(
                onClick = { showCreateSheet = true },
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("my_status_card")
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        SnapAvatar(
                            avatarUrl = currentProfile?.avatarUrl ?: "",
                            name = currentProfile?.displayName ?: "Me",
                            size = 54.dp
                        )
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .align(Alignment.BottomEnd)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text("My Status", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Tap to share a text or photo status update",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            when (val state = updatesState) {
                is UiState.Loading -> SnapLoadingState("Loading updates...")
                is UiState.Error -> SnapErrorState(
                    message = state.message,
                    onRetry = { /* Reactive stream */ }
                )
                is UiState.Success -> {
                    if (state.data.isEmpty()) {
                        SnapEmptyState(
                            title = "No status updates yet",
                            subtitle = "Post a quick 24-hour text or photo status to share moments with friends.",
                            actionLabel = "Post Status",
                            onAction = { showCreateSheet = true }
                        )
                    } else {
                        val myUpdates = state.data.filter { it.userId == currentUserId }
                        val friendUpdates = state.data.filter { it.userId != currentUserId }

                        LazyColumn(
                            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (myUpdates.isNotEmpty()) {
                                item {
                                    Text(
                                        "Your Updates",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                items(myUpdates, key = { it.id }) { item ->
                                    StatusUpdateRow(
                                        update = item,
                                        isViewed = true,
                                        isMine = true,
                                        onClick = { viewingStatus = item },
                                        onDelete = { snapViewModel.deleteStatusUpdate(item.id) }
                                    )
                                }
                            }

                            if (friendUpdates.isNotEmpty()) {
                                item {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        "Recent Updates",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                items(friendUpdates, key = { it.id }) { item ->
                                    val viewed = currentUserId in item.viewerIds
                                    StatusUpdateRow(
                                        update = item,
                                        isViewed = viewed,
                                        isMine = false,
                                        onClick = {
                                            snapViewModel.markStatusViewed(item.id)
                                            viewingStatus = item
                                        },
                                        onDelete = null
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateSheet) {
        CreateStatusBottomSheet(
            onDismiss = { showCreateSheet = false },
            onPublish = { caption, mediaUri, bgHex ->
                snapViewModel.postStatusUpdate(caption, mediaUri?.toString(), bgHex) {
                    showCreateSheet = false
                }
            }
        )
    }

    viewingStatus?.let { status ->
        BackHandler { viewingStatus = null }
        StatusViewerOverlay(
            update = status,
            isMine = status.userId == currentUserId,
            onClose = { viewingStatus = null },
            onReply = { reply ->
                snapViewModel.replyToStatus(status.userId, status.caption, reply)
                viewingStatus = null
            }
        )
    }
}

@Composable
private fun StatusUpdateRow(
    update: StatusUpdate,
    isViewed: Boolean,
    isMine: Boolean,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?
) {
    val ringColor = if (isViewed) Color.Gray.copy(alpha = 0.5f) else SnapEmerald
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("status_row_${update.id}")
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SnapAvatar(
                avatarUrl = update.mediaUrl.ifBlank { update.avatarUrl },
                name = update.username,
                size = 52.dp,
                ringColor = ringColor
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isMine) "My Update" else "@${update.username}",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = update.caption.ifBlank { "Photo status" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Text(
                    text = "${formatRelativeTime(update.createdAt)} • ${update.viewerIds.size} views",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Delete status")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateStatusBottomSheet(
    onDismiss: () -> Unit,
    onPublish: (String, Uri?, String) -> Unit
) {
    val palette = listOf("#063B2C", "#1B263B", "#4A0D1E", "#2D1E5F", "#1F2937")
    var selectedColorHex by remember { mutableStateOf(palette.first()) }
    var caption by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) selectedImageUri = uri
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Create Status Update", style = MaterialTheme.typography.titleLarge)

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                palette.forEach { hex ->
                    val parsed = Color(android.graphics.Color.parseColor(hex))
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(parsed)
                            .border(
                                width = if (selectedColorHex == hex) 3.dp else 1.dp,
                                color = if (selectedColorHex == hex) SnapEmerald else Color.White.copy(alpha = 0.3f),
                                shape = CircleShape
                            )
                            .clickable { selectedColorHex = hex }
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                OutlinedButton(
                    onClick = {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                ) {
                    Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Photo")
                }
            }

            if (selectedImageUri != null) {
                AsyncImage(
                    model = selectedImageUri,
                    contentDescription = "Selected status photo",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            OutlinedTextField(
                value = caption,
                onValueChange = { caption = it.take(300) },
                label = { Text("What's on your mind?") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("status_caption_input")
            )

            Button(
                onClick = { onPublish(caption, selectedImageUri, selectedColorHex) },
                enabled = caption.isNotBlank() || selectedImageUri != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("publish_status_button")
            ) {
                Text("Share Status")
            }
        }
    }
}

@Composable
private fun StatusViewerOverlay(
    update: StatusUpdate,
    isMine: Boolean,
    onClose: () -> Unit,
    onReply: (String) -> Unit
) {
    var progress by remember(update.id) { mutableFloatStateOf(0f) }
    var replyText by remember { mutableStateOf("") }

    LaunchedEffect(update.id) {
        val steps = 60
        for (i in 1..steps) {
            delay(100L)
            progress = i / steps.toFloat()
        }
        onClose()
    }

    val bgColor = remember(update.backgroundHex) {
        try {
            Color(android.graphics.Color.parseColor(update.backgroundHex))
        } catch (_: Exception) {
            SnapObsidian
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .safeDrawingPadding()
    ) {
        if (update.mediaUrl.isNotBlank()) {
            AsyncImage(
                model = update.mediaUrl,
                contentDescription = "Status media",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LinearProgressIndicator(
                    progress = { progress },
                    color = SnapEmerald,
                    trackColor = Color.White.copy(alpha = 0.25f),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SnapAvatar(avatarUrl = update.avatarUrl, name = update.username, size = 40.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "@${update.username}",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            Text(
                                formatRelativeTime(update.createdAt),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.75f)
                            )
                        }
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close status", tint = Color.White)
                    }
                }
            }

            if (update.caption.isNotBlank()) {
                Surface(
                    color = Color.Black.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        text = update.caption,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Medium),
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
                    )
                }
            }

            if (isMine) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Visibility, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("${update.viewerIds.size} views", color = Color.White)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = replyText,
                        onValueChange = { replyText = it },
                        placeholder = { Text("Reply privately...", color = Color.White.copy(alpha = 0.7f)) },
                        shape = RoundedCornerShape(24.dp),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            if (replyText.isNotBlank()) onReply(replyText)
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send reply", tint = SnapEmerald)
                    }
                }
            }
        }
    }
}
