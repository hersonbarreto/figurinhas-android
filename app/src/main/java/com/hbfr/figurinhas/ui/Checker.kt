package com.hbfr.figurinhas.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.hbfr.figurinhas.ui.theme.CheckerA
import com.hbfr.figurinhas.ui.theme.CheckerB

/** Xadrez do fundo transparente, desenhado com retângulos (sem bitmap nem alpha). */
fun Modifier.checker(): Modifier = drawBehind { drawChecker(8.dp.toPx()) }

fun DrawScope.drawChecker(cell: Float) {
    drawRect(CheckerA)
    val cols = (size.width / cell).toInt() + 1
    val rows = (size.height / cell).toInt() + 1
    for (y in 0 until rows) for (x in 0 until cols) {
        if ((x + y) % 2 == 0) drawRect(CheckerB, Offset(x * cell, y * cell), Size(cell, cell))
    }
}
