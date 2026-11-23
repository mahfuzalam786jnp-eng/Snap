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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.rounded.AddIcCall
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.CallLog
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.ui.components.SnapAvatar
import com.example.ui.components.SnapEmptyState
import com.example.ui.components.SnapErrorState
import com.example.ui.components.SnapLoadingState
import com.example.ui.components.formatDuration
import com.example.ui.components.formatRelativeTime
import com.example.ui.theme.SnapCoral
import com.example.ui.theme.SnapEmerald
import com.example.ui.theme.SnapObsidian
import com.example.ui.theme.SnapSurfaceElevated
import com.example.ui.viewmodel.SnapViewModel
import kotlinx.coroutines.delay

data class ActiveCallSession(
    val callId: String,
    val peerName: String,
    val peerAvatarUrl: String,
    val callType: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallsScreen(
    snapViewModel: SnapViewModel,
    onLaunchCallSession: (ActiveCallSession) -> Unit
) {
    val callsState by snapViewModel.callsState.collectAsStateWithLifecycle()
    val users by snapViewModel.discoverableUsers.collectAsStateWithLifecycle()
    val currentUserId = snapViewModel.currentUserId

    var showNewCallSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calls", style = MaterialTheme.typography.headlineSmall) }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showNewCallSheet = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("new_call_fab")
            ) {
                Icon(Icons.Rounded.AddIcCall, contentDescription = "Start new call")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = callsState) {
                is UiState.Loading -> SnapLoadingState("Loading call history...")
                is UiState.Error -> SnapErrorState(
                    message = state.message,
                    onRetry = {}
                )
                is UiState.Success -> {
                    if (state.data.isEmpty()) {
                        SnapEmptyState(
                            title = "No recent calls",
                            subtitle = "Place a voice or video call to any SNAP creator or contact.",
                            actionLabel = "Start Call",
                            onAction = { showNewCallSheet = true }
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(state.data, key = { it.id }) { log ->
                                CallLogRow(
                                    log = log,
                                    currentUserId = currentUserId,
                                    onRedial = {
                                        val peerId = if (log.callerId == currentUserId) log.calleeId else log.callerId
                                        val peerName = if (log.callerId == currentUserId) log.calleeName else log.callerName
                                        val peer = users.firstOrNull { it.userId == peerId }
                                            ?: UserProfile(userId = peerId, displayName = peerName)
                                        snapViewModel.startCall(peer, log.callType) { callId ->
                                            onLaunchCallSession(
                                                ActiveCallSession(
                                                    callId = callId,
                                                    peerName = peer.displayName,
                                                    peerAvatarUrl = peer.avatarUrl,
                                                    callType = log.callType
                                                )
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewCallSheet) {
        ModalBottomSheet(onDismissRequest = { showNewCallSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Start a SNAP Call", style = MaterialTheme.typography.titleLarge)
                val peers = users.filter { it.userId != currentUserId }
                if (peers.isEmpty()) {
                    Text(
                        "No other SNAP contacts found yet. Invite a friend to sign in on SNAP!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    LazyColumn(modifier = Modifier.height(280.dp)) {
                        items(peers, key = { it.userId }) { peer ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SnapAvatar(avatarUrl = peer.avatarUrl, name = peer.displayName, size = 44.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(peer.displayName, style = MaterialTheme.typography.titleMedium)
                                    Text("@${peer.username}", style = MaterialTheme.typography.bodySmall)
                                }
                                IconButton(
                                    onClick = {
                                        showNewCallSheet = false
                                        snapViewModel.startCall(peer, "voice") { callId ->
                                            onLaunchCallSession(
                                                ActiveCallSession(callId, peer.displayName, peer.avatarUrl, "voice")
                                            )
                                        }
                                    }
                                ) {
                                    Icon(Icons.Rounded.Call, contentDescription = "Voice call", tint = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(
                                    onClick = {
                                        showNewCallSheet = false
                                        snapViewModel.startCall(peer, "video") { callId ->
                                            onLaunchCallSession(
                                                ActiveCallSession(callId, peer.displayName, peer.avatarUrl, "video")
                                            )
                                        }
                                    }
                                ) {
                                    Icon(Icons.Rounded.Videocam, contentDescription = "Video call", tint = MaterialTheme.colorScheme.primary)
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
private fun CallLogRow(
    log: CallLog,
    currentUserId: String,
    onRedial: () -> Unit
) {
    val isOutgoing = log.callerId == currentUserId
    val peerName = if (isOutgoing) log.calleeName else log.callerName
    val isMissed = log.status == "missed"

    Surface(
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onRedial)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SnapAvatar(avatarUrl = "", name = peerName, size = 48.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = peerName,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isMissed) SnapCoral else MaterialTheme.colorScheme.onSurface
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (isOutgoing) Icons.AutoMirrored.Rounded.CallMade else Icons.AutoMirrored.Rounded.CallReceived,
                        contentDescription = null,
                        tint = if (isMissed) SnapCoral else SnapEmerald,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "${formatRelativeTime(log.createdAt)} • ${formatDuration(log.durationSeconds)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onRedial) {
                Icon(
                    imageVector = if (log.callType == "video") Icons.Rounded.Videocam else Icons.Rounded.Call,
                    contentDescription = "Redial",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun LiveCallOverlay(
    session: ActiveCallSession,
    onEndCall: (Long) -> Unit
) {
    var elapsedSec by remember { mutableLongStateOf(0L) }
    var muted by remember { mutableStateOf(false) }
    var speakerOn by remember { mutableStateOf(session.callType == "video") }
    var videoEnabled by remember { mutableStateOf(session.callType == "video") }

    LaunchedEffect(session.callId) {
        while (true) {
            delay(1000L)
            elapsedSec += 1L
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF0A1D24), SnapObsidian))
            )
            .safeDrawingPadding()
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (session.callType == "video") "SNAP VIDEO CALL" else "SNAP VOICE CALL",
                    style = MaterialTheme.typography.labelMedium,
                    color = SnapEmerald
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = formatDuration(elapsedSec),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SnapAvatar(
                    avatarUrl = session.peerAvatarUrl,
                    name = session.peerName,
                    size = 112.dp,
                    ringColor = SnapEmerald
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = session.peerName,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
                Text(
                    text = if (videoEnabled) "HD Video Active" else "Connected • Encrypted",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFA0B0C0)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CallControlCircle(
                    icon = if (muted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                    label = if (muted) "Unmute" else "Mute",
                    active = muted,
                    onClick = { muted = !muted }
                )
                CallControlCircle(
                    icon = if (videoEnabled) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff,
                    label = "Camera",
                    active = videoEnabled,
                    onClick = { videoEnabled = !videoEnabled }
                )
                CallControlCircle(
                    icon = Icons.Rounded.VolumeUp,
                    label = "Speaker",
                    active = speakerOn,
                    onClick = { speakerOn = !speakerOn }
                )
                Surface(
                    onClick = { onEndCall(elapsedSec) },
                    shape = CircleShape,
                    color = SnapCoral,
                    modifier = Modifier
                        .size(64.dp)
                        .testTag("end_call_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.CallEnd, contentDescription = "End call", tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun CallControlCircle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = if (active) SnapEmerald else SnapSurfaceElevated,
            modifier = Modifier.size(54.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (active) SnapObsidian else Color.White
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White)
    }
}
