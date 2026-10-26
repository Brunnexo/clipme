package com.bnx.clipme

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.io.OutputStream
import java.nio.ByteOrder

/** Decodes the first audio track of any file Android can read (m4a, webm/opus, mp4 with video) and writes it as MP3. */
object AudioConverter {
    fun toMp3(input: File, out: OutputStream, onProgress: (Int) -> Unit) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(input.absolutePath)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error(tr(R.string.err_audio_none))
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
            codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(format, null, null, 0)
            codec.start()

            var encoder: Mp3Encoder? = null
            var outFormat = codec.outputFormat
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var lastPct = -1
            var pcm = ShortArray(0)
            while (!outputDone) {
                if (!inputDone) {
                    val i = codec.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val n = extractor.readSampleData(codec.getInputBuffer(i)!!, 0)
                        if (n < 0) {
                            codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(i, 0, n, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> outFormat = codec.outputFormat
                    o >= 0 -> {
                        if (info.size > 0) {
                            val channels = outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            val enc = encoder ?: Mp3Encoder(out, outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE), channels.coerceAtMost(2))
                                .also { encoder = it }
                            val buf = codec.getOutputBuffer(o)!!.order(ByteOrder.nativeOrder())
                            buf.position(info.offset)
                            buf.limit(info.offset + info.size)
                            val float = outFormat.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                                outFormat.getInteger(MediaFormat.KEY_PCM_ENCODING) == 4 // ENCODING_PCM_FLOAT
                            val samples = info.size / (if (float) 4 else 2)
                            if (pcm.size < samples) pcm = ShortArray(samples)
                            if (float) {
                                val fb = buf.asFloatBuffer()
                                for (k in 0 until samples) pcm[k] = (fb.get().coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                            } else buf.asShortBuffer().get(pcm, 0, samples)
                            if (channels > 2) { // keep the first two channels
                                val frames = samples / channels
                                for (f in 0 until frames) { pcm[f * 2] = pcm[f * channels]; pcm[f * 2 + 1] = pcm[f * channels + 1] }
                                enc.write(pcm, frames)
                            } else enc.write(pcm, samples / channels)
                        }
                        codec.releaseOutputBuffer(o, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                        if (durationUs > 0) {
                            val pct = (info.presentationTimeUs * 100 / durationUs).toInt().coerceIn(0, 100)
                            if (pct != lastPct) { lastPct = pct; onProgress(pct) }
                        }
                    }
                }
            }
            (encoder ?: error(tr(R.string.err_audio_convert))).finish()
        } finally {
            runCatching { codec?.stop() }
            codec?.release()
            extractor.release()
        }
    }
}