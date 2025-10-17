package com.example.elevetune

import be.tarsos.dsp.AudioDispatcher
import be.tarsos.dsp.io.jvm.AudioDispatcherFactory
import be.tarsos.dsp.pitch.PitchDetectionHandler
import be.tarsos.dsp.pitch.PitchProcessor
import kotlin.math.roundToInt

data class PitchPoint(val timeSec: Double, val frequency: Float)

class ReferenceAnalyzer {
    // extracted pitch contour
    val contour = mutableListOf<PitchPoint>()

    /**
     * Analyze a WAV file (PCM) and fill contour.
     * filepath: full path to the WAV file (PCM).
     */
    fun analyzeWavFile(filepath: String, sampleRate: Int = 44100, bufferSize: Int = 2048, overlap: Int = 0, onComplete: () -> Unit) {
        contour.clear()
        val dispatcher: AudioDispatcher = AudioDispatcherFactory.fromPipe(filepath, sampleRate, bufferSize, overlap)
        val startTimeNano = System.nanoTime()

        val handler = PitchDetectionHandler { res, event ->
            val pitchHz = res.pitch    // -1 if unknown
            val processedSamples = event.bufferSize // not exact, but useful
            // compute approximate time from number of processed frames:
            // Tarsos provides event.timeStamp as seconds sometimes; we'll use event.timeStamp if available:
            val timeSec = event.timeStamp // in seconds
            if (pitchHz > 0) {
                contour.add(PitchPoint(timeSec, pitchHz))
            } else {
                contour.add(PitchPoint(timeSec, 0f))
            }
        }

        val pp = PitchProcessor(PitchProcessor.PitchEstimationAlgorithm.YIN, sampleRate.toFloat(), bufferSize, handler)
        dispatcher.addAudioProcessor(pp)

        Thread {
            dispatcher.run()
            onComplete()
        }.start()
    }
}
