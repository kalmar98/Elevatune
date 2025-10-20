package com.example.elevatune

import android.R.attr.textSize
import android.annotation.SuppressLint
import android.graphics.Paint.Align
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.elevatune.ui.theme.ElevatuneTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    private var playbackManager: PlaybackManager? = null
    private var pitchDetector: LivePitchDetector? = null


    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d("Elevatune", "Opa")

        if (!PermissionUtils.hasAudioPermission(this)) {
            PermissionUtils.requestAudioPermission(this)
        }

        if (PermissionUtils.hasAudioPermission(this)) {
            Log.d("Elevatune", "Microphone permission granted ✅")
        } else {
            Log.e("Elevatune", "Microphone permission missing ❌")
        }

        enableEdgeToEdge()

        setContent {
            ElevatuneTheme {
                playbackManager = PlaybackManager(this)

                val context = LocalContext.current
                val scope = rememberCoroutineScope()

                var instrumentalUri by remember { mutableStateOf<Uri?>(null) }
                var vocalUri by remember { mutableStateOf<Uri?>(null) }

                var referenceList by remember { mutableStateOf(listOf<PitchPoint>()) }

                var playbackTimeSec by remember { mutableFloatStateOf(0f) }
                var livePitch by remember { mutableFloatStateOf(0f) }
                var similarity by remember { mutableFloatStateOf(0f) }

                var analyzing by remember { mutableStateOf(false) }
                var progress by remember { mutableIntStateOf(0) }

                val pickInstrumentalLauncher =
                    rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                        uri?.let {
                            playbackManager?.setSource(it)
                            instrumentalUri = it
                            Log.d("Elevatune", "Instrumental selected: $instrumentalUri")
                        }
                    }

                val pickVocalLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                    onResult = { uri ->
                        uri?.let {
                            vocalUri = it
                            Log.d("Elevatune", "Vocal selected: $it")

                            // Start background analysis
                            analyzing = true
                            ReferenceAnalyzer.analyze(context, it, object : AnalyzeProgressListener {
                                override fun onProgress(percent: Int) {
                                    Handler(Looper.getMainLooper()).post { progress = percent }
                                }

                                override fun onComplete(pitchList: List<PitchPoint>) {
                                    Handler(Looper.getMainLooper()).post {
                                        referenceList = pitchList
                                        analyzing = false
                                        progress = 100
                                        Log.d("Elevatune", "Reference analysis complete. Frames=${pitchList.size}")
                                    }
                                }

                                override fun onError(e: Exception) {
                                    Handler(Looper.getMainLooper()).post {
                                        analyzing = false
                                        Log.e("Elevatune", "Reference analysis failed", e)
                                    }
                                }
                            })
                        }
                    }
                )

                // Permission launcher
                val micPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                    onResult = { granted ->
                        if (granted) {
                            pitchDetector?.start(scope)
                        } else {
                            Toast.makeText(context, "Microphone permission denied", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                Scaffold(
                    topBar = { SimpleTopBar("Elevatune") }
                ) { padding ->
                    Column(
                        Modifier
                            .padding(padding)
                            .fillMaxSize()
                            .background(Color.Black)
                    ) {
                        PitchGraph(
                            referenceList,
                            livePitch,
                            playbackManager,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                                .height(400.dp)
                        )

                        //Pre-First Row
                        Row(
                            Modifier.fillMaxWidth().padding(2.dp),
                            horizontalArrangement = Arrangement.Center
                        ){
                            Text("Live pitch: $livePitch", color = Color.White)
                        }
                        //First Row
                        Row(
                            Modifier.fillMaxWidth().padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Button(onClick = { pickInstrumentalLauncher.launch("audio/*") }) {
                                Text("Pick Instrumental")
                            }
                            Button(onClick = { pickVocalLauncher.launch(arrayOf("audio/*")) }) {
                                Text("Pick Vocal")
                            }
                        }

                        //Second Row
                        Row(
                            Modifier.fillMaxWidth().padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Button(onClick = {
                                playbackManager?.play()

                                pitchDetector = LivePitchDetector { pitch ->
                                    livePitch = pitch

                                    // Switch to main thread to safely access the ExoPlayer
                                    CoroutineScope(Dispatchers.Main).launch {
                                        playbackTimeSec = playbackManager?.getCurrentPosition()?.div(1000f) ?: return@launch

                                        // find nearest pitch point
                                        val nearest = referenceList.minByOrNull { abs(it.timeSec - playbackTimeSec) }
                                        if (nearest != null && livePitch > 0f) {
                                            val similarity = PitchUtils.similarity(nearest.pitch, livePitch)
                                            // you can store or display this value here
                                        }
                                    }
                                }

                                micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                            },
                                enabled = instrumentalUri != null && vocalUri != null && !analyzing,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    disabledContentColor = MaterialTheme.colorScheme.secondary,
                                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                )

                            ) {
                                Text("Start Singing")
                            }
                            Button(onClick = {
                                playbackManager?.pause()
                                pitchDetector?.stop()
                            }) {
                                Text("Pause")
                            }
                            Button(onClick = {
                                playbackManager?.stop()
                                pitchDetector?.stop()
                            }) {
                                Text("Stop")
                            }
                        }

                        //Third Row
                        Row(
                            Modifier.fillMaxWidth().padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Text(
                                "Accuracy: ${"%.0f".format(similarity * 100)}%",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                        }

                        //Fourth Row
                        if (analyzing) {
                            Row(
                                Modifier.fillMaxWidth().padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                Text("Analyzing reference vocal... $progress%", color = Color.White)
                                LinearProgressIndicator(
                                    progress = { progress / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    color = ProgressIndicatorDefaults.linearColor,
                                    trackColor = ProgressIndicatorDefaults.linearTrackColor,
                                    strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PitchGraph(
    referenceList: List<PitchPoint>,
    livePitch: Float,
    playbackManager: PlaybackManager?,
    modifier: Modifier = Modifier
) {
    val maxTime = referenceList.lastOrNull()?.timeSec ?: 60f
    val visibleWindow = 10f // seconds visible at once

    val minMidi = 40   // E2
    val maxMidi = 76   // E5

    val livePoints = remember { mutableStateListOf<Pair<Float, Float>>() }
    val playbackTimeSec = remember { mutableFloatStateOf(0f) }

    // Continuously update playback time for smooth scrolling
    LaunchedEffect(Unit) {
        while (true) {
            playbackTimeSec.floatValue =
                (playbackManager?.getCurrentPosition()?.toFloat()?.div(1000f)) ?: 0f
            delay(16) // ~60 FPS
        }
    }

    // Add new live pitch points
    if (livePitch > 0f) {
        livePoints.add(playbackTimeSec.floatValue to PitchUtils.hzToMidi(livePitch))
    }

    val labelWidth = 30.dp
    val textPaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 30f
            isAntiAlias = true
            textAlign = Align.RIGHT
        }
    }

    Box(modifier = modifier.background(Color.Black)) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = labelWidth)
        ) {
            val width = size.width
            val height = size.height

            val midiRange = (minMidi..maxMidi)
            val noteHeight = height / (maxMidi - minMidi + 1)

            // 🎹 Draw piano-roll-like horizontal grid
            midiRange.forEach { midi ->
                val y = height - (midi - minMidi) * noteHeight
                val isWhiteNote = PitchUtils.midiToNoteName(midi).let {
                    !it.contains("#")
                }

                // alternating white/gray background rows
                drawRect(
                    color = if (isWhiteNote) Color(0xFF202020) else Color(0xFF181818),
                    topLeft = Offset(0f, y - noteHeight),
                    size = androidx.compose.ui.geometry.Size(width, noteHeight)
                )

                // horizontal grid line
                drawLine(
                    color = Color.DarkGray.copy(alpha = 0.6f),
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1f
                )
            }

            val windowStart = playbackTimeSec.floatValue - (visibleWindow / 2)
            val windowEnd = windowStart + visibleWindow
            val pointsInWindow = referenceList.filter { it.timeSec in windowStart..windowEnd }

            // 🎵 Draw reference contour as horizontal bars instead of continuous lines
            for (i in 0 until pointsInWindow.size - 1) {
                val p1 = pointsInWindow[i]
                val p2 = pointsInWindow[i + 1]
                if (p1.pitch > 0 && p2.pitch > 0) {
                    val x1 = ((p1.timeSec - windowStart) / visibleWindow) * width
                    val x2 = ((p2.timeSec - windowStart) / visibleWindow) * width
                    val y = height - ((PitchUtils.hzToMidi(p1.pitch) - minMidi) / (maxMidi - minMidi)) * height
                    val barHeight = 4f

                    drawRect(
                        color = Color.Green,
                        topLeft = Offset(x1, y - barHeight / 2),
                        size = androidx.compose.ui.geometry.Size(x2 - x1, barHeight)
                    )
                }
            }

            // 🔴 Draw live pitch dots
            livePoints.takeLast(100).forEach { (time, pitch) ->
                if (pitch > 0f) {
                    val x = ((time - windowStart) / visibleWindow) * width
                    val y =
                        height - ((pitch - minMidi) / (maxMidi - minMidi)) * height
                    drawCircle(Color.Red, radius = 5f, center = Offset(x, y))
                }
            }
        }

        // 🎵 Note names on the left side
        Canvas(
            modifier = Modifier
                .fillMaxHeight()
                .width(labelWidth)
        ) {
            val height = size.height
            val noteHeight = height / (maxMidi - minMidi + 1)

            (minMidi..maxMidi).forEach { midi ->
                val noteName = PitchUtils.midiToNoteName(midi)
                val y = height - (midi - minMidi) * noteHeight + noteHeight / 2 - 20
                drawContext.canvas.nativeCanvas.drawText(
                    noteName,
                    labelWidth.toPx() - 10f,
                    y,
                    textPaint
                )
            }
        }
    }
}

@Composable
fun SimpleTopBar(title: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF000000))
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(title, color = Color.White, style = MaterialTheme.typography.titleLarge)
    }
}


