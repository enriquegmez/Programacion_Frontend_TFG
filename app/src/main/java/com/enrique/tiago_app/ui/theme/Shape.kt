package com.enrique.tiago_app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/* Bordes redondeados generosos y consistentes (Material 3). Las tarjetas usan
   18 dp; los botones/campos 14 dp; los chips se mantienen "pill". */
val RoboShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small      = RoundedCornerShape(12.dp),
    medium     = RoundedCornerShape(16.dp),
    large      = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/* Tokens de tamaño táctil ergonómico (uso con el pulgar, sin mirar). */
object RoboDimens {
    val touchTarget = 56.dp     // alto mínimo de botones primarios
    val sliderThumb = 28.dp     // pulgar de slider grande
    val joystickSize = 240.dp
    val cardPadding = 16.dp
    val screenPadding = 16.dp
}