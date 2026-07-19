/**
 * @file Theme.kt
 * @brief Orquestador del Sistema de Diseño y Tema Global.
 * @details Implementa la especificación Material Design 3 (M3) adaptada a un entorno
 *          de control robótico industrial.
 * @author Enrique Gómez
 * @date 2026
 */

package com.enrique.r2pilot.ui.theme

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

// ============================================================================
// ESQUEMAS DE COLOR
// ============================================================================

/**
 * @brief Mapeo de tokens para el Modo Oscuro.
 * @details Asigna la paleta "Obsidiana" a los roles semánticos de Material 3.
 */
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

/**
 * @brief Mapeo de tokens para el Modo Claro.
 * @details Asigna la paleta "Platino" a los roles semánticos de Material 3.
 */
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

// ============================================================================
// COMPONENTES PRINCIPALES
// ============================================================================

/**
 * @brief Tema principal de la aplicación.
 * @details Provee la configuración visual (Color, Tipografía y Formas) a toda
 *          la jerarquía de componentes hijos mediante CompositionLocals.
 *
 * @param darkTheme Determina si se fuerza el esquema oscuro (por defecto sigue al sistema).
 * @param dynamicColor [Falso por defecto] Evita la sobreescritura de la paleta corporativa
 *                     por los colores extraídos del fondo de pantalla del usuario.
 * @param content El árbol de UI (Composable) que se renderizará bajo este tema.
 */
@Composable
fun R2Pilot_appTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // Solo usa colores dinámicos si se exige explícitamente y el SO lo soporta (API >= 31)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkColors
        else      -> LightColors
    }

    // Integra la barra de estado superior (Status Bar)
    // del sistema operativo con el fondo de la app para una experiencia inmersiva de "consola de control".
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

/**
 * @brief Alias semántico del tema principal.
 * @details Proporciona un nombre alineado con la marca actual (R2PILOT) sin romper
 *          la compatibilidad con llamadas existentes al tema generado por defecto
 *          (R2Pilot_appTheme) al crear el proyecto en Android Studio.
 */
@Composable
fun R2PilotTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) = R2Pilot_appTheme(darkTheme, dynamicColor, content)