package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class ReelFeedMode(val label: String) {
    FOR_YOU("For You"),
    FOLLOWING("Following"),
    TRENDING("Trending")
}

enum class VideoQualityOption(val value: String, val label: String, val maxBitrate: Int, val maxHeight: Int) {
    AUTO("auto", "Auto (Adaptive)", Int.MAX_VALUE, Int.MAX_VALUE),
    HD_1080("1080p", "Full HD 1080p", 5_000_000, 1080),
    HD_720("720p", "HD 720p", 2_500_000, 720),
    DATA_SAVER_480("480p", "Data Saver 480p", 1_000_000, 480);

    companion object {
        fun fromValue(raw: String): VideoQualityOption =
            entries.firstOrNull { it.value == raw } ?: AUTO
    }
}

enum class ReelVisibility(val value: String, val label: String, val description: String) {
    PUBLIC("public", "Public", "Anyone on SNAP can watch and discover"),
    FOLLOWERS("followers", "Followers Only", "Visible only to people who follow you"),
    PRIVATE("private", "Private", "Only visible to you in your profile");

    companion object {
        fun fromValue(raw: String): ReelVisibility =
            entries.firstOrNull { it.value == raw } ?: PUBLIC
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val username: String = "",
    val displayName: String = "",
    val bio: String = "",
    val avatarUrl: String = "",
    val privateAccount: Boolean = false,
    val discoverable: Boolean = true,
    val followersCount: Long = 0L,
    val followingCount: Long = 0L,
    val reelsCount: Long = 0L,
    val blockedUserIds: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "username" to username,
        "displayName" to displayName,
        "bio" to bio,
        "avatarUrl" to avatarUrl,
        "privateAccount" to privateAccount,
        "discoverable" to discoverable,
        "followersCount" to followersCount,
        "followingCount" to followingCount,
        "reelsCount" to reelsCount,
        "blockedUserIds" to blockedUserIds,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class UserPrivateSettings(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    val readReceipts: Boolean = true,
    val lastSeenVisible: Boolean = true,
    val autoPlayNextReel: Boolean = true,
    val videoQuality: String = VideoQualityOption.AUTO.value,
    val dataSaver: Boolean = false,
    val fcmToken: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "email" to email,
        "readReceipts" to readReceipts,
        "lastSeenVisible" to lastSeenVisible,
        "autoPlayNextReel" to autoPlayNextReel,
        "videoQuality" to videoQuality,
        "dataSaver" to dataSaver,
        "fcmToken" to fcmToken,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class ChatThread(
    @DocumentId val id: String = "",
    val title: String = "",
    val peerAvatarUrl: String = "",
    val groupChat: Boolean = false,
    val memberUids: List<String> = emptyList(),
    val participantNames: Map<String, String> = emptyMap(),
    val lastMessageText: String = "",
    val lastMessageType: String = "text",
    val lastMessageSenderId: String = "",
    val unreadCount: Long = 0L,
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val memberUids: List<String> = emptyList(),
    val text: String = "",
    val messageType: String = "text",
    val sharedReelId: String = "",
    val sharedReelThumbnail: String = "",
    val sharedReelCaption: String = "",
    val sharedReelCreator: String = "",
    val readBy: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class StatusUpdate(
    @DocumentId val id: String = "",
    val userId: String = "",
    val username: String = "",
    val avatarUrl: String = "",
    val caption: String = "",
    val mediaUrl: String = "",
    val backgroundHex: String = "#063B2C",
    val visibility: String = "public",
    val viewerIds: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CallLog(
    @DocumentId val id: String = "",
    val callerId: String = "",
    val callerName: String = "",
    val calleeId: String = "",
    val calleeName: String = "",
    val participantUids: List<String> = emptyList(),
    val callType: String = "voice",
    val status: String = "ringing",
    val durationSeconds: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Reel(
    @DocumentId val id: String = "",
    val creatorId: String = "",
    val creatorUsername: String = "",
    val creatorAvatarUrl: String = "",
    val videoUrl: String = "",
    val thumbnailUrl: String = "",
    val caption: String = "",
    val hashtags: List<String> = emptyList(),
    val musicTitle: String = "Original Audio",
    val musicArtist: String = "SNAP Creator",
    val musicLicenseId: String = "SNAP-ORIG-001",
    val durationSeconds: Long = 15L,
    val visibility: String = "public",
    val moderationStatus: String = "approved",
    val copyrightVerified: Boolean = true,
    val likesCount: Long = 0L,
    val commentsCount: Long = 0L,
    val sharesCount: Long = 0L,
    val savesCount: Long = 0L,
    val viewsCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ReelComment(
    @DocumentId val id: String = "",
    val reelId: String = "",
    val authorId: String = "",
    val authorUsername: String = "",
    val authorAvatarUrl: String = "",
    val text: String = "",
    val parentCommentId: String = "",
    val likesCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class LicensedTrack(
    val id: String,
    val title: String,
    val artist: String,
    val licenseBadge: String,
    val bpm: Int,
    val durationSec: Int,
    val genre: String,
    val previewToneHz: Int
)

object LicensedMusicCatalog {
    val tracks: List<LicensedTrack> = listOf(
        LicensedTrack(
            id = "CC0-SNAP-001",
            title = "Emerald Pulse",
            artist = "Kairo Beats",
            licenseBadge = "CC0 Licensed",
            bpm = 118,
            durationSec = 30,
            genre = "Electronic",
            previewToneHz = 440
        ),
        LicensedTrack(
            id = "CC-BY-SNAP-002",
            title = "Midnight Skyline",
            artist = "Nova LoFi",
            licenseBadge = "CC-BY 4.0",
            bpm = 92,
            durationSec = 60,
            genre = "Lo-Fi Chill",
            previewToneHz = 392
        ),
        LicensedTrack(
            id = "CC0-SNAP-003",
            title = "Solar Drift",
            artist = "Veloce Studio",
            licenseBadge = "CC0 Licensed",
            bpm = 124,
            durationSec = 15,
            genre = "Synthwave",
            previewToneHz = 523
        ),
        LicensedTrack(
            id = "CC-BY-SNAP-004",
            title = "Velvet Acoustic",
            artist = "Mira Sessions",
            licenseBadge = "CC-BY 4.0",
            bpm = 84,
            durationSec = 60,
            genre = "Acoustic",
            previewToneHz = 349
        ),
        LicensedTrack(
            id = "CC0-SNAP-005",
            title = "Kinetic Rush",
            artist = "Apex Percussion",
            licenseBadge = "Royalty-Free",
            bpm = 132,
            durationSec = 30,
            genre = "Action / Sports",
            previewToneHz = 587
        ),
        LicensedTrack(
            id = "SNAP-ORIG-000",
            title = "Original Camera Audio",
            artist = "Creator Mic",
            licenseBadge = "Original Rights",
            bpm = 0,
            durationSec = 60,
            genre = "Original",
            previewToneHz = 0
        )
    )
}
