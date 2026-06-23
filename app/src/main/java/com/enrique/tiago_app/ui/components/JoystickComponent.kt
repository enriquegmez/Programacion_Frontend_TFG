package com.enrique.tiago_app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.compose.foundation.layout.fillMaxSize

/**
 * JoystickComponent
 * Un componente visual puro. No sabe qué es el robot ni la red.
 * Solo dibuja dos círculos y devuelve valores normalizados [-1.0f a 1.0f].
 */
@Composable
fun JoystickComponent(
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,          // Tamaño total de la base del joystick
    isEnabled: Boolean = true,  // Si es false, se vuelve gris y no se puede mover
    onVelocityChanged: (v: Float, w: Float) -> Unit // Callback que devuelve la velocidad
) {
    // Cálculo de radios en píxeles
    val maxRadiusPx = with(LocalDensity.current) { (size / 2).toPx() }
    val thumbRadiusPx = with(LocalDensity.current) { (size / 4).toPx() }
    val maxDragPx = maxRadiusPx - thumbRadiusPx // Límite para que el mando no se salga de la base

    // Estado del mando interno (offset)
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Para suavizar la vuelta al centro
    val coroutineScope = rememberCoroutineScope()

    // ==========================================
    // ¡EL RESORTE VISUAL DE SEGURIDAD!
    // ==========================================
    LaunchedEffect(isEnabled) {
        if (!isEnabled) {
            // Si el joystick se desactiva (ya sea por el usuario, por lag de red
            // o por el escudo anticolisión de Python), forzamos la bolita al centro
            offsetX = 0f
            offsetY = 0f

            // Mandamos velocidad 0 para asegurarnos de que la UI sabe que estamos parados
            onVelocityChanged(0f, 0f)
        }
    }

    // Función matemática para reportar los valores al padre
    fun reportVelocity(x: Float, y: Float) {
        if (!isEnabled) return

        // 1. Normalizamos la posición del dedo entre -1.0 y 1.0
        val normalizedX = x / maxDragPx
        val normalizedY = y / maxDragPx

        // 2. Traducción a Robótica (ROS Estándar):
        // Mover el dedo HACIA ARRIBA es -Y en la pantalla, pero queremos que sea +V (avanzar)
        val v = normalizedY * (-1)
        // Mover el dedo HACIA LA IZQUIERDA es -X en la pantalla, pero queremos que sea +W (giro izquierda)
        val w = -normalizedX

        // 3. Avisamos al ViewModel (o pantalla) que nos esté escuchando
        onVelocityChanged(v, w)
    }

    // Dibujamos la UI
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .alpha(if (isEnabled) 1f else 0.5f) // Si está deshabilitado, lo ponemos semitransparente
            .pointerInput(isEnabled) {
                if (!isEnabled) return@pointerInput

                detectDragGestures(
                    onDragStart = { /* Podríamos hacer vibrar el móvil aquí */ },
                    onDragEnd = {
                        // Cuando el usuario suelta el dedo, el joystick vuelve al centro
                        coroutineScope.launch {
                            offsetX = 0f
                            offsetY = 0f
                            reportVelocity(0f, 0f) // Mandamos velocidad 0 para parar el robot
                        }
                    },
                    onDragCancel = {
                        coroutineScope.launch {
                            offsetX = 0f
                            offsetY = 0f
                            reportVelocity(0f, 0f)
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume() // Consumimos el evento para que la pantalla no haga scroll

                        // Calculamos la nueva posición hipotética
                        val newX = offsetX + dragAmount.x
                        val newY = offsetY + dragAmount.y
                        val distance = hypot(newX.toDouble(), newY.toDouble()).toFloat()

                        // Si el dedo se sale del círculo, lo "chocamos" contra el borde
                        if (distance <= maxDragPx) {
                            offsetX = newX
                            offsetY = newY
                        } else {
                            val angle = atan2(newY.toDouble(), newX.toDouble())
                            offsetX = (cos(angle) * maxDragPx).toFloat()
                            offsetY = (sin(angle) * maxDragPx).toFloat()
                        }

                        reportVelocity(offsetX, offsetY)
                    }
                )
            }
    ) {
        // LA BASE (El círculo grande de fondo)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(Color.LightGray.copy(alpha = 0.5f))
        )

        // EL MANDO (El círculo pequeño que se mueve)
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .size(size / 2)
                .clip(CircleShape)
                .background(if (isEnabled) Color.Blue else Color.DarkGray) // Color encendido/apagado
        )
    }
}