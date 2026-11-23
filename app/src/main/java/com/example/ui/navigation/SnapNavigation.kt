package com.example.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.DonutLarge
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.R
import com.example.data.firebase.AuthManager
import com.example.data.firebase.ReelsRepository
import com.example.data.firebase.SnapRepository
import com.example.data.model.ChatThread
import com.example.data.model.UiState
import com.example.ui.components.SnapLoadingState
import com.example.ui.screens.ActiveCallSession
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CallsScreen
import com.example.ui.screens.ChatDetailScreen
import com.example.ui.screens.ChatsScreen
import com.example.ui.screens.CreateReelScreen
import com.example.ui.screens.CreatorProfileScreen
import com.example.ui.screens.DiscoveryScreen
import com.example.ui.screens.LiveCallOverlay
import com.example.ui.screens.ReelsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.UpdatesScreen
import com.example.ui.theme.SnapEmerald
import com.example.ui.theme.SnapObsidian
import com.example.ui.viewmodel.ReelsViewModel
import com.example.ui.viewmodel.SnapViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.serialization.Serializable

@Serializable
object ChatsRoute

@Serializable
object UpdatesRoute

@Serializable
data class ReelsRoute(val initialReelId: String? = null)

@Serializable
object CallsRoute

@Serializable
object SettingsRoute

@Serializable
data class ChatDetailRoute(val chatId: String)

@Serializable
object CreateReelRoute

@Serializable
data class DiscoveryRoute(val initialQuery: String = "")

@Serializable
data class CreatorProfileRoute(val creatorId: String)

