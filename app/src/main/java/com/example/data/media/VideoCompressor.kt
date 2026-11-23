package com.example.data.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume

data class PreparedVideoResult(
    val file: File,
    val compressedBytes: Long,
    val wasTranscoded: Boolean
)

object VideoCompressor {
    private const val TAG = "VideoCompressor"

    @OptIn(UnstableApi::class)
    suspend fun compressForReels(
        context: Context,
        inputUri: Uri,
        maxDurationSec: Int,
        highQuality: Boolean
    ): PreparedVideoResult = withContext(Dispatchers.Main) {
        val appContext = context.applicationContext
        val outDir = File(appContext.filesDir, "reels_media").apply { mkdirs() }
        val outputFile = File(outDir, "reel_${System.currentTimeMillis()}.mp4")

        val transcodeSuccess = suspendCancellableCoroutine { cont ->
            try {
                val targetHeight = if (highQuality) 1280 else 960
                val clippedMediaItem = MediaItem.Builder()
                    .setUri(inputUri)
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(0L)
                            .setEndPositionMs(maxDurationSec * 1000L)
                            .build()
                    )
                    .build()

                val videoEffects = listOf(
                    Presentation.createForHeight(targetHeight)
                )
                val editedItem = EditedMediaItem.Builder(clippedMediaItem)
                    .setEffects(Effects(emptyList(), videoEffects))
                    .build()

                val transformer = Transformer.Builder(appContext)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            if (cont.isActive) cont.resume(true)
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException
                        ) {
                            Log.w(TAG, "Media3 Transformer fallback: ${exportException.message}")
                            if (cont.isActive) cont.resume(false)
                        }
                    })
                    .build()

                transformer.start(editedItem, outputFile.absolutePath)
                cont.invokeOnCancellation {
                    try {
                        transformer.cancel()
                    } catch (_: Exception) {
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Transformer start exception, falling back to direct copy: ${e.message}")
                if (cont.isActive) cont.resume(false)
            }
        }

        if (transcodeSuccess && outputFile.exists() && outputFile.length() > 0L) {
            return@withContext PreparedVideoResult(
                file = outputFile,
                compressedBytes = outputFile.length(),
                wasTranscoded = true
            )
        }

        // Fallback: copy raw stream to app-local durable file if device lacks hardware H.264 encoder
        withContext(Dispatchers.IO) {
            if (outputFile.exists()) outputFile.delete()
            appContext.contentResolver.openInputStream(inputUri)?.use { input ->
                FileOutputStream(outputFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
        PreparedVideoResult(
            file = outputFile,
            compressedBytes = outputFile.length(),
            wasTranscoded = false
        )
    }

    suspend fun extractThumbnailFrames(
        context: Context,
        videoUri: Uri,
        count: Int = 4
    ): List<File> = withContext(Dispatchers.IO) {
        val frames = mutableListOf<File>()
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 5000L
            val thumbDir = File(context.cacheDir, "reel_thumbs").apply { mkdirs() }
            for (i in 0 until count) {
                val timeUs = ((durationMs * (i + 1)) / (count + 1)) * 1000L
                val bmp = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (bmp != null) {
                    val file = File(thumbDir, "thumb_${System.currentTimeMillis()}_$i.jpg")
                    FileOutputStream(file).use { out ->
                        bmp.compress(Bitmap.CompressFormat.JPEG, 82, out)
                    }
                    bmp.recycle()
                    frames.add(file)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Thumbnail extraction failed: ${e.message}")
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
        frames
    }
}
