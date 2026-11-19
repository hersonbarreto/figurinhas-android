package com.hbfr.figurinhas.media

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import java.io.Closeable

/**
 * Lê quadros de um GIF ou vídeo, um por vez, já reduzidos.
 * Não guarda o arquivo inteiro na memória: cada chamada de [read] percorre só o trecho pedido.
 */
interface FrameReader : Closeable {
    val durationMs: Long
    /** Tamanho já com a rotação do vídeo aplicada. */
    val width: Int
    val height: Int

    /**
     * Chama [onFrame] para os instantes start, start+step, … (< start+length).
     * O bitmap é reaproveitado: copie se precisar guardar. O tempo passado é relativo ao início.
     */
    suspend fun read(startMs: Long, lengthMs: Long, stepMs: Long, maxSide: Int, onFrame: (Bitmap, Long) -> Unit)

    fun thumbnail(atMs: Long, maxSide: Int): Bitmap?

    companion object {
        /** Abre o arquivo certo, ou null se for imagem parada. Chamar fora da thread principal. */
        fun open(context: Context, uri: Uri, mime: String): FrameReader? {
            val type = mime
            return when {
                type.startsWith("video/") -> VideoReader(context, uri)
                type == "image/gif" -> GifReader.open(context, uri)
                else -> null
            }
        }

        fun fit(w: Int, h: Int, maxSide: Int): Pair<Int, Int> {
            val s = maxSide.toFloat() / maxOf(w, h)
            return if (s >= 1f) w to h else maxOf(1, (w * s).toInt()) to maxOf(1, (h * s).toInt())
        }
    }
}
