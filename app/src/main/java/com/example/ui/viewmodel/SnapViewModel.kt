package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.SnapRepository
import com.example.data.firebase.extractFirestoreMessage
import com.example.data.model.CallLog
import com.example.data.model.ChatMessage
import com.example.data.model.ChatThread
import com.example.data.model.StatusUpdate
import com.example.data.model.UiState
import com.example.data.model.UserPrivateSettings
import com.example.data.model.UserProfile
import com.example.data.model.VideoQualityOption
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SnapViewModel(
    private val snapRepository: SnapRepository,
    val currentUserId: String
) : ViewModel() {

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _activeChatId = MutableStateFlow<String?>(null)
    val activeChatId: StateFlow<String?> = _activeChatId.asStateFlow()

    val currentUserProfile: StateFlow<UserProfile?> = snapRepository.observeUserProfile(currentUserId)
        .catch { error ->
            unwrapOrThrowCancellation(error)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val privateSettings: StateFlow<UserPrivateSettings?> = snapRepository.observePrivateSettings()
        .catch { error ->
            unwrapOrThrowCancellation(error)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val discoverableUsers: StateFlow<List<UserProfile>> = snapRepository.observeDiscoverableUsers()
        .catch { error ->
            unwrapOrThrowCancellation(error)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val chatsState: StateFlow<UiState<List<ChatThread>>> = snapRepository.observeChats()
        .combine(currentUserProfile) { chats, profile ->
            val blocked = profile?.blockedUserIds?.toSet() ?: emptySet()
            chats.filter { chat ->
                chat.memberUids.none { it != currentUserId && it in blocked }
            }
        }
        .map<List<ChatThread>, UiState<List<ChatThread>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = unwrapOrThrowCancellation(error)
            emit(UiState.Error(extractFirestoreMessage(firestoreError, "Failed to load SNAP chats.")))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeMessagesState: StateFlow<UiState<List<ChatMessage>>> = _activeChatId
        .flatMapLatest { chatId ->
            if (chatId.isNullOrBlank()) {
                flowOf<UiState<List<ChatMessage>>>(UiState.Success(emptyList()))
            } else {
                snapRepository.observeChatMessages(chatId)
                    .map<List<ChatMessage>, UiState<List<ChatMessage>>> { UiState.Success(it) }
                    .catch { error ->
                        val firestoreError = unwrapOrThrowCancellation(error)
                        emit(UiState.Error(extractFirestoreMessage(firestoreError, "Failed to load messages.")))
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val updatesState: StateFlow<UiState<List<StatusUpdate>>> = snapRepository.observePublicStatusUpdates()
        .combine(currentUserProfile) { updates, profile ->
            val blocked = profile?.blockedUserIds?.toSet() ?: emptySet()
            updates.filter { it.userId !in blocked }
        }
        .map<List<StatusUpdate>, UiState<List<StatusUpdate>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = unwrapOrThrowCancellation(error)
            emit(UiState.Error(extractFirestoreMessage(firestoreError, "Failed to load status updates.")))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val callsState: StateFlow<UiState<List<CallLog>>> = snapRepository.observeCallLogs()
        .map<List<CallLog>, UiState<List<CallLog>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = unwrapOrThrowCancellation(error)
            emit(UiState.Error(extractFirestoreMessage(firestoreError, "Failed to load call logs.")))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    init {
        viewModelScope.launch {
            snapRepository.ensureUserProfileInitialized()
                .onFailure { _statusBanner.value = extractFirestoreMessage(it, "Could not initialize profile") }
        }
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    fun selectChat(chatId: String?) {
        _activeChatId.value = chatId
    }

    fun startDirectChat(peer: UserProfile, onOpened: (ChatThread) -> Unit) {
        val myProfile = currentUserProfile.value ?: UserProfile(userId = currentUserId, displayName = "SNAP User")
        viewModelScope.launch {
            snapRepository.getOrCreateDirectChat(peer, myProfile)
                .onSuccess { chat ->
                    _activeChatId.value = chat.id
                    onOpened(chat)
                }
                .onFailure {
                    _statusBanner.value = extractFirestoreMessage(it, "Failed to open chat")
                }
        }
    }

    fun openSelfNotesChat(onOpened: (ChatThread) -> Unit) {
        val myProfile = currentUserProfile.value ?: UserProfile(userId = currentUserId, displayName = "SNAP User")
        viewModelScope.launch {
            snapRepository.createSelfNoteChat(myProfile)
                .onSuccess { chat ->
                    _activeChatId.value = chat.id
                    onOpened(chat)
                }
                .onFailure {
                    _statusBanner.value = extractFirestoreMessage(it, "Failed to open Saved Notes")
                }
        }
    }

    fun sendTextMessage(chat: ChatThread, text: String) {
        val senderName = currentUserProfile.value?.displayName?.ifBlank { "SNAP User" } ?: "SNAP User"
        viewModelScope.launch {
            snapRepository.sendMessage(chat, senderName, text, messageType = "text")
                .onFailure {
                    _statusBanner.value = extractFirestoreMessage(it, "Failed to send message")
                }
        }
    }

    fun postStatusUpdate(caption: String, mediaUrl: String?, backgroundHex: String, onDone: () -> Unit) {
        val myProfile = currentUserProfile.value ?: return
        viewModelScope.launch {
            snapRepository.postStatusUpdate(myProfile, caption, mediaUrl, backgroundHex)
                .onSuccess {
                    _statusBanner.value = "Status update published!"
                    onDone()
                }
                .onFailure {
                    _statusBanner.value = extractFirestoreMessage(it, "Could not post status")
                }
        }
    }

    fun markStatusViewed(updateId: String) {
        viewModelScope.launch {
            snapRepository.markStatusViewed(updateId)
        }
    }

    fun deleteStatusUpdate(updateId: String) {
        viewModelScope.launch {
            snapRepository.deleteStatusUpdate(updateId)
                .onFailure { _statusBanner.value = extractFirestoreMessage(it, "Could not delete status") }
        }
    }

    fun replyToStatus(ownerId: String, statusCaption: String, replyText: String) {
        val peer = discoverableUsers.value.firstOrNull { it.userId == ownerId } ?: return
        val myProfile = currentUserProfile.value ?: return
        viewModelScope.launch {
            snapRepository.getOrCreateDirectChat(peer, myProfile)
                .onSuccess { chat ->
                    snapRepository.sendMessage(
                        chat = chat,
                        senderName = myProfile.displayName,
                        text = "Status (${statusCaption.take(28)}): $replyText",
                        messageType = "status_reply"
                    )
                    _statusBanner.value = "Reply sent to ${peer.displayName}"
                }
        }
    }

    fun startCall(peer: UserProfile, callType: String, onCallStarted: (String) -> Unit) {
        val myProfile = currentUserProfile.value ?: UserProfile(userId = currentUserId, displayName = "SNAP User")
        viewModelScope.launch {
            snapRepository.startCallLog(myProfile, peer, callType)
                .onSuccess { callId -> onCallStarted(callId) }
                .onFailure { _statusBanner.value = extractFirestoreMessage(it, "Could not start call") }
        }
    }

    fun finishCall(callId: String, durationSeconds: Long) {
        viewModelScope.launch {
            snapRepository.finishCallLog(callId, durationSeconds)
        }
    }

    fun updateProfile(
        username: String,
        displayName: String,
        bio: String,
        avatarUrl: String,
        privateAccount: Boolean
    ) {
        viewModelScope.launch {
            snapRepository.updateProfile(username, displayName, bio, avatarUrl, privateAccount)
                .onSuccess { _statusBanner.value = "Profile saved" }
                .onFailure { _statusBanner.value = extractFirestoreMessage(it, "Failed to update profile") }
        }
    }

    fun updateSettings(
        readReceipts: Boolean,
        lastSeenVisible: Boolean,
        autoPlayNextReel: Boolean,
        videoQuality: VideoQualityOption,
        dataSaver: Boolean
    ) {
        viewModelScope.launch {
            snapRepository.updatePrivateSettings(readReceipts, lastSeenVisible, autoPlayNextReel, videoQuality, dataSaver)
                .onSuccess { _statusBanner.value = "Preferences updated" }
                .onFailure { _statusBanner.value = extractFirestoreMessage(it, "Failed to save settings") }
        }
    }

    fun toggleBlockUser(targetUserId: String, currentlyBlocked: Boolean) {
        viewModelScope.launch {
            snapRepository.toggleBlockUser(targetUserId, currentlyBlocked)
                .onSuccess {
                    _statusBanner.value = if (currentlyBlocked) "User unblocked" else "User blocked"
                }
                .onFailure {
                    _statusBanner.value = extractFirestoreMessage(it, "Failed to update block list")
                }
        }
    }

    private fun unwrapOrThrowCancellation(error: Throwable): Throwable {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        return firestoreError ?: error
    }
}
