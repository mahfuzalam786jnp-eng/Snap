package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.ReelsRepository
import com.example.data.firebase.SnapRepository
import com.example.data.firebase.extractFirestoreMessage
import com.example.data.media.VideoCompressor
import com.example.data.model.ChatThread
import com.example.data.model.LicensedTrack
import com.example.data.model.Reel
import com.example.data.model.ReelComment
import com.example.data.model.ReelFeedMode
import com.example.data.model.ReelVisibility
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.model.VideoQualityOption
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
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
import java.io.File

class ReelsViewModel(
    private val reelsRepository: ReelsRepository,
    private val snapRepository: SnapRepository,
    val currentUserId: String
) : ViewModel() {

    private val _feedMode = MutableStateFlow(ReelFeedMode.FOR_YOU)
    val feedMode: StateFlow<ReelFeedMode> = _feedMode.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _volume = MutableStateFlow(1f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private val _qualityOption = MutableStateFlow(VideoQualityOption.AUTO)
    val qualityOption: StateFlow<VideoQualityOption> = _qualityOption.asStateFlow()

    private val _autoPlayNext = MutableStateFlow(true)
    val autoPlayNext: StateFlow<Boolean> = _autoPlayNext.asStateFlow()

    private val _reportedReelIds = MutableStateFlow<Set<String>>(emptySet())
    private val viewedThisSession = mutableSetOf<String>()

    private val _paginatedReels = MutableStateFlow<List<Reel>>(emptyList())
    private var lastPageCursor: DocumentSnapshot? = null
    private var hasMorePages = true
    private var isFetchingPage = false

    private val _uploadProgressMessage = MutableStateFlow<String?>(null)
    val uploadProgressMessage: StateFlow<String?> = _uploadProgressMessage.asStateFlow()

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _activeCommentsReelId = MutableStateFlow<String?>(null)

    private val _focusedCreatorId = MutableStateFlow<String?>(null)

    private val currentProfileFlow = snapRepository.observeUserProfile(currentUserId)
        .catch { unwrapOrThrowCancellation(it) }

    val likedReelIds: StateFlow<Set<String>> = reelsRepository.observeLikedReelIds()
        .catch { unwrapOrThrowCancellation(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptySet())

    val savedReelIds: StateFlow<Set<String>> = reelsRepository.observeSavedReelIds()
        .catch { unwrapOrThrowCancellation(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptySet())

    val followedCreatorIds: StateFlow<Set<String>> = reelsRepository.observeFollowedCreatorIds()
        .catch { unwrapOrThrowCancellation(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptySet())

    val followerCountsByCreator: StateFlow<Map<String, Long>> = reelsRepository.observeFollowerCountsByCreator()
        .catch { unwrapOrThrowCancellation(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyMap())

    private val livePublicReelsFlow = reelsRepository.observePublicReels()
        .catch { error ->
            unwrapOrThrowCancellation(error)
            throw error
        }

    val reelsFeedState: StateFlow<UiState<List<Reel>>> = combine(
        livePublicReelsFlow,
        _paginatedReels,
        _feedMode,
        followedCreatorIds,
        likedReelIds,
        currentProfileFlow,
        _reportedReelIds
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val liveReels = values[0] as List<Reel>
        @Suppress("UNCHECKED_CAST")
        val pagedReels = values[1] as List<Reel>
        val mode = values[2] as ReelFeedMode
        @Suppress("UNCHECKED_CAST")
        val following = values[3] as Set<String>
        @Suppress("UNCHECKED_CAST")
        val liked = values[4] as Set<String>
        val profile = values[5] as UserProfile?
        @Suppress("UNCHECKED_CAST")
        val reported = values[6] as Set<String>

        val blocked = profile?.blockedUserIds?.toSet() ?: emptySet()
        val merged = (liveReels + pagedReels)
            .distinctBy { it.id }
            .filter { reel ->
                val allowedVisibility = reel.visibility == ReelVisibility.PUBLIC.value ||
                    reel.creatorId == currentUserId ||
                    (reel.visibility == ReelVisibility.FOLLOWERS.value && reel.creatorId in following)
                reel.id !in reported && reel.creatorId !in blocked && allowedVisibility
            }

        val likedHashtags = merged.filter { it.id in liked }.flatMap { it.hashtags }.toSet()

        val sorted = when (mode) {
            ReelFeedMode.FOLLOWING -> merged
                .filter { it.creatorId in following || it.creatorId == currentUserId }
                .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            ReelFeedMode.TRENDING -> merged.sortedByDescending {
                (it.likesCount * 3L) + (it.commentsCount * 4L) + (it.sharesCount * 5L) + it.viewsCount
            }
            ReelFeedMode.FOR_YOU -> merged.sortedByDescending { reel ->
                val followBoost = if (reel.creatorId in following) 40L else 0L
                val tagBoost = reel.hashtags.count { it in likedHashtags } * 15L
                val engagement = (reel.likesCount * 2L) + reel.commentsCount * 3L + reel.viewsCount
                val recency = (reel.createdAt?.seconds ?: 0L) / 3600L
                followBoost + tagBoost + engagement + recency
            }
        }
        UiState.Success(sorted) as UiState<List<Reel>>
    }
        .catch { error ->
            val firestoreError = unwrapOrThrowCancellation(error)
            emit(UiState.Error(extractFirestoreMessage(firestoreError, "Failed to load SNAP Reels feed.")))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    @OptIn(ExperimentalCoroutinesApi::class)
    val commentsState: StateFlow<UiState<List<ReelComment>>> = _activeCommentsReelId
        .flatMapLatest { reelId ->
            if (reelId.isNullOrBlank()) {
                flowOf<UiState<List<ReelComment>>>(UiState.Success(emptyList()))
            } else {
                reelsRepository.observeComments(reelId)
                    .map<List<ReelComment>, UiState<List<ReelComment>>> { UiState.Success(it) }
                    .catch { error ->
                        val firestoreError = unwrapOrThrowCancellation(error)
                        emit(UiState.Error(extractFirestoreMessage(firestoreError, "Failed to load comments.")))
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    @OptIn(ExperimentalCoroutinesApi::class)
    val creatorReelsState: StateFlow<UiState<List<Reel>>> = _focusedCreatorId
        .flatMapLatest { creatorId ->
            if (creatorId.isNullOrBlank()) {
                flowOf<UiState<List<Reel>>>(UiState.Success(emptyList()))
            } else {
                reelsRepository.observeCreatorReels(creatorId)
                    .map<List<Reel>, UiState<List<Reel>>> { UiState.Success(it) }
                    .catch { error ->
                        val firestoreError = unwrapOrThrowCancellation(error)
                        emit(UiState.Error(extractFirestoreMessage(firestoreError, "Failed to load creator videos.")))
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    init {
        loadNextPage()
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun setFeedMode(mode: ReelFeedMode) {
        _feedMode.value = mode
    }

    fun toggleMute() {
        _isMuted.value = !_isMuted.value
    }

    fun setVolume(newVolume: Float) {
        _volume.value = newVolume.coerceIn(0f, 1f)
        if (_volume.value > 0f && _isMuted.value) {
            _isMuted.value = false
        } else if (_volume.value == 0f) {
            _isMuted.value = true
        }
    }

    fun setQualityOption(option: VideoQualityOption) {
        _qualityOption.value = option
    }

    fun setAutoPlayNext(enabled: Boolean) {
        _autoPlayNext.value = enabled
    }

    fun loadNextPage() {
        if (isFetchingPage || !hasMorePages) return
        isFetchingPage = true
        viewModelScope.launch {
            reelsRepository.fetchReelsPage(lastPageCursor, pageSize = 8L)
                .onSuccess { page ->
                    lastPageCursor = page.lastSnapshot
                    hasMorePages = page.hasMore
                    if (page.reels.isNotEmpty()) {
                        _paginatedReels.value = (_paginatedReels.value + page.reels).distinctBy { it.id }
                    }
                }
            isFetchingPage = false
        }
    }

    fun refreshFeed() {
        lastPageCursor = null
        hasMorePages = true
        _paginatedReels.value = emptyList()
        loadNextPage()
    }

    fun recordReelView(reelId: String) {
        if (reelId.isBlank() || !viewedThisSession.add(reelId)) return
        viewModelScope.launch {
            reelsRepository.incrementView(reelId)
        }
    }

    fun toggleLike(reelId: String, currentlyLiked: Boolean) {
        viewModelScope.launch {
            reelsRepository.toggleLike(reelId, currentlyLiked)
                .onFailure { _actionMessage.value = extractFirestoreMessage(it, "Could not update like") }
        }
    }

    fun ensureLiked(reelId: String) {
        if (reelId !in likedReelIds.value) {
            toggleLike(reelId, currentlyLiked = false)
        }
    }

    fun toggleSave(reelId: String, currentlySaved: Boolean) {
        viewModelScope.launch {
            reelsRepository.toggleSave(reelId, currentlySaved)
                .onSuccess {
                    _actionMessage.value = if (currentlySaved) "Removed from Saved" else "Saved to Favorites"
                }
                .onFailure { _actionMessage.value = extractFirestoreMessage(it, "Could not save Reel") }
        }
    }

    fun toggleFollow(creatorId: String) {
        val currentlyFollowing = creatorId in followedCreatorIds.value
        viewModelScope.launch {
            reelsRepository.toggleFollow(creatorId, currentlyFollowing)
                .onFailure { _actionMessage.value = extractFirestoreMessage(it, "Could not update follow") }
        }
    }

    fun openComments(reelId: String?) {
        _activeCommentsReelId.value = reelId
    }

    fun postComment(reelId: String, author: UserProfile, text: String, parentCommentId: String? = null) {
        viewModelScope.launch {
            reelsRepository.addComment(reelId, author, text, parentCommentId)
                .onFailure { _actionMessage.value = extractFirestoreMessage(it, "Failed to post comment") }
        }
    }

    fun shareReelToChat(reel: Reel, chat: ChatThread, senderName: String, onSent: () -> Unit) {
        viewModelScope.launch {
            snapRepository.sendMessage(
                chat = chat,
                senderName = senderName,
                text = "Check out @${reel.creatorUsername}'s SNAP Reel!",
                messageType = "reel_share",
                sharedReelId = reel.id,
                sharedReelThumbnail = reel.thumbnailUrl,
                sharedReelCaption = reel.caption,
                sharedReelCreator = reel.creatorUsername
            ).onSuccess {
                reelsRepository.incrementShare(reel.id)
                _actionMessage.value = "Sent Reel to ${chat.title}"
                onSent()
            }.onFailure {
                _actionMessage.value = extractFirestoreMessage(it, "Failed to share Reel")
            }
        }
    }

    fun incrementExternalShare(reelId: String) {
        viewModelScope.launch {
            reelsRepository.incrementShare(reelId)
        }
    }

    fun reportReel(reelId: String, reason: String, details: String) {
        viewModelScope.launch {
            reelsRepository.reportReel(reelId, reason, details)
                .onSuccess {
                    _reportedReelIds.value = _reportedReelIds.value + reelId
                    _actionMessage.value = "Report submitted. Reel hidden from your feed."
                }
                .onFailure {
                    _actionMessage.value = extractFirestoreMessage(it, "Could not submit report")
                }
        }
    }

    fun deleteReel(reel: Reel, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            reelsRepository.deleteReel(reel)
                .onSuccess {
                    _paginatedReels.value = _paginatedReels.value.filterNot { it.id == reel.id }
                    _actionMessage.value = "Reel deleted"
                    onDeleted()
                }
                .onFailure {
                    _actionMessage.value = extractFirestoreMessage(it, "Failed to delete Reel")
                }
        }
    }

    fun focusCreator(creatorId: String?) {
        _focusedCreatorId.value = creatorId
    }

    fun uploadAndPublishReel(
        context: Context,
        creator: UserProfile,
        videoUri: Uri,
        customThumbnailFile: File?,
        caption: String,
        hashtags: List<String>,
        track: LicensedTrack,
        durationSeconds: Int,
        visibility: ReelVisibility,
        copyrightConfirmed: Boolean,
        highQuality: Boolean,
        onPublished: () -> Unit
    ) {
        if (_isUploading.value) return
        _isUploading.value = true
        _uploadProgressMessage.value = "Compressing and optimizing video..."
        viewModelScope.launch {
            try {
                val prepared = VideoCompressor.compressForReels(
                    context = context,
                    inputUri = videoUri,
                    maxDurationSec = durationSeconds,
                    highQuality = highQuality
                )
                val thumb = customThumbnailFile ?: VideoCompressor.extractThumbnailFrames(
                    context = context,
                    videoUri = Uri.fromFile(prepared.file),
                    count = 1
                ).firstOrNull()

                _uploadProgressMessage.value = if (prepared.wasTranscoded) {
                    "Compressed (${prepared.compressedBytes / 1024} KB). Publishing to SNAP..."
                } else {
                    "Publishing SNAP Reel..."
                }

                reelsRepository.publishReel(
                    creator = creator,
                    localVideoFile = prepared.file,
                    localThumbFile = thumb,
                    caption = caption,
                    hashtags = hashtags,
                    track = track,
                    durationSeconds = durationSeconds,
                    visibility = visibility,
                    copyrightConfirmed = copyrightConfirmed
                ).onSuccess { res ->
                    _actionMessage.value = if (res.storedInCloudBucket) {
                        "SNAP Reel published to cloud!"
                    } else {
                        "SNAP Reel published! (Enable Firebase Storage rules in Console for multi-device video sync)"
                    }
                    onPublished()
                }.onFailure { err ->
                    _actionMessage.value = extractFirestoreMessage(err, "Failed to publish SNAP Reel")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionMessage.value = e.localizedMessage ?: "Video preparation failed"
            } finally {
                _isUploading.value = false
                _uploadProgressMessage.value = null
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
