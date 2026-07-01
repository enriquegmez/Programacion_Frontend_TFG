package com.enrique.tiago_app.ui.theme

import androidx.compose.ui.graphics.Color

/* ============================================================================
 *  PALETA "AXON · PREMIUM TECH"  ·  Rediseño UI/UX (control robótico universal)
 *  Sustituye a la antigua paleta morada de plantilla y a la "industrial" previa.
 *
 *  Filosofía de color (sobriedad + significado):
 *   - Neutros obsidiana / grafito  -> fondos y superficies (saturación casi nula,
 *     pensados para sesiones largas en sala de control sin fatiga visual).
 *   - Cian neón medido  -> color de marca: navegación, datos e información.
 *   - Esmeralda          -> conectado / seguro / OK.
 *   - Ámbar              -> estado ACTIVO / armado / atención (motores, giro).
 *   - Rojo               -> peligro, parada, error (máximo contraste).
 *  El acento sólo aparece para destacar estado: el cromatismo comunica, no decora.
 *
 *  Nombres de val conservados (Robo*) para no romper Theme.kt ni otros call-sites.
 * ========================================================================== */

// ---- Acentos de marca (compartidos por ambos temas) ----
val RoboCyan        = Color(0xFF34D0DE)   // cian neón medido — marca / navegación
val RoboCyanDeep    = Color(0xFF0B3A40)   // contenedor cian en dark
val RoboCyanSoft    = Color(0xFFA7ECF3)   // texto sobre contenedor cian
val RoboAmber       = Color(0xFFF1A73C)   // armado / activo
val RoboAmberDeep   = Color(0xFF402C09)
val RoboGreen       = Color(0xFF36D399)   // conectado / seguro (dark)
val RoboGreenDark   = Color(0xFF10A56B)   // conectado / seguro (light)
val RoboCharge      = Color(0xFFF2C14E)
val OnAccentInk     = Color(0xFF06141A)   // texto oscuro legible sobre cian/ámbar

// ---- DARK (Obsidiana) ----
val DarkBg          = Color(0xFF08090C)
val DarkSurface     = Color(0xFF101218)
val DarkCard        = Color(0xFF161922)   // surfaceVariant
val DarkCardElev    = Color(0xFF1D212C)   // surfaceContainerHigh
val DarkBorder      = Color(0xFF262B36)   // outline
val DarkBorderSoft  = Color(0xFF1C2029)   // outlineVariant
val DarkText        = Color(0xFFEDEFF4)   // onSurface
val DarkDim         = Color(0xFF8A92A3)   // onSurfaceVariant
val DarkDanger      = Color(0xFFFF5468)
val DarkDangerCont  = Color(0xFF3A1418)
val DarkOnDangerCont= Color(0xFFFFB4BD)

// ---- LIGHT (Platino) ----
val LightBg         = Color(0xFFEEF1F5)
val LightSurface    = Color(0xFFFFFFFF)
val LightCard       = Color(0xFFFFFFFF)
val LightCardElev   = Color(0xFFF6F8FB)
val LightBorder     = Color(0xFFE2E6EC)
val LightText       = Color(0xFF0F1217)
val LightDim        = Color(0xFF586072)
val LightDanger     = Color(0xFFE11D38)
val LightDangerCont = Color(0xFFFCE4E7)
val LightOnDangerC  = Color(0xFF7A1220)