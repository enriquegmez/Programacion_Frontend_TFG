/**
 * @file Type.kt
 * @brief Sistema Tipográfico para la interfaz.
 * @details Define la jerarquía de textos, fuentes y estilos de la aplicación.
 *          Sustituye la tipografía por defecto de Material 3 para mejorar la legibilidad
 *          en un entorno de control robótico. Introduce un manejo dual de fuentes:
 *          Sans-serif para la interfaz de usuario estructural y Monospace para la
 *          lectura precisa de telemetría en tiempo real.
 * @author Enrique Gómez
 * @date 2026
 */

package com.enrique.tiago_app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.enrique.tiago_app.R

// ============================================================================
// FAMILIAS TIPOGRÁFICAS
// ============================================================================

/**
 * @brief Fuente principal de la interfaz (Manrope).
 * @details Tipografía Sans-serif geométrica optimizada para interfaces técnicas.
 *          Garantiza una alta legibilidad en pantallas de distintas resoluciones,
 *          evitando la fatiga visual al leer etiquetas y menús del sistema.
 */
val RoboSans = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_bold, FontWeight.Bold)
)

/**
 * @brief Fuente monoespaciada (Monospace) nativa del sistema.
 * @details Decisión de diseño crítico para telemetría: Al tener caracteres de ancho fijo,
 *          evita el efecto de "temblor" visual cuando los valores de los sensores,
 *          coordenadas o velocidades se actualizan a alta frecuencia.
 */
val RoboMono = FontFamily.Monospace

// ============================================================================
// ESCALAS TIPOGRÁFICAS MATERIAL 3
// ============================================================================

/**
 * @brief Diccionario de estilos estándar para componentes de la UI.
 * @details Sobreescribe la configuración base de Jetpack Compose para inyectar
 *          la fuente [RoboSans] y ajustar los tamaños/pesos a las necesidades
 *          ergonómicas de la consola de teleoperación.
 */
val RoboTypography = Typography(
    displaySmall   = TextStyle(fontFamily = RoboSans, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp),
    headlineMedium = TextStyle(fontFamily = RoboSans, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp),
    titleLarge     = TextStyle(fontFamily = RoboSans, fontWeight = FontWeight.Bold,       fontSize = 18.sp),
    titleMedium    = TextStyle(fontFamily = RoboSans, fontWeight = FontWeight.Bold,       fontSize = 15.sp),
    bodyLarge      = TextStyle(fontFamily = RoboSans, fontWeight = FontWeight.Normal,     fontSize = 15.sp),
    labelSmall     = TextStyle(fontFamily = RoboSans, fontWeight = FontWeight.SemiBold,   fontSize = 11.sp)
)

// ============================================================================
// ESTILOS ESPECÍFICOS DE DOMINIO
// ============================================================================

/**
 * @brief Estilo para la visualización de datos numéricos en tiempo real (Ej: "1.45 m/s").
 * @details Emplea fuente monoespaciada con un tamaño generoso para lecturas rápidas.
 */
val MonoData = TextStyle(fontFamily = RoboMono, fontWeight = FontWeight.Bold, fontSize = 18.sp)

/**
 * @brief Estilo para metadatos o unidades asociadas a la telemetría (Ej: "RAD/S", "EJE X").
 */
val MonoLabel = TextStyle(fontFamily = RoboMono, fontWeight = FontWeight.Medium, fontSize = 11.sp)