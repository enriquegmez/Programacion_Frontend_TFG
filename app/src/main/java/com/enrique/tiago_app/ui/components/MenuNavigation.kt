/**
 * @file MenuNavigation.kt
 * @brief Componentes visuales reutilizables para la navegación y disposición de la interfaz.
 * @details Este archivo contiene la lógica visual de la barra de navegación inferior
 *          con su sistema de menús desplegables, así como los controles para
 *          gestionar la vista en pantalla dividida.
 */

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

/**
 * @brief Rutas estáticas que deben coincidir exactamente con el NavHost de MainActivity.
 */
object Routes {
    const val DASHBOARD = "dashboard"
    const val TELEOP    = "teleop"
    const val CAMERA    = "camera"
    const val MOTION    = "motion"
    const val INVEST    = "invest"
    const val JOINTS    = "joints"
    const val SENSORS   = "sensors"
}

/** @brief Modelo de datos para representar un botón dentro del submenú. */
data class Destination(val route: String, val label: String, val icon: ImageVector)

/** Opciones que aparecerán al pulsar la ranura "Control". */
val ControlDestinations = listOf(
    Destination(Routes.TELEOP, "Teleoperación",  Icons.Default.ControlCamera),
    Destination(Routes.MOTION, "Acciones",       Icons.Default.PlayArrow),
    Destination(Routes.JOINTS, "Articulaciones", Icons.Default.PrecisionManufacturing),
)

/** Opciones que aparecerán al pulsar la ranura "Datos". */
val DataDestinations = listOf(
    Destination(Routes.CAMERA,  "Cámara",   Icons.Default.Videocam),
    Destination(Routes.INVEST,  "Análisis", Icons.Default.Search),
    Destination(Routes.SENSORS, "Sensores", Icons.Default.Sensors),
)

/**
 * @brief Componente principal de la barra de navegación flotante inferior.
 * @param currentRoute Ruta actual activa para resaltar el icono correspondiente.
 * @param enabledRoutes Set de rutas permitidas. Se evalúa contra las capacidades reales del robot.
 * @param onNavigate Callback para ejecutar el salto de pantalla.
 * @param onDisconnect Callback para ordenar el cierre de sesión al Director.
 * @param onOpenChange Callback para avisar a la pantalla superior que oscurezca el fondo (velo).
 * @param closeSignal Trigger numérico: si cambia, fuerza el cierre del menú desplegable.
 */
@Composable
fun BottomBar(
    currentRoute: String?,
    enabledRoutes: Set<String>,
    onNavigate: (String) -> Unit,
    onDisconnect: () -> Unit,
    onOpenChange: (Boolean) -> Unit = {},
    closeSignal: Int = 0,
) {
    val cs = MaterialTheme.colorScheme

    // --- ESTADO LOCAL DEL MENÚ ---
    // Guarda qué submenú está abierto actualmente: null (ninguno), "control" o "datos"
    var openGroup by remember { mutableStateOf<String?>(null) }

    // --- EFECTOS SECUNDARIOS (REACCIÓN A EVENTOS EXTERNOS) ---
    // Si el usuario toca la pantalla fuera de la barra (señal enviada desde MainScreen),
    // cerramos cualquier menú desplegable activo.
    LaunchedEffect(closeSignal) {
        if (closeSignal > 0) openGroup = null
    }

    // Cada vez que cambia el menú abierto, avisamos a la pantalla superior
    // para que dibuje o quite un "velo" semitransparente detrás de esta barra.
    LaunchedEffect(openGroup) {
        onOpenChange(openGroup != null)
    }

    // --- CÁLCULO DE RESALTADO ---
    // Determinamos si el icono principal de "Control" o "Datos" debe brillar
    // porque una de sus sub-rutas es la pantalla actual.
    val controlActive = ControlDestinations.any { it.route == currentRoute }
    val dataActive = DataDestinations.any { it.route == currentRoute }

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp)
                .align(Alignment.BottomCenter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- BLOQUE 1: EL MENÚ DESPLEGABLE ---
            // Cargamos la lista de botones correspondiente al menú que el usuario ha tocado.
            val groupDestinations = when (openGroup) {
                "control" -> ControlDestinations
                "datos"   -> DataDestinations
                else      -> emptyList()
            }

            // AnimatedVisibility maneja automáticamente la transición de entrada (subir flotando)
            // y salida (desaparecer hacia abajo) sin bloquear el hilo de la UI.
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
                        // Evaluamos dinámicamente si el hardware de esta vista existe
                        val enabled = dest.route in enabledRoutes

                        SpeedDialItem(
                            dest = dest,
                            enabled = enabled,
                            selected = dest.route == currentRoute,
                            onClick = {
                                if (enabled) {
                                    onNavigate(dest.route)
                                    openGroup = null // Auto-cerramos el menú al navegar
                                }
                            }
                        )
                    }
                }
            }

            // --- BLOQUE 2: LA BARRA PRINCIPAL ---
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
                    // Botón directo a Home/Dashboard
                    BarSlot(
                        icon = Icons.Default.GridView,
                        label = "Inicio",
                        selected = currentRoute == Routes.DASHBOARD,
                        onClick = { openGroup = null; onNavigate(Routes.DASHBOARD) }
                    )
                    // Botón para expandir herramientas motrices
                    BarSlot(
                        icon = Icons.Default.SportsEsports,
                        label = "Control",
                        selected = controlActive || openGroup == "control",
                        onClick = { openGroup = if (openGroup == "control") null else "control" }
                    )
                    // Botón para expandir visualización y telemetría
                    BarSlot(
                        icon = Icons.Default.Insights,
                        label = "Datos",
                        selected = dataActive || openGroup == "datos",
                        onClick = { openGroup = if (openGroup == "datos") null else "datos" }
                    )
                    // Botón directo para abortar y volver al Lobby
                    BarSlot(
                        icon = Icons.Default.PowerSettingsNew,
                        label = "Desconectar",
                        selected = false,
                        tint = cs.error, // Lo forzamos a rojo por su naturaleza destructiva
                        onClick = { openGroup = null; onDisconnect() }
                    )
                }
            }
        }
    }
}

