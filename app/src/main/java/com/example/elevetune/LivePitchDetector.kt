package com.example.elevetune

import be.tarsos.dsp.AudioDispatcher
import be.tarsos.dsp.io.jvm.AudioDispatcherFactory;
import be.tarsos.dsp.pitch.PitchDetectionHandler
import be.tarsos.dsp.pitch.PitchProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LivePitchDetector(
    private val sampleRate: Int = 44100,
    private val bufferSize: Int = 2048,
    private val overlap: Int = 0,
    private val onPitch: (timeSec: Double, freqHz: Float) -> Unit
) {
    private var dispatcher: AudioDispatcher? = null

    fun start() {
        dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(sampleRate, bufferSize, overlap)
        val handler = PitchDetectionHandler { res, event ->
            val pitchHz = res.pitch
            val timeSec = event.timeStamp
            val freq = if (pitchHz > 0) pitchHz else 0f
            onPitch(timeSec, freq)
        }
        val pp = PitchProcessor(PitchProcessor.PitchEstimationAlgorithm.YIN, sampleRate.toFloat(), bufferSize, handler)
        dispatcher?.addAudioProcessor(pp)

        CoroutineScope(Dispatchers.Default).launch {
            dispatcher?.run()
        }
    }

    fun stop() {
        dispatcher?.stop()
        dispatcher = null
    }
}
