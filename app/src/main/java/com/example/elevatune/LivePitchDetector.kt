package com.example.elevatune

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LivePitchDetector(
    private val onPitchDetected: (Float) -> Unit
) {
    private var isRunning = false
    private var audioRecord: AudioRecord? = null

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start(scope: CoroutineScope) {
        if (isRunning) return
        isRunning = true

        val sampleRate = 44100
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        audioRecord?.startRecording()

        scope.launch(Dispatchers.IO) {
            val buffer = ShortArray(bufferSize)

            while (isActive && isRunning) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    val floatBuffer = FloatArray(read) { i -> buffer[i] / 32768.0f }
                    val pitch = PitchUtils.getPitch(floatBuffer, sampleRate)
                    if (pitch > 0) {
                        onPitchDetected(pitch)
                    }
                }
            }

            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        }
    }

    fun stop() {
        isRunning = false
    }
}
