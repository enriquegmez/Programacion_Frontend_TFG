package com.enrique.tiago_app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/* ============================================================================
 *  NAVEGACIÓN AXON  ·  Barra flotante inferior con speed-dial
 *
 *  4 ranuras:
 *    - Inicio       -> Dashboard (acción directa)
 *    - Control      -> despliega: Teleoperación, Acciones, Articulaciones
 *    - Datos        -> despliega: Cámara, Análisis, Sensores
 *    - Desconectar  -> acción directa
 *
 *  Las opciones cuyo hardware no está disponible se muestran DESHABILITADAS
 *  (en gris, no pulsables). Las rutas deben coincidir con las del NavHost.
 * ========================================================================== */

/** ⚠️ Alinear con las rutas reales del NavController existente. */
object AxonRoutes {
    const val DASHBOARD = "dashboard"
    const val TELEOP    = "teleop"
    const val CAMERA    = "camera"
    const val MOTION    = "motion"
    const val INVEST    = "invest"
    const val JOINTS    = "joints"
    const val SENSORS   = "sensors"
}

data class AxonDestination(val route: String, val label: String, val icon: ImageVector)

/** Opciones del grupo "Control". */
val AxonControlDestinations = listOf(
    AxonDestination(AxonRoutes.TELEOP, "Teleoperación",  Icons.Default.ControlCamera),
    AxonDestination(AxonRoutes.MOTION, "Acciones",       Icons.Default.PlayArrow),
    AxonDestination(AxonRoutes.JOINTS, "Articulaciones", Icons.Default.PrecisionManufacturing),
)

/** Opciones del grupo "Datos". */
val AxonDataDestinations = listOf(
    AxonDestination(AxonRoutes.CAMERA,  "Cámara",   Icons.Default.Videocam),
    AxonDestination(AxonRoutes.INVEST,  "Análisis", Icons.Default.Search),
    AxonDestination(AxonRoutes.SENSORS, "Sensores", Icons.Default.Sensors),
)

/**
 * Barra flotante inferior + speed-dial.
 *
 * @param currentRoute ruta activa (string) para resaltar el grupo/ítem.
 * @param enabledRoutes conjunto de rutas habilitadas según capacidades del robot.
 *        Cualquier ruta que NO esté aquí se pinta en gris y no responde.
 */
@Composable
fun AxonBottomBar(
    currentRoute: String?,
    enabledRoutes: Set<String>,
    onNavigate: (String) -> Unit,
    onDisconnect: () -> Unit,
    onOpenChange: (Boolean) -> Unit = {},
    closeSignal: Int = 0,
) {
    val cs = MaterialTheme.colorScheme

    // Qué speed-dial está abierto: null | "control" | "datos"
    var openGroup by remember { mutableStateOf<String?>(null) }

    // Cuando MainScreen incrementa closeSignal (p. ej. al tocar el velo), cerramos.
    LaunchedEffect(closeSignal) {
        if (closeSignal > 0) openGroup = null
    }

    // Avisamos a MainScreen para que pinte (o no) el velo blanco a pantalla completa.
    LaunchedEffect(openGroup) { onOpenChange(openGroup != null) }

    val controlActive = AxonControlDestinations.any { it.route == currentRoute }
    val dataActive = AxonDataDestinations.any { it.route == currentRoute }

    Box(modifier = Modifier.fillMaxWidth()) {

        // (El velo blanco a pantalla completa lo pinta MainScreen por detrás.)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp)
                .align(Alignment.BottomCenter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- SPEED-DIAL: opciones que suben encima de la barra ---
            val groupDestinations = when (openGroup) {
                "control" -> AxonControlDestinations
                "datos"   -> AxonDataDestinations
                else      -> emptyList()
            }

            AnimatedVisibility(
                visible = openGroup != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    groupDestinations.forEach { dest ->
                        val enabled = dest.route in enabledRoutes
                        SpeedDialItem(
                            dest = dest,
                            enabled = enabled,
                            selected = dest.route == currentRoute,
                            onClick = {
                                if (enabled) {
                                    onNavigate(dest.route)
                                    openGroup = null
                                }
                            }
                        )
                    }
                }
            }

            // --- LA BARRA FLOTANTE (píldora) ---
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = cs.surface,
                tonalElevation = 3.dp,
                shadowElevation = 8.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, cs.outline)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // INICIO (directo)
                    BarSlot(
                        icon = Icons.Default.GridView,
                        label = "Inicio",
                        selected = currentRoute == AxonRoutes.DASHBOARD,
                        onClick = { openGroup = null; onNavigate(AxonRoutes.DASHBOARD) }
                    )
                    // CONTROL (grupo)
                    BarSlot(
                        icon = Icons.Default.SportsEsports,
                        label = "Control",
                        selected = controlActive || openGroup == "control",
                        onClick = { openGroup = if (openGroup == "control") null else "control" }
                    )
                    // DATOS (grupo)
                    BarSlot(
                        icon = Icons.Default.Insights,
                        label = "Datos",
                        selected = dataActive || openGroup == "datos",
                        onClick = { openGroup = if (openGroup == "datos") null else "datos" }
                    )
                    // DESCONECTAR (directo, en rojo)
                    BarSlot(
                        icon = Icons.Default.PowerSettingsNew,
                        label = "Desconectar",
                        selected = false,
                        tint = cs.error,
                        onClick = { openGroup = null; onDisconnect() }
                    )
                }
            }
        }
    }
}

