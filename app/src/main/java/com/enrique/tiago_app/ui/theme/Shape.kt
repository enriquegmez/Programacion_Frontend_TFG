/**
 * @file Shape.kt
 * @brief Sistema de Formas y Dimensiones del entorno gráfico.
 * @details Centraliza los radios de borde y las métricas estructurales
 *          de la aplicación. Separa las constantes visuales de la lógica de las vistas,
 *          evitando la proliferación de "Magic Numbers" en el código base y garantizando
 *          la consistencia en toda la interfaz de usuario.
 * @author Enrique Gómez
 * @date 2026
 */

package com.enrique.tiago_app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * @brief Sistema de formas geométricas compatibles con Material Design 3.
 * @details Define bordes redondeados generosos que aportan un aspecto moderno y
 *          tecnológico. Diferencia visualmente la jerarquía de los componentes:
 *          tarjetas (18dp), botones/campos de texto (14dp) y elementos menores.
 */
val RoboShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small      = RoundedCornerShape(12.dp),
    medium     = RoundedCornerShape(16.dp),
    large      = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)
