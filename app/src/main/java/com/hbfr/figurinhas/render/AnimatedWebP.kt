package com.hbfr.figurinhas.render

import android.graphics.Bitmap
import android.graphics.Canvas
import com.hbfr.figurinhas.data.WhatsAppLimits
import com.hbfr.figurinhas.media.FrameReader
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/** Figurinha animada: libwebp (WebPAnimEncoder) com quadros enviados um por vez. */
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
    private const val SOURCE_SIDE = 720

    data class Result(val bytes: ByteArray, val sourceLengthMs: Long)

    /** Duração do quadro na figurinha conforme a velocidade (0,5× a 2×). */
    fun frameDuration(speed: Float) = max(20L, (STEP_MS / speed).roundToLong())

    /** Maior trecho do original que cabe em 10 s na velocidade escolhida. */
    fun maxSourceLength(speed: Float) = MAX_DURATION_MS * STEP_MS / frameDuration(speed)

    /**
     * Gera o WebP dentro de 500 KB. Mede alguns quadros primeiro para escolher a qualidade;
     * só se nem a qualidade mínima couber é que corta o fim do trecho (como no iPhone).
     */
    suspend fun encode(
        reader: FrameReader, layout: StickerLayout, startMs: Long, lengthMs: Long, speed: Float,
        onProgress: (Float) -> Unit,
    ): Result? {
        val dur = frameDuration(speed)
        var frames = max(1, (min(lengthMs, maxSourceLength(speed)) / STEP_MS).toInt())

        // Amostra: os primeiros quadros com qualidade 60.
        val sampleN = min(frames, 10)
        val sample = encodePass(reader, layout, startMs, sampleN, dur, 60f) { } ?: return null
        val perFrame = sample.size.toFloat() / sampleN
        val predicted = perFrame * frames
        var q = when {
            predicted * 1.35f <= TARGET -> 80f
            predicted <= TARGET -> 60f
            predicted * 0.75f <= TARGET -> 45f
            else -> 32f
        }
        if (q == 32f && predicted * 0.6f > TARGET) {
            frames = max(sampleN.coerceAtMost(8), (TARGET / (perFrame * 0.6f)).toInt())
        }

        repeat(4) { attempt ->
            val bytes = encodePass(reader, layout, startMs, frames, dur, q) { p ->
                onProgress(((attempt + p) / (attempt + 1)).coerceAtMost(1f))
            } ?: return null
            if (bytes.size <= WhatsAppLimits.ANIMATED_MAX_BYTES) return Result(bytes, frames * STEP_MS)
            if (q > 30f) q = max(25f, q - 15f) else frames = max(4, (frames * 0.8f).toInt())
        }
        return null
    }

    private suspend fun encodePass(
        reader: FrameReader, layout: StickerLayout, startMs: Long, frames: Int, frameDur: Long, quality: Float,
        onProgress: (Float) -> Unit,
    ): ByteArray? {
        val size = StickerRenderer.SIZE
        val handle = nativeCreate(size, size, quality, 4)
        if (handle == 0L) return null
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        try {
            var i = 0
            var ok = true
            reader.read(startMs, frames * STEP_MS, STEP_MS, SOURCE_SIDE) { frame, _ ->
                if (!ok || i >= frames) return@read
                out.eraseColor(0)
                StickerRenderer.draw(canvas, frame, layout, reader.width)
                ok = nativeAdd(handle, out, (i * frameDur).toInt())
                i++
                onProgress(i / frames.toFloat())
            }
            if (!ok || i == 0) return null
            return nativeFinish(handle, (i * frameDur).toInt())
        } finally {
            nativeRelease(handle)
            out.recycle()
        }
    }
}
