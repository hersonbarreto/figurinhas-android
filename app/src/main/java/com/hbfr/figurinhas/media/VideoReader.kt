package com.hbfr.figurinhas.media

import android.content.Context
import android.graphics.Bitmap
import android.media.Image
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
 * A conversão YUV→RGB já reduz e gira, então só os pixels finais são calculados.
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
        val pixels = IntArray(tw * th)
        var map: YuvMap? = null
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
                            val m = map ?: YuvMap(img, rotation, tw, th).also { map = it }
                            m.convert(img, pixels)
                            img.close()
                            bmp.setPixels(pixels, 0, tw, 0, 0, tw, th)
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

    /** Para cada pixel de saída, onde ler Y, U e V na imagem do decodificador. Calculado uma vez. */
    private class YuvMap(img: Image, rotation: Int, tw: Int, th: Int) {
        private val yIdx = IntArray(tw * th)
        private val uIdx = IntArray(tw * th)
        private val vIdx = IntArray(tw * th)

        init {
            val crop = img.cropRect
            val sw = crop.width(); val sh = crop.height()
            val p = img.planes
            val rotated = rotation == 90 || rotation == 270
            val rw = if (rotated) sh else sw
            val rh = if (rotated) sw else sh
            var i = 0
            for (y in 0 until th) {
                val ry = (y * rh / th).coerceIn(0, rh - 1)
                for (x in 0 until tw) {
                    val rx = (x * rw / tw).coerceIn(0, rw - 1)
                    val (sx0, sy0) = when (rotation) {
                        90 -> ry to (sh - 1 - rx)
                        180 -> (sw - 1 - rx) to (sh - 1 - ry)
                        270 -> (sw - 1 - ry) to rx
                        else -> rx to ry
                    }
                    val sx = sx0 + crop.left; val sy = sy0 + crop.top
                    yIdx[i] = sy * p[0].rowStride + sx * p[0].pixelStride
                    uIdx[i] = (sy / 2) * p[1].rowStride + (sx / 2) * p[1].pixelStride
                    vIdx[i] = (sy / 2) * p[2].rowStride + (sx / 2) * p[2].pixelStride
                    i++
                }
            }
        }

        fun convert(img: Image, out: IntArray) {
            val yb = img.planes[0].buffer; val ub = img.planes[1].buffer; val vb = img.planes[2].buffer
            for (i in out.indices) {
                val c = (yb.get(yIdx[i]).toInt() and 0xFF) - 16
                val d = (ub.get(uIdx[i]).toInt() and 0xFF) - 128
                val e = (vb.get(vIdx[i]).toInt() and 0xFF) - 128
                val c2 = 298 * maxOf(c, 0)
                val r = ((c2 + 409 * e + 128) shr 8).coerceIn(0, 255)
                val g = ((c2 - 100 * d - 208 * e + 128) shr 8).coerceIn(0, 255)
                val b = ((c2 + 516 * d + 128) shr 8).coerceIn(0, 255)
                out[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
    }
}
