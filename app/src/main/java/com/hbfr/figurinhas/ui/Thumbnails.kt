package com.hbfr.figurinhas.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Miniaturas em 256 px, lidas fora da thread principal e guardadas em cache pequeno. */
object Thumbnails {
    private val cache = object : LruCache<String, ImageBitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    fun get(file: File, sample: Int): ImageBitmap? {
        val key = "${file.path}#$sample"
        cache.get(key)?.let { return it }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = BitmapFactory.decodeFile(file.path, opts)?.asImageBitmap() ?: return null
        cache.put(key, bmp)
        return bmp
    }

    fun evict(file: File) {
        cache.snapshot().keys.filter { it.startsWith(file.path) }.forEach { cache.remove(it) }
    }
}

@Composable
fun rememberThumbnail(file: File, sample: Int = 2): State<ImageBitmap?> =
    produceState<ImageBitmap?>(null, file.path, sample) {
        value = withContext(Dispatchers.IO) { Thumbnails.get(file, sample) }
    }
