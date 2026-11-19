package com.hbfr.figurinhas.render

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.sqrt

object BackgroundRemover {
    /**
     * Recorte automático do assunto (pessoa, bicho, objeto) com o ML Kit do Google, no próprio celular.
     * O modelo vem pelo Google Play Services, sem pesar no APK. Devolve null se não achar assunto.
     */
    suspend fun liftSubject(src: Bitmap): Bitmap? {
        val options = SubjectSegmenterOptions.Builder().enableForegroundBitmap().build()
        val client = SubjectSegmentation.getClient(options)
        return try {
            suspendCancellableCoroutine { cont ->
                client.process(InputImage.fromBitmap(src, 0))
                    .addOnSuccessListener { cont.resume(it.foregroundBitmap) }
                    .addOnFailureListener { cont.resumeWithException(it) }
            }
        } finally {
            client.close()
        }
    }

    /** Apaga um fundo de cor única a partir das bordas, com borda suave. Mesma lógica do iPhone. */
    fun removeSolidBackground(src: Bitmap, tolerance: Float): Bitmap {
        val w = src.width; val h = src.height; val n = w * h
        val px = IntArray(n)
        src.getPixels(px, 0, w, 0, 0, w, h)  // ARGB sem pré-multiplicação
        // Cor do fundo: média das bordas.
        var r = 0L; var g = 0L; var b = 0L; var cnt = 0
        fun acc(p: Int) { val c = px[p]; if ((c ushr 24) > 20) { r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF; cnt++ } }
        for (x in 0 until w) { acc(x); acc((h - 1) * w + x) }
        for (y in 0 until h) { acc(y * w); acc(y * w + w - 1) }
        if (cnt == 0) return src
        val br = r / cnt.toFloat(); val bg = g / cnt.toFloat(); val bb = b / cnt.toFloat()

        val dist = FloatArray(n)
        for (p in 0 until n) {
            val c = px[p]
            val dr = ((c shr 16) and 0xFF) - br; val dg = ((c shr 8) and 0xFF) - bg; val db = (c and 0xFF) - bb
            dist[p] = sqrt(dr * dr + dg * dg + db * db)
        }
        val gone = BooleanArray(n)
        val stack = IntArray(n)
        var top = 0
        fun seed(p: Int) {
            if (!gone[p] && (dist[p] < tolerance || (px[p] ushr 24) < 20)) { gone[p] = true; stack[top++] = p }
        }
        for (x in 0 until w) { seed(x); seed((h - 1) * w + x) }
        for (y in 0 until h) { seed(y * w); seed(y * w + w - 1) }
        while (top > 0) {
            val p = stack[--top]
            val x = p % w; val y = p / w
            if (x > 0) seed(p - 1)
            if (x < w - 1) seed(p + 1)
            if (y > 0) seed(p - w)
            if (y < h - 1) seed(p + w)
        }
        val soft = tolerance * 0.8f
        for (p in 0 until n) {
            if (gone[p]) { px[p] = 0; continue }
            val x = p % w; val y = p / w
            val edge = (x > 0 && gone[p - 1]) || (x < w - 1 && gone[p + 1]) || (y > 0 && gone[p - w]) || (y < h - 1 && gone[p + w])
            if (edge) {
                val a = ((dist[p] - tolerance) / soft).coerceIn(0f, 1f)
                val alpha = ((px[p] ushr 24) * a).toInt()
                px[p] = (alpha shl 24) or (px[p] and 0xFFFFFF)
            }
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }
}
