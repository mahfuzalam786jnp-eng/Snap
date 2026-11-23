package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.data.model.CallLog
import com.example.data.model.ChatMessage
import com.example.data.model.ChatThread
import com.example.data.model.StatusUpdate
import com.example.data.model.UserPrivateSettings
import com.example.data.model.UserProfile
import com.example.data.model.VideoQualityOption
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class SnapRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing SNAP.")
    }

    suspend fun ensureUserProfileInitialized(): Result<UserProfile> {
        val currentUser = auth.currentUser
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val uid = currentUser.uid
        val docPath = "users/$uid"
        return try {
            val docRef = db.collection("users").document(uid)
            val snap = docRef.get().await()
            if (snap.exists()) {
                val existing = snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                if (existing != null) return Result.success(existing)
            }
            val rawName = currentUser.displayName?.takeIf { it.isNotBlank() } ?: "SNAP User"
            val sanitizedHandle = rawName.lowercase()
                .replace(Regex("[^a-z0-9_]"), "")
                .take(18)
                .ifBlank { "snapuser" } + "_" + uid.take(4).lowercase()

            val profile = UserProfile(
                userId = uid,
                username = sanitizedHandle.take(30),
                displayName = rawName.take(50),
                bio = "On SNAP & SNAP Reels",
                avatarUrl = currentUser.photoUrl?.toString()?.take(500) ?: "",
                privateAccount = false,
                discoverable = true
            )
            docRef.set(profile.toCreateMap()).await()

            val privateRef = docRef.collection("private").document("info")
            val privateSnap = privateRef.get().await()
            if (!privateSnap.exists()) {
                val privateSettings = UserPrivateSettings(
                    userId = uid,
                    email = currentUser.email ?: ""
                )
                privateRef.set(privateSettings.toCreateMap()).await()
            }
            Result.success(profile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val uid = requireUserId()
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { it.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observePrivateSettings(): Flow<UserPrivateSettings?> = flow {
        val uid = requireUserId()
        val path = "users/$uid/private/info"
        emitAll(
            db.collection("users").document(uid)
                .collection("private").document("info")
                .snapshots()
                .map { it.toObject(UserPrivateSettings::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeDiscoverableUsers(): Flow<List<UserProfile>> = flow {
        val uid = requireUserId()
        val path = "users"
        emitAll(
            db.collection("users")
                .whereEqualTo("discoverable", true)
                .snapshots()
                .map { snap ->
                    snap.toObjects(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.displayName.lowercase() }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun updateProfile(
        username: String,
        displayName: String,
        bio: String,
        avatarUrl: String,
        privateAccount: Boolean
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            val cleanUsername = username.trim().lowercase().replace(Regex("[^a-z0-9_]"), "")
            require(cleanUsername.length in 3..30) { "Username must be 3-30 letters, numbers, or underscores." }
            val cleanDisplay = displayName.trim()
            require(cleanDisplay.length in 1..50) { "Display name must be 1-50 characters." }

            db.collection("users").document(uid).update(
                mapOf(
                    "username" to cleanUsername,
                    "displayName" to cleanDisplay,
                    "bio" to bio.trim().take(200),
                    "avatarUrl" to avatarUrl.trim().take(500),
                    "privateAccount" to privateAccount,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updatePrivateSettings(
        readReceipts: Boolean,
        lastSeenVisible: Boolean,
        autoPlayNextReel: Boolean,
        videoQuality: VideoQualityOption,
        dataSaver: Boolean
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid/private/info"
        return try {
            db.collection("users").document(uid)
                .collection("private").document("info")
                .update(
                    mapOf(
                        "readReceipts" to readReceipts,
                        "lastSeenVisible" to lastSeenVisible,
                        "autoPlayNextReel" to autoPlayNextReel,
                        "videoQuality" to videoQuality.value,
                        "dataSaver" to dataSaver,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleBlockUser(targetUserId: String, currentlyBlocked: Boolean): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            require(targetUserId.isNotBlank() && targetUserId != uid) { "Invalid user to block." }
            val updateValue = if (currentlyBlocked) {
                FieldValue.arrayRemove(targetUserId)
            } else {
                FieldValue.arrayUnion(targetUserId)
            }
            db.collection("users").document(uid).update(
                mapOf(
                    "blockedUserIds" to updateValue,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- CHATS ---

    fun observeChats(): Flow<List<ChatThread>> = flow {
        val uid = requireUserId()
        val path = "chats"
        emitAll(
            db.collection("chats")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(ChatThread::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeChatMessages(chatId: String): Flow<List<ChatMessage>> = flow {
        val uid = requireUserId()
        val path = "chats/$chatId/messages"
        emitAll(
            db.collection("chats").document(chatId).collection("messages")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun getOrCreateDirectChat(peer: UserProfile, currentProfile: UserProfile): Result<ChatThread> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val sortedIds = listOf(uid, peer.userId).sorted()
        val chatId = "dm_${sortedIds[0].take(20)}_${sortedIds[1].take(20)}"
        val path = "chats/$chatId"
        return try {
            val chatRef = db.collection("chats").document(chatId)
            val snap = chatRef.get().await()
            if (snap.exists()) {
                val existing = snap.toObject(ChatThread::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                if (existing != null) return Result.success(existing)
            }
            val payload = mapOf(
                "title" to peer.displayName.take(80),
                "peerAvatarUrl" to peer.avatarUrl.take(500),
                "groupChat" to false,
                "memberUids" to sortedIds,
                "participantNames" to mapOf(
                    uid to currentProfile.displayName,
                    peer.userId to peer.displayName
                ),
                "lastMessageText" to "Started a SNAP conversation",
                "lastMessageType" to "system",
                "lastMessageSenderId" to uid,
                "unreadCount" to 0L,
                "createdBy" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            chatRef.set(payload).await()
            val createdSnap = chatRef.get().await()
            val created = createdSnap.toObject(ChatThread::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: ChatThread(
                    id = chatId,
                    title = peer.displayName,
                    peerAvatarUrl = peer.avatarUrl,
                    memberUids = sortedIds,
                    createdBy = uid
                )
            Result.success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun createSelfNoteChat(currentProfile: UserProfile): Result<ChatThread> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val chatId = "notes_${uid.take(40)}"
        val path = "chats/$chatId"
        return try {
            val chatRef = db.collection("chats").document(chatId)
            val snap = chatRef.get().await()
            if (snap.exists()) {
                val existing = snap.toObject(ChatThread::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                if (existing != null) return Result.success(existing)
            }
            val payload = mapOf(
                "title" to "Saved Notes (You)",
                "peerAvatarUrl" to currentProfile.avatarUrl.take(500),
                "groupChat" to false,
                "memberUids" to listOf(uid),
                "participantNames" to mapOf(uid to currentProfile.displayName),
                "lastMessageText" to "Pin notes, links, and favorite SNAP Reels here",
                "lastMessageType" to "system",
                "lastMessageSenderId" to uid,
                "unreadCount" to 0L,
                "createdBy" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            chatRef.set(payload).await()
            val created = chatRef.get().await().toObject(ChatThread::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: ChatThread(id = chatId, title = "Saved Notes (You)", memberUids = listOf(uid), createdBy = uid)
            Result.success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun sendMessage(
        chat: ChatThread,
        senderName: String,
        text: String,
        messageType: String = "text",
        sharedReelId: String? = null,
        sharedReelThumbnail: String? = null,
        sharedReelCaption: String? = null,
        sharedReelCreator: String? = null
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val chatRef = db.collection("chats").document(chat.id)
        val msgRef = chatRef.collection("messages").document()
        return try {
            val cleanText = text.trim().take(2000)
            require(cleanText.isNotEmpty() || !sharedReelId.isNullOrBlank()) { "Message cannot be empty." }

            val msgPayload = mutableMapOf<String, Any>(
                "chatId" to chat.id,
                "senderId" to uid,
                "senderName" to senderName.take(60).ifBlank { "SNAP User" },
                "memberUids" to chat.memberUids,
                "text" to cleanText,
                "messageType" to messageType,
                "readBy" to listOf(uid),
                "createdAt" to FieldValue.serverTimestamp()
            )
            if (!sharedReelId.isNullOrBlank()) msgPayload["sharedReelId"] = sharedReelId.take(128)
            if (!sharedReelThumbnail.isNullOrBlank()) msgPayload["sharedReelThumbnail"] = sharedReelThumbnail.take(1000)
            if (!sharedReelCaption.isNullOrBlank()) msgPayload["sharedReelCaption"] = sharedReelCaption.take(300)
            if (!sharedReelCreator.isNullOrBlank()) msgPayload["sharedReelCreator"] = sharedReelCreator.take(60)

            val previewText = when (messageType) {
                "reel_share" -> "Shared a SNAP Reel: ${sharedReelCaption?.take(40) ?: "Watch video"}"
                "status_reply" -> "Replied to status: $cleanText"
                else -> cleanText
            }.take(500)

            val batch = db.batch()
            batch.set(msgRef, msgPayload)
            batch.update(
                chatRef,
                mapOf(
                    "lastMessageText" to previewText,
                    "lastMessageType" to messageType,
                    "lastMessageSenderId" to uid,
                    "unreadCount" to 0L,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, msgRef.path)
            Result.failure(e)
        }
    }

    // --- UPDATES (STATUS) ---

    fun observePublicStatusUpdates(): Flow<List<StatusUpdate>> = flow {
        val uid = requireUserId()
        val path = "updates"
        emitAll(
            db.collection("updates")
                .whereEqualTo("visibility", "public")
                .snapshots()
                .map { snap ->
                    snap.toObjects(StatusUpdate::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun postStatusUpdate(
        profile: UserProfile,
        caption: String,
        mediaUrl: String?,
        backgroundHex: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val docRef = db.collection("updates").document()
        return try {
            val cleanCaption = caption.trim().take(300)
            require(cleanCaption.isNotEmpty() || !mediaUrl.isNullOrBlank()) { "Add text or an image for your status." }
            val payload = mutableMapOf<String, Any>(
                "userId" to uid,
                "username" to profile.username.ifBlank { profile.displayName }.take(50),
                "caption" to cleanCaption,
                "backgroundHex" to backgroundHex.take(12),
                "visibility" to "public",
                "viewerIds" to emptyList<String>(),
                "createdAt" to FieldValue.serverTimestamp()
            )
            if (profile.avatarUrl.isNotBlank()) payload["avatarUrl"] = profile.avatarUrl.take(500)
            if (!mediaUrl.isNullOrBlank()) payload["mediaUrl"] = mediaUrl.take(1000)

            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun markStatusViewed(updateId: String): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val docRef = db.collection("updates").document(updateId)
        return try {
            docRef.update("viewerIds", FieldValue.arrayUnion(uid)).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteStatusUpdate(updateId: String): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val docRef = db.collection("updates").document(updateId)
        return try {
            require(uid.isNotBlank())
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    // --- CALLS ---

    fun observeCallLogs(): Flow<List<CallLog>> = flow {
        val uid = requireUserId()
        val path = "calls"
        emitAll(
            db.collection("calls")
                .whereArrayContains("participantUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(CallLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun startCallLog(
        caller: UserProfile,
        callee: UserProfile,
        callType: String
    ): Result<String> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val docRef = db.collection("calls").document()
        return try {
            val payload = mapOf(
                "callerId" to uid,
                "callerName" to caller.displayName.take(60).ifBlank { "SNAP User" },
                "calleeId" to callee.userId,
                "calleeName" to callee.displayName.take(60).ifBlank { "SNAP User" },
                "participantUids" to listOf(uid, callee.userId),
                "callType" to if (callType == "video") "video" else "voice",
                "status" to "connected",
                "durationSeconds" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun finishCallLog(callId: String, durationSeconds: Long): Result<Unit> {
        val docRef = db.collection("calls").document(callId)
        return try {
            requireUserId()
            docRef.update(
                mapOf(
                    "status" to "completed",
                    "durationSeconds" to durationSeconds.coerceIn(0L, 86400L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }
}
