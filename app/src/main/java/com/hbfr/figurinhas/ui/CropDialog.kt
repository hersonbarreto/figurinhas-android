package com.hbfr.figurinhas.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hbfr.figurinhas.R
import com.hbfr.figurinhas.ui.theme.GradientButton
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Corte livre ou quadrado. Devolve o retângulo em frações da imagem (esquerda, topo, direita, base). */
@Composable
fun CropDialog(bitmap: Bitmap, initial: Rect, onCancel: () -> Unit, onApply: (Rect) -> Unit) {
    var crop by remember { mutableStateOf(initial) }
    var square by remember { mutableStateOf(false) }
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val aspect = bitmap.width / bitmap.height.toFloat()
    val handle = with(LocalDensity.current) { 28.dp.toPx() }

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.crop), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { crop = Rect(0f, 0f, 1f, 1f); square = false }) { Text(stringResource(R.string.reset)) }
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val boxW = constraints.maxWidth.toFloat(); val boxH = constraints.maxHeight.toFloat()
                val w = min(boxW, boxH * aspect); val h = w / aspect
                val wDp = with(LocalDensity.current) { w.toDp() }; val hDp = with(LocalDensity.current) { h.toDp() }
                Box(Modifier.size(wDp, hDp)) {
                    Image(image, null, Modifier.fillMaxSize().checker(), contentScale = ContentScale.FillBounds)
                    Canvas(Modifier.fillMaxSize().pointerInput(square) {
                        var mode = 0 // 1..4 cantos, 5 mover
                        detectDragGestures(
                            onDragStart = { p ->
                                val r = Rect(crop.left * w, crop.top * h, crop.right * w, crop.bottom * h)
                                val corners = listOf(r.topLeft, r.topRight, r.bottomLeft, r.bottomRight)
                                val i = corners.indexOfFirst { abs(it.x - p.x) < handle * 1.4f && abs(it.y - p.y) < handle * 1.4f }
                                mode = if (i >= 0) i + 1 else if (r.contains(p)) 5 else 0
                            },
                        ) { change, d ->
                            change.consume()
                            val dx = d.x / w; val dy = d.y / h
                            val minF = 0.08f
                            var (l, t, r, b) = listOf(crop.left, crop.top, crop.right, crop.bottom)
                            when (mode) {
                                5 -> {
                                    val mx = dx.coerceIn(-l, 1f - r); val my = dy.coerceIn(-t, 1f - b)
                                    l += mx; r += mx; t += my; b += my
                                }
                                1 -> { l = (l + dx).coerceIn(0f, r - minF); t = (t + dy).coerceIn(0f, b - minF) }
                                2 -> { r = (r + dx).coerceIn(l + minF, 1f); t = (t + dy).coerceIn(0f, b - minF) }
                                3 -> { l = (l + dx).coerceIn(0f, r - minF); b = (b + dy).coerceIn(t + minF, 1f) }
                                4 -> { r = (r + dx).coerceIn(l + minF, 1f); b = (b + dy).coerceIn(t + minF, 1f) }
                            }
                            if (square && mode in 1..4) {
                                // Mantém o lado em pixels igual, ajustando a altura a partir do canto oposto.
                                val side = min((r - l) * w, (b - t) * h)
                                val fw = side / w; val fh = side / h
                                when (mode) {
                                    1 -> { l = r - fw; t = b - fh }
                                    2 -> { r = l + fw; t = b - fh }
                                    3 -> { l = r - fw; b = t + fh }
                                    4 -> { r = l + fw; b = t + fh }
                                }
                            }
                            crop = Rect(l, t, r, b)
                        }
                    }) {
                        val r = Rect(crop.left * size.width, crop.top * size.height, crop.right * size.width, crop.bottom * size.height)
                        val dim = Color(0xB3000000)
                        drawRect(dim, Offset.Zero, Size(size.width, r.top))
                        drawRect(dim, Offset(0f, r.bottom), Size(size.width, size.height - r.bottom))
                        drawRect(dim, Offset(0f, r.top), Size(r.left, r.height))
                        drawRect(dim, Offset(r.right, r.top), Size(size.width - r.right, r.height))
                        drawRect(Color.White, r.topLeft, r.size, style = Stroke(2.dp.toPx()))
                        val hs = 18.dp.toPx(); val sw = 4.dp.toPx()
                        listOf(r.topLeft, r.topRight, r.bottomLeft, r.bottomRight).forEach { c ->
                            drawCircle(Color.White, radius = hs / 2.2f, center = c)
                            drawCircle(Color(0xFF7C3AED), radius = hs / 2.2f - sw / 2, center = c)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.CenterHorizontally)) {
                FilterChip(selected = !square, onClick = { square = false }, label = { Text(stringResource(R.string.crop_free)) })
                FilterChip(selected = square, onClick = {
                    square = true
                    val side = min((crop.right - crop.left) * bitmap.width, (crop.bottom - crop.top) * bitmap.height)
                    val cx = (crop.left + crop.right) / 2; val cy = (crop.top + crop.bottom) / 2
                    val fw = side / bitmap.width / 2; val fh = side / bitmap.height / 2
                    crop = Rect(max(0f, cx - fw), max(0f, cy - fh), min(1f, cx + fw), min(1f, cy + fh))
                }, label = { Text(stringResource(R.string.crop_square)) })
            }
            Spacer(Modifier.height(12.dp))
            GradientButton(onClick = { onApply(crop) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.apply), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
