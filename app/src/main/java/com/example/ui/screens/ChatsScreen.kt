package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.ChatMessage
import com.example.data.model.ChatThread
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.ui.components.SnapAvatar
import com.example.ui.components.SnapEmptyState
import com.example.ui.components.SnapErrorState
import com.example.ui.components.SnapLoadingState
import com.example.ui.components.formatRelativeTime
import com.example.ui.theme.SnapEmerald
import com.example.ui.theme.SnapObsidian
import com.example.ui.viewmodel.SnapViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(
    snapViewModel: SnapViewModel,
    onOpenChat: (ChatThread) -> Unit,
    onNavigateToReels: () -> Unit
) {
    val chatsState by snapViewModel.chatsState.collectAsStateWithLifecycle()
    val users by snapViewModel.discoverableUsers.collectAsStateWithLifecycle()
    val currentUserId = snapViewModel.currentUserId

    var searchQuery by remember { mutableStateOf("") }
    var showNewChatSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "SNAP",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Chats",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showNewChatSheet = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("new_chat_fab")
            ) {
                Icon(Icons.Rounded.AddComment, contentDescription = "Start new chat")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search chats or messages") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("chats_search_input")
            )

            when (val state = chatsState) {
                is UiState.Loading -> SnapLoadingState("Loading SNAP chats...")
                is UiState.Error -> SnapErrorState(
                    message = state.message,
                    onRetry = { snapViewModel.selectChat(null) }
                )
                is UiState.Success -> {
                    val filtered = state.data.filter { chat ->
                        searchQuery.isBlank() ||
                            chat.title.contains(searchQuery, ignoreCase = true) ||
                            chat.lastMessageText.contains(searchQuery, ignoreCase = true)
                    }

                    if (filtered.isEmpty()) {
                        SnapEmptyState(
                            title = if (searchQuery.isBlank()) "No conversations yet" else "No matching chats",
                            subtitle = "Start a new chat with a SNAP contact, open your Saved Notes, or share a SNAP Reel.",
                            actionLabel = "Start New Chat",
                            onAction = { showNewChatSheet = true },
                            secondaryLabel = "Browse SNAP Reels",
                            onSecondary = onNavigateToReels
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 96.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filtered, key = { it.id }) { chat ->
                                ChatRowItem(
                                    chat = chat,
                                    currentUserId = currentUserId,
                                    onClick = { onOpenChat(chat) }
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 80.dp, end = 16.dp),
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewChatSheet) {
        ModalBottomSheet(onDismissRequest = { showNewChatSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "New SNAP Chat",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                Surface(
                    onClick = {
                        showNewChatSheet = false
                        snapViewModel.openSelfNotesChat(onOpenChat)
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("self_notes_chat_option")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Bookmark,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        Column {
                            Text("Saved Notes (You)", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Save personal notes and Reels in your private thread",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                val peers = users.filter { it.userId != currentUserId }
                if (peers.isEmpty()) {
                    Text(
                        text = "No other SNAP accounts discovered yet. Sign in with a second account on another device to chat live, or use Saved Notes above!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.height(280.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(peers, key = { it.userId }) { peer ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        showNewChatSheet = false
                                        snapViewModel.startDirectChat(peer, onOpenChat)
                                    }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                SnapAvatar(avatarUrl = peer.avatarUrl, name = peer.displayName, size = 44.dp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(peer.displayName, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "@${peer.username}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatRowItem(
    chat: ChatThread,
    currentUserId: String,
    onClick: () -> Unit
) {
    val displayTitle = remember(chat, currentUserId) {
        val otherEntry = chat.participantNames.entries.firstOrNull { it.key != currentUserId }
        otherEntry?.value ?: chat.title
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("chat_row_${chat.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SnapAvatar(avatarUrl = chat.peerAvatarUrl, name = displayTitle, size = 52.dp)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatRelativeTime(chat.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (chat.lastMessageType == "reel_share") {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = chat.lastMessageText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    chat: ChatThread,
    snapViewModel: SnapViewModel,
    onBack: () -> Unit,
    onWatchSharedReel: (String) -> Unit,
    onStartPeerCall: (UserProfile, String) -> Unit
) {
    val messagesState by snapViewModel.activeMessagesState.collectAsStateWithLifecycle()
    val users by snapViewModel.discoverableUsers.collectAsStateWithLifecycle()
    val currentUserId = snapViewModel.currentUserId

    LaunchedEffect(chat.id) {
        snapViewModel.selectChat(chat.id)
    }

    val peerUid = remember(chat, currentUserId) {
        chat.memberUids.firstOrNull { it != currentUserId }
    }
    val peerProfile = remember(users, peerUid) {
        users.firstOrNull { it.userId == peerUid }
    }
    val title = peerProfile?.displayName
        ?: chat.participantNames.entries.firstOrNull { it.key != currentUserId }?.value
        ?: chat.title

    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val messages = (messagesState as? UiState.Success)?.data ?: emptyList()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("chat_back_button")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SnapAvatar(avatarUrl = peerProfile?.avatarUrl ?: chat.peerAvatarUrl, name = title, size = 36.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (peerProfile != null) "@${peerProfile.username}" else "SNAP Encrypted Thread",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    if (peerProfile != null) {
                        IconButton(onClick = { onStartPeerCall(peerProfile, "voice") }) {
                            Icon(Icons.Rounded.Call, contentDescription = "Voice call")
                        }
                        IconButton(onClick = { onStartPeerCall(peerProfile, "video") }) {
                            Icon(Icons.Rounded.Videocam, contentDescription = "Video call")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            when (val state = messagesState) {
                is UiState.Loading -> SnapLoadingState("Loading messages...", Modifier.weight(1f))
                is UiState.Error -> SnapErrorState(
                    message = state.message,
                    onRetry = { snapViewModel.selectChat(chat.id) },
                    modifier = Modifier.weight(1f)
                )
                is UiState.Success -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.data, key = { it.id }) { msg ->
                            ChatMessageBubble(
                                message = msg,
                                isMe = msg.senderId == currentUserId,
                                onWatchReel = onWatchSharedReel
                            )
                        }
                    }
                }
            }

            Surface(
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        placeholder = { Text("Message...") },
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 4,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_message_input")
                    )
                    Surface(
                        onClick = {
                            if (draft.isNotBlank()) {
                                snapViewModel.sendTextMessage(chat, draft)
                                draft = ""
                            }
                        },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("send_message_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Send,
                                contentDescription = "Send message",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(
    message: ChatMessage,
    isMe: Boolean,
    onWatchReel: (String) -> Unit
) {
    val alignment = if (isMe) Alignment.End else Alignment.Start
    val bubbleColor = if (isMe) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (isMe) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isMe) 18.dp else 4.dp,
                bottomEnd = if (isMe) 4.dp else 18.dp
            ),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                if (message.messageType == "reel_share" && message.sharedReelId.isNotBlank()) {
                    Surface(
                        onClick = { onWatchReel(message.sharedReelId) },
                        color = SnapObsidian,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                            .testTag("shared_reel_card_${message.sharedReelId}")
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(170.dp)
                            ) {
                                if (message.sharedReelThumbnail.isNotBlank()) {
                                    AsyncImage(
                                        model = message.sharedReelThumbnail,
                                        contentDescription = "Shared SNAP Reel",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.28f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = SnapEmerald.copy(alpha = 0.9f),
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Rounded.PlayArrow,
                                                contentDescription = "Play shared Reel",
                                                tint = SnapObsidian
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "SNAP REEL",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SnapEmerald,
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(8.dp)
                                )
                            }
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "@${message.sharedReelCreator.ifBlank { "creator" }}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = SnapEmerald
                                )
                                if (message.sharedReelCaption.isNotBlank()) {
                                    Text(
                                        text = message.sharedReelCaption,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
                if (message.text.isNotBlank()) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formatRelativeTime(message.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.65f),
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}
