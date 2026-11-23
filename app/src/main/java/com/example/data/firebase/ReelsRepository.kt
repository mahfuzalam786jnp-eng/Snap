package com.example.data.firebase

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.R
import com.example.data.model.LicensedTrack
import com.example.data.model.Reel
import com.example.data.model.ReelComment
import com.example.data.model.ReelVisibility
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.io.File

data class ReelPageResult(
    val reels: List<Reel>,
    val lastSnapshot: DocumentSnapshot?,
    val hasMore: Boolean
)

data class PublishReelResult(
    val reelId: String,
    val storedInCloudBucket: Boolean
)

class ReelsRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    companion object {
        private const val TAG = "ReelsRepository"
        private val MODERATION_BLOCKLIST = listOf("malware", "phishing", "exploit", "illegal_spam")
    }

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing SNAP Reels.")
    }

    fun observePublicReels(): Flow<List<Reel>> = flow {
        val uid = requireUserId()
        val path = "reels"
        emitAll(
            db.collection("reels")
                .whereEqualTo("moderationStatus", "approved")
                .whereIn("visibility", listOf("public", "followers"))
                .snapshots()
                .map { snap ->
                    snap.toObjects(Reel::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
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

    suspend fun fetchReelsPage(
        lastSnapshot: DocumentSnapshot?,
        pageSize: Long = 8L
    ): Result<ReelPageResult> {
        return try {
            requireUserId()
            var query = db.collection("reels")
                .whereEqualTo("moderationStatus", "approved")
                .whereIn("visibility", listOf("public", "followers"))
                .limit(pageSize)
            if (lastSnapshot != null) {
                query = query.startAfter(lastSnapshot)
            }
            val snap = query.get().await()
            val items = snap.toObjects(Reel::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            Result.success(
                ReelPageResult(
                    reels = items,
                    lastSnapshot = snap.documents.lastOrNull(),
                    hasMore = snap.size() >= pageSize
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "reels")
            Result.failure(e)
        }
    }

    fun observeCreatorReels(creatorId: String): Flow<List<Reel>> = flow {
        val uid = requireUserId()
        val path = "reels"
        val query = if (uid == creatorId) {
            db.collection("reels").whereEqualTo("creatorId", creatorId)
        } else {
            db.collection("reels")
                .whereEqualTo("creatorId", creatorId)
                .whereEqualTo("moderationStatus", "approved")
                .whereIn("visibility", listOf("public", "followers"))
        }
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(Reel::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
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

    fun observeLikedReelIds(): Flow<Set<String>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection("reel_likes")
                .whereEqualTo("userId", uid)
                .snapshots()
                .map { snap -> snap.documents.mapNotNull { it.getString("reelId") }.toSet() }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, "reel_likes")
                    }
                    throw error
                }
        )
    }

    fun observeSavedReelIds(): Flow<Set<String>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection("reel_saves")
                .whereEqualTo("userId", uid)
                .snapshots()
                .map { snap -> snap.documents.mapNotNull { it.getString("reelId") }.toSet() }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, "reel_saves")
                    }
                    throw error
                }
        )
    }

    fun observeFollowedCreatorIds(): Flow<Set<String>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection("follows")
                .whereEqualTo("active", true)
                .snapshots()
                .map { snap ->
                    snap.documents
                        .filter { it.getString("followerId") == uid }
                        .mapNotNull { it.getString("creatorId") }
                        .toSet()
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, "follows")
                    }
                    throw error
                }
        )
    }

    fun observeFollowerCountsByCreator(): Flow<Map<String, Long>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection("follows")
                .whereEqualTo("active", true)
                .snapshots()
                .map { snap ->
                    val counts = mutableMapOf<String, Long>()
                    snap.documents.forEach { doc ->
                        val creatorId = doc.getString("creatorId")
                        if (!creatorId.isNullOrBlank()) {
                            counts[creatorId] = (counts[creatorId] ?: 0L) + 1L
                        }
                    }
                    counts
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, "follows")
                    }
                    throw error
                }
        )
    }

    fun observeComments(reelId: String): Flow<List<ReelComment>> = flow {
        val uid = requireUserId()
        val path = "reels/$reelId/comments"
        emitAll(
            db.collection("reels").document(reelId).collection("comments")
                .whereEqualTo("reelId", reelId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(ReelComment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
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

    suspend fun publishReel(
        creator: UserProfile,
        localVideoFile: File,
        localThumbFile: File?,
        caption: String,
        hashtags: List<String>,
        track: LicensedTrack,
        durationSeconds: Int,
        visibility: ReelVisibility,
        copyrightConfirmed: Boolean
    ): Result<PublishReelResult> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val reelRef = db.collection("reels").document()
        val reelId = reelRef.id
        return try {
            require(copyrightConfirmed) { "Please confirm copyright and licensed audio compliance." }
            val cleanCaption = caption.trim().take(500)
            val lowerCaption = cleanCaption.lowercase()
            if (MODERATION_BLOCKLIST.any { lowerCaption.contains(it) }) {
                return Result.failure(IllegalArgumentException("Caption triggered SNAP automated content moderation."))
            }
            val cleanTags = hashtags
                .map { it.trim().removePrefix("#").lowercase() }
                .filter { it.isNotBlank() }
                .distinct()
                .take(15)

            var finalVideoUrl = Uri.fromFile(localVideoFile).toString()
            var finalThumbUrl = localThumbFile?.let { Uri.fromFile(it).toString() } ?: ""
            var storedInCloud = false

            val bucket = try {
                FirebaseApp.getInstance().options.storageBucket
            } catch (_: Exception) {
                null
            }
            if (!bucket.isNullOrBlank()) {
                try {
                    val storageRef = FirebaseStorage.getInstance().reference
                    val videoStorageRef = storageRef.child("reels/$uid/$reelId.mp4")
                    videoStorageRef.putFile(Uri.fromFile(localVideoFile)).await()
                    finalVideoUrl = videoStorageRef.downloadUrl.await().toString()
                    storedInCloud = true

                    if (localThumbFile != null && localThumbFile.exists()) {
                        val thumbRef = storageRef.child("reels/$uid/${reelId}_thumb.jpg")
                        thumbRef.putFile(Uri.fromFile(localThumbFile)).await()
                        finalThumbUrl = thumbRef.downloadUrl.await().toString()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Firebase Storage unavailable or unconfigured; using local durable URI: ${e.message}")
                }
            }

            val payload = mutableMapOf<String, Any>(
                "creatorId" to uid,
                "creatorUsername" to creator.username.ifBlank { creator.displayName }.take(40),
                "videoUrl" to finalVideoUrl.take(1000),
                "caption" to cleanCaption,
                "hashtags" to cleanTags,
                "musicTitle" to track.title.take(100),
                "musicArtist" to track.artist.take(100),
                "musicLicenseId" to track.id.take(64),
                "durationSeconds" to durationSeconds.coerceIn(1, 60),
                "visibility" to visibility.value,
                "moderationStatus" to "approved",
                "copyrightVerified" to true,
                "likesCount" to 0L,
                "commentsCount" to 0L,
                "sharesCount" to 0L,
                "savesCount" to 0L,
                "viewsCount" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (creator.avatarUrl.isNotBlank()) payload["creatorAvatarUrl"] = creator.avatarUrl.take(500)
            if (finalThumbUrl.isNotBlank()) payload["thumbnailUrl"] = finalThumbUrl.take(1000)

            val batch = db.batch()
            batch.set(reelRef, payload)
            batch.update(
                db.collection("users").document(uid),
                mapOf(
                    "reelsCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()

            Result.success(PublishReelResult(reelId = reelId, storedInCloudBucket = storedInCloud))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, reelRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteReel(reel: Reel): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        if (reel.creatorId != uid) {
            return Result.failure(IllegalStateException("Only the creator can delete this Reel."))
        }
        val reelRef = db.collection("reels").document(reel.id)
        return try {
            val batch = db.batch()
            batch.delete(reelRef)
            batch.update(
                db.collection("users").document(uid),
                mapOf(
                    "reelsCount" to FieldValue.increment(-1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, reelRef.path)
            Result.failure(e)
        }
    }

    suspend fun toggleLike(reelId: String, currentlyLiked: Boolean): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val likeId = "${reelId}_$uid"
        val likeRef = db.collection("reel_likes").document(likeId)
        val reelRef = db.collection("reels").document(reelId)
        return try {
            val batch = db.batch()
            if (currentlyLiked) {
                batch.delete(likeRef)
                batch.update(
                    reelRef,
                    mapOf(
                        "likesCount" to FieldValue.increment(-1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                batch.set(
                    likeRef,
                    mapOf(
                        "reelId" to reelId,
                        "userId" to uid,
                        "active" to true,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.update(
                    reelRef,
                    mapOf(
                        "likesCount" to FieldValue.increment(1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, likeRef.path)
            Result.failure(e)
        }
    }

    suspend fun toggleSave(reelId: String, currentlySaved: Boolean): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val saveId = "${reelId}_$uid"
        val saveRef = db.collection("reel_saves").document(saveId)
        val reelRef = db.collection("reels").document(reelId)
        return try {
            val batch = db.batch()
            if (currentlySaved) {
                batch.delete(saveRef)
                batch.update(
                    reelRef,
                    mapOf(
                        "savesCount" to FieldValue.increment(-1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                batch.set(
                    saveRef,
                    mapOf(
                        "reelId" to reelId,
                        "userId" to uid,
                        "active" to true,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.update(
                    reelRef,
                    mapOf(
                        "savesCount" to FieldValue.increment(1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, saveRef.path)
            Result.failure(e)
        }
    }

    suspend fun toggleFollow(creatorId: String, currentlyFollowing: Boolean): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        if (uid == creatorId) return Result.failure(IllegalArgumentException("You cannot follow yourself."))
        val followId = "${uid}_$creatorId"
        val followRef = db.collection("follows").document(followId)
        return try {
            if (currentlyFollowing) {
                followRef.delete().await()
            } else {
                followRef.set(
                    mapOf(
                        "followerId" to uid,
                        "creatorId" to creatorId,
                        "active" to true,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, followRef.path)
            Result.failure(e)
        }
    }

    suspend fun addComment(
        reelId: String,
        author: UserProfile,
        text: String,
        parentCommentId: String? = null
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val reelRef = db.collection("reels").document(reelId)
        val commentRef = reelRef.collection("comments").document()
        return try {
            val cleanText = text.trim().take(500)
            require(cleanText.isNotEmpty()) { "Comment cannot be empty." }
            val payload = mutableMapOf<String, Any>(
                "reelId" to reelId,
                "authorId" to uid,
                "authorUsername" to author.username.ifBlank { author.displayName }.take(40),
                "text" to cleanText,
                "likesCount" to 0L,
                "createdAt" to FieldValue.serverTimestamp()
            )
            if (author.avatarUrl.isNotBlank()) payload["authorAvatarUrl"] = author.avatarUrl.take(500)
            if (!parentCommentId.isNullOrBlank()) payload["parentCommentId"] = parentCommentId.take(128)

            val batch = db.batch()
            batch.set(commentRef, payload)
            batch.update(
                reelRef,
                mapOf(
                    "commentsCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, commentRef.path)
            Result.failure(e)
        }
    }

    suspend fun incrementView(reelId: String): Result<Unit> {
        val reelRef = db.collection("reels").document(reelId)
        return try {
            requireUserId()
            reelRef.update(
                mapOf(
                    "viewsCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, reelRef.path)
            Result.failure(e)
        }
    }

    suspend fun incrementShare(reelId: String): Result<Unit> {
        val reelRef = db.collection("reels").document(reelId)
        return try {
            requireUserId()
            reelRef.update(
                mapOf(
                    "sharesCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, reelRef.path)
            Result.failure(e)
        }
    }

    suspend fun reportReel(reelId: String, reason: String, details: String): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val reportRef = db.collection("reel_reports").document()
        return try {
            val cleanReason = reason.trim().take(120)
            require(cleanReason.length >= 3) { "Please select a report reason." }
            val payload = mutableMapOf<String, Any>(
                "reelId" to reelId,
                "reporterId" to uid,
                "reason" to cleanReason,
                "status" to "submitted",
                "createdAt" to FieldValue.serverTimestamp()
            )
            if (details.isNotBlank()) payload["details"] = details.trim().take(500)
            reportRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, reportRef.path)
            Result.failure(e)
        }
    }
}
