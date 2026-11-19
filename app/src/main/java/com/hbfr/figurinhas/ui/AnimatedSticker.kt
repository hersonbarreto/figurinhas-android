package com.hbfr.figurinhas.ui

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Figurinha animada tocando pelo decodificador do sistema, já no tamanho de exibição.
 * Só existe enquanto está na tela (LazyColumn descarta as de fora), e para ao sair.
 */
@Composable
fun AnimatedSticker(file: File, sizePx: Int, modifier: Modifier = Modifier) {
    val drawable by produceState<Drawable?>(null, file.path, sizePx) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                ImageDecoder.decodeDrawable(ImageDecoder.createSource(file)) { d, _, _ -> d.setTargetSize(sizePx, sizePx) }
            }.getOrNull()
        }
    }
    AndroidView(
        factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
        modifier = modifier,
        update = { iv ->
            if (iv.drawable !== drawable) {
                (iv.drawable as? AnimatedImageDrawable)?.stop()
                iv.setImageDrawable(drawable)
                (drawable as? AnimatedImageDrawable)?.start()
            }
        },
        onRelease = { iv ->
            (iv.drawable as? AnimatedImageDrawable)?.stop()
            iv.setImageDrawable(null)
        },
    )
}
