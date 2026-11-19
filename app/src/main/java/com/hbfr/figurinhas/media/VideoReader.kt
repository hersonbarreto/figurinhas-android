package com.hbfr.figurinhas.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Vídeo lido em sequência com MediaCodec (rápido, sem pular de quadro-chave em quadro-chave).
 * A conversão YUV→RGB (em C) já reduz com média e gira, então só os pixels finais são calculados.
 */
class VideoReader(private val context: Context, private val uri: Uri) : FrameReader {
    private val retriever = MediaMetadataRetriever().apply { setDataSource(context, uri) }
    private val rotation: Int
    override val durationMs: Long
    override val width: Int
    override val height: Int

    init {
        fun meta(k: Int) = retriever.extractMetadata(k)?.toIntOrNull() ?: 0
        durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        rotation = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
        val w = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
        val h = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
        if (rotation == 90 || rotation == 270) { width = h; height = w } else { width = w; height = h }
    }

    override fun thumbnail(atMs: Long, maxSide: Int): Bitmap? = runCatching {
        synchronized(retriever) {
            retriever.getScaledFrameAtTime(atMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, maxSide, maxSide)
        }
    }.getOrNull()

    override suspend fun read(startMs: Long, lengthMs: Long, stepMs: Long, maxSide: Int, onFrame: (Bitmap, Long) -> Unit) {
        val ex = MediaExtractor()
        var codec: MediaCodec? = null
        val (tw, th) = FrameReader.fit(width, height, maxSide)
        val bmp = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
        try {
            ex.setDataSource(context, uri, null)
            val track = (0 until ex.trackCount).first { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }
            ex.selectTrack(track)
            val fmt = ex.getTrackFormat(track)
            fmt.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            val c = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
            codec = c
            c.configure(fmt, null, null, 0)
            c.start()
            ex.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            val info = MediaCodec.BufferInfo()
            val end = startMs + lengthMs
            var next = startMs
            var inputDone = false
            while (next < end) {
                currentCoroutineContext().ensureActive()
                if (!inputDone) {
                    val i = c.dequeueInputBuffer(5_000)
                    if (i >= 0) {
                        val buf = c.getInputBuffer(i)!!
                        val n = ex.readSampleData(buf, 0)
                        if (n < 0 || ex.sampleTime > (end + 1000) * 1000) {
                            c.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            c.queueInputBuffer(i, 0, n, ex.sampleTime, 0)
                            ex.advance()
                        }
                    }
                }
                val o = c.dequeueOutputBuffer(info, 5_000)
                if (o >= 0) {
                    val pts = info.presentationTimeUs / 1000
                    val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    if (info.size > 0 && pts >= next) {
                        val img = c.getOutputImage(o)
                        if (img != null) {
                            val p = img.planes
                            val cr = img.cropRect
                            NativeImage.yuvToBitmap(p[0].buffer, p[1].buffer, p[2].buffer,
                                p[0].rowStride, p[0].pixelStride, p[1].rowStride, p[1].pixelStride, p[2].rowStride, p[2].pixelStride,
                                cr.left, cr.top, cr.width(), cr.height(), rotation, bmp)
                            img.close()
                            while (next <= pts && next < end) {
                                onFrame(bmp, next - startMs)
                                next += stepMs
                            }
                        }
                    }
                    c.releaseOutputBuffer(o, false)
                    if (eos) break
                }
            }
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            ex.release()
            bmp.recycle()
        }
    }

    override fun close() { runCatching { retriever.release() } }
}