private data class BottomTabItem(
    val route: Any,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@Composable
fun AppContent() {
    val authUser by AuthManager.authStateFlow().collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    val user = authUser
    if (user == null) {
        AuthScreen(onAuthSuccess = { /* authStateFlow updates automatically */ })
        return
    }

    key(user.uid) {
        val context = LocalContext.current
        val appContext = context.applicationContext
        val databaseId = remember(appContext) {
            appContext.getString(R.string.firestore_database_id)
        }
        val firestore = remember(databaseId) {
            FirebaseFirestore.getInstance(databaseId)
        }
        val snapRepo = remember(firestore) { SnapRepository(firestore) }
        val reelsRepo = remember(firestore) { ReelsRepository(firestore) }

        val snapViewModel: SnapViewModel = viewModel(
            key = "snap_${user.uid}",
            factory = viewModelFactory {
                initializer { SnapViewModel(snapRepo, user.uid) }
            }
        )
        val reelsViewModel: ReelsViewModel = viewModel(
            key = "reels_${user.uid}",
            factory = viewModelFactory {
                initializer { ReelsViewModel(reelsRepo, snapRepo, user.uid) }
            }
        )

        MainSnapShell(
            snapViewModel = snapViewModel,
            reelsViewModel = reelsViewModel
        )
    }
}

@Composable
private fun MainSnapShell(
    snapViewModel: SnapViewModel,
    reelsViewModel: ReelsViewModel
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = backStackEntry?.destination

    val chatsState by snapViewModel.chatsState.collectAsStateWithLifecycle()
    val statusBanner by snapViewModel.statusBanner.collectAsStateWithLifecycle()
    val actionMessage by reelsViewModel.actionMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var activeCall by remember { mutableStateOf<ActiveCallSession?>(null) }
    var cachedOpenedChat by remember { mutableStateOf<ChatThread?>(null) }

    LaunchedEffect(statusBanner) {
        val msg = statusBanner
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            snapViewModel.clearBanner()
        }
    }

    LaunchedEffect(actionMessage) {
        val msg = actionMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            reelsViewModel.clearActionMessage()
        }
    }

    val tabs = remember {
        listOf(
            BottomTabItem(ChatsRoute, R.string.nav_chats, Icons.AutoMirrored.Rounded.Chat, Icons.AutoMirrored.Outlined.Chat, "tab_chats"),
            BottomTabItem(UpdatesRoute, R.string.nav_updates, Icons.Rounded.DonutLarge, Icons.Outlined.DonutLarge, "tab_updates"),
            BottomTabItem(ReelsRoute(), R.string.nav_reels, Icons.Rounded.PlayCircle, Icons.Outlined.PlayCircle, "tab_reels"),
            BottomTabItem(CallsRoute, R.string.nav_calls, Icons.Rounded.Call, Icons.Outlined.Call, "tab_calls"),
            BottomTabItem(SettingsRoute, R.string.nav_settings, Icons.Rounded.Settings, Icons.Outlined.Settings, "tab_settings")
        )
    }

    val isReelsTab = currentDest?.hasRoute<ReelsRoute>() == true
    val isTopLevelTab = currentDest?.let { dest ->
        dest.hasRoute<ChatsRoute>() ||
            dest.hasRoute<UpdatesRoute>() ||
            dest.hasRoute<ReelsRoute>() ||
            dest.hasRoute<CallsRoute>() ||
            dest.hasRoute<SettingsRoute>()
    } ?: true

    fun navigateToTopLevel(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = route !is ReelsRoute || route.initialReelId == null
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 840.dp

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (!useNavRail && isTopLevelTab) {
                    val containerColor = if (isReelsTab) SnapObsidian else MaterialTheme.colorScheme.surface
                    val contentColor = if (isReelsTab) Color.White else MaterialTheme.colorScheme.onSurface
                    NavigationBar(
                        containerColor = containerColor,
                        contentColor = contentColor,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        tabs.forEach { tab ->
                            val selected = when (tab.route) {
                                is ChatsRoute -> currentDest?.hasRoute<ChatsRoute>() == true
                                is UpdatesRoute -> currentDest?.hasRoute<UpdatesRoute>() == true
                                is ReelsRoute -> currentDest?.hasRoute<ReelsRoute>() == true
                                is CallsRoute -> currentDest?.hasRoute<CallsRoute>() == true
                                is SettingsRoute -> currentDest?.hasRoute<SettingsRoute>() == true
                                else -> false
                            }
                            val label = stringResource(tab.labelRes)
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTopLevel(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = if (isReelsTab) SnapObsidian else MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor = if (isReelsTab) SnapEmerald else MaterialTheme.colorScheme.primary,
                                    indicatorColor = if (isReelsTab) SnapEmerald else MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = if (isReelsTab) Color(0xFFB4C0CC) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = if (isReelsTab) Color(0xFFB4C0CC) else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail && isTopLevelTab) {
                    NavigationRail(
                        containerColor = if (isReelsTab) SnapObsidian else MaterialTheme.colorScheme.surface
                    ) {
                        tabs.forEach { tab ->
                            val selected = when (tab.route) {
                                is ChatsRoute -> currentDest?.hasRoute<ChatsRoute>() == true
                                is UpdatesRoute -> currentDest?.hasRoute<UpdatesRoute>() == true
                                is ReelsRoute -> currentDest?.hasRoute<ReelsRoute>() == true
                                is CallsRoute -> currentDest?.hasRoute<CallsRoute>() == true
                                is SettingsRoute -> currentDest?.hasRoute<SettingsRoute>() == true
                                else -> false
                            }
                            val label = stringResource(tab.labelRes)
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTopLevel(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = ChatsRoute
                    ) {
                        composable<ChatsRoute> {
                            ChatsScreen(
                                snapViewModel = snapViewModel,
                                onOpenChat = { chat ->
                                    cachedOpenedChat = chat
                                    snapViewModel.selectChat(chat.id)
                                    navController.navigate(ChatDetailRoute(chat.id))
                                },
                                onNavigateToReels = { navigateToTopLevel(ReelsRoute()) }
                            )
                        }

                        composable<UpdatesRoute> {
                            UpdatesScreen(snapViewModel = snapViewModel)
                        }

                        composable<ReelsRoute> { entry ->
                            val args = entry.toRoute<ReelsRoute>()
                            ReelsScreen(
                                reelsViewModel = reelsViewModel,
                                snapViewModel = snapViewModel,
                                isTabActive = isReelsTab && activeCall == null,
                                initialReelId = args.initialReelId,
                                onOpenCreateReel = { navController.navigate(CreateReelRoute) },
                                onOpenDiscovery = { query -> navController.navigate(DiscoveryRoute(query)) },
                                onOpenCreatorProfile = { uid -> navController.navigate(CreatorProfileRoute(uid)) }
                            )
                        }

                        composable<CallsRoute> {
                            CallsScreen(
                                snapViewModel = snapViewModel,
                                onLaunchCallSession = { session -> activeCall = session }
                            )
                        }

                        composable<SettingsRoute> {
                            SettingsScreen(
                                snapViewModel = snapViewModel,
                                reelsViewModel = reelsViewModel,
                                onOpenMyCreatorProfile = { uid ->
                                    navController.navigate(CreatorProfileRoute(uid))
                                }
                            )
                        }

                        composable<ChatDetailRoute> { entry ->
                            val args = entry.toRoute<ChatDetailRoute>()
                            val allChats = (chatsState as? UiState.Success)?.data.orEmpty()
                            val chat = allChats.firstOrNull { it.id == args.chatId }
                                ?: cachedOpenedChat?.takeIf { it.id == args.chatId }
                            if (chat != null) {
                                ChatDetailScreen(
                                    chat = chat,
                                    snapViewModel = snapViewModel,
                                    onBack = { navController.popBackStack() },
                                    onWatchSharedReel = { reelId ->
                                        navController.navigate(ReelsRoute(initialReelId = reelId)) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                        }
                                    },
                                    onStartPeerCall = { peer, type ->
                                        snapViewModel.startCall(peer, type) { callId ->
                                            activeCall = ActiveCallSession(
                                                callId = callId,
                                                peerName = peer.displayName,
                                                peerAvatarUrl = peer.avatarUrl,
                                                callType = type
                                            )
                                        }
                                    }
                                )
                            } else {
                                SnapLoadingState("Opening SNAP chat...")
                            }
                        }

                        composable<CreateReelRoute> {
                            CreateReelScreen(
                                reelsViewModel = reelsViewModel,
                                snapViewModel = snapViewModel,
                                onBack = { navController.popBackStack() },
                                onPublished = {
                                    navController.popBackStack()
                                    navigateToTopLevel(ReelsRoute())
                                }
                            )
                        }

                        composable<DiscoveryRoute> { entry ->
                            val args = entry.toRoute<DiscoveryRoute>()
                            DiscoveryScreen(
                                initialQuery = args.initialQuery,
                                reelsViewModel = reelsViewModel,
                                snapViewModel = snapViewModel,
                                onBack = { navController.popBackStack() },
                                onWatchReel = { reelId ->
                                    navController.navigate(ReelsRoute(initialReelId = reelId)) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                    }
                                },
                                onOpenCreator = { uid -> navController.navigate(CreatorProfileRoute(uid)) }
                            )
                        }

                        composable<CreatorProfileRoute> { entry ->
                            val args = entry.toRoute<CreatorProfileRoute>()
                            CreatorProfileScreen(
                                creatorId = args.creatorId,
                                reelsViewModel = reelsViewModel,
                                snapViewModel = snapViewModel,
                                onBack = { navController.popBackStack() },
                                onWatchReel = { reelId ->
                                    navController.navigate(ReelsRoute(initialReelId = reelId)) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                    }
                                },
                                onOpenChat = { chat ->
                                    cachedOpenedChat = chat
                                    snapViewModel.selectChat(chat.id)
                                    navController.navigate(ChatDetailRoute(chat.id))
                                }
                            )
                        }
                    }
                }
            }
        }

        activeCall?.let { callSession ->
            LiveCallOverlay(
                session = callSession,
                onEndCall = { duration ->
                    snapViewModel.finishCall(callSession.callId, duration)
                    activeCall = null
                }
            )
        }
    }
}
