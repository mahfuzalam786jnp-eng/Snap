package com.example.ui.screens

import android.net.Uri
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.R
import com.example.data.media.MediaCacheManager
import com.example.data.model.Reel
import com.example.data.model.ReelFeedMode
import com.example.data.model.UiState
import com.example.data.model.VideoQualityOption
import com.example.ui.components.SnapAvatar
import com.example.ui.components.SnapEmptyState
import com.example.ui.components.SnapErrorState
import com.example.ui.components.SnapLoadingState
import com.example.ui.components.formatCompactCount
import com.example.ui.theme.SnapCoral
import com.example.ui.theme.SnapEmerald
import com.example.ui.theme.SnapObsidian
import com.example.ui.theme.SnapTheme
import com.example.ui.viewmodel.ReelsViewModel
import com.example.ui.viewmodel.SnapViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun ReelsScreen(
    reelsViewModel: ReelsViewModel,
    snapViewModel: SnapViewModel,
    isTabActive: Boolean,
    initialReelId: String? = null,
    onOpenCreateReel: () -> Unit,
    onOpenDiscovery: (String) -> Unit,
    onOpenCreatorProfile: (String) -> Unit
) {
    val feedState by reelsViewModel.reelsFeedState.collectAsStateWithLifecycle()
    val feedMode by reelsViewModel.feedMode.collectAsStateWithLifecycle()
    val likedIds by reelsViewModel.likedReelIds.collectAsStateWithLifecycle()
    val savedIds by reelsViewModel.savedReelIds.collectAsStateWithLifecycle()
    val followedIds by reelsViewModel.followedCreatorIds.collectAsStateWithLifecycle()
    val isMuted by reelsViewModel.isMuted.collectAsStateWithLifecycle()
    val volume by reelsViewModel.volume.collectAsStateWithLifecycle()
    val qualityOption by reelsViewModel.qualityOption.collectAsStateWithLifecycle()
    val autoPlayNext by reelsViewModel.autoPlayNext.collectAsStateWithLifecycle()
    val currentProfile by snapViewModel.currentUserProfile.collectAsStateWithLifecycle()

    var commentsReel by remember { mutableStateOf<Reel?>(null) }
    var shareReel by remember { mutableStateOf<Reel?>(null) }
    var reportReel by remember { mutableStateOf<Reel?>(null) }
    var settingsReel by remember { mutableStateOf<Reel?>(null) }

    SnapTheme(darkTheme = true) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SnapObsidian)
    ) {
        when (val state = feedState) {
            is UiState.Loading -> {
                SnapLoadingState("Loading SNAP Reels...")
            }
            is UiState.Error -> {
                SnapErrorState(
                    message = state.message,
                    onRetry = { reelsViewModel.refreshFeed() }
                )
            }
            is UiState.Success -> {
                val reels = state.data
                if (reels.isEmpty()) {
                    SnapEmptyState(
                        title = if (feedMode == ReelFeedMode.FOLLOWING) {
                            "No Reels from followed creators"
                        } else {
                            "No SNAP Reels yet"
                        },
                        subtitle = "Record a 15s, 30s, or 60s vertical video or upload from your gallery with licensed music.",
                        actionLabel = "Create SNAP Reel",
                        onAction = onOpenCreateReel,
                        secondaryLabel = "Explore Discovery",
                        onSecondary = { onOpenDiscovery("") },
                        headerContent = {
                            Image(
                                painter = painterResource(id = R.drawable.img_empty_reels),
                                contentDescription = "Create your first SNAP Reel",
                                modifier = Modifier
                                    .size(180.dp)
                                    .clip(RoundedCornerShape(24.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                    )
                } else {
                    val pagerState = rememberPagerState(pageCount = { reels.size })
                    val scope = rememberCoroutineScope()
                    val context = LocalContext.current

                    LaunchedEffect(initialReelId, reels.size) {
                        if (!initialReelId.isNullOrBlank()) {
                            val idx = reels.indexOfFirst { it.id == initialReelId }
                            if (idx >= 0) pagerState.scrollToPage(idx)
                        }
                    }

                    // Pagination & Next Video Preloading
                    LaunchedEffect(pagerState.settledPage, reels.size) {
                        val current = reels.getOrNull(pagerState.settledPage)
                        if (current != null) {
                            reelsViewModel.recordReelView(current.id)
                        }
                        val next = reels.getOrNull(pagerState.settledPage + 1)
                        if (next != null) {
                            MediaCacheManager.preloadNextVideo(context, next.videoUrl, scope)
                        }
                        if (pagerState.settledPage >= reels.size - 3) {
                            reelsViewModel.loadNextPage()
                        }
                    }

                    VerticalPager(
                        state = pagerState,
                        beyondViewportPageCount = 1,
                        key = { index -> reels[index].id },
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("reels_vertical_pager")
                    ) { pageIndex ->
                        val reel = reels[pageIndex]
                        val isCurrentPage = pagerState.settledPage == pageIndex && !pagerState.isScrollInProgress

                        ReelVideoPage(
                            reel = reel,
                            isActive = isTabActive && isCurrentPage,
                            isLiked = reel.id in likedIds,
                            isSaved = reel.id in savedIds,
                            isFollowing = reel.creatorId in followedIds,
                            isOwner = reel.creatorId == reelsViewModel.currentUserId,
                            isMuted = isMuted,
                            volume = volume,
                            qualityOption = qualityOption,
                            autoPlayNext = autoPlayNext,
                            onVideoEnded = {
                                if (autoPlayNext && pageIndex + 1 < reels.size) {
                                    scope.launch {
                                        pagerState.animateScrollToPage(pageIndex + 1)
                                    }
                                }
                            },
                            onDoubleTapLike = { reelsViewModel.ensureLiked(reel.id) },
                            onToggleLike = { reelsViewModel.toggleLike(reel.id, reel.id in likedIds) },
                            onOpenComments = { commentsReel = reel },
                            onShare = { shareReel = reel },
                            onToggleSave = { reelsViewModel.toggleSave(reel.id, reel.id in savedIds) },
                            onToggleFollow = { reelsViewModel.toggleFollow(reel.creatorId) },
                            onToggleMute = { reelsViewModel.toggleMute() },
                            onOpenMore = { settingsReel = reel },
                            onCreatorClick = { onOpenCreatorProfile(reel.creatorId) },
                            onHashtagClick = { tag -> onOpenDiscovery(tag) }
                        )
                    }
                }
            }
        }

        // Top Floating Reels Header (For You | Following | Trending + Search + Create)
        ReelsTopOverlayBar(
            selectedMode = feedMode,
            onSelectMode = { reelsViewModel.setFeedMode(it) },
            onSearchClick = { onOpenDiscovery("") },
            onCreateClick = onOpenCreateReel,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
        )
    }

    commentsReel?.let { target ->
        CommentsBottomSheet(
            reel = target,
            reelsViewModel = reelsViewModel,
            currentProfile = currentProfile,
            onDismiss = { commentsReel = null },
            onCreatorClick = { uid ->
                commentsReel = null
                onOpenCreatorProfile(uid)
            }
        )
    }

    shareReel?.let { target ->
        ShareToChatSheet(
            reel = target,
            snapViewModel = snapViewModel,
            reelsViewModel = reelsViewModel,
            onDismiss = { shareReel = null }
        )
    }

    reportReel?.let { target ->
        ReportReelDialog(
            reel = target,
            onDismiss = { reportReel = null },
            onSubmit = { reason, details ->
                reelsViewModel.reportReel(target.id, reason, details)
                reportReel = null
            }
        )
    }

    settingsReel?.let { target ->
        PlaybackSettingsSheet(
            volume = volume,
            qualityOption = qualityOption,
            autoPlayNext = autoPlayNext,
            isOwner = target.creatorId == reelsViewModel.currentUserId,
            onVolumeChange = { reelsViewModel.setVolume(it) },
            onQualityChange = { reelsViewModel.setQualityOption(it) },
            onAutoPlayChange = { reelsViewModel.setAutoPlayNext(it) },
            onReportClick = { reportReel = target },
            onBlockCreatorClick = { snapViewModel.toggleBlockUser(target.creatorId, currentlyBlocked = false) },
            onDeleteClick = { reelsViewModel.deleteReel(target) },
            onDismiss = { settingsReel = null }
        )
    }
    }
}

@Composable
private fun ReelsTopOverlayBar(
    selectedMode: ReelFeedMode,
    onSelectMode: (ReelFeedMode) -> Unit,
    onSearchClick: () -> Unit,
    onCreateClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent)
                )
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(
            onClick = onSearchClick,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("reels_discovery_button")
        ) {
            Icon(Icons.Rounded.Search, contentDescription = "Search videos & creators", tint = Color.White)
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ReelFeedMode.entries.forEach { mode ->
                val isSelected = mode == selectedMode
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelectMode(mode) }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .testTag("feed_tab_${mode.name.lowercase()}")
                ) {
                    Text(
                        text = mode.label,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.65f)
                    )
                    if (isSelected) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Box(
                            modifier = Modifier
                                .width(20.dp)
                                .height(3.dp)
                                .clip(CircleShape)
                                .background(SnapEmerald)
                        )
                    }
                }
            }
        }

        Surface(
            onClick = onCreateClick,
            shape = CircleShape,
            color = SnapEmerald,
            modifier = Modifier
                .size(40.dp)
                .testTag("create_reel_button")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Add, contentDescription = "Upload or record Reel", tint = SnapObsidian)
            }
        }
    }
}