/** Ranura de la barra: icono + etiqueta, resaltado si está activa. */
@Composable
private fun BarSlot(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    tint: Color? = null,
) {
    val cs = MaterialTheme.colorScheme
    val content = tint ?: if (selected) cs.primary else cs.onSurfaceVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(icon, contentDescription = label, tint = content, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

/** Pastilla del speed-dial: etiqueta a la izquierda + botón circular con icono. */
@Composable
private fun SpeedDialItem(
    dest: AxonDestination,
    enabled: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme

    // Colores según estado (habilitado / deshabilitado / seleccionado)
    val circleColor = when {
        !enabled -> cs.surfaceVariant
        selected -> cs.primary
        else     -> cs.primary
    }
    val iconColor = when {
        !enabled -> cs.onSurfaceVariant.copy(alpha = 0.5f)
        else     -> Color.White
    }
    val labelBg = if (enabled) cs.surface else cs.surface.copy(alpha = 0.7f)
    val labelColor = if (enabled) cs.onSurface else cs.onSurfaceVariant.copy(alpha = 0.6f)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Etiqueta (pastilla)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = labelBg,
            shadowElevation = 4.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, cs.outline)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    dest.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = labelColor,
                    fontWeight = FontWeight.Bold
                )
                if (!enabled) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = "No disponible",
                        tint = cs.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        // Botón circular
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(circleColor)
                .then(
                    if (!enabled) Modifier.border(1.dp, cs.outline, CircleShape) else Modifier
                )
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(dest.icon, contentDescription = dest.label, tint = iconColor, modifier = Modifier.size(24.dp))
        }
    }
}

/* ----------------------------------------------------------------------------
 *  CONTROL "DIVIDIR"  ·  SingleChoiceSegmentedButtonRow (sin cambios de lógica)
 * -------------------------------------------------------------------------- */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitSegmentedControl(
    split: Boolean,
    onSplitChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    // Azul claro de marca para la selección (en vez del ámbar por defecto).
    val segColors = SegmentedButtonDefaults.colors(
        activeContainerColor = cs.primary.copy(alpha = 0.15f),
        activeContentColor = cs.primary,
        activeBorderColor = cs.primary.copy(alpha = 0.5f),
        inactiveContainerColor = cs.surface,
        inactiveContentColor = cs.onSurfaceVariant,
        inactiveBorderColor = cs.outline,
    )
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = !split,
            onClick = { onSplitChange(false) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            colors = segColors,
        ) { Text("Vista completa", fontWeight = FontWeight.Bold) }
        SegmentedButton(
            selected = split,
            onClick = { onSplitChange(true) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            colors = segColors,
        ) { Text("Dividida", fontWeight = FontWeight.Bold) }
    }
}

/**
 * Selector del panel superior en modo dividido (Cámara / Sensores).
 * @param cameraEnabled si el robot no tiene cámara, la opción Cámara se bloquea.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitTopSourceControl(
    topIsCamera: Boolean,
    onSelect: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    cameraEnabled: Boolean = true,
) {
    val cs = MaterialTheme.colorScheme
    val segColors = SegmentedButtonDefaults.colors(
        activeContainerColor = cs.primary.copy(alpha = 0.15f),
        activeContentColor = cs.primary,
        activeBorderColor = cs.primary.copy(alpha = 0.5f),
        inactiveContainerColor = cs.surface,
        inactiveContentColor = cs.onSurfaceVariant,
        inactiveBorderColor = cs.outline,
    )
    SingleChoiceSegmentedButtonRow(modifier) {
        SegmentedButton(
            selected = topIsCamera && cameraEnabled,
            onClick = { if (cameraEnabled) onSelect(true) },
            enabled = cameraEnabled,
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            colors = segColors,
        ) { Text("Cámara") }
        SegmentedButton(
            selected = !topIsCamera || !cameraEnabled,
            onClick = { onSelect(false) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            colors = segColors,
        ) { Text("Sensores") }
    }
}