package com.example.decodektgaudio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.IOException
import java.io.InputStream

class Encoder(
    private val bitrate: Int,
    private val sampleRate: Int,
    private val channelCount: Int
) {
    private var mediaFormat: MediaFormat? = null
    private var mediaCodec: MediaCodec? = null
    private var mediaMuxer: MediaMuxer? = null
    private var bufferInfo: MediaCodec.BufferInfo? = null
    private var outputPath: String? = null
    private var audioTrackId = 0
    private var totalBytesRead = 0
    private var presentationTimeUs = 0.0
    fun setOutputPath(outputPath: String?) {
        this.outputPath = outputPath
    }

    fun prepare() {
        checkNotNull(outputPath) { "The output path must be set first!" }
        try {
            mediaFormat = MediaFormat.createAudioFormat(
                COMPRESSED_AUDIO_FILE_MIME_TYPE,
                sampleRate,
                channelCount
            )
            mediaFormat!!.setInteger(
                MediaFormat.KEY_AAC_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AACObjectLC
            )
            mediaFormat!!.setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            mediaCodec = MediaCodec.createEncoderByType(COMPRESSED_AUDIO_FILE_MIME_TYPE)
            mediaCodec!!.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            mediaCodec!!.start()
            bufferInfo = MediaCodec.BufferInfo()
            mediaMuxer = MediaMuxer(outputPath!!, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            totalBytesRead = 0
            presentationTimeUs = 0.0
        } catch (e: IOException) {
            Log.e(TAG, "Exception while initializing com.example.decodektgaudio.PCMEncoder", e)
        }
    }

    fun stop() {
        Log.d(TAG, "Stopping com.example.decodektgaudio.PCMEncoder")
        handleEndOfStream()
        mediaCodec!!.stop()
        mediaCodec!!.release()
        mediaMuxer!!.stop()
        mediaMuxer!!.release()
    }

    private fun handleEndOfStream() {
        val inputBufferIndex = mediaCodec!!.dequeueInputBuffer(CODEC_TIMEOUT.toLong())
        mediaCodec!!.queueInputBuffer(
            inputBufferIndex,
            0,
            0,
            presentationTimeUs.toLong(),
            MediaCodec.BUFFER_FLAG_END_OF_STREAM
        )
        writeOutputs()
    }

    /**
     * Encodes input stream
     *
     * @param inputStream
     * @param sampleRate  sample rate of input stream
     * @throws IOException
     */
    @Throws(IOException::class)
    fun encode(inputStream: InputStream, sampleRate: Int) {
        Log.d(TAG, "Starting encoding of InputStream")
        val tempBuffer = ByteArray(2 * sampleRate)
        var hasMoreData = true
        var stop = false
        while (!stop) {
            var inputBufferIndex = 0
            var currentBatchRead = 0
            while (inputBufferIndex != -1 && hasMoreData && (currentBatchRead <= 50 * sampleRate)) {
                inputBufferIndex = mediaCodec!!.dequeueInputBuffer(CODEC_TIMEOUT.toLong())
                if (inputBufferIndex >= 0) {
                    val buffer = mediaCodec!!.getInputBuffer(inputBufferIndex)
                    buffer!!.clear()
                    val bytesRead = inputStream.read(tempBuffer, 0, buffer.limit())
                    if (bytesRead == -1) {
                        mediaCodec!!.queueInputBuffer(
                            inputBufferIndex,
                            0,
                            0,
                            presentationTimeUs.toLong(),
                            0
                        )
                        hasMoreData = false
                        stop = true
                    } else {
                        totalBytesRead += bytesRead
                        currentBatchRead += bytesRead
                        buffer.put(tempBuffer, 0, bytesRead)
                        mediaCodec!!.queueInputBuffer(
                            inputBufferIndex,
                            0,
                            bytesRead,
                            presentationTimeUs.toLong(),
                            0
                        )
                        presentationTimeUs =
                            (1000000L * (totalBytesRead / 2) / sampleRate).toDouble()
                    }
                }
            }
            writeOutputs()
        }
        inputStream.close()
        Log.d(TAG, "Finished encoding of InputStream")
    }

    private fun writeOutputs() {
        var outputBufferIndex = 0
        while (outputBufferIndex != MediaCodec.INFO_TRY_AGAIN_LATER) {
            outputBufferIndex =
                mediaCodec!!.dequeueOutputBuffer(bufferInfo!!, CODEC_TIMEOUT.toLong())
            if (outputBufferIndex >= 0) {
                val encodedData = mediaCodec!!.getOutputBuffer(outputBufferIndex)
                encodedData!!.position(bufferInfo!!.offset)
                encodedData.limit(bufferInfo!!.offset + bufferInfo!!.size)
                if (bufferInfo!!.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0 && bufferInfo!!.size != 0) {
                    mediaCodec!!.releaseOutputBuffer(outputBufferIndex, false)
                } else {
                    mediaMuxer!!.writeSampleData(
                        audioTrackId,
                        mediaCodec!!.getOutputBuffer(outputBufferIndex)!!, bufferInfo!!
                    )
                    mediaCodec!!.releaseOutputBuffer(outputBufferIndex, false)
                }
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                mediaFormat = mediaCodec!!.outputFormat
                audioTrackId = mediaMuxer!!.addTrack(mediaFormat!!)
                mediaMuxer!!.start()
            }
        }
    }

    companion object {
        private const val TAG = "com.example.decodektgaudio.PCMEncoder"
        private const val COMPRESSED_AUDIO_FILE_MIME_TYPE = "audio/mp4a-latm"
        private const val CODEC_TIMEOUT = 5000
    }
}