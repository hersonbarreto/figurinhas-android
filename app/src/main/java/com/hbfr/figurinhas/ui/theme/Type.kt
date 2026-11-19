package com.hbfr.figurinhas.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Fonte do sistema (Roboto): zero KB a mais no APK. Só dois pesos fortes de destaque.
private val Sys = FontFamily.Default

val AppTypography = Typography(
    headlineMedium = TextStyle(fontFamily = Sys, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.3).sp), // "Figurinhas"
    titleLarge = TextStyle(fontFamily = Sys, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = Sys, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),   // nome do pacote
    bodyLarge = TextStyle(fontFamily = Sys, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = Sys, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Sys, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),       // "24/30"
    labelLarge = TextStyle(fontFamily = Sys, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),    // botões
    labelMedium = TextStyle(fontFamily = Sys, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp),       // selos
)