@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReelVideoPage(
    reel: Reel,
    isActive: Boolean,
    isLiked: Boolean,
    isSaved: Boolean,
    isFollowing: Boolean,
    isOwner: Boolean,
    isMuted: Boolean,
    volume: Float,
    qualityOption: VideoQualityOption,
    autoPlayNext: Boolean,
    onVideoEnded: () -> Unit,
    onDoubleTapLike: () -> Unit,
    onToggleLike: () -> Unit,
    onOpenComments: () -> Unit,
    onShare: () -> Unit,
    onToggleSave: () -> Unit,
    onToggleFollow: () -> Unit,
    onToggleMute: () -> Unit,
    onOpenMore: () -> Unit,
    onCreatorClick: () -> Unit,
    onHashtagClick: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var userPaused by remember(reel.id) { mutableStateOf(false) }
    var isBuffering by remember(reel.id) { mutableStateOf(true) }
    var playerError by remember(reel.id) { mutableStateOf<String?>(null) }
    var playbackProgress by remember(reel.id) { mutableFloatStateOf(0f) }
    var showPauseOverlay by remember { mutableStateOf(false) }
    var doubleTapPosition by remember { mutableStateOf<Offset?>(null) }
    val heartScale = remember { Animatable(0f) }

    val exoPlayer = remember(reel.id) {
        MediaCacheManager.createReelsPlayer(context, qualityOption).apply {
            repeatMode = if (autoPlayNext) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE
            setMediaItem(MediaItem.fromUri(Uri.parse(reel.videoUrl)))
            prepare()
        }
    }

    LaunchedEffect(qualityOption) {
        MediaCacheManager.updatePlayerQuality(exoPlayer, qualityOption)
    }

    LaunchedEffect(autoPlayNext) {
        exoPlayer.repeatMode = if (autoPlayNext) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE
    }

    LaunchedEffect(isMuted, volume) {
        exoPlayer.volume = if (isMuted) 0f else volume
    }

    LaunchedEffect(isActive, userPaused) {
        exoPlayer.playWhenReady = isActive && !userPaused
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    playerError = null
                }
                if (playbackState == Player.STATE_ENDED && autoPlayNext) {
                    onVideoEnded()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                playerError = error.localizedMessage ?: "Video playback failed"
            }
        }
        exoPlayer.addListener(listener)

        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> exoPlayer.playWhenReady = false
                Lifecycle.Event.ON_RESUME -> exoPlayer.playWhenReady = isActive && !userPaused
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(exoPlayer, isActive) {
        while (isActive) {
            val dur = exoPlayer.duration
            if (dur > 0L) {
                playbackProgress = (exoPlayer.currentPosition.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
            }
            delay(200L)
        }
    }

    val spinTransition = rememberInfiniteTransition(label = "disc_spin")
    val discAngle by spinTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "disc_angle"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SnapObsidian)
            .pointerInput(reel.id) {
                detectTapGestures(
                    onTap = {
                        userPaused = !userPaused
                        showPauseOverlay = true
                        scope.launch {
                            delay(700L)
                            showPauseOverlay = false
                        }
                    },
                    onDoubleTap = { offset ->
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        doubleTapPosition = offset
                        onDoubleTapLike()
                        scope.launch {
                            heartScale.snapTo(0.2f)
                            heartScale.animateTo(1.25f, animationSpec = spring(dampingRatio = 0.45f))
                            delay(350L)
                            heartScale.animateTo(0f, animationSpec = tween(220))
                            doubleTapPosition = null
                        }
                    }
                )
            }
    ) {
        // 9:16 Video Player Surface
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { view ->
                if (view.player !== exoPlayer) {
                    view.player = exoPlayer
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Show thumbnail behind while initial frame buffers
        if (isBuffering && reel.thumbnailUrl.isNotBlank() && playbackProgress == 0f) {
            AsyncImage(
                model = reel.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.6f),
                contentScale = ContentScale.Crop
            )
        }

        // Bottom & Top Scrims for legibility
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                    )
                )
        )

        if (isBuffering) {
            CircularProgressIndicator(
                color = SnapEmerald,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(44.dp)
            )
        }

        if (playerError != null) {
            Surface(
                color = Color.Black.copy(alpha = 0.72f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Unable to stream this Reel", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Text(
                        playerError ?: "",
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = userPaused || showPauseOverlay,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.55f),
                shape = CircleShape,
                modifier = Modifier.size(68.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (userPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                        contentDescription = if (userPaused) "Paused" else "Playing",
                        tint = Color.White,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }
        }

        // Double Tap Heart Animation
        doubleTapPosition?.let { pos ->
            Icon(
                imageVector = Icons.Rounded.Favorite,
                contentDescription = null,
                tint = SnapCoral,
                modifier = Modifier
                    .offset { IntOffset((pos.x - 52).roundToInt(), (pos.y - 52).roundToInt()) }
                    .size(104.dp)
                    .scale(heartScale.value)
            )
        }

        // Right Action Rail
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Creator Avatar + Quick Follow Badge
            Box(contentAlignment = Alignment.BottomCenter) {
                SnapAvatar(
                    avatarUrl = reel.creatorAvatarUrl,
                    name = reel.creatorUsername,
                    size = 48.dp,
                    ringColor = SnapEmerald,
                    modifier = Modifier
                        .clickable(onClick = onCreatorClick)
                        .testTag("reel_creator_avatar")
                )
                if (!isOwner && !isFollowing) {
                    Surface(
                        onClick = onToggleFollow,
                        shape = CircleShape,
                        color = SnapCoral,
                        modifier = Modifier
                            .offset(y = 8.dp)
                            .size(22.dp)
                            .testTag("reel_quick_follow")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.Add,
                                contentDescription = "Follow creator",
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            ReelActionIcon(
                icon = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                tint = if (isLiked) SnapCoral else Color.White,
                label = formatCompactCount(reel.likesCount),
                contentDescription = "Like Reel",
                tag = "reel_like_button",
                onClick = onToggleLike
            )

            ReelActionIcon(
                icon = Icons.AutoMirrored.Rounded.Chat,
                tint = Color.White,
                label = formatCompactCount(reel.commentsCount),
                contentDescription = "Comments",
                tag = "reel_comment_button",
                onClick = onOpenComments
            )

            ReelActionIcon(
                icon = Icons.AutoMirrored.Rounded.Send,
                tint = Color.White,
                label = formatCompactCount(reel.sharesCount),
                contentDescription = "Share Reel",
                tag = "reel_share_button",
                onClick = onShare
            )

            ReelActionIcon(
                icon = if (isSaved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                tint = if (isSaved) SnapEmerald else Color.White,
                label = formatCompactCount(reel.savesCount),
                contentDescription = "Save Reel",
                tag = "reel_save_button",
                onClick = onToggleSave
            )

            ReelActionIcon(
                icon = if (isMuted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                tint = Color.White,
                label = if (isMuted) "Muted" else "Sound",
                contentDescription = "Toggle mute",
                tag = "reel_mute_button",
                onClick = onToggleMute
            )

            IconButton(
                onClick = onOpenMore,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("reel_more_button")
            ) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "More options", tint = Color.White)
            }

            // Spinning Music Disc
            Image(
                painter = painterResource(id = R.drawable.img_music_disc),
                contentDescription = "Audio disc",
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .border(2.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                    .rotate(if (isActive && !userPaused) discAngle else 0f),
                contentScale = ContentScale.Crop
            )
        }

        // Bottom Left Creator, Caption, Hashtags & Music Info
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, end = 86.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "@${reel.creatorUsername}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    modifier = Modifier.clickable(onClick = onCreatorClick)
                )
                Surface(
                    color = Color.White.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = "${formatCompactCount(reel.viewsCount)} views • ${reel.durationSeconds}s",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            if (reel.caption.isNotBlank()) {
                Text(
                    text = reel.caption,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (reel.hashtags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    reel.hashtags.take(5).forEach { tag ->
                        Text(
                            text = "#$tag",
                            style = MaterialTheme.typography.labelMedium,
                            color = SnapEmerald,
                            modifier = Modifier.clickable { onHashtagClick(tag) }
                        )
                    }
                }
            }

            Surface(
                color = Color.Black.copy(alpha = 0.45f),
                shape = RoundedCornerShape(50)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = SnapEmerald,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "${reel.musicTitle} • ${reel.musicArtist}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (reel.copyrightVerified) {
                        Icon(
                            Icons.Rounded.Verified,
                            contentDescription = "Licensed audio",
                            tint = SnapEmerald,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        // Video Playback Progress Scrubber Line at bottom edge
        LinearProgressIndicator(
            progress = { playbackProgress },
            color = SnapEmerald,
            trackColor = Color.White.copy(alpha = 0.2f),
            modifier = Modifier
                .fillMaxWidth()
                .height(2.5.dp)
                .align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun ReelActionIcon(
    icon: ImageVector,
    tint: Color,
    label: String,
    contentDescription: String,
    tag: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .testTag(tag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White
        )
    }
}
