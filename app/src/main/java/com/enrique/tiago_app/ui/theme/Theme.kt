package com.enrique.tiago_app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/* ============================================================================
 *  TEMA  ·  Esquemas de color "AXON Premium Tech" para Material 3
 *
 *  Decisión clave frente al código original:
 *   - dynamicColor pasa a DESACTIVADO por defecto (antes true). Material You
 *     pisaba la identidad de marca con los colores del móvil del operario,
 *     algo inaceptable en una herramienta de control industrial. Se mantiene
 *     como parámetro opcional por si se quiere ofrecer en ajustes.
 *   - Se define el set completo de roles M3 (containers, outline, error...)
 *     para que CADA @Composable existente se reestilice sin tocarlo.
 * ========================================================================== */

private val DarkColors = darkColorScheme(
    primary            = RoboCyan,
    onPrimary          = OnAccentInk,
    primaryContainer   = RoboCyanDeep,
    onPrimaryContainer = RoboCyanSoft,
    secondary          = RoboAmber,
    onSecondary        = OnAccentInk,
    secondaryContainer = RoboAmberDeep,
    onSecondaryContainer = RoboAmber,
    tertiary           = RoboGreen,
    onTertiary         = OnAccentInk,
    background         = DarkBg,
    onBackground       = DarkText,
    surface            = DarkSurface,
    onSurface          = DarkText,
    surfaceVariant     = DarkCard,
    onSurfaceVariant   = DarkDim,
    surfaceContainer       = DarkCard,
    surfaceContainerHigh   = DarkCardElev,
    surfaceContainerHighest= DarkCardElev,
    outline            = DarkBorder,
    outlineVariant     = DarkBorderSoft,
    error              = DarkDanger,
    onError            = Color.White,
    errorContainer     = DarkDangerCont,
    onErrorContainer   = DarkOnDangerCont,
)

private val LightColors = lightColorScheme(
    primary            = RoboCyan,
    onPrimary          = OnAccentInk,
    primaryContainer   = Color(0xFFCDEFF6),
    onPrimaryContainer = Color(0xFF073A44),
    secondary          = RoboAmber,
    onSecondary        = OnAccentInk,
    secondaryContainer = Color(0xFFFDEBC9),
    onSecondaryContainer = Color(0xFF4A3406),
    tertiary           = RoboGreenDark,
    onTertiary         = Color.White,
    background         = LightBg,
    onBackground       = LightText,
    surface            = LightSurface,
    onSurface          = LightText,
    surfaceVariant     = LightCard,
    onSurfaceVariant   = LightDim,
    surfaceContainer       = LightCard,
    surfaceContainerHigh   = LightCardElev,
    surfaceContainerHighest= LightCardElev,
    outline            = LightBorder,
    outlineVariant     = LightBorder,
    error              = LightDanger,
    onError            = Color.White,
    errorContainer     = LightDangerCont,
    onErrorContainer   = LightOnDangerC,
)

@Composable
fun Tiago_appTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,            // ← antes: true
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkColors
        else      -> LightColors
    }

    // Barra de estado integrada con el fondo (look "pantalla de control").
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = RoboTypography,
        shapes = RoboShapes,
        content = content
    )
}

/** Alias de marca: nombre nuevo (AXON) sin romper el call-site existente. */
@Composable
fun AxonTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) = Tiago_appTheme(darkTheme, dynamicColor, content)