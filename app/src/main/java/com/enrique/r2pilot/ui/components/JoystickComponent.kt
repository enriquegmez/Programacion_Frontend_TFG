/**
 * @file JoystickComponent.kt
 * @brief Componente visual interactivo para la teleoperación manual del robot.
 * @details Implementa un joystick virtual desde cero usando el Canvas (drawBehind) de Jetpack Compose.
 *          Captura eventos táctiles, calcula la distancia mediante trigonometría para confinar
 *          el mando dentro de un límite circular, y normaliza los resultados para inyectarlos
 *          directamente como comandos de velocidad cinemática (v, w) en ROS 2.
 * @author Enrique Gómez
 * @date 2026
 */

package com.enrique.r2pilot.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * @brief Dibuja y gestiona la lógica de un Joystick virtual en pantalla.
 * @param modifier Modificador base para aplicar márgenes o alineaciones desde el padre.
 * @param size Diámetro total del joystick (base exterior).
 * @param isEnabled Si es false, el joystick se vuelve translúcido y no emite comandos.
 * @param onVelocityChanged Callback continuo que reporta velocidades normalizadas de -1.0 a 1.0.
 */
@Composable
fun JoystickComponent(
    modifier: Modifier = Modifier,
    size: Dp = 240.dp,
    isEnabled: Boolean = true,
    onVelocityChanged: (v: Float, w: Float) -> Unit
) {
    val cs = MaterialTheme.colorScheme

    // --- CONVERSIÓN DE MEDIDAS (DP a Píxeles) ---
    // Necesario porque el Canvas de Compose trabaja estrictamente en píxeles.
    val maxRadiusPx = with(LocalDensity.current) { (size / 2).toPx() }
    val thumbRadiusPx = with(LocalDensity.current) { (size / 4.5f).toPx() }

    // Distancia máxima que el centro del "dedo" (thumb) puede alejarse del centro de la base.
    val maxDragPx = maxRadiusPx - thumbRadiusPx

    // --- ESTADOS INTERNOS (MEMORIA DEL COMPONENTE) ---
    // Posición actual del dedo respecto al centro geométrico (0,0)
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // --- ANIMACIONES (REBOTE AL SOLTAR) ---
    // Cuando el usuario suelta el dedo (dragging = false), offsetX/Y caen bruscamente a 0.
    // animateFloatAsState interpola esa caída creando un efecto visual de "muelle" que vuelve al centro.
    val animX by animateFloatAsState(targetValue = offsetX, label = "joyX")
    val animY by animateFloatAsState(targetValue = offsetY, label = "joyY")

    // --- EFECTOS SECUNDARIOS (VIGILANTE DE SEGURIDAD) ---
    // Si desde el exterior deshabilitan el joystick (ej. pérdida de conexión o pérdida del multiplexor),
    // abortamos cualquier arrastre en curso y forzamos el envío de comandos (0,0) para detener el robot.
    LaunchedEffect(isEnabled) {
        if (!isEnabled) {
            offsetX = 0f
            offsetY = 0f
            onVelocityChanged(0f, 0f)
        }
    }

    /**
     * @brief Traduce los píxeles arrastrados a valores normalizados de velocidad.
     * @details Eje Y invertido (arriba es negativo en pantalla, pero positivo en avance ROS).
     *          Eje X invertido (derecha es positivo en pantalla, pero giro antihorario negativo en ROS).
     */
    fun reportVelocity(x: Float, y: Float) {
        if (!isEnabled) return
        val v = (y / maxDragPx) * -1f
        val w = -(x / maxDragPx)
        onVelocityChanged(v, w)
    }

    Box(
        modifier = modifier
            .size(size)
            .alpha(if (isEnabled) 1f else 0.4f)
            // --- GESTIÓN DE EVENTOS TÁCTILES ---
            .pointerInput(isEnabled) {
                if (!isEnabled) return@pointerInput

                detectDragGestures(
                    onDragStart = {},
                    onDragEnd = {
                        offsetX = 0f; offsetY = 0f; reportVelocity(0f, 0f)
                    },
                    onDragCancel = {
                        offsetX = 0f; offsetY = 0f; reportVelocity(0f, 0f)
                    },
                    onDrag = { change, drag ->
                        change.consume() // Consumimos el evento para no pasarlo a listas scrolleables debajo

                        val nx = offsetX + drag.x
                        val ny = offsetY + drag.y

                        // Teorema de Pitágoras para saber a qué distancia estamos del centro
                        val dist = hypot(nx.toDouble(), ny.toDouble()).toFloat()

                        if (dist <= maxDragPx) {
                            // Dentro del límite: Movimiento libre 1:1
                            offsetX = nx
                            offsetY = ny
                        } else {
                            // Fuera del límite: Trigonometría para anclar el dedo al borde máximo
                            val a = atan2(ny.toDouble(), nx.toDouble())
                            offsetX = (cos(a) * maxDragPx).toFloat()
                            offsetY = (sin(a) * maxDragPx).toFloat()
                        }
                        reportVelocity(offsetX, offsetY)
                    }
                )
            }
            // --- DIBUJADO EN CANVAS (Z-INDEX IMPLÍCITO POR ORDEN DE CÓDIGO) ---
            .drawBehind {
                val c = Offset(this.size.width / 2f, this.size.height / 2f)
                val baseRadius = this.size.minDimension / 2f

                // CAPA 1: FONDO DE LA BASE (Degradado radial para efecto de bisel)
                drawCircle(
                    Brush.radialGradient(
                        colorStops = arrayOf(
                            0.0f to cs.surface,
                            0.4f to Color.Black.copy(alpha = 0.01f),
                            0.8f to Color.Black.copy(alpha = 0.04f),
                            1.0f to Color.Black.copy(alpha = 0.12f)
                        ),
                        center = c,
                        radius = baseRadius
                    ),
                    radius = baseRadius,
                    center = c
                )

                // CAPA 2: LÍNEA DEL LÍMITE EXTERIOR
                drawCircle(
                    color = Color.Black,
                    radius = baseRadius - 1.5f,
                    center = c,
                    style = Stroke(width = 3.5f)
                )

                val guideColor = cs.onSurface.copy(alpha = 0.15f)

                // CAPA 3: LÍNEA DISCONTINUA INTERMEDIA (Guía visual de mitad de velocidad)
                val dashedRadius = thumbRadiusPx + (baseRadius - thumbRadiusPx) / 2f
                val dashedPathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)

                drawCircle(
                    color = guideColor,
                    radius = dashedRadius,
                    center = c,
                    style = Stroke(width = 2f, pathEffect = dashedPathEffect)
                )

                // CAPA 4: EL BOTÓN MÓVIL (PULGAR)
                // Usamos animX/animY para que el Canvas dibuje el fotograma interpolado de la animación
                val thumbCenter = Offset(c.x + animX, c.y + animY)

                // 4.1 Sombra paralela del pulgar (Iteramos para crear un difuminado suave hacia abajo)
                for (i in 6 downTo 1) {
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.025f * i),
                        radius = thumbRadiusPx + i * 2f,
                        center = Offset(thumbCenter.x, thumbCenter.y + 6f)
                    )
                }

                // 4.2 Círculo base del pulgar
                drawCircle(
                    color = cs.surface,
                    radius = thumbRadiusPx,
                    center = thumbCenter
                )

                // 4.3 Anillo sutil para dar volumen al pulgar
                drawCircle(
                    color = cs.outlineVariant.copy(alpha = 0.4f),
                    radius = thumbRadiusPx,
                    center = thumbCenter,
                    style = Stroke(width = 1.5f)
                )

                // CAPA 5: EJES DIRECCIONALES (CRUZ)
                // Se dibujan AL FINAL de forma intencionada para que crucen visualmente por encima del pulgar.

                // Punto central absoluto de referencia
                drawCircle(
                    color = guideColor,
                    radius = 3f,
                    center = c
                )

                // Cálculo de huecos para que los ejes no toquen ni el centro ni los bordes exteriores
                val gapInner = 12f
                val gapOuter = 10f

                // Eje X (Izquierda)
                drawLine(
                    color = guideColor,
                    start = Offset(c.x - baseRadius + gapOuter, c.y),
                    end = Offset(c.x - gapInner, c.y),
                    strokeWidth = 2f
                )
                // Eje X (Derecha)
                drawLine(
                    color = guideColor,
                    start = Offset(c.x + gapInner, c.y),
                    end = Offset(c.x + baseRadius - gapOuter, c.y),
                    strokeWidth = 2f
                )
                // Eje Y (Arriba)
                drawLine(
                    color = guideColor,
                    start = Offset(c.x, c.y - baseRadius + gapOuter),
                    end = Offset(c.x, c.y - gapInner),
                    strokeWidth = 2f
                )
                // Eje Y (Abajo)
                drawLine(
                    color = guideColor,
                    start = Offset(c.x, c.y + gapInner),
                    end = Offset(c.x, c.y + baseRadius - gapOuter),
                    strokeWidth = 2f
                )
            }
    )
}