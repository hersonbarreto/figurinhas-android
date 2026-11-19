package com.hbfr.figurinhas.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Botão principal com o gradiente da marca (Galeria, Salvar, Comprar). */
@Composable
fun GradientButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val bg = if (enabled) Modifier.background(BrandBrush.Gradient) else Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
    Button(
        onClick = onClick, enabled = enabled, shape = shape,
        modifier = modifier.heightIn(min = 52.dp).clip(shape).then(bg),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White,
            disabledContainerColor = Color.Transparent, disabledContentColor = OnSurfaceVariant),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        content = content,
    )
}
