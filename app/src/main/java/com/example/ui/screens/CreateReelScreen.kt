package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.data.media.VideoCompressor
import com.example.data.model.LicensedMusicCatalog
import com.example.data.model.LicensedTrack
import com.example.data.model.ReelVisibility
import com.example.data.model.UserProfile
import com.example.data.model.VideoQualityOption
import com.example.ui.theme.SnapCoral
import com.example.ui.theme.SnapEmerald
import com.example.ui.theme.SnapObsidian
import com.example.ui.viewmodel.ReelsViewModel
import com.example.ui.viewmodel.SnapViewModel
import kotlinx.coroutines.delay
import java.io.File

@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateReelScreen(
    reelsViewModel: ReelsViewModel,
    snapViewModel: SnapViewModel,
    onBack: () -> Unit,
    onPublished: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentProfile by snapViewModel.currentUserProfile.collectAsStateWithLifecycle()
    val privateSettings by snapViewModel.privateSettings.collectAsStateWithLifecycle()
    val isUploading by reelsViewModel.isUploading.collectAsStateWithLifecycle()
    val uploadProgressMessage by reelsViewModel.uploadProgressMessage.collectAsStateWithLifecycle()

    var selectedDurationSec by remember { mutableIntStateOf(15) }
    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var extractedThumbs by remember { mutableStateOf<List<File>>(emptyList()) }
    var selectedThumbIndex by remember { mutableIntStateOf(0) }
    var customThumbUri by remember { mutableStateOf<Uri?>(null) }

    var caption by remember { mutableStateOf("") }
    var hashtagsInput by remember { mutableStateOf("snap reels") }
    var selectedTrack by remember { mutableStateOf(LicensedMusicCatalog.tracks.first()) }
    var selectedVisibility by remember { mutableStateOf(ReelVisibility.PUBLIC) }
    var copyrightConfirmed by remember { mutableStateOf(true) }
    var showMusicSheet by remember { mutableStateOf(false) }

    // CameraX state
    var useFrontCamera by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var recordElapsedSec by remember { mutableIntStateOf(0) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var videoCaptureUseCase by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }

    var hasCameraAndAudioPerms by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasCameraAndAudioPerms = (results[Manifest.permission.CAMERA] == true) &&
            (results[Manifest.permission.RECORD_AUDIO] == true)
    }

    val galleryVideoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedVideoUri = uri
        }
    }

    val customThumbPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            customThumbUri = uri
        }
    }

    LaunchedEffect(selectedVideoUri) {
        val uri = selectedVideoUri
        if (uri != null) {
            extractedThumbs = VideoCompressor.extractThumbnailFrames(context, uri, count = 4)
            selectedThumbIndex = 0
        } else {
            extractedThumbs = emptyList()
        }
    }

    // Recording auto-stop timer
    LaunchedEffect(isRecording, selectedDurationSec) {
        if (isRecording) {
            recordElapsedSec = 0
            while (isRecording && recordElapsedSec < selectedDurationSec) {
                delay(1000L)
                recordElapsedSec += 1
            }
            if (isRecording) {
                activeRecording?.stop()
                isRecording = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("create_reel_back")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Text(
                        if (selectedVideoUri == null) "Record or Upload Reel" else "Preview & Publish",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                actions = {
                    if (selectedVideoUri != null) {
                        OutlinedButton(
                            onClick = {
                                selectedVideoUri = null
                                customThumbUri = null
                            },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text("Retake")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (selectedVideoUri == null) {
            // CAPTURE OR PICK STEP
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 15s / 30s / 60s Duration Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                ) {
                    listOf(15, 30, 60).forEach { sec ->
                        FilterChip(
                            selected = selectedDurationSec == sec,
                            onClick = { if (!isRecording) selectedDurationSec = sec },
                            label = { Text("${sec}s") },
                            modifier = Modifier.testTag("duration_${sec}s_chip")
                        )
                    }
                }

                // CameraX Preview Box
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(SnapObsidian),
                    contentAlignment = Alignment.Center
                ) {
                    if (hasCameraAndAudioPerms) {
                        val previewView = remember { PreviewView(context) }

                        LaunchedEffect(useFrontCamera) {
                            val cameraProvider = ProcessCameraProvider.getInstance(context).get()
                            val preview = Preview.Builder().build().also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }
                            val recorder = Recorder.Builder()
                                .setQualitySelector(QualitySelector.from(Quality.HD))
                                .build()
                            val vc = VideoCapture.withOutput(recorder)
                            videoCaptureUseCase = vc
                            val selector = if (useFrontCamera) {
                                CameraSelector.DEFAULT_FRONT_CAMERA
                            } else {
                                CameraSelector.DEFAULT_BACK_CAMERA
                            }
                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, vc)
                            } catch (_: Exception) {
                            }
                        }

                        AndroidView(
                            factory = { previewView },
                            modifier = Modifier.fillMaxSize()
                        )

                        if (isRecording) {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                LinearProgressIndicator(
                                    progress = { (recordElapsedSec.toFloat() / selectedDurationSec.toFloat()).coerceIn(0f, 1f) },
                                    color = SnapCoral,
                                    trackColor = Color.White.copy(alpha = 0.25f),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Surface(color = SnapCoral, shape = RoundedCornerShape(50)) {
                                    Text(
                                        text = "REC ${recordElapsedSec}s / ${selectedDurationSec}s",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = { if (!isRecording) useFrontCamera = !useFrontCamera },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.45f))
                        ) {
                            Icon(Icons.Rounded.Cameraswitch, contentDescription = "Flip camera", tint = Color.White)
                        }
                    } else {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Videocam,
                                contentDescription = null,
                                tint = SnapEmerald,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                "Enable Camera & Microphone to record 9:16 Reels, or pick any video from your gallery.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                            Button(
                                onClick = {
                                    permLauncher.launch(
                                        arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                                    )
                                },
                                modifier = Modifier.testTag("grant_camera_permissions_button")
                            ) {
                                Text("Enable Camera & Mic")
                            }
                        }
                    }
                }

                // Controls Row: Gallery Picker + Record Button + Music Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            galleryVideoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        },
                        modifier = Modifier.testTag("pick_gallery_video_button")
                    ) {
                        Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Gallery")
                    }

                    Surface(
                        onClick = {
                            if (!hasCameraAndAudioPerms) {
                                permLauncher.launch(
                                    arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                                )
                                return@Surface
                            }
                            val vc = videoCaptureUseCase ?: return@Surface
                            if (isRecording) {
                                activeRecording?.stop()
                                isRecording = false
                            } else {
                                val outFile = File(context.cacheDir, "rec_${System.currentTimeMillis()}.mp4")
                                val outputOptions = FileOutputOptions.Builder(outFile).build()
                                val recording = vc.output
                                    .prepareRecording(context, outputOptions)
                                    .withAudioEnabled()
                                    .start(ContextCompat.getMainExecutor(context)) { event ->
                                        when (event) {
                                            is VideoRecordEvent.Start -> isRecording = true
                                            is VideoRecordEvent.Finalize -> {
                                                isRecording = false
                                                if (!event.hasError()) {
                                                    selectedVideoUri = Uri.fromFile(outFile)
                                                }
                                            }
                                        }
                                    }
                                activeRecording = recording
                            }
                        },
                        shape = CircleShape,
                        color = if (isRecording) SnapCoral else SnapEmerald,
                        modifier = Modifier
                            .size(70.dp)
                            .testTag("record_reel_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isRecording) Icons.Rounded.Stop else Icons.Rounded.FiberManualRecord,
                                contentDescription = if (isRecording) "Stop recording" else "Start recording",
                                tint = if (isRecording) Color.White else SnapObsidian,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { showMusicSheet = true },
                        modifier = Modifier.testTag("select_music_button")
                    ) {
                        Icon(Icons.Rounded.MusicNote, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Audio")
                    }
                }
            }
        } else {
            // PREVIEW & PUBLISH DETAILS STEP
            val previewUri = selectedVideoUri!!
            val previewPlayer = remember(previewUri) {
                ExoPlayer.Builder(context).build().apply {
                    repeatMode = Player.REPEAT_MODE_ONE
                    setMediaItem(MediaItem.fromUri(previewUri))
                    prepare()
                    playWhenReady = true
                }
            }
            DisposableEffect(previewPlayer) {
                onDispose { previewPlayer.release() }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 9:16 Video Preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .aspectRatio(9f / 16f)
                        .align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(20.dp))
                        .background(SnapObsidian)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = previewPlayer
                                useController = true
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    Surface(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "${selectedDurationSec}s • ${selectedTrack.title}",
                            style = MaterialTheme.typography.labelSmall,
                            color = SnapEmerald,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Thumbnail Picker
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Select Video Thumbnail", style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(
                        onClick = {
                            customThumbPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    ) {
                        Text("Custom Cover")
                    }
                }

                if (customThumbUri != null) {
                    AsyncImage(
                        model = customThumbUri,
                        contentDescription = "Custom thumbnail",
                        modifier = Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(2.dp, SnapEmerald, RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else if (extractedThumbs.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(extractedThumbs) { index, file ->
                            AsyncImage(
                                model = file,
                                contentDescription = "Frame $index",
                                modifier = Modifier
                                    .size(width = 72.dp, height = 108.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(
                                        width = if (selectedThumbIndex == index) 3.dp else 1.dp,
                                        color = if (selectedThumbIndex == index) SnapEmerald else Color.Gray,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable { selectedThumbIndex = index },
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it.take(500) },
                    label = { Text("Caption") },
                    placeholder = { Text("Describe your SNAP Reel...") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reel_caption_input")
                )

                OutlinedTextField(
                    value = hashtagsInput,
                    onValueChange = { hashtagsInput = it },
                    label = { Text("Hashtags (space or comma separated)") },
                    placeholder = { Text("snap travel music") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reel_hashtags_input")
                )

                // Licensed Background Music Card
                Surface(
                    onClick = { showMusicSheet = true },
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 2.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("change_music_card")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text(selectedTrack.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${selectedTrack.artist} • ${selectedTrack.licenseBadge}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text("Change", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    }
                }

                // Video Privacy Selector
                Text("Who can watch this Reel?", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReelVisibility.entries.forEach { vis ->
                        FilterChip(
                            selected = selectedVisibility == vis,
                            onClick = { selectedVisibility = vis },
                            label = { Text(vis.label) },
                            modifier = Modifier.testTag("visibility_${vis.value}")
                        )
                    }
                }

                // Copyright Confirmation
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { copyrightConfirmed = !copyrightConfirmed }
                ) {
                    Checkbox(
                        checked = copyrightConfirmed,
                        onCheckedChange = { copyrightConfirmed = it }
                    )
                    Text(
                        text = "I confirm I hold the rights to this video and am using licensed SNAP audio (${selectedTrack.licenseBadge}).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isUploading && uploadProgressMessage != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(uploadProgressMessage ?: "Uploading...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                Button(
                    onClick = {
                        val creator = currentProfile ?: UserProfile(
                            userId = reelsViewModel.currentUserId,
                            username = "snapcreator",
                            displayName = "SNAP Creator"
                        )
                        val tags = hashtagsInput
                            .split(" ", ",", "#")
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                        val qualityHigh = VideoQualityOption.fromValue(
                            privateSettings?.videoQuality ?: VideoQualityOption.AUTO.value
                        ) != VideoQualityOption.DATA_SAVER_480

                        val chosenThumbFile = if (customThumbUri != null) {
                            runCatching {
                                val out = File(context.cacheDir, "custom_thumb_${System.currentTimeMillis()}.jpg")
                                context.contentResolver.openInputStream(customThumbUri!!)?.use { input ->
                                    out.outputStream().use { output -> input.copyTo(output) }
                                }
                                out
                            }.getOrNull()
                        } else {
                            extractedThumbs.getOrNull(selectedThumbIndex)
                        }

                        reelsViewModel.uploadAndPublishReel(
                            context = context,
                            creator = creator,
                            videoUri = previewUri,
                            customThumbnailFile = chosenThumbFile,
                            caption = caption,
                            hashtags = tags,
                            track = selectedTrack,
                            durationSeconds = selectedDurationSec,
                            visibility = selectedVisibility,
                            copyrightConfirmed = copyrightConfirmed,
                            highQuality = qualityHigh,
                            onPublished = onPublished
                        )
                    },
                    enabled = !isUploading && copyrightConfirmed,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("publish_reel_button")
                ) {
                    Text(
                        if (isUploading) "Compressing & Publishing..." else "Publish SNAP Reel",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }

    if (showMusicSheet) {
        LicensedMusicPickerSheet(
            selectedTrack = selectedTrack,
            onSelect = {
                selectedTrack = it
                showMusicSheet = false
            },
            onDismiss = { showMusicSheet = false }
        )
    }
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LicensedMusicPickerSheet(
    selectedTrack: LicensedTrack,
    onSelect: (LicensedTrack) -> Unit,
    onDismiss: () -> Unit
) {
    val toneGenerator = remember {
        runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 60) }.getOrNull()
    }
    DisposableEffect(Unit) {
        onDispose { toneGenerator?.release() }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Legally Licensed SNAP Audio", style = MaterialTheme.typography.titleLarge)
            Text(
                "All tracks below are cleared for SNAP Reels with built-in copyright attribution.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            LicensedMusicCatalog.tracks.forEach { track ->
                val isSelected = track.id == selectedTrack.id
                Surface(
                    onClick = { onSelect(track) },
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (track.previewToneHz > 0) {
                                    toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 350)
                                }
                            }
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = "Preview track")
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(track.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${track.artist} • ${track.genre} • ${track.licenseBadge}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isSelected) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}
