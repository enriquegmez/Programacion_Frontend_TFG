/**
 * @file Color.kt
 * @brief Sistema de Diseño para la paleta "R2PILOT"·
 * @details Centraliza la paleta de colores de la aplicación garantizando una única
 *          fuente para el renderizado visual.
 * @author Enrique Gómez
 * @date 2026
 */

package com.enrique.tiago_app.ui.theme

import androidx.compose.ui.graphics.Color

/* ============================================================================
 *  ACENTOS SEMÁNTICOS Y DE MARCA (Globales para ambos temas)
 * ========================================================================== */

/** Cian neón medido. Color principal de marca, navegación y visualización de datos neutros. */
val RoboCyan        = Color(0xFF34D0DE)
/** Contenedor oscuro para elementos cian en tema Dark (ej. botones secundarios). */
val RoboCyanDeep    = Color(0xFF0B3A40)
/** Tono de alto contraste para textos renderizados sobre contenedores cian. */
val RoboCyanSoft    = Color(0xFFA7ECF3)

/** Ámbar de alerta. Indica estado ACTIVO, motores armados, o advertencia (Atención). */
val RoboAmber       = Color(0xFFF1A73C)
/** Contenedor oscuro para elementos ámbar. */
val RoboAmberDeep   = Color(0xFF402C09)

/** Verde esmeralda para el tema oscuro. Indica estado SEGURO, conexión OK o éxito. */
val RoboGreen       = Color(0xFF36D399)
/** Verde esmeralda para el tema claro (mayor contraste sobre fondos blancos). */
val RoboGreenDark   = Color(0xFF10A56B)

/** Amarillo estandarizado para métricas de batería o carga de recursos. */
val RoboCharge      = Color(0xFFF2C14E)

/** Tinta obsidiana oscura para asegurar legibilidad máxima sobre fondos cian o ámbar brillantes. */
val OnAccentInk     = Color(0xFF06141A)

/* ============================================================================
 *  TEMA OSCURO (DARK MODE - Obsidiana)
 *  [FUTURE WORK]: Paleta definida y preparada para escalabilidad.
 *  Aunque el MVP actual utiliza exclusivamente el tema claro, estos tokens
 *  se dejan implementados para facilitar la transición a un "Modo Noche"
 *  en futuras versiones sin necesidad de refactorizar la UI. Ideal para
 *  operativa del robot en entornos de baja luminosidad.
 * ========================================================================== */

/** Fondo base (Background) absoluto de la aplicación. */
val DarkBg          = Color(0xFF08090C)
/** Fondo de superficies primarias (TopBar, BottomBar, Modals). */
val DarkSurface     = Color(0xFF101218)
/** Fondo para tarjetas y contenedores de primer nivel (SurfaceVariant). */
val DarkCard        = Color(0xFF161922)
/** Fondo para tarjetas elevadas o destacadas (SurfaceContainerHigh). */
val DarkCardElev    = Color(0xFF1D212C)

/** Contorno estándar para separadores y bordes de tarjetas (Outline). */
val DarkBorder      = Color(0xFF262B36)
/** Contorno suave para divisiones internas sutiles (OutlineVariant). */
val DarkBorderSoft  = Color(0xFF1C2029)

/** Texto principal de alto contraste (OnSurface). */
val DarkText        = Color(0xFFEDEFF4)
/** Texto secundario para etiquetas, leyendas e información no crítica (OnSurfaceVariant). */
val DarkDim         = Color(0xFF8A92A3)

/** Rojo de máxima alerta. Indica parada de emergencia, error crítico o desconexión. */
val DarkDanger      = Color(0xFFFF5468)
/** Fondo para contenedores de error (ErrorContainer). */
val DarkDangerCont  = Color(0xFF3A1418)
/** Texto o iconos sobre contenedores de error (OnErrorContainer). */
val DarkOnDangerCont= Color(0xFFFFB4BD)

/* ============================================================================
 *  TEMA CLARO (LIGHT MODE - Platino)
 *  Alto contraste para entornos de alta luminosidad.
 * ========================================================================== */

/** Fondo base (Background) absoluto. */
val LightBg         = Color(0xFFEEF1F5)
/** Fondo de superficies primarias. */
val LightSurface    = Color(0xFFFFFFFF)
/** Fondo para tarjetas estándar. */
val LightCard       = Color(0xFFFFFFFF)
/** Fondo para tarjetas elevadas (efecto de profundidad). */
val LightCardElev   = Color(0xFFF6F8FB)

/** Contorno estándar para delimitación de áreas. */
val LightBorder     = Color(0xFFE2E6EC)

/** Texto principal oscuro de máxima legibilidad. */
val LightText       = Color(0xFF0F1217)
/** Texto secundario para metadatos o estados inactivos. */
val LightDim        = Color(0xFF586072)

/** Rojo de alerta ajustado para alto contraste en fondos claros. */
val LightDanger     = Color(0xFFE11D38)
/** Fondo suave para alertas no intrusivas. */
val LightDangerCont = Color(0xFFFCE4E7)
/** Texto para contenedores de alerta suave. */
val LightOnDangerC  = Color(0xFF7A1220)