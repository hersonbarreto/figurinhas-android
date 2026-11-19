package com.hbfr.figurinhas.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Movie
import android.net.Uri
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** GIF lido com [Movie], que desenha qualquer instante sem decodificar tudo de uma vez. */
@Suppress("DEPRECATION")
class GifReader private constructor(private val movie: Movie) : FrameReader {
    private val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG)
    override val durationMs = movie.duration().toLong()
    override val width = movie.width()
    override val height = movie.height()

    @Synchronized
    private fun drawAt(t: Long, bmp: Bitmap) {
        bmp.eraseColor(0)
        val c = Canvas(bmp)
        c.scale(bmp.width / width.toFloat(), bmp.height / height.toFloat())
        movie.setTime((t % durationMs).toInt())
        movie.draw(c, 0f, 0f, paint)
    }

    override suspend fun read(startMs: Long, lengthMs: Long, stepMs: Long, maxSide: Int, onFrame: (Bitmap, Long) -> Unit) {
        val (w, h) = FrameReader.fit(width, height, maxSide)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        var t = 0L
        while (t < lengthMs) {
            currentCoroutineContext().ensureActive()
            drawAt(startMs + t, bmp)
            onFrame(bmp, t)
            t += stepMs
        }
        bmp.recycle()
    }

    override fun thumbnail(atMs: Long, maxSide: Int): Bitmap {
        val (w, h) = FrameReader.fit(width, height, maxSide)
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { drawAt(atMs, it) }
    }

    override fun close() {}

    companion object {
        /** null se o GIF tiver um quadro só (vira figurinha parada). */
        fun open(context: Context, uri: Uri): GifReader? {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            val m = Movie.decodeByteArray(bytes, 0, bytes.size) ?: return null
            if (m.duration() <= 0 || m.width() <= 0) return null
            return GifReader(m)
        }
    }
}
