package com.example.elevatune

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteOrder

object FileUtils {
    fun getFilePathFromUri(context: Context, uri: Uri): String {
        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
        val file = File(context.cacheDir, "temp_${System.currentTimeMillis()}.wav")
        val outputStream = FileOutputStream(file)
        val buffer = ByteArray(1024)
        var bytesRead: Int
        while (inputStream?.read(buffer).also { bytesRead = it ?: -1 } != -1) {
            outputStream.write(buffer, 0, bytesRead)
        }
        outputStream.close()
        inputStream?.close()
        return file.absolutePath
    }

    fun decodeToPcmWav(context: Context, inputUri: Uri, outputFile: File): Boolean {
        return try {
            val extractor = MediaExtractor()
            extractor.setDataSource(context, inputUri, null)

            var format: MediaFormat? = null
            var trackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    format = f
                    trackIndex = i
                    break
                }
            }

            if (format == null || trackIndex == -1) return false

            extractor.selectTrack(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            val decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(format, null, null, 0)
            decoder.start()

            val bufferInfo = MediaCodec.BufferInfo()
            val outStream = DataOutputStream(FileOutputStream(outputFile))
            writeWavHeader(outStream, 0, 1, 44100, 16) // placeholder

            var totalBytes = 0
            var isEOS = false
            while (!isEOS) {
                val inBuffer = decoder.dequeueInputBuffer(10000)
                if (inBuffer >= 0) {
                    val buffer = decoder.getInputBuffer(inBuffer)!!
                    val sampleSize = extractor.readSampleData(buffer, 0)
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(inBuffer, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        isEOS = true
                    } else {
                        decoder.queueInputBuffer(inBuffer, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }

                var outBufferId = decoder.dequeueOutputBuffer(bufferInfo, 10000)
                while (outBufferId >= 0) {
                    val outBuffer = decoder.getOutputBuffer(outBufferId)!!
                    val chunk = ByteArray(bufferInfo.size)
                    outBuffer.get(chunk)
                    outBuffer.clear()
                    outStream.write(chunk)
                    totalBytes += chunk.size
                    decoder.releaseOutputBuffer(outBufferId, false)
                    outBufferId = decoder.dequeueOutputBuffer(bufferInfo, 0)
                }
            }

            decoder.stop()
            decoder.release()
            extractor.release()

            outStream.close()
            fixWavHeader(outputFile, totalBytes)

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // Write placeholder WAV header
    fun writeWavHeader(out: DataOutputStream, totalAudioLen: Int, channels: Int, sampleRate: Int, bitsPerSample: Int) {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val header = ByteArray(44)
        // ... fill header as shown in previous examples
        out.write(header)
    }

    // Fix header after writing PCM data
    fun fixWavHeader(file: File, totalAudioLen: Int) {
        val raf = RandomAccessFile(file, "rw")
        val totalDataLen = totalAudioLen + 36
        val byteRate = 44100 * 1 * 16 / 8
        raf.seek(4)
        raf.writeInt(Integer.reverseBytes(totalDataLen))
        raf.seek(40)
        raf.writeInt(Integer.reverseBytes(totalAudioLen))
        raf.close()
    }

    fun decodeAudioToFloatArray(context: Context, uri: Uri): FloatArray {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, uri, null)

        var format: MediaFormat? = null
        var trackIndex = -1

        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) {
                format = f
                trackIndex = i
                break
            }
        }

        if (format == null || trackIndex == -1)
            throw IllegalArgumentException("No audio track found in file")

        extractor.selectTrack(trackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val decoder = MediaCodec.createDecoderByType(mime)
        decoder.configure(format, null, null, 0)
        decoder.start()

        val pcmOutput = mutableListOf<Float>()
        val bufferInfo = MediaCodec.BufferInfo()

        var isEOS = false
        while (!isEOS) {
            val inputIndex = decoder.dequeueInputBuffer(10000)
            if (inputIndex >= 0) {
                val inputBuffer = decoder.getInputBuffer(inputIndex)!!
                val sampleSize = extractor.readSampleData(inputBuffer, 0)
                if (sampleSize < 0) {
                    decoder.queueInputBuffer(
                        inputIndex,
                        0,
                        0,
                        0L,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                    )
                    isEOS = true
                } else {
                    decoder.queueInputBuffer(
                        inputIndex,
                        0,
                        sampleSize,
                        extractor.sampleTime,
                        0
                    )
                    extractor.advance()
                }
            }

            var outputIndex = decoder.dequeueOutputBuffer(bufferInfo, 10000)
            while (outputIndex >= 0) {
                val outputBuffer = decoder.getOutputBuffer(outputIndex)!!
                outputBuffer.order(ByteOrder.LITTLE_ENDIAN)
                val shorts = ShortArray(bufferInfo.size / 2)
                outputBuffer.asShortBuffer().get(shorts)
                outputBuffer.clear()

                // Convert PCM16 to float (-1.0f .. 1.0f)
                shorts.forEach { pcmOutput.add(it / 32768f) }

                decoder.releaseOutputBuffer(outputIndex, false)
                outputIndex = decoder.dequeueOutputBuffer(bufferInfo, 0)
            }
        }

        decoder.stop()
        decoder.release()
        extractor.release()

        return pcmOutput.toFloatArray()
    }
}