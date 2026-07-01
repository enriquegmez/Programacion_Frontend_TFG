package com.enrique.tiago_app.ui.components

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

@Composable
fun JoystickComponent(
    modifier: Modifier = Modifier,
    size: Dp = 240.dp,
    isEnabled: Boolean = true,
    onVelocityChanged: (v: Float, w: Float) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val maxRadiusPx = with(LocalDensity.current) { (size / 2).toPx() }
    val thumbRadiusPx = with(LocalDensity.current) { (size / 4.5f).toPx() }
    val maxDragPx = maxRadiusPx - thumbRadiusPx

    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }

    val animX by animateFloatAsState(if (dragging) offsetX else 0f, label = "joyX")
    val animY by animateFloatAsState(if (dragging) offsetY else 0f, label = "joyY")

    LaunchedEffect(isEnabled) {
        if (!isEnabled) {
            dragging = false; offsetX = 0f; offsetY = 0f
            onVelocityChanged(0f, 0f)
        }
    }

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
            .pointerInput(isEnabled) {
                if (!isEnabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false; offsetX = 0f; offsetY = 0f; reportVelocity(0f, 0f) },
                    onDragCancel = { dragging = false; offsetX = 0f; offsetY = 0f; reportVelocity(0f, 0f) },
                    onDrag = { change, drag ->
                        change.consume()
                        val nx = offsetX + drag.x
                        val ny = offsetY + drag.y
                        val dist = hypot(nx.toDouble(), ny.toDouble()).toFloat()
                        if (dist <= maxDragPx) { offsetX = nx; offsetY = ny }
                        else {
                            val a = atan2(ny.toDouble(), nx.toDouble())
                            offsetX = (cos(a) * maxDragPx).toFloat()
                            offsetY = (sin(a) * maxDragPx).toFloat()
                        }
                        reportVelocity(offsetX, offsetY)
                    }
                )
            }
            .drawBehind {
                val c = Offset(this.size.width / 2f, this.size.height / 2f)
                val baseRadius = this.size.minDimension / 2f

                // 1. Sombreado muy suave por toda la base (se oscurece sutilmente hacia el borde)
                drawCircle(
                    Brush.radialGradient(
                        colorStops = arrayOf(
                            0.0f to cs.surface,
                            0.4f to Color.Black.copy(alpha = 0.01f), // Sombra casi imperceptible en el medio
                            0.8f to Color.Black.copy(alpha = 0.04f),
                            1.0f to Color.Black.copy(alpha = 0.12f)  // Borde oscurecido
                        ),
                        center = c,
                        radius = baseRadius
                    ),
                    radius = baseRadius,
                    center = c
                )

                // 2. Línea exterior negra
                drawCircle(
                    color = Color.Black,
                    radius = baseRadius - 1.5f,
                    center = c,
                    style = Stroke(width = 3.5f)
                )

                val guideColor = cs.onSurface.copy(alpha = 0.15f) // Gris claro

                // 3. Círculo de líneas discontinuas en la mitad (POR DEBAJO DEL DEDO)
                val dashedRadius = thumbRadiusPx + (baseRadius - thumbRadiusPx) / 2f
                val dashedPathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)

                drawCircle(
                    color = guideColor,
                    radius = dashedRadius,
                    center = c,
                    style = Stroke(width = 2f, pathEffect = dashedPathEffect)
                )

                // 4. Pulgar central y su sombra (AHORA EN MEDIO DEL ORDEN DE DIBUJO)
                val thumbCenter = Offset(c.x + animX, c.y + animY)

                // Sombra del dedo: más extendida y notoria en la parte de abajo
                for (i in 6 downTo 1) {
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.025f * i),
                        radius = thumbRadiusPx + i * 2f,
                        center = Offset(thumbCenter.x, thumbCenter.y + 6f) // +6f desplaza la sombra hacia abajo
                    )
                }

                // Círculo del pulgar
                drawCircle(
                    color = cs.surface,
                    radius = thumbRadiusPx,
                    center = thumbCenter
                )

                // Aro del pulgar
                drawCircle(
                    color = cs.outlineVariant.copy(alpha = 0.4f),
                    radius = thumbRadiusPx,
                    center = thumbCenter,
                    style = Stroke(width = 1.5f)
                )

                // 5. Guías y ejes (SE DIBUJAN AL FINAL PARA QUE PASEN POR ENCIMA DEL DEDO)

                // Punto central
                drawCircle(
                    color = guideColor,
                    radius = 3f,
                    center = c
                )

                // Las líneas parten fijas desde cerca del centro hasta cerca del borde exterior
                val gapInner = 12f // Hueco interior para no tocar el punto central
                val gapOuter = 10f // Hueco exterior para no tocar la línea negra

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