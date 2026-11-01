package com.megapro.labyrinth.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.nio.ByteOrder

/**
 * Decodes a shipped recording (Ogg Vorbis in res/raw) into [out], 16-bit stereo samples at [sr],
 * exactly [frames] long, so it loops as seamlessly as the original. [progress] is told how many
 * samples are ready as they arrive, so playing can begin long before the end. False if this device
 * cannot decode it, or the recording is not what it should be (the caller then synthesizes it), or
 * if [stop] says it is not wanted any more.
 */
object Recording {
  fun decode(context: Context, res: Int, sr: Int, frames: Int, out: ShortArray = ShortArray(frames * 2), stop: () -> Boolean = { false }, progress: (Int) -> Unit = {}): Boolean {
    var n = 0
    val extractor = MediaExtractor()
    var codec: MediaCodec? = null
    try {
      context.resources.openRawResourceFd(res).use { fd -> extractor.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length) }
      if (extractor.trackCount < 1) return false
      extractor.selectTrack(0)
      val format = extractor.getTrackFormat(0)
      if (format.getInteger(MediaFormat.KEY_SAMPLE_RATE) != sr || format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) != 2) return false
      val c = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME) ?: return false)
      codec = c
      c.configure(format, null, null, 0)
      c.start()
      val info = MediaCodec.BufferInfo()
      var inputDone = false
      var floats = false
      var outputDone = false
      // Fed and drained without waiting: a three-minute piece is thousands of small packets, and a
      // pause on each would add up to seconds. Only when there is nothing to do is there a short wait.
      while (!outputDone) {
        if (stop()) return false
        var fed = false
        while (!inputDone) {
          val i = c.dequeueInputBuffer(0)
          if (i < 0) break
          val buf = c.getInputBuffer(i) ?: return false
          val size = extractor.readSampleData(buf, 0)
          if (size < 0) { c.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true }
          else { c.queueInputBuffer(i, 0, size, extractor.sampleTime, 0); extractor.advance() }
          fed = true
        }
        var o = c.dequeueOutputBuffer(info, if (fed) 0L else 2_000L)
        while (o != MediaCodec.INFO_TRY_AGAIN_LATER) {
          if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            val f = c.outputFormat
            if (f.getInteger(MediaFormat.KEY_CHANNEL_COUNT) != 2) return false
            floats = f.containsKey(MediaFormat.KEY_PCM_ENCODING) && f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
          } else if (o >= 0) {
            val buf = c.getOutputBuffer(o)
            if (buf != null && info.size > 0) {
              buf.position(info.offset); buf.limit(info.offset + info.size)
              buf.order(ByteOrder.nativeOrder())
              if (floats) {
                val fb = buf.asFloatBuffer()
                while (fb.hasRemaining() && n < out.size) out[n++] = (fb.get().coerceIn(-1f, 1f) * 32767f).toInt().toShort()
              } else {
                val sb = buf.asShortBuffer()
                val take = minOf(sb.remaining(), out.size - n)
                sb.get(out, n, take); n += take
              }
            }
            c.releaseOutputBuffer(o, false)
            progress(n)
            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0 || n >= out.size) { outputDone = true; break }
          }
          o = c.dequeueOutputBuffer(info, 0L)
        }
      }
      // A recording more than a twentieth of a second short is not the right one
      return n >= out.size - sr / 20 * 2 // any last missing samples stay silent
    } catch (_: Exception) {
      return false
    } finally {
      try { codec?.stop() } catch (_: Exception) {}
      try { codec?.release() } catch (_: Exception) {}
      extractor.release()
    }
  }
}
