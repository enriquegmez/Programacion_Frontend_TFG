package com.enrique.tiago_app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
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
 *  COMPONENTES COMPARTIDOS  ·  "kit" visual del rediseño AXON
 *  Piezas reutilizables para que todas las pantallas hablen el mismo idioma.
 *  Ninguna contiene lógica de negocio.
 * ========================================================================== */

/** Tarjeta "premium": superficie elevada por tono + borde sutil + esquinas 18 dp. */
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

/** Botón primario táctil (alto 56 dp): acción principal de la pantalla. */
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

/** Botón de peligro (parar / apagar / desconectar). */
@Composable
fun DangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) =
    PrimaryActionButton(text, onClick, modifier, enabled, icon,
        container = MaterialTheme.colorScheme.error, onContainer = MaterialTheme.colorScheme.onError)

/** Valor numérico de telemetría en monoespaciada (con etiqueta opcional). */
@Composable
fun MonoValue(value: String, label: String? = null, tint: Color = MaterialTheme.colorScheme.onSurface) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MonoData, color = tint)
        if (label != null) Text(label.uppercase(), style = MonoLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Píldora de estado: punto de color + texto (OK / armado / peligro...). */
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

/** Fila de capacidad: nombre + tick verde / cruz roja. */
@Composable
fun CapabilityRow(name: String, available: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name, style = MaterialTheme.typography.bodyLarge)
        Icon(
            if (available) Icons.Default.CheckCircle else Icons.Default.Cancel,
            contentDescription = if (available) "Disponible" else "No disponible",
            tint = if (available) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(22.dp)
        )
    }
}

/* ============================================================================
 *  PIEZAS DE VISUALIZACIÓN DE DATOS  (Dashboard avanzado)
 * ========================================================================== */

/**
 * Anillo de progreso circular con valor central. Sustituye barras lineales y
 * cifras sueltas por un patrón "instrumento" legible de un vistazo. Sin lógica:
 * recibe un progreso 0f..1f ya calculado por el ViewModel.
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
            Canvas(Modifier.size(diameter)) {
                val sw = stroke.toPx()
                val inset = sw / 2f
                val arcSize = Size(size.width - sw, size.height - sw)
                // Track gris visible
                drawArc(trackColor, 0f, 360f, false,
                    topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                    size = arcSize, style = Stroke(width = sw, cap = StrokeCap.Round))
                drawArc(color, -90f, progress.coerceIn(0f, 1f) * 360f, false,
                    topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                    size = arcSize, style = Stroke(width = sw, cap = StrokeCap.Round))
            }
            // Columna para alinear el valor numérico y el label "BATERÍA" internamente
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(valueText, style = valueStyle, color = MaterialTheme.colorScheme.onSurface)
                if (label.isNotEmpty()) {
                    Text(
                        text = label.uppercase(),
                        style = MonoLabel.copy(fontSize = 9.sp), // Tamaño ajustado para encajar
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** AssistChip de capacidad: punto de estado + nombre. Reemplaza la lista plana. */
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
 *  CABECERA COMÚN DE PANTALLA  ·  "RESUMEN / Título" + píldora de batería
 *  Se usa en el topBar de MainScreen para TODAS las sub-pantallas: solo cambia
 *  el texto (eyebrow + title). Así la cabecera es idéntica en toda la app.
 * ========================================================================== */

/** Píldora de batería verde/ámbar/roja con icono dentro de una cápsula. */
@Composable
fun BatteryPill(batteryPct: Double?, isCharging: Boolean) {
    val cs = MaterialTheme.colorScheme
    val color = when {
        batteryPct == null -> cs.onSurfaceVariant
        isCharging         -> cs.secondary
        batteryPct > 50    -> cs.tertiary
        batteryPct > 25    -> cs.secondary
        else               -> cs.error
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
 * Cabecera de pantalla: etiqueta pequeña en mayúsculas ("RESUMEN"), título
 * grande ("Dashboard") y, a la derecha, la píldora de batería.
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
 *  WRAP ROW  ·  Rejilla que coloca los hijos en fila y SALTA de línea cuando
 *  no caben. Equivale a FlowRow pero implementado con la API base `Layout`,
 *  por lo que funciona en cualquier versión de Compose (sin dependencias).
 * ========================================================================== */
@Composable
fun WrapRow(
    modifier: Modifier = Modifier,
    horizontalGap: Dp = 8.dp,
    verticalGap: Dp = 8.dp,
    content: @Composable () -> Unit
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val hGap = horizontalGap.roundToPx()
        val vGap = verticalGap.roundToPx()
        val maxWidth = constraints.maxWidth

        // Medimos cada hijo sin forzar ancho máximo
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }

        // Repartimos en filas según el ancho disponible
        val rows = mutableListOf<MutableList<androidx.compose.ui.layout.Placeable>>()
        var currentRow = mutableListOf<androidx.compose.ui.layout.Placeable>()
        var currentRowWidth = 0

        placeables.forEach { p ->
            val extra = if (currentRow.isEmpty()) 0 else hGap
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

        // Altura total = suma de alturas de fila (la más alta de cada fila) + huecos
        val rowHeights = rows.map { row -> row.maxOfOrNull { it.height } ?: 0 }
        val totalHeight = rowHeights.sum() + vGap * (rows.size - 1).coerceAtLeast(0)
        val layoutHeight = totalHeight.coerceIn(constraints.minHeight, constraints.maxHeight)

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