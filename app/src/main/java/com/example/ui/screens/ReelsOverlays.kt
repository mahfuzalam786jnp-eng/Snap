package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ChatThread
import com.example.data.model.Reel
import com.example.data.model.ReelComment
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.model.VideoQualityOption
import com.example.ui.components.SnapAvatar
import com.example.ui.components.SnapLoadingState
import com.example.ui.components.formatRelativeTime
import com.example.ui.viewmodel.ReelsViewModel
import com.example.ui.viewmodel.SnapViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsBottomSheet(
    reel: Reel,
    reelsViewModel: ReelsViewModel,
    currentProfile: UserProfile?,
    onDismiss: () -> Unit,
    onCreatorClick: (String) -> Unit
) {
    LaunchedEffect(reel.id) {
        reelsViewModel.openComments(reel.id)
    }

    val commentsState by reelsViewModel.commentsState.collectAsStateWithLifecycle()
    var commentText by remember { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<ReelComment?>(null) }

    ModalBottomSheet(
        onDismissRequest = {
            reelsViewModel.openComments(null)
            onDismiss()
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Comments (${reel.commentsCount})",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            when (val state = commentsState) {
                is UiState.Loading -> {
                    SnapLoadingState("Loading comments...", Modifier.height(220.dp))
                }
                is UiState.Error -> {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
                is UiState.Success -> {
                    val topLevel = state.data.filter { it.parentCommentId.isBlank() }
                    val repliesByParent = state.data
                        .filter { it.parentCommentId.isNotBlank() }
                        .groupBy { it.parentCommentId }

                    if (state.data.isEmpty()) {
                        Text(
                            text = "No comments yet. Start the conversation!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 32.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(topLevel, key = { it.id }) { comment ->
                                CommentItemRow(
                                    comment = comment,
                                    isReply = false,
                                    onReply = { replyingTo = comment },
                                    onAuthorClick = { onCreatorClick(comment.authorId) }
                                )
                                val replies = repliesByParent[comment.id].orEmpty()
                                replies.forEach { reply ->
                                    Spacer(modifier = Modifier.height(6.dp))
                                    CommentItemRow(
                                        comment = reply,
                                        isReply = true,
                                        onReply = { replyingTo = comment },
                                        onAuthorClick = { onCreatorClick(reply.authorId) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

            if (replyingTo != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Replying to @${replyingTo?.authorUsername}",
                            style = MaterialTheme.typography.labelMedium
                        )
                        IconButton(
                            onClick = { replyingTo = null },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "Cancel reply", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = commentText,
                    onValueChange = { commentText = it.take(500) },
                    placeholder = {
                        Text(if (replyingTo != null) "Write a reply..." else "Add a comment...")
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("reel_comment_input")
                )
                IconButton(
                    onClick = {
                        val author = currentProfile ?: UserProfile(
                            userId = reelsViewModel.currentUserId,
                            username = "snapuser",
                            displayName = "SNAP User"
                        )
                        if (commentText.isNotBlank()) {
                            reelsViewModel.postComment(
                                reelId = reel.id,
                                author = author,
                                text = commentText,
                                parentCommentId = replyingTo?.id
                            )
                            commentText = ""
                            replyingTo = null
                        }
                    },
                    modifier = Modifier.testTag("post_comment_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Send,
                        contentDescription = "Post comment",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun CommentItemRow(
    comment: ReelComment,
    isReply: Boolean,
    onReply: () -> Unit,
    onAuthorClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (isReply) 38.dp else 0.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SnapAvatar(
            avatarUrl = comment.authorAvatarUrl,
            name = comment.authorUsername,
            size = if (isReply) 28.dp else 36.dp,
            modifier = Modifier.clickable(onClick = onAuthorClick)
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "@${comment.authorUsername}",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.clickable(onClick = onAuthorClick)
                )
                Text(
                    text = formatRelativeTime(comment.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = comment.text,
                style = MaterialTheme.typography.bodyMedium
            )
            TextButton(
                onClick = onReply,
                modifier = Modifier.height(30.dp)
            ) {
                Icon(Icons.AutoMirrored.Rounded.Reply, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Reply", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareToChatSheet(
    reel: Reel,
    snapViewModel: SnapViewModel,
    reelsViewModel: ReelsViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val chatsState by snapViewModel.chatsState.collectAsStateWithLifecycle()
    val users by snapViewModel.discoverableUsers.collectAsStateWithLifecycle()
    val currentProfile by snapViewModel.currentUserProfile.collectAsStateWithLifecycle()
    val senderName = currentProfile?.displayName?.ifBlank { "SNAP User" } ?: "SNAP User"

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Send Reel to SNAP Chat", style = MaterialTheme.typography.titleLarge)

            OutlinedButton(
                onClick = {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "Watch @${reel.creatorUsername}'s SNAP Reel: ${reel.caption}\n${reel.videoUrl}"
                        )
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share SNAP Reel"))
                    reelsViewModel.incrementExternalShare(reel.id)
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("external_share_reel_button")
            ) {
                Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Share via System Sheet")
            }

            Surface(
                onClick = {
                    snapViewModel.openSelfNotesChat { chat ->
                        reelsViewModel.shareReelToChat(reel, chat, senderName, onDismiss)
                    }
                },
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Rounded.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Send to Saved Notes (You)", style = MaterialTheme.typography.titleMedium)
                }
            }

            val chats = (chatsState as? UiState.Success)?.data.orEmpty()
            if (chats.isNotEmpty()) {
                Text("Recent SNAP Chats", style = MaterialTheme.typography.labelLarge)
                LazyColumn(
                    modifier = Modifier.height(180.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(chats, key = { it.id }) { chat ->
                        ShareTargetRow(
                            title = chat.title,
                            subtitle = chat.lastMessageText,
                            avatarUrl = chat.peerAvatarUrl,
                            onSend = {
                                reelsViewModel.shareReelToChat(reel, chat, senderName, onDismiss)
                            }
                        )
                    }
                }
            }

            val peers = users.filter { it.userId != snapViewModel.currentUserId }
            if (peers.isNotEmpty()) {
                Text("SNAP Contacts", style = MaterialTheme.typography.labelLarge)
                LazyColumn(
                    modifier = Modifier.height(140.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(peers, key = { it.userId }) { peer ->
                        ShareTargetRow(
                            title = peer.displayName,
                            subtitle = "@${peer.username}",
                            avatarUrl = peer.avatarUrl,
                            onSend = {
                                snapViewModel.startDirectChat(peer) { chat ->
                                    reelsViewModel.shareReelToChat(reel, chat, senderName, onDismiss)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShareTargetRow(
    title: String,
    subtitle: String,
    avatarUrl: String,
    onSend: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SnapAvatar(avatarUrl = avatarUrl, name = title, size = 42.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Button(onClick = onSend, shape = RoundedCornerShape(12.dp)) {
            Text("Send")
        }
    }
}

@Composable
fun ReportReelDialog(
    reel: Reel,
    onDismiss: () -> Unit,
    onSubmit: (reason: String, details: String) -> Unit
) {
    val reasons = listOf(
        "Inappropriate or explicit content",
        "Copyright infringement",
        "Harassment or hate speech",
        "Spam or misleading"
    )
    var selectedReason by remember { mutableStateOf(reasons.first()) }
    var details by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report SNAP Reel") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                reasons.forEach { reason ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedReason = reason }
                    ) {
                        RadioButton(
                            selected = selectedReason == reason,
                            onClick = { selectedReason = reason }
                        )
                        Text(reason, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                OutlinedTextField(
                    value = details,
                    onValueChange = { details = it.take(500) },
                    label = { Text("Additional details (optional)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(selectedReason, details) },
                modifier = Modifier.testTag("submit_report_button")
            ) {
                Text("Submit Report")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackSettingsSheet(
    volume: Float,
    qualityOption: VideoQualityOption,
    autoPlayNext: Boolean,
    isOwner: Boolean,
    onVolumeChange: (Float) -> Unit,
    onQualityChange: (VideoQualityOption) -> Unit,
    onAutoPlayChange: (Boolean) -> Unit,
    onReportClick: () -> Unit,
    onBlockCreatorClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Video Player & Safety", style = MaterialTheme.typography.titleLarge)

            Column {
                Text("Volume (${(volume * 100).toInt()}%)", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = volume,
                    onValueChange = onVolumeChange,
                    valueRange = 0f..1f
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto-Play Next Reel", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Automatically advance when a video ends",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = autoPlayNext, onCheckedChange = onAutoPlayChange)
            }

            Text("Video Quality", style = MaterialTheme.typography.labelLarge)
            VideoQualityOption.entries.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onQualityChange(option) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(option.label, style = MaterialTheme.typography.bodyMedium)
                    if (option == qualityOption) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            HorizontalDivider()

            if (isOwner) {
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onDeleteClick()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Delete This Reel", color = MaterialTheme.colorScheme.error)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onReportClick()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Report Video")
                    }
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onBlockCreatorClick()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Block Creator", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
