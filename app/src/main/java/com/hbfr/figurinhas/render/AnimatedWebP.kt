package com.hbfr.figurinhas.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import com.hbfr.figurinhas.data.WhatsAppLimits
import com.hbfr.figurinhas.media.FrameReader
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * Figurinha animada: libwebp (WebPAnimEncoder) via JNI.
 * O vídeo é lido e desenhado UMA vez só; os quadros prontos (512×512) ficam num arquivo temporário,
 * e as tentativas de qualidade leem dali. Assim o celular não decodifica o vídeo de novo a cada tentativa.
 */
object AnimatedWebP {
    init { System.loadLibrary("figwebp") }

    @JvmStatic private external fun nativeCreate(w: Int, h: Int, quality: Float, method: Int): Long
    @JvmStatic private external fun nativeAdd(handle: Long, bitmap: Bitmap, timestampMs: Int): Boolean
    @JvmStatic private external fun nativeFinish(handle: Long, endMs: Int): ByteArray?
    @JvmStatic private external fun nativeRelease(handle: Long)

    /** 15 quadros por segundo, como no iPhone. */
    const val STEP_MS = 67L
    const val MAX_DURATION_MS = 10_000L
    private const val TARGET = 480 * 1024
    /** Abaixo disso a figurinha fica borrada: melhor cortar o fim (como no iPhone). */
    private const val MIN_QUALITY = 50f
    private const val METHOD = 3
    private val LADDER = listOf(88f to 1.6f, 80f to 1.3f, 72f to 1.07f, 64f to 0.92f, 57f to 0.8f, MIN_QUALITY to 0.7f)
    private const val SAMPLE_Q = 70f

    data class Result(val bytes: ByteArray, val sourceLengthMs: Long)

    /** Duração do quadro na figurinha conforme a velocidade (0,5× a 2×). */
    fun frameDuration(speed: Float) = max(20L, (STEP_MS / speed).roundToLong())

    /** Maior trecho do original que cabe em 10 s na velocidade escolhida. */
    fun maxSourceLength(speed: Float) = MAX_DURATION_MS * STEP_MS / frameDuration(speed)

    suspend fun encode(
        context: Context, reader: FrameReader, layout: StickerLayout, startMs: Long, lengthMs: Long, speed: Float,
        onProgress: (Float) -> Unit,
    ): Result? {
        val dur = frameDuration(speed)
        val wanted = max(1, (min(lengthMs, maxSourceLength(speed)) / STEP_MS).toInt())
        // Lê o original só no tamanho em que ele aparece na figurinha: nada de reduzir duas vezes.
        val shown = (max(reader.width, reader.height) * layout.scale).toInt()
        val decodeSide = shown.coerceIn(256, 1024)

        val cache = FrameCache(File(context.cacheDir, "frames-${System.nanoTime()}.raw"))
        try {
            // 1) Lê e desenha os quadros finais uma vez (0–45%).
            val size = StickerRenderer.SIZE
            val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            reader.read(startMs, wanted * STEP_MS, STEP_MS, decodeSide) { frame, _ ->
                if (cache.count >= wanted) return@read
                out.eraseColor(0)
                StickerRenderer.draw(canvas, frame, layout, reader.width)
                cache.write(out)
                onProgress(0.45f * cache.count / wanted)
            }
            out.recycle()
            var frames = cache.count
            if (frames == 0) return null

            // 2) Amostra de até 12 quadros seguidos do meio, para escolher a qualidade (45–50%).
            val k = min(frames, 12)
            val from = (frames - k) / 2
            val sample = encodeFromCache(cache, from, k, dur, SAMPLE_Q) {} ?: return null
            val perFrame = sample.size.toFloat() / k
            var q = LADDER.firstOrNull { (_, f) -> perFrame * frames * f <= TARGET }?.first
            if (q == null) {
                q = MIN_QUALITY
                frames = max(min(frames, 8), (TARGET / (perFrame * LADDER.last().second)).toInt()).coerceAtMost(frames)
            }
            onProgress(0.5f)

            // 3) Arquivo final (50–100%). Se passar de 500 KB, baixa um pouco a qualidade ou corta o fim.
            repeat(4) {
                currentCoroutineContext().ensureActive()
                val bytes = encodeFromCache(cache, 0, frames, dur, q!!) { p -> onProgress(0.5f + 0.5f * p) } ?: return null
                if (bytes.size <= WhatsAppLimits.ANIMATED_MAX_BYTES) return Result(bytes, frames * STEP_MS)
                val over = bytes.size / WhatsAppLimits.ANIMATED_MAX_BYTES.toFloat()
                if (q!! > MIN_QUALITY) q = max(MIN_QUALITY, q!! - 8f)
                else frames = max(4, (frames / over * 0.95f).toInt())
            }
            return null
        } finally {
            cache.close()
        }
    }

    private suspend fun encodeFromCache(cache: FrameCache, from: Int, count: Int, frameDur: Long, quality: Float, onProgress: (Float) -> Unit): ByteArray? {
        val size = StickerRenderer.SIZE
        val handle = nativeCreate(size, size, quality, METHOD)
        if (handle == 0L) return null
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        try {
            for (i in 0 until count) {
                currentCoroutineContext().ensureActive()
                cache.read(from + i, bmp)
                if (!nativeAdd(handle, bmp, (i * frameDur).toInt())) return null
                onProgress((i + 1) / count.toFloat())
            }
            return nativeFinish(handle, (count * frameDur).toInt())
        } finally {
            nativeRelease(handle)
            bmp.recycle()
        }
    }

    /** Quadros 512×512 crus num arquivo temporário (cerca de 1 MB cada), apagado no fim. */
    private class FrameCache(private val file: File) {
        private val frameBytes = StickerRenderer.SIZE * StickerRenderer.SIZE * 4
        private val raf = RandomAccessFile(file, "rw")
        private val buf: ByteBuffer = ByteBuffer.allocateDirect(frameBytes)
        var count = 0; private set

        fun write(bmp: Bitmap) {
            buf.clear(); bmp.copyPixelsToBuffer(buf); buf.flip()
            raf.channel.write(buf, count.toLong() * frameBytes)
            count++
        }

        fun read(i: Int, bmp: Bitmap) {
            buf.clear()
            var pos = i.toLong() * frameBytes
            while (buf.hasRemaining()) { val n = raf.channel.read(buf, pos); if (n <= 0) break; pos += n }
            buf.flip(); bmp.copyPixelsFromBuffer(buf)
        }

        fun close() { runCatching { raf.close() }; file.delete() }
    }
}