/**
 * @brief Representa un botón individual (ranura) dentro de la barra flotante.
 */
@Composable
private fun BarSlot(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    tint: Color? = null,
) {
    val cs = MaterialTheme.colorScheme

    // Si no se provee un color fijo (tint), calculamos si es Primario (activo) o Gris (inactivo)
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
            // Hacemos el texto bold si está seleccionado para guiar la vista
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

/**
 * @brief Representa una opción del submenú desplegable (Etiqueta + Botón Flotante Circular).
 */
@Composable
private fun SpeedDialItem(
    dest: Destination,
    enabled: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme

    // --- LÓGICA DE COLORES REACTIVA ---
    // Calculamos el estilo visual combinando 3 estados posibles:
    // 1. Deshabilitado por hardware (Gris apagado + icono de candado)
    // 2. Activo y en pantalla actual (Color Primario puro)
    // 3. Activo pero en otra pantalla (Color neutro)
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
        // --- TEXTO A LA IZQUIERDA ---
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

                // Si el robot no tiene este hardware, incrustamos un candado
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

        // --- BOTÓN CIRCULAR PRINCIPAL ---
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
 *  CONTROL "DIVIDIR"
 * -------------------------------------------------------------------------- */

/**
 * @brief Interruptor superior para decidir si la pantalla es completa o se divide en dos (Split Screen).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitSegmentedControl(
    split: Boolean,
    onSplitChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme

    // Paleta de colores personalizada para anular el color "ámbar" por defecto de Material 3
    // y forzar los tonos corporativos de la aplicación.
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

/* ============================================================================
 *  SELECTOR FLOTANTE DE FUENTE (Cámara / Sensores)
 *  Overlay a pantalla completa que se dispara al activar el modo "Dividida".
 * ========================================================================== */

/**
 * @brief Pantalla emergente para obligar al usuario a elegir la fuente de datos del panel superior.
 */
@Composable
fun SplitSourcePicker(
    visible: Boolean,
    cameraEnabled: Boolean,
    onSelectCamera: () -> Unit,
    onSelectSensors: () -> Unit,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme

    // La animación gestiona la entrada suave del velo blanco
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {

        // --- CAJA PROTECTORA Y FONDO TRANSLÚCIDO ---
        // Absorbe los toques (para no interactuar con lo que hay debajo)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = 0.85f))
                .clickable(
                    indication = null, // Desactiva el efecto "onda" de toque de Android
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                ) { onDismiss() },
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier.padding(top = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "¿Qué mostrar en el panel superior?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = cs.onBackground
                )

                // Renderizamos las dos tarjetas de selección
                SourceOption(
                    icon = Icons.Default.Videocam,
                    label = "Cámara",
                    enabled = cameraEnabled,
                    onClick = { if (cameraEnabled) onSelectCamera() }
                )

                SourceOption(
                    icon = Icons.Default.Insights,
                    label = "Sensores",
                    enabled = true,
                    onClick = onSelectSensors
                )
            }
        }
    }
}

/**
 * @brief Tarjeta de selección individual para el componente SplitSourcePicker.
 */
@Composable
private fun SourceOption(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme

    // Cálculo visual reactivo: Degradamos los colores si no hay hardware habilitado
    val container = if (enabled) cs.surface else cs.surfaceVariant.copy(alpha = 0.6f)
    val content = if (enabled) cs.primary else cs.onSurfaceVariant.copy(alpha = 0.5f)
    val borderColor = if (enabled) cs.primary.copy(alpha = 0.4f) else cs.outline

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = container,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        shadowElevation = if (enabled) 6.dp else 0.dp,
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.width(220.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icono envuelto en un círculo suave
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(content.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = label, tint = content, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))

            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (enabled) cs.onSurface else content
            )

            // Renderizamos el candado a la derecha si está inactivo
            if (!enabled) {
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.Lock, contentDescription = "No disponible", tint = content, modifier = Modifier.size(18.dp))
            }
        }
    }
}