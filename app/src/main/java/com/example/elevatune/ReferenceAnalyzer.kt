package com.example.elevatune

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

data class PitchPoint(val timeSec: Float, val pitch: Float)

object ReferenceAnalyzer {

    fun analyze(context: Context, uri: Uri, listener: AnalyzeProgressListener) {
        Thread {
            try {
                val extractor = MediaExtractor()
                extractor.setDataSource(context, uri, null)

                // find first audio track
                var audioFormat: MediaFormat? = null
                var trackIndex = -1
                for (i in 0 until extractor.trackCount) {
                    val f = extractor.getTrackFormat(i)
                    val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                    if (mime.startsWith("audio/")) {
                        audioFormat = f
                        trackIndex = i
                        break
                    }
                }
                if (audioFormat == null || trackIndex == -1) {
                    listener.onError(IllegalArgumentException("No audio track found"))
                    return@Thread
                }

                extractor.selectTrack(trackIndex)
                val mime = audioFormat.getString(MediaFormat.KEY_MIME)!!
                val sampleRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                val channelCount = if (audioFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT))
                    audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 1
                val durationUs = if (audioFormat.containsKey(MediaFormat.KEY_DURATION))
                    audioFormat.getLong(MediaFormat.KEY_DURATION) else -1L

                Log.d("ReferenceAnalyzer", "SampleRate = $sampleRate, Channels = $channelCount")

                val decoder = MediaCodec.createDecoderByType(mime)
                decoder.configure(audioFormat, null, null, 0)
                decoder.start()

                val bufferInfo = MediaCodec.BufferInfo()
                var isEOS = false

                // YIN params
                val frameSize = 2048
                val hopSize = 512

                val overlapBuffer = FloatArray(frameSize)
                var overlapBufferFill = 0

                val pitchPoints = mutableListOf<PitchPoint>()
                var totalSamplesRead = 0L

                while (!isEOS) {
                    val inputIndex = decoder.dequeueInputBuffer(10000)
                    if (inputIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inputIndex)!!
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize <= 0) {
                            decoder.queueInputBuffer(
                                inputIndex, 0, 0, 0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            isEOS = true
                        } else {
                            decoder.queueInputBuffer(
                                inputIndex, 0, sampleSize, extractor.sampleTime, 0
                            )
                            extractor.advance()
                        }
                    }

                    var outputIndex = decoder.dequeueOutputBuffer(bufferInfo, 10000)
                    while (outputIndex >= 0) {
                        val outputBuffer = decoder.getOutputBuffer(outputIndex)!!
                        outputBuffer.order(ByteOrder.LITTLE_ENDIAN)

                        // read PCM16 bytes
                        val outBytes = ByteArray(bufferInfo.size)
                        outputBuffer.get(outBytes)
                        outputBuffer.clear()

                        // convert bytes -> shorts
                        val shortCount = outBytes.size / 2
                        val shortBuf = ShortArray(shortCount)
                        ByteBuffer.wrap(outBytes)
                            .order(ByteOrder.LITTLE_ENDIAN)
                            .asShortBuffer()
                            .get(shortBuf)

                        // 🔊 FORCE MONO if stereo
                        val monoShorts: ShortArray = if (channelCount == 2) {
                            val monoCount = shortCount / 2
                            val mono = ShortArray(monoCount)
                            var j = 0
                            for (i in 0 until monoCount) {
                                val left = shortBuf[j++].toInt()
                                val right = shortBuf[j++].toInt()
                                mono[i] = ((left + right) / 2).toShort()
                            }
                            mono
                        } else {
                            shortBuf
                        }

                        // Feed mono data into overlap buffer
                        var srcIndex = 0
                        while (srcIndex < monoShorts.size) {
                            val needed = frameSize - overlapBufferFill
                            val copyCount = min(needed, monoShorts.size - srcIndex)
                            for (k in 0 until copyCount) {
                                overlapBuffer[overlapBufferFill + k] = monoShorts[srcIndex + k] / 32768f
                            }
                            overlapBufferFill += copyCount
                            srcIndex += copyCount
                            totalSamplesRead += copyCount

                            if (overlapBufferFill == frameSize) {
                                val frameStartSample = (totalSamplesRead - frameSize).toInt()
                                val timeSec = frameStartSample / sampleRate.toFloat()

                                val frame = overlapBuffer.copyOf()
                                val pitchHz = PitchUtils.getPitch(frame, sampleRate)
                                pitchPoints.add(PitchPoint(timeSec, if (pitchHz <= 0f) 0f else pitchHz))

                                // shift overlap buffer
                                val remain = frameSize - hopSize
                                for (i in 0 until remain) overlapBuffer[i] = overlapBuffer[i + hopSize]
                                overlapBufferFill = remain
                            }
                        }

                        if (durationUs > 0) {
                            val progress = ((extractor.sampleTime.toDouble() / durationUs) * 100).toInt()
                            listener.onProgress(progress.coerceIn(0, 100))
                        }

                        decoder.releaseOutputBuffer(outputIndex, false)
                        outputIndex = decoder.dequeueOutputBuffer(bufferInfo, 0)
                    }
                }

                // flush remainder
                if (overlapBufferFill > frameSize / 4) {
                    val frameStartSample = (totalSamplesRead - overlapBufferFill).toInt().coerceAtLeast(0)
                    val timeSec = frameStartSample / sampleRate.toFloat()

                    val frame = overlapBuffer.copyOf()
                    for (i in overlapBufferFill until frameSize) frame[i] = 0f
                    val pitchHz = PitchUtils.getPitch(frame, sampleRate)
                    pitchPoints.add(PitchPoint(timeSec, if (pitchHz <= 0f) 0f else pitchHz))
                }

                decoder.stop()
                decoder.release()
                extractor.release()

                listener.onProgress(100)
                listener.onComplete(pitchPoints)
            } catch (e: Exception) {
                Log.e("ReferenceAnalyzer", "analyze failed", e)
                listener.onError(e)
            }
        }.start()
    }
}
