package com.hbfr.figurinhas.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import com.hbfr.figurinhas.data.WhatsAppLimits
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

/** Posição de tudo no quadrado de 512×512 da figurinha. */
data class StickerLayout(
    val centerX: Float = SIZE / 2f,
    val centerY: Float = SIZE / 2f,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val mirrored: Boolean = false,
    val topText: String = "",
    val bottomText: String = "",
    val emoji: String = "",
    val emojiX: Float = SIZE * 0.78f,
    val emojiY: Float = SIZE * 0.78f,
    val emojiSize: Float = 150f,
) {
    companion object { const val SIZE = 512 }
}

object StickerRenderer {
    const val SIZE = StickerLayout.SIZE
    private const val MARGIN = 16f
    /** Lado máximo da foto na memória: suficiente para 512 px com zoom, leve em aparelho fraco. */
    private const val MAX_SOURCE_SIDE = 1600

    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = 0xFF000000.toInt(); strokeJoin = Paint.Join.ROUND
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textAlign = Paint.Align.CENTER
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt(); typeface = strokePaint.typeface; textAlign = Paint.Align.CENTER
    }

    /** Escala que faz a foto caber inteira no quadrado. */
    fun fitScale(bmp: Bitmap) = (SIZE - 2 * MARGIN) / max(bmp.width, bmp.height).toFloat()

    /** Lê a foto já reduzida e com a orientação certa. Chamar fora da thread principal. */
    fun decode(context: Context, uri: Uri): Bitmap {
        val src = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
            val w = info.size.width; val h = info.size.height
            val side = max(w, h)
            if (side > MAX_SOURCE_SIDE) {
                val f = MAX_SOURCE_SIDE.toFloat() / side
                decoder.setTargetSize(max(1, (w * f).toInt()), max(1, (h * f).toInt()))
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
        }
    }

    /** Desenha a figurinha no canvas, em coordenadas de 512. A tela do editor usa a mesma função. */
    fun draw(canvas: Canvas, bmp: Bitmap, l: StickerLayout) {
        canvas.save()
        canvas.translate(l.centerX, l.centerY)
        canvas.rotate(l.rotation)
        canvas.scale(if (l.mirrored) -l.scale else l.scale, l.scale)
        canvas.drawBitmap(bmp, -bmp.width / 2f, -bmp.height / 2f, imagePaint)
        canvas.restore()

        if (l.emoji.isNotBlank()) {
            emojiPaint.textSize = l.emojiSize
            canvas.drawText(l.emoji, l.emojiX, l.emojiY + l.emojiSize * 0.35f, emojiPaint)
        }
        drawMemeText(canvas, l.topText, top = true)
        drawMemeText(canvas, l.bottomText, top = false)
    }

    private fun drawMemeText(canvas: Canvas, raw: String, top: Boolean) {
        val text = raw.trim().uppercase()
        if (text.isEmpty()) return
        var size = 68f
        val maxW = SIZE - 2 * MARGIN - 8
        fillPaint.textSize = size
        while (size > 26f && fillPaint.measureText(text) > maxW) { size -= 2f; fillPaint.textSize = size }
        strokePaint.textSize = size
        strokePaint.strokeWidth = size * 0.16f
        val fm = fillPaint.fontMetrics
        val y = if (top) MARGIN - fm.ascent else SIZE - MARGIN - fm.descent
        canvas.drawText(text, SIZE / 2f, y, strokePaint)
        canvas.drawText(text, SIZE / 2f, y, fillPaint)
    }

    fun render(bmp: Bitmap, l: StickerLayout): Bitmap {
        val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        draw(Canvas(out), bmp, l)
        return out
    }

    /** WebP com transparência dentro de 100 KB, baixando a qualidade só o necessário. */
    fun encodeStaticWebP(bmp: Bitmap): ByteArray {
        var q = 92
        while (true) {
            val bytes = compress(bmp, webpFormat(), q)
            if (bytes.size <= WhatsAppLimits.STATIC_MAX_BYTES || q <= 20) return bytes
            q -= 12
        }
    }

    @Suppress("DEPRECATION")
    private fun webpFormat() =
        if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP

    private fun compress(bmp: Bitmap, f: Bitmap.CompressFormat, q: Int): ByteArray {
        val o = ByteArrayOutputStream(64 * 1024)
        bmp.compress(f, q, o)
        return o.toByteArray()
    }

    /** Ícone do pacote: PNG 96×96 até 50 KB, feito da primeira figurinha. */
    fun trayPng(stickerWebp: ByteArray): ByteArray {
        val src = BitmapFactory.decodeByteArray(stickerWebp, 0, stickerWebp.size)
        val out = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(Bitmap.createScaledBitmap(src, 96, 96, true), 0f, 0f, imagePaint)
        var bytes = compress(out, Bitmap.CompressFormat.PNG, 100)
        if (bytes.size > WhatsAppLimits.TRAY_MAX_BYTES) bytes = compress(Bitmap.createScaledBitmap(src, 96, 96, false), Bitmap.CompressFormat.PNG, 100)
        return bytes
    }

    fun clampScale(s: Float) = min(20f, max(0.05f, s))
}
