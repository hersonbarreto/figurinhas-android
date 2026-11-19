package com.hbfr.figurinhas.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Marca (as mesmas cores do iOS, Theme/Brand.swift)
val Violet = Color(0xFFA78BFA)       // destaque claro, ícones e links
val LightViolet = Color(0xFFC4B5FD)
val Cyan = Color(0xFF67E8F9)
val DeepViolet = Color(0xFF7C3AED)   // início do gradiente
val DeepCyan = Color(0xFF0891B2)     // fim do gradiente
val GlowPink = Color(0xFFEC4899)
val Ink = Color(0xFF0A0A14)

val WhatsApp = Color(0xFF16A34A)
val WhatsAppLight = Color(0xFF22C55E)
val WhatsAppDark = Color(0xFF15803D)

// Superfícies opacas (no lugar do vidro do iOS). Escada do mais fundo ao mais alto.
val Background = Color(0xFF0F0F1E)
val SurfaceLowest = Color(0xFF0A0A14)
val SurfaceLow = Color(0xFF15152A)
val SurfaceMid = Color(0xFF1B1B33)      // cartões (pacotes, painéis do editor)
val SurfaceHigh = Color(0xFF23233F)     // botões tonais, campos
val SurfaceHighest = Color(0xFF2C2C4A)  // chips selecionados, trilho de slider
val OnSurface = Color(0xFFF1F0FA)
val OnSurfaceVariant = Color(0xFFB4B2C9)
val Outline = Color(0xFF4A4866)
val OutlineVariant = Color(0xFF2E2D45)

// Xadrez do fundo transparente das figurinhas
val CheckerA = Color(0xFF1C1C30)
val CheckerB = Color(0xFF26263D)

object BrandBrush {
    /** Botão principal (Galeria, Salvar, Comprar). Desenhado só em componentes pequenos. */
    val Gradient = Brush.linearGradient(listOf(DeepViolet, DeepCyan))
    /** Texto e selos de destaque. */
    val LightGradient = Brush.linearGradient(listOf(Violet, Cyan))
    val WhatsAppGradient = Brush.verticalGradient(listOf(WhatsAppLight, WhatsAppDark))
}
