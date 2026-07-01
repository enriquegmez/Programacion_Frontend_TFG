package com.enrique.tiago_app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.enrique.tiago_app.R // Asegúrate de que esto apunta a tu R

val RoboSans = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_bold, FontWeight.Bold)
)

val RoboMono = FontFamily.Monospace

val RoboTypography = Typography(
    displaySmall = TextStyle(fontFamily = RoboSans, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp),
    headlineMedium = TextStyle(fontFamily = RoboSans, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp),
    titleLarge = TextStyle(fontFamily = RoboSans, fontWeight = FontWeight.Bold, fontSize = 18.sp),
    titleMedium = TextStyle(fontFamily = RoboSans, fontWeight = FontWeight.Bold, fontSize = 15.sp),
    bodyLarge = TextStyle(fontFamily = RoboSans, fontWeight = FontWeight.Normal, fontSize = 15.sp),
    labelSmall = TextStyle(fontFamily = RoboSans, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
)

val MonoData = TextStyle(fontFamily = RoboMono, fontWeight = FontWeight.Bold, fontSize = 18.sp)
val MonoLabel = TextStyle(fontFamily = RoboMono, fontWeight = FontWeight.Medium, fontSize = 11.sp)