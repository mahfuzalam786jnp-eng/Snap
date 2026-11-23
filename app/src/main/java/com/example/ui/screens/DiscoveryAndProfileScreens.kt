package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.ChatThread
import com.example.data.model.Reel
import com.example.data.model.ReelVisibility
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.ui.components.SnapAvatar
import com.example.ui.components.SnapEmptyState
import com.example.ui.components.SnapLoadingState
import com.example.ui.components.formatCompactCount
import com.example.ui.theme.SnapEmerald
import com.example.ui.theme.SnapObsidian
import com.example.ui.viewmodel.ReelsViewModel
import com.example.ui.viewmodel.SnapViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DiscoveryScreen(
    initialQuery: String,
    reelsViewModel: ReelsViewModel,
    snapViewModel: SnapViewModel,
    onBack: () -> Unit,
    onWatchReel: (String) -> Unit,
    onOpenCreator: (String) -> Unit
) {
    val feedState by reelsViewModel.reelsFeedState.collectAsStateWithLifecycle()
    val users by snapViewModel.discoverableUsers.collectAsStateWithLifecycle()
    var query by remember(initialQuery) { mutableStateOf(initialQuery) }

    val allReels = (feedState as? UiState.Success)?.data.orEmpty()
    val trendingHashtags = remember(allReels) {
        allReels.flatMap { it.hashtags }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { it.key }
            .ifEmpty { listOf("snap", "reels", "music", "travel", "comedy", "fitness") }
            .take(10)
    }

    val matchingCreators = remember(users, query) {
        if (query.isBlank()) {
            users.take(8)
        } else {
            val clean = query.trim().removePrefix("#")
            users.filter {
                it.username.contains(clean, ignoreCase = true) ||
                    it.displayName.contains(clean, ignoreCase = true)
            }
        }
    }

    val matchingReels = remember(allReels, query) {
        val clean = query.trim().removePrefix("#")
        if (clean.isBlank()) {
            allReels.sortedByDescending { it.likesCount * 2L + it.viewsCount }
        } else {
            allReels.filter { reel ->
                reel.caption.contains(clean, ignoreCase = true) ||
                    reel.creatorUsername.contains(clean, ignoreCase = true) ||
                    reel.hashtags.any { it.contains(clean, ignoreCase = true) }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("discovery_back")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Discover Reels & Creators") }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search videos, #hashtags, or creators") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("discovery_search_input")
                )
            }

            item {
                Text("Trending Hashtags", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    trendingHashtags.forEach { tag ->
                        FilterChip(
                            selected = query.removePrefix("#").equals(tag, ignoreCase = true),
                            onClick = {
                                query = if (query.removePrefix("#").equals(tag, ignoreCase = true)) "" else tag
                            },
                            leadingIcon = {
                                Icon(Icons.Rounded.Tag, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            label = { Text(tag) }
                        )
                    }
                }
            }

            if (matchingCreators.isNotEmpty()) {
                item {
                    Text("Creators", style = MaterialTheme.typography.titleMedium)
                }
                items(matchingCreators, key = { it.userId }) { creator ->
                    Surface(
                        onClick = { onOpenCreator(creator.userId) },
                        shape = RoundedCornerShape(14.dp),
                        tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SnapAvatar(avatarUrl = creator.avatarUrl, name = creator.displayName, size = 44.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(creator.displayName, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "@${creator.username} • ${creator.reelsCount} Reels",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    if (query.isBlank()) "Recommended & Trending Videos" else "Matching Videos (${matchingReels.size})",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            if (matchingReels.isEmpty()) {
                item {
                    SnapEmptyState(
                        title = "No matching SNAP Reels",
                        subtitle = "Try another keyword or hashtag, or publish a new Reel yourself."
                    )
                }
            } else {
                items(matchingReels.chunked(2)) { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        pair.forEach { reel ->
                            ReelGridCard(
                                reel = reel,
                                onClick = { onWatchReel(reel.id) },
                                onDelete = null,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (pair.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatorProfileScreen(
    creatorId: String,
    reelsViewModel: ReelsViewModel,
    snapViewModel: SnapViewModel,
    onBack: () -> Unit,
    onWatchReel: (String) -> Unit,
    onOpenChat: (ChatThread) -> Unit
) {
    LaunchedEffect(creatorId) {
        reelsViewModel.focusCreator(creatorId)
    }

    val users by snapViewModel.discoverableUsers.collectAsStateWithLifecycle()
    val currentProfile by snapViewModel.currentUserProfile.collectAsStateWithLifecycle()
    val creatorReelsState by reelsViewModel.creatorReelsState.collectAsStateWithLifecycle()
    val allFeedState by reelsViewModel.reelsFeedState.collectAsStateWithLifecycle()
    val savedIds by reelsViewModel.savedReelIds.collectAsStateWithLifecycle()
    val followedIds by reelsViewModel.followedCreatorIds.collectAsStateWithLifecycle()
    val followerCounts by reelsViewModel.followerCountsByCreator.collectAsStateWithLifecycle()

    val isOwnProfile = creatorId == snapViewModel.currentUserId
    val creatorProfile = remember(users, currentProfile, creatorId) {
        if (isOwnProfile) currentProfile else users.firstOrNull { it.userId == creatorId }
    }
    val isFollowing = creatorId in followedIds
    val isBlocked = creatorId in (currentProfile?.blockedUserIds.orEmpty())
    val followersCount = followerCounts[creatorId] ?: creatorProfile?.followersCount ?: 0L

    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("creator_profile_back")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Text("@${creatorProfile?.username ?: "creator"}")
                },
                actions = {
                    if (!isOwnProfile) {
                        IconButton(
                            onClick = { snapViewModel.toggleBlockUser(creatorId, isBlocked) }
                        ) {
                            Icon(
                                Icons.Rounded.Block,
                                contentDescription = if (isBlocked) "Unblock creator" else "Block creator",
                                tint = if (isBlocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
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
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SnapAvatar(
                    avatarUrl = creatorProfile?.avatarUrl ?: "",
                    name = creatorProfile?.displayName ?: "Creator",
                    size = 86.dp,
                    ringColor = SnapEmerald
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = creatorProfile?.displayName ?: "SNAP Creator",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    if (creatorProfile?.privateAccount == true) {
                        Icon(Icons.Rounded.Lock, contentDescription = "Private account", modifier = Modifier.size(18.dp))
                    }
                }
                if (!creatorProfile?.bio.isNullOrBlank()) {
                    Text(
                        text = creatorProfile?.bio ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val uploadedReels = (creatorReelsState as? UiState.Success)?.data.orEmpty()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ProfileStatItem(formatCompactCount(uploadedReels.size.toLong()), "Reels")
                    ProfileStatItem(formatCompactCount(followersCount), "Followers")
                    ProfileStatItem(
                        formatCompactCount(
                            if (isOwnProfile) followedIds.size.toLong() else (creatorProfile?.followingCount ?: 0L)
                        ),
                        "Following"
                    )
                }

                if (!isOwnProfile && creatorProfile != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { reelsViewModel.toggleFollow(creatorId) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isFollowing) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
                                contentColor = if (isFollowing) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("creator_follow_button")
                        ) {
                            Text(if (isFollowing) "Following" else "Follow")
                        }
                        OutlinedButton(
                            onClick = { snapViewModel.startDirectChat(creatorProfile, onOpenChat) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("creator_message_button")
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Message")
                        }
                    }
                }
            }

            if (isOwnProfile) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Uploaded") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Saved") }
                    )
                }
            }

            when (val state = creatorReelsState) {
                is UiState.Loading -> SnapLoadingState("Loading creator gallery...")
                is UiState.Error -> Text(state.message, modifier = Modifier.padding(16.dp))
                is UiState.Success -> {
                    val isLockedPrivateAccount = !isOwnProfile && creatorProfile?.privateAccount == true && !isFollowing
                    val gallery = when {
                        isLockedPrivateAccount -> emptyList()
                        isOwnProfile && selectedTab == 1 ->
                            (allFeedState as? UiState.Success)?.data.orEmpty().filter { it.id in savedIds }
                        !isOwnProfile && !isFollowing ->
                            state.data.filter { it.visibility == ReelVisibility.PUBLIC.value }
                        else -> state.data
                    }

                    if (isLockedPrivateAccount) {
                        SnapEmptyState(
                            title = "This account is private",
                            subtitle = "Follow @${creatorProfile?.username ?: "creator"} to watch their SNAP Reels."
                        )
                    } else if (gallery.isEmpty()) {
                        SnapEmptyState(
                            title = if (selectedTab == 1) "No saved Reels yet" else "No uploaded Reels yet",
                            subtitle = if (selectedTab == 1) {
                                "Tap the bookmark icon on any SNAP Reel to save it here."
                            } else {
                                "Uploaded 15s, 30s, and 60s Reels appear here."
                            }
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            contentPadding = PaddingValues(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(gallery, key = { it.id }) { reel ->
                                ReelGridCard(
                                    reel = reel,
                                    onClick = { onWatchReel(reel.id) },
                                    onDelete = if (isOwnProfile && selectedTab == 0) {
                                        { reelsViewModel.deleteReel(reel) }
                                    } else null
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileStatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ReelGridCard(
    reel: Reel,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(9f / 14f)
            .clip(RoundedCornerShape(12.dp))
            .background(SnapObsidian)
            .clickable(onClick = onClick)
    ) {
        if (reel.thumbnailUrl.isNotBlank()) {
            AsyncImage(
                model = reel.thumbnailUrl,
                contentDescription = reel.caption,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))
                    )
                )
        )
        if (onDelete != null) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(32.dp)
            ) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = "Delete Reel",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = SnapEmerald,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = formatCompactCount(reel.viewsCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }
            if (reel.caption.isNotBlank()) {
                Text(
                    text = reel.caption,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
