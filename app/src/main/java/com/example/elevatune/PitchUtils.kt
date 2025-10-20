package com.example.elevatune

import android.util.Log
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max

object PitchUtils {
    fun midiToNoteName(midi: Int): String {
        val notes = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        val note = notes[midi % 12]
        val octave = (midi / 12) - 1
        return "$note$octave"
    }

    // Compare how close two pitches are (percentage)
    fun similarity(p1: Float, p2: Float): Float {
        if (p1 <= 0 || p2 <= 0) return 0f

        // Convert frequency difference to semitones
        val diffInSemitones = 12 * log2(p1 / p2)

        // Take absolute difference
        val absDiff = abs(diffInSemitones)

        // If notes are within 0 semitones → identical, within 1 semitone → high similarity, etc.
        // You can tune the curve as you like
        val similarity = (1f - (absDiff / 12f)).coerceIn(0f, 1f)

        return similarity;
    }


    /**
     * Compute pitch (Hz) from a buffer using YIN.
     *
     * @param buffer mono PCM floats [-1..1]
     * @param sampleRate sample rate in Hz (e.g. 44100)
     * @param yinBuffer reusable buffers to avoid allocations (optional)
     * @param threshold detection threshold (typical 0.10 - 0.20)
     * @param minFreq minimal frequency to detect (e.g. 50 Hz)
     * @param maxFreq maximal frequency to detect (e.g. 2000 Hz)
     */
    fun getPitch(
        buffer: FloatArray,
        sampleRate: Int,
        threshold: Float = 0.15f,
        minFreq: Float = 70f,
        maxFreq: Float = 2000f
    ): Float {
        val n = buffer.size
        if (n == 0) return 0f

        // maxLag determined by minFreq
        val maxLag = (sampleRate / minFreq).toInt().coerceAtMost(n / 2)
        val minLag = (sampleRate / maxFreq).toInt().coerceAtLeast(2)

        val tauMax = maxLag
        val yinBuffer = FloatArray(tauMax + 1)

        // 1) difference function
        for (tau in 0..tauMax) {
            var sum = 0f
            var j = 0
            val limit = n - tau
            while (j < limit) {
                val diff = buffer[j] - buffer[j + tau]
                sum += diff * diff
                j++
            }
            yinBuffer[tau] = sum
        }

        // 2) cumulative mean normalized difference function (CMND)
        var runningSum = 0f
        yinBuffer[0] = 1f // protect
        for (tau in 1..tauMax) {
            runningSum += yinBuffer[tau]
            yinBuffer[tau] = yinBuffer[tau] * tau / runningSum
        }

        // 3) absolute threshold
        var tauEstimate = -1
        var tau = minLag
        while (tau <= tauMax) {
            if (yinBuffer[tau] < threshold) {
                // find local minimum
                var t = tau
                while (t + 1 <= tauMax && yinBuffer[t + 1] < yinBuffer[t]) {
                    t++
                }
                tauEstimate = t
                break
            }
            tau++
        }
        if (tauEstimate == -1) return 0f // no pitch found (unvoiced)

        // 4) parabolic interpolation for better precision
        val betterTau = parabolicInterpolation(yinBuffer, tauEstimate)

        // convert lag -> Hz
        return sampleRate / betterTau
    }

    private fun parabolicInterpolation(yinBuffer: FloatArray, tau: Int): Float {
        val x0 = if (tau - 1 >= 0) yinBuffer[tau - 1] else yinBuffer[tau]
        val x1 = yinBuffer[tau]
        val x2 = if (tau + 1 < yinBuffer.size) yinBuffer[tau + 1] else yinBuffer[tau]
        val denom = (x0 + x2 - 2 * x1)
        return if (denom == 0f) tau.toFloat() else tau + (x0 - x2) / (2 * denom)
    }

    fun hzToMidi(hz: Float): Float =
        if (hz <= 0f) 0f else (69 + 12 * log2(hz / 440f))
}