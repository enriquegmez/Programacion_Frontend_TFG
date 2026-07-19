/**
 * @file SharedComponents.kt
 * @brief Sistema de Diseño de la aplicación.
 * @details Este archivo agrupa componentes visuales genéricos. Garantizan consistencia visual y
 *          reutilización en todas las pantallas.
 * @author Enrique Gómez
 * @date 2026
 */

package com.enrique.tiago_app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.sp
import com.enrique.tiago_app.ui.theme.MonoData
import com.enrique.tiago_app.ui.theme.MonoLabel

/* ============================================================================
 *  1. COMPONENTES ESTRUCTURALES Y BOTONES
 * ========================================================================== */

/**
 * @brief Contenedor para agrupar información.
 * @details Utiliza una elevación tonal sutil, bordes suaves y color SurfaceVariant
 *          para crear contraste con el fondo base (obsidiana) de la aplicación.
 */
@Composable
fun SteelCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        tonalElevation = 2.dp
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

/**
 * @brief Cabecera de sección para el interior de las tarjetas.
 */
@Composable
fun SectionTitle(text: String, icon: ImageVector? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(9.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * @brief Botón de acción principal, grande y ergonómico.
 * @param loading Si es true, oculta el texto/icono y muestra un spinner de carga circular.
 */
@Composable
fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    container: Color = MaterialTheme.colorScheme.primary,
    onContainer: Color = MaterialTheme.colorScheme.onPrimary,
    loading: Boolean = false,
) {
    Button(
        onClick = onClick,
        // Bloqueamos el botón automáticamente si la acción está cargando
        enabled = enabled && !loading,
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = onContainer)
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(22.dp), color = onContainer, strokeWidth = 2.dp)
        } else {
            if (icon != null) { Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
            Text(text, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * @brief Variante destructiva (roja) del botón principal. Usado para paradas de emergencia o desconexiones.
 */
@Composable
fun DangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) =
    PrimaryActionButton(text, onClick, modifier, enabled, icon,
        container = MaterialTheme.colorScheme.error, onContainer = MaterialTheme.colorScheme.onError)

/* ============================================================================
 *  2. INDICADORES Y PÍLDORAS DE INFORMACIÓN
 * ========================================================================== */

/**
 * @brief Etiqueta para valores de sensores. Utiliza la tipografía monoespaciada para alinear números.
 */
@Composable
fun MonoValue(value: String, label: String? = null, tint: Color = MaterialTheme.colorScheme.onSurface) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MonoData, color = tint)
        if (label != null) Text(label.uppercase(), style = MonoLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * @brief Etiqueta visual pequeña con un punto de color y texto descriptivo.
 */
@Composable
fun StatusPill(text: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Box(Modifier.size(9.dp).background(color, CircleShape))
        Spacer(Modifier.width(7.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}

/* ============================================================================
 *  3. VISUALIZACIÓN DE DATOS AVANZADA
 * ========================================================================== */

/**
 * @brief Anillo de progreso circular dibujado a mano usando el Canvas de Compose.
 * @details Este componente es clave en el Dashboard para mostrar la carga del PC o de la batería.
 *          Dibuja un arco de fondo gris y un arco superpuesto con el color y el grado de progreso indicado.
 * @param progress Valor normalizado de 0.0f a 1.0f que representa cuánto rellenar el anillo.
 * @param valueText Texto numérico a mostrar en el centro (ej. "45%").
 */
@Composable
fun MetricRing(
    progress: Float,
    valueText: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    diameter: Dp = 58.dp,
    stroke: Dp = 6.dp,
    valueStyle: androidx.compose.ui.text.TextStyle = MonoData,
) {
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {

            // --- DIBUJO GEOMÉTRICO (CANVAS) ---
            Canvas(Modifier.size(diameter)) {
                val sw = stroke.toPx()
                val inset = sw / 2f
                val arcSize = Size(size.width - sw, size.height - sw)

                // 1. Pista de fondo gris (Anillo completo de 360 grados)
                drawArc(
                    trackColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = sw, cap = StrokeCap.Round)
                )

                // 2. Arco de progreso activo (-90f es necesario para que empiece a dibujar "desde las 12 en punto")
                drawArc(
                    color,
                    startAngle = -90f,
                    sweepAngle = progress.coerceIn(0f, 1f) * 360f, // Multiplicamos el porcentaje normalizado por los grados del círculo
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = sw, cap = StrokeCap.Round)
                )
            }

            // --- TEXTO CENTRAL ---
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(valueText, style = valueStyle, color = MaterialTheme.colorScheme.onSurface)
                if (label.isNotEmpty()) {
                    Text(
                        text = label.uppercase(),
                        style = MonoLabel.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * @brief Píldora compacta que indica si un sensor o actuador específico está activo en el robot.
 */
@Composable
fun CapabilityChip(name: String, available: Boolean) {
    val cs = MaterialTheme.colorScheme
    val dot = if (available) cs.tertiary else cs.error
    val container = if (available) cs.surface else cs.error.copy(alpha = 0.10f)
    val border = if (available) cs.outline else cs.error.copy(alpha = 0.30f)
    val labelColor = if (available) cs.onSurface else cs.error

    Surface(shape = MaterialTheme.shapes.small, color = container, border = BorderStroke(1.dp, border)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(7.dp))
            Text(name, style = MaterialTheme.typography.labelLarge, color = labelColor)
        }
    }
}

/* ============================================================================
 *  4. NAVEGACIÓN Y CABECERAS
 * ========================================================================== */

/**
 * @brief Mini-widget para la batería del robot. Cambia de color dinámicamente según el porcentaje y el estado de carga.
 */
@Composable
fun BatteryPill(batteryPct: Double?, isCharging: Boolean) {
    val cs = MaterialTheme.colorScheme
    val color = when {
        batteryPct == null -> cs.onSurfaceVariant // Estado desconocido
        isCharging         -> cs.secondary        // Cargando (Amarillo/Azul según tema)
        batteryPct > 50    -> cs.tertiary         // Saludable (Verde)
        batteryPct > 25    -> cs.secondary        // Aviso (Amarillo)
        else               -> cs.error            // Crítico (Rojo)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Icon(
            imageVector = if (isCharging) Icons.Default.Bolt else Icons.Default.BatteryFull,
            contentDescription = "Batería",
            tint = color,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (batteryPct != null) "${batteryPct.toInt()}%" else "?%",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

/**
 * @brief Cabecera universal para el tope de todas las pantallas de control.
 * @param eyebrow Texto superior pequeño, generalmente para indicar la categoría (ej. "TELEOPERACIÓN").
 * @param title Texto principal, da nombre a la vista actual (ej. "Joystick Manual").
 */
@Composable
fun ScreenHeader(
    eyebrow: String,
    title: String,
    batteryPct: Double?,
    isCharging: Boolean,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                eyebrow.uppercase(),
                style = MonoLabel,
                color = cs.primary,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                color = cs.onBackground
            )
        }
        BatteryPill(batteryPct = batteryPct, isCharging = isCharging)
    }
}

/* ============================================================================
 *  5. LAYOUTS PERSONALIZADOS
 * ========================================================================== */

/**
 * @brief Contenedor "FlowLayout" implementado a mano.
 * @details Coloca los componentes hijos en una fila horizontal. Si el siguiente componente no cabe
 *          en el ancho disponible de la pantalla, hace un "salto de línea" y continúa debajo.
 *          Se implementa de forma nativa interceptando el motor de renderizado (`Layout`) de Compose.
 */
@Composable
fun WrapRow(
    modifier: Modifier = Modifier,
    horizontalGap: Dp = 8.dp,
    verticalGap: Dp = 8.dp,
    content: @Composable () -> Unit
) {
    // Interceptamos el motor de medidas (measure) y posicionamiento (place) de Compose
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val hGap = horizontalGap.roundToPx()
        val vGap = verticalGap.roundToPx()
        val maxWidth = constraints.maxWidth

        // 1. FASE DE MEDIDA: Preguntamos a cada hijo cuánto ocupa sin forzarle a expandirse
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }

        // 2. FASE DE CÁLCULO DE FILAS: Repartimos los hijos según el ancho de la pantalla
        val rows = mutableListOf<MutableList<androidx.compose.ui.layout.Placeable>>()
        var currentRow = mutableListOf<androidx.compose.ui.layout.Placeable>()
        var currentRowWidth = 0

        placeables.forEach { p ->
            val extra = if (currentRow.isEmpty()) 0 else hGap
            // Si meter este elemento supera el ancho de pantalla, guardamos la fila y creamos una nueva
            if (currentRowWidth + extra + p.width > maxWidth && currentRow.isNotEmpty()) {
                rows.add(currentRow)
                currentRow = mutableListOf()
                currentRowWidth = 0
            }

            val extra2 = if (currentRow.isEmpty()) 0 else hGap
            currentRow.add(p)
            currentRowWidth += extra2 + p.width
        }
        if (currentRow.isNotEmpty()) rows.add(currentRow)

        // Calculamos la altura total que necesitamos pedirle a la pantalla
        val rowHeights = rows.map { row -> row.maxOfOrNull { it.height } ?: 0 }
        val totalHeight = rowHeights.sum() + vGap * (rows.size - 1).coerceAtLeast(0)
        val layoutHeight = totalHeight.coerceIn(constraints.minHeight, constraints.maxHeight)

        // 3. FASE DE DIBUJADO: Colocamos las coordenadas exactas de cada hijo
        layout(width = maxWidth, height = layoutHeight) {
            var y = 0
            rows.forEachIndexed { index, row ->
                var x = 0
                row.forEach { p ->
                    p.placeRelative(x, y)
                    x += p.width + hGap
                }
                y += rowHeights[index] + vGap
            }
        }
    }
}